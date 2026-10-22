package dev.creategalore.bridge;

import com.endertech.minecraft.forge.blocks.ISmokeContainer;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.IFilterFrame;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Pressure in closed spaces. Smog trapped in a room presses on whatever filter frames sit in its ceiling: the fuller
 * the room, the faster smog is forced up through them, no pump needed. Gas a frame doesn't catch passes through
 * unfiltered, and a filter whose top is closed off lets nothing through (see the filter mixin), so the room keeps
 * filling until the machine making the smog blows (Pollution of the Realms' enclosed-emitter rule, or
 * {@link FanSmeltingSmog} for fans).
 */
public final class ExhaustPressure {
    private ExhaustPressure() {}

    private static final int MAX_SEEDS = 256;
    private static final int POCKETS_PER_SECOND = 4;
    private static final int PUSH_PER_FRAME = 6;

    private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> SEEDS = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ExhaustPressure::onLevelTick);
    }

    /** Smog at {@code pos} may be shut in somewhere: check its space soon. */
    public static void seed(ServerLevel level, BlockPos pos) {
        if (Exhaust.outdoors(level, pos)) return;
        LongLinkedOpenHashSet seeds = SEEDS.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        if (seeds.size() < MAX_SEEDS) seeds.add(pos.asLong());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 7) return;
        LongLinkedOpenHashSet seeds = SEEDS.get(level.dimension());
        if (seeds == null) return;
        for (int done = 0; done < POCKETS_PER_SECOND && !seeds.isEmpty(); done++) {
            BlockPos start = BlockPos.of(seeds.removeFirstLong());
            if (!level.isLoaded(start) || !(level.getBlockState(start).getBlock() instanceof Pollutant)) continue;
            Exhaust.Pocket pocket = Exhaust.Pocket.scan(level, start, Exhaust.POCKET_LIMIT);
            pocket.cells.forEach(seeds::remove);
            if (!pocket.open && !pocket.frames.isEmpty()) vent(level, pocket);
        }
    }

    /** Forces smog from a closed space up through the frames in its ceiling, faster the fuller the space is. */
    private static void vent(ServerLevel level, Exhaust.Pocket pocket) {
        LongArrayList gas = new LongArrayList();
        int units = 0;
        int room = 0;
        for (long packed : pocket.cells) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);
            if (!Exhaust.isVolume(state)) continue;
            room += 3;
            if (state.getBlock() instanceof Pollutant<?> pollutant) {
                units += pollutant.getCarriedPollutionAmount(state);
                gas.add(packed);
            }
        }
        if (units == 0) return;
        // Highest smog first: that is what presses on the ceiling.
        gas.sort((a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
        float fullness = units / (float) room;
        int push = Math.min(PUSH_PER_FRAME * pocket.frames.size(), Math.max(1, Math.round(units * fullness)));
        int frame = 0;
        int blocked = 0;
        int next = 0;
        while (push > 0 && next < gas.size() && blocked < pocket.frames.size()) {
            BlockPos cell = BlockPos.of(gas.getLong(next));
            BlockState state = level.getBlockState(cell);
            if (!(state.getBlock() instanceof Pollutant<?> pollutant) || pollutant.getCarriedPollutionAmount(state) <= 0) {
                next++;
                continue;
            }
            if (forceThrough(level, pocket.frames.get(frame), pollutant)) {
                pollutant.spend(level, cell);
                push--;
                blocked = 0;
            } else {
                blocked++;
            }
            frame = (frame + 1) % pocket.frames.size();
        }
    }

    /**
     * Pushes one unit of smog up into the frame stack starting at {@code base}. Each frame gets a chance to catch it
     * (the filter mixin then lets the cleaned air out at the top); smog no frame catches leaves the top unfiltered.
     * An empty or worn-out frame plugs the stack, as it does for pumps.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean forceThrough(ServerLevel level, BlockPos base, Pollutant<?> pollutant) {
        BlockPos.MutableBlockPos cursor = base.mutable();
        BlockState state = level.getBlockState(cursor);
        while (state.getBlock() instanceof IFilterFrame frame) {
            if (!(state.getBlock() instanceof ISmokeContainer container) || !container.isActive(level, cursor)) return false;
            BlockEntity be = level.getBlockEntity(cursor);
            if (be != null && ((IFilterFrame) frame).fill(be, pollutant, 1) > 0) return true;
            cursor.move(0, 1, 0);
            state = level.getBlockState(cursor);
        }
        BlockPos out = Exhaust.outlet(level, base);
        return out != null && pollutant.pump(level, out);
    }
}

package dev.creategalore.bridge;

import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bulk smelting and smoking with a Create fan make smog for every item that comes out done. The fan blowing through
 * lava or fire makes none by itself. When the smog has nowhere left to go (a sealed casing that is full), the
 * pressure blows the casing open, like Pollution of the Realms does for any other machine.
 */
public final class FanSmeltingSmog {
    private FanSmeltingSmog() {}

    private static final float BLASTING_CARBON = 0.25f;
    private static final float BLASTING_SULFUR = 0.05f;
    private static final float SMOKING_CARBON = 0.125f;
    /** Failed emissions in a row (within the window) before the space blows. */
    private static final int FAILS_TO_BLOW = 4;
    private static final long FAIL_WINDOW = 200;
    private static final float BLAST_POWER = 3.0f;
    /** Cells searched for room before the smog counts as having nowhere to go. */
    private static final int SPILL_REACH = 64;

    /** Per dimension: packed 4x4x4 area -> (game time of last failure << 8 | failures). */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> FAILS = new HashMap<>();

    public static void processed(Level level, Vec3 at, FanProcessingType type, int items) {
        if (!(level instanceof ServerLevel server) || items <= 0) return;
        float carbon;
        float sulfur = 0f;
        if (type == AllFanProcessingTypes.BLASTING) {
            carbon = BLASTING_CARBON;
            sulfur = BLASTING_SULFUR;
        } else if (type == AllFanProcessingTypes.SMOKING) {
            carbon = SMOKING_CARBON;
        } else {
            return;
        }
        BlockPos pos = BlockPos.containing(at);
        // An item on a belt sits inside the belt's own cell; its smoke starts in the air above it.
        if (!Exhaust.isVolume(server.getBlockState(pos))) pos = pos.above();
        int[] tally = new int[2];
        emit(server, pos, "carbon", items * carbon, tally);
        emit(server, pos, "sulfur", items * sulfur, tally);
        int wanted = tally[0];
        int placed = tally[1];
        if (wanted == 0) return;
        ExhaustPressure.seed(server, pos);
        Long2LongOpenHashMap fails = FAILS.computeIfAbsent(server.dimension(), k -> new Long2LongOpenHashMap());
        if (fails.size() > 1024) fails.clear();
        // Items move along a belt, so count failures per small area, not per cell.
        long key = BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        if (placed >= wanted) {
            fails.remove(key);
            return;
        }
        long now = server.getGameTime();
        long last = fails.get(key);
        int count = last != 0 && now - (last >>> 8) <= FAIL_WINDOW ? (int) (last & 0xFF) + 1 : 1;
        if (count < FAILS_TO_BLOW) {
            fails.put(key, now << 8 | count);
            return;
        }
        fails.remove(key);
        server.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, BLAST_POWER, false, Level.ExplosionInteraction.BLOCK);
    }

    /** Puts {@code amount} units of one pollutant into the air around {@code pos}; tally = {wanted, placed}. */
    private static void emit(ServerLevel level, BlockPos pos, String name, float amount, int[] tally) {
        int units = round(level, amount);
        if (units <= 0) return;
        Pollutant<?> pollutant = AdPother.getInstance().pollutants.findByName(name).orElse(null);
        if (pollutant == null) return;
        tally[0] += units;
        tally[1] += spill(level, pollutant, pos, units);
    }

    /**
     * Puts smog into the nearest free room around {@code start} that gas can actually reach (walls and sealed belts
     * stop it), so smog made inside a casing stays inside it. Returns how many units found room.
     */
    private static int spill(ServerLevel level, Pollutant<?> pollutant, BlockPos start, int units) {
        int placed = 0;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start.asLong());
        queue.enqueue(start.asLong());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && placed < units && seen.size() <= SPILL_REACH) {
            BlockPos cell = BlockPos.of(queue.dequeueLong());
            placed += pollutant.pump(level, cell, units - placed);
            for (Direction dir : Direction.values()) {
                next.setWithOffset(cell, dir);
                if (!level.isLoaded(next) || !seen.add(next.asLong())) continue;
                if (Exhaust.passable(level, next, level.getBlockState(next), dir)) queue.enqueue(next.asLong());
            }
        }
        return placed;
    }

    /** Whole units, with the fraction as a chance, so small batches still smoke on average. */
    private static int round(ServerLevel level, float amount) {
        int whole = (int) amount;
        return whole + (level.random.nextFloat() < amount - whole ? 1 : 0);
    }
}

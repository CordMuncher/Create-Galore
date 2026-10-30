package dev.creategalore.bridge.waterpump;

import com.endertech.minecraft.mods.adpother.blocks.PollutedWater;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * The pump's work. Every few seconds it looks for polluted water within its reach (only loaded chunks, and only the
 * chunk sections that hold any). It takes the nearest patches it can pull and moves each one block toward itself along
 * the water's surface, as fast as its Filter Frame allows. A patch that reaches the pump's column turns into clean
 * water with a burst of bubbles. A patch with no water path toward the pump (another pond, a wall in the way) is left
 * for a minute before the pump tries it again. Nothing is saved: a reloaded pump just looks again.
 */
public final class WaterPumpBlockEntity extends BlockEntity {
    private static final int SCAN_TICKS = 100;
    private static final int RETRY_STUCK_TICKS = 1200;

    /** Patches being pulled: where each one is now, and where it was a step ago (so it never steps straight back). */
    private final Map<BlockPos, BlockPos> pulling = new LinkedHashMap<>();
    /** Patches that found no way toward the pump, and the game time to try them again. */
    private final Map<BlockPos, Long> stuck = new HashMap<>();
    private long nextScan;
    private long nextStep;
    /** The last look found polluted water, so an emptied pump looks again soon instead of waiting the full time. */
    private boolean sawWater;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(WaterPumps.BLOCK_ENTITY.get(), pos, state);
    }

    private WaterPumps.Tier tier() {
        return getBlockState().getBlock() instanceof WaterPumpBlock pump ? pump.tier : WaterPumps.Tier.IRON;
    }

    private int pullSpeed() {
        return WaterPumps.pullSpeed(level.getBlockState(worldPosition.above()));
    }

    private boolean inWater() {
        for (Direction side : Direction.values()) {
            if (side != Direction.UP && level.getFluidState(worldPosition.relative(side)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (now < nextStep) return;
        int speed = pullSpeed();
        if (speed == 0 || !inWater() || !server.hasNeighborSignal(worldPosition)) {
            pulling.clear();
            nextStep = now + 20;
            return;
        }
        nextStep = now + 20 / speed;
        stuck.values().removeIf(until -> until <= now);
        if (now >= nextScan || pulling.isEmpty() && sawWater && now >= nextScan - SCAN_TICKS + 20) {
            nextScan = now + SCAN_TICKS;
            List<BlockPos> found = find(server);
            sawWater = !found.isEmpty();
            for (BlockPos patch : found) {
                if (pulling.size() >= tier().patches) break;
                if (!pulling.containsKey(patch) && !stuck.containsKey(patch)) pulling.put(patch, patch);
            }
        }
        if (!pulling.isEmpty()) step(server, now);
        if (now % 20 < 20 / speed) bubbles(server);
    }

    /** Polluted water within reach, nearest first. */
    private List<BlockPos> find(ServerLevel server) {
        int reach = tier().reach;
        BlockPos pos = worldPosition;
        List<BlockPos> found = new ArrayList<>();
        int minY = Math.max(server.getMinBuildHeight(), pos.getY() - reach);
        int maxY = Math.min(server.getMaxBuildHeight() - 1, pos.getY() + reach);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - reach); cx <= SectionPos.blockToSectionCoord(pos.getX() + reach); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - reach); cz <= SectionPos.blockToSectionCoord(pos.getZ() + reach); cz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int index = server.getSectionIndex(minY); index <= server.getSectionIndex(maxY); index++) {
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof PollutedWater)) continue;
                    int baseY = SectionPos.sectionToBlockCoord(server.getSectionYFromSectionIndex(index));
                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (y < minY || y > maxY) continue;
                        for (int lz = 0; lz < 16; lz++) {
                            int z = (cz << 4) + lz;
                            if (Math.abs(z - pos.getZ()) > reach) continue;
                            for (int lx = 0; lx < 16; lx++) {
                                int x = (cx << 4) + lx;
                                if (Math.abs(x - pos.getX()) > reach) continue;
                                if (isPatch(section.getBlockState(lx, ly, lz))) found.add(at.set(x, y, z).immutable());
                            }
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingInt(p -> flatDistance(p, pos)));
        return found;
    }

    private static boolean isPatch(BlockState state) {
        return state.getBlock() instanceof PollutedWater && state.getFluidState().isSource();
    }

    private static int flatDistance(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Moves every pulled patch one block closer, or cleans it once it is over the pump. */
    private void step(ServerLevel server, long now) {
        Map<BlockPos, BlockPos> moved = new LinkedHashMap<>();
        for (Iterator<Map.Entry<BlockPos, BlockPos>> it = pulling.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, BlockPos> entry = it.next();
            BlockPos patch = entry.getKey();
            it.remove();
            BlockState state = server.getBlockState(patch);
            // The wind or another pump took it, or it was cleaned some other way.
            if (!server.isLoaded(patch) || !isPatch(state)) continue;
            int dx = worldPosition.getX() - patch.getX(), dz = worldPosition.getZ() - patch.getZ();
            if (Math.max(Math.abs(dx), Math.abs(dz)) <= 1) {
                clean(server, patch);
                continue;
            }
            BlockPos next = nextStep(server, patch, entry.getValue(), dx, dz);
            if (next == null || moved.containsKey(next)) {
                stuck.put(patch, now + RETRY_STUCK_TICKS);
                continue;
            }
            server.setBlock(patch, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            server.setBlock(next, state, Block.UPDATE_ALL);
            moved.put(next, patch);
        }
        pulling.putAll(moved);
    }

    /** The open surface water next to the patch that brings it closest to the pump, never straight back. */
    private static BlockPos nextStep(ServerLevel server, BlockPos patch, BlockPos came, int dx, int dz) {
        Direction alongX = dx > 0 ? Direction.EAST : Direction.WEST;
        Direction alongZ = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        Direction[] order = xFirst
                ? new Direction[] {alongX, dz != 0 ? alongZ : Direction.SOUTH, dz != 0 ? alongZ.getOpposite() : Direction.NORTH}
                : new Direction[] {alongZ, dx != 0 ? alongX : Direction.EAST, dx != 0 ? alongX.getOpposite() : Direction.WEST};
        for (Direction side : order) {
            BlockPos next = patch.relative(side);
            if (!next.equals(came) && openSurface(server, next)) return next;
        }
        return null;
    }

    /** Clean still water with no clean still water on top: where a patch can float. */
    private static boolean openSurface(ServerLevel server, BlockPos pos) {
        if (!server.isLoaded(pos)) return false;
        BlockState state = server.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) return false;
        BlockState above = server.getBlockState(pos.above());
        return !(above.is(Blocks.WATER) && above.getFluidState().isSource());
    }

    private static void clean(ServerLevel server, BlockPos patch) {
        server.setBlock(patch, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        server.sendParticles(ParticleTypes.BUBBLE, patch.getX() + 0.5, patch.getY() + 0.4, patch.getZ() + 0.5, 14, 0.35, 0.3, 0.35, 0.05);
        server.sendParticles(ParticleTypes.SPLASH, patch.getX() + 0.5, patch.getY() + 1.0, patch.getZ() + 0.5, 10, 0.3, 0.05, 0.3, 0.1);
        server.playSound(null, patch, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 0.5F,
                Mth.randomBetween(server.random, 0.9F, 1.2F));
    }

    /** A thin stream of bubbles from the pump's water side while it works. */
    private void bubbles(ServerLevel server) {
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            FluidState fluid = server.getFluidState(next);
            if (side != Direction.UP && fluid.is(FluidTags.WATER) && fluid.isSourceOfType(Fluids.WATER)) {
                server.sendParticles(ParticleTypes.BUBBLE, next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.02);
                return;
            }
        }
    }

    /** What a right-click says: what is missing, or what it is doing. */
    Component status() {
        WaterPumps.Tier tier = tier();
        String key = "create_galore_bridge.water_pump.";
        if (pullSpeed() == 0) return Component.translatable(key + "no_frame");
        if (!inWater()) return Component.translatable(key + "no_water");
        if (!level.hasNeighborSignal(worldPosition)) return Component.translatable(key + "no_signal");
        int found = level instanceof ServerLevel server ? find(server).size() : 0;
        if (found == 0) return Component.translatable(key + "clean", tier.reach, pullSpeed());
        return Component.translatable(key + "working", found, tier.reach, Math.min(found, tier.patches), pullSpeed());
    }
}

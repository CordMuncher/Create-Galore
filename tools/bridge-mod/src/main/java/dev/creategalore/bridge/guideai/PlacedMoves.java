package dev.creategalore.bridge.guideai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * For the mixins that move blocks around (Create contraptions, Sable ships): asks whether a block was placed by a
 * player before it leaves, and marks it again where it lands. Opened storage moves along the same way. Does nothing on clients, in singleplayer or when no
 * placed blocks are known in that dimension.
 */
public final class PlacedMoves {
    private PlacedMoves() {
    }

    public static boolean active(Level level) {
        PlacedBlocks placed = GuideAi.placed();
        return placed != null && level instanceof ServerLevel server && placed.has(server);
    }

    public static boolean isPlaced(Level level, BlockPos pos) {
        PlacedBlocks placed = GuideAi.placed();
        return placed != null && level instanceof ServerLevel server && placed.isPlaced(server, pos);
    }

    public static void forget(Level level, BlockPos pos) {
        PlacedBlocks placed = GuideAi.placed();
        if (placed != null && level instanceof ServerLevel server) placed.remove(server, pos);
    }

    /** Marks the block that landed at pos, if anything did. */
    public static void mark(Level level, BlockPos pos) {
        PlacedBlocks placed = GuideAi.placed();
        if (placed == null || !(level instanceof ServerLevel server)) return;
        BlockState state = server.getBlockState(pos);
        if (PlacedBlocks.counts(state)) placed.addPos(server, pos);
    }

    /** A player emptied a bucket at pos. */
    public static void poured(Level level, BlockPos pos) {
        PlacedBlocks placed = GuideAi.placed();
        if (placed != null && level instanceof ServerLevel server) placed.onPour(server, pos.immutable());
    }

    /** Whether a player has opened the storage at pos, so the book counts it as theirs. */
    public static boolean isTouchedStorage(Level level, BlockPos pos) {
        TouchedStorage storage = GuideAi.storage();
        return storage != null && level instanceof ServerLevel server && storage.isTouched(server, pos);
    }

    /** Opened storage that moved from olds to news (same index), so the book still knows it as the player's. */
    public static void storageMoved(Level from, Level to, java.util.List<BlockPos> olds, java.util.List<BlockPos> news) {
        TouchedStorage storage = GuideAi.storage();
        if (storage != null && from instanceof ServerLevel a && to instanceof ServerLevel b) storage.moved(a, b, olds, news);
    }

    public static boolean tracking() {
        return GuideAi.placed() != null;
    }
}

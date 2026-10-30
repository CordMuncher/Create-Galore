package dev.creategalore.bridge;

import com.endertech.minecraft.forge.blocks.ISmokeContainer;
import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import com.endertech.minecraft.mods.adpother.pollution.IFilterFrame;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import dev.creategalore.bridge.encased.EncasedDoors;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Filtered exhaust and the shared rules of the pack's exhaust system (Pollution of the Realms smog, Advanced Chimneys
 * flues). Filtered exhaust rises like smoke, slips up through filter stacks and chimneys, drifts along ceilings, and
 * only thins away once it is under the open sky (or in a space too big to count as a room). Smog can't move into it.
 */
public final class Exhaust {
    private Exhaust() {}

    static final TagKey<Block> CHIMNEYS = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "chimneys"));
    /** A closed space bigger than this many cells counts as open air. */
    static final int POCKET_LIMIT = 2048;
    private static final int COLUMN_LIMIT = 48;

    static int stepDelay(RandomSource random) {
        return 8 + random.nextInt(7);
    }

    public static boolean isExhaust(BlockState state) {
        return state.is(CreateGaloreBridge.FILTERED_EXHAUST.get());
    }

    /** Filter frames, chimneys, pumps, vents and ducts: smoke and exhaust pass up through them. */
    static boolean isFlue(BlockState state) {
        Block block = state.getBlock();
        return block instanceof IFilterFrame || block instanceof ISmokeContainer || state.is(CHIMNEYS);
    }

    /** A belt with casing on it is sealed: items ride through, smog and exhaust don't. */
    public static boolean isSealedBelt(BlockState state) {
        return state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.CASING);
    }

    /** A belt with casing, or a cased door or trapdoor that is shut. */
    public static boolean isSealed(BlockState state) {
        return isSealedBelt(state) || EncasedDoors.isSealed(state);
    }

    /** Air, smog or exhaust: room that gas fills. */
    static boolean isVolume(BlockState state) {
        return state.isAir() || state.getBlock() instanceof AbstractGas || isExhaust(state);
    }

    /** Whether gas moving in direction {@code dir} can get through this cell (the same rule smog uses). */
    static boolean passable(LevelReader level, BlockPos pos, BlockState state, Direction dir) {
        if (isVolume(state)) return true;
        if (!state.getFluidState().isEmpty() || isSealed(state) || isFlue(state)) return false;
        return !state.isFaceSturdy(level, pos, dir) && !state.isFaceSturdy(level, pos, dir.getOpposite());
    }

    /** Nothing solid anywhere above: out in the open. */
    static boolean outdoors(LevelReader level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    /**
     * Where exhaust leaves the flue above {@code pos}: the first free cell above its frames, chimneys, smog and
     * waiting exhaust, or null when something solid closes it off.
     */
    static BlockPos outlet(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < COLUMN_LIMIT; i++) {
            cursor.move(Direction.UP);
            if (level.isOutsideBuildHeight(cursor) || !level.isLoaded(cursor)) return null;
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) return cursor.immutable();
            if (!isFlue(state) && !isVolume(state)) return null;
        }
        return null;
    }

    public static boolean hasOutlet(Level level, BlockPos frame) {
        return outlet(level, frame) != null;
    }

    /** A filter above {@code frame} has just caught {@code units} of smog: let the cleaned air out at the top. */
    public static void release(ServerLevel level, BlockPos frame, int units) {
        int blocks = units / 3 + (level.random.nextInt(3) < units % 3 ? 1 : 0);
        for (int i = 0; i < blocks; i++) {
            BlockPos out = outlet(level, frame);
            if (out == null) return;
            level.setBlock(out, CreateGaloreBridge.FILTERED_EXHAUST.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    static void step(ServerLevel level, BlockPos pos, RandomSource random) {
        Block self = CreateGaloreBridge.FILTERED_EXHAUST.get();
        if (!level.getBlockState(pos).is(self)) return;
        if (!level.isAreaLoaded(pos, 1)) {
            level.scheduleTick(pos, self, 40);
            return;
        }
        if (outdoors(level, pos)) {
            // Out in the open it thins away within a couple of seconds, still rising while it does.
            BlockPos up = pos.above();
            if (random.nextInt(3) != 0 && level.getBlockState(up).isAir()) move(level, pos, up);
            else level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (random.nextInt(25) == 0 && Pocket.scan(level, pos, POCKET_LIMIT).open) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        BlockPos dest = rise(level, pos);
        if (dest == null && random.nextInt(3) == 0) dest = drift(level, pos, random);
        if (dest != null) move(level, pos, dest);
        else level.scheduleTick(pos, self, 20 + random.nextInt(20));
    }

    private static BlockPos rise(ServerLevel level, BlockPos pos) {
        BlockPos up = pos.above();
        BlockState above = level.getBlockState(up);
        if (above.isAir()) return up;
        if (isFlue(above) || isExhaust(above)) return outlet(level, pos);
        if (!isVolume(above) && passable(level, up, above, Direction.UP) && level.getBlockState(up.above()).isAir()) return up.above();
        return null;
    }

    private static BlockPos drift(ServerLevel level, BlockPos pos, RandomSource random) {
        int first = random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            BlockPos side = pos.relative(Direction.from2DDataValue((first + i) & 3));
            if (level.isLoaded(side) && level.getBlockState(side).isAir()) return side;
        }
        return null;
    }

    private static void move(ServerLevel level, BlockPos from, BlockPos to) {
        level.setBlock(to, CreateGaloreBridge.FILTERED_EXHAUST.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** The connected space around a cell that gas can spread through, with the filter frames in its ceiling. */
    static final class Pocket {
        boolean open;
        final LongArrayList cells = new LongArrayList();
        final List<BlockPos> frames = new ArrayList<>();

        static Pocket scan(Level level, BlockPos start, int limit) {
            Pocket pocket = new Pocket();
            LongOpenHashSet seen = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            seen.add(start.asLong());
            queue.enqueue(start.asLong());
            BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            while (!queue.isEmpty()) {
                long packed = queue.dequeueLong();
                cell.set(packed);
                pocket.cells.add(packed);
                if (pocket.cells.size() > limit || outdoors(level, cell)) {
                    pocket.open = true;
                    return pocket;
                }
                for (Direction dir : Direction.values()) {
                    next.setWithOffset(cell, dir);
                    long key = next.asLong();
                    if (seen.contains(key)) continue;
                    if (level.isOutsideBuildHeight(next)) continue;
                    if (!level.isLoaded(next)) {
                        pocket.open = true;
                        return pocket;
                    }
                    BlockState state = level.getBlockState(next);
                    if (state.getBlock() instanceof IFilterFrame) {
                        // Only a frame in the ceiling takes the pressure; one met from the side may still sit above.
                        if (dir == Direction.UP) {
                            seen.add(key);
                            pocket.frames.add(next.immutable());
                        }
                        continue;
                    }
                    // Not marked as seen when blocked: a thin block may still let gas through from another side.
                    if (!passable(level, next, state, dir)) continue;
                    seen.add(key);
                    queue.enqueue(key);
                }
            }
            return pocket;
        }
    }
}

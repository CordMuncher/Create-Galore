package dev.creategalore.fishing.machine;

import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Where a machine turns its rod to drop a catch: into the column of blocks right beside it (touching, no gap), down
 * to {@link #REACH_DOWN} blocks below it.
 *
 * <ul>
 * <li>A block beside the machine at its own height that takes items, whatever it is (from that face, as before).</li>
 * <li>Lower down, the first block the fish would fall onto: a belt, an upward funnel that is taking items (it passes
 * the fish into what it sits on and its filter has a say), a barrel with its lid facing up (it opens to catch it), or
 * a hopper.</li>
 * </ul>
 *
 * Anything else in the way (a solid block, water) closes that column. Below the machine itself is
 * {@link MountedRod}'s business: a catch goes straight down into what is under it, with no turn.
 */
final class CatchDrop {
    /** How far below the machine a catch can be dropped. */
    static final int REACH_DOWN = 5;
    /** Blocks per tick per tick a dropped fish falls with (about what an item falls with). */
    static final double GRAVITY = 0.05;
    /** Ticks the fish hangs over the target, settling, before it is let go. */
    static final int HOLD_TICKS = 5;
    /** Ticks a barrel stays open after the fish lands in it. */
    static final int BARREL_OPEN_AFTER = 8;
    /** The machine's sides, in the order they are tried. */
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    enum Kind { TOUCHING, BELT, FUNNEL, BARREL, HOPPER }

    /**
     * A place a catch can go: what it is, where, on which side of the machine, and how high its landing surface is
     * above the machine block's bottom (blocks; below zero for a target lower down).
     */
    record Target(Kind kind, BlockPos pos, Direction side, float top) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind.name().toLowerCase(Locale.ROOT));
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.putInt("side", side.get3DDataValue());
            tag.putFloat("top", top);
            return tag;
        }

        static Target load(CompoundTag tag) {
            Kind kind;
            try {
                kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
            BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
            return pos == null ? null : new Target(kind, pos, Direction.from3DDataValue(tag.getInt("side")), tag.getFloat("top"));
        }
    }

    private CatchDrop() {
    }

    /** The first place beside the machine at {@code at} that would take some of {@code stack}, or null. */
    static Target find(Level level, BlockPos at, ItemStack stack) {
        for (Direction side : SIDES) {
            Target t = inColumn(level, at, side, stack);
            if (t != null) return t;
        }
        return null;
    }

    private static Target inColumn(Level level, BlockPos at, Direction side, ItemStack stack) {
        BlockPos column = at.relative(side);
        for (int down = 0; down <= REACH_DOWN; down++) {
            BlockPos pos = column.below(down);
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            // A row of drives would otherwise feed each other's bait slots with the catch.
            if (be instanceof RodMount) return null;
            Kind kind = opensUp(level, pos, state, be);
            if (kind == null && down == 0 && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite()) != null) {
                kind = Kind.TOUCHING;
            }
            if (kind != null) {
                Target t = new Target(kind, pos.immutable(), side, landing(kind, level, pos, state) - down);
                return takes(level, t, stack) ? t : null;
            }
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return null;
        }
        return null;
    }

    /** What a fish falling from above can land in at {@code pos}, or null. */
    private static Kind opensUp(Level level, BlockPos pos, BlockState state, BlockEntity be) {
        if (MountedRod.isBelt(be)) return Kind.BELT;
        if (state.getBlock() instanceof FunnelBlock && AbstractFunnelBlock.getFunnelFacing(state) == Direction.UP
                && !state.getValue(FunnelBlock.EXTRACTING) && !state.getValue(AbstractFunnelBlock.POWERED)) {
            return Kind.FUNNEL;
        }
        if (state.getBlock() instanceof BarrelBlock && state.getValue(BlockStateProperties.FACING) == Direction.UP) return Kind.BARREL;
        if (state.getBlock() instanceof HopperBlock) return Kind.HOPPER;
        return null;
    }

    /** Height of the landing surface in the block at {@code pos}, from its bottom. */
    private static float landing(Kind kind, Level level, BlockPos pos, BlockState state) {
        if (kind == Kind.BARREL) return 0.85f;
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? 0.5f : (float) Math.min(1, shape.max(Direction.Axis.Y));
    }

    private static boolean takes(Level level, Target t, ItemStack stack) {
        return insert(level, t, stack, true).getCount() < stack.getCount();
    }

    /** Puts {@code stack} into the target (or tries to); what is left over. */
    static ItemStack insert(Level level, Target t, ItemStack stack, boolean simulate) {
        BlockState state = level.getBlockState(t.pos());
        // Changed since the drop was planned (broken, turned, a funnel switched off).
        Kind now = opensUp(level, t.pos(), state, level.getBlockEntity(t.pos()));
        if (t.kind() != Kind.TOUCHING && now != t.kind()) return stack;
        if (t.kind() == Kind.FUNNEL) return AbstractFunnelBlock.tryInsert(level, t.pos(), stack, simulate);
        Direction face = t.kind() == Kind.TOUCHING ? t.side().getOpposite() : Direction.UP;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, t.pos(), face);
        return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }

    /** Whether the target is a belt, where a fish stays alive for a while. */
    static boolean onBelt(Level level, Target t) {
        return MountedRod.isBelt(level.getBlockEntity(t.pos()));
    }

    /** Ticks a fish let go {@code height} blocks above where it lands takes to fall there. */
    static int fallTicks(double height) {
        return Math.max(2, (int) Math.ceil(Math.sqrt(2 * Math.max(0.1, height) / GRAVITY)));
    }
}

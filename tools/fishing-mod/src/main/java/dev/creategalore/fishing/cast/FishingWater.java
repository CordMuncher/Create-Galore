package dev.creategalore.fishing.cast;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Reads the fluid around a hook: where its surface and bottom are, which fluid it is, whether it is open water. */
public final class FishingWater {
    private FishingWater() {
    }

    /** "water", "lava", or null when the spot isn't a fluid. The void is checked separately. */
    public static String medium(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.is(FluidTags.WATER)) return "water";
        if (fluid.is(FluidTags.LAVA)) return "lava";
        return null;
    }

    /**
     * Height below which the void can be fished, as Tide's defaults had it: six blocks under the bottom of the
     * world in the Overworld and Nether, fifty above the bottom of the End (off the edge of the islands).
     */
    public static double voidSurface(Level level) {
        if (level.dimension() == Level.END) return level.getMinBuildHeight() + 50;
        return level.getMinBuildHeight() - 6;
    }

    /** Top of the fluid column at {@code pos} (searching up to 32 blocks), as a y coordinate. */
    public static double surface(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        if (level.getFluidState(p).isEmpty()) return pos.getY();
        for (int i = 0; i < 32 && !level.getFluidState(p.above()).isEmpty(); i++) p.move(0, 1, 0);
        FluidState top = level.getFluidState(p);
        return p.getY() + top.getHeight(level, p);
    }

    /** Bottom of the fluid column at {@code pos} (the top of the first solid block below), searching 64 blocks. */
    public static double bottom(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 64 && !level.getFluidState(p.below()).isEmpty(); i++) p.move(0, -1, 0);
        return p.getY();
    }

    /** Fluid depth at a spot, in blocks. */
    public static double depth(Level level, BlockPos pos) {
        return surface(level, pos) - bottom(level, pos);
    }

    /**
     * Vanilla's "open water", simplified: the 5x5 around the hook is the same fluid at the hook's level and the one
     * below, with nothing solid over the surface. Vanilla treasure needs it.
     */
    public static boolean openWater(Level level, Vec3 hook) {
        BlockPos center = BlockPos.containing(hook);
        FluidState here = level.getFluidState(center);
        if (here.isEmpty()) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    if (!level.getFluidState(center.offset(dx, dy, dz)).getType().isSame(here.getType())) return false;
                }
                if (!level.getBlockState(center.offset(dx, 1, dz)).getCollisionShape(level, center).isEmpty()
                        && level.getFluidState(center.offset(dx, 1, dz)).isEmpty()) return false;
            }
        }
        return level.canSeeSky(center.above());
    }

    /** True if {@code pos} is in the same fluid as the medium (or open air below the void surface for "void"). */
    public static boolean in(Level level, BlockPos pos, String medium) {
        if ("void".equals(medium)) return pos.getY() <= voidSurface(level) && level.getBlockState(pos).isAir();
        return medium.equals(medium(level, pos));
    }
}

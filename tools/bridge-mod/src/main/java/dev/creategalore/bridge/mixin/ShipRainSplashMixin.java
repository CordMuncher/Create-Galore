package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rain splashes land on a Sable ship where Sable says the column's top is: the ship's top block rounded to a whole
 * block, then the world's own block or water at that height. On a deck that sits under the sea's level (a laden launch)
 * that is the sea's surface, which the hull keeps out of sight, so the drops splashed on hidden water above the deck
 * (seen even from where the water itself isn't); elsewhere they floated up to a block over the deck or sank into it.
 * Each splash now goes on the ship's own surface at that spot, unless the world has something over it there: a roof or
 * bridge of its own, or open sea that really covers the ship.
 */
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class ShipRainSplashMixin {
    /** How far over and under the splash Sable picked to look for the ship's surface. */
    @Unique
    private static final double create_galore_bridge$ABOVE = 1.5;
    @Unique
    private static final double create_galore_bridge$BELOW = 3.0;
    @Unique
    private static final double create_galore_bridge$STEP = 1.0 / 32.0;

    @WrapOperation(method = "tickRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void create_galore_bridge$splashOnShip(ClientLevel level, ParticleOptions particle, double x, double y, double z,
                                                   double dx, double dy, double dz, Operation<Void> original) {
        original.call(level, particle, x, create_galore_bridge$landing(level, x, y, z), z, dx, dy, dz);
    }

    @Unique
    private static double create_galore_bridge$landing(ClientLevel level, double x, double y, double z) {
        double from = y + create_galore_bridge$ABOVE;
        double to = y - create_galore_bridge$BELOW;
        BoundingBox3d column = new BoundingBox3d(x - 0.01, to, z - 0.01, x + 0.01, from, z + 0.01);
        double deck = Double.NEGATIVE_INFINITY;
        Vector3d local = new Vector3d();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, column)) {
            if (ship.isRemoved()) continue;
            for (double h = from; h > to && h > deck; h -= create_galore_bridge$STEP) {
                ship.logicalPose().transformPositionInverse(local.set(x, h, z));
                if (create_galore_bridge$solid(level, pos.set(Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z)), local)) {
                    deck = h;
                    break;
                }
            }
        }
        if (deck == Double.NEGATIVE_INFINITY) return y;
        // Rounded down into the deck: lift it out. Over the deck: drop it there unless the world covers the ship.
        if (deck >= y) return deck;
        return create_galore_bridge$coveredByWorld(level, x, deck, y, z) ? y : deck;
    }

    /** Whether the ship's block at pos fills the point (in the ship's own coordinates). */
    @Unique
    private static boolean create_galore_bridge$solid(ClientLevel level, BlockPos pos, Vector3d local) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        double fx = local.x - pos.getX(), fy = local.y - pos.getY(), fz = local.z - pos.getZ();
        if (!state.getFluidState().isEmpty() && fy <= state.getFluidState().getOwnHeight()) return true;
        if (!state.blocksMotion()) return false;
        for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
            if (fx >= box.minX && fx <= box.maxX && fy >= box.minY && fy <= box.maxY && fz >= box.minZ && fz <= box.maxZ) return true;
        }
        return false;
    }

    /** Whether the world's own blocks, or sea the ship doesn't keep out, lie over the ship's surface up to the splash. */
    @Unique
    private static boolean create_galore_bridge$coveredByWorld(ClientLevel level, double x, double deck, double top, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = Mth.floor(deck + 0.05); by <= Mth.floor(top - 0.01); by++) {
            pos.set(Mth.floor(x), by, Mth.floor(z));
            BlockState state = CompartmentTracker.realBlockState(level, pos);
            if (state.blocksMotion()) return true;
        }
        Vec3 above = new Vec3(x, deck + 0.05, z);
        if (!CompartmentTracker.realFluidState(level, BlockPos.containing(above)).is(FluidTags.WATER)) return false;
        WaterOcclusionContainer<?> dry = WaterOcclusionContainer.getContainer(level);
        return dry == null || !dry.isOccluded(above);
    }
}

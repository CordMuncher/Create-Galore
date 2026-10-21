package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Assembling a ship moves its blocks into the ship's plot and writes air straight into the world's chunks where they
 * stood, without the neighbour updates a broken block sends. The water around a hull built in the sea never gets a
 * fluid tick, so a ship-shaped hole stays in the sea for good after the ship sails off, and while the ship still sits
 * over it Deep Seas reads the water surface at the bottom of the hole and lets the boat settle too low. Give every
 * fluid next to an emptied spot its tick, so the sea flows back in as it would after breaking those blocks by hand.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableAssemblyRefillMixin {
    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private static void create_galore_bridge$refillVacated(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                         Iterable<BlockPos> blocks, CallbackInfo ci) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos p : blocks) {
            // Only spots left in the world: taking a ship apart or splitting one empties plot cells, which stay dry.
            if (Sable.HELPER.getContaining(level, p) != null || !level.isLoaded(p)) continue;
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                if (!level.isLoaded(n)) continue;
                FluidState fluid = level.getFluidState(n);
                if (!fluid.isEmpty()) level.scheduleTick(n.immutable(), fluid.getType(), fluid.getType().getTickDelay(level));
            }
        }
    }
}

package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ShipGluedWater;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every ship built or split off from now on carries no glued water, so ShipGluedWater's one-time clean skips it. */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableAssembledCleanMixin {
    @Inject(method = "assembleBlocks", at = @At("RETURN"))
    private static void create_galore_bridge$markClean(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks, BoundingBox3ic bounds,
                                                     CallbackInfoReturnable<ServerSubLevel> cir) {
        if (cir.getReturnValue() != null) ShipGluedWater.markClean(level, cir.getReturnValue().getUniqueId());
    }
}

package dev.creategalore.structscan.mixin;

import dev.creategalore.structscan.ColumnCache;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Answers climate samples from ColumnCache while a scan runs; does nothing otherwise. */
@Mixin(Climate.Sampler.class)
abstract class ClimateSamplerMixin {
    @Inject(method = "sample", at = @At("HEAD"), cancellable = true)
    private void structscan$cached(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        Climate.TargetPoint t = ColumnCache.climate((Climate.Sampler) (Object) this, x, y, z);
        if (t != null) cir.setReturnValue(t);
    }

    @Inject(method = "sample", at = @At("RETURN"))
    private void structscan$store(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        ColumnCache.storeClimate((Climate.Sampler) (Object) this, x, y, z, cir.getReturnValue());
    }
}

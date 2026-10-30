package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.CostMeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cost meter: times the shadow map draw (its shadowcomp passes count apart). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisShadowRendererCostMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"))
    private void create_galore_bridge$costShadowStart(CallbackInfo ci) {
        CostMeter.shadowBegin();
    }

    @Inject(method = "renderShadows", at = @At("RETURN"))
    private void create_galore_bridge$costShadowEnd(CallbackInfo ci) {
        CostMeter.shadowEnd();
    }
}

package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.CostMeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cost meter: times the final pass (with its buffer swaps). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.FinalPassRenderer", remap = false)
public abstract class IrisFinalPassCostMixin {
    @Inject(method = "renderFinalPass", at = @At("HEAD"))
    private void create_galore_bridge$costFinalStart(CallbackInfo ci) {
        CostMeter.push(CostMeter.FINAL);
    }

    @Inject(method = "renderFinalPass", at = @At("RETURN"))
    private void create_galore_bridge$costFinalEnd(CallbackInfo ci) {
        CostMeter.pop();
    }
}

package dev.creategalore.dlsstestkit.mixin;

import dev.creategalore.dlsstestkit.Capture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a run the pack's frameCounter (and so its TAA jitter and the Bridge's DLSS jitter) restarts with each mode. */
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$FrameCounter", remap = false)
public abstract class IrisFrameCounterMixin {
    @Inject(method = "getAsInt", at = @At("HEAD"), cancellable = true)
    private void create_galore_dlss_testkit$frameCounter(CallbackInfoReturnable<Integer> cir) {
        if (Capture.running()) {
            cir.setReturnValue(Capture.frame());
        }
    }
}

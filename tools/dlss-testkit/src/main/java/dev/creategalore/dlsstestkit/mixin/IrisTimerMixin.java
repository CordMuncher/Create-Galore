package dev.creategalore.dlsstestkit.mixin;

import dev.creategalore.dlsstestkit.Capture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a run the pack's clock (waving leaves, water, clouds, exposure fades) ticks once per frame (60 fps unless set). */
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public abstract class IrisTimerMixin {
    @Inject(method = "getFrameTimeCounter", at = @At("HEAD"), cancellable = true)
    private void create_galore_dlss_testkit$frameTimeCounter(CallbackInfoReturnable<Float> cir) {
        if (Capture.running()) {
            cir.setReturnValue(Capture.frame() * Capture.packSecondsPerFrame());
        }
    }

    @Inject(method = "getLastFrameTime", at = @At("HEAD"), cancellable = true)
    private void create_galore_dlss_testkit$lastFrameTime(CallbackInfoReturnable<Float> cir) {
        if (Capture.running()) {
            cir.setReturnValue(Capture.packSecondsPerFrame());
        }
    }
}

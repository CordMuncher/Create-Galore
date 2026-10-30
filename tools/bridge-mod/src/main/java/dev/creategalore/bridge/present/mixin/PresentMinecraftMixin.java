package dev.creategalore.bridge.present.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.creategalore.bridge.present.Presentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Perfect Presentation's MinecraftMixin (MIT, sidit77): frame pacing, the final copy and swap chain resizes. */
@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public abstract class PresentMinecraftMixin {
    @Inject(method = "resizeDisplay", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;resize(IIZ)V"))
    private void create_galore_bridge$resizeSwapChain(CallbackInfo ci) {
        Presentation.resize();
    }

    /** Waits for the swap chain before starting a frame (frame latency 1), like a DirectX game. */
    @Inject(method = "runTick(Z)V", at = @At(value = "CONSTANT", args = "stringValue=render"))
    private void create_galore_bridge$waitForSwapChain(boolean renderLevel, CallbackInfo ci) {
        Presentation.waitForSwapChain();
        dev.creategalore.bridge.latency.FrameLatency.beforeRender(true);
    }

    @WrapOperation(method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    private void create_galore_bridge$blitToSwapChain(RenderTarget target, int width, int height, Operation<Void> original) {
        if (!Presentation.blitToSwapChain(target.getColorTextureId())) {
            original.call(target, width, height);
        }
    }
}

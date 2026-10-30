package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.client.CostMeter;
import dev.creategalore.bridge.client.FpsOverlay;
import dev.creategalore.bridge.latency.FrameLatency;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where each frame starts, starts rendering and is handed to the window: for the input latency work (see
 * {@link FrameLatency}), the frame counter ({@link FpsOverlay}) and the cost meter ({@link CostMeter}).
 */
@Mixin(Minecraft.class)
public abstract class LatencyMinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void create_galore_bridge$frameStart(boolean renderLevel, CallbackInfo ci) {
        FpsOverlay.frame();
        CostMeter.frameStart();
        FrameLatency.frameStart();
    }

    /** Perfect Presentation's own mixin does this after its swap chain wait while it shows the game. */
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=render"))
    private void create_galore_bridge$beforeRender(boolean renderLevel, CallbackInfo ci) {
        FrameLatency.beforeRender(false);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
    private void create_galore_bridge$frameSubmitted(boolean renderLevel, CallbackInfo ci) {
        CostMeter.frameEnd();
        FrameLatency.frameSubmitted();
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void create_galore_bridge$frameCap(int fps, Operation<Void> original) {
        if (!FrameLatency.limitsFrameRate(fps)) {
            original.call(fps);
        }
    }
}

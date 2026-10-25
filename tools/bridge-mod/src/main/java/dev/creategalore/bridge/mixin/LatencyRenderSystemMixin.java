package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.creategalore.bridge.latency.FrameLatency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Input latency tests: when the game last read input (see {@link FrameLatency}). */
@Mixin(RenderSystem.class)
public abstract class LatencyRenderSystemMixin {
    @Inject(method = "pollEvents", at = @At("RETURN"))
    private static void create_galore_bridge$polled(CallbackInfo ci) {
        FrameLatency.polled();
    }
}

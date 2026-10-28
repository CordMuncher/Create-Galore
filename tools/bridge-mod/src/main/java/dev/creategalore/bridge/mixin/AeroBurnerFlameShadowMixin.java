package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A lit Aeronautics burner draws its flame straight away with a Veil shader instead of through the buffers it is
 * given. Iris also draws block entities into a shader pack's shadow map (Photon's Block Entity Shadows, on at Ultra),
 * and under Iris every Veil shader draw is sent to the screen's own colour and depth target. So the flame seen from
 * the sun landed in the screen's depth, at the place the shadow camera sees it: a thin dashed streak pinned on screen
 * that drifted as the player walked, lit with whatever the screen held there before. A flame casts no shadow, so the
 * shadow pass now skips it; the flame itself is drawn as before.
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerRenderer", remap = false)
public abstract class AeroBurnerFlameShadowMixin {
    @Inject(method = "renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$notFromShadowPass(CallbackInfo ci) {
        if (NameTagDefer.inShadowPass()) {
            ci.cancel();
        }
    }
}

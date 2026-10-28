package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The heat shimmer inside a heated Aeronautics balloon is a screen effect drawn when solid blocks are done. Under
 * Iris that moment also comes while a shader pack draws its shadow map, so the shimmer was drawn with the shadow
 * camera into the screen's own colour and depth, then left the shadow map drawing at the window's size for the rest
 * of its blocks. The shadow pass now skips it; the shimmer on screen is drawn as before.
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect.ClientBalloonEffectRenderer", remap = false)
public abstract class AeroBalloonHeatShadowMixin {
    @Inject(method = "onRenderLevelStage(Lfoundry/veil/api/event/VeilRenderLevelStageEvent$Stage;Lorg/joml/Matrix4fc;Lorg/joml/Matrix4fc;I)V",
            at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$notFromShadowPass(CallbackInfo ci) {
        if (NameTagDefer.inShadowPass()) {
            ci.cancel();
        }
    }
}

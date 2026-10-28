package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Flight Control's Attitude Display draws a small see-through copy of the ship it sits on after the world is
 * drawn, at the place on screen its block was last drawn: each time the block renders, it saves the model-view
 * matrix it was drawn with. Iris also draws block entities into a shader pack's shadow map (Photon's Block Entity
 * Shadows, on at Ultra), with the shadow camera's model-view, and the block saved that one too. When the ship was out
 * of view, only the shadow pass drew the block, so the copy was drawn where the shadow camera sees it: a ship-coloured
 * speck pinned on screen that jumped as the player walked and drifted with the sun. The shadow pass no longer saves
 * the place; the copy shows only where the block itself is seen, as without shaders.
 */
@Mixin(targets = "ace.flight.neoforge.client.AttitudeDisplayBlockEntityRenderer", remap = false)
public abstract class FlightAttitudeShadowMixin {
    @Inject(method = "cacheSubLevel(Lace/flight/block/AttitudeDisplayBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$notFromShadowPass(CallbackInfo ci) {
        if (NameTagDefer.inShadowPass()) {
            ci.cancel();
        }
    }
}

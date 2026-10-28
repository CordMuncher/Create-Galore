package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Flight Control's HUD Projector has the same flaw as the Attitude Display (see FlightAttitudeShadowMixin):
 * its block renderer only saves the model-view it was drawn with, and the HUD is drawn from that after the world.
 * In Iris's shadow pass that was the shadow camera's, so a projector out of view showed its HUD at the shadow
 * camera's place on screen. The shadow pass no longer saves it.
 */
@Mixin(targets = "ace.flight.neoforge.client.HudProjectorBlockEntityRenderer", remap = false)
public abstract class FlightHudProjectorShadowMixin {
    @Inject(method = "render(Lace/flight/block/HudProjectorBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$notFromShadowPass(CallbackInfo ci) {
        if (NameTagDefer.inShadowPass()) {
            ci.cancel();
        }
    }
}

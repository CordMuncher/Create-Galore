package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the name tags {@link NameTagDefer} held back, once the level (and Iris's last pass) is done and before the hand. */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer")
public abstract class NameTagFlushMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void create_galore_bridge$drawNameTags(CallbackInfo ci) {
        NameTagDefer.flush();
    }
}

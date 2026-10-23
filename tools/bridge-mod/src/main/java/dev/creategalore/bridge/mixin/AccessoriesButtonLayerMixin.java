package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accessories puts its inventory button on as a screen layer, not a widget, so it can't be taken off the screen like
 * the other two. The wear screen replaces Accessories' screen, so the layer is never added.
 */
@Mixin(targets = "io.wispforest.accessories.client.AccessoriesClient", remap = false)
public abstract class AccessoriesButtonLayerMixin {
    @Inject(method = "initLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noButton(CallbackInfo ci) {
        ci.cancel();
    }
}

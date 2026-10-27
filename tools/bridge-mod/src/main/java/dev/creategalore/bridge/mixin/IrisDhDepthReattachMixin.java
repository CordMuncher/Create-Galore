package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.DhDepthReattach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After Iris pointed its framebuffers at Minecraft's new depth buffer, Distant Horizons' own depth goes back on its
 * framebuffers even when its texture kept its name (see {@link DhDepthReattach}): Iris then attaches it again itself.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompatInternal", remap = false)
public abstract class IrisDhDepthReattachMixin {
    @Shadow
    private int storedDepthTex;

    @Inject(method = "reconnectDHTextures", at = @At("HEAD"))
    private void create_galore_bridge$reattach(int depthTex, CallbackInfo ci) {
        if (DhDepthReattach.take()) {
            storedDepthTex = -1;
        }
    }
}

package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.DhDepthReattach;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris is about to point its framebuffers at Minecraft's new depth buffer, Distant Horizons' too (see {@link DhDepthReattach}). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.targets.RenderTargets", remap = false)
public abstract class IrisDhDepthResizeMixin {
    @Shadow
    private int cachedDepthBufferVersion;

    @Inject(method = "resizeIfNeeded", at = @At("HEAD"))
    private void create_galore_bridge$depthReplaced(int newDepthBufferVersion, int newDepthTextureId, int newWidth, int newHeight,
            DepthBufferFormat newDepthFormat, PackDirectives packDirectives, CallbackInfoReturnable<Boolean> cir) {
        if (cachedDepthBufferVersion != newDepthBufferVersion) {
            DhDepthReattach.depthReplaced();
        }
    }
}

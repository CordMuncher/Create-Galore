package dev.creategalore.bridge.present.mixin;

import java.nio.IntBuffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.MainTarget;
import dev.creategalore.bridge.present.Presentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Perfect Presentation's RenderTargetMixin (MIT, sidit77). The main target's colour texture takes its storage from a
 * shared D3D11 texture. Perfect Presentation picks the second texture upload; NeoForge adds a depth-stencil upload
 * before it, so this picks the colour one by its format (GL_RGBA8) instead.
 */
@Mixin(targets = "com.mojang.blaze3d.pipeline.RenderTarget", remap = false)
public abstract class PresentRenderTargetMixin {
    @Shadow
    protected int colorTextureId;

    @WrapOperation(method = "createBuffers(IIZ)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V"))
    private void create_galore_bridge$sharedColorTexture(int target, int level, int internalFormat, int width, int height, int border,
                                                         int format, int type, IntBuffer data, Operation<Void> original) {
        if (!(internalFormat == 0x8058 /* GL_RGBA8 */ && (Object) this instanceof MainTarget
                && Presentation.allocateMainColor(colorTextureId, target, internalFormat, width, height))) {
            original.call(target, level, internalFormat, width, height, border, format, type, data);
        }
    }

    @Inject(method = "destroyBuffers()V", at = @At("HEAD"))
    private void create_galore_bridge$releaseSharedColorTexture(CallbackInfo ci) {
        if ((Object) this instanceof MainTarget && colorTextureId > -1) {
            Presentation.releaseMainColor(colorTextureId);
        }
    }
}

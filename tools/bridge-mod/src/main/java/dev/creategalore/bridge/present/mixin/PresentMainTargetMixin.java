package dev.creategalore.bridge.present.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.MainTarget;
import dev.creategalore.bridge.present.Presentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Builds the main target through RenderTarget.createBuffers, where its colour texture gets shared with D3D11. */
@Mixin(targets = "com.mojang.blaze3d.pipeline.MainTarget", remap = false)
public abstract class PresentMainTargetMixin {
    @WrapOperation(method = "<init>(II)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/MainTarget;createFrameBuffer(II)V"))
    private void create_galore_bridge$createBuffers(MainTarget target, int width, int height, Operation<Void> original) {
        if (Presentation.active()) {
            target.createBuffers(width, height, false);
        } else {
            original.call(target, width, height);
        }
    }
}

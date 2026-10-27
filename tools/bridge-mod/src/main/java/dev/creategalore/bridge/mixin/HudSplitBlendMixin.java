package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.creategalore.bridge.hud.HudSplitBlend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every blend factor change, so the HUD split can record the HUD's coverage in alpha (see {@link HudSplitBlend}). */
@Mixin(GlStateManager.class)
public abstract class HudSplitBlendMixin {
    @WrapOperation(method = "_blendFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V"))
    private static void create_galore_bridge$blendFunc(int src, int dst, Operation<Void> original) {
        if (!HudSplitBlend.blendFunc(src, dst)) {
            original.call(src, dst);
        }
    }

    @WrapOperation(method = "glBlendFuncSeparate", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL14;glBlendFuncSeparate(IIII)V"))
    private static void create_galore_bridge$blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, Operation<Void> original) {
        if (!HudSplitBlend.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)) {
            original.call(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }
}

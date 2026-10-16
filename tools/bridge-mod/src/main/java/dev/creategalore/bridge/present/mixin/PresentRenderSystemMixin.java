package dev.creategalore.bridge.present.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.present.Presentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Presents the swap chain instead of swapping the (context-less) game window. */
@Mixin(targets = "com.mojang.blaze3d.systems.RenderSystem", remap = false)
public abstract class PresentRenderSystemMixin {
    @WrapOperation(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void create_galore_bridge$present(long window, Operation<Void> original) {
        if (!Presentation.swapBuffers()) {
            original.call(window);
        }
    }
}

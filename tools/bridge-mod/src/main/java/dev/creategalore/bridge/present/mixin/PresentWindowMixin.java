package dev.creategalore.bridge.present.mixin;

import static org.lwjgl.glfw.GLFW.GLFW_DECORATED;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwSetWindowAttrib;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.present.Presentation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Perfect Presentation's WindowMixin (MIT, sidit77), on NeoForge's window creation. See {@link Presentation}. */
@Mixin(targets = "com.mojang.blaze3d.platform.Window", remap = false)
public abstract class PresentWindowMixin {
    @Shadow
    @Final
    private long window;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"))
    private void create_galore_bridge$captureWindowHint(int hint, int value, Operation<Void> original) {
        if (!Presentation.swallowHint(hint, value)) {
            original.call(hint, value);
        }
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long create_galore_bridge$createWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor, Operation<Long> original) {
        return Presentation.createWindow(m -> original.call(width, height, title, m), monitor);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V"))
    private void create_galore_bridge$makeCurrent(long handle, Operation<Void> original) {
        if (!Presentation.makeCurrent()) {
            original.call(handle);
        }
    }

    @WrapOperation(method = "updateVsync", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V"))
    private void create_galore_bridge$swapInterval(int interval, Operation<Void> original) {
        if (!Presentation.swapInterval(interval)) {
            original.call(interval);
        }
    }

    /** Fullscreen becomes a borderless window covering the monitor; the swap chain makes it as fast as fullscreen. */
    @WrapOperation(method = "setMode", at = @At(value = "INVOKE", ordinal = 0, target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V"))
    private void create_galore_bridge$borderlessFullscreen(long handle, long monitor, int x, int y, int width, int height, int refreshRate, Operation<Void> original) {
        if (!Presentation.active()) {
            original.call(handle, monitor, x, y, width, height, refreshRate);
            return;
        }
        glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE);
        original.call(handle, 0L, x, y, width, height, -1);
    }

    @Inject(method = "setMode", at = @At(value = "INVOKE", ordinal = 1, target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V"))
    private void create_galore_bridge$restoreBorder(CallbackInfo ci) {
        if (Presentation.active()) {
            glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
        }
    }

    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDestroyWindow(J)V"))
    private void create_galore_bridge$closeSwapChain(CallbackInfo ci) {
        Presentation.close();
    }
}

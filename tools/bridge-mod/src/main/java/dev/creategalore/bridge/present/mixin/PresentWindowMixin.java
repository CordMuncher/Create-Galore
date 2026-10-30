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
    @Shadow
    private boolean fullscreen;

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
        Presentation.modeChanged(handle, monitor, true, x, y, width, height);
    }

    /** A window that doesn't fit the screen comes back fitted to it (see Presentation.fitWindow). */
    @WrapOperation(method = "setMode", at = @At(value = "INVOKE", ordinal = 1, target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V"))
    private void create_galore_bridge$restoreBorder(long handle, long monitor, int x, int y, int width, int height, int refreshRate, Operation<Void> original) {
        if (!Presentation.active()) {
            original.call(handle, monitor, x, y, width, height, refreshRate);
            return;
        }
        glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_TRUE);
        int[] fit = Presentation.fitWindow(handle, x, y, width, height);
        original.call(handle, monitor, fit[0], fit[1], fit[2], fit[3], refreshRate);
        Presentation.modeChanged(handle, monitor, false, fit[0], fit[1], fit[2], fit[3]);
    }

    /** The borderless full screen window counts as on its monitor, as exclusive full screen is (see Presentation.borderlessMonitor). */
    @WrapOperation(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetWindowMonitor(J)J"))
    private long create_galore_bridge$borderlessMonitor(long handle, Operation<Long> original) {
        long monitor = original.call(handle);
        return monitor != 0L ? monitor : Presentation.borderlessMonitor();
    }

    /** For the log: the window's size changed, while the game has it in full screen or a window. */
    @Inject(method = "onResize", at = @At("HEAD"))
    private void create_galore_bridge$watchSize(long handle, int width, int height, CallbackInfo ci) {
        Presentation.windowResized(handle, fullscreen, width, height);
    }

    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDestroyWindow(J)V"))
    private void create_galore_bridge$closeSwapChain(CallbackInfo ci) {
        Presentation.close();
    }
}

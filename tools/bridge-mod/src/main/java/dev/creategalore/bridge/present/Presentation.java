package dev.creategalore.bridge.present;

import static org.lwjgl.glfw.GLFW.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import dev.creategalore.bridge.DisplaySettings;
import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perfect Presentation for NeoForge: the game window gets no OpenGL context; the game renders on a hidden OpenGL
 * context and each frame is shown through a DXGI flip-model swap chain (see {@link InteropContext}). Borderless
 * fullscreen then runs like exclusive fullscreen, Alt-Tab never changes the display mode (no 10-bit to 8-bit flash on
 * NVIDIA), and NVIDIA's "Prefer layered on DXGI Swapchain" present method is not needed (it held the GPU at ~55%).
 *
 * Ported from Perfect Presentation 1.0 for Fabric 1.21.1 (https://github.com/sidit77/perfect_presentation, MIT,
 * Copyright (c) sidit77). NeoForge creates the window through ImmediateWindowHandler, so that hook differs, NeoForge's
 * OpenGL version check is pointed away from the context-less window (see hideFromGlVersionCheck), and the main
 * target is copied to D3D11 instead of shared (see {@link InteropContext}).
 *
 * EXPERIMENTAL, OFF BY DEFAULT, Windows only: config/create_galore_bridge-display.properties, perfectPresentation=true.
 * The mixins are only applied when it is on ({@link PresentationMixinPlugin}). If the DirectX side cannot start (no
 * WGL_NV_DX_interop2, no D3D11), the game logs the error and falls back to a normal OpenGL window.
 */
public final class Presentation {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Applies the mixins without turning the feature on, to check they still fit the game's code. */
    private static final String FORCE_MIXINS = "create_galore_bridge.presentation.forceMixins";

    private static boolean requested;
    private static boolean failed;
    private static final WglContext.ContextFlags FLAGS = new WglContext.ContextFlags();
    private static final List<int[]> SWALLOWED_HINTS = new ArrayList<>();
    private static InteropContext interop;
    private static long window;

    private Presentation() {}

    /** Decides at mixin load whether the presentation mixins go in. */
    static synchronized boolean decide() {
        boolean force = Boolean.getBoolean(FORCE_MIXINS);
        if (!DisplaySettings.get(DisplaySettings.PERFECT_PRESENTATION)) {
            LOG.info("Perfect Presentation: off (config/{})", DisplaySettings.FILE);
            return force;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            LOG.warn("Perfect Presentation: on in config but this is not Windows, leaving it off");
            return force;
        }
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
            LOG.warn("Perfect Presentation: NeoForge's early loading window already made an OpenGL game window. "
                    + "Turned earlyWindowControl off in config/fml.toml; Perfect Presentation takes effect after a restart.");
            return force;
        }
        requested = true;
        LOG.info("Perfect Presentation: on, the game window will be shown through a DXGI swap chain");
        return true;
    }

    /** True while frames go through the DXGI swap chain. */
    public static boolean active() {
        return requested && !failed;
    }

    // --- Window.<init> ---

    /** Window hints: the OpenGL ones describe the hidden context instead, the game window gets no client API. */
    public static boolean swallowHint(int hint, int value) {
        if (!active()) {
            return false;
        }
        switch (hint) {
            case GLFW_CLIENT_API -> { }
            case GLFW_CONTEXT_CREATION_API -> { }
            case GLFW_CONTEXT_VERSION_MAJOR -> FLAGS.majorVersion = value;
            case GLFW_CONTEXT_VERSION_MINOR -> FLAGS.minorVersion = value;
            case GLFW_OPENGL_PROFILE -> FLAGS.profile = switch (value) {
                case GLFW_OPENGL_COMPAT_PROFILE -> WglContext.ContextFlags.Profile.COMPAT;
                case GLFW_OPENGL_ANY_PROFILE -> WglContext.ContextFlags.Profile.ANY;
                default -> WglContext.ContextFlags.Profile.CORE;
            };
            case GLFW_OPENGL_FORWARD_COMPAT -> FLAGS.forwardCompatible = value != GLFW_FALSE;
            default -> {
                return false;
            }
        }
        SWALLOWED_HINTS.add(new int[] {hint, value});
        return true;
    }

    /**
     * Creates the game window without an OpenGL context and builds the swap chain on it. {@code create} makes the
     * window on the given monitor (0 = windowed); fullscreen is turned into borderless later, in setMode.
     */
    public static long createWindow(java.util.function.Function<LongSupplier, Long> create, LongSupplier monitor) {
        if (!active()) {
            long handle = create.apply(monitor);
            if (Boolean.getBoolean(FORCE_MIXINS)) {
                hideFromGlVersionCheck(); // test run: check the reflection works on this NeoForge
            }
            return handle;
        }
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_FALSE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        long handle = create.apply(() -> 0L);
        try {
            interop = new InteropContext(GLFWNativeWin32.glfwGetWin32Window(handle), FLAGS);
            window = handle;
            hideFromGlVersionCheck();
            LOG.info("Perfect Presentation: DXGI swap chain ready (OpenGL on a hidden context)");
            return handle;
        } catch (Throwable t) {
            LOG.error("Perfect Presentation: could not set up the DXGI swap chain, falling back to a normal OpenGL window", t);
            failed = true;
            interop = null;
            glfwDestroyWindow(handle);
            glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_TRUE);
            for (int[] hint : SWALLOWED_HINTS) {
                glfwWindowHint(hint[0], hint[1]);
            }
            return create.apply(monitor);
        }
    }

    /**
     * NeoForge checks mods' openGLVersion requirement against the version it reads off the game window, which has no
     * context now and would read as 0.0, failing every mod that asks for 3.2+. Its fallback class only reads the window
     * it created itself; clearing that makes it answer "4.6", its own answer when it has no window. The class loads
     * before mixins do, so this uses reflection (NeoForge's module is automatic, so its packages are open).
     */
    private static void hideFromGlVersionCheck() {
        try {
            Field field = Class.forName("net.neoforged.neoforge.client.loading.NoVizFallback").getDeclaredField("WINDOW");
            field.setAccessible(true);
            field.setLong(null, 0L);
            LOG.info("Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window");
        } catch (Throwable t) {
            LOG.warn("Perfect Presentation: could not clear NeoForge's window for its OpenGL version check", t);
        }
    }

    public static boolean makeCurrent() {
        if (!active()) {
            return false;
        }
        interop.makeCurrent();
        return true;
    }

    // --- Window ---

    public static boolean swapInterval(int interval) {
        if (!active()) {
            return false;
        }
        interop.setSyncInterval(interval);
        return true;
    }

    public static void close() {
        if (active() && interop != null) {
            interop.close();
            interop = null;
        }
    }

    // --- RenderSystem.flipFrame ---

    public static boolean swapBuffers() {
        if (!active()) {
            return false;
        }
        interop.present();
        return true;
    }

    // --- Minecraft.runTick / resizeDisplay ---

    public static void waitForSwapChain() {
        if (active()) {
            interop.waitForSwapChainSignal();
        }
    }

    public static boolean blitToSwapChain(int colorTexture) {
        if (!active()) {
            return false;
        }
        interop.blitToSwapChain(colorTexture);
        return true;
    }

    // --- DLAA (dev.creategalore.bridge.dlss) runs DLSS on this device instead of opening its own ---

    /** The swap chain's ID3D11Device, or 0 when frames are not going through it. */
    public static long d3dDevice() {
        return active() && interop != null ? interop.device() : 0L;
    }

    /** The device's immediate ID3D11DeviceContext, or 0. NGX keeps its state as it found it. */
    public static long d3dContext() {
        return active() && interop != null ? interop.context() : 0L;
    }

    /** The device's WGL_NV_DX_interop handle (from wglDXOpenDeviceNV), or 0. */
    public static long interopDevice() {
        return active() && interop != null ? interop.interopDevice() : 0L;
    }

    public static void resize() {
        if (!active()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            interop.resizeSwapChain(w.get(0), h.get(0));
        }
    }
}

package dev.creategalore.bridge.present;

import static org.lwjgl.glfw.GLFW.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import dev.creategalore.bridge.DisplaySettings;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowIconifyCallback;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perfect Presentation for NeoForge: the game window gets no OpenGL context; the game renders on a hidden OpenGL
 * context and each frame is shown through a DXGI flip-model swap chain, on Direct3D 11 ({@link InteropContext}) or,
 * with presenter=dx12 (a choice on the settings screen, and a fresh install's), Direct3D 12 ({@link D3D12Presenter},
 * falling back to 11). Borderless
 * fullscreen then runs like exclusive fullscreen, Alt-Tab never changes the display mode (no 10-bit to 8-bit flash on
 * NVIDIA), and NVIDIA's "Prefer layered on DXGI Swapchain" present method is not needed (it held the GPU at ~55%).
 *
 * Ported from Perfect Presentation 1.0 for Fabric 1.21.1 (https://github.com/sidit77/perfect_presentation, MIT,
 * Copyright (c) sidit77). NeoForge creates the window through ImmediateWindowHandler, so that hook differs, NeoForge's
 * OpenGL version check is pointed away from the context-less window (see hideFromGlVersionCheck), and the main
 * target is copied to D3D11 instead of shared (see {@link InteropContext}).
 *
 * Windows only, on in a fresh install (see DisplaySettings): config/create_galore_bridge-display.properties,
 * perfectPresentation=true. The mixins are only applied when it is on ({@link PresentationMixinPlugin}). If the DirectX side cannot start (no
 * WGL_NV_DX_interop2, no D3D11), the game logs the error and falls back to a normal OpenGL window.
 */
public final class Presentation {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Applies the mixins without turning the feature on, to check they still fit the game's code. */
    private static final String FORCE_MIXINS = "create_galore_bridge.presentation.forceMixins";
    /** Where cg_present.dll is unpacked: the game folder's DLSS folder, beside cg_ngx.dll. */
    private static final String NATIVES_FOLDER = "create-galore-dlss";

    private static boolean requested;
    private static boolean failed;
    /** DirectX 12 was asked for at launch but could not start, so Direct3D 11 runs. */
    private static boolean dx12FellBack;
    private static final WglContext.ContextFlags FLAGS = new WglContext.ContextFlags();
    private static final List<int[]> SWALLOWED_HINTS = new ArrayList<>();
    private static Presenter presenter;
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

    /** True if Perfect Presentation was on when the game started, even if it then failed (see {@link #active()}). */
    public static boolean requested() {
        return requested;
    }

    /**
     * Perfect Presentation was just switched on in the settings. NeoForge's early loading window would make an OpenGL
     * game window first at the next launch, so it goes off now and one restart is enough.
     */
    public static void switchedOn() {
        if (System.getProperty("os.name", "").startsWith("Windows")
                && FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
            LOG.info("Perfect Presentation: switched on in the settings, turned earlyWindowControl off in config/fml.toml "
                    + "so it starts with the next launch");
        }
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
        WglContext openglContext = null;
        try {
            openglContext = new WglContext(FLAGS);
            presenter = startPresenter(GLFWNativeWin32.glfwGetWin32Window(handle), openglContext);
            window = handle;
            if (presenter instanceof D3D12Presenter d3d12 && d3d12.frameGeneration()) {
                watchMinimize(handle, d3d12);
            }
            hideFromGlVersionCheck();
            LOG.info("Perfect Presentation: DXGI swap chain ready on {} (OpenGL on a hidden context)", presenter.api());
            return handle;
        } catch (Throwable t) {
            LOG.error("Perfect Presentation: could not set up the DXGI swap chain, falling back to a normal OpenGL window", t);
            failed = true;
            presenter = null;
            if (openglContext != null) {
                openglContext.close();
            }
            glfwDestroyWindow(handle);
            glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_TRUE);
            for (int[] hint : SWALLOWED_HINTS) {
                glfwWindowHint(hint[0], hint[1]);
            }
            return create.apply(monitor);
        }
    }

    /** Tells frame generation when the window is minimized or back, before the game's next frame (any earlier callback still runs). */
    private static void watchMinimize(long handle, D3D12Presenter d3d12) {
        GLFWWindowIconifyCallback[] previous = new GLFWWindowIconifyCallback[1];
        previous[0] = glfwSetWindowIconifyCallback(handle, (w, iconified) -> {
            if (previous[0] != null) {
                previous[0].invoke(w, iconified);
            }
            d3d12.minimizedChanged(iconified);
        });
    }

    /** Direct3D 12 when presenter=dx12 and it starts, else Direct3D 11. */
    private static Presenter startPresenter(long hwnd, WglContext openglContext) {
        String api = DisplaySettings.getString(DisplaySettings.PRESENTER, "dx11").toLowerCase(Locale.ROOT);
        if (api.equals("dx12")) {
            try {
                return new D3D12Presenter(hwnd, openglContext, FMLPaths.GAMEDIR.get().resolve(NATIVES_FOLDER),
                        DisplaySettings.get(DisplaySettings.PRESENT_CHECK));
            } catch (Throwable t) {
                LOG.warn("Perfect Presentation: DirectX 12 could not start, using DirectX 11", t);
                dx12FellBack = true;
            }
        } else if (!api.equals("dx11")) {
            LOG.warn("Unknown {}={} in {}, using dx11 (choose dx11 or dx12)", DisplaySettings.PRESENTER, api, DisplaySettings.FILE);
        }
        return new InteropContext(hwnd, openglContext);
    }

    /** "DirectX 11" or "DirectX 12" while Perfect Presentation runs, else null. */
    public static String api() {
        return active() && presenter != null ? presenter.api() : null;
    }

    /** True while Perfect Presentation shows the game through DirectX 12. */
    public static boolean directX12() {
        return active() && presenter instanceof D3D12Presenter;
    }

    /**
     * True while the DirectX 12 presenter runs with Streamline loaded (DLSS Frame Generation's backend, on or off, see
     * FrameGen), so Reflex goes through it.
     */
    public static boolean streamline() {
        return active() && presenter instanceof D3D12Presenter d3d12 && d3d12.streamline();
    }

    /** An export of the DirectX 12 presenter's cg_present.dll; throws while it doesn't run. */
    public static long presenterSymbol(String name) {
        if (!(active() && presenter instanceof D3D12Presenter d3d12)) {
            throw new IllegalStateException("the DirectX 12 presenter isn't running");
        }
        return d3d12.symbol(name);
    }

    /** True if presenter=dx12 at launch but DirectX 12 could not start, so Perfect Presentation runs on DirectX 11. */
    public static boolean dx12FellBack() {
        return active() && dx12FellBack;
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
        presenter.makeCurrent();
        return true;
    }

    // --- Window ---

    public static boolean swapInterval(int interval) {
        if (!active()) {
            return false;
        }
        presenter.setSyncInterval(interval);
        return true;
    }

    /** The DirectX device showing the game (ID3D11Device* or ID3D12Device*), 0 while Perfect Presentation is off. */
    public static long d3dDevice() {
        return active() && presenter != null ? presenter.device() : 0L;
    }

    public static void close() {
        if (active() && presenter != null) {
            presenter.close();
            presenter = null;
        }
    }

    // --- RenderSystem.flipFrame ---

    public static boolean swapBuffers() {
        if (!active()) {
            return false;
        }
        dev.creategalore.bridge.latency.FrameLatency.presentStart();
        presenter.present();
        dev.creategalore.bridge.latency.FrameLatency.presentEnd();
        return true;
    }

    // --- Minecraft.runTick / resizeDisplay ---

    public static void waitForSwapChain() {
        if (active()) {
            presenter.waitForSwapChainSignal();
        }
    }

    public static boolean blitToSwapChain(int colorTexture) {
        if (!active()) {
            return false;
        }
        presenter.blitToSwapChain(colorTexture);
        return true;
    }

    // --- Window.setMode / onResize: what changes the window, for the log ---

    /** How long after a mode change the window's resizes are logged. */
    private static final long WATCH_NS = 3_000_000_000L;
    private static final int WATCHED_RESIZES = 6;
    private static final int SCREEN_SIZED_LINES = 10;
    private static long watchUntil;
    private static int watchedResizes;
    private static int screenSizedLines;

    /** The monitor the window covers in borderless full screen, 0 while it is a window. */
    private static long borderlessMonitor;

    /**
     * The monitor the window covers in borderless full screen, else 0. Window.setMode keeps the window's place and size
     * for leaving full screen only while the window is on no monitor, and the borderless window never is: asked again
     * while in full screen (a new full screen resolution), it kept the screen's size as the window's.
     */
    public static long borderlessMonitor() {
        return active() ? borderlessMonitor : 0L;
    }

    /** The window just turned into borderless full screen or back into a window (Window.setMode), and who asked. */
    public static void modeChanged(long handle, long monitor, boolean fullscreen, int x, int y, int width, int height) {
        borderlessMonitor = fullscreen ? monitor : 0L;
        LOG.info("Perfect Presentation: {} {}x{} at ({}, {}){}; from: {}", fullscreen ? "borderless full screen," : "a window,",
                width, height, x, y, glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) != 0 ? ", maximized" : "", caller());
        watchUntil = System.nanoTime() + WATCH_NS;
        watchedResizes = 0;
    }

    /**
     * The window Window.setMode brings back after full screen ({x, y, width, height}, the picture's size), made to fit
     * the work area of the screen it was on, with its title bar. A window as big as the screen (the game started in
     * full screen at the screen's resolution) came back with its title bar above the screen and its picture over all
     * of it, so leaving full screen looked like nothing happened. Call with the window decorated again.
     */
    public static int[] fitWindow(long handle, int x, int y, int width, int height) {
        long monitor = borderlessMonitor != 0L ? borderlessMonitor : glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return new int[] {x, y, width, height};
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var area = stack.mallocInt(4);
            glfwGetMonitorWorkarea(monitor, area.slice(0, 1), area.slice(1, 1), area.slice(2, 1), area.slice(3, 1));
            var frame = stack.mallocInt(4);
            glfwGetWindowFrameSize(handle, frame.slice(0, 1), frame.slice(1, 1), frame.slice(2, 1), frame.slice(3, 1));
            int left = frame.get(0), top = frame.get(1), right = frame.get(2), bottom = frame.get(3);
            int maxWidth = area.get(2) - left - right;
            int maxHeight = area.get(3) - top - bottom;
            if (maxWidth <= 0 || maxHeight <= 0 || width <= maxWidth && height <= maxHeight) {
                return new int[] {x, y, width, height};
            }
            int fitWidth = Math.min(width, maxWidth);
            int fitHeight = Math.min(height, maxHeight);
            int fitX = Math.max(area.get(0) + left, Math.min(x, area.get(0) + area.get(2) - right - fitWidth));
            int fitY = Math.max(area.get(1) + top, Math.min(y, area.get(1) + area.get(3) - bottom - fitHeight));
            LOG.info("Perfect Presentation: the window to come back after full screen, {}x{}, is bigger than the screen's"
                    + " work area; it comes back as {}x{} at ({}, {})", width, height, fitWidth, fitHeight, fitX, fitY);
            return new int[] {fitX, fitY, fitWidth, fitHeight};
        }
    }

    /**
     * The window's size changed (Window.onResize, GLFW's size callback). Logged for a few seconds after a mode change,
     * and whenever a window the game has as a window covers a whole screen: where it came from, and whether DXGI holds
     * the swap chain in exclusive full screen.
     */
    public static void windowResized(long handle, boolean fullscreen, int width, int height) {
        if (!active() || width <= 0 || height <= 0) {
            return;
        }
        boolean watched = System.nanoTime() < watchUntil && watchedResizes < WATCHED_RESIZES;
        boolean screenSized = !fullscreen && coversScreen(width, height) && screenSizedLines < SCREEN_SIZED_LINES;
        if (!watched && !screenSized) {
            return;
        }
        if (watched) {
            watchedResizes++;
        }
        int exclusive = presenter instanceof D3D12Presenter d3d12 ? d3d12.exclusiveFullscreen() : -1;
        String state = String.format("the game has it %s%s%s", fullscreen ? "in full screen" : "as a window",
                glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) != 0 ? ", maximized" : "",
                exclusive == 1 ? ", DXGI in exclusive full screen" : exclusive == 0 ? ", DXGI windowed" : "");
        if (screenSized) {
            screenSizedLines++;
            LOG.info("Perfect Presentation: the window became {}x{}, a whole screen, though {}; from: {}", width, height, state, caller());
        } else {
            LOG.info("Perfect Presentation: the window is now {}x{}, {}; from: {}", width, height, state, caller());
        }
    }

    /** True if {@code width} x {@code height} is the size of one of the screens' current modes. */
    private static boolean coversScreen(int width, int height) {
        PointerBuffer monitors = glfwGetMonitors();
        for (int i = 0; monitors != null && i < monitors.limit(); i++) {
            GLFWVidMode mode = glfwGetVideoMode(monitors.get(i));
            if (mode != null && mode.width() == width && mode.height() == height) {
                return true;
            }
        }
        return false;
    }

    /** The callers above the Bridge's own frames (GLFW's included: glfwPollEvents is Windows, glfwSetWindowMonitor the game). */
    private static String caller() {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String type = element.getClassName();
            if (type.startsWith("java.") || type.startsWith("jdk.") || type.startsWith("org.lwjgl.system")
                    || type.equals(Presentation.class.getName())) {
                continue;
            }
            text.append(shown == 0 ? "" : " < ").append(type.substring(type.lastIndexOf('.') + 1)).append('.').append(element.getMethodName());
            if (++shown == 12) {
                break;
            }
        }
        return text.toString();
    }

    public static void resize() {
        if (!active()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            presenter.resizeSwapChain(w.get(0), h.get(0));
        }
    }
}

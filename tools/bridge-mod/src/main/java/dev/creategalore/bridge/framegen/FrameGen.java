package dev.creategalore.bridge.framegen;

import static org.lwjgl.glfw.GLFW.GLFW_ICONIFIED;
import static org.lwjgl.glfw.GLFW.glfwGetWindowAttrib;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.GL_R8;
import static org.lwjgl.opengl.GL30C.GL_RG16F;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.creategalore.bridge.DisplaySettings;
import dev.creategalore.bridge.dlss.Dlss;
import dev.creategalore.bridge.hud.HudSplit;
import dev.creategalore.bridge.latency.FrameLatency;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frame generation, TESTING ONLY (keys in the display settings, nothing on the settings screen): NVIDIA DLSS Frame
 * Generation through Streamline, in Perfect Presentation's DirectX 12 presenter (cg_present.dll's cg_fg_* calls and
 * tools/bridge-mod/native/cg_streamline.cpp). Each frame, once DLSS or FSR ran and the HUD is drawn, the presenter has
 * {@link FgCapture} write the frame's inputs (the world without the HUD, the HUD's coverage, depth and motion vectors)
 * into textures shared with DirectX 12, hands over the camera ({@link FgCamera}), and Streamline shows generated frames
 * between the rendered ones from Present on. Reflex then runs through Streamline (see FrameLatency).
 *
 * Made for AMD's FSR frame generation to follow: the inputs come from whichever upscaler ran ({@link #upscaled}) in one
 * form for any backend, so another backend in cg_present.dll takes them as they are, beside DLSS or FSR upscaling.
 *
 * It generates only while that can work, as NVIDIA asks: a world that DLSS or FSR drew this frame, its copy without the
 * HUD (HudSplit; with the HUD hidden by F1 the frame itself), no screen open but chat, the window neither minimized nor
 * resizing (the frame before a resize goes out without, and a few after it), and Streamline reporting no problem and
 * a window at least its minimum size. Otherwise the frame goes out as it is.
 *
 * Keys (config/create_galore_bridge-display.properties, not written there): frameGen=dlss turns it on (at the next
 * start, with perfectPresentation=true and presenter=dx12; nvngx_dlssg.dll beside nvngx_dlss.dll, found in NVIDIA's
 * folder by itself if it isn't); frameGenFrames frames generated per rendered one (1); frameGenUi=false hands the HUD
 * over as part of the picture; frameGenStreamline a folder of Streamline's development DLLs instead of the shipped
 * ones; frameGenVerbose=true a verbose Streamline log (create-galore-dlss/streamline-logs). frameGenView=hudless,
 * alpha, depth or motion shows that input instead of the game, and frameGenCheck=true checks the inputs and the
 * camera every 5 seconds ({@link FgCheck}); both work without the presenter, on any graphics card.
 */
public final class FrameGen {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Streamline's DLLs and licences, in the mod jar; unpacked into create-galore-dlss/streamline-VERSION. */
    private static final String STREAMLINE_VERSION = "2.14.16";
    private static final String STREAMLINE_RESOURCES = "/natives/windows-x64/streamline/";
    private static final String[] STREAMLINE_FILES = {"sl.interposer.dll", "sl.common.dll", "sl.dlss_g.dll", "sl.reflex.dll",
            "sl.pcl.dll", "license.txt", "reflex.license.txt", "3rd-party-licenses.md"};
    /** Minecraft's near plane (GameRenderer). */
    private static final float NEAR = 0.05f;
    /** Frames after a resize without frame generation (NVIDIA: off around window changes). */
    private static final int AFTER_RESIZE = 3;
    /** Frames without frame generation after Streamline reported a problem, before trying again. */
    private static final int AFTER_PROBLEM = 300;
    /** Failed calls in a row before frame generation stays off for the game. */
    private static final int FAILURES_LIMIT = 10;
    private static final long CHECK_EVERY = 5_000_000_000L;

    // cg_framegen.h
    public static final int BACKEND_DLSSG = 1;
    private static final int SL_DEVELOPMENT = 1;
    private static final int SL_VERBOSE = 2;
    public static final int FRAME_GENERATE = 1;
    public static final int FRAME_RESET = 2;
    public static final int FRAME_CAMERA = 4;
    public static final int FRAME_UI = 8;
    /** The inputs' OpenGL formats, in FgInput order: hudless, UI alpha, depth, motion. */
    public static final int[] INPUT_FORMATS = {GL_RGBA8, GL_R8, GL_R32F, GL_RG16F};
    public static final int INPUTS = INPUT_FORMATS.length;
    // sl::DLSSGStatus bits
    private static final String[] STATUS_NAMES = {"resolution too low", "Reflex not detected", "HDR format not supported",
            "common constants invalid", "GetCurrentBackBufferIndex not called", "reserved 5"};

    /** What the presenter needs to start frame generation (cg_fg_prepare). */
    public record Start(int backend, Path pluginFolder, Path dllFolder, Path logFolder, int flags) {}

    /** One frame for the presenter: cg_fg_frame's arguments, and the capture's sources. */
    public static final class Frame {
        public int frameIndex;
        public int flags;
        public int renderWidth;
        public int renderHeight;
        public int framesToGenerate;
        /** The camera's floats (0 without FRAME_CAMERA). */
        public long camera;
        int hudless;
        boolean noHud;
        int depth;
        int motion;

        public boolean generate() {
            return (flags & FRAME_GENERATE) != 0;
        }
    }

    private static final Frame FRAME = new Frame();
    private static final FgCamera camera = new FgCamera();

    // The session.
    private static boolean running;
    private static String broken;
    private static FgCapture capture;

    // Settings, read again when the display settings change.
    private static int generation = -1;
    private static int framesWanted = 1;
    private static boolean ui = true;
    private static int view;
    private static boolean check;

    // This frame's inputs, from the upscaler.
    private static long upscaledFrame = Long.MIN_VALUE;
    private static int motionTexture;
    private static int depthTexture;
    private static int inputWidth;
    private static int inputHeight;
    private static int renderWidth;
    private static int renderHeight;
    private static boolean upscalerReset;

    // Generating.
    private static boolean generating;
    private static String lastOff = "";
    private static int afterResize;
    private static int afterProblem;
    private static long status;
    private static long lastStatus;
    private static long minSize;
    private static long maxGenerated;
    private static boolean confirmed;
    private static boolean vsyncWarned;
    private static int failures;

    // Testing without the presenter: the inputs in plain OpenGL textures.
    private static final int[] local = new int[INPUTS];
    private static int localWidth;
    private static int localHeight;
    private static FgView fgView;
    private static long nextCheck;
    private static boolean noInputsLogged;

    private FrameGen() {}

    // --- the presenter's start ---

    /**
     * At the presenter's start, before its swap chain: unpacks Streamline and finds nvngx_dlssg.dll. Null when frame
     * generation is off, or can't start (then logged).
     */
    public static Start start() {
        String mode = DisplaySettings.getString(DisplaySettings.FRAME_GEN, "off").toLowerCase(Locale.ROOT);
        if (mode.equals("off") || mode.isEmpty() || mode.equals("false")) {
            return null;
        }
        if (!mode.equals("dlss")) {
            LOG.warn("Frame generation: unknown {}={}, off (dlss or off)", DisplaySettings.FRAME_GEN, mode);
            return null;
        }
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve(Dlss.FOLDER);
            String development = DisplaySettings.getString(DisplaySettings.FRAME_GEN_STREAMLINE, "");
            Path plugins;
            int flags = DisplaySettings.get(DisplaySettings.FRAME_GEN_VERBOSE) ? SL_VERBOSE : 0;
            if (!development.isEmpty()) {
                plugins = Path.of(development);
                flags |= SL_DEVELOPMENT | SL_VERBOSE;
                LOG.warn("Frame generation: Streamline's development DLLs from {} (not signature checked)", plugins);
            } else {
                plugins = unpackStreamline(root.resolve("streamline-" + STREAMLINE_VERSION));
            }
            Path logs = root.resolve("streamline-logs");
            Files.createDirectories(logs);
            LOG.info("Frame generation: NVIDIA DLSS Frame Generation through Streamline {} from {}; {}", STREAMLINE_VERSION,
                    plugins, Dlss.readyFrameGenerationDll());
            return new Start(BACKEND_DLSSG, plugins, Dlss.dllFolder(), logs, flags);
        } catch (IOException | RuntimeException e) {
            unavailable("Streamline could not be readied: " + e);
            return null;
        }
    }

    /** Writes the shipped Streamline files into {@code folder} unless the same ones are there. */
    private static Path unpackStreamline(Path folder) throws IOException {
        Files.createDirectories(folder);
        for (String name : STREAMLINE_FILES) {
            byte[] bytes;
            try (InputStream in = FrameGen.class.getResourceAsStream(STREAMLINE_RESOURCES + name)) {
                if (in == null) {
                    throw new IOException("the mod jar has no " + STREAMLINE_RESOURCES + name);
                }
                bytes = in.readAllBytes();
            }
            Path file = folder.resolve(name);
            if (Files.isRegularFile(file) && Files.size(file) == bytes.length && Arrays.equals(Files.readAllBytes(file), bytes)) {
                continue;
            }
            Path temp = Files.createTempFile(folder, name, ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return folder;
    }

    /** Frame generation was asked for but can't run (the presenter goes on without it). */
    public static void unavailable(String reason) {
        LOG.warn("Frame generation: off, {}", reason);
    }

    /** The presenter runs with frame generation. */
    public static void started(String adapter) {
        running = true;
        LOG.info("Frame generation: ready in the DirectX 12 presenter on {}; Reflex goes through Streamline", adapter);
    }

    /** True while the presenter runs with frame generation (Streamline loaded). */
    public static boolean running() {
        return running;
    }

    // --- the frame ---

    private static void settings() {
        if (generation == DisplaySettings.generation()) {
            return;
        }
        generation = DisplaySettings.generation();
        framesWanted = Math.max(1, parseInt(DisplaySettings.getString(DisplaySettings.FRAME_GEN_FRAMES, "1"), 1));
        ui = !DisplaySettings.getString(DisplaySettings.FRAME_GEN_UI, "true").equalsIgnoreCase("false");
        String viewName = DisplaySettings.getString(DisplaySettings.FRAME_GEN_VIEW, "off").toLowerCase(Locale.ROOT);
        int newView = Math.max(0, Arrays.asList(FgView.NAMES).indexOf(viewName));
        boolean newCheck = DisplaySettings.get(DisplaySettings.FRAME_GEN_CHECK);
        if (newView != view || newCheck != check) {
            LOG.info("Frame generation: view {}, checks {}", FgView.NAMES[newView], newCheck ? "every 5 s" : "off");
        }
        view = newView;
        check = newCheck;
        nextCheck = 0L;
        noInputsLogged = false;
    }

    /** True while anything needs the inputs: frame generation, or its test view or check. */
    private static boolean wanted() {
        settings();
        return running && broken == null || view != 0 || check;
    }

    /** For HudSplit: frame generation needs the world copy without the HUD. */
    public static boolean wantsHudSplit() {
        return wanted();
    }

    /**
     * DLSS or FSR just ran on the pack's frame (Dlss.afterPass): its motion and depth images (the window's size, the
     * frame in their lower left render-sized corner), the jitter it rendered with (render pixels, +y up), whether
     * nothing connects this frame with the last, and the time since it.
     */
    public static void upscaled(int motion, int depth, int width, int height, int renderW, int renderH, float jitterX,
                                float jitterY, boolean reset, float frameTimeMs) {
        if (!wanted()) {
            return;
        }
        long id = FrameLatency.frameId();
        upscaledFrame = id;
        motionTexture = motion;
        depthTexture = depth;
        inputWidth = width;
        inputHeight = height;
        renderWidth = renderW;
        renderHeight = renderH;
        upscalerReset = reset;
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 position = minecraft.gameRenderer.getMainCamera().getPosition();
        camera.update(CapturedRenderingState.INSTANCE.getGbufferModelView(), CapturedRenderingState.INSTANCE.getGbufferProjection(),
                position.x, position.y, position.z, NEAR, minecraft.gameRenderer.getDepthFar(), jitterX, jitterY, renderW, renderH,
                frameTimeMs, id);
    }

    /**
     * The presenter, every frame before its draw: whether this frame generates, and cg_fg_frame's arguments.
     * {@code width} x {@code height} is the frame's (and the inputs') size; {@code resizing}: a swap chain resize waits
     * for this frame to go out.
     */
    public static Frame frame(int width, int height, boolean resizing) {
        settings();
        Minecraft minecraft = Minecraft.getInstance();
        Frame frame = FRAME;
        long id = FrameLatency.frameId();
        boolean inputs = upscaledFrame == id && inputWidth == width && inputHeight == height;
        int hudless = HudSplit.hudless();
        boolean noHud = hudless == 0 && minecraft.options.hideGui && minecraft.screen == null;
        frame.frameIndex = (int) id;
        frame.flags = inputs ? FRAME_CAMERA : 0;
        frame.camera = inputs ? camera.address() : 0L;
        frame.renderWidth = inputs ? renderWidth : width;
        frame.renderHeight = inputs ? renderHeight : height;
        frame.framesToGenerate = Math.max(1, maxGenerated > 0 ? Math.min(framesWanted, (int) maxGenerated) : framesWanted);
        frame.hudless = hudless;
        frame.noHud = noHud;
        frame.depth = depthTexture;
        frame.motion = motionTexture;
        String off = offReason(minecraft, inputs, hudless != 0 || noHud, width, height, resizing);
        if (off == null) {
            frame.flags |= FRAME_GENERATE | (ui ? FRAME_UI : 0);
            if (!generating || upscalerReset || !camera.continuous()) {
                frame.flags |= FRAME_RESET;
            }
            if (!generating) {
                LOG.info("Frame generation: on, {} generated per rendered frame, the HUD {}", frame.framesToGenerate,
                        ui ? "apart" : "in the picture");
            }
        } else if (generating || !off.equals(lastOff)) {
            LOG.info("Frame generation: off, {}", off);
        }
        generating = off == null;
        lastOff = off == null ? "" : off;
        if (afterResize > 0) {
            afterResize--;
        }
        if (afterProblem > 0) {
            afterProblem--;
        }
        return frame;
    }

    private static String offReason(Minecraft minecraft, boolean inputs, boolean hudless, int width, int height, boolean resizing) {
        if (broken != null) {
            return "stopped for this game: " + broken;
        }
        if (minecraft.level == null) {
            return "no world";
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
            return "a screen is open";
        }
        if (width <= 0 || height <= 0 || glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW_ICONIFIED) != 0) {
            return "the window is minimized";
        }
        if (resizing || afterResize > 0) {
            return "the window is resizing";
        }
        if (!inputs) {
            return "DLSS or FSR didn't run this frame (DLAA on, a shader pack made for it, no debug view)";
        }
        if (!hudless) {
            return "no copy of the world without the HUD this frame";
        }
        if (afterProblem > 0) {
            return "Streamline reported " + statusText(lastStatus);
        }
        if (minSize > 0 && Math.min(width, height) < minSize) {
            return "the window is below frame generation's smallest size, " + minSize + " pixels";
        }
        return null;
    }

    /** The presenter, between its copy and its signal to DirectX 12: writes the inputs into the frame's slot. False if it failed. */
    public static boolean capture(Frame frame, int[] targets, int glTexture, int width, int height) {
        try {
            if (capture == null) {
                capture = new FgCapture();
            }
            capture.run(targets, frame.noHud ? glTexture : frame.hudless, glTexture, frame.depth, frame.motion, width, height,
                    frame.renderWidth, frame.renderHeight, frame.noHud);
            return true;
        } catch (RuntimeException e) {
            stop("the inputs could not be written: " + e.getMessage());
            return false;
        }
    }

    /** The presenter's slot textures are about to be deleted. */
    public static void forget(int[] targets) {
        if (capture != null && targets[0] != 0) {
            capture.forget(targets);
        }
    }

    /** cg_fg_frame's result: failures in a row turn frame generation off for the game. */
    public static void frameSent(boolean ok, String error) {
        if (ok) {
            failures = 0;
            return;
        }
        failures++;
        if (failures == 1) {
            LOG.warn("Frame generation: {}", error);
        }
        if (failures >= FAILURES_LIMIT && broken == null) {
            stop(FAILURES_LIMIT + " failed frames in a row, the last: " + error);
        }
    }

    /** Off for the rest of the game. */
    public static void stop(String reason) {
        if (broken == null) {
            broken = reason;
            LOG.error("Frame generation: off for this game, {}", reason);
        }
    }

    /** True if this frame asked for generated frames (the swap chain then resizes after the next frame). */
    public static boolean generating() {
        return generating;
    }

    /** The swap chain resized: no frame generation for a few frames. */
    public static void resized() {
        afterResize = AFTER_RESIZE;
    }

    /**
     * The presenter, after each Present: cg_fg_state's numbers (generating, status, frames presented, minimum size,
     * most frames generated, vsync supported, errors) and the sync interval it presented with.
     */
    public static void presented(long[] state, int syncInterval) {
        status = state[1];
        minSize = state[3];
        maxGenerated = state[4];
        if (!generating) {
            return;
        }
        if (status != 0) {
            if (status != lastStatus) {
                LOG.warn("Frame generation: Streamline reports {} (status {}); off for {} frames, then tried again", statusText(status),
                        status, AFTER_PROBLEM);
            }
            lastStatus = status;
            afterProblem = AFTER_PROBLEM;
        } else if (state[0] != 0 && state[2] > 1 && !confirmed) {
            confirmed = true;
            LOG.info("Frame generation: running, {} frames shown per rendered one (most it can generate: {}, smallest size {})",
                    state[2], maxGenerated, minSize);
        }
        if (syncInterval > 0 && state[5] == 0 && !vsyncWarned) {
            vsyncWarned = true;
            LOG.warn("Frame generation: vsync is on, which this frame generation says it doesn't support");
        }
    }

    private static String statusText(long status) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < STATUS_NAMES.length; i++) {
            if ((status & (1L << i)) != 0) {
                names.add(STATUS_NAMES[i]);
            }
        }
        return names.isEmpty() ? "status " + status : String.join(", ", names);
    }

    /** A line of the native side's log, its level letter (E, W, I) first. */
    public static void nativeLog(String line) {
        if (line.isEmpty()) {
            return;
        }
        String text = line.substring(1);
        switch (line.charAt(0)) {
            case 'E' -> LOG.error("Frame generation (native): {}", text);
            case 'W' -> LOG.warn("Frame generation (native): {}", text);
            default -> LOG.info("Frame generation (native): {}", text);
        }
    }

    // --- testing: frameGenView and frameGenCheck ---

    /**
     * Minecraft.runTick, just before the frame goes to the window (after HudSplit's frame end): the test view and check
     * capture the inputs into plain OpenGL textures, so they work without the presenter.
     */
    public static void frameEnd() {
        settings();
        if (view == 0 && !check) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        int width = main.width;
        int height = main.height;
        boolean inputs = upscaledFrame == FrameLatency.frameId() && inputWidth == width && inputHeight == height;
        int hudless = HudSplit.hudless();
        boolean noHud = hudless == 0 && minecraft.options.hideGui && minecraft.screen == null;
        if (!inputs || hudless == 0 && !noHud) {
            if (!noInputsLogged && minecraft.level != null && minecraft.screen == null) {
                noInputsLogged = true;
                LOG.info("Frame generation test: no inputs this frame ({})", !inputs ? "DLSS or FSR didn't run" : "no HUD split copy");
            }
            return;
        }
        try {
            if (capture == null) {
                capture = new FgCapture();
            }
            if (localWidth != width || localHeight != height) {
                makeLocal(width, height);
            }
            int source = noHud ? main.getColorTextureId() : hudless;
            capture.run(local, source, main.getColorTextureId(), depthTexture, motionTexture, width, height, renderWidth, renderHeight,
                    noHud);
            long now = System.nanoTime();
            if (check && now - nextCheck >= 0L) {
                nextCheck = now + CHECK_EVERY;
                FgCheck.run(local, source, main.getColorTextureId(), depthTexture, motionTexture, width, height, renderWidth,
                        renderHeight, noHud, camera, true);
            }
            if (view != 0) {
                if (fgView == null) {
                    fgView = new FgView();
                }
                fgView.draw(view, local[view - 1], main.frameBufferId, width, height);
            }
        } catch (RuntimeException e) {
            LOG.warn("Frame generation test: off, {}", e.toString());
            view = 0;
            check = false;
        }
    }

    private static void makeLocal(int width, int height) {
        if (capture != null && local[0] != 0) {
            capture.forget(local);
        }
        for (int i = 0; i < INPUTS; i++) {
            if (local[i] != 0) {
                glDeleteTextures(local[i]);
            }
            local[i] = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorage2D(local[i], 1, INPUT_FORMATS[i], width, height);
            glTextureParameteri(local[i], GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(local[i], GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        localWidth = width;
        localHeight = height;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Frame generation: {} is not a number, using {}", value, fallback);
            return fallback;
        }
    }

    /** For the log and tests: what it does now. */
    public static String state() {
        if (!running) {
            return "off";
        }
        return broken != null ? "stopped: " + broken : generating ? "generating" : "off: " + Objects.requireNonNullElse(lastOff, "");
    }
}

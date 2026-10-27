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
 * Frame generation (Display tab, "Frame generation"), in Perfect Presentation's DirectX 12 presenter (cg_present.dll's
 * cg_fg_* calls): NVIDIA DLSS Frame Generation through Streamline on the cards Streamline says it runs on
 * (tools/bridge-mod/native/cg_streamline.cpp), or AMD FSR 3.1 frame generation, EXPERIMENTAL, through AMD's FidelityFX
 * SDK on any card (cg_ffx.cpp). Each frame, once DLSS or FSR ran and the HUD is drawn, the presenter has {@link FgCapture} write the
 * frame's inputs (the world without the HUD, the HUD's coverage, depth and motion vectors) into textures shared with
 * DirectX 12, hands over the camera ({@link FgCamera}), and the backend shows generated frames between the rendered
 * ones from Present on. The inputs come from whichever upscaler ran ({@link #upscaled}) in one form for either backend,
 * so either runs beside DLSS or FSR upscaling. With Streamline loaded, Reflex runs through it (see FrameLatency).
 *
 * One backend loads whenever the presenter starts on DirectX 12 ({@link #start}), so frame generation turns on and off
 * at once: the presenter makes its swap chain again each time ({@link #on()}, {@link #switched}). Picking the other
 * backend needs a restart.
 *
 * It generates only while that can work, as NVIDIA asks: a world that DLSS or FSR drew this frame, its copy without the
 * HUD (HudSplit; with the HUD hidden by F1 the frame itself), no screen open but chat, the first frames of a new shader
 * pipeline past, the window neither minimized, hidden nor resizing (the frame before a resize goes out without, and a
 * few after it), and the backend reporting no problem and a window at least its minimum size. Otherwise the frame goes
 * out as it is.
 *
 * Keys (config/create_galore_bridge-display.properties): frameGen=dlss or fsr turns it on (with perfectPresentation=true
 * and presenter=dx12, which the settings screen sets with it; DLSS needs nvngx_dlssg.dll beside nvngx_dlss.dll, the
 * player's own or copied from NVIDIA's folder by the screen's Find on this PC); frameGenType, the backend loaded while
 * it is off (the screen writes the last one picked). For testing, not written there: frameGenFrames
 * frames generated per rendered one (1); frameGenUi=false hands the HUD over as part of the picture; frameGenStreamline
 * a folder of Streamline's development DLLs instead of the shipped ones; frameGenVerbose=true a verbose Streamline log
 * (create-galore-dlss/streamline-logs), or AMD's debug checks and every message of theirs in the game log. frameGenView=hudless, alpha, depth or motion shows that input instead of the
 * game, and frameGenCheck=true checks the inputs and the camera every 5 seconds ({@link FgCheck}); both work without the
 * presenter, on any graphics card. frameGenStreamlineSays=no takes Streamline's yes as a no (AMD's loads in its place).
 *
 * Which cards run DLSS Frame Generation is Streamline's call (slIsFeatureSupported, at every start on DirectX 12), as
 * NVIDIA's guides do it: the card reports what it supports, so new card generations need no Bridge update.
 */
public final class FrameGen {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Streamline's DLLs and licences, in the mod jar; unpacked into create-galore-dlss/streamline-VERSION. */
    private static final String STREAMLINE_VERSION = "2.14.16";
    private static final String STREAMLINE_RESOURCES = "/natives/windows-x64/streamline/";
    private static final String[] STREAMLINE_FILES = {"sl.interposer.dll", "sl.common.dll", "sl.dlss_g.dll", "sl.reflex.dll",
            "sl.pcl.dll", "license.txt", "reflex.license.txt", "3rd-party-licenses.md"};
    /** AMD's FidelityFX frame generation DLLs and licence, in the mod jar; unpacked into create-galore-dlss/fidelityfx-VERSION. */
    private static final String FIDELITYFX_VERSION = "2.0.0";
    private static final String FIDELITYFX_RESOURCES = "/natives/windows-x64/fidelityfx/";
    private static final String[] FIDELITYFX_FILES = {"amd_fidelityfx_loader_dx12.dll", "amd_fidelityfx_framegeneration_dx12.dll",
            "license.md"};
    /** Minecraft's near plane (GameRenderer). */
    private static final float NEAR = 0.05f;
    /** Frames after a resize or a restored window without frame generation (NVIDIA: off around window changes). */
    private static final int AFTER_RESIZE = 3;
    /**
     * Upscaled frames after the shader pack's pipeline was made (loaded, reloaded, resized) without frame generation:
     * the first ones' motion vectors don't hold yet (the pack has no last frame to reproject to).
     */
    private static final int AFTER_RELOAD = 3;
    /** Frames without frame generation after Streamline reported a problem, before trying again. */
    private static final int AFTER_PROBLEM = 300;
    /** Failed calls in a row before frame generation stays off for the game. */
    private static final int FAILURES_LIMIT = 10;
    private static final long CHECK_EVERY = 5_000_000_000L;

    // cg_framegen.h
    public static final int BACKEND_DLSSG = 1;
    public static final int BACKEND_FSR = 2;
    private static final int SL_DEVELOPMENT = 1;
    private static final int SL_VERBOSE = 2;
    private static final int FFX_VERBOSE = 2;
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
    // cg_ffx.cpp's status bits
    private static final String[] FFX_STATUS_NAMES = {"its context could not be made at this size", "a call failed"};

    /** What the presenter needs to start frame generation (cg_fg_prepare), and whether it starts on (cg_fg_enable). */
    public record Start(int backend, Path pluginFolder, Path dllFolder, Path logFolder, int flags, boolean on) {}

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
    private static boolean requested;
    /** Streamline's answer at this game's start, whether DLSS Frame Generation runs here; null before it was asked. */
    private static Boolean streamlineSays;
    private static String streamlineWhy;
    /** The backend the presenter loaded (BACKEND_DLSSG or BACKEND_FSR), 0 before. */
    private static int backend;
    private static String unavailable;
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
    /** Upscaled frames since the shader pack's pipeline was made. */
    private static int sinceReload = AFTER_RELOAD;

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
    /** The frame number the last frame went out with, so no two presents share one (see {@link #frame}). */
    private static long lastFrameIndex = Long.MIN_VALUE;
    private static int repeatedFrames;
    private static volatile boolean minimized;
    private static boolean hidden;

    // Testing without the presenter: the inputs in plain OpenGL textures.
    private static final int[] local = new int[INPUTS];
    private static int localWidth;
    private static int localHeight;
    private static FgView fgView;
    private static long nextCheck;
    private static boolean noInputsLogged;

    private FrameGen() {}

    // --- the presenter's start ---

    /** The frameGen setting: off, dlss or fsr. */
    public static String setting() {
        String mode = DisplaySettings.getString(DisplaySettings.FRAME_GEN, "off").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "dlss" -> "dlss";
            case "fsr", "fsr3" -> "fsr";
            default -> "off";
        };
    }

    /** A backend's setting value: dlss, fsr, or off for none. */
    public static String settingOf(int backend) {
        return backend == BACKEND_DLSSG ? "dlss" : backend == BACKEND_FSR ? "fsr" : "off";
    }

    /**
     * At the presenter's start on DirectX 12, before its swap chain: Streamline, NVIDIA DLSS Frame Generation's backend,
     * whichever frame generation the settings ask for. The presenter prepares it, asks it whether DLSS Frame Generation
     * runs on this card (cg_fg_supports) and hands the answer to {@link #answered}, which keeps it or names AMD's to
     * load in its place. Streamline's answer goes. Null if nothing can load.
     */
    public static Start start() {
        requested = true;
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve(Dlss.FOLDER);
            boolean verbose = DisplaySettings.get(DisplaySettings.FRAME_GEN_VERBOSE);
            String development = DisplaySettings.getString(DisplaySettings.FRAME_GEN_STREAMLINE, "");
            Path plugins;
            int flags = verbose ? SL_VERBOSE : 0;
            if (!development.isEmpty()) {
                plugins = Path.of(development);
                flags |= SL_DEVELOPMENT | SL_VERBOSE;
                LOG.warn("Frame generation: Streamline's development DLLs from {} (not signature checked)", plugins);
            } else {
                plugins = unpack(root.resolve("streamline-" + STREAMLINE_VERSION), STREAMLINE_RESOURCES, STREAMLINE_FILES);
            }
            Path logs = root.resolve("streamline-logs");
            Files.createDirectories(logs);
            backend = BACKEND_DLSSG;
            return new Start(BACKEND_DLSSG, plugins, Dlss.dllFolder(), logs, flags, setting().equals("dlss"));
        } catch (IOException | RuntimeException e) {
            return answered(null, false, "Streamline could not be readied: " + e);
        }
    }

    /**
     * Streamline's answer ({@code yes}, else {@code why} not): the start to go on with, {@code streamline} itself to keep
     * it (DLSS Frame Generation asked for, or none asked for and nvngx_dlssg.dll there), else AMD's, or null if that
     * can't be readied either. Testing: frameGenStreamlineSays=no takes a yes as no.
     */
    public static Start answered(Start streamline, boolean yes, String why) {
        if (yes && DisplaySettings.getString(DisplaySettings.FRAME_GEN_STREAMLINE_SAYS, "").equalsIgnoreCase("no")) {
            yes = false;
            why = "frameGenStreamlineSays=no in the settings file (testing)";
        }
        streamlineSays = yes;
        streamlineWhy = yes ? null : why;
        if (yes) {
            LOG.info("Frame generation: Streamline says NVIDIA DLSS Frame Generation runs on this card");
        } else {
            LOG.info("Frame generation: Streamline says NVIDIA DLSS Frame Generation can't run here: {}", why);
        }
        String mode = setting();
        String picked = !mode.equals("off") ? mode
                : DisplaySettings.getString(DisplaySettings.FRAME_GEN_TYPE, "").toLowerCase(Locale.ROOT);
        String model = Dlss.readyFrameGenerationDll();
        if (yes && streamline != null && model != null && !picked.equals("fsr")) {
            LOG.info("Frame generation: NVIDIA DLSS Frame Generation through Streamline {} from {}; nvngx_dlssg.dll {} (tested"
                    + " with {}) from {}, {}", STREAMLINE_VERSION, streamline.pluginFolder(), model,
                    Dlss.testedFrameGenerationDllVersion(), Dlss.dllFolder(), streamline.on() ? "on" : "off until turned on");
            backend = BACKEND_DLSSG;
            return streamline;
        }
        if (picked.equals("dlss")) {
            LOG.info("Frame generation: NVIDIA's can't run ({}); AMD's loads instead, off", !yes ? "Streamline says no"
                    : "no nvngx_dlssg.dll, NVIDIA's frame generation model, in " + Dlss.dllFolder());
        }
        boolean on = mode.equals("fsr");
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve(Dlss.FOLDER);
            boolean verbose = DisplaySettings.get(DisplaySettings.FRAME_GEN_VERBOSE);
            Path folder = unpack(root.resolve("fidelityfx-" + FIDELITYFX_VERSION), FIDELITYFX_RESOURCES, FIDELITYFX_FILES);
            LOG.info("Frame generation: AMD FSR 3.1 frame generation (FidelityFX SDK {}) from {}, {}", FIDELITYFX_VERSION, folder,
                    on ? "on" : "off until turned on");
            backend = BACKEND_FSR;
            return new Start(BACKEND_FSR, folder, folder, folder, verbose ? FFX_VERBOSE : 0, on);
        } catch (IOException | RuntimeException e) {
            unavailable("FidelityFX could not be readied: " + e);
            return null;
        }
    }

    /**
     * False once Streamline said this game run that DLSS Frame Generation can't run here ({@link #streamlineWhy()} says
     * why); true if it said it can, or wasn't asked (the presenter didn't start on DirectX 12): it is asked then.
     */
    public static boolean streamlineAllows() {
        return streamlineSays == null || streamlineSays;
    }

    /** Why Streamline said no, null if it didn't. */
    public static String streamlineWhy() {
        return streamlineWhy;
    }

    /** Writes the shipped {@code files} from {@code resources} into {@code folder} unless the same ones are there. */
    private static Path unpack(Path folder, String resources, String[] files) throws IOException {
        Files.createDirectories(folder);
        for (String name : files) {
            byte[] bytes;
            try (InputStream in = FrameGen.class.getResourceAsStream(resources + name)) {
                if (in == null) {
                    throw new IOException("the mod jar has no " + resources + name);
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
        unavailable = reason;
        backend = 0;
        LOG.warn("Frame generation: off, {}", reason);
    }

    /** The presenter runs with frame generation's backend, {@code on} or off. */
    public static void started(String adapter, boolean on) {
        running = true;
        LOG.info("Frame generation: ready in the DirectX 12 presenter on {}, {}{}", adapter, on ? "on" : "off",
                backend == BACKEND_DLSSG ? "; Reflex goes through Streamline" : "");
    }

    /** True while the presenter runs with frame generation's backend loaded (on or off). */
    public static boolean running() {
        return running;
    }

    /** The backend loaded (BACKEND_DLSSG or BACKEND_FSR) while {@link #running()}, else 0. */
    public static int backend() {
        return running ? backend : 0;
    }

    /** True while frameGen asks for the backend that runs, and it hasn't stopped: the presenter then has it on. */
    public static boolean on() {
        return running && broken == null && setting().equals(settingOf(backend));
    }

    /** The presenter turned frame generation on or off (its swap chain made again). */
    public static void switched(boolean on) {
        generating = false;
        confirmed = false;
        lastOff = "";
        afterResize = AFTER_RESIZE;
        LOG.info("Frame generation: turned {}", on ? "on" : "off");
    }

    /** True if the presenter was asked for frame generation at its start. */
    public static boolean requested() {
        return requested;
    }

    /** Why frame generation didn't start, or stopped for this game; null if neither. */
    public static String problem() {
        return broken != null ? broken : running ? null : unavailable;
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
        return on() || view != 0 || check;
    }

    /** For HudSplit: frame generation needs the world copy without the HUD. */
    public static boolean wantsHudSplit() {
        return wanted();
    }

    /** Dlss: the shader pack's pipeline was made; its first frames' motion vectors don't hold yet. */
    public static void pipelineCreated() {
        sinceReload = 0;
    }

    /**
     * The window was minimized or brought back (GLFW's iconify callback, on the render thread while it polls events).
     * The presenter turns frame generation off at once on minimizing (NVIDIA: off before window changes).
     */
    public static void minimized(boolean now) {
        if (minimized && !now) {
            afterResize = AFTER_RESIZE;
        }
        minimized = now;
    }

    /** The presenter: Present said the window can't be seen (DXGI_STATUS_OCCLUDED), or that it can again. */
    public static void hidden(boolean now) {
        if (hidden && !now) {
            afterResize = AFTER_RESIZE;
        }
        hidden = now;
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
        if (sinceReload <= AFTER_RELOAD) {
            sinceReload++;
        }
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
        boolean repeated = id == lastFrameIndex;
        if (repeated) {
            // A second present without a new game frame (something drew and presented outside Minecraft's frame): it
            // gets a number of its own, as Streamline allows one set of constants per frame number, and no generated
            // frames, as nothing new was rendered.
            id = FrameLatency.extraFrameId();
            repeatedFrames++;
            if (repeatedFrames == 1) {
                LOG.info("Frame generation: a frame was presented twice in one game frame; the extra one goes out on its own, "
                        + "without generated frames. From: {}", caller());
            }
        }
        lastFrameIndex = id;
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
        String off = repeated ? "a second present in one game frame" : offReason(minecraft, inputs, hudless != 0 || noHud, width,
                height, resizing);
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
        if (!on()) {
            return "turned off";
        }
        if (minecraft.level == null) {
            return "no world";
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
            return "a screen is open";
        }
        if (minimized || width <= 0 || height <= 0 || glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW_ICONIFIED) != 0) {
            return "the window is minimized";
        }
        if (hidden) {
            return "the window can't be seen";
        }
        if (resizing || afterResize > 0) {
            return "the window is resizing";
        }
        if (!inputs) {
            return "DLSS or FSR didn't run this frame (DLAA on, a shader pack made for it, no debug view)";
        }
        if (sinceReload < AFTER_RELOAD) {
            return "the shaders were just loaded";
        }
        if (!hudless) {
            return "no copy of the world without the HUD this frame";
        }
        if (afterProblem > 0) {
            return reporter() + " reported " + statusText(lastStatus);
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

    /**
     * The presenter showed a frame that wasn't drawn for it (Minecraft presents again while it changes the window mode),
     * with frame generation off: the next frame that generates starts over.
     */
    public static void presentedAlone() {
        generating = false;
    }

    /**
     * For the frame cap: frames shown per rendered one while AMD's frame generation generates, so a cap counts the frames
     * on screen as it does with DLSS (whose Reflex, through Streamline, counts them itself); else 1.
     */
    public static int capShownPerRendered() {
        return generating && backend() == BACKEND_FSR ? 1 + Math.max(1, FRAME.framesToGenerate) : 1;
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
                LOG.warn("Frame generation: {} reports {} (status {}); off for {} frames, then tried again", reporter(),
                        statusText(status), status, AFTER_PROBLEM);
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

    /** Who reports the backend's status: Streamline or FidelityFX. */
    private static String reporter() {
        return backend == BACKEND_FSR ? "FidelityFX" : "Streamline";
    }

    private static String statusText(long status) {
        String[] statusNames = backend == BACKEND_FSR ? FFX_STATUS_NAMES : STATUS_NAMES;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < statusNames.length; i++) {
            if ((status & (1L << i)) != 0) {
                names.add(statusNames[i]);
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
            // The frames after a reload, which frame generation skips, are checked each, to show they need skipping.
            boolean afterReload = sinceReload <= AFTER_RELOAD;
            if (check && (afterReload || now - nextCheck >= 0L)) {
                nextCheck = now + CHECK_EVERY;
                String note = afterReload ? "frame " + sinceReload + " after the shader pipeline was made"
                        + (sinceReload < AFTER_RELOAD ? ", frame generation skips it" : "") : null;
                if (upscalerReset) {
                    note = (note != null ? note + ", " : "") + "the upscaler started over";
                }
                FgCheck.run(local, source, main.getColorTextureId(), depthTexture, motionTexture, width, height, renderWidth,
                        renderHeight, noHud, camera, true, note);
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

    /** Where the present came from, for the log: the call stack outside the Bridge's presenter, a few frames. */
    private static String caller() {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String type = element.getClassName();
            if (type.startsWith("java.") || type.startsWith("dev.creategalore.bridge.framegen") || type.startsWith("dev.creategalore.bridge.present")) {
                continue;
            }
            text.append(shown == 0 ? "" : " < ").append(type.substring(type.lastIndexOf('.') + 1)).append('.').append(element.getMethodName());
            if (++shown == 8) {
                break;
            }
        }
        return text.toString();
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

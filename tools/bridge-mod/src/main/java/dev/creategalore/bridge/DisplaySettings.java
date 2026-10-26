package dev.creategalore.bridge;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/create_galore_bridge-display.properties: the Bridge's client display switches. Missing keys are added with
 * their defaults and existing values are kept. A fresh install on Windows (no file yet) starts with Perfect
 * Presentation on DirectX 12; a file from before keeps what it says, and a key it lacks gets the old default (off,
 * DirectX 11), so nobody's setup changes under them. Read before the game window exists, so the window switches need a
 * restart; the DLAA ones are read again whenever shaders reload ({@link #reload()}). The display settings screen
 * ({@code DlaaScreen}) writes them with {@link #save(Map)}.
 */
public final class DisplaySettings {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    public static final String FILE = "create_galore_bridge-display.properties";
    public static final String TEN_BIT_WINDOW = "tenBitWindow";
    public static final String PERFECT_PRESENTATION = "perfectPresentation";
    /** auto (the default), dlss, fsr3 or off; true and false from the test builds still mean dlss and off. */
    public static final String DLAA = "dlaa";
    public static final String DLAA_PRESET = "dlaaPreset";
    /** Testing only, not written to the file: color, motion, depth, residual or mask shows that DLSS input (or check) instead of DLSS. */
    public static final String DLAA_DEBUG_VIEW = "dlaaDebugView";
    /** Testing only, not written to the file: x,y signs for the jitter handed to DLSS (1,1 unless a test shows otherwise). */
    public static final String DLAA_JITTER_SIGN = "dlaaJitterSign";
    /** Testing only, not written to the file: with dlaa=true, dlss (the default) or fsr3 (the test kit's way to pick). */
    public static final String DLAA_ENGINE = "dlaaEngine";
    /** Testing only, not written to the file: false leaves out the motion vectors of things that move by themselves. */
    public static final String DLAA_OBJECT_MOTION = "dlaaObjectMotion";
    /** Testing only, not written to the file: false leaves out FSR 3's reactive and composition masks. */
    public static final String DLAA_REACTIVE_MASK = "dlaaReactiveMask";
    /** Testing only, not written to the file: true has DLSS meter the exposure itself with presets J and K too. */
    public static final String DLAA_AUTO_EXPOSURE = "dlaaAutoExposure";
    /** Testing only, not written to the file: added to the textures' mip bias while DLSS or FSR runs (NVIDIA's guide: -1). */
    public static final String DLAA_MIP_BIAS = "dlaaMipBias";
    /** true: the settings screen sets the render resolution with a slider instead of the standard modes. */
    public static final String DLAA_CUSTOM_SCALE = "dlaaCustomScale";
    /** NVIDIA Reflex on Perfect Presentation's DirectX device: off, on or boost (markers for testing; see FrameLatency). */
    public static final String REFLEX = "reflex";
    /** Testing only, not written to the file: true or false reads input again just before each frame renders, or not (unset: with Reflex on). */
    public static final String LATE_POLL = "latePoll";
    /** Testing only, not written to the file: true logs the input latency every 10 seconds (see FrameLatency). */
    public static final String LATENCY_LOG = "latencyLog";
    /** off, on or lowest: wait just before each frame renders so it doesn't queue on the GPU (see GpuPacer). */
    public static final String GPU_PACING = "gpuPacing";
    /** Testing only, not written to the file: true or false turns the GPU pacer on or off whatever gpuPacing says. */
    public static final String GPU_PACER = "gpuPacer";
    /** Testing only, not written to the file: how long, in microseconds, the GPU pacer lets a frame queue on the GPU, or auto (unset: as gpuPacing says). */
    public static final String PACER_TARGET_US = "pacerTargetUs";
    /** Testing only, not written to the file: false leaves the Max Framerate cap to the game's limiter, after the frame, instead of the frame's start (see FrameLatency). */
    public static final String FRAME_CAP_AT_START = "frameCapAtStart";
    /** dx12 (a fresh install's) or dx11: the DirectX version Perfect Presentation shows the game with (needs a restart). */
    public static final String PRESENTER = "presenter";
    /** Testing only, not written to the file: true has the DirectX 12 presenter compare two shown frames with the game's, pixel by pixel, in the log. */
    public static final String PRESENT_CHECK = "presentCheck";
    /** Testing only, not written to the file: true logs GPU timestamps around the DirectX 12 hand-off (see PresentTiming). */
    public static final String PRESENT_TIMING = "presentTiming";
    /** Testing only, not written to the file: true keeps a copy of the world without the HUD each frame and the HUD's coverage in alpha (see HudSplit). */
    public static final String HUD_SPLIT = "hudSplit";
    /** Testing only, not written to the file: hudless, alpha, ui or marks shows that part of the HUD split instead of the game. */
    public static final String HUD_SPLIT_VIEW = "hudSplitView";
    /** Testing only, not written to the file: true checks the HUD split every 5 seconds (log, screenshots/hud-split). */
    public static final String HUD_SPLIT_CHECK = "hudSplitCheck";
    /** off, dlss or fsr: frame generation in the DirectX 12 presenter, experimental (see FrameGen); on and off at once, dlss and fsr swap at a restart. */
    public static final String FRAME_GEN = "frameGen";
    /** dlss or fsr: the frame generation the DirectX 12 presenter loads at its start while frameGen is off (the last one picked; none: the best for the card). */
    public static final String FRAME_GEN_TYPE = "frameGenType";
    /** Testing only, not written to the file: frames generated per rendered one (1, the default: twice the frame rate). */
    public static final String FRAME_GEN_FRAMES = "frameGenFrames";
    /** Testing only, not written to the file: false has frame generation take the HUD as part of the picture instead of apart. */
    public static final String FRAME_GEN_UI = "frameGenUi";
    /** Testing only, not written to the file: a folder of Streamline's development DLLs to run instead of the mod's (no signature check). */
    public static final String FRAME_GEN_STREAMLINE = "frameGenStreamline";
    /** Testing only, not written to the file: true has Streamline log verbosely (create-galore-dlss/streamline-logs). */
    public static final String FRAME_GEN_VERBOSE = "frameGenVerbose";
    /** Testing only, not written to the file: hudless, alpha, depth or motion shows that frame generation input instead of the game. */
    public static final String FRAME_GEN_VIEW = "frameGenView";
    /** Testing only, not written to the file: true checks frame generation's inputs and camera every 5 seconds (log). */
    public static final String FRAME_GEN_CHECK = "frameGenCheck";
    /** The frame counter: off, basic or detailed (see FpsOverlay). */
    public static final String FPS_COUNTER = "fpsCounter";
    /** The frame counter's corner: top-left, top-right, bottom-left or bottom-right. */
    public static final String FPS_COUNTER_CORNER = "fpsCounterCorner";
    /** true: the frame counter see-through and moving a pixel now and then, for OLED screens. */
    public static final String FPS_COUNTER_DIM = "fpsCounterDim";

    private static final String COMMENT = "Create Galore Bridge display settings, also on the Upscaling & Display screen (Mods, Create\n"
            + " Galore Bridge, Config, or its button in the shader pack menu). perfectPresentation, presenter and tenBitWindow\n"
            + " need a restart; dlaa and dlaaPreset apply when shaders reload (Iris's reload key, F8 in this pack).\n"
            + " dlaa: optional. Only with a shader pack made for DLAA (one with shaders/create_galore_dlss.properties), such as\n"
            + " Photon v1.3a + DLAA, a copy of Photon the Bridge makes in shaderpacks/ at startup (pick it in the shader menu):\n"
            + " it replaces the pack's own anti-aliasing with NVIDIA DLSS or AMD FSR 3.1. auto: DLSS on an NVIDIA RTX card\n"
            + " with nvngx_dlss.dll in create-galore-dlss/ (not part of the pack: bring your own), else FSR 3.1;\n"
            + " dlss, fsr3: only that one; off: the pack's own anti-aliasing (and no Photon copy). Works with or without\n"
            + " perfectPresentation.\n"
            + " dlaaPreset: default (K), J, K, L, M or any other letter (DLSS render presets; L and M are made for low render\n"
            + " resolutions; a letter DLSS has no preset for runs as DLSS's own default).\n"
            + " Upscaling: the render resolution is the shader pack's own upscaling options (in Photon TAAU and TAAU Render\n"
            + " Scale; Render resolution on the settings screen); DLSS or FSR 3.1 upscale from it to the window.\n"
            + " dlaaCustomScale: true shows a 25-100% slider on the settings screen instead of the standard modes.\n"
            + " reflex: off, on or boost. NVIDIA Reflex low latency (boost also keeps the GPU clocks up), applied at once.\n"
            + " Needs an NVIDIA card (GTX 900 or newer) and perfectPresentation=true; the settings screen turns that on\n"
            + " with it. With Max Framerate set, Reflex keeps that cap (where it helps most: use one the card can hold).\n"
            + " gpuPacing: off, on or lowest, any graphics card. The game waits just before drawing each frame so it reaches\n"
            + " the graphics card about when the card is ready, instead of queueing behind earlier frames: shorter input\n"
            + " delay without a frame cap. on keeps the frame rate; lowest is a little quicker still, for about 6% fewer frames.\n"
            + " frameGen: off, dlss or fsr, EXPERIMENTAL. Frame generation: the graphics card shows a frame of its own between\n"
            + " every two the game renders. dlss: NVIDIA DLSS Frame Generation, NVIDIA RTX 40 or 50 cards, with Windows'\n"
            + " Hardware-accelerated GPU scheduling on and nvngx_dlssg.dll in create-galore-dlss/ (not part of the pack, like\n"
            + " nvngx_dlss.dll); Reflex stays on while it runs. fsr: AMD FSR 3.1 frame generation, any card DirectX 12 runs\n"
            + " on. Both need perfectPresentation=true with presenter=dx12 (the settings screen sets both with it) and dlaa\n"
            + " running (DLSS or FSR 3.1). The one in frameGenType (dlss or fsr; empty: DLSS on an RTX 40 or 50 card with\n"
            + " nvngx_dlssg.dll, else FSR) loads whenever DirectX 12 starts, so it turns on and off without a restart; picking\n"
            + " the other one needs a restart.\n"
            + " fpsCounter: off, basic (the frame rate, small, in a corner) or detailed (also the frame time, the 1% and 0.1%\n"
            + " lows of the last 10 seconds and a frame time graph). fpsCounterCorner: top-left, top-right, bottom-left or\n"
            + " bottom-right. fpsCounterDim: true for OLED screens: see-through, and it moves a pixel every two minutes.\n"
            + " perfectPresentation: Windows only, on in a fresh install. true shows the game through a DirectX swap chain\n"
            + " (ported from Perfect Presentation by sidit77): borderless fullscreen at exclusive-fullscreen speed and\n"
            + " Alt-Tab without a display mode change. Leave NVIDIA's Vulkan/OpenGL present method on Auto.\n"
            + " Needs an NVIDIA or AMD driver with WGL_NV_DX_interop2. Turns off NeoForge's early loading window, so\n"
            + " it starts from the launch after the one that turned it on. On NVIDIA also set Power management mode to\n"
            + " Prefer maximum performance for javaw.exe, or the driver keeps the GPU clocks low.\n"
            + " presenter: dx12 (a fresh install's) or dx11, the DirectX version Perfect Presentation shows the game with.\n"
            + " dx12: marginally lower input delay than dx11 in tests, same frame rate; frame generation needs it. Needs an\n"
            + " OpenGL driver that shares textures with DirectX 12 (NVIDIA's does, Intel's doesn't); where it can't\n"
            + " start, the game uses dx11.\n"
            + " tenBitWindow: EXPERIMENTAL. true asks for a 10-bit game window, meant to keep a 10-bit monitor at 10-bit\n"
            + " in fullscreen. It did not stop the 8-bit switch on an RTX 4080 Super. Turns off NeoForge's early\n"
            + " loading window (config/fml.toml).";

    private static Properties cached;
    private static volatile int generation;

    private DisplaySettings() {}

    public static synchronized boolean get(String key) {
        if (cached == null) {
            cached = load();
        }
        return Boolean.parseBoolean(cached.getProperty(key, "false").trim());
    }

    public static synchronized String getString(String key, String fallback) {
        if (cached == null) {
            cached = load();
        }
        return cached.getProperty(key, fallback).trim();
    }

    /** The dlaa setting: auto, dlss, fsr3 or off (true from the test builds is dlss, or dlaaEngine's pick). */
    public static synchronized String dlaaEngine() {
        String value = getString(DLAA, "auto").toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "auto", "" -> "auto";
            case "dlss" -> "dlss";
            case "fsr3", "fsr" -> "fsr3";
            case "off", "false" -> "off";
            case "true" -> getString(DLAA_ENGINE, System.getProperty("create_galore_bridge.dlss.engine", "dlss"))
                    .toLowerCase(java.util.Locale.ROOT).startsWith("fsr") ? "fsr3" : "dlss";
            default -> {
                LOG.warn("Unknown {}={} in {}, using auto (choose auto, dlss, fsr3 or off)", DLAA, value, FILE);
                yield "auto";
            }
        };
    }

    /** Reads the file again on the next get (the DLAA settings apply on a shader reload). */
    public static synchronized void reload() {
        cached = null;
        generation++;
    }

    /** Changes whenever the settings may have changed (a reload or a save), for code that keeps its own copy. */
    public static int generation() {
        return generation;
    }

    /** Writes {@code values} into the file, keeping its other keys. False (logged) if it could not be written. */
    public static synchronized boolean save(Map<String, String> values) {
        Properties props = load();
        props.putAll(values);
        cached = props;
        generation++;
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE);
        try (Writer w = Files.newBufferedWriter(file)) {
            props.store(w, COMMENT);
            return true;
        } catch (IOException e) {
            LOG.warn("Could not write {}", file, e);
            return false;
        }
    }

    private static Properties load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE);
        Properties props = new Properties();
        try {
            boolean fresh = !Files.exists(file);
            if (!fresh) {
                try (Reader r = Files.newBufferedReader(file)) {
                    props.load(r);
                }
            }
            // A fresh install on Windows starts on Perfect Presentation and DirectX 12; an older file keeps the old
            // defaults for keys it lacks (off, DirectX 11), so a setup the player never changed stays as it was.
            boolean present = fresh && FMLEnvironment.dist.isClient() && System.getProperty("os.name", "").startsWith("Windows");
            if (present) {
                LOG.info("Display settings: a fresh {}, starting with Perfect Presentation on DirectX 12 (Windows)", FILE);
            }
            boolean missing = props.putIfAbsent(TEN_BIT_WINDOW, "false") == null;
            missing |= props.putIfAbsent(PERFECT_PRESENTATION, Boolean.toString(present)) == null;
            missing |= props.putIfAbsent(DLAA, "auto") == null;
            missing |= props.putIfAbsent(DLAA_PRESET, "default") == null;
            missing |= props.putIfAbsent(PRESENTER, present ? "dx12" : "dx11") == null;
            missing |= props.putIfAbsent(REFLEX, "off") == null;
            missing |= props.putIfAbsent(GPU_PACING, "off") == null;
            missing |= props.putIfAbsent(FRAME_GEN, "off") == null;
            missing |= props.putIfAbsent(FPS_COUNTER, "off") == null;
            missing |= props.putIfAbsent(FPS_COUNTER_CORNER, "top-right") == null;
            missing |= props.putIfAbsent(FPS_COUNTER_DIM, "false") == null;
            if (missing) {
                try (Writer w = Files.newBufferedWriter(file)) {
                    props.store(w, COMMENT);
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}, using defaults", file, e);
        }
        return props;
    }
}

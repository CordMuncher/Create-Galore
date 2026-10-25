package dev.creategalore.bridge;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/create_galore_bridge-display.properties: the Bridge's client display switches. Missing keys are added with
 * their defaults and existing values are kept. Read before the game window exists, so the window switches need a
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
    /** Testing only, not written to the file: true waits just before each frame renders so it doesn't queue on the GPU (see GpuPacer). */
    public static final String GPU_PACER = "gpuPacer";
    /** Testing only, not written to the file: how long, in microseconds, the GPU pacer lets a frame queue on the GPU (auto unless set). */
    public static final String PACER_TARGET_US = "pacerTargetUs";
    /** Testing only, not written to the file: true has the Max Framerate cap wait at the frame's start, before input (see FrameLatency). */
    public static final String FRAME_CAP_AT_START = "frameCapAtStart";

    private static final String COMMENT = "Create Galore Bridge display settings, also on the settings screen (Mods, Create Galore Bridge,\n"
            + " Config, or the DLAA button in the shader pack menu). perfectPresentation and tenBitWindow need a restart;\n"
            + " dlaa and dlaaPreset apply when shaders reload (Iris's reload key, F8 in this pack).\n"
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
            + " perfectPresentation: EXPERIMENTAL, Windows only. true shows the game through a DirectX swap chain\n"
            + " (ported from Perfect Presentation by sidit77): borderless fullscreen at exclusive-fullscreen speed and\n"
            + " Alt-Tab without a display mode change. Leave NVIDIA's Vulkan/OpenGL present method on Auto.\n"
            + " Needs an NVIDIA or AMD driver with WGL_NV_DX_interop2. Turns off NeoForge's early loading window.\n"
            + " On NVIDIA also set Power management mode to Prefer maximum performance for javaw.exe, or the\n"
            + " driver keeps the GPU clocks low.\n"
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
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    props.load(r);
                }
            }
            boolean missing = props.putIfAbsent(TEN_BIT_WINDOW, "false") == null;
            missing |= props.putIfAbsent(PERFECT_PRESENTATION, "false") == null;
            missing |= props.putIfAbsent(DLAA, "auto") == null;
            missing |= props.putIfAbsent(DLAA_PRESET, "default") == null;
            missing |= props.putIfAbsent(REFLEX, "off") == null;
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

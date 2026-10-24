package dev.creategalore.bridge;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/create_galore_bridge-display.properties: the Bridge's client display switches. Missing keys are added with
 * their defaults (off) and existing values are kept. Read before the game window exists, so the window switches need a
 * restart; the DLAA ones are read again whenever shaders reload ({@link #reload()}).
 */
public final class DisplaySettings {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    public static final String FILE = "create_galore_bridge-display.properties";
    public static final String TEN_BIT_WINDOW = "tenBitWindow";
    public static final String PERFECT_PRESENTATION = "perfectPresentation";
    public static final String DLAA = "dlaa";
    public static final String DLAA_PRESET = "dlaaPreset";
    /** Testing only, not written to the file: color, motion, depth, residual or mask shows that DLSS input (or check) instead of DLSS. */
    public static final String DLAA_DEBUG_VIEW = "dlaaDebugView";
    /** Testing only, not written to the file: x,y signs for the jitter handed to DLSS (1,1 unless a test shows otherwise). */
    public static final String DLAA_JITTER_SIGN = "dlaaJitterSign";
    /** Testing only, not written to the file: dlss (the default) or fsr3 (AMD FSR 3.1 in DLSS's place, any GPU). */
    public static final String DLAA_ENGINE = "dlaaEngine";
    /** Testing only, not written to the file: false leaves out the motion vectors of things that move by themselves. */
    public static final String DLAA_OBJECT_MOTION = "dlaaObjectMotion";
    /** Testing only, not written to the file: false leaves out FSR 3's reactive and composition masks. */
    public static final String DLAA_REACTIVE_MASK = "dlaaReactiveMask";

    private static Properties cached;

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

    /** Reads the file again on the next get (the DLAA settings apply on a shader reload). */
    public static synchronized void reload() {
        cached = null;
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
            missing |= props.putIfAbsent(DLAA, "false") == null;
            missing |= props.putIfAbsent(DLAA_PRESET, "default") == null;
            if (missing) {
                try (Writer w = Files.newBufferedWriter(file)) {
                    props.store(w, "Create Galore Bridge display settings. perfectPresentation and tenBitWindow need a restart;\n"
                            + " dlaa and dlaaPreset apply when shaders reload (Iris's reload key, R by default).\n"
                            + " dlaa: EXPERIMENTAL, Windows + NVIDIA RTX only. true replaces the shader pack's TAA with NVIDIA DLAA\n"
                            + " (DLSS at full resolution) in packs patched for it: the Bridge makes a Photon v1.3a + DLAA copy in\n"
                            + " shaderpacks/ at startup; pick that pack in the shader menu. Works with or without perfectPresentation.\n"
                            + " dlaaPreset: default, J, K, L or M (DLSS render presets; default lets DLSS choose, K today).\n"
                            + " perfectPresentation: EXPERIMENTAL, Windows only. true shows the game through a DirectX swap chain\n"
                            + " (ported from Perfect Presentation by sidit77): borderless fullscreen at exclusive-fullscreen speed and\n"
                            + " Alt-Tab without a display mode change. Leave NVIDIA's Vulkan/OpenGL present method on Auto.\n"
                            + " Needs an NVIDIA or AMD driver with WGL_NV_DX_interop2. Turns off NeoForge's early loading window.\n"
                            + " On NVIDIA also set Power management mode to Prefer maximum performance for javaw.exe, or the\n"
                            + " driver keeps the GPU clocks low.\n"
                            + " tenBitWindow: EXPERIMENTAL. true asks for a 10-bit game window, meant to keep a 10-bit monitor at 10-bit\n"
                            + " in fullscreen. It did not stop the 8-bit switch on an RTX 4080 Super. Turns off NeoForge's early\n"
                            + " loading window (config/fml.toml).");
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}, using defaults", file, e);
        }
        return props;
    }
}

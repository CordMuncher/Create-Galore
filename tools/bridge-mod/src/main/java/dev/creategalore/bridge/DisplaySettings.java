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

    private static final String COMMENT = "Create Galore Bridge display settings, also on the settings screen (Mods, Create Galore Bridge,\n"
            + " Config, or the DLAA button in the shader pack menu). perfectPresentation and tenBitWindow need a restart;\n"
            + " dlaa and dlaaPreset apply when shaders reload (Iris's reload key, F8 in this pack).\n"
            + " dlaa: EXPERIMENTAL and optional, not widely tested yet. Only with the shader pack Photon v1.3a + DLAA, a copy\n"
            + " of Photon the Bridge makes in shaderpacks/ at startup (pick it in the shader menu): it replaces Photon's TAA with\n"
            + " NVIDIA DLAA (DLSS at full resolution) or AMD FSR 3.1 at full resolution. auto: DLSS on an NVIDIA RTX card\n"
            + " with nvngx_dlss.dll in create-galore-dlss/ (not part of the pack: bring your own), else FSR 3.1;\n"
            + " dlss, fsr3: only that one; off: Photon's own TAA (and no copy). Works with or without perfectPresentation.\n"
            + " dlaaPreset: default, J, K, L, M or any other letter (DLSS render presets; default lets DLSS choose, K today;\n"
            + " a letter DLSS has no preset for runs as default).\n"
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
    }

    /** Writes {@code values} into the file, keeping its other keys. False (logged) if it could not be written. */
    public static synchronized boolean save(Map<String, String> values) {
        Properties props = load();
        props.putAll(values);
        cached = props;
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

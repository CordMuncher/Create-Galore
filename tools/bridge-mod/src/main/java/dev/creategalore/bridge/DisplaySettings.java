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
 * their defaults (off) and existing values are kept. Read before the game window exists, so changes need a restart.
 */
public final class DisplaySettings {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    public static final String FILE = "create_galore_bridge-display.properties";
    public static final String TEN_BIT_WINDOW = "tenBitWindow";
    public static final String PERFECT_PRESENTATION = "perfectPresentation";

    private static Properties cached;

    private DisplaySettings() {}

    public static synchronized boolean get(String key) {
        if (cached == null) {
            cached = load();
        }
        return Boolean.parseBoolean(cached.getProperty(key, "false").trim());
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
            if (missing) {
                try (Writer w = Files.newBufferedWriter(file)) {
                    props.store(w, "Create Galore Bridge display settings. Both need a restart.\n"
                            + " perfectPresentation: EXPERIMENTAL, Windows only. true shows the game through a DirectX swap chain\n"
                            + " (ported from Perfect Presentation by sidit77): borderless fullscreen at exclusive-fullscreen speed and\n"
                            + " Alt-Tab without a display mode change. Leave NVIDIA's Vulkan/OpenGL present method on Auto.\n"
                            + " Needs an NVIDIA or AMD driver with WGL_NV_DX_interop2. Turns off NeoForge's early loading window.\n"
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

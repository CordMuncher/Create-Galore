package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Photon's options while DLSS Ray Reconstruction runs, kept apart from the player's own. Ray Reconstruction shows
 * Photon's lighting and reflections at their best with its effects on, so turning it on in the Upscaling & Display
 * screen moves the DLAA Photon copy ({@link PhotonPatcher#PATCHED_NAME}) to Photon's own Ultra profile the first time,
 * and to the settings the player made under Ray Reconstruction after that; turning it off puts back the settings
 * Photon had before. A toast says so each time. Both sets live in the config folder:
 * - create_galore_bridge-photon-own.txt: Photon's settings from before Ray Reconstruction went on (written then);
 * - create_galore_bridge-photon-rr.txt: the settings made under Ray Reconstruction (written when it goes off), only
 *   while they differ from Ultra, so an untouched set follows the pack's default.
 * The render resolution (Photon's upscaling switch and render scale, see {@link RenderScaleTarget}) is the screen's
 * own setting and stays out of both. Only the screen switches them: the test kit and the settings file turn Ray
 * Reconstruction on and off without touching Photon's options.
 */
final class PhotonRrSettings {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8000L);
    /** Photon v1.3a's Ultra profile (profile.ultra in its shaders.properties): every effect it has on. */
    static final Map<String, String> ULTRA = Map.ofEntries(
            Map.entry("INFO", "3"),
            Map.entry("shadowMapResolution", "2048"),
            Map.entry("SHADOW_PCF", "true"),
            Map.entry("SHADOW_COLOR", "true"),
            Map.entry("SHADOW_VPS", "true"),
            Map.entry("ENTITY_SHADOWS", "true"),
            Map.entry("BLOCK_ENTITY_SHADOWS", "true"),
            Map.entry("ENVIRONMENT_REFLECTIONS", "true"),
            Map.entry("GTAO", "true"),
            Map.entry("VL", "true"),
            Map.entry("AIR_FOG_COLORED_LIGHT_SHAFTS", "true"),
            Map.entry("WATER_CAUSTICS", "true"),
            Map.entry("WATER_PARALLAX", "true"),
            Map.entry("COLORED_LIGHTS", "true"),
            Map.entry("SHADOW_SSRT", "true"));

    private PhotonRrSettings() {}

    /** The screen turned Ray Reconstruction on or off: swaps Photon's options, before the reload that applies them. */
    static void switched(boolean on) {
        Path options = Iris.getShaderpacksDirectory().resolve(PhotonPatcher.PATCHED_NAME + ".txt");
        Path own = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-photon-own.txt");
        Path rr = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-photon-rr.txt");
        RenderScaleTarget photon = RenderScaleTarget.photon();
        Set<String> screenOptions = Set.of(photon.upscaling(), photon.scale());
        Map<String, String> current = read(options);
        Map<String, String> settings = new HashMap<>(current);
        settings.keySet().removeAll(screenOptions);
        Map<String, String> next;
        String title;
        String message;
        try {
            if (on) {
                Map<String, String> saved = Files.isRegularFile(rr) ? read(rr) : null;
                write(own, settings);
                next = new HashMap<>(saved != null ? saved : ULTRA);
                title = saved != null ? "Photon: your Ray Reconstruction settings" : "Photon: Ultra for Ray Reconstruction";
                message = "Your own Photon settings come back when you turn it off. Change these in Photon's menu as usual.";
            } else {
                if (!Files.isRegularFile(own)) {
                    LOG.info("DLAA: Ray Reconstruction off; no Photon settings from before it, so Photon keeps these");
                    return;
                }
                if (normalized(settings).equals(normalized(ULTRA))) {
                    Files.deleteIfExists(rr);
                } else {
                    write(rr, settings);
                }
                next = read(own);
                title = "Photon: your settings are back";
                message = "Ray Reconstruction's own Photon settings come back when you turn it on again.";
            }
            next.keySet().removeAll(screenOptions);
            for (String option : screenOptions) {
                if (current.containsKey(option)) {
                    next.put(option, current.get(option));
                }
            }
            write(options, next);
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not switch Photon's settings for Ray Reconstruction: {}", e.toString());
            return;
        }
        LOG.info("DLAA: Ray Reconstruction {}: Photon's settings switched ({})", on ? "on" : "off", title);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, TOAST, Component.literal(title), Component.literal(message)));
    }

    /**
     * The values as Iris keeps them, when the Photon copy is the pack loaded: each option that differs from Photon's
     * default, with Iris's own reading of it (MutableOptionValues), unknown names left out. Else as they are.
     */
    private static Map<String, String> normalized(Map<String, String> values) {
        if (!PhotonPatcher.PATCHED_NAME.equals(Iris.getCurrentPackName())) {
            return values;
        }
        return Iris.getCurrentPack().map(pack -> {
            MutableOptionValues parsed = new MutableOptionValues(pack.getShaderPackOptions().getOptionSet(), values);
            Map<String, String> result = new HashMap<>(parsed.getStringValues());
            parsed.getBooleanValues().forEach((name, value) -> result.put(name, Boolean.toString(value)));
            return result;
        }).orElse(values);
    }

    private static Map<String, String> read(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        Properties stored = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            stored.load(reader);
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not read {}: {}", file, e.toString());
        }
        stored.stringPropertyNames().forEach(name -> values.put(name, stored.getProperty(name).trim()));
        return values;
    }

    private static void write(Path file, Map<String, String> values) throws IOException {
        Properties stored = new Properties();
        stored.putAll(values);
        try (Writer writer = Files.newBufferedWriter(file)) {
            stored.store(writer, null);
        }
    }
}

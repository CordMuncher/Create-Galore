package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
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
 * A shader pack's options while DLSS Ray Reconstruction runs, kept apart from the player's own, for each pack that
 * supports it. Turning Ray Reconstruction on in the Upscaling & Display screen moves the pack in use to the settings the
 * player made under Ray Reconstruction (the first time: for the DLAA Photon copy, {@link PhotonPatcher#PATCHED_NAME},
 * Photon's own Ultra profile, as Ray Reconstruction shows its lighting and reflections at their best with its effects
 * on; for other packs, the settings it has); turning it off puts back the settings the pack had before. A toast says
 * so each time. Both sets live in the config folder (photon is the Photon copy's name in them, else the pack's name):
 * - create_galore_bridge-photon-own.txt: the pack's settings from before Ray Reconstruction went on (written then);
 * - create_galore_bridge-photon-rr.txt: the settings made under Ray Reconstruction (written when it goes off), for
 *   Photon only while they differ from Ultra, so an untouched set follows the pack's default.
 * The options the screen sets (the render resolution and the anti-aliasing switch, see {@link RenderScaleTarget}) stay
 * out of both. Only the screen switches them: the test kit and the settings file turn Ray Reconstruction on and off
 * without touching the pack's options.
 */
final class PackRrSettings {
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

    private PackRrSettings() {}

    /**
     * The screen turned Ray Reconstruction on or off with {@code target}'s pack in use: swaps the pack's options, before
     * the reload that applies them.
     */
    static void switched(RenderScaleTarget target, boolean on) {
        boolean photon = PhotonPatcher.PATCHED_NAME.equals(target.pack());
        String name = photon ? "Photon" : target.pack();
        String slug = photon ? "photon" : target.pack().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Path options = target.optionsFile();
        Path own = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-" + slug + "-own.txt");
        Path rr = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-" + slug + "-rr.txt");
        Set<String> screenOptions = new HashSet<>();
        for (String option : new String[] {target.upscaling(), target.scale(), target.taaOption()}) {
            if (option != null && !option.isEmpty()) {
                screenOptions.add(option);
            }
        }
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
                next = new HashMap<>(saved != null ? saved : photon ? ULTRA : settings);
                title = saved != null ? name + ": your Ray Reconstruction settings"
                        : photon ? "Photon: Ultra for Ray Reconstruction" : name + ": settings kept apart for Ray Reconstruction";
                message = "Your own " + name + " settings come back when you turn it off. Change these in " + name + "'s menu as usual.";
            } else {
                if (!Files.isRegularFile(own)) {
                    LOG.info("DLAA: Ray Reconstruction off; no {} settings from before it, so {} keeps these", name, name);
                    return;
                }
                if (photon && normalized(target.pack(), settings).equals(normalized(target.pack(), ULTRA))) {
                    Files.deleteIfExists(rr);
                } else {
                    write(rr, settings);
                }
                next = read(own);
                title = name + ": your settings are back";
                message = "Ray Reconstruction's own " + name + " settings come back when you turn it on again.";
            }
            next.keySet().removeAll(screenOptions);
            for (String option : screenOptions) {
                if (current.containsKey(option)) {
                    next.put(option, current.get(option));
                }
            }
            write(options, next);
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not switch {}'s settings for Ray Reconstruction: {}", name, e.toString());
            return;
        }
        LOG.info("DLAA: Ray Reconstruction {}: {}'s settings switched ({})", on ? "on" : "off", name, title);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, TOAST, Component.literal(title), Component.literal(message)));
    }

    /**
     * The values as Iris keeps them, when {@code pack} is the pack loaded: each option that differs from the pack's
     * default, with Iris's own reading of it (MutableOptionValues), unknown names left out. Else as they are.
     */
    private static Map<String, String> normalized(String pack, Map<String, String> values) {
        if (!pack.equals(Iris.getCurrentPackName())) {
            return values;
        }
        return Iris.getCurrentPack().map(loaded -> {
            MutableOptionValues parsed = new MutableOptionValues(loaded.getShaderPackOptions().getOptionSet(), values);
            Map<String, String> result = new HashMap<>(parsed.getStringValues());
            parsed.getBooleanValues().forEach((option, value) -> result.put(option, Boolean.toString(value)));
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

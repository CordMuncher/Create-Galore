package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import dev.creategalore.bridge.DisplaySettings;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

/**
 * Where the render resolution is set (the Upscaling & Display screen and the test kit): the shader pack in use when it is made for
 * DLAA and has an upscaling switch (renderScaleOptions in its profile, see {@link PackProfile}), else the Photon copy.
 * The resolution is the pack's own options, so the pack renders at that size whether DLSS, FSR or the pack's own
 * upscaling makes the window's picture from it. A pack whose profile says renderScale=bridge has no such option: the
 * resolution is then the Bridge's own setting ({@link DisplaySettings#DLAA_RENDER_SCALE}), saved by the screen.
 *
 * pack: its name in the shaderpacks folder. inUse: it is the pack Iris has picked (its options then go through Iris's
 * queue, else into the .txt Iris keeps beside it). upscaling, scale: its upscaling switch and render scale option.
 * requireOn: options that must be on for it to upscale (Photon only upscales with its TAA on). bridge: the resolution
 * is the Bridge's setting (upscaling and scale are then empty).
 */
public record RenderScaleTarget(String pack, boolean inUse, String upscaling, String scale, List<String> requireOn, boolean bridge) {
    /** The upscaling switch's state and the scale option's value (null when the pack doesn't say). */
    public record Setting(boolean upscaling, String scale) {}

    public static RenderScaleTarget current() {
        String name = Iris.getCurrentPackName();
        try {
            PackProfile profile = PackProfile.current();
            if (profile != null && profile.bridgeScale()) {
                return new RenderScaleTarget(name, true, "", "", List.of(), true);
            }
            if (profile != null && profile.renderScaleOptions().size() == 2) {
                return of(name, true, profile);
            }
        } catch (IOException | RuntimeException e) {
            // the Photon copy's, below
        }
        return photon();
    }

    /** The DLAA Photon copy's, whether it is the pack in use or not. */
    public static RenderScaleTarget photon() {
        String name = Iris.getCurrentPackName();
        boolean photonInUse = PhotonPatcher.PATCHED_NAME.equals(name);
        try {
            PackProfile photon = PackProfile.of(PhotonPatcher.PATCHED_NAME);
            if (photon != null && photon.renderScaleOptions().size() == 2) {
                return of(PhotonPatcher.PATCHED_NAME, photonInUse, photon);
            }
        } catch (IOException | RuntimeException e) {
            // not made yet: the options PhotonPatcher gives it
        }
        return new RenderScaleTarget(PhotonPatcher.PATCHED_NAME, photonInUse, "TAAU", "TAAU_RENDER_SCALE", List.of("TAA"), false);
    }

    /** The Bridge's own render scale setting, from {@link PackProfile#MIN_RENDER_SCALE} to 1 (1 when unset or unreadable). */
    public static float bridgeScale() {
        try {
            float scale = Float.parseFloat(DisplaySettings.getString(DisplaySettings.DLAA_RENDER_SCALE, "1"));
            return Float.isFinite(scale) ? Math.max(PackProfile.MIN_RENDER_SCALE, Math.min(1.0f, scale)) : 1.0f;
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    private static RenderScaleTarget of(String name, boolean inUse, PackProfile profile) {
        return new RenderScaleTarget(name, inUse, profile.renderScaleOptions().get(0), profile.renderScaleOptions().get(1),
                profile.requireOn(), false);
    }

    /** The pack's current setting: from Iris when it is the pack loaded (defaults included), else from its .txt. */
    public Setting read() {
        if (bridge) {
            float scale = bridgeScale();
            return new Setting(scale < 1.0f, String.format(java.util.Locale.ROOT, "%.2f", scale));
        }
        OptionValues values = inUse ? Iris.getCurrentPack().map(p -> p.getShaderPackOptions().getOptionValues()).orElse(null) : null;
        if (values != null) {
            boolean on = values.getOptionSet().getBooleanOptions().containsKey(upscaling) && values.getBooleanValueOrDefault(upscaling);
            for (String option : requireOn) {
                on &= !values.getOptionSet().getBooleanOptions().containsKey(option) || values.getBooleanValueOrDefault(option);
            }
            return new Setting(on, values.getOptionSet().getStringOptions().containsKey(scale) ? values.getStringValueOrDefault(scale).trim() : null);
        }
        Properties stored = new Properties();
        try (Reader reader = Files.newBufferedReader(optionsFile())) {
            stored.load(reader);
        } catch (IOException | RuntimeException e) {
            // the pack's defaults: no upscaling
        }
        boolean on = Boolean.parseBoolean(stored.getProperty(upscaling, "false").trim());
        for (String option : requireOn) {
            on &= !stored.getProperty(option, "true").trim().equalsIgnoreCase("false");
        }
        String value = stored.getProperty(scale);
        return new Setting(on, value == null ? null : value.trim());
    }

    /**
     * Sets the pack's options: through Iris's queue when it is the pack in use (the reload after this applies and saves
     * them), else into its .txt for when it is picked. For a pack that leaves the resolution to the Bridge, into the
     * Bridge's display settings ({@link DisplaySettings#DLAA_RENDER_SCALE}).
     */
    public void write(Map<String, String> options) {
        if (bridge) {
            DisplaySettings.save(options);
            return;
        }
        if (inUse) {
            Iris.getShaderPackOptionQueue().putAll(options);
            return;
        }
        Path file = optionsFile();
        Properties stored = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    stored.load(reader);
                }
            }
            stored.putAll(options);
            try (Writer writer = Files.newBufferedWriter(file)) {
                stored.store(writer, null);
            }
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("create_galore_bridge").warn("DLAA: could not write {}: {}", file, e.toString());
        }
    }

    /** The options file Iris keeps beside the pack. */
    public Path optionsFile() {
        return Iris.getShaderpacksDirectory().resolve(pack + ".txt");
    }
}

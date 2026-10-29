package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

/**
 * shaders/create_galore_dlss.properties in a shader pack: where DLSS goes in that pack, and what the pack supports.
 * Only packs made for it have it (see {@link PhotonPatcher}); any other pack keeps its own anti-aliasing.
 *
 * pass: the composite pass after which DLSS runs (the pack's TAA pass, which the patch turns into a copy).
 * colorAttachment: that pass's colour output DLSS reads. outputAttachments: the outputs DLSS's result is written to;
 * keepAlpha: those of them whose alpha the pack still needs. motionImage, depthImage, exposureImage: the pack's
 * custom images holding motion vectors (render pixels, towards the previous frame), DLSS depth (1 near, 0 sky) and
 * the exposure (1x1). maskImage (optional): FSR 3's masks, reactive in red and transparency and composition in green
 * (RG16F, 0..1). requireOn / requireOff: pack options that must be on / off for the patch to apply (an option Iris
 * does not offer keeps the value the pack's source gives it, which the patcher checked). renderScaleOptions
 * (optional): the pack's upscaling switch and its render scale option; with the switch on, the pack renders at that
 * scale into the lower left corner of its buffers and DLSS upscales it, at the size the Bridge rounds it to
 * (taau_render_scale). renderScale=bridge (optional, in place of renderScaleOptions): the pack has no resolution option
 * of its own; the render resolution picked on the Upscaling & Display screen ({@link RenderScaleTarget}) is the size it
 * draws at, read from taau_render_scale every frame, while DLSS, FSR or its own TAAU runs (1 otherwise).
 *
 * supports (optional): what the pack supports, comma-separated, any of dlss, fsr (DLSS and FSR upscale its frame),
 * frameGeneration, rayReconstruction (with rrGuideImages), renderResolution (the screen sets the render resolution,
 * through renderScaleOptions or renderScale=bridge), taau (its own upscaling: its upscaling switch on, or with
 * renderScale=bridge and renderResolution, taau_render_scale below 1 while cg_dlss_active is 0) and off (no
 * anti-aliasing: taaOption off). Its own anti-aliasing (TAA) is always there, as it runs whenever nothing else does.
 * Without the key: dlss, fsr, frameGeneration, rayReconstruction, renderResolution, and taau with renderScaleOptions,
 * as far as the rest of the profile allows (what packs got before the key). The settings screen greys out the rest,
 * and the Bridge doesn't run it for the pack (a frame generation setting left on then shows only rendered frames).
 * pass, colorAttachment, outputAttachments and the three images are needed only with dlss or fsr.
 * taaOption (optional): the pack's boolean option for its anti-aliasing: off for off, on for everything else.
 * rrGuideImages (optional): the pack's guide images for DLSS Ray Reconstruction, written while cg_rr_active is 1:
 * diffuse albedo, specular albedo (both RGBA8), the normal in world space with the roughness in alpha (RGBA16F), and
 * optionally the specular hit distance (R32F); see {@link DlssPass.Guides}. A pack without them gets DLSS.
 */
record PackProfile(String pass, int colorAttachment, int[] outputAttachments, int[] keepAlpha, String motionImage,
                   String depthImage, String exposureImage, String maskImage, List<String> requireOn, List<String> requireOff,
                   List<String> renderScaleOptions, boolean bridgeScale, Set<String> supports, String taaOption,
                   List<String> rrGuideImages) {
    /** The lowest render scale the Bridge takes (a quarter of the window's width and height). */
    static final float MIN_RENDER_SCALE = 0.25f;

    static final String FILE = "shaders/create_galore_dlss.properties";
    // What a pack can support (its supports key, read without case).
    static final String DLSS = "dlss";
    static final String FSR = "fsr";
    static final String FRAME_GENERATION = "framegeneration";
    static final String RAY_RECONSTRUCTION = "rayreconstruction";
    static final String RENDER_RESOLUTION = "renderresolution";
    static final String TAAU = "taau";
    static final String OFF = "off";

    /** The current pack's profile, or null if it has none. */
    static PackProfile current() throws IOException {
        String name = Iris.getCurrentPackName();
        if (name == null || Iris.getCurrentPack().isEmpty()) {
            return null;
        }
        return of(name);
    }

    /** The profile of the pack called {@code name} in the shaderpacks folder (in use or not), or null if it has none. */
    static PackProfile of(String name) throws IOException {
        Path pack = Iris.getShaderpacksDirectory().resolve(name);
        Properties props = new Properties();
        if (Files.isDirectory(pack)) {
            Path file = pack.resolve(FILE);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        } else if (Files.isRegularFile(pack)) {
            try (FileSystem zip = FileSystems.newFileSystem(pack)) {
                Path file = zip.getPath(FILE);
                if (!Files.isRegularFile(file)) {
                    return null;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
            }
        } else {
            return null;
        }
        boolean bridge = props.getProperty("renderScale", "").trim().equalsIgnoreCase("bridge");
        Set<String> supports = new HashSet<>();
        if (props.getProperty("supports") != null) {
            names(props.getProperty("supports")).forEach(feature -> supports.add(feature.toLowerCase(Locale.ROOT)));
        } else {
            supports.addAll(List.of(DLSS, FSR, FRAME_GENERATION, RAY_RECONSTRUCTION, RENDER_RESOLUTION));
            if (!bridge) {
                supports.add(TAAU); // its upscaling switch (a pack leaving the resolution to the Bridge had no TAAU then)
            }
        }
        // The hand-off to DLSS and FSR, needed only when the pack supports one of them.
        boolean upscaled = supports.contains(DLSS) || supports.contains(FSR);
        return new PackProfile(upscaled ? required(props, "pass") : optional(props, "pass"),
                upscaled ? Integer.parseInt(required(props, "colorAttachment")) : -1,
                ints(upscaled ? required(props, "outputAttachments") : props.getProperty("outputAttachments", "")),
                ints(props.getProperty("keepAlpha", "")),
                upscaled ? required(props, "motionImage") : optional(props, "motionImage"),
                upscaled ? required(props, "depthImage") : optional(props, "depthImage"),
                upscaled ? required(props, "exposureImage") : optional(props, "exposureImage"),
                optional(props, "maskImage"),
                names(props.getProperty("requireOn", "")), names(props.getProperty("requireOff", "")),
                names(props.getProperty("renderScaleOptions", "")), bridge, Set.copyOf(supports), optional(props, "taaOption"),
                names(props.getProperty("rrGuideImages", "")));
    }

    /** True if DLSS can upscale the pack's frame. */
    boolean supportsDlss() {
        return supports.contains(DLSS) && pass != null;
    }

    /** True if FSR 3.1 can upscale the pack's frame. */
    boolean supportsFsr() {
        return supports.contains(FSR) && pass != null;
    }

    /** True if frame generation takes the frames DLSS or FSR make with the pack. */
    boolean supportsFrameGeneration() {
        return supports.contains(FRAME_GENERATION) && (supportsDlss() || supportsFsr());
    }

    /** True if DLSS Ray Reconstruction can take DLSS's place: the pack says so and writes the guides. */
    boolean supportsRayReconstruction() {
        return supports.contains(RAY_RECONSTRUCTION) && supportsDlss() && hasRayReconstructionGuides();
    }

    /** True if the settings screen sets the render resolution: the pack says so and has a way to take it. */
    boolean supportsRenderResolution() {
        return supports.contains(RENDER_RESOLUTION) && (bridgeScale || renderScaleOptions.size() == 2);
    }

    /**
     * True if the screen can pick the pack's own upscaling (TAAU): through its upscaling switch, or, for a pack that
     * leaves the resolution to the Bridge, at the screen's render resolution.
     */
    boolean supportsTaau() {
        return supports.contains(TAAU) && (bridgeScale ? supportsRenderResolution() : renderScaleOptions.size() == 2);
    }

    /** True if the screen can turn the pack's anti-aliasing off (its taaOption). */
    boolean supportsOff() {
        return supports.contains(OFF) && taaOption != null;
    }

    /**
     * True if the pack supports the anti-aliasing setting {@code engine} ({@link dev.creategalore.bridge.DisplaySettings#dlaaEngine}):
     * auto (DLSS or FSR), dlss, fsr3, off (its own, always), taau or none.
     */
    boolean supportsEngine(String engine) {
        return switch (engine) {
            case "auto" -> supportsDlss() || supportsFsr();
            case "dlss" -> supportsDlss();
            case "fsr3" -> supportsFsr();
            case "taau" -> supportsTaau();
            case "none" -> supportsOff();
            default -> true;
        };
    }

    /** True if the pack writes Ray Reconstruction's guides (at least diffuse and specular albedo and the normals). */
    boolean hasRayReconstructionGuides() {
        return rrGuideImages.size() >= 3;
    }

    /**
     * The render scale the pack's current options ask for: 1 without upscaling (or a profile without it), else the
     * scale option's value, from {@link #MIN_RENDER_SCALE} to 1. With renderScale=bridge, the one picked on the
     * Upscaling & Display screen (1 if the pack doesn't support render resolution).
     */
    float renderScale() {
        if (bridgeScale) {
            return supportsRenderResolution() ? RenderScaleTarget.bridgeScale() : 1.0f;
        }
        if (renderScaleOptions.size() != 2) {
            return 1.0f;
        }
        OptionValues values = Iris.getCurrentPack().map(pack -> pack.getShaderPackOptions().getOptionValues()).orElse(null);
        if (values == null || !values.getOptionSet().getBooleanOptions().containsKey(renderScaleOptions.get(0))
                || !values.getBooleanValueOrDefault(renderScaleOptions.get(0))
                || !values.getOptionSet().getStringOptions().containsKey(renderScaleOptions.get(1))) {
            return 1.0f;
        }
        for (String option : requireOn) {
            if (values.getOptionSet().getBooleanOptions().containsKey(option) && !values.getBooleanValueOrDefault(option)) {
                return 1.0f; // (the pack only upscales with its TAA on)
            }
        }
        try {
            float scale = Float.parseFloat(values.getStringValueOrDefault(renderScaleOptions.get(1)).trim());
            return Float.isFinite(scale) ? Math.max(MIN_RENDER_SCALE, Math.min(1.0f, scale)) : 1.0f;
        } catch (RuntimeException e) {
            return 1.0f;
        }
    }

    /** Null when the pack's current option values allow DLSS, else why not. */
    String optionProblem() {
        OptionValues values = Iris.getCurrentPack().map(pack -> pack.getShaderPackOptions().getOptionValues()).orElse(null);
        if (values == null) {
            return "no shader pack options";
        }
        for (String option : requireOn) {
            if (values.getOptionSet().getBooleanOptions().containsKey(option) && !values.getBooleanValueOrDefault(option)) {
                return "the pack option " + option + " is off";
            }
        }
        for (String option : requireOff) {
            if (values.getOptionSet().getBooleanOptions().containsKey(option) && values.getBooleanValueOrDefault(option)) {
                return "the pack option " + option + " is on";
            }
        }
        return null;
    }

    boolean keepsAlpha(int attachment) {
        for (int a : keepAlpha) {
            if (a == attachment) {
                return true;
            }
        }
        return false;
    }

    private static String required(Properties props, String key) throws IOException {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException(FILE + " has no " + key);
        }
        return value.trim();
    }

    private static String optional(Properties props, String key) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int[] ints(String list) {
        return names(list).stream().mapToInt(Integer::parseInt).toArray();
    }

    private static List<String> names(String list) {
        List<String> names = new ArrayList<>();
        for (String part : list.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }
}

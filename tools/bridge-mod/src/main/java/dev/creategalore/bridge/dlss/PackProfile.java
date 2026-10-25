package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

/**
 * shaders/create_galore_dlss.properties in a shader pack: where DLSS goes in that pack. Only packs patched for DLAA
 * have it (see {@link PhotonPatcher}); any other pack keeps its own anti-aliasing.
 *
 * pass: the composite pass after which DLSS runs (the pack's TAA pass, which the patch turns into a copy).
 * colorAttachment: that pass's colour output DLSS reads. outputAttachments: the outputs DLSS's result is written to;
 * keepAlpha: those of them whose alpha the pack still needs. motionImage, depthImage, exposureImage: the pack's
 * custom images holding motion vectors (render pixels, towards the previous frame), DLSS depth (1 near, 0 sky) and
 * the exposure (1x1). maskImage (optional): FSR 3's masks, reactive in red and transparency and composition in green
 * (RG16F, 0..1). requireOn / requireOff: pack options that must be on / off for the patch to apply (an option Iris
 * does not offer keeps the value the pack's source gives it, which the patcher checked).
 */
record PackProfile(String pass, int colorAttachment, int[] outputAttachments, int[] keepAlpha, String motionImage,
                   String depthImage, String exposureImage, String maskImage, List<String> requireOn, List<String> requireOff) {
    static final String FILE = "shaders/create_galore_dlss.properties";

    /** The current pack's profile, or null if it has none. */
    static PackProfile current() throws IOException {
        String name = Iris.getCurrentPackName();
        if (name == null || Iris.getCurrentPack().isEmpty()) {
            return null;
        }
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
        return new PackProfile(required(props, "pass"), Integer.parseInt(required(props, "colorAttachment")),
                ints(required(props, "outputAttachments")), ints(props.getProperty("keepAlpha", "")),
                required(props, "motionImage"), required(props, "depthImage"), required(props, "exposureImage"),
                props.getProperty("maskImage", "").isBlank() ? null : props.getProperty("maskImage").trim(),
                names(props.getProperty("requireOn", "")), names(props.getProperty("requireOff", "")));
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

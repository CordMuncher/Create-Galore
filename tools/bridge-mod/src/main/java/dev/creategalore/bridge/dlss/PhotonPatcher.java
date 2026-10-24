package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import dev.creategalore.bridge.DisplaySettings;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes "Photon v1.3a + DLAA" in shaderpacks/: a local copy of the pack's Photon v1.3a with DLSS in place of its TAA.
 * Photon's license allows personal changes; the copy is made on this computer from the pack's own zip and never
 * shared. Every change is an exact find-and-replace on Photon's text that must match once, so a different Photon
 * version is left alone instead of patched wrongly.
 *
 * While DLSS runs (cg_dlss_active, set by {@link Dlss}), the patched pack:
 * - jitters with the Bridge's Halton sequence (cg_dlss_jitter_x/y, in pixels) instead of its own;
 * - turns its TAA pass (composite4) into a copy that also writes, into custom images, motion vectors from Photon's
 *   own reprojection (Distant Horizons terrain included; the hand gets none, the sky rotation only), a DLSS depth and
 *   the exposure. The Bridge then runs DLSS and writes its result where TAA's would have gone;
 * - skips FXAA and CAS sharpening, so the picture is DLSS's own. With DLSS off it renders exactly as Photon does.
 */
public final class PhotonPatcher {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Bumped when the patch changes, so existing copies are made again. */
    private static final int PATCH_VERSION = 1;
    static final String SOURCE_ZIP = "photon_v1.3a.zip";
    static final String PATCHED_NAME = "Photon v1.3a + DLAA";
    private static final String MARKER = "create_galore_dlaa.txt";

    private static boolean prepared;

    private PhotonPatcher() {}

    /**
     * Makes the copy when DLAA is on. Runs while mixins load, before Iris first looks for its shader pack; touches
     * no game classes.
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        if (DisplaySettings.get(DisplaySettings.DLAA)) {
            run(FMLPaths.GAMEDIR.get().resolve("shaderpacks"));
        }
    }

    /** Makes or refreshes the patched copy if the pack's Photon zip is there. Never throws. */
    static void run(Path shaderpacks) {
        Path source = shaderpacks.resolve(SOURCE_ZIP);
        if (!Files.isRegularFile(source)) {
            LOG.info("DLAA: no shaderpacks/{}, so no Photon + DLAA copy", SOURCE_ZIP);
            return;
        }
        Path target = shaderpacks.resolve(PATCHED_NAME);
        try {
            String stamp = "patch " + PATCH_VERSION + " of " + SOURCE_ZIP + " sha256 " + sha256(Files.readAllBytes(source));
            Path marker = target.resolve(MARKER);
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(stamp)) {
                return;
            }
            Path work = Files.createTempDirectory(shaderpacks, ".dlaa-patch");
            try {
                try (FileSystem zip = FileSystems.newFileSystem(source)) {
                    copyTree(zip.getPath("/"), work);
                }
                for (Map.Entry<String, String[][]> file : edits().entrySet()) {
                    Path path = work.resolve(file.getKey());
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    for (String[] edit : file.getValue()) {
                        text = replaceOnce(file.getKey(), text, edit[0], edit[1]);
                    }
                    Files.writeString(path, text, StandardCharsets.UTF_8);
                }
                Files.writeString(work.resolve(PackProfile.FILE), PROFILE, StandardCharsets.UTF_8);
                Files.writeString(work.resolve(MARKER), stamp + "\n", StandardCharsets.UTF_8);
                deleteTree(target);
                Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
                LOG.info("DLAA: made shaderpacks/{} from {}", PATCHED_NAME, SOURCE_ZIP);
            } finally {
                deleteTree(work);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not make shaderpacks/{}: {}", PATCHED_NAME, e.toString());
        }
    }

    private static String replaceOnce(String file, String text, String find, String replace) throws IOException {
        int at = text.indexOf(find);
        if (at < 0 || text.indexOf(find, at + 1) >= 0) {
            throw new IOException(file + ": expected exactly one \"" + find.lines().findFirst().orElse(find)
                    + "\" (a different Photon version?)");
        }
        return text.substring(0, at) + replace + text.substring(at + find.length());
    }

    // Photon's files are LF-only; the anchors below are copied from v1.3a.
    private static Map<String, String[][]> edits() {
        Map<String, String[][]> edits = new LinkedHashMap<>();
        edits.put("shaders/shaders.properties", new String[][] {
                {"uniform.vec2.taa_offset = vec2(jitter_x / viewWidth, jitter_y / viewWidth)",
                        // DLSS wants the full sub-pixel range; Photon's vertex shaders scale this by 0.66.
                        "uniform.vec2.taa_offset = if(cg_dlss_active > 0.5, vec2(cg_dlss_jitter_x / (0.33 * viewWidth), "
                                + "cg_dlss_jitter_y / (0.33 * viewHeight)), vec2(jitter_x / viewWidth, jitter_y / viewWidth))\n"
                                + "\n"
                                + "# Create Galore DLAA: motion vectors, depth and exposure handed to DLSS (written by composite4)\n"
                                + "image.cg_motion_img = none rg rg16f half_float false true 1.0 1.0\n"
                                + "image.cg_depth_img = none red r32f float false true 1.0 1.0\n"
                                + "image.cg_exposure_img = none red r32f float false false 1 1"},
        });
        String[][] imageExtension = {{"#version 400 compatibility\n",
                "#version 400 compatibility\n#extension GL_ARB_shader_image_load_store : enable\n"}};
        edits.put("shaders/world0/composite4.fsh", imageExtension);
        edits.put("shaders/world-1/composite4.fsh", imageExtension);
        edits.put("shaders/world1/composite4.fsh", imageExtension);
        edits.put("shaders/program/c4_taa_exposure.fsh", new String[][] {
                {"void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy * taau_render_scale);\n",
                        C4_INPUTS + "void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy * taau_render_scale);\n" + C4_BRANCH},
        });
        edits.put("shaders/program/gbuffers_skytextured.vsh", new String[][] {
                {"uniform vec2 taa_offset;\n", "uniform vec2 taa_offset;\nuniform float cg_dlss_active;\n"},
                // The sun and moon jitter a little more than the world for Photon's TAA; DLSS needs them to match.
                {"    clip_pos.xy += taa_offset * clip_pos.w * 0.75;\n",
                        "    clip_pos.xy += taa_offset * clip_pos.w * (cg_dlss_active > 0.5 ? 0.66 : 0.75);\n"},
        });
        edits.put("shaders/program/c15_fxaa.fsh", new String[][] {
                {"uniform vec2 view_pixel_size;\n", "uniform vec2 view_pixel_size;\nuniform float cg_dlss_active;\n"},
                {"void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy);\n",
                        "void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy);\n\n"
                                + "    if (cg_dlss_active > 0.5) { // DLAA already anti-aliased this\n"
                                + "        scene_color = texelFetch(colortex0, texel, 0).rgb;\n"
                                + "        return;\n"
                                + "    }\n"},
        });
        edits.put("shaders/program/final.fsh", new String[][] {
                {"uniform vec2 taa_offset;\n", "uniform vec2 taa_offset;\nuniform float cg_dlss_active;\n"},
                {"#ifndef CAS\n    return display_eotf(texelFetch(sampler, texel, 0).rgb);\n#endif\n",
                        "#ifndef CAS\n    return display_eotf(texelFetch(sampler, texel, 0).rgb);\n#endif\n"
                                + "    if (cg_dlss_active > 0.5) { // DLAA's output is shown as DLSS made it\n"
                                + "        return display_eotf(texelFetch(sampler, texel, 0).rgb);\n"
                                + "    }\n"},
        });
        return edits;
    }

    private static final String C4_INPUTS = """
            // Create Galore DLAA: while DLSS runs this pass only copies the scene and hands DLSS its inputs.
            uniform float cg_dlss_active;
            layout(rg16f) writeonly uniform image2D cg_motion_img;
            layout(r32f) writeonly uniform image2D cg_depth_img;
            layout(r32f) writeonly uniform image2D cg_exposure_img;

            void cg_dlss_inputs(ivec2 texel) {
                float depth = texelFetch(depthtex0, texel, 0).x;
                vec3 screen_pos = vec3((vec2(texel) + 0.5) * view_pixel_size, depth);
                bool is_lod = false;
            #ifdef LOD_MOD_ACTIVE
                float depth_lod = texelFetch(lod_depth_tex, texel, 0).x;
                is_lod = is_lod_terrain(depth, depth_lod);
                if (is_lod) screen_pos.z = depth_lod;
            #endif
                bool sky = screen_pos.z >= 1.0;
                bool hand = depth < hand_depth;

                vec3 view_pos = screen_to_view_space(screen_pos, false, is_lod);
                vec2 motion = vec2(0.0); // the hand moves with the camera
                if (!hand) {
                    // Towards where this pixel was last frame, in pixels. The sky only turns with the camera.
                    vec3 previous = reproject_scene_space(view_to_scene_space(view_pos), sky, is_lod);
                    motion = (previous.xy - screen_pos.xy) * view_res;
                }
                // DLSS depth, reversed and infinite like most engines: 1 at the near plane, towards 0 far away, 0 sky.
                float dlss_depth = sky ? 0.0 : clamp(near / max(-view_pos.z, near), 0.0, 1.0);

                imageStore(cg_motion_img, texel, vec4(motion, 0.0, 0.0));
                imageStore(cg_depth_img, texel, vec4(dlss_depth));
                if (texel == ivec2(0)) imageStore(cg_exposure_img, ivec2(0), vec4(exposure));
            }

            """;

    private static final String C4_BRANCH = """

            #ifndef TAAU
                if (cg_dlss_active > 0.5) {
                    cg_dlss_inputs(texel);
                    // Photon's colour, unfiltered; the Bridge writes DLSS's result over it after this pass. Alpha 0
                    // restarts Photon's TAA history cleanly if DLSS stops; texel 0 keeps the exposure as usual.
                    result = vec4(texelFetch(colortex0, texel, 0).rgb, 0.0);
                    if (texel == ivec2(0)) result.a = exposure;
                    bloom_input = result.rgb;
                    return;
                }
            #endif
            """;

    private static final String PROFILE = """
            # Made by the Create Galore Bridge with this Photon + DLAA copy: where DLSS goes in this pack.
            pass=composite4
            colorAttachment=1
            outputAttachments=0,1
            keepAlpha=1
            motionImage=cg_motion_img
            depthImage=cg_depth_img
            exposureImage=cg_exposure_img
            requireOn=TAA
            requireOff=TAAU
            """;

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path dest = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    try (InputStream in = Files.newInputStream(path)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

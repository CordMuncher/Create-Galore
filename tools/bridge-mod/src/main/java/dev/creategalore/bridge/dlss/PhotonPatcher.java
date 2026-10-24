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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * The copy is made from Colorwheel Patcher's "photon_v1.3a + Clrwl_X.Y.Z.zip" when there is one, so Create's
 * contraptions keep their shader support (Colorwheel's programs take the same jitter, through taa_offset), and from
 * photon_v1.3a.zip otherwise. Colorwheel Patcher makes its zip during mod setup, after Iris has loaded its pack, so a
 * new or updated one is picked up when loading finishes ({@link #refresh()}, called by {@link Dlss}).
 *
 * While DLSS runs (cg_dlss_active, set by {@link Dlss}), the patched pack:
 * - jitters with the Bridge's Halton sequence (cg_dlss_jitter_x/y, in pixels) instead of its own;
 * - turns its TAA pass (composite4) into a copy that also writes, into custom images, motion vectors from Photon's
 *   own reprojection (Distant Horizons terrain included; the hand gets none, the sky rotation only), a DLSS depth and
 *   the exposure. The Bridge then runs DLSS and writes its result where TAA's would have gone;
 * - gives things that move by themselves their own motion vectors (waving plants and leaves, Sable's ships, Create's
 *   machines and contraptions through Colorwheel, entities, block entities and the hand through {@link EntityMotion}):
 *   the solid gbuffers programs write them into colortex2, which Photon itself only uses with normal or specular
 *   mapping, POM or TAAU, and composite4 takes them over the reprojection (see {@link #MOTION_INCLUDE});
 * - skips FXAA and CAS sharpening, so the picture is DLSS's own. With DLSS off it renders exactly as Photon does.
 */
public final class PhotonPatcher {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Bumped when the patch changes, so existing copies are made again. */
    private static final int PATCH_VERSION = 5;
    private static final String BASE_ZIP = "photon_v1.3a.zip";
    /** Colorwheel Patcher's copy of {@link #BASE_ZIP}; an outdated one gets a prefix and no longer matches. */
    private static final Pattern COLORWHEEL_ZIP = Pattern.compile("photon_v1\\.3a \\+ Clrwl_(\\d+)\\.(\\d+)\\.(\\d+)\\.zip");
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

    /**
     * When loading has finished, after Colorwheel Patcher's setup: makes the copy again if Colorwheel Patcher has just
     * made or updated its zip. True if it did, so the shaders need a reload when the copy is the pack in use.
     */
    static synchronized boolean refresh() {
        return DisplaySettings.get(DisplaySettings.DLAA) && run(FMLPaths.GAMEDIR.get().resolve("shaderpacks"));
    }

    /** Makes or refreshes the patched copy if the pack's Photon zip is there; true if it made it. Never throws. */
    static boolean run(Path shaderpacks) {
        Path source = source(shaderpacks);
        if (source == null) {
            LOG.info("DLAA: no shaderpacks/{}, so no Photon + DLAA copy", BASE_ZIP);
            return false;
        }
        String sourceName = source.getFileName().toString();
        Path target = shaderpacks.resolve(PATCHED_NAME);
        try {
            String stamp = "patch " + PATCH_VERSION + " of " + sourceName + " sha256 " + sha256(Files.readAllBytes(source));
            Path marker = target.resolve(MARKER);
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(stamp)) {
                return false;
            }
            Path work = Files.createTempDirectory(shaderpacks, ".dlaa-patch");
            try {
                try (FileSystem zip = FileSystems.newFileSystem(source)) {
                    copyTree(zip.getPath("/"), work);
                }
                for (Map.Entry<String, String[][]> file : edits().entrySet()) {
                    Path path = work.resolve(file.getKey());
                    if (OPTIONAL.contains(file.getKey()) && !Files.isRegularFile(path)) {
                        continue;
                    }
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    for (String[] edit : file.getValue()) {
                        text = replaceOnce(file.getKey(), text, edit[0], edit[1]);
                    }
                    Files.writeString(path, text, StandardCharsets.UTF_8);
                }
                Files.writeString(work.resolve(MOTION_INCLUDE_FILE), MOTION_INCLUDE, StandardCharsets.UTF_8);
                Files.writeString(work.resolve(PackProfile.FILE), PROFILE, StandardCharsets.UTF_8);
                Files.writeString(work.resolve(MARKER), stamp + "\n", StandardCharsets.UTF_8);
                deleteTree(target);
                Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
                // The first time, the copy starts with the player's Photon settings (Iris keeps them in PACK.txt).
                Path options = shaderpacks.resolve(PATCHED_NAME + ".txt");
                Path sourceOptions = shaderpacks.resolve(sourceName + ".txt");
                if (!Files.exists(options) && Files.isRegularFile(sourceOptions)) {
                    Files.copy(sourceOptions, options);
                }
                LOG.info("DLAA: made shaderpacks/{} from {}", PATCHED_NAME, sourceName);
                return true;
            } finally {
                deleteTree(work);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not make shaderpacks/{}: {}", PATCHED_NAME, e.toString());
            return false;
        }
    }

    /** Colorwheel Patcher's newest Photon zip, else the plain one, else null. */
    private static Path source(Path shaderpacks) {
        Path best = null;
        long bestVersion = -1;
        try (Stream<Path> files = Files.list(shaderpacks)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = COLORWHEEL_ZIP.matcher(file.getFileName().toString());
                if (m.matches() && Files.isRegularFile(file)) {
                    long version = Long.parseLong(m.group(1)) << 40 | Long.parseLong(m.group(2)) << 20 | Long.parseLong(m.group(3));
                    if (version > bestVersion) {
                        best = file;
                        bestVersion = version;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not list shaderpacks/: {}", e.toString());
        }
        if (best != null) {
            return best;
        }
        Path base = shaderpacks.resolve(BASE_ZIP);
        return Files.isRegularFile(base) ? base : null;
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
                // (final.fsh declares taa_offset only for its distance view, so this goes next to an unconditional one.)
                {"uniform float frameTimeCounter;\n", "uniform float frameTimeCounter;\nuniform float cg_dlss_active;\n"},
                {"#ifndef CAS\n    return display_eotf(texelFetch(sampler, texel, 0).rgb);\n#endif\n",
                        "#ifndef CAS\n    return display_eotf(texelFetch(sampler, texel, 0).rgb);\n#endif\n"
                                + "    if (cg_dlss_active > 0.5) { // DLAA's output is shown as DLSS made it\n"
                                + "        return display_eotf(texelFetch(sampler, texel, 0).rgb);\n"
                                + "    }\n"},
        });

        // Motion of things that move by themselves (MOTION_INCLUDE). The wind can be asked for an earlier time and
        // player position, so the solid programs can work out where a waving plant's vertex was last frame.
        edits.put("shaders/include/vertex/displacement.glsl", new String[][] {
                {"#include \"/include/misc/material_masks.glsl\"\n",
                        "#include \"/include/misc/material_masks.glsl\"\n\n"
                                + "// Create Galore DLAA: how far back in time and position to animate (set while the solid programs\n"
                                + "// work out last frame's position, see cg_motion.glsl).\n"
                                + "float cg_time_shift = 0.0;\n"
                                + "vec3 cg_eye_shift = vec3(0.0);\n"},
                {"        frameTimeCounter * wave_speed,\n", "        (frameTimeCounter - cg_time_shift) * wave_speed,\n"},
                {"    float t = wind_speed * frameTimeCounter;\n", "    float t = wind_speed * (frameTimeCounter - cg_time_shift);\n"},
                {"    vec3 to_player = eyePosition - world_pos;\n", "    vec3 to_player = eyePosition - cg_eye_shift - world_pos;\n"},
        });
        edits.put("shaders/program/gbuffers_all_solid.vsh", new String[][] {
                {"#include \"/include/vertex/utility.glsl\"\n", "#include \"/include/vertex/utility.glsl\"\n" + VERTEX_MOTION_INPUTS
                        + "\n#ifdef CG_MOTION_GBUFFER\n"
                        + "// Set by the Create Galore Bridge while Sable draws a ship: takes a vertex from where it is now (relative to\n"
                        + "// the camera) to where the ship had it last frame. All 0 for everything else (see ObjectMotion).\n"
                        + "uniform mat4 cg_object_motion;\n"
                        + "#endif\n"
                        + "\n#if defined CG_MOTION_GBUFFER && (defined PROGRAM_GBUFFERS_ENTITIES || defined PROGRAM_GBUFFERS_BLOCK \\\n"
                        + "    || defined PROGRAM_GBUFFERS_HAND || defined PROGRAM_GBUFFERS_SPIDEREYES)\n"
                        + "#define CG_VERTEX_PREV\n"
                        + "// Where the Create Galore Bridge had this vertex last frame (EntityMotion), in Iris's entity format: w 2\n"
                        + "// relative to the camera now, w 3 in the vertex's own space with this frame's model-view (the hand), else\n"
                        + "// not known.\n"
                        + "layout(location = " + EntityMotion.LOCATION + ") in vec4 cg_prev_pos;\n"
                        + "#endif\n"},
                {"    vec3 pos = transform(gl_ModelViewMatrix, gl_Vertex.xyz);\n"
                        + "    pos = view_to_scene_space(pos);\n"
                        + "    pos = pos + cameraPosition;\n"
                        + "    pos = animate_vertex(pos, is_top_vertex, light_levels.y, material_mask);\n"
                        + "    pos = pos - cameraPosition;\n",
                        vertexMotion("    ", false)},
                {"    vec4 clip_pos = project(gl_ProjectionMatrix, view_pos);\n",
                        "    vec4 clip_pos = project(gl_ProjectionMatrix, view_pos);\n"
                                + "#ifdef CG_MOTION_GBUFFER\n"
                                + "    cg_clip_now = clip_pos;\n"
                                + "#endif\n"},
        });
        // Create's contraptions, trains and Sable's ships through Colorwheel: the Bridge's Colorwheel hook sets
        // cg_flw_prev_pos, where Flywheel had the vertex last frame (DlssColorwheel).
        edits.put("shaders/program/clrwl_solid.vsh", new String[][] {
                {"#include \"/include/vertex/utility.glsl\"\n", "#include \"/include/vertex/utility.glsl\"\n" + VERTEX_MOTION_INPUTS
                        + "\n#ifdef CG_MOTION_GBUFFER\n"
                        + "// Last frame's flw_vertexPos (w 1), set by the Create Galore Bridge before this main runs; w 0 without it.\n"
                        + "vec4 cg_flw_prev_pos = vec4(0.0);\n"
                        + "#endif\n"},
                {"\tvec3 pos = transform(gl_ModelViewMatrix, gl_Vertex.xyz);\n"
                        + "\t     pos = view_to_scene_space(pos);\n"
                        + "\t     pos = pos + cameraPosition;\n"
                        + "\t     pos = animate_vertex(pos, is_top_vertex, light_levels.y, material_mask);\n"
                        + "\t     pos = pos - cameraPosition;\n",
                        vertexMotion("\t", true)},
                {"\tvec4 clip_pos = project(gl_ProjectionMatrix, view_pos);\n",
                        "\tvec4 clip_pos = project(gl_ProjectionMatrix, view_pos);\n"
                                + "#ifdef CG_MOTION_GBUFFER\n"
                                + "\tcg_clip_now = clip_pos;\n"
                                + "#endif\n"},
        });
        String[][] solidTargets = {{"#ifdef SPECULAR_MAPPING\n/* RENDERTARGETS: 1,2 */\n#endif\n",
                "#ifdef SPECULAR_MAPPING\n/* RENDERTARGETS: 1,2 */\n#endif\n\n"
                        + "#include \"/include/cg_motion.glsl\"\n"
                        + "#ifdef CG_MOTION_GBUFFER\n"
                        + "/* RENDERTARGETS: 1,2 */\n"
                        + "#endif\n"}};
        edits.put("shaders/program/gbuffers_all_solid.fsh", new String[][] {
                {solidTargets[0][0], solidTargets[0][1] + MOTION_FRAGMENT_INPUTS},
                {"#if defined PROGRAM_GBUFFERS_PARTICLES\n    // Kill the little rain splash particles\n",
                        MOTION_FRAGMENT_OUTPUT + "#if defined PROGRAM_GBUFFERS_PARTICLES\n    // Kill the little rain splash particles\n"},
        });
        // Lines and leads never move by themselves; they still say so, so colortex2 never keeps an older motion under them.
        edits.put("shaders/program/clrwl_solid.fsh", new String[][] {
                {solidTargets[0][0], solidTargets[0][1] + MOTION_FRAGMENT_INPUTS},
                {"#if defined PROGRAM_GBUFFERS_PARTICLES\n\t// Kill the little rain splash particles\n",
                        MOTION_FRAGMENT_OUTPUT + "#if defined PROGRAM_GBUFFERS_PARTICLES\n\t// Kill the little rain splash particles\n"},
        });
        edits.put("shaders/program/gbuffers_basic.fsh", new String[][] {
                {"/* RENDERTARGETS: 0,1,2 */\n",
                        "/* RENDERTARGETS: 0,1,2 */\n\n"
                                + "#include \"/include/cg_motion.glsl\"\n"
                                + "#ifdef CG_MOTION_GBUFFER\n"
                                + "uniform int frameCounter;\n"
                                + "#endif\n"},
                {"    gbuffer_data_1.w = pack_unorm_2x8(specular_map.zw);\n#endif\n}\n",
                        "    gbuffer_data_1.w = pack_unorm_2x8(specular_map.zw);\n#endif\n\n"
                                + "#ifdef CG_MOTION_GBUFFER\n"
                                + "    gbuffer_data_1 = cg_motion_encode(vec4(0.0), vec4(0.0), false, frameCounter, view_res);\n"
                                + "#endif\n}\n"},
        });
        return edits;
    }

    /** Files Colorwheel Patcher adds: patched when there, skipped in a copy made from the plain Photon zip. */
    private static final java.util.Set<String> OPTIONAL = java.util.Set.of("shaders/program/clrwl_solid.vsh", "shaders/program/clrwl_solid.fsh");

    private static final String VERTEX_MOTION_INPUTS = """
            #include "/include/cg_motion.glsl"

            #ifdef CG_MOTION_GBUFFER
            uniform mat4 gbufferPreviousModelView;
            uniform mat4 gbufferPreviousProjection;
            uniform vec3 previousCameraPosition;
            uniform float frameTime;

            out vec4 cg_clip_now;
            out vec4 cg_clip_prev;
            out float cg_moving;
            #endif
            """;

    private static final String MOTION_FRAGMENT_INPUTS = """
            #ifdef CG_MOTION_GBUFFER
            in vec4 cg_clip_now;
            in vec4 cg_clip_prev;
            in float cg_moving;
            #endif
            """;

    private static final String MOTION_FRAGMENT_OUTPUT = """
            #ifdef CG_MOTION_GBUFFER
                gbuffer_data_1 = cg_motion_encode(cg_clip_now, cg_clip_prev, cg_moving > 0.0, frameCounter, view_res);
            #endif

            """;

    /**
     * Photon's vertex position, from the model-view through its wind animation, with last frame's position worked out
     * beside it: the same animation at last frame's time and player position, from where the vertex was last frame
     * (for Colorwheel's programs where Flywheel had it, cg_flw_prev_pos; else where the ship carrying it had it,
     * cg_object_motion, or here). Kept relative to the camera, so the motion stays exact far from the world's origin.
     */
    private static String vertexMotion(String indent, boolean flywheel) {
        String i = indent;
        String cont = indent.equals("\t") ? "\t     " : indent;
        StringBuilder b = new StringBuilder();
        b.append(i).append("vec3 pos = transform(gl_ModelViewMatrix, gl_Vertex.xyz);\n");
        b.append(cont).append("pos = view_to_scene_space(pos);\n");
        b.append("#ifdef CG_MOTION_GBUFFER\n");
        if (flywheel) {
            b.append(i).append("bool cg_carried = cg_flw_prev_pos.w > 0.0 && cg_flw_prev_pos.xyz != gl_Vertex.xyz;\n");
            b.append(i).append("vec3 cg_scene_prev = cg_carried\n");
            b.append(i).append("    ? view_to_scene_space(transform(gl_ModelViewMatrix, cg_flw_prev_pos.xyz)) : pos;\n");
        } else {
            b.append(i).append("bool cg_carried = cg_object_motion[3][3] != 0.0;\n");
            b.append(i).append("vec3 cg_scene_prev = cg_carried ? (cg_object_motion * vec4(pos, 1.0)).xyz : pos;\n");
            b.append("#ifdef CG_VERTEX_PREV\n");
            b.append(i).append("bool cg_drawn_moving = (cg_prev_pos.w == 2.0 || cg_prev_pos.w == 3.0) && cg_prev_pos.xyz != gl_Vertex.xyz;\n");
            b.append(i).append("if (cg_drawn_moving) {\n");
            b.append(i).append("    cg_carried = true;\n");
            b.append(i).append("    cg_scene_prev = view_to_scene_space(transform(gl_ModelViewMatrix, cg_prev_pos.xyz));\n");
            b.append(i).append("}\n");
            b.append("#endif\n");
        }
        b.append("#endif\n");
        b.append(cont).append("pos = pos + cameraPosition;\n");
        b.append("#ifdef CG_MOTION_GBUFFER\n");
        b.append(i).append("vec3 cg_world_pos = pos;\n");
        b.append("#endif\n");
        b.append(cont).append("pos = animate_vertex(pos, is_top_vertex, light_levels.y, material_mask);\n");
        b.append("#ifdef CG_MOTION_GBUFFER\n");
        b.append(i).append("// Last frame's position: the same wind at last frame's time and player position.\n");
        b.append(i).append("vec3 cg_world_prev_base = cg_scene_prev + cameraPosition;\n");
        b.append(i).append("cg_time_shift = frameTime;\n");
        b.append(i).append("cg_eye_shift = cameraPosition - previousCameraPosition;\n");
        b.append(i).append("vec3 cg_world_prev = animate_vertex(cg_world_prev_base, is_top_vertex, light_levels.y, material_mask);\n");
        b.append(i).append("cg_time_shift = 0.0;\n");
        b.append(i).append("cg_eye_shift = vec3(0.0);\n");
        b.append(i).append("cg_moving = cg_carried || pos != cg_world_pos || cg_world_prev != cg_world_prev_base ? 1.0 : 0.0;\n");
        b.append(i).append("cg_scene_prev += cg_world_prev - cg_world_prev_base;\n");
        b.append(i).append("cg_clip_prev = gbufferPreviousProjection\n");
        b.append(i).append("    * vec4(transform(gbufferPreviousModelView, cg_scene_prev + cameraPosition - previousCameraPosition), 1.0);\n");
        if (!flywheel) {
            b.append("#ifdef CG_VERTEX_PREV\n");
            b.append(i).append("// The hand moves with the camera: only its own motion on the screen, through this frame's matrices.\n");
            b.append(i).append("if (cg_drawn_moving && cg_prev_pos.w == 3.0) {\n");
            b.append(i).append("    cg_clip_prev = project(gl_ProjectionMatrix, transform(gl_ModelViewMatrix, cg_prev_pos.xyz));\n");
            b.append(i).append("}\n");
            b.append("#endif\n");
        }
        b.append("#endif\n");
        b.append(cont).append("pos = pos - cameraPosition;\n");
        return b.toString();
    }

    private static final String MOTION_INCLUDE_FILE = "shaders/include/cg_motion.glsl";

    /**
     * shaders/include/cg_motion.glsl in the copy: motion vectors for things that move by themselves, so DLSS follows
     * them instead of smearing them. The solid gbuffers programs write colortex2 (Photon's second gbuffer, RGBA16):
     * xy the motion in render pixels towards the previous frame as two half floats, z full when xy is set (else
     * composite4 reprojects the depth as for everything else), w this frame's frameCounter, so a pixel nothing
     * solid drew this frame (the sky, Distant Horizons terrain) is never taken. Rendering into colortex2 keeps the
     * nearest surface's motion by the depth test. Photon only uses colortex2 itself with normal or specular mapping,
     * POM or TAAU; with any of those on, DLSS gets the reprojection alone as before.
     */
    private static final String MOTION_INCLUDE = """
            // Create Galore DLAA: motion vectors for things that move by themselves (see PhotonPatcher in the Bridge).
            #if !defined INCLUDE_CG_MOTION
            #define INCLUDE_CG_MOTION

            #if !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined POM && !defined TAAU
            #define CG_MOTION_GBUFFER
            #endif

            #ifdef CG_MOTION_GBUFFER
            // Half floats by hand: Photon's programs are GLSL 4.00, packHalf2x16 needs 4.20. Below 2^-14 pixels is 0.
            uint cg_half_bits(float v) {
                uint b = floatBitsToUint(v);
                uint sign_bit = (b >> 16u) & 0x8000u;
                int e = int((b >> 23u) & 0xFFu) - 112;
                if (e <= 0) return sign_bit;
                if (e >= 31) return sign_bit | 0x7BFFu;
                uint m = b & 0x7FFFFFu;
                return sign_bit | min((uint(e) << 10u | m >> 13u) + (m >> 12u & 1u), 0x7BFFu);
            }

            float cg_half_value(uint h) {
                uint e = h >> 10u & 0x1Fu;
                return e == 0u ? 0.0 : uintBitsToFloat((h & 0x8000u) << 16u | (e + 112u) << 23u | (h & 0x3FFu) << 13u);
            }

            // From the unjittered clip positions now and last frame; not moving, or behind the camera in either frame,
            // leaves it to composite4's reprojection.
            vec4 cg_motion_encode(vec4 clip_now, vec4 clip_prev, bool moving, int frame, vec2 res) {
                uvec2 bits = uvec2(0u);
                uint set = 0u;
                if (moving && clip_now.w > 0.0 && clip_prev.w > 0.0) {
                    vec2 motion = (clip_prev.xy / clip_prev.w - clip_now.xy / clip_now.w) * 0.5 * res;
                    bits = uvec2(cg_half_bits(motion.x), cg_half_bits(motion.y));
                    set = 65535u;
                }
                return vec4(vec2(bits), float(set), float(uint(frame) & 0xFFFFu)) / 65535.0;
            }

            // True if a solid program drew this pixel this frame with its own motion (render pixels, to last frame).
            bool cg_motion_decode(vec4 data, int frame, out vec2 motion) {
                uvec4 u = uvec4(round(data * 65535.0));
                motion = vec2(cg_half_value(u.x), cg_half_value(u.y));
                return u.z == 65535u && u.w == (uint(frame) & 0xFFFFu);
            }
            #endif

            #endif
            """;

    private static final String C4_INPUTS = """
            // Create Galore DLAA: while DLSS runs this pass only copies the scene and hands DLSS its inputs.
            uniform float cg_dlss_active;
            layout(rg16f) writeonly uniform image2D cg_motion_img;
            layout(r32f) writeonly uniform image2D cg_depth_img;
            layout(r32f) writeonly uniform image2D cg_exposure_img;

            #include "/include/cg_motion.glsl"
            #ifdef CG_MOTION_GBUFFER
            uniform sampler2D colortex2; // own motion of things that move by themselves (never TAAU's, see cg_motion.glsl)
            uniform float cg_dlss_object_motion; // 0 while testing without it
            uniform float cg_dlss_residual_albedo; // 1 in the residual test view: surface colours instead of the lit scene
            uniform sampler2D colortex1; // gbuffer data 0 (TAAU, which uses it here, is off)
            uniform sampler2D depthtex1;
            uniform int frameCounter;
            #endif

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
            #ifdef CG_MOTION_GBUFFER
                // Something that moves by itself gave its own motion; behind a translucent surface that surface's stays.
                vec2 own_motion;
                if (cg_dlss_object_motion > 0.5 && !is_lod && cg_motion_decode(texelFetch(colortex2, texel, 0), frameCounter, own_motion)
                        && texelFetch(depthtex1, texel, 0).x == depth) {
                    motion = own_motion;
                }
            #endif
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
            #ifdef CG_MOTION_GBUFFER
                    if (cg_dlss_residual_albedo > 0.5) {
                        // For the residual test view: surface colours have none of the lighting's frame-to-frame noise,
                        // so what is left after following the motion vectors is their error. Black sky, and no albedo
                        // left over from earlier frames (colortex1 is not cleared).
                        float data_x = texelFetch(colortex1, texel, 0).x;
                        float data_y = texelFetch(colortex1, texel, 0).y;
                        vec2 rg; rg.x = modf((65535.0 / 256.0) * data_x, rg.y);
                        vec2 b_; b_.x = modf((65535.0 / 256.0) * data_y, b_.y);
                        vec3 albedo = vec3(rg * vec2(256.0 / 255.0, 1.0 / 255.0), b_.x * (256.0 / 255.0));
                        result.rgb = texelFetch(depthtex0, texel, 0).x < 1.0 ? albedo / max(exposure, 1e-4) : vec3(0.0);
                    }
            #endif
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

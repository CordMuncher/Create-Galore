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
 * - marks for FSR 3 where it should take this frame over its history ({@link #REACTIVE_INCLUDE}): particles, rain and
 *   the nether portal (the translucent programs), lava and fire (their materials); DLSS's current models take no
 *   such mask;
 * - keeps particles behind the first-person hand: Photon draws them into its translucent layer before the hand and
 *   blends that layer over it, so they showed on top of the hand and, with the hand's motion vectors, smeared there.
 *   The solid hand now clears that layer where it draws, as Minecraft itself draws the hand over everything;
 * - skips FXAA and CAS sharpening, so the picture is DLSS's own. With DLSS off it renders as Photon does, apart from
 *   the hand fix;
 * - upscales with Photon's own TAAU path: with TAAU on, the scene renders into the lower left corner of the buffers
 *   at the render size and composite4 makes the window-sized picture, so DLSS or FSR take the corner and make it
 *   instead. The render scale comes from the Bridge (taau_render_scale, TAAU_RENDER_SCALE rounded to whole pixels per
 *   axis, offered down to 0.25) so the render size is exact, and the textures' mip bias with it (cg_mip_bias).
 *   Photon's own TAAU renders the same size, so it can be compared at the same internal resolution.
 *
 * For DLSS Ray Reconstruction (dev builds, see {@link Dlss}), while cg_rr_active is 1 the patched pack also:
 * - writes Ray Reconstruction's guides ({@link #RR_INCLUDE}): deferred4 for the solid surfaces and the sky, the water
 *   program for translucent blocks in front of them (their normal, roughness, reflectance and reflection distance; the
 *   diffuse colour stays the one seen through them);
 * - with cg_rr_raw_reflections 1, gives Photon's own rough materials (metals, polished blocks and the like) rough
 *   reflections at one ray per pixel from white noise, which Photon otherwise has only with a LabPBR resource pack,
 *   and leaves their noise to Ray Reconstruction; the reflection's ray length goes into the hit distance guide;
 * - with cg_rr_raw_shadows 1, turns the soft shadows' sample patterns with white noise instead of Photon's blue noise
 *   that steps each frame (NVIDIA: no screen-space dithering, no patterns shared between pixels);
 * - with cg_rr_exposed 1, hands DLSS its colour times its exposure (Ray Reconstruction takes no exposure).
 */
public final class PhotonPatcher {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Bumped when the patch changes, so existing copies are made again. */
    private static final int PATCH_VERSION = 13;
    private static final String BASE_ZIP = "photon_v1.3a.zip";
    /** Colorwheel Patcher's copy of {@link #BASE_ZIP}; an outdated one gets a prefix and no longer matches. */
    private static final Pattern COLORWHEEL_ZIP = Pattern.compile("photon_v1\\.3a \\+ Clrwl_(\\d+)\\.(\\d+)\\.(\\d+)\\.zip");
    static final String PATCHED_NAME = "Photon v1.3a + DLAA";
    private static final String MARKER = "create_galore_dlaa.txt";

    private static boolean prepared;

    private PhotonPatcher() {}

    /**
     * Makes the copy unless DLAA is set to off. Runs while mixins load, before Iris first looks for its shader pack; touches
     * no game classes.
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        if (!"off".equals(DisplaySettings.dlaaEngine())) {
            run(FMLPaths.GAMEDIR.get().resolve("shaderpacks"));
        }
    }

    /**
     * When loading has finished, after Colorwheel Patcher's setup: makes the copy again if Colorwheel Patcher has just
     * made or updated its zip. True if it did, so the shaders need a reload when the copy is the pack in use.
     */
    static synchronized boolean refresh() {
        return !"off".equals(DisplaySettings.dlaaEngine()) && run(FMLPaths.GAMEDIR.get().resolve("shaderpacks"));
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
                for (Map.Entry<String, String[][]> file : edits(work).entrySet()) {
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
                Files.writeString(work.resolve(REACTIVE_INCLUDE_FILE), REACTIVE_INCLUDE, StandardCharsets.UTF_8);
                Files.writeString(work.resolve(RR_INCLUDE_FILE), RR_INCLUDE, StandardCharsets.UTF_8);
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

    // Photon's files are LF-only; the anchors below are copied from v1.3a. {@code work} is the unpacked copy, for the
    // per-world program files that only include a shared one.
    private static Map<String, String[][]> edits(Path work) throws IOException {
        Map<String, String[][]> edits = new LinkedHashMap<>();
        edits.put("shaders/shaders.properties", new String[][] {
                {"uniform.vec2.taa_offset = vec2(jitter_x / viewWidth, jitter_y / viewWidth)",
                        // DLSS wants the full sub-pixel range; Photon's vertex shaders scale this by 0.66, except
                        // with TAAU, where a render pixel is a pixel of the buffers (the frame is drawn into their
                        // lower left corner).
                        "#if defined TAA && defined TAAU\n"
                                + "uniform.vec2.taa_offset = if(cg_dlss_active > 0.5, vec2(2.0 * cg_dlss_jitter_x / viewWidth, "
                                + "2.0 * cg_dlss_jitter_y / viewHeight), vec2(jitter_x / viewWidth, jitter_y / viewWidth))\n"
                                + "#else\n"
                                + "uniform.vec2.taa_offset = if(cg_dlss_active > 0.5, vec2(cg_dlss_jitter_x / (0.33 * viewWidth), "
                                + "cg_dlss_jitter_y / (0.33 * viewHeight)), vec2(jitter_x / viewWidth, jitter_y / viewWidth))\n"
                                + "#endif\n"
                                + "\n"
                                + "# Create Galore DLAA: motion vectors, depth and exposure handed to DLSS (written by composite4)\n"
                                + "image.cg_motion_img = none rg rg16f half_float false true 1.0 1.0\n"
                                + "image.cg_depth_img = none red r32f float false true 1.0 1.0\n"
                                + "image.cg_exposure_img = none red r32f float false false 1 1\n"
                                + "# FSR 3's masks: what the translucent programs mark (cleared every frame), and both masks as composite4\n"
                                + "# hands them over (see cg_reactive.glsl)\n"
                                + "image.cg_reactive_img = none red_integer r32ui unsigned_int true true 1.0 1.0\n"
                                + "image.cg_mask_img = none rg rg16f half_float false true 1.0 1.0\n"
                                + "# Create Galore Ray Reconstruction (dev builds): its guides, written by deferred4 and the water program (see cg_rr.glsl)\n"
                                + "image.cg_rr_albedo_img = none rgba rgba8 unsigned_byte false true 1.0 1.0\n"
                                + "image.cg_rr_specular_img = none rgba rgba8 unsigned_byte false true 1.0 1.0\n"
                                + "image.cg_rr_normal_img = none rgba rgba16f half_float false true 1.0 1.0\n"
                                + "image.cg_rr_hitdist_img = none red r32f float false true 1.0 1.0"},
        });

        // Upscaling (TAAU): the render scale comes from the Bridge, per axis and in whole render pixels, so DLSS
        // and FSR get an exact render size and Photon's own TAAU renders the same one; the texture mip bias with it.
        edits.put("shaders/include/global.glsl", new String[][] {
                {"#if defined TAA && defined TAAU\nconst float taau_render_scale = TAAU_RENDER_SCALE;\n#else\n",
                        "#if defined TAA && defined TAAU\n"
                                + "// Create Galore DLAA: the render size over the window's, per axis: TAAU_RENDER_SCALE rounded to whole\n"
                                + "// pixels by the Create Galore Bridge. A uniform under Photon's own name, as mods that look for it in the\n"
                                + "// program still find it (Create Deep Seas culls the terrain behind ship hulls at gl_FragCoord over it).\n"
                                + "uniform vec2 taau_render_scale;\n"
                                + "#else\n"},
                {"\n// Helper functions\n",
                        "\n// Create Galore DLAA: the textures' mip bias, log2 of the render scale (as Photon has it), plus the Bridge's\n"
                                + "// dlaaMipBias while DLSS or FSR runs.\n"
                                + "uniform float cg_mip_bias;\n"
                                + "\n// Helper functions\n"},
                {"\n// Helper functions\n", RR_GLOBAL + "\n// Helper functions\n"},
        });
        StringBuilder lowScales = new StringBuilder();
        for (int percent = 25; percent < 50; percent++) {
            lowScales.append("0.").append(percent).append(' ');
        }
        edits.put("shaders/settings.glsl", new String[][] {
                {"#define TAAU_RENDER_SCALE 0.75 // [0.50 ", "#define TAAU_RENDER_SCALE 0.75 // [" + lowScales + "0.50 "},
        });
        edits.put("shaders/include/lighting/specular_lighting.glsl", new String[][] {
                {"vec3 get_sky_reflection(vec3 ray_dir, float skylight, vec3 hit_pos) {",
                        "// Create Galore Ray Reconstruction: how far the last specular ray went before it hit something (65504: nothing,\n"
                                + "// the sky), for its hit distance guide.\n"
                                + "float cg_ssr_hit_distance = 65504.0;\n\n"
                                + "vec3 get_sky_reflection(vec3 ray_dir, float skylight, vec3 hit_pos) {"},
                {"        vec3 hit_pos_scene = view_to_scene_space(hit_pos_view);\n",
                        "        vec3 hit_pos_scene = view_to_scene_space(hit_pos_view);\n"
                                + "        cg_ssr_hit_distance = min(cg_ssr_hit_distance, distance(view_pos, hit_pos_view));\n"},
                {"    float dither =\n        r1(frameCounter,\n           texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b);\n",
                        "    float dither =\n        r1(frameCounter,\n           texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b);\n\n"
                                + "    // Create Galore Ray Reconstruction: while it cleans them up, white noise for each pixel and frame instead.\n"
                                + "    bool cg_raw = cg_rr_raw_reflections > 0.5;\n"
                                + "    vec4 cg_noise = cg_white_noise(ivec2(gl_FragCoord.xy), frameCounter, 1u);\n"
                                + "    if (cg_raw) dither = cg_noise.z;\n"
                                + "    cg_ssr_hit_distance = 65504.0;\n"},
                {"#if defined SSR_ROUGHNESS_SUPPORT && defined SPECULAR_MAPPING\n    if (!is_water) { // Rough reflection\n",
                        "#if defined SSR_ROUGHNESS_SUPPORT && defined SPECULAR_MAPPING\n    if (!is_water) { // Rough reflection\n"
                                + "#elif defined SSR_ROUGHNESS_SUPPORT\n"
                                + "    // Create Galore Ray Reconstruction: rough reflections for Photon's own rough materials too, while it\n"
                                + "    // cleans them up (Photon has them only with a resource pack's specular maps).\n"
                                + "    if (!is_water && cg_raw && material.roughness > 0.05) {\n"
                                + "#else\n"
                                + "    if (false) {\n"
                                + "#endif\n"},
                {"        for (int i = 0; i < SSR_RAY_COUNT; ++i) {\n",
                        "        // Create Galore Ray Reconstruction: one ray, its direction from white noise.\n"
                                + "        int cg_ray_count = cg_raw ? 1 : SSR_RAY_COUNT;\n"
                                + "        for (int i = 0; i < cg_ray_count; ++i) {\n"},
                {"                frameCounter * SSR_RAY_COUNT + i\n            );\n\n",
                        "                frameCounter * SSR_RAY_COUNT + i\n            );\n"
                                + "            if (cg_raw) hash = cg_noise.xy;\n\n"},
                {"        reflection *= albedo_tint * rcp(float(SSR_RAY_COUNT));\n",
                        "        reflection *= albedo_tint * rcp(float(cg_ray_count));\n"},
                {"#else\n    // Fade reflection when rough reflections are disabled\n",
                        "#if !(defined SSR_ROUGHNESS_SUPPORT && defined SPECULAR_MAPPING)\n"
                                + "    // Fade reflection when rough reflections are disabled\n"},
        });
        // Soft shadows: white noise for each pixel and frame while Ray Reconstruction cleans them up.
        String[][] shadowNoise = {{"    dither = r1(frameCounter, dither);\n",
                "    dither = r1(frameCounter, dither);\n"
                        + "    if (cg_rr_raw_shadows > 0.5) dither = cg_white_noise(ivec2(gl_FragCoord.xy), frameCounter, 2u).x; // Create Galore RR\n"}};
        edits.put("shaders/include/lighting/shadows/pcss.glsl", shadowNoise);
        edits.put("shaders/include/lighting/shadows/ssrt.glsl", shadowNoise);
        // Ray Reconstruction's guides: deferred4 for the solid surfaces and the sky.
        String[][] rrGuides = {{"#version 400 compatibility\n",
                "#version 400 compatibility\n#extension GL_ARB_shader_image_load_store : enable\n#define CG_RR_GUIDES\n"}};
        for (String world : new String[] {"world0", "world-1", "world1"}) {
            edits.put("shaders/" + world + "/deferred4.fsh", rrGuides);
        }
        edits.put("shaders/program/d4_deferred_shading.fsh", new String[][] {
                {"#include \"/include/utility/space_conversion.glsl\"\n",
                        "#include \"/include/utility/space_conversion.glsl\"\n#include \"/include/cg_rr.glsl\"\n"},
                {"    if (depth == 1.0) { // Sky\n",
                        "    if (depth == 1.0) { // Sky\n"
                                + "#ifdef CG_RR_GUIDES\n"
                                + "        if (cg_rr_active > 0.5) cg_rr_write_sky(texel); // Create Galore Ray Reconstruction (cg_rr.glsl)\n"
                                + "#endif\n"},
                {"        // Edge highlight\n",
                        "#ifdef CG_RR_GUIDES\n"
                                + "        // Create Galore Ray Reconstruction: what this surface is made of (cg_rr.glsl).\n"
                                + "        if (cg_rr_active > 0.5) {\n"
                                + "#if defined ENVIRONMENT_REFLECTIONS || defined SKY_REFLECTIONS\n"
                                + "            float cg_hit = material.ssr_multiplier > eps ? cg_ssr_hit_distance : 65504.0;\n"
                                + "#else\n"
                                + "            float cg_hit = 65504.0;\n"
                                + "#endif\n"
                                + "            cg_rr_write_surface(texel, material, normal, NoV, cg_hit, true);\n"
                                + "        }\n"
                                + "#endif\n\n"
                                + "        // Edge highlight\n"},
        });

        // The clouds' depth helper took the scale as a float.
        edits.put("shaders/program/d1_clouds.fsh", new String[][] {
                {"float depth_max_4x4(sampler2D depth_sampler, float scale) {", "float depth_max_4x4(sampler2D depth_sampler, vec2 scale) {"},
                {"rcp(float(taau_render_scale));", "rcp(vec2(taau_render_scale));"},
                {"depth_max_4x4(depthtex1, taau_render_scale);", "depth_max_4x4(depthtex1, vec2(taau_render_scale));"},
                {"depth_max_4x4(lod_depth_tex, lod_depth_tex_scale);", "depth_max_4x4(lod_depth_tex, vec2(lod_depth_tex_scale));"},
        });
        // TAAU's colour limits for its own resolve go into colortex1 and colortex2; while DLSS runs this pass hands
        // on what is there instead (the gbuffer data and the motion of things that move by themselves).
        edits.put("shaders/program/c3_taau_prep.fsh", new String[][] {
                {"layout(location = 0) out vec3 min_color;\nlayout(location = 1) out vec3 max_color;\n",
                        "layout(location = 0) out vec4 cg_out_1;\nlayout(location = 1) out vec4 cg_out_2;\n"},
                {"uniform sampler2D colortex0;\n",
                        "uniform sampler2D colortex0;\n\n"
                                + "// Create Galore DLAA: while DLSS runs, composite4 takes colortex1 and colortex2 as the gbuffers left them.\n"
                                + "uniform sampler2D colortex1;\n"
                                + "uniform sampler2D colortex2;\n"
                                + "uniform float cg_dlss_active;\n"
                                + "vec3 min_color;\n"
                                + "vec3 max_color;\n"},
                {"void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy);\n",
                        "void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy);\n\n"
                                + "    if (cg_dlss_active > 0.5) {\n"
                                + "        cg_out_1 = texelFetch(colortex1, texel, 0);\n"
                                + "        cg_out_2 = texelFetch(colortex2, texel, 0);\n"
                                + "        return;\n"
                                + "    }\n"},
                // (Photon's file ends with a stray #endif, which Iris's preprocessor logs as an error whenever TAAU is on.)
                {"    max_color = max_color * 0.5 + 0.5;\n}\n\n#endif\n", "    max_color = max_color * 0.5 + 0.5;\n\n"
                        + "    cg_out_1 = vec4(min_color, 1.0);\n"
                        + "    cg_out_2 = vec4(max_color, 1.0);\n}\n\n"},
        });
        String[][] imageExtension = {{"#version 400 compatibility\n",
                "#version 400 compatibility\n#extension GL_ARB_shader_image_load_store : enable\n"}};
        edits.put("shaders/world0/composite4.fsh", imageExtension);
        edits.put("shaders/world-1/composite4.fsh", imageExtension);
        edits.put("shaders/world1/composite4.fsh", imageExtension);
        // Every world's programs drawn by the translucent and weather programs mark FSR's reactive mask (image atomics).
        String[][] reactiveExtension = {{"#version 400 compatibility\n",
                "#version 400 compatibility\n#extension GL_ARB_shader_image_load_store : enable\n#define CG_REACTIVE_MASK\n"}};
        for (String world : new String[] {"world0", "world-1", "world1"}) {
            Path dir = work.resolve("shaders").resolve(world);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".fsh") && Files.isRegularFile(file)) {
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        if (text.contains("#include \"/program/gbuffers_all_translucent.fsh\"")
                                || text.contains("#include \"/program/gbuffers_weather.fsh\"")) {
                            // (The water program, translucent blocks, also writes Ray Reconstruction's guides.)
                            edits.put("shaders/" + world + "/" + name, text.contains("#define PROGRAM_GBUFFERS_WATER\n")
                                    ? new String[][] {{reactiveExtension[0][0], reactiveExtension[0][1] + "#define CG_RR_GUIDES\n"}}
                                    : reactiveExtension);
                        }
                    }
                }
            }
        }
        edits.put("shaders/program/gbuffers_all_translucent.fsh", new String[][] {
                {"#include \"/include/global.glsl\"\n", "#include \"/include/global.glsl\"\n#include \"/include/cg_reactive.glsl\"\n"},
                {"#include \"/include/utility/space_conversion.glsl\"\n",
                        "#include \"/include/utility/space_conversion.glsl\"\n#include \"/include/cg_rr.glsl\"\n"},
                {"    // Refraction data\n", """
                        #if defined CG_RR_GUIDES && defined PROGRAM_GBUFFERS_WATER
                            // Create Galore Ray Reconstruction: a translucent block in front of the solid surface gives its own
                            // normal, roughness, reflectance and reflection distance, as the depth and motion vectors are its own too;
                            // the diffuse colour stays the one seen through it (cg_rr.glsl).
                            if (cg_rr_active > 0.5 && depth0 <= depth1) {
                        #if defined ENVIRONMENT_REFLECTIONS || defined SKY_REFLECTIONS
                                float cg_hit = material.ssr_multiplier > eps ? cg_ssr_hit_distance : 65504.0;
                        #else
                                float cg_hit = 65504.0;
                        #endif
                                cg_rr_write_surface(ivec2(gl_FragCoord.xy), material, normal, NoV, cg_hit, false);
                            }
                        #endif

                            // Refraction data
                        """},
                {"    // Refraction data\n", """
                        #ifdef CG_REACTIVE_MASK
                            // Create Galore DLAA: particles by their opacity, and the nether portal's swirl (cg_reactive.glsl).
                            float cg_reactive = is_nether_portal ? CG_REACTIVE_PORTAL : 0.0;
                        #if defined PROGRAM_GBUFFERS_TEXTURED || defined PROGRAM_GBUFFERS_PARTICLES_TRANSLUCENT
                            cg_reactive = max(cg_reactive, fragment_color.a * CG_REACTIVE_PARTICLES);
                        #endif
                            cg_mark_reactive(cg_reactive, depth1);
                        #endif

                            // Refraction data
                        """},
        });
        edits.put("shaders/program/gbuffers_weather.fsh", new String[][] {
                {"#include \"/include/global.glsl\"\n", "#include \"/include/global.glsl\"\n#include \"/include/cg_reactive.glsl\"\n"
                        + "#ifdef CG_REACTIVE_MASK\nuniform sampler2D depthtex1;\n#endif\n"},
                {"    frag_color.rgb *= frag_color.a;\n}", """
                            frag_color.rgb *= frag_color.a;

                        #ifdef CG_REACTIVE_MASK
                            // Create Galore DLAA: rain and snow by their opacity (cg_reactive.glsl).
                            cg_mark_reactive(base_color.a * (is_rain ? CG_REACTIVE_RAIN : CG_REACTIVE_SNOW),
                                texelFetch(depthtex1, ivec2(gl_FragCoord.xy), 0).x);
                        #endif
                        }"""},
        });
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
                {solidTargets[0][0], solidTargets[0][1] + MOTION_FRAGMENT_INPUTS + HAND_COVER_OUTPUT},
                {"#if defined PROGRAM_GBUFFERS_PARTICLES\n    // Kill the little rain splash particles\n",
                        MOTION_FRAGMENT_OUTPUT + HAND_COVER_WRITE
                                + "#if defined PROGRAM_GBUFFERS_PARTICLES\n    // Kill the little rain splash particles\n"},
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
        // The texture mip bias follows the Bridge (cg_mip_bias, see global.glsl above).
        String[] mipBias = {"const float lod_bias = log2(taau_render_scale);\n", "#define lod_bias cg_mip_bias\n"};
        for (String program : new String[] {"gbuffers_all_solid", "gbuffers_all_translucent", "gbuffers_armor_glint",
                "gbuffers_damagedblock", "clrwl_solid", "clrwl_translucent", "clrwl_glint", "clrwl_damagedblock"}) {
            edits.merge("shaders/program/" + program + ".fsh", new String[][] {mipBias}, (a, b) -> {
                String[][] both = java.util.Arrays.copyOf(a, a.length + b.length);
                System.arraycopy(b, 0, both, a.length, b.length);
                return both;
            });
        }
        return edits;
    }

    /** Files Colorwheel Patcher adds: patched when there, skipped in a copy made from the plain Photon zip. */
    private static final java.util.Set<String> OPTIONAL = java.util.Set.of("shaders/program/clrwl_solid.vsh", "shaders/program/clrwl_solid.fsh",
            "shaders/program/clrwl_translucent.fsh", "shaders/program/clrwl_glint.fsh", "shaders/program/clrwl_damagedblock.fsh");

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

    /**
     * The solid hand also writes colortex13, Photon's translucent layer, with nothing: particles drawn there before the
     * hand (Photon blends that layer over the hand) no longer show through it. Translucent parts of held items are
     * drawn after the solid hand, so they stay. Last of the program's RENDERTARGETS, so it is the one Iris uses. Where
     * the program keeps no colortex2 (normal mapping, specular mapping and motion vectors off, as with POM alone or
     * TAAU), gbuffer_data_1 carries the cover to colortex13 instead, so colortex2 stays as Photon leaves it.
     */
    private static final String HAND_COVER_OUTPUT = """
            #ifdef PROGRAM_GBUFFERS_HAND
            #if defined NORMAL_MAPPING || defined SPECULAR_MAPPING || defined CG_MOTION_GBUFFER
            layout(location = 2) out vec4 cg_hand_cover;
            /* RENDERTARGETS: 1,2,13 */
            #else
            /* RENDERTARGETS: 1,13 */
            #endif
            #endif
            """;

    private static final String HAND_COVER_WRITE = """
            #ifdef PROGRAM_GBUFFERS_HAND
            #if defined NORMAL_MAPPING || defined SPECULAR_MAPPING || defined CG_MOTION_GBUFFER
                cg_hand_cover = vec4(0.0);
            #else
                gbuffer_data_1 = vec4(0.0);
            #endif
            #endif

            """;

    private static final String MOTION_FRAGMENT_OUTPUT = """
            #ifdef CG_MOTION_GBUFFER
                gbuffer_data_1 = cg_motion_encode(cg_clip_now, cg_clip_prev, cg_moving > 0.0, frameCounter, view_res * taau_render_scale);
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
     * nearest surface's motion by the depth test. Photon only uses colortex2 itself with normal or specular mapping
     * (and POM is left out too); with any of those on, DLSS gets the reprojection alone as before. TAAU keeps its
     * colour limits there from composite3 on, which hands colortex2 on unchanged while DLSS runs instead.
     */
    private static final String MOTION_INCLUDE = """
            // Create Galore DLAA: motion vectors for things that move by themselves (see PhotonPatcher in the Bridge).
            #if !defined INCLUDE_CG_MOTION
            #define INCLUDE_CG_MOTION

            #if !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined POM
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

    private static final String REACTIVE_INCLUDE_FILE = "shaders/include/cg_reactive.glsl";

    /**
     * shaders/include/cg_reactive.glsl in the copy: FSR 3's reactive mask. Particles, rain, snow and the nether portal
     * change in ways no motion vector follows (they move on their own, their textures scroll or swirl), so FSR should
     * take this frame there instead of blending in its history, which smears them or fades them out. The translucent
     * programs mark how much, per pixel, in front of the solid surface (the largest value wins); composite4 adds the
     * solid materials whose textures animate and hands both to the Bridge (cg_mask_img). The programs whose world
     * files enable image stores define CG_REACTIVE_MASK. AMD advises no more than 0.9.
     */
    private static final String REACTIVE_INCLUDE = """
            // Create Galore DLAA: FSR 3's reactive mask (see PhotonPatcher in the Bridge).
            #if !defined INCLUDE_CG_REACTIVE
            #define INCLUDE_CG_REACTIVE

            #ifdef CG_REACTIVE_MASK
            #define CG_REACTIVE_PARTICLES 0.9 // times the particle's opacity
            #define CG_REACTIVE_RAIN 0.5 // times the rain texture's opacity
            #define CG_REACTIVE_SNOW 0.7
            #define CG_REACTIVE_PORTAL 0.6

            layout(r32ui) uniform uimage2D cg_reactive_img;

            // Marks value (0..1) here if this fragment is in front of the solid surface (solid_depth, from depthtex1): the
            // fragment shader can run for hidden fragments too, as these programs discard.
            void cg_mark_reactive(float value, float solid_depth) {
                if (value > 0.0 && gl_FragCoord.z <= solid_depth) {
                    imageAtomicMax(cg_reactive_img, ivec2(gl_FragCoord.xy), uint(clamp(value, 0.0, 1.0) * 255.0 + 0.5));
                }
            }
            #endif

            #endif
            """;

    /**
     * In global.glsl, for every program: the Ray Reconstruction switches the Bridge sets, and white noise. NVIDIA's
     * guide asks for samples independent between pixels and frames, from a hash with few BigCrush failures (it points to
     * Jarzynski and Olano, "Hash Functions for GPU Rendering", JCGT 2020); pcg4d is that paper's pick.
     */
    private static final String RR_GLOBAL = """

            // Create Galore Ray Reconstruction (dev builds, see PhotonPatcher in the Bridge): 1 while the pack writes its
            // guides, and while it leaves rough reflections and soft shadows noisy for it to clean up.
            uniform float cg_rr_active;
            uniform float cg_rr_raw_reflections;
            uniform float cg_rr_raw_shadows;

            uvec4 cg_pcg4d(uvec4 v) {
                v = v * 1664525u + 1013904223u;
                v.x += v.y * v.w; v.y += v.z * v.x; v.z += v.x * v.y; v.w += v.y * v.z;
                v ^= v >> 16u;
                v.x += v.y * v.w; v.y += v.z * v.x; v.z += v.x * v.y; v.w += v.y * v.z;
                return v;
            }

            // Four numbers in [0, 1) for this pixel, frame and use (salt), independent of every other pixel and frame.
            vec4 cg_white_noise(ivec2 texel, int frame, uint salt) {
                return vec4(cg_pcg4d(uvec4(uvec2(texel), uint(frame), salt)) >> 8u) * (1.0 / 16777216.0);
            }
            """;

    private static final String RR_INCLUDE_FILE = "shaders/include/cg_rr.glsl";

    /**
     * shaders/include/cg_rr.glsl in the copy: DLSS Ray Reconstruction's guide images (see NVIDIA's DLSS-RR Integration
     * Guide, section 3.4), for the programs that define CG_RR_GUIDES (deferred4 and the water program). Render pixels,
     * like DLSS's other inputs. Diffuse albedo: the colour diffuse light takes (metals' small part included); specular
     * albedo: NVIDIA's EnvBRDFApprox2 of the reflectance at normal incidence (the guide's appendix); the normal in world
     * space with the linear roughness in alpha (Photon's roughness is GGX alpha, its square); the hit distance in
     * blocks. The sky gets NVIDIA's defaults.
     */
    private static final String RR_INCLUDE = """
            // Create Galore Ray Reconstruction: its guides (see PhotonPatcher in the Bridge).
            #if !defined INCLUDE_CG_RR
            #define INCLUDE_CG_RR

            #ifdef CG_RR_GUIDES
            layout(rgba8) writeonly uniform image2D cg_rr_albedo_img;
            layout(rgba8) writeonly uniform image2D cg_rr_specular_img;
            layout(rgba16f) writeonly uniform image2D cg_rr_normal_img;
            layout(r32f) writeonly uniform image2D cg_rr_hitdist_img;

            // NVIDIA's EnvBRDFApprox2 (Ray Tracing Gems, chapter 32). HLSL's float2x2(a, b, c, d) lists rows and GLSL's
            // mat2 columns, so x * M here is mul(M, x) there.
            vec3 cg_specular_albedo(vec3 specular_color, float alpha, float NoV) {
                NoV = abs(NoV);
                vec4 X = vec4(1.0, NoV, NoV * NoV, NoV * NoV * NoV);
                vec4 Y = vec4(1.0, alpha, alpha * alpha, alpha * alpha * alpha);
                mat2 M1 = mat2(0.99044, -1.28514, 1.29678, -0.755907);
                mat3 M2 = mat3(1.0, 2.92338, 59.4188, 20.3225, -27.0302, 222.592, 121.563, 626.13, 316.627);
                mat2 M3 = mat2(0.0365463, 3.32707, 9.0632, -9.04756);
                mat3 M4 = mat3(1.0, 3.59685, -1.36772, 9.04401, -16.3174, 9.22949, 5.56589, 19.7886, -20.2123);
                float bias = dot(X.xy * M1, Y.xy) / dot(X.xyw * M2, Y.xyw);
                float scale = dot(X.xy * M3, Y.xy) / dot(X.xzw * M4, Y.xyw);
                bias *= clamp(specular_color.g * 50.0, 0.0, 1.0); // (NVIDIA's: no specular reflectance gives none)
                return specular_color * max(0.0, scale) + max(0.0, bias);
            }

            // A surface's guides; with_albedo false keeps the diffuse colour already there (seen through a translucent block).
            void cg_rr_write_surface(ivec2 texel, Material material, vec3 normal, float NoV, float hit_distance, bool with_albedo) {
                vec3 specular_color = material.is_hardcoded_metal ? material.f0 * material.albedo
                    : material.is_metal ? material.albedo : vec3(material.f0.x);
                if (with_albedo) {
                    vec3 diffuse = material.albedo * (material.is_metal ? metal_diffuse_amount : 1.0);
                    imageStore(cg_rr_albedo_img, texel, vec4(clamp(diffuse, 0.0, 1.0), 1.0));
                }
                vec3 specular_albedo = cg_specular_albedo(clamp(specular_color, 0.0, 1.0), material.roughness, NoV);
                imageStore(cg_rr_specular_img, texel, vec4(clamp(specular_albedo, 0.0, 1.0), 1.0));
                imageStore(cg_rr_normal_img, texel, vec4(normalize(normal), sqrt(clamp(material.roughness, 0.0, 1.0))));
                imageStore(cg_rr_hitdist_img, texel, vec4(hit_distance));
            }

            // NVIDIA's defaults for the sky: grey albedo, no reflectance, no normal or roughness, the farthest hit.
            void cg_rr_write_sky(ivec2 texel) {
                imageStore(cg_rr_albedo_img, texel, vec4(0.5, 0.5, 0.5, 1.0));
                imageStore(cg_rr_specular_img, texel, vec4(0.0));
                imageStore(cg_rr_normal_img, texel, vec4(0.0));
                imageStore(cg_rr_hitdist_img, texel, vec4(65504.0));
            }
            #endif

            #endif
            """;

    private static final String C4_INPUTS = """
            // Create Galore DLAA: while DLSS runs this pass only copies the scene and hands DLSS its inputs.
            uniform float cg_dlss_active;
            uniform float cg_rr_exposed; // 1: Ray Reconstruction takes the colour times the exposure
            layout(rg16f) writeonly uniform image2D cg_motion_img;
            layout(r32f) writeonly uniform image2D cg_depth_img;
            layout(r32f) writeonly uniform image2D cg_exposure_img;

            #include "/include/cg_motion.glsl"
            #ifndef TAAU
            uniform sampler2D colortex1; // gbuffer data 0 (with TAAU declared above; composite3 hands it on while DLSS runs)
            #endif
            uniform sampler2D depthtex1;
            // FSR 3's masks (see cg_reactive.glsl): r reactive, as the translucent programs marked it; g composition, for
            // solid materials whose textures animate: lava (39), fire and magma (40), soul fire (43).
            layout(r32ui) readonly uniform uimage2D cg_reactive_img;
            layout(rg16f) writeonly uniform image2D cg_mask_img;
            #define CG_COMPOSITION_ANIMATED 0.8
            #ifdef CG_MOTION_GBUFFER
            #ifndef TAAU
            uniform sampler2D colortex2; // own motion of things that move by themselves (with TAAU declared above, as colortex1)
            #endif
            uniform float cg_dlss_object_motion; // 0 while testing without it
            uniform float cg_dlss_residual_albedo; // 1 in the residual test view: surface colours instead of the lit scene
            uniform int frameCounter;
            #endif

            // texel is a render pixel: with TAAU the frame fills the lower left corner of the buffers at the render size.
            void cg_dlss_inputs(ivec2 texel) {
                vec2 render_res = view_res * taau_render_scale;
                float depth = texelFetch(depthtex0, texel, 0).x;
                vec3 screen_pos = vec3((vec2(texel) + 0.5) / render_res, depth);
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
                    // Towards where this pixel was last frame, in render pixels. The sky only turns with the camera.
                    vec3 previous = reproject_scene_space(view_to_scene_space(view_pos), sky, is_lod);
                    motion = (previous.xy - screen_pos.xy) * render_res;
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
                // near over clip w, so it follows the projection with view bobbing in it as frame generation's camera does.
                float clip_w = (gbufferProjection * vec4(view_pos, 1.0)).w;
                float dlss_depth = sky ? 0.0 : clamp(near / max(clip_w, near), 0.0, 1.0);

                float composition = 0.0;
                if (!is_lod && texelFetch(depthtex1, texel, 0).x < 1.0) { // a solid surface drew here: its material
                    uint material = uint(round(texelFetch(colortex1, texel, 0).y * 65535.0)) >> 8u;
                    if (material == 39u || material == 40u || material == 43u) composition = CG_COMPOSITION_ANIMATED;
                }
                imageStore(cg_mask_img, texel, vec4(float(imageLoad(cg_reactive_img, texel).x) * (1.0 / 255.0), composition, 0.0, 0.0));

                imageStore(cg_motion_img, texel, vec4(motion, 0.0, 0.0));
                imageStore(cg_depth_img, texel, vec4(dlss_depth));
                if (texel == ivec2(0)) imageStore(cg_exposure_img, ivec2(0), vec4(exposure));
            }

            """;

    private static final String C4_BRANCH = """

                if (cg_dlss_active > 0.5) {
                    // This pass draws at the window's size. DLSS gets the frame pixel for pixel: all of it, or with
                    // TAAU the lower left corner it was rendered into (the Bridge copies only that part).
                    ivec2 cg_texel = ivec2(gl_FragCoord.xy);
                    result = vec4(0.0);
                    if (all(lessThan(vec2(cg_texel) + 0.5, view_res * taau_render_scale))) {
                        cg_dlss_inputs(cg_texel);
                        // Photon's colour, unfiltered; the Bridge writes DLSS's result over it after this pass. Alpha 0
                        // restarts Photon's TAA history cleanly if DLSS stops; texel 0 keeps the exposure as usual.
                        result = vec4(texelFetch(colortex0, cg_texel, 0).rgb, 0.0);
                        // Ray Reconstruction takes no exposure: it gets the colour exposed, and the Bridge divides it out.
                        if (cg_rr_exposed > 0.5) result.rgb *= exposure;
            #ifdef CG_MOTION_GBUFFER
                        if (cg_dlss_residual_albedo > 0.5) {
                            // For the residual test view: surface colours have none of the lighting's frame-to-frame
                            // noise, so what is left after following the motion vectors is their error. Black sky, and
                            // no albedo left over from earlier frames (colortex1 is not cleared).
                            float data_x = texelFetch(colortex1, cg_texel, 0).x;
                            float data_y = texelFetch(colortex1, cg_texel, 0).y;
                            vec2 rg; rg.x = modf((65535.0 / 256.0) * data_x, rg.y);
                            vec2 b_; b_.x = modf((65535.0 / 256.0) * data_y, b_.y);
                            vec3 albedo = vec3(rg * vec2(256.0 / 255.0, 1.0 / 255.0), b_.x * (256.0 / 255.0));
                            result.rgb = texelFetch(depthtex0, cg_texel, 0).x < 1.0 ? albedo / max(exposure, 1e-4) : vec3(0.0);
                        }
            #endif
                    }
                    if (cg_texel == ivec2(0)) result.a = exposure;
                    bloom_input = result.rgb;
                    return;
                }
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
            maskImage=cg_mask_img
            requireOn=TAA
            renderScaleOptions=TAAU,TAAU_RENDER_SCALE
            rrGuideImages=cg_rr_albedo_img,cg_rr_specular_img,cg_rr_normal_img,cg_rr_hitdist_img
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

// Every world program's fragment stage: Minecraft 1.21.1's own colour for the draw (see vanilla.glsl), and its
// motion for DLSS and FSR in colortex7 (see gbuffers_kind.glsl). In the lit overworld: the surface for the deferred
// pass, or the draw lit by itself (see gbuffers_kind.glsl's LIT_ classes).

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/lit.glsl"
#ifdef PROGRAM_WATER
#include "/lib/water.glsl"
#endif
#ifdef LIT_MAPS
#include "/lib/material_maps.glsl"
#endif
#endif

uniform sampler2D gtexture;
uniform float alphaTestRef;
#ifdef PROGRAM_SKYBASIC
uniform int renderStage;
#endif
#ifdef MOB_LIGHTING
uniform vec4 entityColor; // the hurt and creeper flash: rgb its colour, a how much of it
#endif
#ifdef LIT_RELIEF
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 cg_object_motion; // see gbuffers.vsh
in vec4 v_region;
#elif defined PROGRAM_WATER
uniform mat4 gbufferProjection; // a pixel's size, for the waves (water_surface)
#endif

in vec2 v_uv;
in vec4 v_color;
in vec3 v_light;
in float v_fog_dist;
in vec4 v_clip_now;
in vec4 v_clip_prev;

#if defined LIT_WORLD && defined LIT_GBUFFER
in vec3 v_normal;
in vec2 v_level;
in vec3 v_scene_pos;
flat in int v_material;
#if defined PROGRAM_TERRAIN
flat in float v_emission; // the light the block gives, 0..1
flat in int v_glow;       // what kind of light it is (GLOW_, see block_emission)
flat in int v_surface;    // what it is made of (SURFACE_)
in float v_ao;
#else
uniform ivec4 blendFunc; // the draw's blending (all 0 when off)
#endif

/* RENDERTARGETS: 0,7,1,2,9,12 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
layout(location = 2) out vec4 out_albedo;
layout(location = 3) out vec4 out_surface;
layout(location = 4) out vec4 out_occlusion;
layout(location = 5) out vec4 out_material;
#elif defined LIT_WORLD && defined PROGRAM_WATER
in vec3 v_normal;
in vec2 v_level;
in vec3 v_scene_pos;
flat in int v_material;

// Water leaves its surface for the composite pass, which draws it (see gbuffer.glsl); the other translucent blocks
// (glass, ice) draw themselves.
/* RENDERTARGETS: 0,7,4,5,6 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
layout(location = 2) out vec4 out_water;
layout(location = 3) out vec4 out_water_dist;
layout(location = 4) out vec4 out_glass_dist;
#else
#ifdef LIT_WORLD
in vec3 v_normal;
in vec2 v_level;
in vec3 v_scene_pos;
flat in int v_material;
#endif

/* RENDERTARGETS: 0,7 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
#endif

#ifdef LIT_MAPS
// The resource pack's maps where the draw is (see material_maps.glsl), read first thing in main().
Maps maps = Maps(vec2(0.0), vec3(0.0, 0.0, 1.0), 1.0, 1.0, vec4(0.0), 0.0);
#endif

#ifdef LIT_WORLD
// The draw in the lit overworld: its colour (texture times tint, sRGB) into the G-buffer or lit here.
void lit_output(vec4 color, int flags) {
#if defined LIT_SKY
    discard;
#elif defined LIT_GLOW
    // Glowing eyes, the glint, lightning: light added to what is there, fading out in the fog and the air.
    float fade = fog_fade(v_fog_dist) * air_seen_at(v_scene_pos, gl_FragCoord.xy).a;
#ifdef PROGRAM_ARMOR_GLINT
    out_color = vec4(glow(color.rgb) * fade * v_color.a, color.a);
#else
    out_color = vec4(glow(color.rgb) * fade, color.a);
#endif
#elif defined LIT_FORWARD
    vec3 dir = normalize(v_scene_pos - gbufferModelViewInverse[3].xyz);
#if defined PROGRAM_CLOUDS && defined LIT_CLOUDS
    discard; // the pack draws its own (see clouds.glsl)
#endif
#if defined PROGRAM_CLOUDS
    // Minecraft's clouds come untextured and unlit, in boxes: lit by the sun and the sky by the faces' own normals,
    // white, keeping only their alpha.
    vec3 normal = normalize(cross(dFdx(v_scene_pos), dFdy(v_scene_pos)));
    normal = dot(normal, dir) > 0.0 ? -normal : normal;
    vec3 rgb = forward_lit(vec3(0.9), normal, vec2(0.0, 1.0), v_scene_pos, false);
#elif defined PROGRAM_WEATHER
    // Rain and snow: lit like plants, through, by the sky, the sun and the lights near them. Raindrops are clear
    // water: pale grey streaks, fainter than Minecraft's blue lines (its rain texture is the blue one).
    if (color.b > 1.2 * color.r) {
        color = vec4(vec3(0.8), color.a * 0.5);
    }
    vec3 rgb = forward_lit(mix(vec3(luminance(color.rgb)), color.rgb, 0.3), vec3(0.0, 1.0, 0.0), v_level, v_scene_pos, true);
#elif defined PROGRAM_WATER
    float dist = length(v_scene_pos - gbufferModelViewInverse[3].xyz);
    if (v_material == MATERIAL_WATER) {
        // The top of water (either side of it) with its waves and ripples (see water.glsl); the composite pass draws
        // the rest.
        vec3 normal = v_normal;
        float roughness = 0.0;
        if (abs(v_normal.y) > 0.9) {
            vec3 view_dir = (v_scene_pos - gbufferModelViewInverse[3].xyz) / max(dist, 1e-4);
            vec3 at = v_normal.y > 0.0 ? wave_parallax(v_scene_pos, view_dir, dist) : v_scene_pos;
            float pixel_angle = 2.0 / (gbufferProjection[1][1] * render_size().y);
            vec4 surface = water_surface(at, dist, view_dir, v_level.y, pixel_angle);
            normal = surface.xyz;
            roughness = surface.w;
        }
        out_color = vec4(0.0);
        out_water = vec4(encode_normal(normal), v_level.y, 1.0);
        out_water_dist = vec4(dist, roughness, 0.0, 1.0);
        out_glass_dist = vec4(0.0);
        return;
    }
    out_water = vec4(0.0);
    out_water_dist = vec4(0.0);
    out_glass_dist = vec4(dist, 0.0, 0.0, 1.0);
#ifdef LIT_MAPS
    vec3 normal = maps.normal;
#else
    vec3 normal = v_normal;
#endif
    vec3 rgb = forward_lit(color.rgb, normal, v_level, v_scene_pos, v_material == MATERIAL_PLANT);
#else
    vec3 rgb = forward_lit(color.rgb, v_normal, v_level, v_scene_pos, v_material == MATERIAL_PLANT);
#endif
    out_color = vec4(rgb, color.a);
#if defined PROGRAM_WATER
    out_color = with_sky_reflection(out_color, normal, dir, v_level.y);
#elif defined PROGRAM_CLRWL_TRANSLUCENT
    out_color = with_sky_reflection(out_color, v_normal, dir, v_level.y);
#endif
    // The light in the air in front of it and the fog, blended over what is behind as it is (which has its own).
    out_color.rgb = apply_fog_lit(through_air(out_color.rgb, air_seen_at(v_scene_pos, gl_FragCoord.xy)), v_fog_dist,
            dir, fog_range());
#else // LIT_GBUFFER
    // Light added on top (a charged creeper's swirl, the warden's glow) or blended over another surface (a mob's
    // shadow, smoke) leaves that surface's own description in the G-buffer: only its colour (colortex1) and its
    // light (colortex0) blend.
#if defined PROGRAM_TERRAIN
    bool blended = false;
    bool over = false;
#else
    bool blended = blendFunc != ivec4(0);
    bool over = (flags & KEY_DRAWN_OVER) != 0 || blendFunc.y == 1; // GL_ONE: additive
#endif
#ifdef LIT_SELF
    float weight = blended ? 0.0 : 1.0;
#else
    float weight = over || (blended && color.a < 0.5) ? 0.0 : 1.0;
#endif
#if defined PROGRAM_DAMAGEDBLOCK
    // Block cracks multiply the surface's colour (Minecraft blends them by twice the destination's colour).
    out_color = vec4(0.5);
    out_albedo = color;
    out_surface = vec4(0.0);
    out_occlusion = vec4(0.0);
    out_material = vec4(0.0);
#elif defined LIT_SELF
    vec3 dir = normalize(v_scene_pos - gbufferModelViewInverse[3].xyz);
    vec4 air = air_seen_at(v_scene_pos, gl_FragCoord.xy);
    vec3 rgb;
    if ((flags & KEY_LIGHTMAP) == 0) {
        rgb = glow(color.rgb) * fog_fade(v_fog_dist) * air.a; // the block outline, unlit lines
    } else {
        rgb = apply_fog_lit(through_air(forward_lit(color.rgb, v_normal, v_level, v_scene_pos, true), air), v_fog_dist,
                dir, fog_range());
    }
    out_color = vec4(rgb, color.a);
    out_albedo = vec4(0.0, 0.0, 0.0, color.a);
    out_surface = encode_surface(vec3(0.0, 1.0, 0.0), v_level, MATERIAL_SELF, weight);
    out_occlusion = vec4(1.0, 0.0, 1.0, weight);
    out_material = vec4(0.0, 0.0, 0.0, weight);
#else
    out_occlusion = vec4(1.0, 0.0, 1.0, weight);
    out_material = vec4(0.0, 0.0, 0.0, weight);
    if ((flags & KEY_LIGHTMAP) == 0) {
        // What Minecraft draws without its lightmap glows by itself: the beacon's beam, glowing eyes and layers.
        out_color = vec4(glow(color.rgb) * fog_fade(v_fog_dist) * air_seen_at(v_scene_pos, gl_FragCoord.xy).a, color.a);
        out_albedo = vec4(0.0, 0.0, 0.0, color.a);
        out_surface = encode_surface(v_normal, v_level, MATERIAL_SELF, weight);
    } else {
        out_color = vec4(0.0, 0.0, 0.0, color.a);
        vec3 normal = v_normal;
#ifdef LIT_MAPS
        // The resource pack's maps: the texture's own facing (not on plants, lit as if they faced the light), its
        // crevices, how much of the sun its relief lets in, and its material. Where it has none, the pack's own.
        bool mapped = maps.spec != vec4(0.0);
        float smoothness = filtered_smoothness(maps.spec.r, maps.normal);
        if (v_material != MATERIAL_PLANT) {
            normal = maps.normal;
#ifdef PROGRAM_TERRAIN
            // (Not leaves: they let the light through from either side.)
            if (v_surface != SURFACE_LEAVES) {
                out_occlusion.b = maps.sun * micro_shadow(max(dot(normal, g_light_dir.xyz), 0.0), maps.ao);
            }
#else
            out_occlusion.b = maps.sun * micro_shadow(max(dot(normal, g_light_dir.xyz), 0.0), maps.ao);
#endif
        }
        out_occlusion.r = maps.ao;
        out_material = mapped ? vec4(max(smoothness, 0.0), maps.spec.g, maps.spec.b, weight) : vec4(0.0, 0.0, 0.0, weight);
        // What glows by the maps: their emission, in the texture's own colour, as bright as a light block's at full.
        if (mapped && maps.spec.a < 1.0) {
            out_color.rgb = srgb_to_linear(color.rgb) * (maps.spec.a * 255.0 / 254.0)
                    * min(emission_luminance * exposure(), emission_frame_max) * EMISSION * fog_fade(v_fog_dist);
        }
#else
        const bool mapped = false;
#endif
#ifdef PROGRAM_TERRAIN
        // A block that gives light glows where its texture is bright (see block_emission), fading in the fog; where
        // the maps say what glows, they do.
        if (v_emission > 0.0 && !mapped) {
            out_color.rgb = block_emission(color.rgb, v_emission, v_glow) * fog_fade(v_fog_dist);
        }
#endif
        out_albedo = color;
#ifdef PROGRAM_TERRAIN
        // Leaves let light through them, metal shines, rain soaks into sand (see deferred.fsh).
        out_occlusion = vec4(v_ao * out_occlusion.r, float(v_surface) / 7.0, out_occlusion.b, 1.0);
#endif
        out_surface = encode_surface(normal, v_level, v_material, weight);
    }
#endif
#endif
}
#endif

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }

    int flags = key_flags();
    vec4 color = v_color;
#ifdef LIT_MAPS
#ifdef LIT_RELIEF
    vec4 region = v_region;
#else
    vec4 region = vec4(0.0);
#endif
    maps = read_maps(v_scene_pos, v_uv, normalize(v_normal), normalize(v_scene_pos - gbufferModelViewInverse[3].xyz),
            region, g_light_dir.xyz);
#endif
    // Vanilla's mob shaders test their texture's alpha alone; the rest test the colour as drawn.
    bool test_texture = false;
#ifdef TEXTURED
    if ((flags & KEY_UNTEXTURED) == 0) {
#ifdef LIT_RELIEF
        // Where the relief took the view (across the texture's pixels, with the mip level of the surface itself).
        vec4 albedo = textureGrad(gtexture, maps.uv, dFdx(v_uv) * exp2(cg_mip_bias), dFdy(v_uv) * exp2(cg_mip_bias));
#else
        vec4 albedo = texture(gtexture, v_uv, cg_mip_bias);
#endif
        test_texture = (flags & KEY_MOB_SHADING) != 0;
        if (test_texture && albedo.a < alphaTestRef) {
            discard;
        }
        color *= albedo;
    }
#endif
    if (!test_texture && color.a < alphaTestRef) {
        discard;
    }

#ifdef MOB_LIGHTING
    color.rgb = mix(color.rgb, entityColor.rgb, entityColor.a);
#endif
#ifdef LIT_WORLD
    lit_output(color, flags);
#else
    color.rgb *= v_light;

#if defined END_OWN_SKY && (defined PROGRAM_SKYBASIC || defined PROGRAM_SKYTEXTURED)
    discard; // the End draws its own sky instead (see end_sky.glsl)
#endif
#if defined PROGRAM_SKYBASIC
    // The sky's dome fades into the fog colour towards the horizon; the sunrise glow has no fog, and the stars are
    // drawn with the fog moved out of the way.
    if (renderStage != MC_RENDER_STAGE_SUNSET) {
        color.rgb = mix(color.rgb, fogColor, fog_amount(v_fog_dist, fogStart, fogEnd));
    }
#elif defined PROGRAM_ARMOR_GLINT
    color.rgb *= fog_fade(v_fog_dist) * v_color.a; // (Iris hands the glint's strength over in the vertex alpha)
#elif defined FOG_FADES
    color *= fog_fade(v_fog_dist);
#else
    if (fogShape >= 0) { // Iris marks the draws Minecraft doesn't fog (-1)
        color.rgb = apply_fog(color.rgb, v_fog_dist);
    }
#endif

    out_color = color;
#endif

    float claim = color.a > 0.1 ? 1.0 : 0.0;
    if ((flags & KEY_DRAWN_OVER) != 0) {
        out_motion = vec4(0.0);
        return;
    }
#if defined OWN_MOTION
#ifdef HAND
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_HAND, claim);
#elif defined LIT_RELIEF
    // The point the relief shows moves as itself, deeper than the face it's drawn on.
    vec3 seen = v_scene_pos + normalize(v_scene_pos - gbufferModelViewInverse[3].xyz) * maps.depth;
    vec3 seen_prev = cg_object_motion[3][3] != 0.0 ? (cg_object_motion * vec4(seen, 1.0)).xyz : seen;
    out_motion = vec4(motion_vector(gbufferProjection * (gbufferModelView * vec4(seen, 1.0)),
            previous_clip(seen_prev, gbufferPreviousProjection)), MOTION_WORLD, claim);
#else
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, claim);
#endif
#elif defined NO_MOTION_CLAIM
    out_motion = vec4(0.0);
#else
    out_motion = vec4(0.0, 0.0, MOTION_NONE, claim);
#endif
}

// Every world program's fragment stage: Minecraft 1.21.1's own colour for the draw (see vanilla.glsl), and its
// motion for DLSS and FSR in colortex7 (see gbuffers_kind.glsl). In the lit overworld: the surface for the deferred
// pass, or the draw lit by itself (see gbuffers_kind.glsl's LIT_ classes).

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/lit.glsl"
#endif

uniform sampler2D gtexture;
uniform float alphaTestRef;
#ifdef PROGRAM_SKYBASIC
uniform int renderStage;
#endif
#ifdef MOB_LIGHTING
uniform vec4 entityColor; // the hurt and creeper flash: rgb its colour, a how much of it
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
#if !defined PROGRAM_TERRAIN
uniform ivec4 blendFunc; // the draw's blending (all 0 when off)
#endif

/* RENDERTARGETS: 0,7,1,2 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
layout(location = 2) out vec4 out_albedo;
layout(location = 3) out vec4 out_surface;
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

#ifdef LIT_WORLD
// The draw in the lit overworld: its colour (texture times tint, sRGB) into the G-buffer or lit here.
void lit_output(vec4 color, int flags) {
#if defined LIT_SKY
    discard;
#elif defined LIT_GLOW
    // Glowing eyes, the glint, lightning: light added to what is there, fading out in the fog.
#ifdef PROGRAM_ARMOR_GLINT
    out_color = vec4(glow(color.rgb) * fog_fade(v_fog_dist) * v_color.a, color.a);
#else
    out_color = vec4(glow(color.rgb) * fog_fade(v_fog_dist), color.a);
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
    // Rain and snow: lit like plants, through, by the sky and the sun.
    vec3 rgb = forward_lit(color.rgb, vec3(0.0, 1.0, 0.0), v_level, v_scene_pos, true);
#else
    vec3 rgb = forward_lit(color.rgb, v_normal, v_level, v_scene_pos, v_material == MATERIAL_PLANT);
#endif
#ifdef PROGRAM_WATER
    if (v_material == MATERIAL_WATER) {
        // Water sends little light back out of its depth: its own colour is dim, and what it reflects (below) and
        // lets through is most of what you see.
        rgb *= 0.15;
    }
#endif
    rgb = apply_fog_lit(rgb, v_fog_dist, dir, fog_range());
    out_color = vec4(rgb, color.a);
#if defined PROGRAM_WATER || defined PROGRAM_CLRWL_TRANSLUCENT
    out_color = with_sky_reflection(out_color, v_normal, dir, v_level.y);
#endif
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
#elif defined LIT_SELF
    vec3 dir = normalize(v_scene_pos - gbufferModelViewInverse[3].xyz);
    vec3 rgb;
    if ((flags & KEY_LIGHTMAP) == 0) {
        rgb = glow(color.rgb) * fog_fade(v_fog_dist); // the block outline, unlit lines
    } else {
        rgb = apply_fog_lit(forward_lit(color.rgb, v_normal, v_level, v_scene_pos, true), v_fog_dist, dir, fog_range());
    }
    out_color = vec4(rgb, color.a);
    out_albedo = vec4(0.0, 0.0, 0.0, color.a);
    out_surface = encode_surface(vec3(0.0, 1.0, 0.0), v_level, MATERIAL_SELF, weight);
#else
    if ((flags & KEY_LIGHTMAP) == 0) {
        // What Minecraft draws without its lightmap glows by itself: the beacon's beam, glowing eyes and layers.
        out_color = vec4(glow(color.rgb) * fog_fade(v_fog_dist), color.a);
        out_albedo = vec4(0.0, 0.0, 0.0, color.a);
        out_surface = encode_surface(v_normal, v_level, MATERIAL_SELF, weight);
    } else {
        out_color = vec4(0.0, 0.0, 0.0, color.a);
        out_albedo = color;
        out_surface = encode_surface(v_normal, v_level, v_material, weight);
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
    // Vanilla's mob shaders test their texture's alpha alone; the rest test the colour as drawn.
    bool test_texture = false;
#ifdef TEXTURED
    if ((flags & KEY_UNTEXTURED) == 0) {
        vec4 albedo = texture(gtexture, v_uv, cg_mip_bias);
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
#else
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, claim);
#endif
#elif defined NO_MOTION_CLAIM
    out_motion = vec4(0.0);
#else
    out_motion = vec4(0.0, 0.0, MOTION_NONE, claim);
#endif
}

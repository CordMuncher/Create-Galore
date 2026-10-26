// Create's machines and contraptions through Colorwheel: Flywheel's own material code gives the colour (with the
// face shading Minecraft gives blocks, and the hurt flash), and this applies the lightmap and the fog as Minecraft
// does. In the lit overworld they go into the G-buffer like any block (see gbuffers.fsh), or light themselves.

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/lit.glsl"
#endif

uniform sampler2D gtexture;

in vec2 v_uv;
in float v_fog_dist;
in vec4 v_clip_now;
in vec4 v_clip_prev;
#ifdef LIT_WORLD
in vec3 v_normal;
in vec3 v_scene_pos;
#endif

#if defined LIT_WORLD && defined LIT_GBUFFER
/* RENDERTARGETS: 0,7,1,2,9 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
layout(location = 2) out vec4 out_albedo;
layout(location = 3) out vec4 out_surface;
layout(location = 4) out vec4 out_occlusion;
#else
/* RENDERTARGETS: 0,7 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;
#endif

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }

    vec4 color;
    vec2 light;
    float ao;
    vec4 overlay;
    // Discards what the material cuts out.
    clrwl_computeFragment(texture(gtexture, v_uv, cg_mip_bias), color, light, ao, overlay);
#ifdef LIT_WORLD
    // Colorwheel hands the light over at the lightmap's texel centres (level / 16 + 1 / 32).
    vec2 level = clamp((light - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    vec3 normal = normalize(v_normal);
#if defined PROGRAM_CLRWL_DAMAGEDBLOCK
    out_color = vec4(0.5); // the cracks multiply the surface's colour (see gbuffers.fsh)
    out_albedo = color;
    out_surface = vec4(0.0);
    out_occlusion = vec4(1.0, 0.0, 0.0, 1.0);
    out_motion = vec4(0.0);
#elif defined LIT_GLOW
    out_color = vec4(glow(color.rgb) * fog_fade(v_fog_dist), color.a);
    out_motion = vec4(0.0);
#else
    color.rgb = mix(color.rgb, overlay.rgb, overlay.a);
#if defined CLRWL_OLD_LIGHTING && !defined _CLRWL_IS_FALLBACK
    // Flywheel shades the faces as Minecraft does (with oldLighting); the sun and the sky do it now.
    color.rgb /= max(_clrwl_diffuseFactor(), 0.05);
#endif
#ifdef LIT_FORWARD
    vec3 dir = normalize(v_scene_pos - gbufferModelViewInverse[3].xyz);
    vec3 rgb = apply_fog_lit(forward_lit(color.rgb, normal, level, v_scene_pos, false), v_fog_dist, dir, fog_range());
    out_color = with_sky_reflection(vec4(rgb, color.a), normal, dir, level.y);
#else
    out_color = vec4(0.0, 0.0, 0.0, color.a);
    out_albedo = color;
    out_surface = encode_surface(normal, level, MATERIAL_LIT, 1.0);
    out_occlusion = vec4(1.0, 0.0, 0.0, 1.0);
#endif
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, color.a > 0.1 ? 1.0 : 0.0);
#endif
#elif defined PROGRAM_CLRWL_DAMAGEDBLOCK
    out_color = color; // the cracks multiply what is there, unlit and without fog
    out_motion = vec4(0.0);
#elif defined PROGRAM_CLRWL_GLINT || defined PROGRAM_CLRWL_ADDITIVE || defined PROGRAM_CLRWL_LIGHTNING
    // Light that adds itself to what is there (the glint, glowing parts): it fades out in the fog, and adds nothing
    // to the motion behind it.
    out_color = vec4(color.rgb * fog_fade(v_fog_dist), color.a);
    out_motion = vec4(0.0);
#else
    color.rgb = mix(color.rgb, overlay.rgb, overlay.a);
    // Colorwheel hands the light over at the lightmap's texel centres; Minecraft samples blocks between them.
    color.rgb *= lightmap_smooth(light - 1.0 / 32.0);
    color.rgb = apply_fog(color.rgb, v_fog_dist);
    out_color = color;
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, color.a > 0.1 ? 1.0 : 0.0);
#endif
}

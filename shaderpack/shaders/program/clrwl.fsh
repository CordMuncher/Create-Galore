// Create's machines and contraptions through Colorwheel: Flywheel's own material code gives the colour (with the
// face shading Minecraft gives blocks, and the hurt flash), and this applies the lightmap and the fog as Minecraft
// does.

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"

uniform sampler2D gtexture;

in vec2 v_uv;
in vec3 v_fog_pos;
in vec4 v_clip_now;
in vec4 v_clip_prev;

/* RENDERTARGETS: 0,7 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_motion;

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
#if defined PROGRAM_CLRWL_DAMAGEDBLOCK
    out_color = color; // the cracks multiply what is there, unlit and without fog
    out_motion = vec4(0.0);
#elif defined PROGRAM_CLRWL_GLINT || defined PROGRAM_CLRWL_ADDITIVE || defined PROGRAM_CLRWL_LIGHTNING
    // Light that adds itself to what is there (the glint, glowing parts): it fades out in the fog, and adds nothing
    // to the motion behind it.
    out_color = vec4(color.rgb * fog_fade(v_fog_pos), color.a);
    out_motion = vec4(0.0);
#else
    color.rgb = mix(color.rgb, overlay.rgb, overlay.a);
    // Colorwheel hands the light over at the lightmap's texel centres; Minecraft samples blocks between them.
    color.rgb *= lightmap_smooth(light - 1.0 / 32.0);
    color.rgb = apply_fog(color.rgb, v_fog_pos);
    out_color = color;
    out_motion = vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, color.a > 0.1 ? 1.0 : 0.0);
#endif
}

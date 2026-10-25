// Every world program's fragment stage: Minecraft 1.21.1's own colour for the draw (see vanilla.glsl), and its
// motion for DLSS and FSR in colortex7 (see gbuffers_kind.glsl).

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"

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
    color.rgb *= v_light;

#if defined PROGRAM_SKYBASIC
    // The sky's dome fades into the fog colour towards the horizon; the sunrise glow has no fog, and the stars are
    // drawn with the fog moved out of the way.
    if (renderStage != MC_RENDER_STAGE_SUNSET) {
        color.rgb = mix(color.rgb, fogColor, fog_amount(length(v_fog_pos), fogStart, fogEnd));
    }
#elif defined PROGRAM_ARMOR_GLINT
    color.rgb *= fog_fade(v_fog_pos) * v_color.a; // (Iris hands the glint's strength over in the vertex alpha)
#elif defined FOG_FADES
    color *= fog_fade(v_fog_pos);
#else
    if (fogShape >= 0) { // Iris marks the draws Minecraft doesn't fog (-1)
        color.rgb = apply_fog(color.rgb, v_fog_pos);
    }
#endif

    out_color = color;

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

// Distant Horizons' terrain and water. Its depth goes to dhDepthTex0, which the composite1 pass reads for its motion
// vectors and DLSS depth wherever Minecraft drew nothing.
//
// Distant Horizons draws against a depth buffer of its own: its terrain before Minecraft's chunks, which then cover
// it, and its water after them, which nothing covers. So it only draws past the chunks Minecraft has (else its coarse
// copy shows through cave mouths and dug holes, and its water over the near shore), and its water only where
// Minecraft drew nothing.

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/lit.glsl"
#endif

#ifdef PROGRAM_DH_WATER
uniform sampler2D depthtex0;
#ifdef LIT_WORLD
// For how much of the sky the water sees (see sky_probe.glsl): what is opaque in front of the points over it.
uniform sampler2D depthtex1;
uniform sampler2D dhDepthTex1;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 dhProjectionInverse;
#define SKY_PROBE_DEPTH depthtex1
#define SKY_PROBE_DH_DEPTH dhDepthTex1
#include "/lib/sky_probe.glsl"
#endif
#endif

in vec4 v_color;
in vec3 v_fog_pos;
#ifdef LIT_WORLD
in vec3 v_normal;
in vec2 v_level;
#ifdef PROGRAM_DH_GENERIC
flat in int v_dh_material;
#endif
#endif

#if defined LIT_WORLD && !defined PROGRAM_DH_WATER
// The lit overworld: its terrain into the G-buffer for the deferred pass, without the shadow map (which doesn't reach
// it); anything else it draws (generic objects) lit by itself, as particles are (see gbuffers.fsh).
/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_albedo;
layout(location = 2) out vec4 out_surface;
#else
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;
#endif

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
#if defined PROGRAM_DH_TERRAIN || defined PROGRAM_DH_WATER
    // A chunk inside the render distance, where Minecraft's own chunks are (they are loaded in a cylinder).
    if (length(v_fog_pos.xz) < far - 16.0) {
        discard;
    }
#endif
#ifdef PROGRAM_DH_WATER
    if (texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r < 1.0) {
        discard;
    }
#endif
    // Its fog is measured at every pixel, as Distant Horizons' own shaders do (its triangles can be very long).
#ifdef LIT_WORLD
    vec3 dir = normalize(v_fog_pos - gbufferModelViewInverse[3].xyz);
#if defined PROGRAM_DH_TERRAIN
    out_color = vec4(0.0, 0.0, 0.0, 1.0);
    out_albedo = v_color;
    out_surface = encode_surface(v_normal, v_level, MATERIAL_LIT, 1.0);
#elif defined PROGRAM_DH_WATER
    // Lit here: past the shadow map's reach the sun goes by the sky light (see direct_visibility), and the sky's own
    // light by how much of the sky the water sees where it lies under something high, which Minecraft's sky light
    // makes as dark as a cave (see sky_probe.glsl).
    vec2 level = v_level;
    float seen = -1.0;
    if (level.y < 0.999) {
        seen = sky_seen_around(v_fog_pos, vec3(0.0, 1.0, 0.0), level.y, 0.5, true, true);
        level.y = max(level.y, sky_seen_level(seen));
    }
    vec3 rgb = light_surface(srgb_to_linear(v_color.rgb), v_normal, level, v_fog_pos, false, false, false, 1.0, 1.0,
            v_level.y) * exposure();
    out_color = with_sky_reflection(vec4(rgb, v_color.a), v_normal, dir, level.y);
    out_color.rgb = apply_fog_lit(through_air(out_color.rgb, air_seen_at(v_fog_pos, gl_FragCoord.xy)),
            fog_distance(v_fog_pos), dir, fog_range());
#if DEBUG_VIEW == 6
    out_color = vec4(vec3(sky_light_curve(v_level.y), max(seen, 0.0), seen < 0.0 ? 0.5 : 0.0) * 2.0, 1.0);
#endif
#else
    // Its other objects, lit here: its clouds, the beacons' beams past the render distance, and any a mod adds.
    // It gives its clouds and beams full block and sky light, so that its own shaders draw them as bright as
    // their colour.
    float dist = fog_distance(v_fog_pos);
    vec4 air = air_seen_at(v_fog_pos, gl_FragCoord.xy);
    vec3 rgb;
    if (v_dh_material == DH_BLOCK_ILLUMINATED) {
        rgb = glow(v_color.rgb) * fog_fade(dist) * air.a;
    } else if (v_dh_material == DH_BLOCK_UNKNOWN) {
#ifdef LIT_CLOUDS
        discard; // the pack draws its own clouds (see clouds.glsl)
#endif
        // Its clouds come in Minecraft's cloud colour, which darkens them by night and in the rain as the sun and
        // the sky do here: white like Minecraft's own clouds (see gbuffers.fsh), under the open sky.
        rgb = light_surface(srgb_to_linear(vec3(0.9)), v_normal, vec2(0.0, 1.0), v_fog_pos, false, false) * exposure();
        rgb = apply_fog_lit(through_air(rgb, air), dist, dir, fog_range());
    } else {
        rgb = light_surface(srgb_to_linear(v_color.rgb), v_normal, v_level, v_fog_pos, false, false) * exposure();
        rgb = apply_fog_lit(through_air(rgb, air), dist, dir, fog_range());
    }
    out_color = vec4(rgb, v_color.a);
    out_albedo = vec4(0.0, 0.0, 0.0, v_color.a);
    out_surface = encode_surface(v_normal, v_level, MATERIAL_SELF, 1.0);
#endif
#else
    out_color = vec4(apply_fog(v_color.rgb, fog_distance(v_fog_pos)), v_color.a);
#endif
}

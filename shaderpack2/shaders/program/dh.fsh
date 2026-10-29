// Distant Horizons' terrain, water and other objects (see dh.vsh).
//
// It draws against a depth buffer of its own: its terrain before Minecraft's chunks, which then cover it, and its
// water after them, which nothing covers. So it only draws past the chunks Minecraft has (else its coarse copy shows
// through cave mouths and dug holes, and its water over the near shore), and its water only where Minecraft drew
// nothing.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/buffers.glsl"

uniform float far; // the render distance in blocks

in vec4 v_color;
in vec3 v_scene_pos;
in vec3 v_normal;
in vec2 v_light;
flat in int v_material;

#if defined PROGRAM_DH_WATER

#include "/lib/forward.glsl"
#include "/lib/water.glsl"
#if defined SUNLIGHT
#include "/lib/sky.glsl"
#endif

uniform sampler2D depthtex0;
uniform sampler2D dhDepthTex1; // its terrain without its water
uniform mat4 dhProjectionInverse;
uniform int isEyeInWater;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color; // premultiplied by its alpha, as Minecraft's water (see gbuffers.fsh)

void main() {
    if (outside_render_area(gl_FragCoord.xy) || length(v_scene_pos.xz) < far - 16.0
            || texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r < 1.0) {
        discard;
    }
    // The same water as near by (see shade_water in gbuffers.fsh), with only the long waves this far off and the
    // sun without the shadow map.
    vec3 to_eye = normalize(-v_scene_pos);
    float distance_away = length(v_scene_pos);
    vec3 n = v_normal.y > 0.5 ? wave_normal(v_scene_pos.xz + cameraPosition.xz, frameTimeCounter, 1.0, distance_away)
            : v_normal;
    if (isEyeInWater == 1) {
        n = -n;
    }
    float cos_v = clamp(dot(n, to_eye), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cos_v, 5.0);
    vec3 light_here = forward_light(vec3(0.0, 1.0, 0.0), v_scene_pos, v_light, 1.0, 0.0);
#if defined SUNLIGHT
    vec3 r = reflect(-to_eye, n);
    r.y = abs(r.y);
    vec3 reflected = mix(light_here * 0.05, sky(r, false), vanilla_sky_share(v_light.y));
#else
    vec3 reflected = light_here * 0.05;
#endif

    float thickness = 64.0;
    float under = texelFetch(dhDepthTex1, ivec2(gl_FragCoord.xy), 0).r;
    if (under < 1.0) {
        vec2 ndc = (gl_FragCoord.xy - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
        vec4 v = dhProjectionInverse * vec4(ndc, under * 2.0 - 1.0, 1.0);
        vec3 under_scene = view_to_scene(v.xyz / v.w);
        thickness = distance(under_scene, v_scene_pos);
    }
    vec3 through = exp(-water_extinction * thickness);
    float kept = dot(through, vec3(0.3, 0.4, 0.3));
    vec3 tint = clamp(srgb_to_linear(v_color.rgb) * 1.2, 0.0, 1.0);
    vec3 scattered = light_here * water_scattering / water_extinction * (1.0 - through) * tint * 2.2;
    out_color = vec4(scattered * (1.0 - fresnel) + reflected * fresnel, 1.0 - kept * (1.0 - fresnel));
}

#else

/* RENDERTARGETS: 1,2,3,4 */
layout(location = 0) out vec4 out_albedo;
layout(location = 1) out vec4 out_normal;
layout(location = 2) out vec4 out_light;
layout(location = 3) out vec4 out_material;

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
#ifdef PROGRAM_DH_TERRAIN
    // A chunk inside the render distance, where Minecraft's own chunks are (they are loaded in a cylinder).
    if (length(v_scene_pos.xz) < far - 16.0) {
        discard;
    }
#endif
    // Lava and lit blocks glow; its beacon beams too.
    float glow = v_material == DH_BLOCK_ILLUMINATED ? 1.0 : v_material == DH_BLOCK_LAVA ? 0.8 : 0.0;
    vec2 light = v_light;
#ifdef PROGRAM_DH_GENERIC
    // Its clouds come in Minecraft's cloud colour with no light of their own: white under the open sky.
    if (v_material == DH_BLOCK_UNKNOWN) {
        light = vec2(0.0, 1.0);
    }
#endif
    out_albedo = vec4(v_color.rgb, float(MAT_LOD) / 255.0);
    vec2 e = encode_normal(v_normal);
    out_normal = vec4(e, e);
    out_light = vec4(light, 1.0, glow);
    out_material = vec4(v_material == DH_BLOCK_WATER ? 0.9 : 0.1, 0.0, 0.0, 1.0);
}

#endif

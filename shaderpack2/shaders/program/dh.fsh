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
#define CLOUDS_NO_READ_BACK
#include "/lib/sky_mirror.glsl"
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
    // The same water as near by (see shade_water in gbuffers.fsh): the waves a pixel can still show this far off (the
    // rest as roughness that spreads the sun's glint into a path), the sky mirrored, the sun without the shadow map.
    vec3 to_eye = normalize(-v_scene_pos);
    float distance_away = length(v_scene_pos);
    bool inside = isEyeInWater == 1;
    vec3 face = normalize(v_normal);
    if (dot(face, to_eye) < 0.0) {
        face = -face;
    }
    vec3 n = face;
    float rough = 0.0;
    if (abs(face.y) > 0.5) {
        float grazing = abs(to_eye.y);
        float footprint = distance_away * pixel_angle() * inversesqrt(max(grazing, 0.02));
        float strength = WATER_WAVES * mix(0.25, 1.0, smoothstep(0.5, 0.95, v_light.y)) * water_chop(v_scene_pos)
                       * mix(0.35, 1.0, smoothstep(0.0, 0.25, grazing));
        float rain = g_water.x * smoothstep(0.8, 0.9, v_light.y);
        Waves w = water_waves(water_local(v_scene_pos), strength, 1.0, rain, footprint);
        rough = w.rough;
        vec3 wave = wave_normal(w);
        n = face.y > 0.0 ? wave : -wave;
    }
    float spread = 2.0 * sqrt(rough);
    float cos_v = max(dot(n, to_eye), 0.0);
    float fresnel = fresnel_dielectric(cos_v, inside ? 1.0 / water_ior : water_ior);
    vec3 light_here = forward_light(vec3(0.0, 1.0, 0.0), v_scene_pos, v_light, 1.0, 0.0);
    vec3 r = reflect(-to_eye, n);
#if defined SUNLIGHT
    r.y = inside ? -abs(r.y) : abs(r.y);
    vec3 reflected = inside ? light_here * 0.05 : mix(light_here * 0.05, reflect_sky(r), vanilla_sky_share(v_light.y));
    if (!inside && g_light_dir.w > 0.5) {
        reflected += sun_seen(r, spread) * smoothstep(0.05, 0.3, v_light.y) * cloud_shadow(v_scene_pos);
    }
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
    vec3 extinction = water_absorption_for(v_color.rgb) + water_scatter;
    float lit = smoothstep(0.02, 0.4, luminance(light_here));
    float depth = inside ? 0.0 : max(thickness, mix(2.0, 1.0, lit));
    vec3 through = exp(-extinction * depth);
    float kept = dot(through, vec3(0.3, 0.4, 0.3));
    float cos_t = sqrt(max(1.0 - (1.0 - cos_v * cos_v) / (water_ior * water_ior), 0.0));
    vec3 along = extinction * (1.0 + 1.15 * cos_t);
    vec3 glow = light_here * (water_scatter * water_scatter_boost) * (1.0 - exp(-along * depth)) / along;
    out_color = vec4(glow * (1.0 - fresnel) + reflected * fresnel, 1.0 - kept * (1.0 - fresnel));
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

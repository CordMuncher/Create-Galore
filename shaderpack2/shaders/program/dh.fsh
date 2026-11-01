// Distant Horizons' terrain, water and other objects (see dh.vsh).
//
// It draws against a depth buffer of its own: its terrain before Minecraft's chunks, which then cover it, and its
// water after them, which nothing covers. So it only draws past the chunks Minecraft has (else its coarse copy shows
// through cave mouths and dug holes), and its water only past Minecraft's (taking over from it pixel by pixel over the
// last 16 blocks before the render distance) and never over anything of Minecraft's in front of it (the near shore).

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
uniform sampler2D depthtex1;   // Minecraft's terrain without its water
uniform sampler2D dhDepthTex1; // its terrain without its water
uniform mat4 dhProjectionInverse;
uniform int isEyeInWater;
#ifdef WATER_REFRACTION
// It draws what lies under it itself, as Minecraft's water does (program/gbuffers.fsh), from the lit scene's copy
// (written by program/deferred_light.fsh): its alpha is 1.
#define DH_WATER_SEES_UNDER
uniform sampler2D colortex5;
#endif

// Where Minecraft's depth at this pixel puts its surface (scene space).
vec3 near_scene_at(float depth) {
    vec2 ndc = gl_FragCoord.xy / render_size() * 2.0 - 1.0;
    vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view_to_scene(v.xyz / v.w);
}

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color; // premultiplied by its alpha, as Minecraft's water (see gbuffers.fsh)

void main() {
    if (outside_render_area(gl_FragCoord.xy) || !dh_water_takes(v_scene_pos, gl_FragCoord.xy, far)) {
        discard;
    }
    float distance_away = length(v_scene_pos);
    float near_depth = texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r;
    if (near_depth < 1.0 && length(near_scene_at(near_depth)) < distance_away - 0.5) {
        discard;
    }
    // The same water as near by (see shade_water in gbuffers.fsh): the waves a pixel can still show this far off (the
    // rest as roughness that spreads the sun's glint into a path), the sky mirrored, the sun without the shadow map.
    vec3 to_eye = normalize(-v_scene_pos);
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
        float strength = WATER_WAVES * mix(0.25, 1.0, smoothstep(0.5, 0.95, v_light.y)) * water_chop(v_scene_pos);
        float rain = g_water.x * smoothstep(0.8, 0.9, v_light.y);
        Waves w = water_waves(water_local(v_scene_pos), strength, 1.0, rain, footprint, distance_away);
        // As the near water (program/gbuffers.fsh): flatter at a grazing look, the rest as roughness, which tilts
        // the water towards you.
        float flatter = mix(0.35, 1.0, smoothstep(0.0, 0.25, grazing));
        w.slope *= flatter;
        rough = w.rough + w.seen * (1.0 - flatter * flatter);
        if (face.y > 0.0 && !inside) {
            w.slope += facing_tilt(rough, to_eye);
        }
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
    // The sky mirrored wherever it is open to the sky, as the near water's traced reflection sees it there (a dim copy
    // of its own light only where Distant Horizons says the sky is mostly hidden).
    float open_sky = smoothstep(0.1, 0.5, v_light.y);
    vec3 reflected = inside ? light_here * 0.05 : mix(light_here * 0.05, reflect_sky(r, v_scene_pos), open_sky);
    if (!inside && g_light_dir.w > 0.5) {
        reflected += sun_seen(r, spread) * smoothstep(0.05, 0.3, v_light.y) * cloud_shadow(v_scene_pos);
    }
#else
    vec3 reflected = light_here * 0.05;
#endif

    // What lies under it, as for the near water: Minecraft's floor where it has one behind the water (in the hand-over
    // stretch), else Distant Horizons', else 64 blocks of water.
    float thickness = 64.0;
    float near_under = texelFetch(depthtex1, ivec2(gl_FragCoord.xy), 0).r;
    vec3 near_floor = near_scene_at(near_under);
    float under = texelFetch(dhDepthTex1, ivec2(gl_FragCoord.xy), 0).r;
    if (near_under < 1.0 && length(near_floor) > distance_away) {
        thickness = distance(near_floor, v_scene_pos);
    } else if (under < 1.0) {
        vec2 ndc = (gl_FragCoord.xy - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
        vec4 v = dhProjectionInverse * vec4(ndc, under * 2.0 - 1.0, 1.0);
        vec3 under_scene = view_to_scene(v.xyz / v.w);
        thickness = distance(under_scene, v_scene_pos);
    }
    vec3 extinction = water_absorption_for(v_color.rgb) + water_scatter;
    float lit = smoothstep(0.02, 0.4, luminance(light_here));
    float depth = inside ? 0.0 : max(thickness, mix(2.0, 1.0, lit));
    vec3 through = exp(-extinction * depth);
    float cos_t = sqrt(max(1.0 - (1.0 - cos_v * cos_v) / (water_ior * water_ior), 0.0));
    vec3 along = extinction * (1.0 + 1.15 * cos_t);
    vec3 glow = light_here * (water_scatter * water_scatter_boost) * (1.0 - exp(-along * depth)) / along;
#ifdef DH_WATER_SEES_UNDER
    // Tinted by the water between, as the near water's floor is (not greyed by a single blend).
    vec3 under_light = textureLod(colortex5, gl_FragCoord.xy / window_size(), 0.0).rgb;
    out_color = vec4((under_light * through + glow) * (1.0 - fresnel) + reflected * fresnel, 1.0);
#else
    float kept = dot(through, vec3(0.3, 0.4, 0.3));
    out_color = vec4(glow * (1.0 - fresnel) + reflected * fresnel, 1.0 - kept * (1.0 - fresnel));
#endif
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

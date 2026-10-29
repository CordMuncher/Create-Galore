// The air between you and what you see, over the whole lit scene (translucents included):
// - the coloured light in the air: the per-block light (see program/voxel_light.csh; cells in the open take light
//   from every side, so this is the light in the air itself), gathered along the view ray in 8-block steps up to
//   the first surface or half the voxel box; bright spots count much more than dim ones, the average's fourth root
//   makes even faint light show, and it fades in from nothing at your feet to full 32 blocks out (your own torch
//   doesn't fog your view). It darkens what is behind it a little, then adds itself (in display space): haze, not a
//   glow on top. In the Overworld it is faint by day out in the open and full at night and underground;
// - haze far off, thickest at sea level, in the sky's colour at the horizon, starting slowly;
// - water around you (when the camera is under water) and lava.

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/globals.glsl"
#include "/lib/voxel_sample.glsl"
#include "/lib/lighting.glsl"
#include "/lib/water.glsl"

uniform sampler2D colortex0;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
uniform float far;
uniform vec3 fogColor;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
uniform int dhRenderDistance; // in blocks
#endif

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

vec3 light_in_air(vec3 dir, float reach, ivec2 texel) {
    float start = fract(ign(vec2(texel)) + float(frameCounter % 64) * GOLDEN);
    vec3 origin = vox_pos(vec3(0.0));
    vec3 sum = vec3(0.0);
    float count = 0.0;
    for (int i = 0; i < 8; i++) {
        float t = (float(i) + start) * 8.0;
        if (t >= reach) {
            break;
        }
        vec3 p = origin + dir * t;
        vec3 r = abs(p) / vec3(vox_half);
        // Fades towards the box's edge, sooner up and down (the box is flatter).
        float edge = (1.0 - smoothstep(0.6, 0.95, max(r.x, r.z))) * (1.0 - smoothstep(0.5, 0.9, r.y));
        float near_fade = smoothstep(0.0, 32.0, t);
        vec4 light = sample_block_table(cg_blk_tex, p);
        vec3 c = light.rgb * light.a;
        // Bright spots count far more than dim ones, so the haze gathers round strong lights.
        float lum = max3(c);
        c *= lum * lum * 4.0;
        sum += c * edge * near_fade;
        count += 1.0;
    }
    if (count == 0.0) {
        return vec3(0.0);
    }
    vec3 avg = sum / count;
    return sqrt(sqrt(avg)) * 0.25;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    if (outside_render_area(gl_FragCoord.xy)) {
        out_color = vec4(0.0);
        return;
    }
    vec3 color = texelFetch(colortex0, texel, 0).rgb;
#if DEBUG_VIEW != 0
    out_color = vec4(color, 1.0); // a debug view shows its values as they are
    return;
#endif
    float depth = texelFetch(depthtex0, texel, 0).r;
    vec3 view = pixel_to_view(texel, min(depth, 0.9999999));
    bool sky_pixel = depth >= 1.0;
    float far_end = far; // where the world drawn ends
#ifdef DISTANT_HORIZONS
    if (sky_pixel) {
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
            vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
            view = v.xyz / v.w;
            sky_pixel = false;
        }
    }
    far_end = max(far, float(dhRenderDistance));
#endif
    vec3 scene = view_to_scene_pos(view);
    float dist = sky_pixel ? 1e4 : length(scene);
    vec3 dir = normalize(scene);
    float solid = sky_pixel ? 0.0 : 1.0;

    // Water or lava around you.
    if (isEyeInWater == 1) {
        float d = min(dist, 96.0);
        vec3 through = exp(-water_extinction * d);
        vec3 lit = (g_light_color.rgb * 0.25 + g_sky_up.rgb) * vanilla_sky_share(float(eyeBrightnessSmooth.y) / 240.0);
        lit += vec3(0.02, 0.04, 0.05) * CAVE_BRIGHTNESS * 0.2;
        vec3 scattered = lit * water_scattering / water_extinction * (1.0 - through) * vec3(0.55, 0.9, 1.0);
        color = color * through + scattered;
    } else if (isEyeInWater == 2) {
        float f = 1.0 - exp(-dist * 1.2);
        color = mix(color, vec3(1.6, 0.35, 0.05), f);
    }

#if defined SUNLIGHT
    // Haze far off.
    if (isEyeInWater == 0) {
        float height = scene.y + cameraPosition.y;
        // Starts slowly (squared), so the land around you stays clear: about 0.05 at 64 blocks, 0.15 at 128, 0.35
        // at 256.
        float density = 0.005 * exp(-max(height - 63.0, 0.0) / 60.0);
        float haze = sqr(1.0 - exp(-min(dist, far_end) * density));
        float border = smoothstep(far_end * 0.8, far_end, dist);
        haze = max(haze * 0.7, border);
        // Mixed in display space, like the light on surfaces: in linear light even a little of the bright horizon
        // lifts dark land a lot, so the distance greys out.
        const vec3 to_display = vec3(1.0 / 2.2);
        vec3 shown = mix(pow(max(color, 0.0), to_display), pow(g_horizon.rgb, to_display), haze * solid);
        color = pow(shown, vec3(2.2));
    }
#elif defined WORLD_NETHER
    float haze = 1.0 - exp(-dist * 0.012);
    color = mix(color, srgb_to_linear(fogColor) * 0.23, haze);
#endif

    // The coloured light in the air.
    if (LIGHT_FOG > 0.0 && isEyeInWater != 2) {
        float reach = min(dist, float(vox_half.x));
        vec3 fog = light_in_air(dir, reach, texel);
#if defined SUNLIGHT
        float open = float(eyeBrightnessSmooth.y) / 240.0;
        float strength = mix(1.0, 0.15, open * smoothstep(-0.05, 0.15, g_sun_dir.w));
#elif defined WORLD_NETHER
        float strength = 2.0;
#else
        float strength = 1.0;
#endif
        fog *= strength * LIGHT_FOG;
        if (isEyeInWater == 1) {
            fog *= vec3(0.4, 0.8, 1.0);
        }
        // Mixed in display space, like the light on surfaces (see lib/lighting.glsl): faint haze stays faint.
        vec3 shown = pow(max(color, 0.0), vec3(1.0 / 2.2));
        shown = shown * (1.0 - clamp(max3(fog) * 0.5, 0.0, 0.5)) + fog * 0.65;
        color = pow(shown, vec3(2.2));
    }

    out_color = vec4(color, 1.0);
}

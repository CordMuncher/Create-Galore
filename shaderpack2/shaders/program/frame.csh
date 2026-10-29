// The frame's own values, worked out once by one small work group for every later pass (see lib/globals.glsl): where
// the sun and the moon are, the light the one that lights the world gives, and the sky's light on surfaces facing
// up, sideways and down, from the sky view table (see program/sky.csh). The Nether has no sun and no sky: its
// light comes from its fog's colour. The End has a still light from above, from where Iris puts its shadow light (so
// it casts shadows through the shadow map).
//
// The light on surfaces is set apart from the sky as you see it, and tuned to the look we follow (numbers are linear
// light, at the fixed exposure in lib/post.glsl):
// - the sun's: what gets through the air, times a warm colour that grows as the sun sinks (sun_high at noon, sun_low
//   at the horizon, mixed by the square root of its height's sine), so mid-afternoon is golden and evenings glow;
//   about 1.9, 1.1, 0.55 at noon and 2.7, 0.5, 0.05 a few degrees up;
// - the moon's: faint and only a little cool (about 0.006, 0.0055, 0.008 at its height, full);
// - the sky's: the air's own, pulled towards grey (sky_saturation), brighter and warmer as the sun sinks (the air
//   alone dims too fast for the look) and on its own scale at night: about 0.22, 0.38, 0.71 facing up at noon,
//   0.023, 0.04, 0.075 under a full moon;
// - in rain the sun is mostly gone and the sky goes grey.
// So sunlit and shaded parts differ in colour as well as in brightness: warm in the sun, blue in the shade (see
// lib/lighting.glsl for how they mix).

#define WRITE_GLOBALS
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/globals.glsl"
#include "/lib/view.glsl"
#ifndef NO_SKY_LIGHT
#include "/lib/sky.glsl"
#endif

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform int moonPhase;
uniform vec3 fogColor;
uniform float rainStrength;
layout(rgba32f) writeonly uniform image2D cg_globals_img;

const vec3 sun_high = vec3(2.0, 1.25, 0.72);
const vec3 sun_low = vec3(6.0, 2.45, 1.0);
const vec3 moon_light = vec3(0.0064, 0.0063, 0.0104);
const float sky_high = 1.26;
const float sky_low = 3.8;
const float sky_night = 1.8;
const vec3 sky_low_tint = vec3(1.5, 0.85, 0.55);
const float sky_saturation = 0.7;

// A colour's distance from grey scaled by a power of it (under 1 pulls it towards grey), keeping its brightness.
vec3 push_from_grey(vec3 c, float by) {
    vec3 pushed = pow(max(c, 1e-6), vec3(by));
    return pushed * (luminance(c) / max(luminance(pushed), 1e-6));
}

shared vec3 s_up[64];
shared vec3 s_side[64];

void main() {
    uint i = gl_LocalInvocationIndex;
    vec3 sun = normalize(mat3(gbufferModelViewInverse) * sunPosition);
    vec3 moon = -sun;
    float phase = abs(float(moonPhase) - 4.0) / 4.0; // 0 new .. 1 full
    if (i == 0u) {
        g_sun_dir = vec4(sun, sun.y);
        g_moon_dir = vec4(moon, phase);
    }
    memoryBarrierShared();
    barrier();

#ifndef NO_SKY_LIGHT
    // A direction from an 8 x 8 grid over a half sphere, spread by the cosine (so the plain average is the light on
    // a surface facing its middle).
    float a = (float(i % 8u) + 0.5) / 8.0;
    float b = (float(i / 8u) + 0.5) / 8.0;
    float r = sqrt(b);
    vec2 disk = r * vec2(cos(TAU * a), sin(TAU * a));
    float h = sqrt(max(0.0, 1.0 - b));
    s_up[i] = sky_air(vec3(disk.x, h, disk.y));
    // Sideways: the four sides averaged; the half below the horizon sees the ground.
    vec3 side = vec3(0.0);
    for (int k = 0; k < 4; k++) {
        float az = float(k) * 0.5 * PI;
        vec3 n = vec3(cos(az), 0.0, sin(az));
        vec3 t = vec3(0.0, 1.0, 0.0);
        vec3 bt = cross(n, t);
        vec3 dir = n * h + t * disk.x + bt * disk.y;
        side += dir.y >= 0.0 ? sky_air(dir) : vec3(-1.0);
    }
    s_side[i] = side * 0.25;
#endif
    barrier();
    if (i != 0u) {
        return;
    }

#if defined WORLD_NETHER
    vec3 haze = srgb_to_linear(fogColor);
    g_light_dir = vec4(0.0, 1.0, 0.0, 0.0);
    g_light_color = vec4(0.0);
    // The light in the Nether's air, on every surface the bounce light finds open (see lib/lighting.glsl): its fog's
    // hue a little towards grey, about as bright as a moonlit night, whatever the biome.
    vec3 hue = mix(vec3(luminance(haze)), haze, 0.75) / max(luminance(haze), 1e-4);
    vec3 nether_air = hue * 0.035 * NIGHT_BRIGHTNESS;
    g_sky_up = vec4(nether_air, 0.0);
    g_sky_side = vec4(nether_air, 0.0);
    g_sky_down = vec4(nether_air * 0.8, 0.0);
    g_horizon = vec4(haze * 0.23, 0.0);
#elif defined WORLD_END
    vec3 end_light = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
    g_light_dir = vec4(end_light.y > 0.2 ? end_light : normalize(vec3(0.25, 1.0, 0.4)), 0.0);
    g_light_color = vec4(vec3(0.42, 0.34, 0.52) * 0.34, 0.0);
    g_sky_up = vec4(vec3(0.20, 0.14, 0.26) * 0.14, 0.0);
    g_sky_side = vec4(vec3(0.20, 0.14, 0.26) * 0.11, 0.0);
    g_sky_down = vec4(vec3(0.20, 0.14, 0.26) * 0.057, 0.0);
    g_horizon = vec4(vec3(0.029, 0.02, 0.04), 0.0);
#else
    vec3 up = vec3(0.0);
    vec3 side_sky = vec3(0.0);
    float side_ground = 0.0;
    for (int k = 0; k < 64; k++) {
        up += s_up[k];
        if (s_side[k].x >= 0.0) {
            side_sky += s_side[k];
        } else {
            side_ground += 1.0;
        }
    }
    up /= 64.0;

    // The light that casts the shadows: the sun by day, the moon by night, fading out as it nears the horizon (where
    // the shadow map turns over to the other one).
    vec3 light = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
    bool by_sun = dot(light, sun) > 0.0;
    float cam_r = camera_radius(cameraPosition.y);
    vec3 through = air_transmittance(cam_r, max(light.y, 0.0));
    float rise = smoothstep(0.0, 0.08, light.y);
    float noon = sqrt(max(sun.y, 0.0));
    vec3 moon_now = moon_light * NIGHT_BRIGHTNESS * (0.35 + 0.65 * g_moon_dir.w); // a new moon gives a third
    vec3 light_color = through * (by_sun ? mix(sun_low, sun_high, noon) : moon_now);
    float rain = rainStrength;
    light_color = mix(light_color, vec3(luminance(light_color)), 0.6 * rain) * mix(1.0, 0.08, rain);
    g_light_dir = vec4(light, by_sun ? 1.0 : 0.0);
    g_light_color = vec4(light_color * rise, 0.0);

    // The sky: day and night swap over while the sun is just under the horizon (the air's own twilight fades out on
    // its own).
    float day = smoothstep(-0.06, 0.0, sun.y);
    vec3 sky_tint = mix(vec3(1.0), mix(sky_low_tint, vec3(1.0), noon), day)
            * mix(sky_night, mix(sky_low, sky_high, noon), day);
    vec3 overcast = vec3(0.9, 0.95, 1.1);
    vec3 up_lit = push_from_grey(up, sky_saturation) * sky_tint;
    up_lit = mix(up_lit, luminance(up_lit) * overcast, 0.7 * rain) * mix(1.0, 0.5, rain);
    vec3 side_lit = push_from_grey(side_sky / 64.0, sky_saturation) * sky_tint;
    side_lit = mix(side_lit, luminance(side_lit) * overcast, 0.7 * rain) * mix(1.0, 0.5, rain);

    // The ground under the horizon: lit by the sun and the sky, a dull grey-green.
    vec3 ground = vec3(0.12, 0.13, 0.10) * (up_lit + g_light_color.rgb * max(light.y, 0.0));
    g_sky_up = vec4(up_lit, 0.0);
    g_sky_side = vec4(side_lit + ground * side_ground / 64.0, 0.0);
    g_sky_down = vec4(ground, 0.0);
    vec3 away = normalize(vec3(-sun.x, 0.0, -sun.z) + vec3(1e-4, 0.0, 0.0));
    g_horizon = vec4(sky_air(normalize(away + vec3(0.0, 0.05, 0.0))), 0.0);
#endif

    for (int k = 0; k < GLOBALS_COUNT; k++) {
        imageStore(cg_globals_img, ivec2(k, 0), g_globals[k]);
    }
}

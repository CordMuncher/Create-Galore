// The frame's own values, worked out once by one small work group for every later pass (see lib/globals.glsl): where
// the sun and the moon are, the light the one that lights the world gives, and the sky's light on surfaces facing
// up, sideways and down, from the sky view table (see program/sky.csh). The Nether has no sun and no sky: its
// light comes from its fog's colour. The End has a still light from above.

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
layout(rgba32f) writeonly uniform image2D cg_globals_img;

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
    g_sky_up = vec4(haze * 0.10, 0.0);
    g_sky_side = vec4(haze * 0.10, 0.0);
    g_sky_down = vec4(haze * 0.10, 0.0);
    g_horizon = vec4(haze * 0.4, 0.0);
#elif defined WORLD_END
    g_light_dir = vec4(normalize(vec3(0.25, 1.0, 0.4)), 0.0);
    g_light_color = vec4(vec3(0.42, 0.34, 0.52) * 0.6, 0.0);
    g_sky_up = vec4(vec3(0.20, 0.14, 0.26) * 0.25, 0.0);
    g_sky_side = vec4(vec3(0.20, 0.14, 0.26) * 0.2, 0.0);
    g_sky_down = vec4(vec3(0.20, 0.14, 0.26) * 0.1, 0.0);
    g_horizon = vec4(vec3(0.05, 0.035, 0.07), 0.0);
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
    vec3 light_color = by_sun ? through * sun_illuminance / PI : through * moon_illuminance() / PI * vec3(0.86, 0.93, 1.0);
    g_light_dir = vec4(light, by_sun ? 1.0 : 0.0);
    g_light_color = vec4(light_color * rise, 0.0);

    // The ground under the horizon: lit by the sun and the sky, a dull grey-green.
    vec3 ground = vec3(0.12, 0.13, 0.10) * (up + g_light_color.rgb * max(light.y, 0.0));
    g_sky_up = vec4(up, 0.0);
    g_sky_side = vec4((side_sky + ground * side_ground) / 64.0, 0.0);
    g_sky_down = vec4(ground, 0.0);
    vec3 away = normalize(vec3(-sun.x, 0.0, -sun.z) + vec3(1e-4, 0.0, 0.0));
    g_horizon = vec4(sky_air(normalize(away + vec3(0.0, 0.05, 0.0))), 0.0);
#endif

    for (int k = 0; k < GLOBALS_COUNT; k++) {
        imageStore(cg_globals_img, ivec2(k, 0), g_globals[k]);
    }
}

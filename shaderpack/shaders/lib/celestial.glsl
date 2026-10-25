// The sun, the moon and the stars over the overworld, in real units (see globals.glsl). The sun is a 5772 K black
// body of its real size, 128 klux above the air; the moon is lit by it (Alpha Piscium's albedo and tint, its phase
// Minecraft's), brightened by the Night Light option so nights stay playable.

#if !defined LIB_CELESTIAL
#define LIB_CELESTIAL

#include "/lib/settings.glsl"
#include "/lib/view.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

uniform vec3 sunPosition;  // view space, 100 blocks out
uniform vec3 moonPosition;
uniform int moonPhase;     // 0 full moon .. 4 new moon .. 7

// 5772 K in linear sRGB, luminance 1, times 128 klux.
const vec3 sun_illuminance = vec3(1.0650, 0.9809, 0.9974) * 128.0;
const float sun_angular_radius = 0.0046504;  // radians (0.2665 degrees)
const float sun_solid_angle = 6.7943e-5;     // steradians
const float moon_angular_radius = 0.0045198; // radians (0.259 degrees)
const float moon_solid_angle = 6.4178e-5;
const vec3 moon_albedo = 0.12 * vec3(0.8, 0.9, 1.0);
// The moon (and the starlight) as much brighter than the real ones as nights need to be seen by: a full moon lights
// the ground with 0.3 lux in reality, which is near black.
const float moon_boost = 12.0 * NIGHT_LIGHT;

vec3 sun_dir_world() {
    return normalize(mat3(gbufferModelViewInverse) * sunPosition);
}

vec3 moon_dir_world() {
    return normalize(mat3(gbufferModelViewInverse) * moonPosition);
}

// How much of the moon's light reaches us at its phase: a Lambertian sphere lit from the phase angle.
float moon_phase_light() {
    float angle = float(moonPhase) * (PI / 4.0);
    return ((PI - angle) * cos(angle) + sin(angle)) / PI;
}

// The full moon's radiance (kcd/m²) and the moon's illuminance (klux) at the top of the air, its phase included.
vec3 moon_radiance() {
    return sun_illuminance * moon_albedo / PI * moon_boost;
}

vec3 moon_illuminance() {
    return moon_radiance() * moon_solid_angle * moon_phase_light();
}

#endif

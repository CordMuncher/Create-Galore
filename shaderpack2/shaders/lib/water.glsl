// Water: its colour (light it soaks up and light it scatters, per block of water) and its waves.
//
// The waves cross from three directions about 120 degrees apart, so the surface never reads as all moving one way,
// each in three sizes from a swell a few blocks long to fine ripples. Each is a ridge of smooth noise stretched
// across its direction and drifting along it at a speed that grows with the square root of its length (longer waves
// move faster, as on deep water), so the ripples stay calm while the swell rolls. Rates come from the clock, not
// the frame count, so the look is the same at any frame rate.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/common.glsl"

const vec3 water_absorption = vec3(0.30, 0.075, 0.05); // per block
const vec3 water_scattering = vec3(0.038, 0.04, 0.04);
const vec3 water_extinction = water_absorption + water_scattering;

// Smooth value noise and its slope (d/dx, d/dy), on a grid of whole numbers.
vec3 noise_slope(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    vec2 du = 6.0 * f * (1.0 - f);
    uvec2 c = uvec2(ivec2(i) + 32768);
    float a = hash33(uvec3(c, 41u)).x;
    float b = hash33(uvec3(c + uvec2(1, 0), 41u)).x;
    float d = hash33(uvec3(c + uvec2(0, 1), 41u)).x;
    float e = hash33(uvec3(c + uvec2(1, 1), 41u)).x;
    float k = a - b - d + e;
    float value = a + (b - a) * u.x + (d - a) * u.y + k * u.x * u.y;
    vec2 slope = du * vec2(b - a + k * u.y, d - a + k * u.x);
    return vec3(value, slope);
}

// The surface's slope (d height / dx, d height / dz) at a world point, t seconds into the hour. strength scales it
// (calm 0 .. 1); distance (blocks from the camera) drops the finest ripples far off, where they would only shimmer.
vec2 wave_slope(vec2 world_xz, float t, float strength, float distance) {
    const float lengths[3] = float[](4.5, 1.7, 0.65);
    const float heights[3] = float[](0.15, 0.055, 0.018);
    vec2 slope = vec2(0.0);
    for (int k = 0; k < 3; k++) {
        float angle = 0.35 + float(k) * TAU / 3.0;
        vec2 along = vec2(cos(angle), sin(angle));
        vec2 across = vec2(-along.y, along.x);
        for (int b = 0; b < 3; b++) {
            float fade = 1.0 - smoothstep(20.0 * lengths[b], 45.0 * lengths[b], distance);
            if (fade <= 0.0) {
                continue;
            }
            float wavelength = lengths[b];
            float speed = 0.55 * sqrt(9.8 * wavelength / TAU); // blocks a second
            // Long along the crest, short across it: ridges.
            vec2 q = vec2(dot(world_xz, along) - speed * t, dot(world_xz, across) * 0.4) / wavelength;
            q += float(k * 37 + b * 11);
            vec3 n = noise_slope(q);
            vec2 local = n.yz * vec2(1.0, 0.4) / wavelength * heights[b];
            slope += (along * local.x + across * local.y) * fade;
        }
    }
    return slope * strength;
}

vec3 wave_normal(vec2 world_xz, float t, float strength, float distance) {
    vec2 s = wave_slope(world_xz, t, strength, distance);
    return normalize(vec3(-s.x, 1.0, -s.y));
}

#endif

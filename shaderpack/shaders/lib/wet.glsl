// Rain on the world, in the lit overworld: puddles, damp ground around them, and the rings raindrops leave on water.
//
// Puddles gather on the tops of blocks that see the open sky, in the dips of a fixed pattern in the world (so the
// same spots fill every time it rains). They grow over the first half minute of rain and, once it stops, shrink back
// into their deepest spots and are gone in about four minutes (cg_puddle, see shaders.properties). Sand, gravel and
// snow soak the rain up and only darken; soil takes most of it in, so only its deepest dips hold water. Around every
// puddle the ground is darker where it is damp.

#if !defined LIB_WET
#define LIB_WET

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/motion.glsl"
#include "/lib/noise.glsl"
#include "/lib/gbuffer.glsl"
#include "/lib/weather.glsl"

// The pattern the puddles fill (about 0..1, its middle 0.5), at a point in the world (xz, blocks from the camera's
// whole block, so it stays exact far out: the pattern starts over every 40960 blocks).
float puddle_pattern(vec2 p) {
    float n = gradient_noise_2d(p / 6.0) + 0.5 * gradient_noise_2d(p / 2.7 + 17.3) + 0.25 * gradient_noise_2d(p / 1.2 + 41.9);
    return 0.5 + 0.7 * n;
}

// How much water stands on a surface (x: 0 dry, 1 a puddle) and how damp it is (y), for a surface facing normal
// (world space) at scene_pos, with sky light sky (0..1) and its kind (SURFACE_...).
vec2 puddle_at(vec3 scene_pos, vec3 normal, float sky, int surface) {
#ifndef RAIN_WETNESS
    return vec2(0.0); // Rain Wetness off
#endif
    float fade = smoothstep(0.03, 0.1, cg_puddle) * smoothstep(14.0 / 15.0, 14.8 / 15.0, sky);
    if (fade <= 0.0 || normal.y < 0.9 || surface == SURFACE_LEAVES) {
        return vec2(0.0);
    }
    vec2 p = vec2((cameraPositionInt.xz % 40960 + 40960) % 40960) + cameraPositionFract.xz + scene_pos.xz;
    float n = puddle_pattern(p);
    // The threshold the pattern must pass: from above its top while dry down to the share a good rain covers (about
    // a fifth of stone, a thirtieth of soil). As they dry they keep half their area for the first minute, then
    // shrink to a few small ones by two and a half minutes and are gone by four.
    float full = surface == SURFACE_SAND ? 2.0 : surface == SURFACE_SOIL ? 0.75 : 0.6;
    float t = mix(1.02, full, sqrt(sqrt(cg_puddle)));
    float water = smoothstep(t, t + 0.03, n) * fade;
    float damp = smoothstep(t - 0.12, t, n) * fade;
    return vec2(water, max(damp, water));
}

// The slope (dh/dx, dh/dz) of the rings raindrops leave on still water at a point (scene space), while it rains
// (amount 0..1). Each cell of two offset grids gets a drop at a random spot and moment, once a second, whose ring
// spreads and fades over its second.
vec2 rain_ripple_slope(vec3 scene_pos, float amount) {
    if (amount <= 0.0) {
        return vec2(0.0);
    }
    const float cells_per_block = 2.5;
    vec2 world = vec2((cameraPositionInt.xz % 4096 + 4096) % 4096) + cameraPositionFract.xz + scene_pos.xz;
    vec2 slope = vec2(0.0);
    for (int layer = 0; layer < 2; layer++) {
        vec2 p = world * cells_per_block + float(layer) * vec2(0.37, 0.71);
        vec2 cell = floor(p);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec2 c = cell + vec2(x, y);
                vec3 r = hash_to_float(hash_pcg3d(uvec3(ivec3(c, layer))));
                float life = fract(frameTimeCounter * (0.9 + 0.3 * r.z) + r.z * 7.0);
                if (fract(r.z * 13.0) > amount) {
                    continue; // no drop here in light rain
                }
                vec2 d = p - (c + r.xy);
                float dist = length(d);
                float radius = life * 1.2;
                float x_ring = (dist - radius) * 9.0;
                // A ring's cross-section: a crest with a trough each side, its slope towards the outside.
                float ring = -2.0 * x_ring * exp(-x_ring * x_ring) * (1.0 - life) * (1.0 - life);
                slope += d / max(dist, 1e-3) * ring;
            }
        }
    }
    return slope * 0.25 * amount;
}

#endif

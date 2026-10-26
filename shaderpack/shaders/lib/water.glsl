// Water in the lit overworld (see settings.glsl): small wind waves on every water surface, and ripples where
// something moves through it, both only in the surface's normal (the water's blocks stay where they are, so its
// motion vectors stay exact).
//
// The waves: eight trains of sine waves of 0.9 to 7 blocks, each moving at the speed deep water waves of its length
// do, every one a whole number of times over 4096 blocks and over an hour, so they join up anywhere in the world
// and when the game's clock (frameTimeCounter) goes back to 0 each hour.
//
// The ripples: the wave equation on a grid of 512 by 512 cells around you, a quarter of a block each (128 blocks
// across), stepped 60 times a second whatever the frame rate (water_sim.csh, after the shadow pass). The shadow pass
// marks, in each cell, the height of the water's surface and how low and how high anything else that casts a shadow
// reaches there (you, mobs, boats, Sable ships, Create's contraptions and water wheels: whatever the shadow
// options let cast shadows). Where something reaches through the surface it pushes the water down, so a thing
// standing still leaves a dimple and a thing moving leaves ripples and a wake.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/settings.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

#ifdef LIT_WATER_RIPPLES
const int water_sim_size = 512;      // cells across
const float water_cells_per_block = 4.0;
const int water_wrap = 1 << 20;      // cell positions are kept modulo this (exact as floats)

// A height relative to the camera (blocks, -128..128) in the marks' 16 bits, 0 meaning none.
uint water_height_mark(float y) {
    return uint(clamp((y + 128.0) * 256.0, 1.0, 65535.0));
}

float water_height_from_mark(uint mark) {
    return float(mark) / 256.0 - 128.0;
}

// The cell a point (scene space) is in, counted from the corner of the grid centred on the camera's cell.
ivec2 water_cell(vec3 scene_pos, vec3 camera_fract) {
    return ivec2(floor((camera_fract.xz + scene_pos.xz) * water_cells_per_block))
            - ivec2(floor(camera_fract.xz * water_cells_per_block)) + water_sim_size / 2;
}

// A difference of two positions modulo water_wrap, back to -water_wrap/2..water_wrap/2.
ivec2 water_wrap_delta(ivec2 d) {
    return ((d % water_wrap) + water_wrap + water_wrap / 2) % water_wrap - water_wrap / 2;
}
#endif

#if !defined PROGRAM_SHADOW && !defined PROGRAM_WATER_SIM
#include "/lib/motion.glsl"

uniform float frameTimeCounter; // seconds, back to 0 each hour

// The wind waves' slope (dh/dx, dh/dz) at a point (scene space).
vec2 wave_slope(vec3 scene_pos) {
    // Each train: its wave counts over 4096 blocks along x and z, its waves an hour, and its height (blocks).
    const ivec4 trains[8] = ivec4[8](
            ivec4(507, 293, 1701, 4453), ivec4(339, 728, 1992, 2953), ivec4(1107, 0, 2339, 1945),
            ivec4(0, 1412, 2641, 1418), ivec4(1768, -824, 3104, 932), ivec4(1810, 1810, 3556, 654),
            ivec4(2414, -2414, 4107, 450), ivec4(-1923, 4125, 4742, 310));
    // Where the point is: whole blocks modulo 4096 (exact), and the rest.
    vec2 rest = cameraPositionFract.xz + scene_pos.xz;
    ivec2 whole = ((cameraPositionInt.xz + ivec2(floor(rest))) % 4096 + 4096) % 4096;
    rest = fract(rest);
    float hours = frameTimeCounter / 3600.0;
    vec2 slope = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        ivec4 w = trains[i];
        // The phase in turns: the whole blocks' part in integers, so it stays exact far out.
        int turns_whole = (w.x * whole.x + w.y * whole.y) % 4096;
        float turns = float(turns_whole) / 4096.0 + dot(vec2(w.xy), rest) / 4096.0 - fract(float(w.z) * hours);
        float k_amp = 2.0 * PI / 4096.0 * float(w.w) * 1e-5;
        slope += vec2(w.xy) * k_amp * cos(2.0 * PI * turns);
    }
    return slope * WATER_WAVES;
}

#ifdef LIT_WATER_RIPPLES
#include "/lib/globals.glsl"

uniform sampler2D cg_water_tex;

// The ripples' height (blocks) at a point (scene space), between the cells' centres.
float ripple_height(vec2 cell, int half_x) {
    vec2 at = clamp(cell, vec2(0.5), vec2(water_sim_size) - 0.5) + vec2(half_x, 0.0);
    return texture(cg_water_tex, at / vec2(2 * water_sim_size, water_sim_size)).r;
}

// The ripples' slope (dh/dx, dh/dz) at a point on the water (scene space), from the state the last of this frame's
// steps left (see water_sim.csh).
vec2 ripple_slope(vec3 scene_pos) {
    vec4 water = global(G_WATER);
    vec4 origin = global(G_WATER_ORIGIN);
    int half_x = (int(water.y + water.x) & 1) * water_sim_size;
    // The point's cell, continuous, counted from the grid's corner.
    ivec2 camera_cell = cameraPositionInt.xz * 4 + ivec2(floor(cameraPositionFract.xz * water_cells_per_block));
    ivec2 corner = water_wrap_delta(camera_cell - ivec2(origin.xy));
    vec2 cell = vec2(corner) + (cameraPositionFract.xz + scene_pos.xz) * water_cells_per_block
            - floor(cameraPositionFract.xz * water_cells_per_block);
    if (any(lessThan(cell, vec2(2.0))) || any(greaterThan(cell, vec2(water_sim_size - 2)))) {
        return vec2(0.0);
    }
    float dx = ripple_height(cell + vec2(1.0, 0.0), half_x) - ripple_height(cell - vec2(1.0, 0.0), half_x);
    float dz = ripple_height(cell + vec2(0.0, 1.0), half_x) - ripple_height(cell - vec2(0.0, 1.0), half_x);
    // Fading out towards the grid's edges.
    vec2 edge = min(cell, vec2(water_sim_size) - cell);
    float fade = smoothstep(2.0, 48.0, min(edge.x, edge.y));
    return vec2(dx, dz) * (water_cells_per_block * 0.5) * fade * WATER_RIPPLES_STRENGTH;
}
#endif

// The normal of a water surface facing up at a point (scene space): the waves', and the ripples' where they are.
vec3 water_normal(vec3 scene_pos) {
    vec2 slope = wave_slope(scene_pos);
#ifdef LIT_WATER_RIPPLES
    slope += ripple_slope(scene_pos);
#endif
    return normalize(vec3(-slope.x, 1.0, -slope.y));
}
#endif

#endif

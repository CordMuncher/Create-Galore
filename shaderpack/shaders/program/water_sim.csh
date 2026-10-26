// The overworld's shadowcomp passes 2 to 4: one step each of the ripples' wave equation (see water.glsl), for as
// many steps as this frame has (WATER_STEP: which of them this pass is). The state, each cell's height now and a
// step ago, alternates between the two halves of its image: each step reads one and writes the other. The first
// step reads last frame's state where the camera's move put each cell in it.

#define PROGRAM_WATER_SIM
#include "/lib/water.glsl"
#include "/lib/globals.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(32, 32, 1);

layout(rg16f) uniform image2D cg_water_img;
layout(r32ui) readonly uniform uimage2D cg_water_marks_img;

// The waves' speed (2 blocks a second, as ripples a few blocks long move) over the cells' size, per step, squared;
// and how much of their height they keep each step (half of it in a little over a second).
const float courant2 = (2.0 / 60.0 * 4.0) * (2.0 / 60.0 * 4.0);
const float damping = 0.99;
// How far something reaching through the surface pushes it down (blocks), and how quickly.
const float push_depth = 0.12;
const float push_rate = 0.35;

int read_x;
ivec2 shift;

float height(ivec2 cell, int layer) {
    ivec2 at = cell + shift;
    if (any(lessThan(at, ivec2(0))) || any(greaterThanEqual(at, ivec2(water_sim_size)))) {
        return 0.0;
    }
    return imageLoad(cg_water_img, at + ivec2(read_x, 0))[layer];
}

void main() {
    vec4 water = global(G_WATER);
    if (WATER_STEP >= int(water.x)) {
        return;
    }
    ivec2 cell = ivec2(gl_GlobalInvocationID.xy);
    int parity = int(water.y) + WATER_STEP;
    read_x = (parity & 1) * water_sim_size;
    int write_x = water_sim_size - read_x;
#if WATER_STEP == 0
    vec4 origin = global(G_WATER_ORIGIN);
    shift = water_wrap_delta(ivec2(origin.xy) - ivec2(origin.zw));
#else
    shift = ivec2(0);
#endif

    float now = height(cell, 0);
    float before = height(cell, 1);
    float around = height(cell + ivec2(1, 0), 0) + height(cell - ivec2(1, 0), 0)
            + height(cell + ivec2(0, 1), 0) + height(cell - ivec2(0, 1), 0);
    float next = (2.0 * now - before + courant2 * (around - 4.0 * now)) * damping;

    // Something reaching through the water's surface here pushes it down.
    uint surface = imageLoad(cg_water_marks_img, cell).r;
    uint lowest = imageLoad(cg_water_marks_img, cell + ivec2(0, water_sim_size)).r;
    uint highest = imageLoad(cg_water_marks_img, cell + ivec2(0, 2 * water_sim_size)).r;
    if (surface != 0u && highest != 0u) {
        float level = water_height_from_mark(surface);
        float low = water_height_from_mark(65536u - lowest);
        float high = water_height_from_mark(highest);
        if (low < level + 0.05 && high > level - 0.6) {
            next = mix(next, -push_depth, push_rate);
        }
    }
    imageStore(cg_water_img, cell + ivec2(write_x, 0), vec4(clamp(next, -1.0, 1.0), now, 0.0, 0.0));
}

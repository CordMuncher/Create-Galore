// Wakes: the ripples that spread from what moves through the water's surface near you (you, other players, mobs,
// boats, Sable's ships; the Create Galore Bridge lists them, see program/wakes.csh). Two height fields 64 blocks
// across around you, 8 cells a block, fixed to the world: each cell keeps its place in the world as you move (the
// slots wrap around), so a wake stays where it was made. Their ripples run at two speeds (real water's longer waves
// outrun its shorter ones), so what moves leaves a V behind it however fast it goes. They live in cg_data_img's rows
// past the light tables (lib/data.glsl): two copies (last frame's is read, this frame's written) of each cell's
// heights (blocks, a field in each half of a word) and then of how fast they rise (blocks a second), then the slope
// of the surface both give there, which the water's forward pass reads.

#if !defined LIB_WAKES
#define LIB_WAKES

#include "/lib/view.glsl"
#include "/lib/data.glsl"

const int wake_side = 512;          // cells a side (a power of two: the slots wrap with a mask)
const int wake_per_block = 8;
const int wake_reach = 32;          // blocks from your block to the field's edge
const uint data_wake_base = 1552u * 4096u;
const uint data_wake_cells = 512u * 512u;
const uint data_wake_slope = data_wake_base + 4u * data_wake_cells;

// Where a copy of the heights (rates false) or of how fast they rise (rates true) starts.
uint wake_state_base(int copy, bool rates) {
    return data_wake_base + (uint(copy & 1) * 2u + (rates ? 1u : 0u)) * data_wake_cells;
}

// The field's low corner (cells, world) for a camera block.
ivec2 wake_window_lo(ivec3 camera_block) {
    return (camera_block.xz - wake_reach) * wake_per_block;
}

bool wake_in_window(ivec2 cell, ivec2 lo) {
    ivec2 c = cell - lo;
    return all(greaterThanEqual(c, ivec2(0))) && all(lessThan(c, ivec2(wake_side)));
}

uint wake_word(uint base, ivec2 cell) {
    ivec2 slot = cell & (wake_side - 1);
    return base + uint(slot.y * wake_side + slot.x);
}

// A pair of halves as read back, or nothing where it isn't a number (nothing clears the image when the pack loads, so
// the fields can start on whatever was in that memory) or has run away.
vec2 wake_sane(vec2 x) {
    return mix(vec2(0.0), x, lessThan(abs(x), vec2(16.0)));
}

// The slope of the water's surface the wakes give at a point (scene space): blended between the four nearest cells.
vec2 wake_slope(vec3 scene_pos) {
    vec2 at = (cameraPositionFract.xz + scene_pos.xz) * float(wake_per_block) - 0.5;
    ivec2 corner = cameraPositionInt.xz * wake_per_block + ivec2(floor(at));
    vec2 f = fract(at);
    ivec2 lo = wake_window_lo(cameraPositionInt);
    if (!wake_in_window(corner, lo) || !wake_in_window(corner + 1, lo)) {
        return vec2(0.0);
    }
    vec2 a = unpackHalf2x16(data_load(wake_word(data_wake_slope, corner)));
    vec2 b = unpackHalf2x16(data_load(wake_word(data_wake_slope, corner + ivec2(1, 0))));
    vec2 c = unpackHalf2x16(data_load(wake_word(data_wake_slope, corner + ivec2(0, 1))));
    vec2 d = unpackHalf2x16(data_load(wake_word(data_wake_slope, corner + ivec2(1, 1))));
    return wake_sane(mix(mix(a, b, f.x), mix(c, d, f.x), f.y));
}

#endif

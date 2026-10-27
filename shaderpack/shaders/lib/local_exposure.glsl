// Local exposure, after DLSS (or FSR): a room's window doesn't burn out to white and the room stays as it is. The
// frame's brightness at large is drawn in towards the middle where it is brighter than the average, its detail left
// as it is (Ghost of Tsushima's, Patry 2021: Chen, Paris and Durand's bilateral grid of the frame's log luminance,
// blended with a wide blur of it so its edges don't ring; a contrast for what is brighter than the average and none
// for what is darker, so night stays night). The grid is worked out in local_exposure.csh, in rows 1 and up of the
// globals image (see globals.glsl).

#if !defined LIB_LOCAL_EXPOSURE
#define LIB_LOCAL_EXPOSURE

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"

// The grid: 64 by 32 tiles of the frame, each with 64 steps of log2 luminance (of the frame's values) from 8 stops
// under the frame's average to 8 over it.
const ivec2 lex_tiles = ivec2(64, 32);
const int lex_steps = 64;
const float lex_range = 16.0;
// Rows of the globals image: the tiles' grids (a row of tiles, their steps side by side), the tiles' own averages,
// and those blurred wide.
const int lex_grid_row = 1;
const int lex_means_row = lex_grid_row + 32;
const int lex_wide_row = lex_means_row + 1;
// How much of the large-scale brightness above the average is kept (1 all of it), and how much of the grid against
// the wide blur.
const float lex_highlights = 1.0 - 0.3 * LOCAL_EXPOSURE;
const float lex_grid_share = 0.4;

// The frame's average (log2 of its values) the exposure brings it to: the middle of the grid's steps.
float lex_middle(vec4 exposure_data) {
    return exposure_data.y + exposure_data.z;
}

// Which step (fractional, 0 at the middle of the first) a log2 luminance falls in.
float lex_step_of(float log_luminance, float middle) {
    return (log_luminance - middle + 0.5 * lex_range) / lex_range * float(lex_steps) - 0.5;
}

#ifndef GLOBALS_WRITE
// The grid's blurred log luminance at a tile (fractional, 0 at the middle of the first) and step: the sums and their
// weights between the eight around it, so empty steps don't count.
float lex_grid_at(vec2 tile, float step) {
    vec2 t0 = floor(tile);
    float s0 = floor(step);
    vec2 ft = tile - t0;
    float fs = step - s0;
    vec2 sum = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        ivec3 o = ivec3(i & 1, (i >> 1) & 1, i >> 2);
        ivec2 t = clamp(ivec2(t0) + o.xy, ivec2(0), lex_tiles - 1);
        int s = clamp(int(s0) + o.z, 0, lex_steps - 1);
        float w = (o.x == 1 ? ft.x : 1.0 - ft.x) * (o.y == 1 ? ft.y : 1.0 - ft.y) * (o.z == 1 ? fs : 1.0 - fs);
        sum += texelFetch(cg_globals_tex, ivec2(t.x * lex_steps + s, lex_grid_row + t.y), 0).ba * w;
    }
    return sum.y > 1e-6 ? sum.x / sum.y : 1e9;
}

// The wide blur's log luminance at a tile (fractional).
float lex_wide_at(vec2 tile) {
    vec2 t0 = floor(tile);
    vec2 f = tile - t0;
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 t = clamp(ivec2(t0) + o, ivec2(0), lex_tiles - 1);
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
        sum += texelFetch(cg_globals_tex, ivec2(t.x + t.y * lex_tiles.x, lex_wide_row), 0).r * w;
    }
    return sum;
}

// The frame's colour at a window pixel (at its centre) with the local exposure.
vec3 local_exposure(vec3 color, vec2 window_pixel) {
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    if (!(luminance > 1e-12)) {
        return color;
    }
    float middle = lex_middle(g_exposure_data);
    float here = log2(luminance);
    vec2 tile = window_pixel / window_size() * vec2(lex_tiles) - 0.5;
    float grid = lex_grid_at(tile, lex_step_of(here, middle));
    float wide = lex_wide_at(tile);
    float base = grid < 1e8 ? mix(wide, grid, lex_grid_share) : wide;
    float above = max(base - middle, 0.0);
    return color * exp2((lex_highlights - 1.0) * above);
}
#endif

#endif

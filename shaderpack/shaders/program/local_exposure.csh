// The local exposure's grid (see local_exposure.glsl), from the frame after DLSS (or FSR) and the bloom.
// LEX_BUILD: each tile of the frame sorts its pixels' log luminance into the grid's steps (sums and counts), and keeps
// its own average. LEX_BLUR: the grid blurred a little across tiles and steps (Chen, Paris and Durand's bilateral
// grid: a blur that stops at edges, as it doesn't mix steps far apart), and the tiles' averages blurred wide.

#define GLOBALS_WRITE
#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"
#include "/lib/local_exposure.glsl"

#if defined LEX_BUILD

// One group for each tile.
layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(64, 32, 1);

uniform sampler2D colortex0;

shared uint shared_sum[lex_steps];   // the log luminance above the step's start, in 1/256 stops
shared uint shared_count[lex_steps];

void main() {
    if (LOCAL_EXPOSURE <= 0.0) {
        return; // Local Exposure off: final.fsh doesn't read the grid
    }
    uint index = gl_LocalInvocationIndex;
    if (index < uint(lex_steps)) {
        shared_sum[index] = 0u;
        shared_count[index] = 0u;
    }
    barrier();

    // The frame as it is now: DLSS's result at the window's size, else the render-size corner.
    vec2 area = (cg_dlss_active > 0.5 ? window_size() : render_size()) / window_size();
    float middle = lex_middle(global(G_EXPOSURE));
    ivec2 tile = ivec2(gl_WorkGroupID.xy);
    vec2 tile_size = window_size() / vec2(lex_tiles);
    // 32 by 32 points over the tile, 4 for each invocation.
    for (int i = 0; i < 4; i++) {
        vec2 at = (vec2(gl_LocalInvocationID.xy * 2u + uvec2(i & 1, i >> 1)) + 0.5) / 32.0;
        vec2 uv = (vec2(tile) + at) * tile_size / window_size();
        float luminance = dot(textureLod(colortex0, uv * area, 0.0).rgb, vec3(0.2126, 0.7152, 0.0722));
        if (!(luminance > 1e-12) || isinf(luminance)) {
            continue;
        }
        float v = clamp(log2(luminance) - middle + 0.5 * lex_range, 0.0, lex_range - 1e-3); // 0..16 stops
        int s = int(v / lex_range * float(lex_steps));
        float start = float(s) * (lex_range / float(lex_steps));
        atomicAdd(shared_sum[s], uint((v - start) * 256.0 + 0.5));
        atomicAdd(shared_count[s], 1u);
    }
    barrier();

    if (index < uint(lex_steps)) {
        int s = int(index);
        float count = float(shared_count[s]);
        float start = float(s) * (lex_range / float(lex_steps)) + middle - 0.5 * lex_range;
        // Sum (log2 of the frame's values) and count: what the blur adds up.
        vec2 step_sum = vec2(float(shared_sum[s]) / 256.0 + count * start, count);
        imageStore(cg_globals_img, ivec2(tile.x * lex_steps + s, lex_grid_row + tile.y), vec4(step_sum, 0.0, 0.0));
    }
    barrier();
    // The tile's own average, from its steps (the sums just kept in shared memory).
    if (index == 0u) {
        vec2 total = vec2(0.0);
        for (int s = 0; s < lex_steps; s++) {
            float count = float(shared_count[s]);
            float start = float(s) * (lex_range / float(lex_steps)) + middle - 0.5 * lex_range;
            total += vec2(float(shared_sum[s]) / 256.0 + count * start, count);
        }
        float mean = total.y > 0.0 ? total.x / total.y : middle;
        imageStore(cg_globals_img, ivec2(tile.x + tile.y * lex_tiles.x, lex_means_row), vec4(mean, 0.0, 0.0, 0.0));
    }
}

#elif defined LEX_BLUR

// One invocation for each step of each tile, and the first row of groups also blurs the tiles' averages.
layout(local_size_x = 64, local_size_y = 1) in;
const ivec3 workGroups = ivec3(64, 32, 1);

void main() {
    if (LOCAL_EXPOSURE <= 0.0) {
        return;
    }
    ivec2 tile = ivec2(gl_WorkGroupID.xy);
    int s = int(gl_LocalInvocationID.x);
    ivec2 texel = ivec2(tile.x * lex_steps + s, lex_grid_row + tile.y);

    // 5 by 5 tiles and 5 steps, weighted 1 4 6 4 1 each way.
    const float w5[5] = float[5](1.0, 4.0, 6.0, 4.0, 1.0);
    vec2 sum = vec2(0.0);
    for (int z = -2; z <= 2; z++) {
        int sz = s + z;
        if (sz < 0 || sz >= lex_steps) {
            continue;
        }
        for (int y = -2; y <= 2; y++) {
            int ty = clamp(tile.y + y, 0, lex_tiles.y - 1);
            for (int x = -2; x <= 2; x++) {
                int tx = clamp(tile.x + x, 0, lex_tiles.x - 1);
                vec2 v = imageLoad(cg_globals_img, ivec2(tx * lex_steps + sz, lex_grid_row + ty)).rg;
                sum += v * (w5[x + 2] * w5[y + 2] * w5[z + 2]);
            }
        }
    }
    vec4 own = imageLoad(cg_globals_img, texel);
    imageStore(cg_globals_img, texel, vec4(own.rg, sum));

    // The tiles' averages blurred wide (a Gaussian 13 tiles across each way), one tile for each of the first 32
    // invocations of the groups in the first two columns: 64 by 32 tiles from 64 groups of 32.
    if (tile.x < 2 && s < 32) {
        ivec2 t = ivec2(tile.y * 2 + tile.x, s);
        float total = 0.0;
        float weight = 0.0;
        for (int y = -13; y <= 13; y++) {
            int ty = t.y + y;
            if (ty < 0 || ty >= lex_tiles.y) {
                continue;
            }
            for (int x = -13; x <= 13; x++) {
                int tx = t.x + x;
                if (tx < 0 || tx >= lex_tiles.x) {
                    continue;
                }
                float w = exp(-float(x * x + y * y) / (2.0 * 6.5 * 6.5));
                total += imageLoad(cg_globals_img, ivec2(tx + ty * lex_tiles.x, lex_means_row)).r * w;
                weight += w;
            }
        }
        imageStore(cg_globals_img, ivec2(t.x + t.y * lex_tiles.x, lex_wide_row), vec4(total / weight, 0.0, 0.0, 0.0));
    }
}

#endif

// The overworld's shadowcomp pass 6: the inside of the ground, filled in in the sky grid (see sky_grid.glsl) before
// its coarser copies are made. The shadow pass sees a block only by its faces, and Minecraft draws no face between two
// solid blocks, so the grid had the rock around a cave and under the ground only a block thick: a ray that started
// behind it (the light in the air reaches into the walls, see air_in_block in air_volume.csh) or got into it went
// through the rock to the open sky.
//
// Each column is read from the top down by which way its blocks' faces point: under a block with a face up the ground
// begins, and it goes down to a block with a face down (a cave's roof, an overhang's underside) or to the grid's
// bottom; the blocks between that the shadow pass didn't mark are inside it. Where a block with a face up comes first,
// what lay between was air after all (over a block whose model has no face down), and it stays open.

#define PROGRAM_SKY_GRID
#include "/lib/sky_grid.glsl"

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(1024, 1, 1); // a column each, 256²

layout(r32ui) uniform uimage2D cg_water_marks_img;

uint marked[sky_grid_words];
uint inside[sky_grid_words];

// Marks the blocks from y0 to y1 (y0 <= y1) as inside the ground.
void fill_inside(int y0, int y1) {
    for (int word = y0 >> 5; word <= (y1 >> 5); word++) {
        int lo = max(y0 - word * 32, 0);
        int hi = min(y1 - word * 32, 31);
        inside[word] |= (hi == 31 ? 0xffffffffu : (1u << uint(hi + 1)) - 1u) & ~((1u << uint(lo)) - 1u);
    }
}

void main() {
    int index = int(gl_GlobalInvocationID.x);
    if (index >= sky_grid_size * sky_grid_size) {
        return;
    }
    ivec2 column = ivec2(index % sky_grid_size, index / sky_grid_size);
    uint up[sky_grid_words];
    uint down[sky_grid_words];
    for (int word = 0; word < sky_grid_words; word++) {
        marked[word] = imageLoad(cg_water_marks_img, sky_grid_texel(column, word, 0)).r;
        up[word] = imageLoad(cg_water_marks_img, sky_grid_face_texel(column, word, true)).r;
        down[word] = imageLoad(cg_water_marks_img, sky_grid_face_texel(column, word, false)).r;
        inside[word] = 0u;
    }

    bool ground = false; // whether the blocks under the last face up are the ground's
    int open_top = -1;   // the highest unmarked block under it, while it isn't yet known to be inside the ground
    for (int word = sky_grid_words - 1; word >= 0; word--) {
        if (marked[word] == 0u) {
            if (ground && open_top < 0) {
                open_top = word * 32 + 31;
            }
            continue;
        }
        for (int i = 31; i >= 0; i--) {
            uint bit = 1u << uint(i);
            if ((marked[word] & bit) == 0u) {
                if (ground && open_top < 0) {
                    open_top = word * 32 + i;
                }
            } else if ((up[word] & bit) != 0u) {
                // Air over this block, so over it was air too; the ground goes on under it unless it has air under
                // it as well.
                open_top = -1;
                ground = (down[word] & bit) == 0u;
            } else if ((down[word] & bit) != 0u) {
                // A roof over air: what lay over it was inside the ground.
                if (open_top >= 0) {
                    fill_inside(word * 32 + i + 1, open_top);
                }
                open_top = -1;
                ground = false;
            }
            // (A block with faces only to the sides, a cave's wall beside the column, goes on as it was.)
        }
    }
    if (open_top >= 0) {
        fill_inside(0, open_top);
    }

    for (int word = 0; word < sky_grid_words; word++) {
        if (inside[word] != 0u) {
            imageStore(cg_water_marks_img, sky_grid_texel(column, word, 0), uvec4(marked[word] | inside[word], 0u, 0u, 0u));
        }
    }
}

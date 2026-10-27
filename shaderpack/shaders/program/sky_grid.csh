// The overworld's shadowcomp passes 6 and 7: the sky grid's coarser copies (see sky_grid.glsl), each column of a copy
// marking every block any of the 2 by 2 columns under it in the one before has. Pass 6 makes the 2, 4 and 8 block
// copies from the grid, pass 7 the 16, 32 and 64 block ones from the 8 block one (SKY_GRID_PASS: which of them this
// is), so no column reads more than 64 others.

#define PROGRAM_SKY_GRID
#include "/lib/sky_grid.glsl"

layout(local_size_x = 64) in;
#if SKY_GRID_PASS == 0
const ivec3 workGroups = ivec3(2688, 1, 1); // 8 words of 128², 64² and 32² columns
#else
const ivec3 workGroups = ivec3(42, 1, 1);   // 8 words of 16², 8² and 4² columns
#endif

layout(r32ui) uniform uimage2D cg_water_marks_img;

void main() {
    int index = int(gl_GlobalInvocationID.x);
    int from = SKY_GRID_PASS * 3;
    int level = from + 1;
    for (; level <= from + 3; level++) {
        int size = sky_grid_size >> level;
        int count = size * size * sky_grid_words;
        if (index < count) {
            break;
        }
        index -= count;
    }
    if (level > from + 3) {
        return;
    }
    int size = sky_grid_size >> level;
    int word = index / (size * size);
    ivec2 column = ivec2(index % size, (index / size) % size);
    int span = 1 << (level - from);
    uint bits = 0u;
    for (int z = 0; z < span; z++) {
        for (int x = 0; x < span; x++) {
            bits |= imageLoad(cg_water_marks_img, sky_grid_texel(column * span + ivec2(x, z), word, from)).r;
        }
    }
    imageStore(cg_water_marks_img, sky_grid_texel(column, word, level), uvec4(bits, 0u, 0u, 0u));
}

// The block grid the Create Galore Bridge keeps for the pack (see the Bridge's WorldGrid and BlockPalette): every block
// in a box around the camera, fixed to the world, read from the game's own block data as chunks load and blocks change,
// never from what the camera sees. Two images the pack declares and the Bridge fills before the frame's first pass:
// - cg_world_tex, one word per block, in the block's slot (its world position modulo the grid's size): bits 0..15 its
//   entry in the palette, 16..19 its sky light (0..15), 20..27 which place the slot holds (x and z divided by the
//   grid's size, 3 bits each, then y in 2), bit 31 filled;
// - cg_palette_tex, 16 words per block state: 0 the pack's number from block.properties (bits 0..15, 0xffff none),
//   the light Minecraft says it gives (16..19) and facts about it (20 and up); 1 its colour as drawn (sRGB, alpha the
//   share of its texture that shows); 2 its glow colour (its brightest texels, sRGB); 3 where in its block it glows
//   (x, y, z in 32nds, 6 bits each) and how many boxes its shape has (24..27); 4..11 its shape, a box a word (low
//   corner x, y, z, then high corner, in 16ths, 5 bits each).
// Air's word 15 holds grid_mark: without it there is no Bridge, and the pack falls back to Minecraft's own light.
//
// program/world.csh turns the grid into the voxel copy's words each frame (lib/voxel.glsl); everything else reads
// those, and the palette for the shapes of part blocks.

#if !defined LIB_WORLD
#define LIB_WORLD

#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform usampler3D cg_world_tex;
uniform usampler2D cg_palette_tex;

const uint grid_mark = 0x43474731u;

#define PAL_WATER (1u << 20u)
#define PAL_TRANSLUCENT (1u << 21u)
#define PAL_MOSTLY_CLEAR (1u << 22u)
#define PAL_NO_COLLISION (1u << 23u)
#define PAL_FULL_CUBE (1u << 24u)
#define PAL_HAS_SHAPE (1u << 25u)
#define PAL_LAVA (1u << 26u)
#define PAL_FLUID (1u << 27u)

int imod(int a, int n) {
    int r = a - n * (a / n);
    return r < 0 ? r + n : r;
}

ivec3 imod3(ivec3 a, ivec3 n) {
    return ivec3(imod(a.x, n.x), imod(a.y, n.y), imod(a.z, n.z));
}

uint palette_word(uint entry, uint w) {
    return texelFetch(cg_palette_tex, ivec2(int((entry & 255u) * 16u + w), int(entry >> 8u)), 0).r;
}

// Whether the Bridge keeps the grid.
bool grid_live() {
    return palette_word(0u, 15u) == grid_mark;
}

int floor_div(int a, int n) {
    return a >= 0 ? a / n : -((n - 1 - a) / n);
}

// The grid's word for a world block: 0 (nothing known) when its slot is still empty or holds another place.
uint grid_word(ivec3 world_block) {
    ivec3 size = ivec3(VOX_X, VOX_Y, VOX_X);
    ivec3 slot = ivec3(imod(world_block.x, size.x), imod(world_block.y, size.y), imod(world_block.z, size.z));
    uint w = texelFetch(cg_world_tex, slot, 0).r;
    uint tag = (uint(floor_div(world_block.x, size.x) & 7) << 20u) | (uint(floor_div(world_block.z, size.z) & 7) << 23u)
             | (uint(floor_div(world_block.y, size.y) & 3) << 26u);
    return (w & 0x80000000u) != 0u && (w & 0x0ff00000u) == tag ? w : 0u;
}

// One box of a palette entry's shape, in blocks (0..1 within its block), rounded to 8ths: a step of a 16th (a path
// against grass, a carpet's lip) never casts a shadow.
void palette_box(uint entry, uint i, out vec3 lo, out vec3 hi) {
    uint b = palette_word(entry, 4u + i);
    uvec3 l = uvec3(b, b >> 5u, b >> 10u) & 31u;
    uvec3 h = uvec3(b >> 15u, b >> 20u, b >> 25u) & 31u;
    lo = floor(vec3(l) * 0.5 + 0.5) / 8.0;
    hi = floor(vec3(h) * 0.5 + 0.5) / 8.0;
}

uint palette_box_count(uint entry) {
    return min((palette_word(entry, 3u) >> 24u) & 15u, 8u);
}

#endif

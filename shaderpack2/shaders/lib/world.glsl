// The block grid the Create Galore Bridge keeps for the pack (see the Bridge's WorldGrid and BlockPalette): every block
// in a box around the camera, fixed to the world, read from the game's own block data as chunks load and blocks change,
// never from what the camera sees. Two images the pack declares and the Bridge fills before the frame's first pass:
// - cg_world_tex, one word per block, in the block's slot (its world position modulo the grid's size): bits 0..15 its
//   entry in the palette, 16..19 its sky light (0..15), 20..27 which place the slot holds (x and z divided by the
//   grid's size, 3 bits each, then y in 2), bit 31 filled;
// - cg_palette_tex, 16 words per block state: 0 the pack's number from block.properties (bits 0..15, 0xffff none),
//   the light Minecraft says it gives (16..19) and facts about it (20 and up); 1 its colour as drawn (sRGB, alpha the
//   share of its texture that shows); 2 its glow colour (its brightest texels, sRGB); 3 where in its block it glows
//   (x, y, z in 32nds, 6 bits each) and how many boxes its shape has (24..27); 4..15 its shape as drawn, a box a word
//   (low corner x, y, z, then high corner, in 16ths, 5 bits each).
// Air's word 15 holds grid_mark: without it there is no Bridge, and the pack falls back to Minecraft's own light.
// The palette image's rows after its own 256 hold the holes in block states' cut-out faces (48 rows, see hole_word),
// then where each state's faces find their texture in the block atlas (512 rows, see face_texture); the pack declares
// it 816 rows tall.
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
#define PAL_HOLES (1u << 28u) // its cut-out faces have clear pixels (see hole_word)

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

// One box of a palette entry's shape, in blocks (0..1 within its block), exactly as the Bridge gives it (16ths): a
// box the copy made bigger than the drawn one would hold the surface's own pixels, and light would get out through it.
void palette_box(uint entry, uint i, out vec3 lo, out vec3 hi) {
    uint b = palette_word(entry, 4u + i);
    uvec3 l = uvec3(b, b >> 5u, b >> 10u) & 31u;
    uvec3 h = uvec3(b >> 15u, b >> 20u, b >> 25u) & 31u;
    lo = vec3(l) / 16.0;
    hi = vec3(h) / 16.0;
}

uint palette_box_count(uint entry) {
    return min((palette_word(entry, 3u) >> 24u) & 15u, 12u);
}

// The holes in block states' cut-out faces (the Bridge's BlockHoles), for light to get through them: a trapdoor's
// windows, a grate's slots, a ladder's gaps. 48 rows of words after the palette's, 4096 a row:
// - [0, 65536): per palette entry, the word where its table starts (0: no holes); air's word holds hole_mark when the
//   Bridge writes them (a Bridge that doesn't leaves no holes anywhere);
// - [65536, 131072): the tables, two words per box of the entry's shape (in its order): its masks across x (bits
//   0..15) and y (16..31), then across z (0..15);
// - [131072, 196608): the masks, 8 words each, for both faces of a box across an axis: 16 x 16 cells in 16ths of the
//   block, open where every face drawn across that axis in the box shows a clear texel there. Of the two axes along
//   the faces, a is the one after the faces' own (y for x, z for y, x for z) and b the one after that: cell (i, j) is
//   bit i of row j, rows two a word (the even one in bits 0..15). Mask 0 is a closed face.
const uint hole_mark = 0x484f4c31u;

uint hole_word(uint i) {
    return texelFetch(cg_palette_tex, ivec2(int(i & 4095u), 256 + int(i >> 12u)), 0).r;
}

bool holes_live() {
    return hole_word(0u) == hole_mark;
}

// Where an entry's table starts; 0 when it has no holes.
uint hole_table(uint entry) {
    uint t = entry == 0u ? 0u : hole_word(entry);
    return t >= 65536u && t < 131072u ? t : 0u;
}

// Box i's masks across x, y and z.
uvec3 hole_masks(uint table, uint i) {
    uint w0 = hole_word(table + i * 2u);
    uint w1 = hole_word(table + i * 2u + 1u);
    return uvec3(w0 & 0xffffu, w0 >> 16u, w1 & 0xffffu);
}

// Row j (0..15) of a mask: bit i set where cell i is open.
uint hole_row(uint mask, int j) {
    uint w = hole_word(131072u + mask * 8u + uint(j >> 1));
    return (w >> uint((j & 1) * 16)) & 0xffffu;
}

// Where block states' faces find their texture in the block atlas (the Bridge's BlockFaces), for drawing the copy's
// blocks with their real textures (reflections): 512 rows after the holes', 32 words per palette entry. For each side
// in Minecraft's order (0 down, 1 up, 2 north -z, 3 south +z, 4 west -x, 5 east +x) five words: its sprite's low and high
// corner in the atlas (u in bits 0..15, v in 16..31, in 65536ths), then how a point on the side finds its texel in
// the sprite, s = s0 + s1 a + s2 b and t = t0 + t1 a + t2 b, with a and b the point's place in the block along the
// side's two axes (as hole_word's) and the six numbers as half floats, two a word. Word 30: which sides have a texture
// (bits 0..5) and which are tinted (8..13); word 31 the tint (sRGB, red low), and in air's entry faces_mark.
uniform sampler2D cg_atlas;

const uint faces_mark = 0x55565331u;

uint face_word(uint entry, uint w) {
    uint i = entry * 32u + w;
    return texelFetch(cg_palette_tex, ivec2(int(i & 4095u), 304 + int(i >> 12u)), 0).r;
}

bool faces_live() {
    return face_word(0u, 31u) == faces_mark;
}

// The side a face with outward normal n is, in Minecraft's order.
int face_side(vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) {
        return n.y < 0.0 ? 0 : 1;
    }
    if (a.z >= a.x) {
        return n.z < 0.0 ? 2 : 3;
    }
    return n.x < 0.0 ? 4 : 5;
}

// A block's texture where a point (local: 0..1 in its block) on its side with outward normal n is, in linear colour
// with the share that shows in alpha, as seen across footprint blocks per pixel (its mip level follows from that and the
// texture's size); false when the Bridge gives that side no texture (the block's average colour stands in).
bool face_texture(uint entry, vec3 n, vec3 local, float footprint, out vec4 color) {
    color = vec4(0.0);
    uint flags = face_word(entry, 30u);
    int side = face_side(n);
    if ((flags & (1u << uint(side))) == 0u) {
        return false;
    }
    uint base = uint(side) * 5u;
    uint w0 = face_word(entry, base);
    uint w1 = face_word(entry, base + 1u);
    vec2 s01 = unpackHalf2x16(face_word(entry, base + 2u));
    vec2 s2t0 = unpackHalf2x16(face_word(entry, base + 3u));
    vec2 t12 = unpackHalf2x16(face_word(entry, base + 4u));
    int axis = side < 2 ? 1 : side < 4 ? 2 : 0;
    float a = local[(axis + 1) % 3];
    float b = local[(axis + 2) % 3];
    vec2 st = vec2(s01.x + s01.y * a + s2t0.x * b, s2t0.y + t12.x * a + t12.y * b);
    vec2 lo = vec2(uvec2(w0 & 0xffffu, w0 >> 16u)) / 65536.0;
    vec2 hi = vec2(uvec2(w1 & 0xffffu, w1 >> 16u)) / 65536.0;
    // Kept half a texel inside the sprite, so its neighbours in the atlas never bleed in.
    vec2 texels = max(abs(hi - lo) * vec2(textureSize(cg_atlas, 0)), vec2(1.0));
    st = clamp(st, 0.5 / texels, 1.0 - 0.5 / texels);
    float per_block = max(texels.x * length(vec2(s01.y, s2t0.x)), texels.y * length(t12));
    float lod = max(log2(max(footprint * per_block, 1e-4)), 0.0);
    color = textureLod(cg_atlas, mix(lo, hi, st), lod);
    color.rgb = srgb_to_linear(color.rgb);
    if ((flags & (1u << uint(8 + side))) != 0u) {
        uint tint = face_word(entry, 31u);
        color.rgb *= srgb_to_linear(vec3(uvec3(tint, tint >> 8u, tint >> 16u) & 255u) / 255.0);
    }
    return true;
}

#endif

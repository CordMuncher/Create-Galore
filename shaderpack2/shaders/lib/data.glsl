// The pack's tables of words, in one image (cg_data_img: 4096 words a row, 1880 rows), each 8-block tile's in its tile
// slot (the tile's place in the world modulo the box's tiles, see lib/lights.glsl), enough for the biggest box:
// - rows 0..127: the light hash (lib/voxel.glsl), 65536 slots of eight words;
// - rows 128..647: each tile's light list (lib/lights.glsl), 129 words each;
// - rows 648..779: the lights inside each tile (program/world.csh), 33 words each;
// - rows 780..1551: each tile's lights as records the per-pixel light reads (program/voxel_light.csh), 193 words each;
// - rows 1552..1871: the wakes' ripples (lib/wakes.glsl), two copies of their state and then their slope;
// - rows 1872..1879: each tile's frame stamps (lib/lights.glsl): the frame its blocks last changed (program/world.csh),
//   then the frame its block light was last worked out (program/voxel_light.csh); then a mark that the image has been
//   written before and the frame it was found fresh (program/clear.csh);
// - the last word of the tiles' light list rows, past the biggest box's lists: the bad frame record (see
//   lib/bad_frame.glsl).
//
// An image rather than storage buffers: Iris gives storage buffers only binding points 0 to 8, which Flywheel's
// indirect renderer (Create's machines through Colorwheel) also binds every frame, while Iris binds the pack's only
// once.

#if !defined LIB_DATA
#define LIB_DATA

layout(r32ui) uniform uimage2D cg_data_img;

const uint data_hash_base = 0u;
const uint data_tile_lists_base = 128u * 4096u;
const uint data_tile_lists_size = 520u * 4096u;
const uint data_own_lists_base = 648u * 4096u;
const uint data_records_base = 780u * 4096u;
const uint data_bad_frame = data_tile_lists_base + data_tile_lists_size - 1u;
const uint data_stamps_base = 1872u * 4096u;
const uint data_stamps_tiles = 16384u; // the biggest box's tiles
const uint data_fresh_mark = data_stamps_base + 2u * data_stamps_tiles;
const uint data_fresh_frame = data_fresh_mark + 1u;
const uint data_fresh_magic = 0x5ec7012bu;

// How many frames ago a frame stamp was taken, this frame being frame (Iris counts frames modulo 720720); a stamp
// that can't be one (fresh image memory) counts as that long ago.
int frames_since(uint stamp, int frame) {
    return stamp >= 720720u ? 720720 : (frame - int(stamp) + 720720) % 720720;
}

ivec2 data_texel(uint i) {
    return ivec2(int(i & 4095u), int(i >> 12u));
}

uint data_load(uint i) {
    return imageLoad(cg_data_img, data_texel(i)).r;
}

void data_store(uint i, uint v) {
    imageStore(cg_data_img, data_texel(i), uvec4(v));
}

uint data_add(uint i, uint v) {
    return imageAtomicAdd(cg_data_img, data_texel(i), v);
}

uint data_or(uint i, uint v) {
    return imageAtomicOr(cg_data_img, data_texel(i), v);
}

uint data_max(uint i, uint v) {
    return imageAtomicMax(cg_data_img, data_texel(i), v);
}

uint data_swap_if(uint i, uint compare, uint v) {
    return imageAtomicCompSwap(cg_data_img, data_texel(i), compare, v);
}

#endif

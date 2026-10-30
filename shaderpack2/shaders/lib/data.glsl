// The pack's tables of words, in one image (cg_data_img: 4096 words a row, 1552 rows), each 8-block tile's in its tile
// slot (the tile's place in the world modulo the box's tiles, see lib/lights.glsl), enough for the biggest box:
// - rows 0..127: the light hash (lib/voxel.glsl), 65536 slots of eight words;
// - rows 128..647: each tile's light list (lib/lights.glsl), 129 words each;
// - rows 648..779: the lights inside each tile (program/world.csh), 33 words each;
// - rows 780..1551: each tile's lights as records the per-pixel light reads (program/voxel_light.csh), 193 words each;
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

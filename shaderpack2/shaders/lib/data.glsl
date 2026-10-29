// The pack's tables of words, in one image (cg_data_img: 4096 words a row, 1688 rows):
// - rows 0..127: the light hash (lib/voxel.glsl), 65536 slots of eight words;
// - rows 128..647: each 8-block tile's light list (lib/lights.glsl), 129 words each;
// - rows 648..1687: each screen tile's light list (program/pixel_light.csh), 65 words each.
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
const uint data_screen_lists_base = 648u * 4096u;
const uint data_screen_lists_size = 1040u * 4096u;

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

uint data_max(uint i, uint v) {
    return imageAtomicMax(cg_data_img, data_texel(i), v);
}

uint data_swap_if(uint i, uint compare, uint v) {
    return imageAtomicCompSwap(cg_data_img, data_texel(i), compare, v);
}

#endif

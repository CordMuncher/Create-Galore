// The voxel copy of the world around the camera (see program/shadow.gsh, which makes it each frame).
//
// A box of VOX_X x VOX_Y x VOX_X cells, at VOXEL_LEVELS levels of detail: level 0 has a cell per block, each level
// after it covers half the width at twice the detail (level 5: 32 cells per block, 2 blocks around you). All levels
// share one 32-bit word per cell slot, a bit each; a slot holds a different place at each level. The box moves with
// the camera a block at a time, and the slots wrap around (a place keeps its slot while it stays in the box), so the
// tables kept from frame to frame (the distance field, the light) never have to be moved.
//
// Each word: bits 0..7 solid at that level, 8..15 see-through (glass, water) at that level; at level 0 also bit 16
// water, bit 17 a light, bits 18..19 the sky light the block's faces see (a Gray code: OR-ing two keeps a value
// between them), and from bit 20 up how many colour samples the block's colour sums hold. The words are the first
// third of cg_vox_img along x; the second third holds the colour sums' red | green << 16 and the last blue |
// alpha << 16 (sums of 0..255), slot for slot.

#if !defined LIB_VOXEL
#define LIB_VOXEL

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"

#define VOX_WATER (1u << 16u)
#define VOX_LIGHT (1u << 17u)
#define VOX_SKY_SHIFT 18u
#define VOX_COUNT_SHIFT 20u

const ivec3 vox_half = vox_size / 2;
const ivec3 vox_rg_offset = ivec3(VOX_X, 0, 0);
const ivec3 vox_ba_offset = ivec3(2 * VOX_X, 0, 0);

uint vox_solid_bit(int level) {
    return 1u << uint(level);
}

uint vox_see_bit(int level) {
    return 1u << uint(level + 8);
}

int imod(int a, int n) {
    int r = a - n * (a / n);
    return r < 0 ? r + n : r;
}

ivec3 imod3(ivec3 a, ivec3 n) {
    return ivec3(imod(a.x, n.x), imod(a.y, n.y), imod(a.z, n.z));
}

// A point in scene space (camera-relative) as blocks from the corner of the camera's block.
vec3 vox_pos(vec3 scene_pos) {
    return scene_pos + cameraPositionFract;
}

// The cell a point (from vox_pos) is in at a level, counted from the box's middle.
ivec3 vox_cell(vec3 p, int level) {
    return ivec3(floor(p * float(1 << level)));
}

bool vox_in_box(ivec3 cell) {
    return all(greaterThanEqual(cell, -vox_half)) && all(lessThan(cell, vox_half));
}

// The slot of a cell (counted from the box's middle) at a level, for this frame's camera.
ivec3 vox_slot(ivec3 cell, int level) {
    ivec3 base = imod3(cameraPositionInt * (1 << level), vox_size);
    return (base + cell + vox_size) % vox_size;
}

// The world cell a slot holds this frame at a level, counted from the box's middle (the inverse of vox_slot).
ivec3 vox_slot_cell(ivec3 slot, int level) {
    ivec3 base = imod3(cameraPositionInt * (1 << level), vox_size);
    return (slot - base + vox_size + vox_half) % vox_size - vox_half;
}

// Whether a cell (counted from this frame's box middle) was in last frame's box too.
bool vox_was_in_box(ivec3 cell, int level) {
    ivec3 moved = (cameraPositionInt - previousCameraPositionInt) * (1 << level);
    return vox_in_box(cell + moved);
}

// The finest level whose box holds a point, keeping a cell clear of the box's edge; -1 outside them all.
int vox_level(vec3 p) {
    for (int level = VOXEL_LEVELS - 1; level >= 0; level--) {
        ivec3 cell = vox_cell(p, level);
        if (all(greaterThanEqual(cell, -vox_half + 1)) && all(lessThan(cell, vox_half - 1))) {
            return level;
        }
    }
    return -1;
}

// The light hash (in cg_data_img, see lib/data.glsl): what the shadow pass found out about each block that holds a light, in 65536
// slots of eight words, found by the block's key (up to four tries from where the key hashes to). Words: 0 the key
// plus 1 (0: free), 1 the light's place in its block as x and y << 16 (sums, 1/32 block steps), 2 z and the count of
// samples << 16, 3 red and green << 16 (sums, 1/32 steps), 4 blue, 5 its level << 8 and its kind + 1 (the largest
// any sample gave). Cleared at the start of each frame (program/clear.csh).
const uint light_hash_slots = 65536u;
const uint light_hash_tries = 4u;
const uint light_none = 0xffffffffu;

#include "/lib/data.glsl"

// A block's place as 29 bits: x and z wrapped to 1024 blocks, y from -64 up in 9 bits. Light lists keep lights
// this way, and find the block again from the camera's.
uint light_key(ivec3 block) {
    return uint(block.x & 1023) | (uint(block.z & 1023) << 10u) | (uint((block.y + 64) & 511) << 20u);
}

// The block a key means, near the camera (world block coordinates).
ivec3 light_key_block(uint key) {
    ivec3 wrapped = ivec3(int(key & 1023u), int((key >> 20u) & 511u) - 64, int((key >> 10u) & 1023u));
    ivec3 cam = cameraPositionInt;
    ivec3 d = wrapped - ivec3(cam.x & 1023, 0, cam.z & 1023);
    d.x = ((d.x + 512) & 1023) - 512;
    d.z = ((d.z + 512) & 1023) - 512;
    return ivec3(cam.x + d.x, wrapped.y, cam.z + d.z);
}

uint light_hash_start(uint key) {
    return pcg3d(uvec3(key, key >> 11u, 77u)).x;
}

// The first word of a key's slot, taking a free one for it if it has none yet; light_none when all its tries are
// taken by other keys.
uint light_claim(uint key) {
    uint start = light_hash_start(key);
    for (uint i = 0u; i < light_hash_tries; i++) {
        uint base = data_hash_base + ((start + i) & (light_hash_slots - 1u)) * 8u;
        uint was = data_swap_if(base, 0u, key + 1u);
        if (was == 0u || was == key + 1u) {
            return base;
        }
    }
    return light_none;
}

uint light_find(uint key) {
    uint start = light_hash_start(key);
    for (uint i = 0u; i < light_hash_tries; i++) {
        uint base = data_hash_base + ((start + i) & (light_hash_slots - 1u)) * 8u;
        uint k = data_load(base);
        if (k == key + 1u) {
            return base;
        }
        if (k == 0u) {
            break;
        }
    }
    return light_none;
}

struct Light {
    vec3 pos;    // scene space
    vec3 color;  // times its brightness
    float level; // 0..31
    float reach; // blocks
    int kind;    // -1 not on the list
    bool valid;
};

#endif

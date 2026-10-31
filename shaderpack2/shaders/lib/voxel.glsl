// The voxel copy of the world around the camera, one cell per block, made each frame by program/world.csh from the
// Create Galore Bridge's block grid (see lib/world.glsl).
//
// A box of VOX_X x VOX_Y x VOX_X cells fixed to the world: its low corner sits on a multiple of 8 blocks, as near to
// centred on the camera's block as that allows, so its 8-block tiles are the world's own and the box moves 8 blocks
// at a time. A block's cell (its slot) is its position modulo the box's size, so a place keeps its slot while it stays
// in the box, and the tables kept from frame to frame never move. What a block holds comes from the game itself (the
// Bridge reads each block's shape and light), never from what the camera happens to see, so a ray through the copy
// gives the same answer from wherever it is looked at.
//
// Each word: bits 0..15 the block's palette entry (its colour and shape, lib/world.glsl); bit 16 something in it stops
// light (VOX_SOLID); 17 see-through (stained glass, water, ice: tints the light); 18 water; 19 a light; 20..23 the sky
// light in the block (0..15); 24 a part block (a slab, a path, a fence post: VOX_SOLID only where its shape's boxes
// are); 25 leaves (they let some light through); 26 known (the Bridge has read it); 27 a part block whose faces have
// see-through pixels (a trapdoor's windows, a grate's slots: the Bridge's hole masks, lib/world.glsl), where light gets
// through.

#if !defined LIB_VOXEL
#define LIB_VOXEL

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"
#include "/lib/world.glsl"

#define VOX_ENTRY 0xffffu
#define VOX_SOLID (1u << 16u)
#define VOX_SEE (1u << 17u)
#define VOX_WATER (1u << 18u)
#define VOX_LIGHT (1u << 19u)
#define VOX_SKY_SHIFT 20u
#define VOX_PART (1u << 24u)
#define VOX_LEAVES (1u << 25u)
#define VOX_KNOWN (1u << 26u)
#define VOX_HOLES (1u << 27u)

const ivec3 vox_half = vox_size / 2;

// A cell that stops light everywhere in it: a full block (not a part block, not leaves).
bool vox_full(uint word) {
    return (word & (VOX_SOLID | VOX_PART | VOX_LEAVES)) == VOX_SOLID;
}

// (The level parameters below are left over from the copy's six detail levels: there is only level 0 now.)
uint vox_solid_bit(int level) {
    return VOX_SOLID;
}

uint vox_see_bit(int level) {
    return VOX_SEE;
}

// A point in scene space (camera-relative) as blocks from the corner of the camera's block.
vec3 vox_pos(vec3 scene_pos) {
    return scene_pos + cameraPositionFract;
}

// The block a point (from vox_pos) is in, counted from the camera's block.
ivec3 vox_cell(vec3 p, int level) {
    return ivec3(floor(p));
}

// The box's low corner in world blocks for a camera block: on a multiple of 8, as near as that allows to half the box
// below the camera's block. The Bridge places its grid the same way.
ivec3 vox_box_world_lo(ivec3 camera_block) {
    return ((camera_block - vox_half) >> 3) << 3;
}

// The box's low corner, counted from the camera's block (this frame's).
ivec3 vox_box_lo() {
    return vox_box_world_lo(cameraPositionInt) - cameraPositionInt;
}

// The box's middle (vox_pos space).
vec3 vox_box_middle() {
    return vec3(vox_box_lo() + vox_half);
}

bool vox_in_box(ivec3 cell, int level) {
    ivec3 c = cell - vox_box_lo();
    return all(greaterThanEqual(c, ivec3(0))) && all(lessThan(c, vox_size));
}

// The slot of a cell (the slots wrap around the world, so each place keeps its own).
ivec3 vox_slot(ivec3 cell, int level) {
    return imod3(cameraPositionInt + cell, vox_size);
}

// Whether a cell (of this frame) was in last frame's box too.
bool vox_was_in_box(ivec3 cell, int level) {
    ivec3 world = cameraPositionInt + cell;
    ivec3 c = world - vox_box_world_lo(previousCameraPositionInt);
    return all(greaterThanEqual(c, ivec3(0))) && all(lessThan(c, vox_size));
}

// How far a cell is from the box's edge, in cells (0 on the outermost cells).
int vox_edge_distance(ivec3 cell, int level) {
    ivec3 c = cell - vox_box_lo();
    ivec3 d = min(c, vox_size - 1 - c);
    return min(d.x, min(d.y, d.z));
}

// How far a tile's middle (vox_pos space) is from the camera, or from where the camera will be in about a second at
// its speed, whichever is nearer: the tiles you are heading for refresh as often as the ones around you, so the
// light ahead is up to date when you get there.
float vox_tile_distance(vec3 centre) {
    vec3 moved = vec3(cameraPositionInt - previousCameraPositionInt) + (cameraPositionFract - previousCameraPositionFract);
    vec3 ahead = moved * 60.0;
    float far = length(ahead);
    if (far > 32.0) {
        ahead *= 32.0 / far; // a teleport, not a walk
    }
    return min(length(centre), length(centre - ahead));
}

// 0 inside the box (a cell clear of its edge), -1 outside.
int vox_level(vec3 p) {
    return vox_edge_distance(ivec3(floor(p)), 0) >= 1 ? 0 : -1;
}

// The light hash (in cg_data_img, see lib/data.glsl): what program/world.csh found out about each block that holds a light, in 65536
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
    uint group;  // how many blocks it stands for (a patch of lava, see program/world.csh), else 1
    bool valid;
};

#endif

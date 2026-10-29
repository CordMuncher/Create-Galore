// The lights the voxel copy found (see the light hash in lib/voxel.glsl), and the short lists of them that each
// tile of blocks (program/voxel_light.csh) and each tile of the screen (program/pixel_light.csh) keeps.
//
// There is no list of every light. A tile starts from the lights inside it, the ones that lit it last time and the
// ones that lit its neighbours; keeps those that still reach it, brightest first; and after lighting, keeps the ones
// that did light it for next time. So a lantern found in one tile becomes known to the tiles around it, then to
// theirs, as far as it still matters, and lights that stop mattering drop out by themselves.

#if !defined LIB_LIGHTS
#define LIB_LIGHTS

#include "/lib/voxel.glsl"
#include "/lib/blocks.glsl"

// Each block tile's list (in cg_data_img, see lib/data.glsl): its count, then up to tile_list_max keys.
const uint tile_list_max = 128u;
const uint tile_list_stride = tile_list_max + 1u;

uint block_tile_index(ivec3 tile) {
    return data_tile_lists_base + uint(tile.x + vox_tiles.x * (tile.y + vox_tiles.y * tile.z)) * tile_list_stride;
}

// The block tile a point is in (vox_pos space), counted from the box's corner; false outside the box.
bool block_tile_of(vec3 p, out ivec3 tile) {
    ivec3 cell = ivec3(floor(p)) + vox_half;
    tile = cell >> 3;
    return all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, vox_size));
}

// The light whose key this is, as this frame's shadow pass found it; valid false when it is gone. Its place is in
// vox_pos space (blocks from the corner of the camera's block).
Light light_from_key(uint key) {
    Light l;
    l.valid = false;
    l.kind = -1;
    uint base = light_find(key);
    if (base == light_none) {
        return l;
    }
    uint count = data_load(base + 2u) >> 16u;
    if (count == 0u) {
        return l;
    }
    float n = 32.0 * float(count);
    uint xy = data_load(base + 1u);
    uint zc = data_load(base + 2u);
    uint rg = data_load(base + 3u);
    vec3 local = vec3(float(xy & 0xffffu), float(xy >> 16u), float(zc & 0xffffu)) / n;
    vec3 color = vec3(float(rg & 0xffffu), float(rg >> 16u), float(data_load(base + 4u))) / n;
    uint level_kind = data_load(base + 5u);
    l.pos = vec3(light_key_block(key) - cameraPositionInt) + local;
    l.level = float(level_kind >> 8u);
    l.kind = int(level_kind & 255u) - 1;
    l.color = color * light_brightness(l.level);
    l.reach = light_reach(l.level);
    l.valid = l.level > 0.0;
    return l;
}

// A light's rough strength over a box (vox_pos space): its brightness at the box's nearest point, 0 out of reach.
float light_score(Light l, vec3 lo, vec3 hi) {
    vec3 nearest = clamp(l.pos, lo, hi);
    float d = distance(l.pos, nearest);
    return d >= l.reach ? 0.0 : max3(l.color) * light_falloff(d, l.reach);
}

#endif

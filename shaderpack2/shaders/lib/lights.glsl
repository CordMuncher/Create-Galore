// The lights the voxel copy holds (see the light hash in lib/voxel.glsl), and the short lists of them that each 8-block
// tile of the world keeps (program/voxel_light.csh).
//
// Every tile knows the lights inside it (program/world.csh lists them each frame). A tile's list starts from those of
// the tiles around it as far as a light can reach, plus the ones that lit it and its neighbours last time; keeps those
// that still reach it, brightest first; and after lighting keeps the ones that did light it. The per-pixel light
// (program/pixel_light.csh) takes the list of the tile its surface is in, as records ready to use. So which lights a
// spot gets depends on where the spot is, never on where the camera looks from.
//
// Each tile's tables sit in its tile slot: its place in the world (in tiles) modulo the box's tiles, so they stay
// with the tile while it stays in the box.

#if !defined LIB_LIGHTS
#define LIB_LIGHTS

#include "/lib/voxel.glsl"
#include "/lib/blocks.glsl"

// Each block tile's list (in cg_data_img, see lib/data.glsl): its count, then up to tile_list_max keys.
const uint tile_list_max = 128u;
const uint tile_list_stride = tile_list_max + 1u;

// The lights inside each tile: their count, then up to own_list_max keys.
const uint own_list_max = 32u;
const uint own_list_stride = own_list_max + 1u;

// Each tile's records: their count, then up to record_max lights of three words (see store_record).
const uint record_max = 64u;
const uint record_stride = 3u * record_max + 1u;

// A tile (counted from the box's corner) as its slot's number.
uint tile_slot_index(ivec3 tile) {
    ivec3 slot = imod3((vox_box_world_lo(cameraPositionInt) >> 3) + tile, vox_tiles);
    return uint(slot.x + vox_tiles.x * (slot.y + vox_tiles.y * slot.z));
}

uint block_tile_index(ivec3 tile) {
    return data_tile_lists_base + tile_slot_index(tile) * tile_list_stride;
}

uint own_list_index(ivec3 tile) {
    return data_own_lists_base + tile_slot_index(tile) * own_list_stride;
}

uint record_index(ivec3 tile) {
    return data_records_base + tile_slot_index(tile) * record_stride;
}

bool tile_in_box(ivec3 tile) {
    return all(greaterThanEqual(tile, ivec3(0))) && all(lessThan(tile, vox_tiles));
}

// The tile a point is in (vox_pos space), counted from the box's corner; false outside the box.
bool block_tile_of(vec3 p, out ivec3 tile) {
    ivec3 cell = ivec3(floor(p)) - vox_box_lo();
    tile = cell >> 3;
    return all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, vox_size));
}

// A patch of lava's light against one lava block's: as bright as all its blocks together, and reaching further.
float group_brightness(uint group) {
    return float(group);
}

float group_reach(uint group) {
    return sqrt(sqrt(float(group)));
}

// The light whose key this is, as this frame's program/world.csh found it; valid false when it is gone. Its place is in
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
    l.group = l.kind == KIND_LAVA ? min(count, 8u) : 1u;
    l.color = color * light_brightness(l.level) * group_brightness(l.group);
    l.reach = light_reach(l.level) * group_reach(l.group);
    l.valid = l.level > 0.0;
    return l;
}

// A light as a record: its key; its place (32nds from the corner of its key's block, 6 bits each: a lava patch's
// light can sit in the next block), the size of its soft shadows' cone (in 1/510 blocks, 8 bits) and its level
// (5 bits); its colour at full strength (10, 10 and 9 bits) and its group less one (3 bits).
void store_record(uint at, uint key, Light l, float radius) {
    vec3 home = vec3(light_key_block(key) - cameraPositionInt);
    uvec3 local = uvec3(clamp((l.pos - home) * 32.0, 0.0, 63.0) + 0.5);
    uint r = uint(clamp(radius * 510.0, 0.0, 255.0) + 0.5);
    uint level = uint(clamp(l.level, 0.0, 31.0) + 0.5);
    uint group = clamp(l.group, 1u, 8u);
    vec3 c = clamp(l.color / max(light_brightness(l.level) * group_brightness(group), 1e-6), 0.0, 1.0);
    uvec3 q = uvec3(c * vec3(1023.0, 1023.0, 511.0) + 0.5);
    data_store(at, key);
    data_store(at + 1u, local.x | (local.y << 6u) | (local.z << 12u) | (r << 18u) | (level << 26u));
    data_store(at + 2u, q.r | (q.g << 10u) | (q.b << 20u) | ((group - 1u) << 29u));
}

Light load_record(uint at, out float radius) {
    uint key = data_load(at);
    uint bits = data_load(at + 1u);
    uint q = data_load(at + 2u);
    Light l;
    vec3 local = vec3(uvec3(bits, bits >> 6u, bits >> 12u) & 63u) / 32.0;
    l.pos = vec3(light_key_block(key) - cameraPositionInt) + local;
    l.level = float((bits >> 26u) & 31u);
    l.group = (q >> 29u) + 1u;
    l.color = vec3(float(q & 1023u) / 1023.0, float((q >> 10u) & 1023u) / 1023.0, float((q >> 20u) & 511u) / 511.0)
            * light_brightness(l.level) * group_brightness(l.group);
    l.reach = light_reach(l.level) * group_reach(l.group);
    l.kind = -1;
    l.valid = l.level > 0.0;
    radius = float((bits >> 18u) & 255u) / 510.0;
    return l;
}

// A light's rough strength over a box (vox_pos space): its brightness at the box's nearest point, 0 out of reach.
float light_score(Light l, vec3 lo, vec3 hi) {
    vec3 nearest = clamp(l.pos, lo, hi);
    float d = distance(l.pos, nearest);
    return d >= l.reach ? 0.0 : max3(l.color) * light_falloff(d, l.reach);
}

#endif

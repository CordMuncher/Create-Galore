// The voxel copy of the world for this frame (lib/voxel.glsl), from the Create Galore Bridge's block grid
// (lib/world.glsl): one work group per 8 x 8 x 8 tile of the box. Each block's word says what it does to light, from
// what the game says it is:
// - a light (block.properties lists it, or Minecraft says it gives light 7 or more): into the light hash and its
//   tile's own list, at the place in its block where its texture glows most, in its listed colour or its texture's.
//   A light never stops light (it never shadows itself);
// - water and other fluids: see-through;
// - plants, and anything you walk through (torches, flowers, rails, signs): nothing;
// - flat layers (every box of its shape at most 2/16 tall, on the floor: carpets, snow layers, pressure plates,
//   barnacles): nothing, so they can't shade their block or trap the light of what lies on them;
// - mostly clear blocks drawn cut out (glass, panes, bars): nothing; drawn see-through (stained glass, ice): tints;
// - leaves: they stop some of the light;
// - anything else with a shape: solid, a part block when its shape isn't the whole block (its boxes do the stopping).

#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"
#include "/lib/blocks.glsl"
#include "/lib/lights.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(VOX_GROUPS_X, VOX_GROUPS_Y, VOX_GROUPS_X);

uniform float frameTimeCounter;

layout(r32ui) writeonly uniform uimage3D cg_vox_img;

shared uint s_live;
shared uint s_count;
shared uint s_keys[own_list_max];

// Every box of the shape at most 2/16 tall, standing on the floor.
bool flat_layer(uint entry) {
    uint n = palette_box_count(entry);
    if (n == 0u) {
        return false;
    }
    for (uint i = 0u; i < n; i++) {
        uint b = palette_word(entry, 4u + i);
        if (((b >> 5u) & 31u) != 0u || ((b >> 20u) & 31u) > 2u) {
            return false;
        }
    }
    return true;
}

// Adds a light to the light hash (lib/voxel.glsl) with one sample: its place in its block and its colour.
void add_light(ivec3 world, uint entry, int kind, float level) {
    uint base = light_claim(light_key(world));
    if (base == light_none) {
        return;
    }
    uint at = palette_word(entry, 3u);
    vec3 local = vec3(uvec3(at, at >> 6u, at >> 12u) & 63u) / 32.0;
    if (kind == 25) {
        // A nether portal's light drifts slowly about its block, as its swirl does.
        vec3 h = hash33(uvec3(world + 4096));
        float t = frameTimeCounter;
        local += 0.12 * vec3(sin(t * 0.9 + h.x * TAU), sin(t * 0.7 + h.y * TAU), sin(t * 0.8 + h.z * TAU));
    }
    local = clamp(local, 0.03, 0.97);
    vec4 listed = light_kind(kind);
    vec3 c;
    if (listed.r >= 0.0) {
        c = listed.rgb;
    } else {
        uint g = palette_word(entry, 2u);
        c = light_color_from_texture(srgb_to_linear(vec3(uvec3(g, g >> 8u, g >> 16u) & 255u) / 255.0));
    }
    uvec3 q = uvec3(local * 32.0 + 0.5);
    uvec3 qc = uvec3(clamp(c, 0.0, 1.0) * 32.0 + 0.5);
    data_add(base + 1u, q.x | (q.y << 16u));
    data_add(base + 2u, q.z | (1u << 16u));
    data_add(base + 3u, qc.r | (qc.g << 16u));
    data_add(base + 4u, qc.b);
    data_max(base + 5u, (uint(level + 0.5) << 8u) | uint(kind + 1));
}

uint classify(ivec3 world, uint grid) {
    uint entry = grid & 0xffffu;
    uint sky = (grid >> 16u) & 15u;
    uint word = entry | (sky << VOX_SKY_SHIFT) | VOX_KNOWN;
    if (entry == 0u) {
        return word; // air
    }
    uint p0 = palette_word(entry, 0u);
    int id = int(p0 & 0xffffu);
    float emission = float((p0 >> 16u) & 15u);
    bool listed = id >= BLOCK_LIGHT && id < BLOCK_LIGHT + LIGHT_KINDS;
    bool fluid = (p0 & PAL_FLUID) != 0u;
    bool walk_through = (p0 & PAL_NO_COLLISION) != 0u;
    if (listed || emission >= 7.0) {
        int kind = listed ? id - BLOCK_LIGHT : -1;
        add_light(world, entry, kind, listed ? light_kind(kind).a : unlisted_light_level(emission));
        uint at = atomicAdd(s_count, 1u);
        if (at < own_list_max) {
            s_keys[at] = light_key(world);
        }
        return word | VOX_LIGHT;
    }
    if (fluid && (walk_through || id == BLOCK_WATER)) {
        // Water (seagrass and kelp in it too) and other fluids.
        return word | VOX_SEE | ((p0 & PAL_WATER) != 0u ? VOX_WATER : 0u);
    }
    if (fluid) {
        word |= (p0 & PAL_WATER) != 0u ? VOX_WATER : 0u; // a waterlogged slab or fence: its shape still counts
    }
    if ((p0 & PAL_HAS_SHAPE) == 0u || id == BLOCK_PLANT || walk_through || flat_layer(entry)) {
        return word;
    }
    bool clear = (p0 & PAL_MOSTLY_CLEAR) != 0u;
    bool translucent = (p0 & PAL_TRANSLUCENT) != 0u;
    if (translucent) {
        return word | VOX_SEE;
    }
    if (clear) {
        return word;
    }
    if (id == BLOCK_LEAVES) {
        return word | VOX_SOLID | VOX_LEAVES;
    }
    return word | VOX_SOLID | ((p0 & PAL_FULL_CUBE) != 0u ? 0u : VOX_PART);
}

void main() {
    ivec3 tile = ivec3(gl_WorkGroupID);
    ivec3 cell = vox_box_lo() + tile * 8 + ivec3(gl_LocalInvocationID);
    uint index = gl_LocalInvocationIndex;
    if (index == 0u) {
        s_live = grid_live() ? 1u : 0u;
        s_count = 0u;
    }
    barrier();

    ivec3 world = cameraPositionInt + cell;
    uint grid = s_live != 0u ? grid_word(world) : 0u;
    uint word = grid != 0u ? classify(world, grid) : 0u;
    imageStore(cg_vox_img, vox_slot(cell, 0), uvec4(word));
    barrier();

    // The lights inside this tile, for the light lists (lib/lights.glsl).
    if (index == 0u) {
        uint base = own_list_index(tile);
        uint n = min(s_count, own_list_max);
        data_store(base, n);
        for (uint i = 0u; i < n; i++) {
            data_store(base + 1u + i, s_keys[i]);
        }
    }
}

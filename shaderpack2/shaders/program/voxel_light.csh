// Coloured block light per block (the softer of the two block light layers): one value per cell of the voxel box,
// for water and glass, the light in the air, the bounce light, and any pixel the traced light on the screen can't
// serve (see program/pixel_light.csh).
//
// One work group per 8 x 8 x 8 tile of blocks. It gathers its candidate lights (see lib/lights.glsl): those inside
// the tiles up to two away, and those that lit it and its six neighbours last time. It weighs each at its nearest
// point of the tile, sorts them, and keeps the 128 strongest. Then each cell, for each light in reach that its surface
// faces: one cone trace through the distance field for how much of the light gets there, and a walk to the first
// see-through block for its colour. A cell in the open has no surface, so it takes light from every side (the light in
// the air). The sum is log-compressed (see compress_light). Last, the tile keeps its list for next time and writes
// its strongest lights as records for the per-pixel light (program/pixel_light.csh), those that lit something here
// first.
//
// Tiles in view (and a little around it) update: near you (or where you are heading) every frame, further away less
// often, down to every 10th frame. Tiles the box has just reached update at once.

#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
#include "/lib/view.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(VOX_GROUPS_X, VOX_GROUPS_Y, VOX_GROUPS_X);

uniform int frameCounter;

layout(rgba16f) writeonly uniform image3D cg_blk_img;

const int candidates_max = 512;
const int set_size = 1024;
const int traces_max = 32;

shared uint s_update;
shared uint s_set[set_size];
shared uint s_count;
shared uint s_key[candidates_max];
shared float s_score[candidates_max];
shared uint s_order[candidates_max];
shared uint s_list_count;
shared vec3 s_pos[tile_list_max];
shared vec3 s_color[tile_list_max];
shared float s_reach[tile_list_max];
shared float s_radius[tile_list_max];
shared float s_level[tile_list_max];
shared uint s_contribution[tile_list_max];

const int own_reach_tiles = 2; // the tiles whose own lights are candidates: up to this many away on each axis

// Whether a box (vox_pos space, centre and half size) is in view, give or take margin blocks.
bool in_view(vec3 centre, float radius, float margin) {
    vec3 view = (gbufferModelView * vec4(centre - cameraPositionFract, 1.0)).xyz;
    mat4 m = gbufferProjection;
    vec4 row0 = vec4(m[0][0], m[1][0], m[2][0], m[3][0]);
    vec4 row1 = vec4(m[0][1], m[1][1], m[2][1], m[3][1]);
    vec4 row2 = vec4(m[0][2], m[1][2], m[2][2], m[3][2]);
    vec4 row3 = vec4(m[0][3], m[1][3], m[2][3], m[3][3]);
    vec4 planes[5] = vec4[](row3 + row0, row3 - row0, row3 + row1, row3 - row1, row3 + row2);
    for (int i = 0; i < 5; i++) {
        float len = length(planes[i].xyz);
        if (dot(planes[i].xyz, view) + planes[i].w < -(radius + margin) * len) {
            return false;
        }
    }
    return true;
}

void add_candidate(uint key) {
    uint h = pcg3d(uvec3(key, 3u, 5u)).x & uint(set_size - 1);
    for (int i = 0; i < 16; i++) {
        uint was = atomicCompSwap(s_set[h], 0u, key + 1u);
        if (was == 0u) {
            uint at = atomicAdd(s_count, 1u);
            if (at < uint(candidates_max)) {
                s_key[at] = key;
            }
            return;
        }
        if (was == key + 1u) {
            return;
        }
        h = (h + 1u) & uint(set_size - 1);
    }
}

// Sorts s_order by s_score, highest first (bitonic, all threads).
void sort_candidates(uint index) {
    for (uint size = 2u; size <= uint(candidates_max); size <<= 1u) {
        for (uint stride = size >> 1u; stride > 0u; stride >>= 1u) {
            uint other = index ^ stride;
            if (other > index) {
                bool descending = (index & size) == 0u;
                uint a = s_order[index];
                uint b = s_order[other];
                if ((s_score[a] < s_score[b]) == descending) {
                    s_order[index] = b;
                    s_order[other] = a;
                }
            }
            barrier();
        }
    }
}

void main() {
    ivec3 tile = ivec3(gl_WorkGroupID);
    ivec3 tile_min = vox_box_lo() + tile * 8;
    ivec3 cell = tile_min + ivec3(gl_LocalInvocationID);
    uint index = gl_LocalInvocationIndex;
    vec3 lo = vec3(tile_min);
    vec3 hi = lo + 8.0;
    vec3 centre = lo + 4.0;

    if (index == 0u) {
        float distance_away = vox_tile_distance(centre);
        int interval = clamp(int(distance_away / 8.0), 1, 10);
        uint offset = pcg3d(uvec3((vox_box_world_lo(cameraPositionInt) >> 3) + tile + 1024)).x;
        bool due = (uint(frameCounter) + offset) % uint(interval) == 0u;
        s_update = due && in_view(centre, 7.0, 10.0) ? 1u : 0u;
        s_count = 0u;
        s_list_count = 0u;
    }
    barrier();
    if (!vox_was_in_box(cell, 0)) {
        atomicOr(s_update, 1u);
    }
    barrier();
    if (s_update == 0u) {
        return;
    }

    for (uint i = index; i < uint(set_size); i += 512u) {
        s_set[i] = 0u;
    }
    barrier();

    // Candidates: the lights inside the tiles around (one tile per thread), then the lists of the tile and its six
    // neighbours.
    const int span = 2 * own_reach_tiles + 1;
    if (index < uint(span * span * span)) {
        ivec3 t = tile + ivec3(int(index) % span, (int(index) / span) % span, int(index) / (span * span)) - own_reach_tiles;
        if (tile_in_box(t)) {
            uint base = own_list_index(t);
            uint count = min(data_load(base), own_list_max);
            for (uint j = 0u; j < count; j++) {
                add_candidate(data_load(base + 1u + j));
            }
        }
    }
    for (int n = 0; n < 7; n++) {
        ivec3 t = tile + (n == 0 ? ivec3(0) : ivec3(n == 1 ? 1 : n == 2 ? -1 : 0, n == 3 ? 1 : n == 4 ? -1 : 0, n == 5 ? 1 : n == 6 ? -1 : 0));
        if (!tile_in_box(t)) {
            continue;
        }
        uint base = block_tile_index(t);
        uint count = min(data_load(base), tile_list_max);
        for (uint j = index; j < count; j += 512u) {
            add_candidate(data_load(base + 1u + j));
        }
    }
    barrier();

    // Each weighed at its nearest point of the tile, then sorted.
    uint candidates = min(s_count, uint(candidates_max));
    float score = -1.0;
    if (index < candidates) {
        Light l = light_from_key(s_key[index]);
        score = l.valid ? light_score(l, lo, hi) : 0.0;
    }
    s_score[index] = score;
    s_order[index] = index;
    barrier();
    sort_candidates(index);

    if (index < tile_list_max) {
        uint k = s_order[index];
        if (s_score[k] > 0.0) {
            Light l = light_from_key(s_key[k]);
            s_pos[index] = l.pos;
            s_color[index] = l.color;
            s_reach[index] = l.reach;
            s_level[index] = l.level;
            // Its soft shadows' cone: half a block, less where the light's block sits in a tight spot (its shadows
            // are then sharper). Measured from the block's middle, not the light's place in it, so a light that moves
            // in its block (a portal's) keeps the width of every shadow it casts.
            float space = max(free_space(floor(l.pos) + 0.5, 0), 0.05);
            s_radius[index] = 0.5 * min(1.0, space) * 0.6;
            s_contribution[index] = 0u;
            atomicMax(s_list_count, index + 1u);
        }
    }
    barrier();

    // This cell's light.
    uint word = vox_word(cell);
    ivec3 slot = vox_slot(cell, 0);
    if ((word & VOX_SOLID) != 0u && (word & VOX_PART) == 0u) {
        imageStore(cg_blk_img, slot, vec4(0.0)); // a full block or leaves: nothing inside
    } else {
        vec3 normal = vox_normal(cell);
        vec3 p = cell_light_point(cell, word, normal);
        vec3 sum = vec3(0.0);
        int traces = 0;
        uint lights = s_list_count;
        for (uint j = 0u; j < lights && traces < traces_max; j++) {
            vec3 to_light = s_pos[j] - p;
            float d = length(to_light);
            if (d >= s_reach[j]) {
                continue;
            }
            vec3 dir = to_light / max(d, 1e-4);
            float facing = dot(normal, normal) > 0.5 ? dot(normal, dir) : 1.0;
            if (facing <= 0.0) {
                continue;
            }
            vec3 c = s_color[j] * light_falloff(d, s_reach[j]) * facing;
            if (max3(c) < 1e-4) {
                continue;
            }
            traces++;
            float visible = cone_visibility(p, dir, d, 0.1, 0.25, 0.5, 48);
            if (visible <= 0.0) {
                continue;
            }
            c *= visible * see_through_tint(p, dir, d);
            sum += c;
            atomicMax(s_contribution[j], floatBitsToUint(max3(c)));
        }
        imageStore(cg_blk_img, slot, vec4(compress_light(sum), 1.0));
    }
    barrier();

    // The lights that lit something here, for next time; and the records, those first.
    if (index == 0u) {
        uint base = block_tile_index(tile);
        uint records = record_index(tile);
        uint n = 0u;
        uint r = 0u;
        for (uint j = 0u; j < s_list_count; j++) {
            if (uintBitsToFloat(s_contribution[j]) > 2e-4) {
                uint key = s_key[s_order[j]];
                data_store(base + 1u + n, key);
                n++;
                if (r < record_max) {
                    Light l;
                    l.pos = s_pos[j];
                    l.color = s_color[j];
                    l.level = s_level[j];
                    store_record(records + 1u + 3u * r, key, l, s_radius[j]);
                    r++;
                }
            }
        }
        data_store(base, n);
        for (uint j = 0u; j < s_list_count && r < record_max; j++) {
            if (uintBitsToFloat(s_contribution[j]) <= 2e-4) {
                Light l;
                l.pos = s_pos[j];
                l.color = s_color[j];
                l.level = s_level[j];
                store_record(records + 1u + 3u * r, s_key[s_order[j]], l, s_radius[j]);
                r++;
            }
        }
        data_store(records, r);
    }
}

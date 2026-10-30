// The voxel copy's distance field (see lib/trace.glsl): per cell, how many cells away the nearest cell that stops
// light is (green: full blocks, part blocks, leaves), and the nearest full block (red), counted as a cube grows. Each
// update a cell takes the smallest value among its 26 neighbours plus one, so the field spreads a cell per update,
// settles in a few frames and from then on only follows what changes (a block placed or broken). It works in
// 8 x 8 x 8 tiles with their neighbours in shared memory, twice per update; half the tiles update each frame (a
// checkerboard), and tiles the box has just reached update every frame, starting from a cell (which only makes rays
// take shorter steps until it settles).

#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(VOX_GROUPS_X, VOX_GROUPS_Y, VOX_GROUPS_X);

uniform int frameCounter;

layout(r32ui) readonly uniform uimage3D cg_vox_img;
layout(rg8) uniform image3D cg_sdf_img; // whole numbers 0..255, kept as 0..1 so they can be read blended

shared uint s_d[1000]; // the tile and a cell around it, 10 x 10 x 10: red | green << 8
shared uint s_update;

const uint far_away = 255u;

uint pack2(uint full, uint any) {
    return full | (any << 8u);
}

void main() {
    ivec3 tile = ivec3(gl_WorkGroupID);
    ivec3 tile_min = vox_box_lo() + tile * 8;
    uint index = gl_LocalInvocationIndex;
    ivec3 world_tile = (vox_box_world_lo(cameraPositionInt) >> 3) + tile;

    if (index == 0u) {
        s_update = (world_tile.x + world_tile.y + world_tile.z + frameCounter) % 2 == 0 ? 1u : 0u;
    }
    barrier();
    ivec3 cell = tile_min + ivec3(gl_LocalInvocationID);
    if (!vox_was_in_box(cell, 0)) {
        atomicOr(s_update, 1u);
    }
    barrier();
    if (s_update == 0u) {
        return;
    }

    // The tile and its neighbours: cells that stop light 0, new ones 1, the rest as last time.
    for (uint i = index; i < 1000u; i += 512u) {
        ivec3 at = tile_min + ivec3(int(i % 10u), int((i / 10u) % 10u), int(i / 100u)) - 1;
        uint d;
        if (!vox_in_box(at, 0)) {
            d = pack2(far_away, far_away);
        } else {
            uint word = imageLoad(cg_vox_img, vox_slot(at, 0)).r;
            bool any = (word & VOX_SOLID) != 0u;
            bool full = vox_full(word);
            if (!vox_was_in_box(at, 0)) {
                d = pack2(full ? 0u : 1u, any ? 0u : 1u);
            } else {
                vec2 old = imageLoad(cg_sdf_img, vox_slot(at, 0)).rg * 255.0 + 0.5;
                d = pack2(full ? 0u : uint(old.r), any ? 0u : uint(old.g));
            }
        }
        s_d[i] = d;
    }
    barrier();

    ivec3 local = ivec3(gl_LocalInvocationID) + 1;
    uint own = uint(local.x + local.y * 10 + local.z * 100);
    uint word = imageLoad(cg_vox_img, vox_slot(cell, 0)).r;
    bool any = (word & VOX_SOLID) != 0u;
    bool full = vox_full(word);

    uint d = s_d[own];
    for (int pass = 0; pass < 2; pass++) {
        uint m_full = far_away;
        uint m_any = far_away;
        for (int z = -1; z <= 1; z++) {
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    if (x != 0 || y != 0 || z != 0) {
                        uint n = s_d[own + uint(x + y * 10 + z * 100)];
                        m_full = min(m_full, n & 255u);
                        m_any = min(m_any, n >> 8u);
                    }
                }
            }
        }
        d = pack2(full ? 0u : min(m_full + 1u, far_away), any ? 0u : min(m_any + 1u, far_away));
        barrier();
        s_d[own] = d;
        barrier();
    }
    imageStore(cg_sdf_img, vox_slot(cell, 0), vec4(float(d & 255u), float(d >> 8u), 0.0, 0.0) / 255.0);
}

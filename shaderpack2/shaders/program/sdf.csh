// The voxel copy's distance field, one per detail level (see lib/trace.glsl): per cell, how many cells away the
// nearest solid cell is, counted as a cube grows. Each update a cell takes the smallest value among its 26
// neighbours plus one, so the field spreads a cell per update, settles in a few frames and from then on only follows
// what changes. It works in 8 x 8 x 8 tiles with their neighbours in shared memory, twice per update; half the
// tiles update each frame (a checkerboard), and tiles the moving box has just reached update every frame.
//
// The levels lean on each other. A fine cell far from any surface is at least as far as its coarser cell says
// (which settles sooner), and cells the box has just reached start from the coarser level's value. Every level's box
// holds everything in it up to its edge (shadow.gsh), so near the edge a fine level needs no help: taking the coarser
// level's word there (as it once did) put the ground a coarse cell closer than it is, and darkened the lamp light on
// the floor around you in a square.

#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(VOX_GROUPS_X, SDF_GROUPS_Y, VOX_GROUPS_X);

uniform int frameCounter;

layout(r32ui) readonly uniform uimage3D cg_vox_img;
layout(r8) uniform image3D cg_sdf_img; // whole numbers 0..255, kept as 0..1 so it can be read blended

shared uint s_d[1000]; // the tile and a cell around it, 10 x 10 x 10
shared uint s_update;

const uint far_away = 255u;

ivec3 sdf_texel(ivec3 cell, int level) {
    ivec3 slot = vox_slot(cell, level);
    return ivec3(slot.x, slot.y + level * vox_size.y, slot.z);
}

uint sdf_load(ivec3 cell, int level) {
    return uint(imageLoad(cg_sdf_img, sdf_texel(cell, level)).r * 255.0 + 0.5);
}

// What the next coarser level says of a cell at this level: a lower bound on how far it is (in this level's
// cells), or 0 when the coarser cell is solid. far_away outside the coarser box too.
uint coarser_bound(ivec3 cell, int level) {
    ivec3 coarse = cell >> 1;
    if (!vox_in_box(coarse, level - 1)) {
        return far_away;
    }
    uint d = sdf_load(coarse, level - 1);
    return d == 0u ? 0u : min(2u * d - 1u, far_away);
}

void main() {
    int tiles_y = VOX_Y / 8;
    int level = int(gl_WorkGroupID.y) / tiles_y;
    ivec3 tile = ivec3(gl_WorkGroupID.x, int(gl_WorkGroupID.y) % tiles_y, gl_WorkGroupID.z);
    ivec3 tile_min = tile * 8 - vox_half + vox_centre(level);
    uint index = gl_LocalInvocationIndex;

    if (index == 0u) {
        s_update = (tile.x + tile.y + tile.z + frameCounter + level) % 2 == 0 ? 1u : 0u;
    }
    barrier();
    ivec3 cell = tile_min + ivec3(gl_LocalInvocationID);
    if (!vox_was_in_box(cell, level)) {
        atomicOr(s_update, 1u);
    }
    barrier();
    if (s_update == 0u) {
        return;
    }

    // The tile and its neighbours: solid cells 0, new ones from the coarser level, the rest as last time.
    for (uint i = index; i < 1000u; i += 512u) {
        ivec3 at = tile_min + ivec3(int(i % 10u), int((i / 10u) % 10u), int(i / 100u)) - 1;
        uint d;
        if (!vox_in_box(at, level)) {
            d = level > 0 ? coarser_bound(at, level) : far_away;
        } else if ((imageLoad(cg_vox_img, vox_slot(at, level)).r & vox_solid_bit(level)) != 0u) {
            d = 0u;
        } else if (!vox_was_in_box(at, level)) {
            d = level > 0 ? max(coarser_bound(at, level), 1u) : 1u;
        } else {
            d = sdf_load(at, level);
        }
        s_d[i] = d;
    }
    barrier();

    ivec3 local = ivec3(gl_LocalInvocationID) + 1;
    uint own = uint(local.x + local.y * 10 + local.z * 100);
    bool solid = (imageLoad(cg_vox_img, vox_slot(cell, level)).r & vox_solid_bit(level)) != 0u;
    uint coarse = level > 0 ? coarser_bound(cell, level) : far_away;

    uint d = s_d[own];
    for (int pass = 0; pass < 2; pass++) {
        if (solid) {
            d = 0u;
        } else {
            uint m = far_away;
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        if (x != 0 || y != 0 || z != 0) {
                            m = min(m, s_d[own + uint(x + y * 10 + z * 100)]);
                        }
                    }
                }
            }
            d = min(m + 1u, far_away);
            if (coarse != far_away) {
                d = max(d, coarse);
            }
        }
        barrier();
        s_d[own] = d;
        barrier();
    }
    imageStore(cg_sdf_img, sdf_texel(cell, level), vec4(float(d) / 255.0));
}

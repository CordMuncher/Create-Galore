// The sky visibility (shadowcomp10, after the sky grid's coarser copies, see sky_grid.glsl): how much of the sky each
// block of the light volume sees past the blocks around it, for the lighting to read smoothly between blocks (see
// sky_visibility in light_volume.glsl). In place of rays from every pixel each frame, which a room lit through a window
// would sparkle with, rays from the middle of each block along the same fixed directions over the sky every time, so
// a block sees the same sky every frame however the camera moves.
//
// A sixteenth of the blocks each frame (by where they are in the world, so the set doesn't change as you move) traces
// its rays through the sky grid (sky_ray_open), and every other block keeps last frame's, moved along with the volume
// as you go from block to block; a block new to the volume (at its edge as you move, or all of it after the pack loads)
// traces its own at once. A changed roof shows within sixteen frames.
//
// What it keeps, in the light image's two sky sections (r11f_g11f_b10f, which has no sign):
// - the share of the sky a surface facing +x, -x, +y (first texel), +z, -z (second) sees: the sky above the horizon on
//   its side, each direction weighted as its light falls on the surface (by the cosine), as lighting.glsl's sky light
//   does. Down sees no sky.
// - and in the second texel's b, 2 plus whether the sun's (or the moon's) light gets to the block's middle past the
//   blocks around it (a block at a time for 32 blocks, so it finds a window across a room), for the light in the air
//   (see light_reaches); under 1.5 where the block isn't worked out yet.
// A solid block keeps none (all 0): the lighting reads between the open blocks around a point only, as probe volumes
// leave out the probes inside walls, since a wall's block is the room's on one side and the outside's on the other.

#define PROGRAM_SKY_VISIBILITY
#define READ_IMAGES
#include "/lib/settings.glsl"
#include "/lib/light_volume.glsl"
#include "/lib/globals.glsl"
#include "/lib/sky_grid.glsl"

#ifndef LIT_SKY_VISIBILITY
// Sky Visibility off (or no volume): nothing to do.
layout(local_size_x = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);
void main() {
}
#else

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
// The volume's size over the groups' (Iris takes only plain numbers here).
#if LIGHT_RANGE == 64
const ivec3 workGroups = ivec3(8, 4, 8);
#elif LIGHT_RANGE == 192
const ivec3 workGroups = ivec3(24, 12, 24);
#elif LIGHT_RANGE == 256
const ivec3 workGroups = ivec3(32, 16, 32);
#else
const ivec3 workGroups = ivec3(16, 8, 16);
#endif

layout(r11f_g11f_b10f) uniform image3D cg_light_img;
uniform ivec3 cameraPositionInt;
uniform ivec3 previousCameraPositionInt;
uniform int frameCounter;

// The directions over the sky each block traces: spread evenly over the sky above the horizon (evenly in height, which
// is evenly by solid angle), each turned the golden angle from the last.
const int sky_directions = 24;

vec3 sky_direction(int i) {
    float up = 0.05 + 0.95 * (float(i) + 0.5) / float(sky_directions);
    float r = sqrt(max(1.0 - up * up, 0.0));
    float phi = 2.39996323 * float(i);
    return vec3(r * cos(phi), up, r * sin(phi));
}

// Last frame's two texels of a block, (0, 0, 0) where it lies outside the volume.
void previous_sky(ivec3 block, out vec3 a, out vec3 b) {
    int turn = 1 - (frameCounter & 1);
    bool inside = in_light_volume(block);
    a = inside ? imageLoad(cg_light_img, light_texel(block, section_sky, turn)).rgb : vec3(0.0);
    b = inside ? imageLoad(cg_light_img, light_texel(block, section_sky + 1, turn)).rgb : vec3(0.0);
}

void store_sky(ivec3 block, vec3 a, vec3 b) {
    int turn = frameCounter & 1;
    imageStore(cg_light_img, light_texel(block, section_sky, turn), vec4(a, 0.0));
    imageStore(cg_light_img, light_texel(block, section_sky + 1, turn), vec4(b, 0.0));
}

bool block_solid(ivec3 block) {
    return in_light_volume(block) && marks_solid(voxel_marks(marks_texel(block, section_marks)));
}

void main() {
    ivec3 block = ivec3(gl_GlobalInvocationID);
    if (!in_light_volume(block)) {
        return;
    }
    if (block_solid(block)) {
        store_sky(block, vec3(0.0), vec3(0.0));
        return;
    }
    // Where this block was in last frame's volume, and which sixteenth of the world's blocks it is in.
    ivec3 prev = block + cameraPositionInt - previousCameraPositionInt;
    ivec3 world = block - light_volume_size / 2 + cameraPositionInt;
    int slot = ((world.x & 3) | ((world.z & 3) << 2)) ^ ((world.y * 5) & 15);
    vec3 a;
    vec3 b;
    previous_sky(prev, a, b);
    if (slot != (frameCounter & 15) && b.b >= 1.5) {
        store_sky(block, a, b);
        return;
    }

    // The block's middle in the sky grid (the grid and the volume are both centred on the block you are in).
    vec3 o = vec3(block - light_volume_size / 2) + 0.5
            + vec3(float(sky_grid_size / 2), float(sky_grid_below), float(sky_grid_size / 2));
    float open_px = 0.0, open_nx = 0.0, open_py = 0.0, open_pz = 0.0, open_nz = 0.0;
    float sum_px = 0.0, sum_nx = 0.0, sum_py = 0.0, sum_pz = 0.0, sum_nz = 0.0;
    for (int i = 0; i < sky_directions; i++) {
        vec3 d = sky_direction(i);
        float open = sky_ray_open(o, d) ? 1.0 : 0.0;
        float wpx = max(d.x, 0.0), wnx = max(-d.x, 0.0), wpz = max(d.z, 0.0), wnz = max(-d.z, 0.0);
        open_px += open * wpx;
        open_nx += open * wnx;
        open_py += open * d.y;
        open_pz += open * wpz;
        open_nz += open * wnz;
        sum_px += wpx;
        sum_nx += wnx;
        sum_py += d.y;
        sum_pz += wpz;
        sum_nz += wnz;
    }
    // And the sun's (or the moon's) light, while it is above the horizon.
    vec3 l = g_light_dir.xyz;
    float light = l.y > 0.0 && sky_ray_open(o, normalize(l), 32) ? 1.0 : 0.0;
    store_sky(block, vec3(open_px / sum_px, open_nx / sum_nx, open_py / sum_py),
            vec3(open_pz / sum_pz, open_nz / sum_nz, 2.0 + light));
}
#endif

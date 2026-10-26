// The overworld's light flood, after the shadow pass (see light_volume.glsl), for the coloured light and the bounce
// light. Coloured light: each block of the volume takes the brightest light of its six neighbours in last frame's
// volume, a light level dimmer (each channel in proportion, so the colour stays), or its own light if it is a light;
// a solid cube takes none, tinting glass tints it. Bounce light: each block takes what the sunlit surfaces around it
// reflected into it this frame (light_bounce.csh), and a share of each facing's light in its six neighbours
// (bounce_pass, less through leaves); a solid neighbour is a wall facing this block, which sends back what reaches it
// of the other facings' light as its own facing's. A solid cube takes none. As the camera moves into another block,
// last frame's volume is read where this block was in it.

#define PROGRAM_LIGHT_FLOOD
#include "/lib/light_volume.glsl"

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

layout(r32ui) readonly uniform uimage3D cg_voxel_img;
layout(r11f_g11f_b10f) uniform image3D cg_light_img;
uniform ivec3 cameraPositionInt;
uniform ivec3 previousCameraPositionInt;
uniform int frameCounter;

// Last frame's coloured light in a block, none outside the volume.
vec3 previous_light(ivec3 block) {
    return in_light_volume(block) ? imageLoad(cg_light_img, light_texel(block, section_color, 1 - (frameCounter & 1))).rgb : vec3(0.0);
}

void store_light(ivec3 block, int section, vec3 light) {
    imageStore(cg_light_img, light_texel(block, section, frameCounter & 1), vec4(light, 0.0));
}

#ifdef LIT_GI
// Last frame's bounce light in a block, none outside the volume: its colour, and each facing's (see light_volume.glsl).
vec3 previous_bounce(ivec3 block, out float facing[6]) {
    int turn = 1 - (frameCounter & 1);
    bool inside = in_light_volume(block);
    vec3 a = inside ? imageLoad(cg_light_img, light_texel(block, section_bounce_facing, turn)).rgb : vec3(0.0);
    vec3 b = inside ? imageLoad(cg_light_img, light_texel(block, section_bounce_facing + 1, turn)).rgb : vec3(0.0);
    facing = float[6](a.r, a.g, a.b, b.r, b.g, b.b);
    return inside ? imageLoad(cg_light_img, light_texel(block, section_bounce, turn)).rgb : vec3(0.0);
}
#endif

void main() {
    ivec3 block = ivec3(gl_GlobalInvocationID);
    if (!in_light_volume(block)) {
        return;
    }
    ivec3 prev = block + cameraPositionInt - previousCameraPositionInt;
    uint marks = imageLoad(cg_voxel_img, marks_texel(block, section_marks)).r;
    bool solid = marks_solid(marks);

#ifdef LIT_COLORED_LIGHT
    uint light_mark_here = imageLoad(cg_voxel_img, marks_texel(block, section_lights)).r;
    vec3 light = vec3(0.0);
    if (!solid) {
        vec3 m = max(max(previous_light(prev + ivec3(1, 0, 0)), previous_light(prev - ivec3(1, 0, 0))),
                max(previous_light(prev + ivec3(0, 1, 0)), previous_light(prev - ivec3(0, 1, 0))));
        m = max(m, max(previous_light(prev + ivec3(0, 0, 1)), previous_light(prev - ivec3(0, 0, 1))));
        float peak = max(m.r, max(m.g, m.b));
        if (peak > 0.0) {
            light = m * (max(peak - 1.0 / 15.0, 0.0) / peak);
        }
        if ((marks & mark_tinted) != 0u) {
            light *= tint_from_mark(marks);
        }
    }
    if (light_mark_here != 0u) {
        light = max(light, light_from_mark(light_mark_here));
    }
    store_light(block, section_color, light);
#endif

#ifdef LIT_GI
    vec3 color = vec3(0.0);
    float facing[6] = float[6](0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    if (!solid) {
        float here[6];
        vec3 here_color = previous_bounce(prev, here);
        float here_sum = here[0] + here[1] + here[2] + here[3] + here[4] + here[5];
        uint added[5];
        for (int i = 0; i < 5; i++) {
            added[i] = imageLoad(cg_voxel_img, marks_texel(block, section_bounce_added + i)).r;
        }
        vec3 color_around = vec3(0.0);
        float around[6] = float[6](0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 6; i++) {
            ivec3 d = ivec3(facing_dir(i));
            ivec3 n = block + d;
            if (in_light_volume(n) && marks_solid(imageLoad(cg_voxel_img, marks_texel(n, section_marks)).r)) {
                // A wall facing back this way (facing i ^ 1).
                color_around += here_color * bounce_wall_reflect;
                around[i ^ 1] += (here_sum - here[i ^ 1]) * bounce_wall_reflect;
            } else {
                float f[6];
                color_around += previous_bounce(prev + d, f);
                for (int k = 0; k < 6; k++) {
                    around[k] += f[k];
                }
            }
        }
        float pass = bounce_pass / 6.0 * ((marks & mark_foliage) != 0u ? bounce_foliage_pass : 1.0);
        vec3 tint = (marks & mark_tinted) != 0u ? tint_from_mark(marks) : vec3(1.0);
        float tint_luma = dot(tint, bounce_luma);
        vec3 added_color = vec3(bounce_added_value(added[0], 0), bounce_added_value(added[0], 1), bounce_added_value(added[1], 2));
        // Halfway from last frame's towards this, so it settles rather than flickers where the shadow map's samples
        // move across the blocks (as the sun moves, and the shadow map with you).
        color = mix(here_color, (added_color + color_around * pass) * tint, 0.5);
        for (int k = 0; k < 6; k++) {
            int sum = 3 + k;
            facing[k] = mix(here[k], (bounce_added_value(added[sum / 2], sum) + around[k] * pass) * tint_luma, 0.5);
        }
    }
    store_light(block, section_bounce, color);
    store_light(block, section_bounce_facing, vec3(facing[0], facing[1], facing[2]));
    store_light(block, section_bounce_facing + 1, vec3(facing[3], facing[4], facing[5]));
#endif
}

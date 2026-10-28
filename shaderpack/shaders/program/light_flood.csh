// The overworld's light flood, after the shadow pass (see light_volume.glsl), for the coloured light and the bounce
// light. Coloured light: each block of the volume takes the brightest light of its six neighbours in last frame's
// volume, a light level dimmer (each channel in proportion, so the colour stays), and of the blocks two away past an
// open one, two levels dimmer, or its own light if it is a light; a solid cube takes none, tinting glass tints it.
// With it goes where the light comes from (see block_light_source). Bounce light: each block takes what the sunlit surfaces around it
// reflected into it this frame (light_bounce.csh), and a share of each facing's light in its six neighbours
// (bounce_pass, less through leaves); a solid neighbour is a wall facing this block, which sends back what reaches it
// of the other facings' light as its own facing's. A solid cube takes none. As the camera moves into another block,
// last frame's volume is read where this block was in it. The trees' crowns: how many of the blocks around each block
// are leaves, nearer ones counting for more, over about one block and about two (see canopy_shape), counted afresh
// every frame.

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

// Last frame's source of a block's light (see section_source), none outside the volume.
vec3 previous_source(ivec3 block) {
    return in_light_volume(block) ? imageLoad(cg_light_img, light_texel(block, section_source, 1 - (frameCounter & 1))).rgb : vec3(0.0);
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

// The leaves among the group's blocks and two blocks around them, for the crowns.
#ifdef TREE_CROWNS
shared uint group_leaves[12 * 12 * 12];
#endif
// How much a leaf counts by how far it is along each axis (0, 1, 2 blocks), for the narrow count and the wide one, and
// all of them together.
const float near_weight[3] = float[3](1.0, 0.46, 0.0);
const float far_weight[3] = float[3](1.0, 0.8, 0.41);
const float near_sum = (1.0 + 2.0 * 0.46) * (1.0 + 2.0 * 0.46) * (1.0 + 2.0 * 0.46);
const float far_sum = (1.0 + 2.0 * 0.8 + 2.0 * 0.41) * (1.0 + 2.0 * 0.8 + 2.0 * 0.41) * (1.0 + 2.0 * 0.8 + 2.0 * 0.41);

void main() {
    ivec3 block = ivec3(gl_GlobalInvocationID);
#ifdef TREE_CROWNS
    ivec3 group_corner = ivec3(gl_WorkGroupID) * 8 - 2;
    for (uint i = gl_LocalInvocationIndex; i < 1728u; i += 512u) {
        ivec3 b = group_corner + ivec3(i % 12u, (i / 12u) % 12u, i / 144u);
        group_leaves[i] = in_light_volume(b) ? imageLoad(cg_voxel_img, marks_texel(b, section_marks)).r & mark_foliage : 0u;
    }
    barrier();
#endif
    if (!in_light_volume(block)) {
        return;
    }

#ifdef TREE_CROWNS
    ivec3 local = ivec3(gl_LocalInvocationID) + 2;
    vec2 leaves = vec2(0.0);
    for (int z = -2; z <= 2; z++) {
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                ivec3 t = local + ivec3(x, y, z);
                if (group_leaves[t.x + 12 * (t.y + 12 * t.z)] != 0u) {
                    leaves += vec2(near_weight[abs(x)] * near_weight[abs(y)] * near_weight[abs(z)],
                            far_weight[abs(x)] * far_weight[abs(y)] * far_weight[abs(z)]);
                }
            }
        }
    }
    store_light(block, section_canopy, vec3(leaves / vec2(near_sum, far_sum), 0.0));
#endif
    ivec3 prev = block + cameraPositionInt - previousCameraPositionInt;
    uint marks = imageLoad(cg_voxel_img, marks_texel(block, section_marks)).r;
    bool solid = marks_solid(marks);

#ifdef LIT_COLORED_LIGHT
    uint light_mark_here = imageLoad(cg_voxel_img, marks_texel(block, section_lights)).r;
    vec3 light = vec3(0.0);
    // Where it comes from: the way to the light of the brightest neighbour's, one step further (the first of equals,
    // so it doesn't flicker between them where two lights meet).
    vec3 source = vec3(0.0);
    if (!solid) {
        vec3 m = vec3(0.0);
        float brightest = 0.0;
        for (int i = 0; i < 6; i++) {
            ivec3 d = ivec3(facing_dir(i));
            vec3 l = previous_light(prev + d);
            m = max(m, l);
            float p = max(l.r, max(l.g, l.b));
            if (p > brightest) {
                vec3 s = previous_source(prev + d);
                if (s.x > 0.5) {
                    brightest = p;
                    source = s + vec3(d);
                }
            }
            // And the light two blocks off through an open block between, a level dimmer again: two blocks a frame,
            // so a room's light fills in twice as fast (after a camera cut, or when a light is placed).
            ivec3 between = block + d;
            uint between_marks = in_light_volume(between) ? imageLoad(cg_voxel_img, marks_texel(between, section_marks)).r
                                                         : mark_solid;
            if (!marks_solid(between_marks) && (between_marks & mark_tinted) == 0u) {
                vec3 l2 = previous_light(prev + 2 * d);
                float p2 = max(l2.r, max(l2.g, l2.b));
                if (p2 > 1.0 / 15.0) {
                    l2 *= (p2 - 1.0 / 15.0) / p2;
                    m = max(m, l2);
                    if (p2 - 1.0 / 15.0 > brightest) {
                        vec3 s = previous_source(prev + 2 * d);
                        if (s.x > 0.5) {
                            brightest = p2 - 1.0 / 15.0;
                            source = s + vec3(2 * d);
                        }
                    }
                }
            }
        }
        float peak = max(m.r, max(m.g, m.b));
        if (peak > 0.0) {
            light = m * (max(peak - 1.0 / 15.0, 0.0) / peak);
        }
        if ((marks & mark_tinted) != 0u) {
            light *= tint_from_mark(marks);
        }
    }
    if (light_mark_here != 0u) {
        vec3 own = light_from_mark(light_mark_here);
        if (max(own.r, max(own.g, own.b)) >= max(light.r, max(light.g, light.b))) {
            source = vec3(source_offset); // its own
        }
        light = max(light, own);
    }
    if (max(light.r, max(light.g, light.b)) <= 0.0 || any(lessThan(source, vec3(0.5)))
            || any(greaterThan(source, vec3(2.0 * source_offset - 0.5)))) {
        source = vec3(0.0);
    }
    store_light(block, section_color, light);
    store_light(block, section_source, source);
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

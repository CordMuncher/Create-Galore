// The overworld's light flood, after the shadow pass (see light_volume.glsl), for the coloured light and the bounce
// light. Coloured light: each block of the volume takes the brightest light of its six neighbours in last frame's
// volume, a light level dimmer (each channel in proportion, so the colour stays), or its own light if it is a light;
// a solid cube takes none, tinting glass tints it. Bounce light: each block takes what the sunlit surfaces around it
// reflected into it this frame (light_bounce.csh), and a share of its six neighbours' (bounce_pass, less through
// leaves), a solid neighbour sending some of its own back; a solid cube takes none. As the camera moves into another block, last frame's volume is read where this
// block was in it.

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

// Last frame's light of a section (section_color, section_bounce) in a block, none outside the volume.
vec3 previous_light(ivec3 block, int section) {
    int z = (section + 1 - (frameCounter & 1)) * light_volume_depth;
    return in_light_volume(block) ? imageLoad(cg_light_img, block + ivec3(0, 0, z)).rgb : vec3(0.0);
}

void store_light(ivec3 block, int section, vec3 light) {
    imageStore(cg_light_img, block + ivec3(0, 0, (section + (frameCounter & 1)) * light_volume_depth), vec4(light, 0.0));
}

void main() {
    ivec3 block = ivec3(gl_GlobalInvocationID);
    if (!in_light_volume(block)) {
        return;
    }
    ivec3 prev = block + cameraPositionInt - previousCameraPositionInt;
    uint marks = imageLoad(cg_voxel_img, block + ivec3(0, 0, section_marks * light_volume_depth)).r;
    bool solid = marks_solid(marks);

#ifdef LIT_COLORED_LIGHT
    uint light_mark_here = imageLoad(cg_voxel_img, block + ivec3(0, 0, section_lights * light_volume_depth)).r;
    vec3 light = vec3(0.0);
    if (!solid) {
        vec3 m = max(max(previous_light(prev + ivec3(1, 0, 0), section_color), previous_light(prev - ivec3(1, 0, 0), section_color)),
                max(previous_light(prev + ivec3(0, 1, 0), section_color), previous_light(prev - ivec3(0, 1, 0), section_color)));
        m = max(m, max(previous_light(prev + ivec3(0, 0, 1), section_color), previous_light(prev - ivec3(0, 0, 1), section_color)));
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
    vec3 bounce = vec3(0.0);
    if (!solid) {
        vec3 added = vec3(
                imageLoad(cg_voxel_img, block + ivec3(0, 0, section_bounce_added * light_volume_depth)).r,
                imageLoad(cg_voxel_img, block + ivec3(0, 0, (section_bounce_added + 1) * light_volume_depth)).r,
                imageLoad(cg_voxel_img, block + ivec3(0, 0, (section_bounce_added + 2) * light_volume_depth)).r)
                / bounce_scale;
        // A solid neighbour sends some of this block's light back (bounce_wall_reflect), an open one its own.
        vec3 here = previous_light(prev, section_bounce);
        vec3 around = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            ivec3 d = ivec3(0);
            d[i >> 1] = (i & 1) == 0 ? 1 : -1;
            ivec3 n = block + d;
            bool wall = in_light_volume(n)
                    && marks_solid(imageLoad(cg_voxel_img, n + ivec3(0, 0, section_marks * light_volume_depth)).r);
            around += wall ? here * bounce_wall_reflect : previous_light(prev + d, section_bounce);
        }
        float pass = bounce_pass * ((marks & mark_foliage) != 0u ? bounce_foliage_pass : 1.0);
        bounce = added + around * (pass / 6.0);
        if ((marks & mark_tinted) != 0u) {
            bounce *= tint_from_mark(marks);
        }
        // Halfway from last frame's towards this, so it settles rather than flickers where the shadow map's samples
        // move across the blocks (as the sun moves, and the shadow map with you).
        bounce = mix(here, bounce, 0.5);
    }
    store_light(block, section_bounce, bounce);
#endif
}

// The overworld's shadowcomp pass: the light flood for the coloured light (see light_volume.glsl). Each block of
// the volume takes the brightest light of its six neighbours in last frame's volume, a light level dimmer (each
// channel in proportion, so the colour stays), or its own light if it is a light; a solid cube takes none, tinting
// glass tints it. As the camera moves into another block, last frame's volume is read where this block was in it.

#define PROGRAM_LIGHT_FLOOD
#include "/lib/light_volume.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(COLORED_LIGHT_RANGE / 8, COLORED_LIGHT_RANGE / 16, COLORED_LIGHT_RANGE / 8);

layout(r32ui) readonly uniform uimage3D cg_voxel_img;
layout(r11f_g11f_b10f) uniform image3D cg_light_img;
uniform ivec3 cameraPositionInt;
uniform ivec3 previousCameraPositionInt;
uniform int frameCounter;

vec3 previous_light(ivec3 block, int read_z) {
    return in_light_volume(block) ? imageLoad(cg_light_img, block + ivec3(0, 0, read_z)).rgb : vec3(0.0);
}

void main() {
    ivec3 block = ivec3(gl_GlobalInvocationID);
    if (!in_light_volume(block)) {
        return;
    }
    int write_z = (frameCounter & 1) == 0 ? 0 : light_volume_depth;
    int read_z = light_volume_depth - write_z;
    ivec3 prev = block + cameraPositionInt - previousCameraPositionInt;

    uint marks = imageLoad(cg_voxel_img, block).r;
    uint light_mark_here = imageLoad(cg_voxel_img, block + ivec3(0, 0, light_volume_depth)).r;
    vec3 light = vec3(0.0);
    if ((marks & (mark_solid | mark_partial)) != mark_solid) {
        vec3 m = max(max(previous_light(prev + ivec3(1, 0, 0), read_z), previous_light(prev - ivec3(1, 0, 0), read_z)),
                max(previous_light(prev + ivec3(0, 1, 0), read_z), previous_light(prev - ivec3(0, 1, 0), read_z)));
        m = max(m, max(previous_light(prev + ivec3(0, 0, 1), read_z), previous_light(prev - ivec3(0, 0, 1), read_z)));
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
    imageStore(cg_light_img, block + ivec3(0, 0, write_z), vec4(light, 0.0));
}

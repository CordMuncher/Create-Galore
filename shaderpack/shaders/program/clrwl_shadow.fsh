// Flywheel's material code cuts out what its textures leave open; the rest casts a shadow, and with ripples on marks
// how low and how high it reaches in each cell of their grid (water wheels, contraptions on the water; see shadow.fsh).

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/water.glsl"

uniform sampler2D gtexture;

in vec2 v_uv;

#ifdef LIT_WATER_RIPPLES
uniform vec3 cameraPositionFract;
layout(r32ui) uniform uimage2D cg_water_marks_img;
in vec3 v_scene_pos;
#endif

void main() {
    vec4 color;
    vec2 light;
    float ao;
    vec4 overlay;
    // Discards what the material cuts out.
    clrwl_computeFragment(texture(gtexture, v_uv), color, light, ao, overlay);
#ifdef LIT_WATER_RIPPLES
    ivec2 cell = water_cell(v_scene_pos, cameraPositionFract);
    if (all(greaterThanEqual(cell, ivec2(0))) && all(lessThan(cell, ivec2(water_sim_size)))) {
        uint height = water_height_mark(v_scene_pos.y);
        imageAtomicMax(cg_water_marks_img, cell + ivec2(0, water_sim_size), 65536u - height);
        imageAtomicMax(cg_water_marks_img, cell + ivec2(0, 2 * water_sim_size), height);
    }
#endif
}

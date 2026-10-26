// The shadow map's depth only: what a texture cuts out (leaves, grass, fences) casts no shadow. With ripples on (see
// water.glsl), each cell of their grid also gets the height of the water's top there (shadow_water), or how low and
// how high anything else that casts a shadow reaches there (mobs, you, boats, ships); the world's blocks mark nothing.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/water.glsl"

uniform sampler2D gtexture;
uniform float alphaTestRef;

in vec2 v_uv;
in float v_alpha;

#ifdef LIT_WATER_RIPPLES
uniform vec3 cameraPositionFract;
layout(r32ui) uniform uimage2D cg_water_marks_img;
in vec3 v_scene_pos;
#ifdef PROGRAM_SHADOW_WATER
flat in int v_water_top;
#endif
#endif

void main() {
    if (texture(gtexture, v_uv).a * v_alpha < max(alphaTestRef, 0.1)) {
        discard;
    }
#if defined LIT_WATER_RIPPLES && (defined PROGRAM_SHADOW_WATER || !defined PROGRAM_SHADOW_TERRAIN)
    ivec2 cell = water_cell(v_scene_pos, cameraPositionFract);
    if (all(greaterThanEqual(cell, ivec2(0))) && all(lessThan(cell, ivec2(water_sim_size)))) {
        uint height = water_height_mark(v_scene_pos.y);
#ifdef PROGRAM_SHADOW_WATER
        if (v_water_top != 0) {
            imageAtomicMax(cg_water_marks_img, cell, height);
        }
#else
        imageAtomicMax(cg_water_marks_img, cell + ivec2(0, water_sim_size), 65536u - height);
        imageAtomicMax(cg_water_marks_img, cell + ivec2(0, 2 * water_sim_size), height);
#endif
    }
#endif
}

// The shadow map's depth: what a texture cuts out (leaves, grass, fences) casts no shadow, and nothing translucent
// casts one (water and stained glass only mark, below). With ripples on (see water.glsl), each cell of their grid
// also gets the height of the water's top there (shadow_water), or how low and how high anything else that casts a
// shadow reaches there (mobs, you, boats, ships); the world's blocks mark nothing. With bounce light on (see
// light_volume.glsl), the world's blocks leave their colour and facing in shadowcolor0 and shadowcolor1 for the
// bounce light's source (light_bounce.csh); everything else leaves black there, and bounces nothing.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/water.glsl"
#include "/lib/gbuffer.glsl"
#include "/lib/color.glsl"

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

#ifdef LIT_GI
#if defined PROGRAM_SHADOW_TERRAIN && !defined PROGRAM_SHADOW_WATER
in vec3 v_tint;
in vec3 v_normal;
#endif
/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 shadow_color;  // rgb the square root of the linear colour, a 1 (drawn)
layout(location = 1) out vec4 shadow_facing; // rg the world-space normal (octahedral)
#endif

void main() {
    vec4 texel = texture(gtexture, v_uv);
    if (texel.a * v_alpha < max(alphaTestRef, 0.1)) {
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
#ifdef PROGRAM_SHADOW_WATER
    discard;
#elif defined LIT_GI
#ifdef PROGRAM_SHADOW_TERRAIN
    shadow_color = vec4(sqrt(srgb_to_linear(texel.rgb * v_tint)), 1.0);
    shadow_facing = vec4(encode_normal(v_normal), 0.0, 1.0);
#else
    shadow_color = vec4(0.0, 0.0, 0.0, 1.0);
    shadow_facing = vec4(0.5, 0.5, 0.0, 1.0);
#endif
#endif
}

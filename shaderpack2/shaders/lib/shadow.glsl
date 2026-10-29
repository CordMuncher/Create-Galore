// The sun's (or the moon's) shadow map: drawn by program/shadow.*, read here.
//
// The map is squeezed towards the middle (a radial warp), so it has the most texels around you and fewer far
// away, and its depth is squeezed too, so it reaches from deep caves to the build limit. shadowtex0 holds the
// nearest of everything, shadowtex1 only what light can't pass (no water or glass), and shadowcolor0 the colour of
// the nearest see-through thing over the light-blocking one (rgb, sRGB; a below a half for water, whose depth then
// absorbs the light, see shadow_tint).

#if !defined LIB_SHADOW
#define LIB_SHADOW

#include "/lib/settings.glsl"
#include "/lib/common.glsl"

const float shadow_warp = 0.85;
const float shadow_depth_scale = 0.25;

// From the shadow camera's clip space (before the warp) to the map's: texels gather in the middle.
vec3 shadow_warp_clip(vec3 clip) {
    float r = length(clip.xy);
    clip.xy /= r * shadow_warp + (1.0 - shadow_warp);
    clip.z *= shadow_depth_scale;
    return clip;
}

// How many map texels one block covers at a distance r (0..1 of the map's half width) from the middle: the warp's
// slope there.
float shadow_texels_per_block(float r) {
    float k = r * shadow_warp + (1.0 - shadow_warp);
    return float(shadowMapResolution) * 0.5 / shadowDistance * (1.0 - shadow_warp) / (k * k);
}

#if !defined PROGRAM_SHADOW

uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
// The depth compared by the hardware (2 x 2 texels each look-up), and plain (iris.features SEPARATE_HARDWARE_SAMPLERS).
uniform sampler2DShadow shadowtex0HW;
uniform sampler2DShadow shadowtex1HW;
uniform sampler2D shadowtex0;
uniform sampler2D shadowcolor0;

// Where a point (scene space) falls in the map: xy 0..1, z its depth there, w how far from the middle (0..1).
vec4 shadow_coord(vec3 scene_pos) {
    vec3 clip = (shadowProjection * vec4((shadowModelView * vec4(scene_pos, 1.0)).xyz, 1.0)).xyz;
    float r = length(clip.xy);
    clip = shadow_warp_clip(clip);
    return vec4(clip * 0.5 + 0.5, r);
}

// The direction towards the light in the map's own warped space doesn't matter here: the normal offset (in blocks)
// moves the point out of its surface before it is looked up, more far away where texels are bigger.
vec3 shadow_offset_point(vec3 scene_pos, vec3 normal, vec3 light_dir) {
    vec3 clip = (shadowProjection * vec4((shadowModelView * vec4(scene_pos, 1.0)).xyz, 1.0)).xyz;
    float texel = 1.0 / shadow_texels_per_block(min(length(clip.xy), 1.0));
    float facing = dot(normal, light_dir);
    // Further out on surfaces turned away from the light (where acne starts), less on those facing it.
    float offset = texel * (1.2 + 1.5 * (1.0 - clamp(facing, 0.0, 1.0)));
    return scene_pos + normal * offset;
}

// A fixed pattern of taps (no noise from frame to frame, so the edges stand still), each one a 2 x 2 compare by the
// hardware.
const int shadow_taps = 12;
const vec2 shadow_disk[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598)
);

// How much of the light reaches a point (0..1), and its colour through glass and water. radius: the filter's
// width in texels at the middle of the map.
vec3 shadow_light(vec3 scene_pos, float radius) {
    vec4 c = shadow_coord(scene_pos);
    if (any(lessThan(c.xy, vec2(0.0))) || any(greaterThan(c.xy, vec2(1.0))) || c.z > 1.0) {
        return vec3(1.0);
    }
    float texel = 1.0 / float(shadowMapResolution);
    // Wider far out, where the map is coarser, so the edge doesn't turn into stairs.
    float r = radius * texel * mix(1.0, 2.0, c.w);
    float bias = texel * 0.5 * shadow_depth_scale;
    float solid = 0.0;
    float all_of = 0.0;
    for (int i = 0; i < shadow_taps; i++) {
        vec3 p = vec3(c.xy + shadow_disk[i] * r, c.z - bias);
        solid += texture(shadowtex1HW, p);
        all_of += texture(shadowtex0HW, p);
    }
    solid /= float(shadow_taps);
    all_of /= float(shadow_taps);
    if (solid - all_of < 0.01) {
        return vec3(solid);
    }
    // Some of it comes through something see-through: its colour, and for water, its depth.
    vec4 tint = texture(shadowcolor0, c.xy);
    vec3 color = srgb_to_linear(tint.rgb);
    if (tint.a < 0.5) {
        float surface = texture(shadowtex0, c.xy).r;
        float depth = max(c.z - surface, 0.0) * 2.0 / shadow_depth_scale / shadowProjection[2][2] * -1.0;
        color *= exp(-vec3(0.28, 0.07, 0.04) * abs(depth));
    }
    return vec3(all_of) + (solid - all_of) * color;
}

#endif

#endif

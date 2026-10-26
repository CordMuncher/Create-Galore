// The sun's and the moon's shadows in the overworld: Iris's shadow map, drawn from the light without any warping (see
// shadow.vsh), so every texel covers the same ground and the shadows hold still as you move. Soft the way the sun's
// are: sharp where a thing meets its shadow and softer the farther the light falls past it (the penumbra grows with
// the distance from what casts it, found by a search of the shadow map around the point first). Past the shadow
// distance it fades into the light Minecraft's sky light levels give.
//
// Translucent blocks cast no shadow, but water does draw into the shadow map (shadowtex0 has everything, shadowtex1
// only what is opaque), so the light knows how deep under water a point lies (see shadow_water_depth).

#if !defined LIB_SHADOW
#define LIB_SHADOW

#include "/lib/settings.glsl"

#if defined LIT_WORLD && defined SHADOWS
#define SHADOW_MAP

uniform sampler2DShadow shadowtex1HW; // what is opaque, with hardware depth compares (filtered between texels)
uniform sampler2D shadowtex1;         // the same as plain depth, for the search
uniform sampler2D shadowtex0;         // everything, the water's surface too
uniform sampler2D shadowcolor1;       // b 1 where water lies over what is opaque, a 0.5 leaves and plants (shadow.fsh)
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

const float shadow_texel = 2.0 * shadowDistance / float(shadowMapResolution); // in blocks

// The light's size as the penumbra sees it: the tangent of its angular radius. The sun's own is 0.27°; Shadow
// Softness makes it look bigger, for softer shadows away from what casts them (0: the sun as it is).
const float shadow_light_radius = 0.0047 * (1.0 + 4.0 * SHADOW_SOFTNESS);
// How far past what casts it a shadow is looked for (blocks): the widest penumbra the search can find.
const float shadow_search_distance = 24.0;

#if SHADOW_QUALITY >= 4
const int shadow_search_taps = 16;
const int shadow_filter_taps = 32;
#elif SHADOW_QUALITY == 3
const int shadow_search_taps = 12;
const int shadow_filter_taps = 20;
#elif SHADOW_QUALITY == 2
const int shadow_search_taps = 8;
const int shadow_filter_taps = 12;
#else
const int shadow_search_taps = 6;
const int shadow_filter_taps = 8;
#endif

// Blocks per unit of the shadow map's depth (0..1): its projection is orthographic.
float shadow_depth_blocks() {
    return 2.0 / abs(shadowProjection[2][2]);
}

// A point (scene space) in the shadow map: xy where (0..1), z its depth.
vec3 shadow_coord(vec3 scene_pos) {
    vec4 shadow_view = shadowModelView * vec4(scene_pos, 1.0);
    return (shadowProjection * shadow_view).xyz * 0.5 + 0.5;
}

// The i-th of n points spread evenly over a disc of radius 1 (Vogel's spiral), turned by angle.
vec2 vogel_disc(int i, int n, float angle) {
    float r = sqrt((float(i) + 0.5) / float(n));
    float a = float(i) * 2.39996323 + angle;
    return vec2(cos(a), sin(a)) * r;
}

// How much of the light reaches a point (scene space), 0..1, or past the shadow map what the caller says instead
// (beyond). normal: world space, the surface's (a plant's faces the light). noise: 0..1, a different one each pixel
// and frame, which turns the taps (the upscaler evens it out).
float shadow_visibility(vec3 scene_pos, vec3 normal, vec3 light_dir, float beyond, float noise) {
    float n_dot_l = dot(normal, light_dir);
    // Lifted off its own surface by about a texel, more where the light grazes it.
    float lift = shadow_texel * (0.5 + 1.0 * clamp(1.0 - abs(n_dot_l), 0.0, 1.0));
    vec3 pos = scene_pos + normal * lift * (n_dot_l >= 0.0 ? 1.0 : -1.0);
    vec3 coord = shadow_coord(pos);
    float edge = max(length(scene_pos.xz), abs(scene_pos.y)) / shadowDistance;
    float fade = smoothstep(0.85, 1.0, edge);
    if (fade >= 1.0 || any(lessThan(coord.xy, vec2(0.0))) || any(greaterThan(coord.xy, vec2(1.0))) || coord.z >= 1.0) {
        return beyond;
    }

    // The surface's slope in the shadow map, so taps away from the point compare with the surface's depth there, not
    // the point's (no shadow of a surface on itself, however wide the filter).
    vec3 n_shadow = mat3(shadowModelView) * normal;
    vec2 depth_slope = -vec2(shadowProjection[2][2] / shadowProjection[0][0], shadowProjection[2][2] / shadowProjection[1][1])
            * n_shadow.xy / max(abs(n_shadow.z), 0.25) * (n_shadow.z >= 0.0 ? 1.0 : -1.0);
    const float bias = 0.00002;

    float depth_blocks = shadow_depth_blocks();
    float angle = noise * 6.2831853;
    float texel_uv = 1.0 / float(shadowMapResolution);

    // The search: how far, on average, the things that shadow the point are above it (blocks).
    float search_radius = shadow_search_distance * shadow_light_radius / (2.0 * shadowDistance);
    float blocker_sum = 0.0;
    float blockers = 0.0;
    for (int i = 0; i < shadow_search_taps; i++) {
        vec2 offset = vogel_disc(i, shadow_search_taps, angle) * search_radius;
        float d = texture(shadowtex1, coord.xy + offset).r;
        float receiver = coord.z + dot(depth_slope, offset) - bias;
        if (d < receiver) {
            blocker_sum += receiver - d;
            blockers += 1.0;
        }
    }
    if (blockers < 0.5) {
        return mix(1.0, beyond, fade); // nothing near: lit
    }
    float blocker_distance = blocker_sum / blockers * depth_blocks;
    // The penumbra's radius in the shadow map: the light's size over that distance, at least a texel or so (finer
    // than that the shadow map can't tell).
    float radius = max(blocker_distance * shadow_light_radius / (2.0 * shadowDistance), 0.75 * texel_uv);

    float sum = 0.0;
    for (int i = 0; i < shadow_filter_taps; i++) {
        vec2 offset = vogel_disc(i, shadow_filter_taps, angle) * radius;
        sum += texture(shadowtex1HW, vec3(coord.xy + offset, coord.z + dot(depth_slope, offset) - bias));
    }
    // The shadow map's edge fades into what is beyond it.
    return mix(sum / float(shadow_filter_taps), beyond, fade);
}

// How much of the light gets through to a point (scene space) when what is nearest the light above it is leaves or
// plants (shadowcolor1's a, see shadow.fsh): less the further the light has come through them, depth blocks for
// each e-fold. 0 under anything else (and past the shadow map).
float shadow_through(vec3 scene_pos, float depth) {
    vec3 coord = shadow_coord(scene_pos);
    if (any(lessThan(coord, vec3(0.0))) || any(greaterThan(coord, vec3(1.0)))) {
        return 0.0;
    }
    ivec2 texel = ivec2(coord.xy * float(shadowMapResolution));
    float kind = texelFetch(shadowcolor1, texel, 0).a;
    if (kind > 0.75) {
        return 0.0; // something solid
    }
    float through = max(coord.z - texelFetch(shadowtex1, texel, 0).r, 0.0) * shadow_depth_blocks();
    return exp(-max(through - 0.25, 0.0) / depth);
}

// How deep under water a point (scene space) lies towards the light (blocks): from the water's surface the shadow map
// has above it, if what is above it is water and nothing opaque; 0 elsewhere (and past the shadow map).
float shadow_water_depth(vec3 scene_pos) {
    vec3 coord = shadow_coord(scene_pos);
    if (any(lessThan(coord, vec3(0.0))) || any(greaterThan(coord, vec3(1.0)))) {
        return 0.0;
    }
    ivec2 texel = ivec2(coord.xy * float(shadowMapResolution));
    float translucent = texelFetch(shadowtex0, texel, 0).r;
    float water = texelFetch(shadowcolor1, texel, 0).b;
    if (water < 0.5 || translucent >= coord.z) {
        return 0.0;
    }
    return (coord.z - translucent) * shadow_depth_blocks();
}
#endif

#endif

// The sun's and the moon's shadows in the overworld: Iris's shadow map, drawn from the light without any warping (see
// shadow.vsh), read with hardware depth compares over a fixed disc of taps for soft edges. Nothing in it changes from
// frame to frame, so the upscaler sees the same shadow every frame. Past the shadow distance it fades into the
// light Minecraft's sky light levels give.

#if !defined LIB_SHADOW
#define LIB_SHADOW

#include "/lib/settings.glsl"

#if defined LIT_WORLD && defined SHADOWS
#define SHADOW_MAP

uniform sampler2DShadow shadowtex1; // what is opaque (translucent blocks cast no shadow yet)
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

const float shadow_texel = 2.0 * shadowDistance / float(shadowMapResolution); // in blocks

// Fixed taps over a disc (Vogel's spiral), so the penumbra is the same in every frame.
const int shadow_taps = 12;

// How much of the light reaches a point (scene space), 0..1, or past the shadow map what the caller says instead
// (beyond). normal: world space, to lift the point off its own surface by about a texel (more where the light grazes
// it).
float shadow_visibility(vec3 scene_pos, vec3 normal, vec3 light_dir, float beyond) {
    float n_dot_l = dot(normal, light_dir);
    float lift = shadow_texel * (0.6 + 1.4 * clamp(1.0 - abs(n_dot_l), 0.0, 1.0));
    vec3 pos = scene_pos + normal * lift * (n_dot_l >= 0.0 ? 1.0 : -1.0);
    vec4 shadow_view = shadowModelView * vec4(pos, 1.0);
    vec3 shadow_ndc = (shadowProjection * shadow_view).xyz;
    vec3 coord = shadow_ndc * 0.5 + 0.5;
    float edge = max(length(scene_pos.xz), abs(scene_pos.y)) / shadowDistance;
    float fade = smoothstep(0.85, 1.0, edge);
    if (fade >= 1.0 || any(lessThan(coord.xy, vec2(0.0))) || any(greaterThan(coord.xy, vec2(1.0))) || coord.z >= 1.0) {
        return beyond;
    }
    float radius = (0.5 + 1.5 * SHADOW_SOFTNESS) / float(shadowMapResolution);
    float sum = 0.0;
    for (int i = 0; i < shadow_taps; i++) {
        float r = sqrt((float(i) + 0.5) / float(shadow_taps));
        float a = float(i) * 2.39996323;
        vec2 offset = vec2(cos(a), sin(a)) * r * radius;
        sum += texture(shadowtex1, vec3(coord.xy + offset, coord.z - 0.00002));
    }
    // The shadow map's edge fades into what is beyond it.
    return mix(sum / float(shadow_taps), beyond, fade);
}
#endif

#endif

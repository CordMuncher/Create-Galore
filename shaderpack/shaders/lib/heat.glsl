// The heat shimmer over the Nether's lava (see settings.glsl): the hot air rising off it bends what is seen through
// it, in slow wobbles that drift upwards. Drawn in the water pass (water_body.fsh), the last before the frame goes to
// DLSS or FSR: frame generation takes the frame from there and must find nothing moved about after it (see
// composite1.fsh), and the upscaler blends the wobble over frames as the eye would.
//
// How hot the air along a pixel's ray is comes from the light volume (see light_volume.glsl): near lava its light is
// strong and the lava's own deep orange (torches and fire are yellower), and hot air rises, so the air a few blocks
// over that is hot too. Past the volume, the air low over the lava seas is.
//
// The program says what to read before including this: HEAT_DEPTH, the depth of everything drawn (and declares
// gbufferProjectionInverse).

#if !defined LIB_HEAT
#define LIB_HEAT

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/light_volume.glsl"

uniform sampler3D cg_cumulus_detail2_tex; // the clouds' detail noise (see clouds.glsl), for the wobbles

// How far along the ray the shimmer looks for hot air (blocks), and in how many steps.
const float heat_reach = 48.0;
const int heat_steps = 6;
// How far the wobbles move a pixel at most (render pixels at 1080 rows), where the ray goes through a lot of hot air.
const float heat_amplitude = 2.5;

// How hot the air is at a point (scene space), 0..1.
float heat_at(vec3 p) {
    float y = p.y + cameraPosition.y;
    // Low over the lava seas.
    float heat = 0.5 * smoothstep(nether_lava_sea + 16.0, nether_lava_sea + 1.0, y);
#ifdef LIT_COLORED_LIGHT
    // Near lava, and a few blocks over it, by the light volume.
    vec3 at;
    float fade = light_volume_at(p, vec3(0.0), 0.0, at);
    if (fade > 0.0) {
        float near = 0.0;
        for (int i = 0; i < 2; i++) {
            vec3 light = light_volume_sample(at - vec3(0.0, 3.0 * float(i), 0.0), section_color);
            float lava = smoothstep(0.55, 0.42, light.g / max(light.r, 1e-4)) * smoothstep(0.3, 0.15, light.b / max(light.r, 1e-4));
            near = max(near, lava * smoothstep(9.0 / 15.0, 14.0 / 15.0, light.r));
        }
        heat = mix(heat, near, fade);
    }
#endif
    return heat;
}

// Where to read the frame for a render pixel (texel, point its unjittered centre), shimmering (render pixels).
vec2 heat_shimmer(ivec2 texel, vec2 point) {
    vec2 at = vec2(texel) + 0.5;
    float depth = texelFetch(HEAT_DEPTH, texel, 0).r;
    if (HEAT_SHIMMER <= 0.0 || depth < 0.5 + 0.5 * MC_HAND_DEPTH) {
        return at; // (not the hand)
    }
    vec2 ndc = point / render_size() * 2.0 - 1.0;
    vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec3 view = v.xyz / v.w;
    float dist = depth >= 1.0 ? heat_reach : min(length(view), heat_reach);
    vec3 dir = mat3(gbufferModelViewInverse) * normalize(view);

    // The hot air along the ray, in blocks of it.
    float hot = 0.0;
    float step_length = dist / float(heat_steps);
    for (int i = 0; i < heat_steps; i++) {
        hot += heat_at(dir * (step_length * (float(i) + 0.5))) * step_length;
    }
    float amount = 1.0 - exp(-hot / 6.0);
    if (amount < 0.01) {
        return at;
    }

    // The wobbles: noise over the directions you look in (so they hold still as you turn), rising slowly.
    float t = frameTimeCounter;
    vec3 q = dir * 3.5 + vec3(0.013 * t, -0.05 * t, 0.007 * t);
    vec2 wobble = vec2(texture(cg_cumulus_detail2_tex, q).r, texture(cg_cumulus_detail2_tex, q + vec3(0.37, 0.11, 0.53)).r);
    wobble = (wobble - 0.5) * 2.0;
    vec2 moved = at + wobble * (amount * heat_amplitude * HEAT_SHIMMER * render_size().y / 1080.0);
    moved = clamp(moved, vec2(0.5), render_size() - 0.5);

    // Never pull what is nearer (or the hand) over what is behind it: only what the hot air lies in front of bends.
    float there = texelFetch(HEAT_DEPTH, ivec2(moved), 0).r;
    if (there < 0.5 + 0.5 * MC_HAND_DEPTH) {
        return at;
    }
    if (there < 1.0) {
        vec4 w = gbufferProjectionInverse * vec4(moved / render_size() * 2.0 - 1.0, there * 2.0 - 1.0, 1.0);
        if (-w.z / w.w < 0.95 * -view.z) {
            return at;
        }
    }
    return moved;
}

#endif

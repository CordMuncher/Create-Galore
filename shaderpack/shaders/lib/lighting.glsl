// Light on the overworld's surfaces, in real units (see globals.glsl): the sun or the moon, the sky all around, and
// the blocks' own light. Minecraft's light levels say how much of the sky a surface sees (sky light) and how much
// light torches and every other light-giving block, modded ones too, put on it (block light), each through
// Minecraft's own brightness curve. Shared by the deferred pass (opaque surfaces) and the translucent programs.

#if !defined LIB_LIGHTING
#define LIB_LIGHTING

#include "/lib/settings.glsl"
#include "/lib/globals.glsl"
#include "/lib/shadow.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

uniform float screenBrightness;     // Minecraft's Brightness setting, 0 (Moody) .. 1 (Bright)
uniform float nightVision;
uniform float darknessLightFactor;  // the Darkness effect's pulse

// Torchlight: a warm 3000 K or so, luminance 1. A light level 15 surface gets light_block_lux.
const vec3 torch_color = vec3(1.453, 0.916, 0.494);
const float light_block_lux = 0.012;
// What a surface gets with no light at all, so caves aren't pure black (Minecraft mixes its lightmap a little towards
// grey); the Brightness setting raises it as it raises Minecraft's.
const float light_floor_lux = 0.00008;

// Minecraft's brightness for a light level (0..1 for 0..15): its lightmap's curve.
float light_curve(float level) {
    return level / (4.0 - 3.0 * level);
}

// The sky's light falling on a surface facing n: exact for a block's six faces (see frame_globals.csh), blended
// between them for any other direction.
vec3 sky_irradiance(vec3 n) {
    vec3 w = n * n / max(dot(n, n), 1e-8);
    return w.x * global(G_SKY_CUBE + (n.x >= 0.0 ? 0 : 1)).rgb
            + w.y * global(G_SKY_CUBE + (n.y >= 0.0 ? 2 : 3)).rgb
            + w.z * global(G_SKY_CUBE + (n.z >= 0.0 ? 4 : 5)).rgb;
}

// The light from the blocks and the floor, for a block light level (0..1).
vec3 block_irradiance(float block) {
    vec3 light = torch_color * light_block_lux * light_curve(block);
    vec3 floor_light = vec3(light_floor_lux * (1.0 + 7.0 * screenBrightness));
    // Night vision lights everything up, as Minecraft's does.
    floor_light += vec3(light_floor_lux * 400.0 * nightVision);
    return light + floor_light;
}

// How much of the sun's (or the moon's) light a surface at scene_pos gets: the shadow map where it reaches, else its
// sky light (a surface that sees little of the sky is under something). shadowed: false where the shadow map doesn't
// reach (Distant Horizons' terrain).
float direct_visibility(vec3 scene_pos, vec3 normal, float sky, bool shadowed) {
    // Under a roof or deep in a cave the light can't reach, whatever the shadow map says at its edges.
    float by_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, sky);
#ifdef SHADOW_MAP
    if (shadowed) {
        return shadow_visibility(scene_pos, normal, g_light_dir.xyz, by_sky) * smoothstep(1.0 / 15.0, 4.0 / 15.0, sky);
    }
#endif
    return by_sky;
}

// The light a surface reflects towards the eye (kcd/m², not yet exposed): albedo linear, normal world space, light
// the block and sky light levels (0..1).
vec3 light_surface(vec3 albedo, vec3 normal, vec2 light, vec3 scene_pos, bool plant, bool shadowed) {
    // A plant is lit through from whichever side the light is on, as if it faced it (a little less when it's low).
    vec3 n = plant ? vec3(0.0, 1.0, 0.0) : normal;
    float n_dot_l = plant ? 0.4 + 0.6 * max(g_light_dir.y, 0.0) : max(dot(normal, g_light_dir.xyz), 0.0);
    vec3 direct = vec3(0.0);
    if (n_dot_l > 0.0) {
        direct = g_light_color.rgb * n_dot_l * direct_visibility(scene_pos, plant ? g_light_dir.xyz : normal, light.y, shadowed);
    }
    vec3 sky = sky_irradiance(n) * light_curve(light.y);
    vec3 irradiance = direct + sky + block_irradiance(light.x);
    return albedo / PI * irradiance * (1.0 - 0.9 * darknessLightFactor);
}

#endif

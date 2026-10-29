// How a surface's light comes together (every lit program: the deferred pass for the G-buffer, the forward programs
// for water, glass and particles).
//
// The light is mixed in a square-root space: sqrt(shade^2 * (block + (sun + sky)^2 + minimum) + glow^2). Sun and sky
// light come out about as they went in; block light goes in unsquared, so it comes out square-rooted: dim block
// light is lifted a lot and strong block light is squeezed. With the log compression it already has (see
// compress_light), that is what makes the coloured light read strong: dim, far light still shows its colour, and
// many lights never blow out. Inside the voxel box Minecraft's own block light is replaced by the traced one, not
// added to, so unlit corners stay dark; outside it, and for Distant Horizons, Minecraft's levels stand in.

#if !defined LIB_LIGHTING
#define LIB_LIGHTING

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/globals.glsl"
#include "/lib/blocks.glsl"

// The faint cool light where no sky light reaches (deep caves), so they never go fully black.
vec3 minimum_light(float sky) {
    return vec3(0.010, 0.012, 0.015) * CAVE_BRIGHTNESS * (1.0 - sky);
}

// Minecraft's own block light (0..1) as the traced light would give it from a torch: for the edge of the voxel box,
// Distant Horizons, and wherever the traced light has nothing.
vec3 vanilla_block_light(float level) {
    float distance_from_torch = (1.0 - level) * 15.0 + 0.5;
    vec4 torch = light_kind(0);
    float reach = light_reach(torch.a) + 1.0;
    return 4.0 * compress_light(torch.rgb * light_brightness(torch.a) * light_falloff(distance_from_torch, reach))
            * step(0.01, level);
}

// The sky's light on a surface facing n, out in the open.
vec3 sky_light_on(vec3 n) {
    vec3 side = g_sky_side.rgb;
    return n.y >= 0.0 ? mix(side, g_sky_up.rgb, n.y) : mix(side, g_sky_down.rgb, -n.y);
}

// Minecraft's sky light level (0..1) as a share of the open sky's light.
float vanilla_sky_share(float sky) {
    return smoothstep(0.0, 1.0, sky) * sky;
}

// A little shading by which way a face turns, so block sides read apart in any light.
float side_shade(vec3 n) {
    return n.y >= 0.0 ? mix(0.88, 1.0, n.y) * (1.0 - 0.06 * abs(n.x)) : mix(0.88, 0.72, -n.y);
}

struct LightParts {
    vec3 sun;     // the sun's (or moon's) light that reaches the surface, facing included
    vec3 sky;     // the sky and bounce light
    vec3 block;   // block light, compressed (see compress_light), times 4
    vec3 minimum;
    float shade;  // ambient occlusion and side shading
    float glow;   // the surface's own light (hot texels)
};

// The mix. Returns the light on the surface: times its colour for what leaves it.
vec3 mix_light(LightParts p) {
    vec3 sun_sky = p.sun + p.sky;
    return sqrt(max(sqr(p.shade) * (p.block + sun_sky * sun_sky + p.minimum), 0.0) + sqr(p.glow));
}

// How the sun lights a face: a little past side-on still catches some (the light wraps), so block sides don't go
// black when the sun is low.
float sun_facing(vec3 n, vec3 light_dir) {
    float d = dot(n, light_dir);
    return clamp((d + 0.15) / 1.15, 0.0, 1.0);
}

// GGX highlight (per unit of light reaching a surface), for a light from l, seen from v.
float ggx_specular(vec3 n, vec3 v, vec3 l, float smoothness, float f0) {
    float roughness = max(sqr(1.0 - smoothness), 0.02);
    float a2 = roughness * roughness;
    vec3 h = normalize(l + v);
    float nh = max(dot(n, h), 0.0);
    float nl = max(dot(n, l), 0.0);
    float nv = max(dot(n, v), 1e-3);
    float vh = max(dot(v, h), 0.0);
    float d = a2 / (PI * sqr(nh * nh * (a2 - 1.0) + 1.0));
    float k = roughness * 0.5;
    float g = nl / (nl * (1.0 - k) + k) / (nv * (1.0 - k) + k);
    float f = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    return d * g * f * 0.25 * PI;
}

#endif

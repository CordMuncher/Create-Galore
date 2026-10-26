// The water's body in the lit overworld, for the composite pass that draws water (water_body.fsh): what the water
// between two points of a ray lets through (see medium.glsl), and the light it scatters towards the eye there. That
// light is lit where it is, not where you are: the sun's (or the moon's) through the water above each point and
// wherever the shadow map has nothing in its way (shafts of light under the waves, a boat's shadow in the water), the
// sky's coming down from the surface, and the blocks' own light around their lights (a glow around a sea lantern).

#if !defined LIB_WATER_BODY
#define LIB_WATER_BODY

#include "/lib/settings.glsl"
#include "/lib/lighting.glsl"
#include "/lib/medium.glsl"

// Steps along a ray through water: more for the long rays under water than for the water under a surface you look
// down at.
#if SHADOW_QUALITY >= 4
const int water_steps_inside = 32;
#elif SHADOW_QUALITY == 3
const int water_steps_inside = 24;
#else
const int water_steps_inside = 16;
#endif
// Past this much water nothing gets through (blocks): a ray goes no further.
const float water_ray_limit = 96.0;

// Henyey-Greenstein's phase function: water scatters the light mostly onwards, a little all around.
float water_phase(float cos_theta) {
    const float g = 0.75;
    float hg = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cos_theta, 1.5));
    return mix(1.0 / (4.0 * PI), hg, 0.7);
}

// The light at a point under water (scene space) that the water there scatters, before the phase: the sun's (or the
// moon's) through what is above it (rgb), and the light all around (the sky's from the surface, the blocks').
void water_light_at(vec3 p, out vec3 direct, out vec3 around) {
    float lit = 1.0;
    float above = 0.0;
#ifdef SHADOW_MAP
    vec3 coord = shadow_coord(p);
    if (all(greaterThan(coord, vec3(0.0))) && all(lessThan(coord, vec3(1.0)))) {
        lit = texture(shadowtex1HW, vec3(coord.xy, coord.z - 0.00002));
        // How far up towards the light the water (or whatever covers it) begins.
        above = max(coord.z - texelFetch(shadowtex0, ivec2(coord.xy * float(shadowMapResolution)), 0).r, 0.0)
                * shadow_depth_blocks();
    }
#endif
    above = min(above, water_ray_limit);
    direct = g_light_color.rgb * lit * water_transmittance(above);
    // The sky's light comes down through the water from all over the sky: a longer way than straight down, and less
    // of it under a roof or a boat.
    float below = above * max(g_light_dir.y, 0.2);
    vec3 sky = sky_irradiance(vec3(0.0, 1.0, 0.0)) * water_transmittance(1.5 * below) * (0.35 + 0.65 * lit);
    // A diffuse light from above lights the water with half its irradiance over 2π (scalar irradiance 2E, over 4π).
    around = sky / (2.0 * PI);
#ifdef LIT_COLORED_LIGHT
    // The blocks' light where the volume has it: as bright as torchlight of its level (see block_light_color).
    vec3 at;
    float fade = light_volume_at(p, vec3(0.0), 0.0, at);
    if (fade > 0.0) {
        vec3 light = light_volume_sample(at, section_color);
        float peak = max(light.r, max(light.g, light.b));
        if (peak > 1e-3) {
            around += block_light_tint(light) * light_block_lux * light_curve(peak) * fade / (4.0 * PI) * 4.0;
        }
    }
#endif
}

// The water between a and b blocks along dir from origin (scene space): the light it scatters towards origin
// (kcd/m², not exposed) and what it lets through. noise: 0..1, a different one each pixel and frame, which moves the
// steps (the upscaler evens it out).
void water_ray(vec3 origin, vec3 dir, float a, float b, int steps, float noise, out vec3 light, out vec3 through) {
    through = water_transmittance(b - a);
    light = vec3(0.0);
    float end = min(b, a + water_ray_limit);
    if (end <= a) {
        return;
    }
    float dt = (end - a) / float(steps);
    // What one step lets through, and the share of a step's scattered light that gets out of it (the light scattered
    // along it, fading as it goes).
    vec3 step_through = water_transmittance(dt);
    vec3 step_out = (1.0 - step_through) / water_extinction;
    float phase = water_phase(dot(dir, g_light_dir.xyz));
    vec3 so_far = vec3(1.0);
    for (int i = 0; i < steps; i++) {
        vec3 p = origin + dir * (a + (float(i) + noise) * dt);
        vec3 direct, around;
        water_light_at(p, direct, around);
        light += so_far * step_out * water_scattering * (direct * phase + around);
        so_far *= step_through;
    }
}

#endif

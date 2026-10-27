// Light on the overworld's surfaces, in real units (see globals.glsl): the sun or the moon, the sky all around, the
// sunlight bouncing off the world (see light_volume.glsl), and the blocks' own light. Minecraft's light levels say how
// much of the sky a surface sees (sky light) and how much light torches and every other light-giving block, modded
// ones too, put on it (block light), each through Minecraft's own brightness curve. Shared by the deferred pass
// (opaque surfaces) and the translucent programs.

#if !defined LIB_LIGHTING
#define LIB_LIGHTING

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"
#include "/lib/noise.glsl"
#include "/lib/shadow.glsl"
#ifdef SHADOW_MAP
#include "/lib/water.glsl"
#endif
#include "/lib/light_volume.glsl"
#include "/lib/medium.glsl"
#ifdef LIT_CLOUDS
#include "/lib/clouds.glsl"
#endif

#ifndef PI
#define PI 3.14159265358979
#endif

uniform float screenBrightness;     // Minecraft's Brightness setting, 0 (Moody) .. 1 (Bright)
uniform float nightVision;
uniform float darknessLightFactor;  // the Darkness effect's pulse

// A number 0..1 for this pixel and frame, for what samples a few points of many: the upscaler evens out what it
// leaves (fragment stages only); seed: another number for each use (0..3). Noise Pattern 0: spread evenly over the
// screen and moved on each frame (Jorge Jimenez's interleaved gradient noise, made for TAA). 1: white noise, a hash
// of the pixel and the frame (Jarzynski and Olano's PCG), with no pattern across the screen, which is what NVIDIA
// asks Ray Reconstruction to be given.
float pixel_noise(uint seed) {
#if NOISE_PATTERN == 1
    return hash_to_float(hash_pcg3d(uvec3(uvec2(gl_FragCoord.xy), (uint(frameCounter) << 2u) | seed))).x;
#else
    vec2 p = seed == 0u ? gl_FragCoord.xy : gl_FragCoord.yx + vec2(17.0, 43.0) * float(seed);
    p += 5.588238 * float((frameCounter + 29 * int(seed)) & 63);
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
#endif
}

float pixel_noise() {
    return pixel_noise(0u);
}

// A light level 15 surface gets light_block_lux of torchlight (torch_color, see light_volume.glsl): a little more than
// a real torch gives up close, so a room lit by its lamps reads as lit, not as a dim night.
const float light_block_lux = 0.024;
// What a surface gets with no light at all, so caves aren't pure black (Minecraft mixes its lightmap a little towards
// grey); the Brightness setting raises it as it raises Minecraft's.
const float light_floor_lux = 0.00008;

// Minecraft's brightness for a light level (0..1 for 0..15): its lightmap's curve.
float light_curve(float level) {
    return level / (4.0 - 3.0 * level);
}

// The sky's light for a sky light level (0..1): Minecraft's curve, to the power SKY_FALLOFF.
float sky_light_curve(float level) {
    return pow(light_curve(level), SKY_FALLOFF);
}

// The blocks' light for a block light level (0..1): Minecraft's curve, to the power BLOCK_FALLOFF.
float block_light_curve(float level) {
    return pow(light_curve(level), BLOCK_FALLOFF);
}

// The sky's light falling on a surface facing n: exact for a block's six faces (see frame_globals.csh), blended
// between them for any other direction.
vec3 sky_irradiance(vec3 n) {
    vec3 w = n * n / max(dot(n, n), 1e-8);
    return w.x * global(G_SKY_CUBE + (n.x >= 0.0 ? 0 : 1)).rgb
            + w.y * global(G_SKY_CUBE + (n.y >= 0.0 ? 2 : 3)).rgb
            + w.z * global(G_SKY_CUBE + (n.z >= 0.0 ? 4 : 5)).rgb;
}

// By day the blocks' light is at least this much in the frame (frame units, times BLOCK_LIGHT_DAY): a few times what
// lights the shade by day at the exposure's key, so a lantern visibly lights the shade around it.
const float light_block_day_frame = 16.0;

// The light from the blocks (their colour, luminance 1, see light_volume.glsl) and the floor, for a block light
// level (0..1).
vec3 block_irradiance(float block, vec3 color) {
    float lux = max(light_block_lux, light_block_day_frame * BLOCK_LIGHT_DAY / exposure());
    vec3 light = color * lux * block_light_curve(block);
    vec3 floor_light = vec3(light_floor_lux * (1.0 + 7.0 * screenBrightness));
    // Night vision lights everything up, as Minecraft's does.
    floor_light += vec3(light_floor_lux * 400.0 * nightVision);
    return light + floor_light;
}

// How much of the sun's (or the moon's) light a surface at scene_pos gets: the shadow map where it reaches, and what
// the clouds let through. Past the shadow map (and on Distant Horizons' terrain, shadowed false) Minecraft's own sky
// light sky_mc stands in for it: a surface it says sees little of the sky is under something, which keeps the sun off
// it (the shadow of a floating island falls straight down, though, not away from the sun). sky: the sky light the
// rest of the light goes by, which is more under something high (see sky_grid.glsl); deep in a cave the sun can't
// reach, whatever the shadow map says at its edges.
float direct_visibility(vec3 scene_pos, vec3 normal, float sky, float sky_mc, bool shadowed) {
#ifdef LIT_CLOUDS
    float clouds = cloud_shadow(scene_pos, g_light_dir.xyz);
#else
    float clouds = 1.0;
#endif
    float by_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, sky_mc);
#ifdef SHADOW_MAP
    if (shadowed) {
        return shadow_visibility(scene_pos, normal, g_light_dir.xyz, by_sky, pixel_noise()) * smoothstep(1.0 / 15.0, 4.0 / 15.0, sky)
                * clouds;
    }
#endif
    return by_sky * clouds;
}

// The same, from one sharp look at the shadow map: for the sun's glint on water and polished things, where a soft
// shadow edge would only blur the glint.
float glint_visibility(vec3 scene_pos, vec3 normal, float sky, float sky_mc) {
#ifdef LIT_CLOUDS
    float clouds = cloud_shadow(scene_pos, g_light_dir.xyz);
#else
    float clouds = 1.0;
#endif
    float lit = smoothstep(12.0 / 15.0, 14.5 / 15.0, sky_mc) * clouds;
#ifdef SHADOW_MAP
    vec3 coord = shadow_coord(scene_pos + normal * 0.05);
    if (all(greaterThan(coord, vec3(0.0))) && all(lessThan(coord, vec3(1.0)))) {
        lit = smoothstep(12.0 / 15.0, 14.5 / 15.0, sky) * clouds;
        lit *= lit > 0.0 ? texture(shadowtex1HW, vec3(coord.xy, coord.z - 0.00002)) : 0.0;
    }
#endif
    return lit;
}

// How much of the light all around reaches a surface whose occlusion (the share of the directions around it that
// are open) is ao: more than that, as light bounces between the sides of a crease, the more so the lighter they are
// (Jimenez et al.'s fit, for the surface's own albedo).
vec3 ao_bounced(float ao, vec3 albedo) {
    vec3 a = 2.0404 * albedo - 0.3324;
    vec3 b = -4.7951 * albedo + 0.6417;
    vec3 c = 2.7552 * albedo + 0.6903;
    return max(vec3(ao), ((ao * a + b) * ao + c) * ao);
}

#ifdef SHADOW_MAP
// How far the sun's (or the moon's) light comes through water to reach a point (scene space; blocks along the light,
// 0 where no water lies over it): from the tops of water the shadow pass marks around you, straight above the point
// (see water.glsl); farther out, from the shadow map (see shadow_water_depth).
float water_over(vec3 scene_pos) {
#ifdef LIT_WATER_RIPPLES
    float marked = water_depth_marked(scene_pos);
    if (marked >= 0.0) {
        return marked / max(g_light_dir.y, 0.2);
    }
#endif
    return shadow_water_depth(scene_pos);
}
#endif

// The light a surface reflects towards the eye (kcd/m², not yet exposed): albedo linear, normal world space, light
// the block and sky light levels (0..1). plant: grass, flowers and crops (see MATERIAL_PLANT); foliage: leaves.
// contact: how much of the light the screen says gets past what is right next to the surface (see deferred.fsh), 1
// where only the shadow map says. ao: how open the surface is to the light all around, 0..1 (see deferred.fsh); the
// sun's (or the moon's) isn't touched by it, the shadows see to that. sky_mc: Minecraft's own sky light, where light.y
// is more under something high (see direct_visibility).
vec3 light_surface(vec3 albedo, vec3 normal, vec2 light, vec3 scene_pos, bool plant, bool foliage, bool shadowed,
        float contact, float ao, float sky_mc) {
    // Under water the light has come down through it: the sun's (or the moon's) from the surface the shadow map has
    // above, the sky's from all over the sky, on a longer way down. Minecraft's sky light drops a level for every
    // block of water, which the water's own dimming takes over from.
    vec3 water_direct = vec3(1.0);
    vec3 water_sky = vec3(1.0);
#ifdef SHADOW_MAP
    float water_depth = shadowed ? water_over(scene_pos) : 0.0;
    if (water_depth > 0.0) {
        float below = water_depth * max(g_light_dir.y, 0.2);
        water_direct = water_transmittance(water_depth) * water_caustics(scene_pos, water_depth, length(scene_pos));
        water_sky = water_transmittance(1.5 * below);
        // (Not where Minecraft's sky light doesn't reach at all: a cave under the sea is not the sea floor.)
        light.y = min(light.y + below / 15.0 * smoothstep(0.0, 2.0 / 15.0, light.y), 1.0);
        sky_mc = min(sky_mc + below / 15.0 * smoothstep(0.0, 2.0 / 15.0, sky_mc), 1.0);
    }
#endif
    // A tree's leaves are shaded by the shape of their crown more than by their own faces (see canopy_shape), the way
    // stylised trees are: lit and seeing the sky the way the crown's outside faces there, so a tree reads as one
    // round mass with clumps in it rather than as a heap of cubes; and the deeper inside it, the less sky they see.
    vec3 leaf_normal = normal;
    float crown_sky = 1.0;
#ifdef LIT_VOXELS
    if (foliage && shadowed) {
        vec4 crown = canopy_shape(scene_pos);
        float shaped = smoothstep(0.05, 0.25, length(crown.xyz));
        vec3 m = mix(normal, crown.xyz / max(length(crown.xyz), 1e-6), 0.75 * shaped);
        leaf_normal = dot(m, m) > 1e-4 ? normalize(m) : normal;
        crown_sky = 1.0 - 0.5 * smoothstep(0.4, 0.9, crown.w);
    }
#endif
    // A plant is lit through from whichever side the light is on, as if it faced it (a little less when it's low).
    vec3 n = plant ? vec3(0.0, 1.0, 0.0) : leaf_normal;
    float n_dot_l = plant ? 0.4 + 0.6 * max(g_light_dir.y, 0.0) : max(dot(normal, g_light_dir.xyz), 0.0);
    vec3 direct = vec3(0.0);
    if (foliage || plant) {
        // Leaves and plants let the light through: a quarter of it reaches every side of a leaf, and what lies in
        // the shade of other leaves or blades gets what they let through (less the more of them), greener for it.
        // A tree's leaves let through less than a blade of grass does: the inside of a tree glows a little, a
        // depth of shade under its sunlit top rather than lit up as brightly. A plant is lit as if it faced the
        // light (a little less when it's low).
        float n_l = dot(n, g_light_dir.xyz);
        float facing = plant ? n_dot_l : 0.25 + 0.75 * max(n_l, 0.0);
        float vis = direct_visibility(scene_pos, g_light_dir.xyz, light.y, sky_mc, shadowed);
        float through = 0.0;
#ifdef SHADOW_MAP
        if (shadowed) {
            through = shadow_through(scene_pos, plant ? 0.75 : 1.0) * smoothstep(1.0 / 15.0, 4.0 / 15.0, light.y)
                    * (plant ? 1.0 : 0.3);
        }
#endif
        float glow = clamp(through - vis, 0.0, 1.0) + (plant ? 0.0 : max(-n_l, 0.0) * 0.5);
        vec3 tint = mix(vec3(1.0), clamp(albedo * 2.5, 0.0, 1.0), 0.6 * min(glow, 1.0));
        direct = g_light_color.rgb * water_direct * contact * facing * max(vis, through) * tint;
    } else if (n_dot_l > 0.0 && contact > 0.0) {
        direct = g_light_color.rgb * water_direct * n_dot_l * contact
                * direct_visibility(scene_pos, normal, light.y, sky_mc, shadowed);
    }
    // Under a roof the sky a surface facing up sees is the low sky around it, not the deep blue overhead: as much
    // light, in the colour of the sky's sides.
    vec3 sky = sky_irradiance(n);
    if (foliage) {
        // A tree's leaves get the light all around them more by which way they face: a quarter of the difference
        // between the sky one way and the other more (Ghost of Tsushima turns up which way all its light all around
        // comes from by a quarter). The top of a crown is brighter and its sides and underside darker, so it reads
        // as round from every side, not as evenly lit.
        sky = max(sky + 0.25 * (sky - sky_irradiance(-n)), vec3(0.0));
    }
    float covered = (1.0 - smoothstep(13.0 / 15.0, 1.0, light.y)) * max(n.y, 0.0);
    if (covered > 0.0) {
        vec3 side = 0.25 * (global(G_SKY_CUBE).rgb + global(G_SKY_CUBE + 1).rgb + global(G_SKY_CUBE + 4).rgb
                + global(G_SKY_CUBE + 5).rgb);
        const vec3 luma = vec3(0.2126, 0.7152, 0.0722);
        sky = mix(sky, side * (dot(sky, luma) / max(dot(side, luma), 1e-8)), covered);
    }
    vec3 around = sky * sky_light_curve(light.y) * water_sky * crown_sky
            + block_irradiance(light.x, block_light_color(scene_pos, n));
#ifdef LIT_GI
    around += g_light_color.rgb * bounce_light(scene_pos, n, light.y);
#endif
    vec3 irradiance = direct + around * ao_bounced(ao, albedo);
    return albedo / PI * irradiance * (1.0 - 0.9 * darknessLightFactor);
}

vec3 light_surface(vec3 albedo, vec3 normal, vec2 light, vec3 scene_pos, bool plant, bool shadowed) {
    return light_surface(albedo, normal, light, scene_pos, plant, false, shadowed, 1.0, 1.0, light.y);
}

#endif

// Light for what is drawn after the deferred pass, over the lit scene (water, glass, particles, rain, clouds): the
// same mix as the deferred pass (see lighting.glsl), with the softer per-block light, as these surfaces have no
// G-buffer for the traced per-pixel light.

#if !defined LIB_FORWARD
#define LIB_FORWARD

#include "/lib/lighting.glsl"
#include "/lib/voxel_sample.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"

// The light on a surface at scene_pos facing n. light: Minecraft's block and sky light (0..1); ao: its corner
// shading; sun_shadow_radius: the shadow filter's width in texels (0: no shadow look-up, the sky light decides).
vec3 forward_light(vec3 n, vec3 scene_pos, vec2 light, float ao, float sun_shadow_radius) {
    vec3 p = vox_pos(scene_pos) + n * 0.5;
    float fade = box_fade(p);
    vec4 blk = sample_block_table(cg_blk_tex, p);
    vec4 gi = sample_block_table(cg_gi_tex, p);

    LightParts parts;
    float inside = fade * blk.a;
    parts.block = mix(vanilla_block_light(light.x), blk.rgb * 4.0, inside);
    vec3 open_sky = sky_light_on(n) * vanilla_sky_share(light.y);
    parts.sky = mix(open_sky, max(gi.rgb * GI_STRENGTH, 0.5 * open_sky), fade * gi.a);
    parts.minimum = minimum_light(light.y);
    parts.shade = ao;
    parts.glow = 0.0;

    float facing = sun_facing(n, g_light_dir.xyz);
    vec3 sun = vec3(0.0);
#if defined SUNLIGHT || defined WORLD_END
#if defined SUNLIGHT
    float sky_gate = smoothstep(0.05, 0.3, light.y);
#else
    float sky_gate = 1.0;
#endif
    if (facing > 0.0 && sky_gate > 0.0) {
#ifdef PROGRAM_HAND_WATER
        const bool held = true; // what you hold looks past your own body (see shadow_light)
#else
        const bool held = false;
#endif
        vec3 reach = sun_shadow_radius > 0.0
                ? shadow_light(shadow_offset_point(scene_pos, n, g_light_dir.xyz), sun_shadow_radius, 0.0,
                               sun_beyond_map(light.y), held)
                : vec3(1.0);
        sun = g_light_color.rgb * facing * reach * sky_gate * cloud_shadow(scene_pos);
    }
#endif
    parts.sun = sun;
    return mix_light(parts);
}

#endif

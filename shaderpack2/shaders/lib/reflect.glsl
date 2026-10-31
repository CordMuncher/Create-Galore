// Reflections traced through the block copy (see lib/trace.glsl): the same from wherever you look, and what is off
// screen or behind you too. A reflected ray walks the copy to the first block it meets (part blocks by their real
// shape, see-through pixels let it by); that block is drawn with its own texture where its face meets the ray (the
// Bridge's face maps into the block atlas, lib/world.glsl face_texture; its average colour where it gives none) at
// the mip level the spot's size on screen asks for, and lit as the deferred pass lights the world: the traced block
// light and bounce light around it, the sky light in front of the face, the sun through the shadow map, its own glow.
// A ray that leaves the copy, or reaches as far as it may, sees the sky and its clouds.

#if !defined LIB_REFLECT
#define LIB_REFLECT

#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/world.glsl"
#include "/lib/blocks.glsl"
#include "/lib/forward.glsl"
#include "/lib/sky_mirror.glsl"

// The light on the face a ray hit, lit as lib/lighting.glsl mixes it; albedo is its colour there (linear).
vec3 reflect_hit_light(Hit h, vec3 albedo, float glow) {
    vec3 n = h.normal;
    vec3 scene = h.pos - cameraPositionFract;
    vec3 in_front = h.pos + n * 0.5;
    ivec3 front = ivec3(floor(in_front));
    float sky_light = vox_in_box(front, 0) ? vox_sky(vox_word(front)) : 1.0;

    LightParts parts;
    float fade = box_fade(in_front);
    vec4 blk = sample_block_table(cg_blk_tex, in_front);
    parts.block = blk.rgb * 4.0 * fade * blk.a;
    vec3 open_sky = sky_light_on(n) * vanilla_sky_share(sky_light);
#ifdef GI
    vec4 gi = sample_block_table(cg_gi_tex, in_front);
    parts.sky = mix(open_sky, max(gi.rgb * GI_STRENGTH, 0.5 * open_sky), fade * gi.a);
#else
    parts.sky = open_sky;
#endif
    parts.minimum = minimum_light(sky_light);
    parts.shade = side_shade(n);
    parts.glow = glow * 4.0;

    vec3 sun = vec3(0.0);
#if defined SUNLIGHT || defined WORLD_END
    float facing = sun_facing(n, g_light_dir.xyz);
#if defined SUNLIGHT
    float sky_gate = smoothstep(0.05, 0.3, sky_light);
#else
    float sky_gate = 1.0;
#endif
    if (facing > 0.0 && sky_gate > 0.0) {
        g_water_path = water_path(h.pos + n * 0.05, g_light_dir.xyz, 48);
        vec3 reach = shadow_light(shadow_offset_point(scene, n, g_light_dir.xyz), 1.0, 0.0, sun_beyond_map(sky_light));
        g_water_path = -1.0;
        sun = g_light_color.rgb * facing * reach * sky_gate * cloud_shadow(scene);
    }
#endif
    parts.sun = sun;
    return mix_light(parts);
}

// What a ray that hit a block sees there: its texture, lit. footprint: how wide the spot is that one pixel's look
// covers there, in blocks.
vec3 reflect_hit_color(Hit h, float footprint) {
    uint word = vox_word(h.block);
    uint entry = word & VOX_ENTRY;
    vec3 local = clamp(h.pos - vec3(h.block), 0.0, 1.0);
    vec4 tex;
    if (!(faces_live() && face_texture(entry, h.normal, local, footprint, tex))) {
        tex = vox_color(h.block);
    }
    vec3 albedo = tex.rgb;
    uint w0 = palette_word(entry, 0u);
    float glow = glow_of(albedo, int(w0 & 0xffffu), float((w0 >> 16u) & 15u));
    if (glow > 0.0) {
        // Hot texels burn in a deeper version of their own colour, as in the deferred pass.
        float top = max(max3(albedo), 1e-4);
        albedo = mix(albedo, pow(albedo / top, vec3(1.6)) * top, smoothstep(0.0, 0.5, glow));
    }
    return albedo * reflect_hit_light(h, albedo, glow);
}

// What a reflected ray from start (scene space) going along r sees: the first block in the copy, or the sky. spread:
// how much wider the look gets per block it travels (the screen's pixel size there, and how rough the surface is);
// start_width: how wide it is where it leaves the surface. dist: how far it went (the reach where it saw the sky).
vec3 reflect_world(vec3 start, vec3 r, float start_width, float spread, float reach, int steps, out float dist) {
    vec3 p = vox_pos(start);
    dist = reach;
    if (grid_live() && vox_edge_distance(ivec3(floor(p)), 0) >= 1) {
        Hit h = trace_ray(p, r, reach, steps);
        if (h.hit) {
            dist = h.t;
            return reflect_hit_color(h, start_width + spread * h.t);
        }
    }
    return reflect_sky(r);
}

#endif

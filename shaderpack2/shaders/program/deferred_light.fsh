// The lighting of everything in the G-buffer (and the sky behind it), into colortex0: the traced block light per
// pixel (or per block where a pixel has none), the bounce light, the sun through the shadow map, the sky, ambient
// occlusion, and the hot texels' own glow, mixed as lib/lighting.glsl says; then the highlights on top. Light that
// the world's programs already added to colortex0 (glowing eyes, the enchantment glint) stays on top. The clouds
// (program/clouds.csh) go over the sky, and over the terrain where they start before it.

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/lighting.glsl"
#include "/lib/voxel_sample.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"
#if DEBUG_VIEW == 7 || DEBUG_VIEW == 8 || DEBUG_VIEW == 10 || DEBUG_VIEW == 11 || DEBUG_VIEW == 14
#include "/lib/trace.glsl"
#endif
#if DEBUG_VIEW == 12
#include "/lib/lights.glsl"
#endif

uniform sampler2D colortex0; // light added before this pass (see gbuffers.fsh, LIT_GLOW)

#ifdef VOLUME_CLOUDS
layout(rgba16f) readonly uniform image2D cg_cloud_img; // see clouds.csh
#include "/lib/clouds.glsl"
#endif

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

#ifdef PIXEL_LIGHT
layout(rgba16f) readonly uniform image2D cg_plh_img; // see pixel_light_filter.csh
#endif

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

// Screen-space ambient occlusion: eight looks into the half sphere over the surface, in view space, up to 0.6 of a
// block out; a surface is shaded by how many land just behind something. Only something near counts: a look that
// lands behind a surface far in front of it (the rim of a hole, seen from the side) says nothing about this point,
// and counting it made a hole's floor darken or not with the angle you looked from. Returns what multiplies the
// whole lit colour (linear): the open share to the power 0.88.
float ssao(vec3 view, vec3 view_normal, ivec2 texel) {
#ifdef SSAO
    float noise = fract(ign(vec2(texel)) + float(frameCounter % 64) * GOLDEN);
    vec3 t, b;
    basis(view_normal, t, b);
    float occluded = 0.0;
    const float radius = 0.6;
    for (int i = 0; i < 8; i++) {
        float a = (float(i) + noise) * (TAU * GOLDEN * 2.0);
        float r = sqrt((float(i) + 0.5) / 8.0);
        float h = sqrt(1.0 - r * r);
        vec3 dir = t * (cos(a) * r) + b * (sin(a) * r) + view_normal * h;
        vec3 sp = view + dir * radius * mix(0.25, 1.0, fract(noise + float(i) * 0.37));
        vec4 clip = gbufferProjection * vec4(sp, 1.0);
        vec2 ndc = clip.xy / clip.w;
        vec2 pixel = (ndc * 0.5 + 0.5) * render_size() + vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
        if (any(lessThan(pixel, vec2(0.0))) || any(greaterThanEqual(pixel, render_size()))) {
            continue;
        }
        ivec2 at = ivec2(pixel);
        float d = texelFetch(depthtex0, at, 0).r;
        if (is_hand_depth(d)) {
            continue;
        }
        float surface_z = pixel_to_view(at, d).z;
        float in_front = surface_z - sp.z;
        if (in_front > 0.02) {
            occluded += 1.0 - smoothstep(radius, radius * 1.5, in_front);
        }
    }
    return pow(1.0 - occluded / 8.0, 0.88);
#else
    return 1.0;
#endif
}

// The clouds over what a render pixel shows, `dist` blocks away (cloud_far_sky for the sky). A surface below the
// clouds seen from below them (or above them from above) has none in front of it.
vec3 with_clouds(vec3 color, ivec2 texel, float dist, float surface_y) {
#ifdef VOLUME_CLOUDS
    float bottom = cloud_bottom();
    float y0 = cameraPosition.y;
    if (!clouds_here() || (y0 < bottom && surface_y < bottom) || (y0 > bottom + cloud_depth && surface_y > bottom + cloud_depth)) {
        return color;
    }
    vec4 c = clouds_at(texel, dist).light;
    return color * c.a + c.rgb;
#else
    return color;
#endif
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    if (outside_render_area(gl_FragCoord.xy)) {
        out_color = vec4(0.0);
        return;
    }
#if DEBUG_VIEW == 13
    // The frame's light, at half its value, in four bands from the top: the sun's (or moon's), the sky's on a
    // surface facing up, facing the horizon and facing down.
    int band = clamp(int(4.0 - 4.0 * gl_FragCoord.y / render_size().y), 0, 3);
    vec4 frame_light = band == 0 ? g_light_color : band == 1 ? g_sky_up : band == 2 ? g_sky_side : g_sky_down;
    out_color = vec4(frame_light.rgb * 0.5, 1.0);
    return;
#endif
    float depth = texelFetch(depthtex0, texel, 0).r;
    vec3 view;
    bool lod = false;
    if (depth >= 1.0) {
#ifdef DISTANT_HORIZONS
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
            vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
            view = v.xyz / v.w;
            lod = true;
        }
#endif
        if (!lod) {
            vec3 far_view = pixel_to_view(texel, 1.0);
            vec3 dir = normalize(mat3(gbufferModelViewInverse) * far_view);
            vec3 c = sky(dir, true);
#ifdef VOLUME_CLOUDS
            c = with_clouds(c, texel, cloud_far_sky, dir.y > 0.0 ? 1e6 : -1e6);
#endif
            out_color = vec4(c + texelFetch(colortex0, texel, 0).rgb, 1.0);
            return;
        }
    } else {
        view = pixel_to_view(texel, depth);
    }

    Surface s = read_surface(texel);
    vec3 scene = view_to_scene_pos(view);
    vec3 v = normalize(-scene);
    vec3 n = s.normal;
    vec3 p = vox_pos(scene);
    vec3 in_front = p + s.face * 0.5;
    float fade = lod ? 0.0 : box_fade(in_front);

    // Block light: the traced light of this pixel, or of its block.
    vec4 per_block = fade > 0.0 ? sample_block_table(cg_blk_tex, in_front) : vec4(0.0);
    vec3 block = per_block.rgb;
    float confidence = per_block.a;
    vec3 highlight = vec3(0.0);
#ifdef PIXEL_LIGHT
    ivec2 now = texel + ivec2((frameCounter & 1) * int(viewWidth), 0);
    vec4 traced = imageLoad(cg_plh_img, now);
    if (traced.a > 0.5 && !lod) {
        block = traced.rgb;
        confidence = 1.0;
        highlight = imageLoad(cg_plh_img, now + ivec2(0, imageSize(cg_plh_img).y / 3)).rgb * 4.0;
    }
#endif
    if (s.material == MAT_HAND && fade > 0.0) {
        // What you hold also takes the light all around you, from every side, so it picks up the colour of a portal
        // or lava next to you even where its faces turn away.
        block = max(block, per_block.rgb * 0.7);
        confidence = max(confidence, per_block.a);
    }
    vec3 vanilla_block = vanilla_block_light(s.block);
    LightParts parts;
    parts.block = mix(vanilla_block, block * 4.0, fade * confidence);
    if (s.emission > 0.0) {
        parts.block = max(parts.block, vanilla_block); // a lamp is never darker than Minecraft says on its own face
    }

    // Sky and bounce light.
    vec4 gi = fade > 0.0 ? sample_block_table(cg_gi_tex, in_front) : vec4(0.0);
    vec3 open_sky = sky_light_on(n) * vanilla_sky_share(s.sky);
#ifdef GI
    // Bounce light, never below half the open sky's (a mob's never below all of it: it isn't in the voxel copy, so
    // the nook it stands in shouldn't turn it into a silhouette).
    float gi_floor = s.material == MAT_ENTITY ? 1.0 : 0.5;
    parts.sky = mix(open_sky, max(gi.rgb * GI_STRENGTH, gi_floor * open_sky), fade * gi.a);
#else
    parts.sky = open_sky;
#endif
    parts.minimum = minimum_light(s.sky);

    // The sun (or the moon).
    bool thin = s.material == MAT_PLANT || s.material == MAT_LEAVES;
    vec3 l = g_light_dir.xyz;
    vec3 sun = vec3(0.0);
    vec3 sun_reach = vec3(0.0);
#if defined SUNLIGHT || defined WORLD_END
    float facing = sun_facing(n, l);
    if (thin) {
        facing = mix(facing, 1.0, 0.45);
    } else if (s.material == MAT_ENTITY) {
        facing = 0.25 + 0.75 * max(dot(n, l), 0.0); // mobs: the light wraps further, so they never turn flat black
    }
#if defined SUNLIGHT
    float sky_gate = smoothstep(0.05, 0.3, s.sky);
#else
    float sky_gate = 1.0; // the End has no sky light: its light reaches wherever the shadow map lets it
#endif
    if (facing > 0.0 && sky_gate > 0.0 && !lod) {
        // What you hold and plants get a little softer edge. Leaves let the light through the outside of their
        // clump, and grass doesn't shade itself.
        float radius = (s.material == MAT_HAND || thin ? 2.0 : 1.5) * SHADOW_SOFTNESS;
        float ignore = s.material == MAT_LEAVES ? 1.0 : s.material == MAT_PLANT ? 0.5 : 0.0;
        sun_reach = shadow_light(shadow_offset_point(scene, s.face, l), radius, ignore, sun_beyond_map(s.sky),
                                 s.material == MAT_HAND) * sky_gate * cloud_shadow(scene);
        sun = g_light_color.rgb * facing * sun_reach;
    } else if (lod) {
        sun = g_light_color.rgb * facing * sun_beyond_map(s.sky) * cloud_shadow(scene);
    }
#endif
    parts.sun = sun;

    // Shade: the corners' shading and the face's turn, inside the mix. The screen's ambient occlusion darkens the
    // whole lit colour afterwards (not mobs or the hand, which it would outline).
    float ao = mix(1.0, s.ao, AO_STRENGTH);
    float screen_ao = 1.0;
    if (s.material != MAT_HAND && s.material != MAT_ENTITY && !lod) {
        screen_ao = mix(1.0, ssao(view, mat3(gbufferModelView) * s.face, texel), AO_STRENGTH);
    }
    parts.shade = ao * side_shade(n);
    parts.glow = s.emission * 4.0;

    vec3 light = mix_light(parts);
    vec3 diffuse_color = s.albedo * (1.0 - 0.7 * s.metal);
    // Hot texels burn in a deeper version of their own colour: a torch's pale yellow flame orange, a soul flame cyan.
    if (s.emission > 0.0) {
        float top = max(max3(diffuse_color), 1e-4);
        vec3 deeper = pow(diffuse_color / top, vec3(1.6)) * top;
        diffuse_color = mix(diffuse_color, deeper, smoothstep(0.0, 0.5, s.emission));
    }
    vec3 color = diffuse_color * light;

    // Highlights: every nearby light's, and the sun's. Their strength follows how smooth the surface is: rough blocks
    // (sand, dirt, stone, everything not marked polished or metal) keep only a faint sheen, so a lamp's reflection
    // doesn't slide over them as you move; polished blocks, metal and machines keep theirs.
    vec3 tint = mix(vec3(1.0), s.albedo, s.metal);
    float sheen = s.smoothness * mix(0.15, 1.0, smoothstep(0.15, 0.4, s.smoothness));
    color += highlight * tint * sheen;
#if defined SUNLIGHT || defined WORLD_END
    if (!lod && max3(sun_reach) > 0.0 && s.smoothness > 0.2) {
        color += g_light_color.rgb * sun_reach * tint * ggx_specular(n, v, l, s.smoothness, mix(0.04, 0.6, s.metal));
    }
#endif
    color *= screen_ao;

#if DEBUG_VIEW == 1
    color = s.albedo;
#elif DEBUG_VIEW == 2
    color = srgb_to_linear(n * 0.5 + 0.5);
#elif DEBUG_VIEW == 3
    color = sun_reach;
#elif DEBUG_VIEW == 4
    color = per_block.rgb * 4.0 * fade;
#elif DEBUG_VIEW == 5
    color = block * 4.0;
#elif DEBUG_VIEW == 6
    color = gi.rgb * fade;
#elif DEBUG_VIEW == 7
    // Lit by the face the ray went in by, so part blocks show their real shape.
    Hit h = trace_ray(vox_pos(vec3(0.0)), -v, 128.0, 128);
    color = h.hit ? vox_color(h.block).rgb * (0.55 + 0.45 * abs(dot(h.normal, vec3(0.3, 0.9, 0.5)))) : vec3(0.0, 0.0, 0.2);
#elif DEBUG_VIEW == 8
    color = vec3(float(sdf_at(ivec3(floor(in_front)), 0)) / 16.0);
#elif DEBUG_VIEW == 9
    color = vec3(ao * screen_ao);
#elif DEBUG_VIEW == 10
    // The surface's own block in the voxel copy: red stops light (yellow a part block, orange leaves), green
    // see-through, blue a light; dim where the Bridge hasn't read the block yet.
    uint word = vox_word(ivec3(floor(p - s.face * 0.02)));
    color = vec3(float((word & VOX_SOLID) != 0u), float((word & VOX_SEE) != 0u), float((word & VOX_LIGHT) != 0u));
    color.g += (word & VOX_PART) != 0u ? 0.8 : (word & VOX_LEAVES) != 0u ? 0.4 : 0.0;
    color *= (word & VOX_KNOWN) != 0u ? 1.0 : 0.3;
#elif DEBUG_VIEW == 11
    color = vox_color(ivec3(floor(p - s.face * 0.02))).rgb;
#elif DEBUG_VIEW == 12
    // The light list of the block tile in front of the surface: red its length (16 = full), green how many of its
    // first eight lights are still there (8 = full), blue the per-block table's confidence.
    ivec3 tile;
    color = vec3(0.0, 0.0, per_block.a);
    if (block_tile_of(in_front, tile)) {
        uint base = block_tile_index(tile);
        uint count = min(data_load(base), tile_list_max);
        float valid = 0.0;
        for (uint i = 0u; i < min(count, 8u); i++) {
            valid += light_from_key(data_load(base + 1u + i)).valid ? 1.0 : 0.0;
        }
        color.rg = vec2(float(count) / 16.0, valid / 8.0);
    }
#elif DEBUG_VIEW == 14
    {
        // In strips from the left: the lit picture (a fifth), then the block light used, the per-block table, the
        // traced per-pixel light (blue where there is none), the bounce light, and flags (red: traced light there;
        // green: the trace starts inside a full block; blue: inside the voxel box).
        float sx = gl_FragCoord.x / render_size().x;
        if (sx < 0.2) {
        } else if (sx < 0.35) {
            color = block * 4.0;
        } else if (sx < 0.5) {
            color = per_block.rgb * 4.0 * fade;
        } else if (sx < 0.65) {
#ifdef PIXEL_LIGHT
            color = traced.a > 0.5 ? traced.rgb * 4.0 : vec3(0.0, 0.0, 1.0);
#endif
        } else if (sx < 0.8) {
            color = gi.rgb * fade;
        } else {
            vec3 dp = vox_pos(scene) + s.face * 0.04;
#ifdef PIXEL_LIGHT
            float valid = traced.a > 0.5 ? 1.0 : 0.0;
#else
            float valid = 0.0;
#endif
            color = vec3(valid * 0.5, start_inside(dp) ? 1.0 : 0.0, float(vox_level(dp) + 1));
        }
    }
#endif
    color += texelFetch(colortex0, texel, 0).rgb;
#if DEBUG_VIEW == 0
    if (s.material != MAT_HAND) {
        color = with_clouds(color, texel, length(view), cameraPosition.y + scene.y);
    }
#endif
    out_color = vec4(color, 1.0);
}

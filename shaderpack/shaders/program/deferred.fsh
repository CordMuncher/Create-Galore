// The overworld's deferred pass (its last part, deferred3), after everything opaque is drawn and before the
// translucent things: lights each surface the G-buffer holds (see gbuffer.glsl) by the sun or the moon, the sky and
// the blocks (lighting.glsl), adds what glows or lit itself (colortex0), and draws the sky where nothing is, with
// the clouds the passes before worked out (see clouds.glsl), all seen through the light in the air in front of them
// (air.glsl). The translucent programs then draw over the lit scene, lighting themselves the same way.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex9;
#ifdef LIT_MATERIALS
uniform sampler2D colortex12;
#endif
#if AO_QUALITY > 0 || defined LIT_SKY_GRID
uniform sampler2D colortex7;
#endif
#if AO_QUALITY > 0
uniform sampler2D colortex13;
uniform sampler2D colortex14;
#endif
#ifdef LIT_SKY_GRID
uniform sampler2D colortex15;
#endif
#if REFLECTION_QUALITY > 0
// Smaller copies of last frame's picture, for reflections blurred by a surface's roughness (see mirrored).
const bool colortex3MipmapEnabled = true;
#endif
uniform sampler2D depthtex0;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

#include "/lib/reflection.glsl"
#define SKY_PROBE_DEPTH depthtex0
#define SKY_PROBE_DH_DEPTH dhDepthTex0
#include "/lib/sky_probe.glsl"

#ifdef LIT_CLOUDS
#include "/lib/clouds.glsl"

uniform sampler2D cg_cloud_hist_tex;

// The cumulus in front of what a pixel sees (rgb their light with the air in front of them, a how much of what is
// behind gets through), from the image the gather pass wrote this frame (see clouds_gather.csh), at the point the
// pixel sees (in unjittered render pixels).
vec4 cumulus_at(vec2 point) {
    vec2 size = window_size();
    vec2 at = clamp(point, vec2(0.5), render_size() - 0.5) + vec2((frameCounter & 1) == 0 ? 0.0 : size.x, 0.0);
    return texture(cg_cloud_hist_tex, at / vec2(2.0 * size.x, size.y));
}

// The sky with the clouds: the cumulus over the air, and the cirrus between the air and what is beyond it.
vec3 sky_with_clouds(vec3 dir, float pixel_angle, vec2 point, ivec2 texel) {
    vec3 beyond = sky_space(dir, pixel_angle) * (1.0 - rainStrength);
#ifdef CIRRUS
    vec4 ci = cirrus(dir, cloud_noise(texel, 7u));
    beyond = ci.rgb + ci.a * beyond;
#endif
    vec4 cu = cumulus_at(point);
    return (sky_radiance(dir) + beyond * sky_transmittance(dir)) * cu.a + cu.rgb;
}
#endif

in vec2 v_uv;

#if AO_QUALITY > 0 && defined LIT_SKY_GRID
/* RENDERTARGETS: 0,14,15 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_screen_history; // see screen_light_settled
layout(location = 2) out vec4 out_sky;            // see sky_rays_settled
#elif AO_QUALITY > 0
/* RENDERTARGETS: 0,14 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_screen_history;
#elif defined LIT_SKY_GRID
/* RENDERTARGETS: 0,15 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_sky;
#else
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;
#endif

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

// What last frame's picture showed where this frame's render pixel is (rgb, a 1), or a 0 where it was off the screen:
// the reflections come from it, as this frame's isn't drawn yet. Blurred over spread blocks for every block from
// (view space) to what the pixel shows, from a smaller copy of the picture (0: sharp).
vec4 previous_frame_at(vec2 pixel, vec3 from, float spread) {
    vec2 size = render_size();
    ivec2 p = ivec2(pixel);
    float d = texelFetch(depthtex0, p, 0).r;
    vec2 ndc = (vec2(p) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0;
    vec3 seen = unproject(gbufferProjectionInverse, ndc, d);
    vec4 clip = previous_clip(view_to_scene(seen), gbufferPreviousProjection);
    if (clip.w <= 0.0) {
        return vec4(0.0);
    }
    vec2 prev = (clip.xy / clip.w * 0.5 + 0.5) * size;
    if (any(lessThan(prev, vec2(0.5))) || any(greaterThan(prev, size - 0.5))) {
        return vec4(0.0);
    }
    float lod = 0.0;
    if (spread > 0.0) {
        float pixels = spread * length(seen - from) * gbufferProjection[1][1] * 0.5 * size.y / max(-seen.z, 0.05);
        lod = clamp(log2(2.0 * pixels), 0.0, 5.0);
    }
    return vec4(textureLod(colortex3, prev / window_size(), lod).rgb, 1.0);
}

// The world mirrored in a surface at view (view space) along r (world space), rough as GGX alpha, in the frame's
// units: what the screen showed where the ray meets something, else fallback. A rough surface's reflection spreads
// as it goes, so what the ray meets is blurred as wide as that is where it lands (cone tracing, as Uludag's Hi-Z
// reflections do): GGX's lobe at half its peak, about 1.3 alpha either side of the mirror direction, so contact
// stays sharp and what lies further off blurs.
vec3 mirrored(vec3 view, vec3 r, vec3 fallback, float alpha, float noise) {
#if REFLECTION_QUALITY > 0
    vec3 hit = reflection_trace(depthtex0, view, mat3(gbufferModelView) * r, noise, hand_depth_limit);
    if (hit.z > 0.0) {
        vec4 seen = previous_frame_at(hit.xy, view, 1.3 * alpha);
        return mix(fallback, seen.rgb, hit.z * seen.a);
    }
#endif
    return fallback;
}

#if defined SHADOW_MAP && defined CONTACT_SHADOWS
// Screen-space shadows, as Bend Studio's for Days Gone (Aldridge, SIGGRAPH 2023): what the shadow map is too coarse
// for (a blade of grass, a fence post, your feet on the ground, a machine's detail) still shades what lies behind it
// from the light, sharp to the pixel. Seen from the eye, the ray from a surface towards the sun (or the moon) runs
// straight across the screen, towards where the sun stands on it (or away from there, with the sun behind you), its
// depth changing evenly along the way in the screen's own depth. It's followed a pixel at a time; the screen's depth
// is read between the two pixels it passes between (unless they lie far apart, at an edge), and anything in front of
// it by less than a thin thing's thickness (a little more far off) shadows it: fully in the first few pixels, where a
// thing meets its shadow, and further on by the share of each four pixels that do, fading out over the last ones.
// No noise: the same pixels give the same shadow every frame, which the upscaler takes as it is. 1 lit, 0 in shadow.
#if SHADOW_QUALITY >= 4
const int screen_shadow_steps = 60;
#elif SHADOW_QUALITY == 3
const int screen_shadow_steps = 40;
#elif SHADOW_QUALITY == 2
const int screen_shadow_steps = 28;
#else
const int screen_shadow_steps = 16;
#endif
const int screen_shadow_hard = 4; // the first steps, which shadow fully
const int screen_shadow_fade = 8; // the last steps, which fade out

// How far in front of the eye (blocks) what lies at a depth of depthtex0's is.
float eye_distance(float depth) {
    return gbufferProjection[3][2] / (depth * 2.0 - 1.0 + gbufferProjection[2][2]);
}

float screen_shadow(ivec2 texel, vec3 view, float depth) {
    // The surface and the light's direction in clip space, and how the ray's place on the screen (pixels) and its
    // depth (depthtex0's) change as it leaves the surface.
    vec4 surface = gbufferProjection * vec4(view, 1.0);
    vec4 light = gbufferProjection * vec4(mat3(gbufferModelView) * g_light_dir.xyz, 0.0);
    vec2 size = render_size();
    vec2 across = (light.xy * surface.w - surface.xy * light.w) * 0.5 * size;
    float deeper = (light.z * surface.w - surface.z * light.w) * 0.5;
    float pixels = length(across);
    if (pixels < 1e-6 * abs(surface.w * surface.w)) {
        return 1.0; // the light straight along the view: its ray stays on the pixel
    }
    vec2 step_px = across / pixels;          // a pixel along the ray
    float step_depth = deeper / pixels;      // and how much deeper the ray is there
    bool x_major = abs(step_px.x) > abs(step_px.y);

    float dist = -view.z;
    float thickness = 0.25 + 0.015 * dist;
    float bias = 0.01 + 0.002 * dist;
    float lit = 1.0;
    float group = 0.0;
    vec2 at = vec2(texel) + 0.5;
    ivec2 last = ivec2(size) - 1;
    for (int k = 1; k <= screen_shadow_steps; k++) {
        vec2 p = at + step_px * float(k);
        float ray_depth = depth + step_depth * float(k);
        if (any(lessThan(p, vec2(0.0))) || any(greaterThanEqual(p, size)) || ray_depth >= 1.0 || ray_depth <= 0.0) {
            break; // off the screen, or past the far or the near plane
        }
        // The depth between the two pixels the ray passes between, across its way.
        ivec2 near = ivec2(p);
        float minor = (x_major ? p.y : p.x) - floor(x_major ? p.y : p.x) - 0.5;
        ivec2 other = clamp(near + (x_major ? ivec2(0, minor > 0.0 ? 1 : -1) : ivec2(minor > 0.0 ? 1 : -1, 0)),
                ivec2(0), last);
        float d0 = texelFetch(depthtex0, near, 0).r;
        float d1 = texelFetch(depthtex0, other, 0).r;
        float occluded = 0.0;
        if (d0 >= hand_depth_limit && d0 < 1.0) {
            float in_front = eye_distance(d0);
            if (d1 >= hand_depth_limit && d1 < 1.0) {
                float beside = eye_distance(d1);
                if (abs(beside - in_front) < 0.1 * in_front) {
                    in_front = mix(in_front, beside, abs(minor));
                }
            }
            float behind = eye_distance(ray_depth) - in_front;
            occluded = smoothstep(bias, 2.0 * bias, behind) * (1.0 - smoothstep(thickness, 1.5 * thickness, behind));
        }
        if (k <= screen_shadow_hard) {
            lit = min(lit, 1.0 - occluded);
        } else {
            group += occluded;
            if ((k - screen_shadow_hard) % 4 == 0) {
                float fade = clamp(float(screen_shadow_steps - k + 4) / float(screen_shadow_fade), 0.0, 1.0);
                lit = min(lit, 1.0 - 0.25 * group * fade);
                group = 0.0;
            }
        }
        if (lit <= 0.0) {
            break;
        }
    }
    return lit;
}
#endif

#if AO_QUALITY > 0 || defined LIT_SKY_GRID
// Where the surface a pixel shows was last frame (render pixels, prev), by its own motion where its program worked
// one out (see gbuffers_kind.glsl), else by the camera's; false where it was off the screen. texel and point: the
// pixel (point unjittered).
bool previous_point(ivec2 texel, vec2 point, vec3 scene_pos, out vec2 prev) {
    vec4 motion = texelFetch(colortex7, texel, 0);
    if (motion.b == MOTION_WORLD || motion.b == MOTION_HAND) {
        prev = point + motion.rg;
    } else {
        vec4 clip = previous_clip(scene_pos, gbufferPreviousProjection);
        prev = clip.w > 0.0 ? (clip.xy / clip.w * 0.5 + 0.5) * render_size() : vec2(-1.0);
    }
    return all(greaterThanEqual(prev, vec2(0.5))) && all(lessThanEqual(prev, render_size() - 0.5));
}

// How much of what the own denoisers below held last frame they keep (eight frames or so).
const float screen_history = 0.88;
#endif

#if AO_QUALITY > 0
// OWN DENOISER (off under Ray Reconstruction, see the README): what the screen shows around a surface (colortex13,
// see screen_light.fsh) blended with what it held where the surface was last frame (colortex14, kept from frame to
// frame), as TAA blends: last frame's held within what this frame's shows in the pixels around (so it doesn't trail
// behind what moves or opens up), most of it kept. prev: where the surface was last frame (see previous_point), if
// seen.
vec4 screen_light_settled(ivec2 texel, vec2 prev, bool seen) {
    vec4 now = texelFetch(colortex13, texel, 0);
    vec4 lo = now;
    vec4 hi = now;
    ivec2 last = ivec2(render_size()) - 1;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec4 around = texelFetch(colortex13, clamp(texel + ivec2(x, y), ivec2(0), last), 0);
            lo = min(lo, around);
            hi = max(hi, around);
        }
    }
    if (!seen) {
        return now;
    }
    vec4 history = textureLod(colortex14, prev / window_size(), 0.0);
    if (any(isnan(history)) || any(isinf(history))) {
        return now;
    }
    return mix(now, clamp(history, lo, hi), screen_history);
}
#endif

#ifdef LIT_SKY_GRID
// OWN DENOISER (off under Ray Reconstruction, see the README): the share of the sky the sky grid's few rays from a
// surface see this frame (colortex15's r, see screen_light.fsh), blended over frames as the screen's light is, in
// colortex15's g; -1 where they see none. A room lit through a window would sparkle otherwise, a ray here and there
// finding the window.
float sky_rays_settled(ivec2 texel, vec2 prev, bool seen) {
    float now = texelFetch(colortex15, texel, 0).r;
    if (now < 0.0) {
        return now;
    }
    float lo = now;
    float hi = now;
    ivec2 last = ivec2(render_size()) - 1;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float around = texelFetch(colortex15, clamp(texel + ivec2(x, y), ivec2(0), last), 0).r;
            if (around >= 0.0) {
                lo = min(lo, around);
                hi = max(hi, around);
            }
        }
    }
    if (!seen) {
        return now;
    }
    // (Where the pixels around held none last frame, the blend of them comes out low; the clamp keeps it to this
    // frame's.)
    float history = textureLod(colortex15, prev / window_size(), 0.0).g;
    if (!(history >= 0.0) || isinf(history)) {
        return now;
    }
    return mix(now, clamp(history, lo, hi), screen_history);
}
#endif

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
#if AO_QUALITY > 0
    out_screen_history = vec4(0.0, 0.0, 0.0, 1.0);
#endif
#ifdef LIT_SKY_GRID
    out_sky = vec4(0.0, -1.0, 0.0, 0.0);
#endif
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        out_color = vec4(0.0); // outside the frame while upscaling
        return;
    }

    // This pixel's centre without the jitter, in normalised device coordinates, and the direction it looks in.
    vec2 point = vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec2 ndc = point / size * 2.0 - 1.0;
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0));

    vec3 emissive = texelFetch(colortex0, texel, 0).rgb;
    vec4 surface = texelFetch(colortex2, texel, 0);
    int material = decode_material(surface);
    float depth = texelFetch(depthtex0, texel, 0).r;

    bool lod = false;
    vec3 view = vec3(0.0);
    if (depth < 1.0) {
        bool hand = depth < hand_depth_limit;
        view = unproject(gbufferProjectionInverse, ndc, hand ? (depth - 0.5) / MC_HAND_DEPTH + 0.5 : depth);
    } else {
#ifdef DISTANT_HORIZONS
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            view = unproject(dhProjectionInverse, ndc, dh_depth);
            lod = true;
        } else {
            material = MATERIAL_NONE;
        }
#else
        material = MATERIAL_NONE;
#endif
    }

    vec3 color;
    if (material == MATERIAL_NONE) {
        // The sky, behind whatever only blended or glowed over it (smoke, lightning). A render pixel's size, for
        // the sun's edge and the stars.
        float pixel_angle = 2.0 / (gbufferProjection[1][1] * size.y);
#ifdef LIT_CLOUDS
        vec3 sky = sky_with_clouds(dir, pixel_angle, point, texel);
#else
        vec3 sky = sky_full(dir, pixel_angle);
#endif
        // In lava or powder snow, and blind, the fog hides the sky (under water, the water does: see water_body.fsh).
        if (isEyeInWater >= 2 || blindness > 0.0 || darknessFactor > 0.0) {
            vec2 range = fog_range();
            sky = mix(sky, fog_color_lit(dir), range.y > range.x ? 1.0 : 0.0);
        }
        sky += global(G_FLASH).rgb; // lightning
        float covered = texelFetch(colortex1, texel, 0).a;
        vec3 behind = sky * exposure();
#ifdef LIT_AIR
        // The air nearest you, with its shafts, in front of the sky (whose table begins past it, see air.glsl).
        behind = through_air(behind, air_seen(point, air_sky_start * -normalize(unproject(gbufferProjectionInverse, ndc, 1.0)).z));
#endif
        color = emissive + behind * (1.0 - covered);
    } else if (material == MATERIAL_SELF) {
        color = emissive;
    } else {
        vec3 scene_pos = view_to_scene(view);
        vec3 albedo = srgb_to_linear(texelFetch(colortex1, texel, 0).rgb);
        vec4 occlusion = texelFetch(colortex9, texel, 0);
        int kind = material == MATERIAL_LIT ? decode_surface_kind(occlusion.g) : SURFACE_PLAIN;
        bool foliage = kind == SURFACE_LEAVES;
        vec3 normal = decode_normal(surface.rg);
        // What it reflects and how (see material.glsl): by the resource pack's maps where it has them, else by kind.
        Material mat = material_of_kind(kind, albedo);
#ifdef LIT_MATERIALS
        vec3 maps = texelFetch(colortex12, texel, 0).rgb;
        if (material != MATERIAL_SELF && has_maps(maps)) {
            mat = material_from_maps(maps, kind, albedo);
        }
#endif
        // Minecraft's corners, and what the screen shows around a surface (plants have only theirs: every blade
        // would shade the next): how open it is, and the light the surfaces close around it bounce onto it (see
        // screen_light.fsh). In a tree the leaves seen through the gaps of the outer ones lie deeper in, so they get
        // less of the light all around: the tree has depth, without its outside going dark.
        float ao = occlusion.r;
        vec4 screen = vec4(0.0, 0.0, 0.0, 1.0);
        bool screen_lit = false;
#if AO_QUALITY > 0 || defined LIT_SKY_GRID
        // Where the surface was last frame, for what the passes before worked out a little of each frame.
        vec2 prev = vec2(0.0);
        bool was_seen = !lod && depth >= hand_depth_limit && previous_point(texel, point, scene_pos, prev);
#endif
#if AO_QUALITY > 0
        if (!lod && depth >= hand_depth_limit && material == MATERIAL_LIT) {
            screen = screen_light_settled(texel, prev, was_seen);
            out_screen_history = screen;
            if (foliage) {
                screen = vec4(screen.rgb * 0.75, mix(1.0, screen.a, 0.75));
            }
            ao = min(ao, screen.a);
            screen_lit = true;
        }
#endif
        vec2 light = decode_light(surface);
        // Rain leaves a film of water on what it falls on while it falls (not on plants, which shed it) and soaks
        // into it, darker and deeper in colour the more it takes in, for a while after the shine has gone; puddles
        // stand in the dips of flat ground, damp around them, for a while longer (see wet.glsl).
        vec2 wetness = material == MATERIAL_LIT ? wetness_at(normal, light.y) : vec2(0.0);
        float wet = wetness.x;
        vec2 puddle = vec2(0.0);
        if (material == MATERIAL_LIT && !lod && depth >= hand_depth_limit) {
            puddle = puddle_at(scene_pos, normal, light.y, kind);
#ifdef LIT_SHORE
            // And the band the water wets on a wall or the bank where it meets them, as high as it laps.
            float shore = shore_wet(scene_pos, normal, cg_swell);
            wet = max(wet, 0.6 * shore);
            wetness.y = max(wetness.y, shore);
#endif
        }
        albedo = soaked(albedo, max(wetness.y, puddle.y), mat.porosity) * (1.0 - 0.25 * puddle.x);
        // Under something high (a floating island, a tall roof, a bridge) more of the sky's light gets in from around
        // it than Minecraft's sky light gives (see sky_grid.glsl and sky_probe.glsl). The rain doesn't: the wet look
        // keeps Minecraft's.
        float sky_light_mc = light.y;
        float seen = -1.0;
        if (depth >= hand_depth_limit && light.y < 0.999) {
            // The sky grid's rays from here, traced a few each frame (see screen_light.fsh).
            float rays = -1.0;
#ifdef LIT_SKY_GRID
            if (!lod) {
                rays = sky_rays_settled(texel, prev, was_seen);
                out_sky.g = rays;
            }
#endif
            seen = sky_seen_blend(scene_pos, light.y, rays, lod, false);
            float level = sky_seen_level(seen);
            // Indoors it can be darker too: Minecraft's sky light comes in through a window and spreads through the
            // whole room a level less a block, but a room sees the sky only through its openings. Where the grid's
            // rays traced here find little sky (a room, the shade under a roof), they stand in for it, the more so
            // the less they find; out in the open, and where only the screen's probe can tell, never darker.
            float indoors = 0.0;
#ifdef LIT_SKY_GRID
            if (!lod && rays >= 0.0) {
                indoors = (1.0 - smoothstep(0.15, 0.5, seen))
                        * (1.0 - smoothstep(sky_grid_reach - 28.0, sky_grid_reach, max(abs(scene_pos.x), abs(scene_pos.z))));
            }
#endif
            light.y = level >= light.y ? level : mix(light.y, level, indoors);
        }
        float contact = 1.0;
#if defined SHADOW_MAP && defined CONTACT_SHADOWS
        // Not on plants: in a meadow every blade would find another in front of it and the grass would go dark.
        if (!lod && depth >= hand_depth_limit && material == MATERIAL_LIT && (foliage || dot(normal, g_light_dir.xyz) > 0.0)) {
            contact = screen_shadow(texel, view, depth);
        }
#endif
        // The texture's own relief and crevices shade it from the sun too (see material_maps.glsl).
        contact *= occlusion.b;
        vec3 nearby;
        vec3 lit = light_surface(albedo, normal, light, scene_pos, material == MATERIAL_PLANT, foliage, !lod, contact,
                ao, sky_light_mc, screen.rgb, screen_lit, nearby) * exposure();
#if DEBUG_VIEW == 1 && defined LIT_GI
        lit = g_light_color.rgb * bounce_light(scene_pos, normal, light.y) / PI * exposure();
#elif DEBUG_VIEW == 2
        lit = vec3(ao * 10.0);
#elif DEBUG_VIEW == 7
        lit = screen.rgb * exposure();
#elif DEBUG_VIEW == 3 && defined SHADOW_MAP
        // Under water (the water pass leaves it as it is): red how deep under it the point is (a quarter per block),
        // green the caustics' brightening (a third per 1), blue 1. Along the bottom left, a grey ramp from 0 to 2
        // (at x 512) to read them by.
        {
            float under = shadow_water_depth(scene_pos);
            if (under > 0.0) {
                lit = vec3(under / 4.0, water_caustics(scene_pos, under, length(scene_pos)).g / 3.0, 1.0);
            }
            if (gl_FragCoord.y < 24.0 && gl_FragCoord.x < 512.0) {
                lit = vec3(gl_FragCoord.x / 256.0);
            }
        }
#elif DEBUG_VIEW == 4 && defined SHADOW_MAP
        // Why the shadow map does or doesn't find water over a point, where it has any sign of water there: yellow it
        // finds it (how deep, see shadow_water_depth); magenta the water's mark but no water depth in shadowtex0;
        // cyan water depth but no mark; green both, but the point lies in front of the water's depth; blue the point
        // is outside the shadow map.
        {
            vec3 sc = shadow_coord(scene_pos);
            ivec2 st = ivec2(clamp(sc.xy, 0.0, 1.0) * float(shadowMapResolution - 1));
            float all_depth = texelFetch(shadowtex0, st, 0).r;
            float opaque_depth = texelFetch(shadowtex1, st, 0).r;
            bool mark = texelFetch(shadowcolor1, st, 0).b >= 0.5;
            bool water_depth = all_depth < opaque_depth;
            if (any(lessThan(sc, vec3(0.0))) || any(greaterThan(sc, vec3(1.0)))) {
                lit = vec3(0.0, 0.0, 2.0);
            } else if (shadow_water_depth(scene_pos) > 0.0) {
                lit = vec3(2.0, 2.0, 0.0);
            } else if (mark && !water_depth) {
                lit = vec3(2.0, 0.0, 2.0);
            } else if (!mark && water_depth) {
                lit = vec3(0.0, 2.0, 2.0);
            } else if (mark && water_depth) {
                lit = vec3(0.0, 2.0, 0.0);
            }
        }
#elif DEBUG_VIEW == 6
        // How much of the sky a surface sees (see sky_grid.glsl, sky_probe.glsl): red Minecraft's sky light (through
        // its curve), green the share the pack works out, blue where it works out none (Minecraft's sky light alone).
        lit = vec3(sky_light_curve(sky_light_mc), max(seen, 0.0), seen < 0.0 ? 0.5 : 0.0) * 2.0;
#elif DEBUG_VIEW == 5 && defined LIT_VOXELS
        // On leaves, the way out of their crown (see canopy_shape) as a colour: red towards east, green up, blue
        // south (grey halfway, where it points along the other two), darker the less of a crown there is.
        if (foliage && !lod) {
            vec4 crown = canopy_shape(scene_pos);
            float shaped = smoothstep(0.05, 0.25, length(crown.xyz));
            lit = (crown.xyz / max(length(crown.xyz), 1e-6) * 0.5 + 0.5) * shaped * 2.0;
        }
#endif
        if (wet > 0.0) {
            lit = with_wet_film(lit, normal, dir, wet * 0.5 * (1.0 - puddle.x));
        }
        if (puddle.x > 0.0) {
            // Still water: the world mirrored, rings where raindrops fall on it, the sun's glint.
            // (The rings fade out a little way off, where they would only sparkle from pixel to pixel.)
            vec2 rings = rain_ripple_slope(scene_pos, cg_rain_here * (1.0 - smoothstep(8.0, 24.0, length(scene_pos))));
            vec3 n = normalize(vec3(-rings.x, 1.0, -rings.y));
            float f = fresnel_schlick(dot(-dir, n), vec3(0.02)).x * puddle.x;
            float glint = glint_visibility(scene_pos, normal, light.y, sky_light_mc);
            vec3 r = reflect(dir, n);
            vec3 sky_seen = sky_radiance(normalize(vec3(r.x, max(r.y, 0.02), r.z))) * exposure();
            lit = mix(lit, mirrored(view, r, sky_seen, 0.0, pixel_noise()), f)
                    + sun_glint(n, dir, 0.03, vec3(0.02), glint) * exposure() * puddle.x;
        } else if (material == MATERIAL_LIT && !foliage) {
            // Every surface reflects (see material.glsl): a clear sheen over its colour, faint and broad on most
            // things (4% head on, more at a glancing angle, less of that the rougher it is), sharp on polished stone
            // and what the resource pack makes smooth; metal in its own colour, with nothing under its sheen. What it
            // reflects is the light all around as the sheen sees it, as Unreal's Lumen does for rough surfaces: the
            // sky where it's open (blurred by its roughness), the blocks' light and the bounce light, and where the
            // surfaces close around it block those (Lagarde and de Rousiers' specular occlusion), their own light from
            // the screen. Smooth surfaces mirror what the screen shows too, blurred as their roughness spreads it.
            // (Not leaves: a sheen on each face of their cubes would undo their crown's round shading. Distant
            // Horizons' terrain takes the light all around only, so it meets the world's without a seam.)
            float n_dot_v = clamp(dot(-dir, normal), 0.0, 1.0);
            vec3 f = fresnel_rough(n_dot_v, mat.f0, mat.alpha);
            float open_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, light.y);
            vec3 r = reflect(dir, normal);
            float spec_vis = clamp(pow(n_dot_v + ao, exp2(-16.0 * mat.alpha - 1.0)) - 1.0 + ao, 0.0, 1.0);
            float sharp = clamp(1.0 - 2.2 * mat.alpha, 0.0, 1.0);
            vec3 sky_seen = mix(sky_irradiance(r) / PI, sky_radiance(normalize(vec3(r.x, max(r.y, 0.02), r.z))), sharp)
                    * open_sky;
            vec3 around = (sky_seen + nearby / PI) * spec_vis;
            if (screen_lit) {
                around += screen.rgb / max(1.0 - screen.a, 0.1) * (1.0 - spec_vis);
            }
            around *= exposure();
            if (mat.alpha < 0.2 && !lod) {
                around = mix(mirrored(view, r, around, mat.alpha, pixel_noise()), around, smoothstep(0.12, 0.2, mat.alpha));
            }
            float glint = glint_visibility(scene_pos, normal, light.y, sky_light_mc) * occlusion.b;
            lit = lit * (1.0 - f) * (1.0 - mat.metal) + around * f
                    + sun_glint(normal, dir, max(mat.alpha, 0.03), mat.f0, glint) * exposure();
        }
        // Minecraft's fog: a cylinder around you in the open air, round in water and the like (Iris gives this pass
        // no fog shape).
        bool open_air = isEyeInWater == 0 && blindness == 0.0 && darknessFactor == 0.0;
        float dist = open_air ? max(length(scene_pos.xz), abs(scene_pos.y)) : length(scene_pos);
        // The light in the air between you and it (see air.glsl), which dims what glows too.
        vec4 air = depth >= hand_depth_limit ? air_seen(point, -view.z) : vec4(0.0, 0.0, 0.0, 1.0);
        color = emissive * air.a + apply_fog_lit(through_air(lit, air), dist, dir, fog_range_shaped(open_air ? 1 : 0));
#ifdef LIT_CLOUDS
        // From above the cumulus, they hide what lies under them.
        if (atm_height(cameraPosition.y) > cumulus_bottom && atm_height(scene_pos.y + cameraPosition.y) < cumulus_bottom) {
            vec4 cu = cumulus_at(point);
            color = color * cu.a + cu.rgb * exposure();
        }
#endif
    }
#if DEBUG_VIEW == 4 && defined SHADOW_MAP
    // Along the bottom left, the shadow map within 48 blocks of you as the light sees it: grey what is opaque
    // (lighter nearer the light), red where something translucent lies over it, blue water's mark; right of it, the
    // ripples' grid within 48 blocks (from above, north up): green the tops of water the shadow pass marked, red
    // anything else that reaches into water.
    {
        float side = floor(0.45 * float(textureSize(depthtex0, 0).y));
        vec2 p = gl_FragCoord.xy - 4.0;
        if (p.y >= 0.0 && p.y < side && p.x >= 0.0 && p.x < side) {
            vec2 centre = shadow_coord(vec3(0.0)).xy;
            vec2 uv = centre + (p / side - 0.5) * (96.0 / (2.0 * shadowDistance));
            ivec2 st = ivec2(uv * float(shadowMapResolution));
            bool translucent = false;
            bool mark = false;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    ivec2 t = clamp(st + ivec2(x, y), ivec2(0), ivec2(shadowMapResolution - 1));
                    translucent = translucent || texelFetch(shadowtex0, t, 0).r < texelFetch(shadowtex1, t, 0).r - 1e-6;
                    mark = mark || texelFetch(shadowcolor1, t, 0).b >= 0.5;
                }
            }
            color = vec3(1.0 - texelFetch(shadowtex1, st, 0).r) * 0.5;
            if (translucent) {
                color.r = 2.0;
            }
            if (mark) {
                color.b = 2.0;
            }
        }
#ifdef LIT_WATER_RIPPLES
        p.x -= side + 4.0;
        if (p.y >= 0.0 && p.y < side && p.x >= 0.0 && p.x < side) {
            ivec2 cell = ivec2(vec2(p.x, side - p.y) / side * 384.0) + water_sim_size / 2 - 192;
            bool top = false;
            bool reach = false;
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    ivec2 c = cell + ivec2(x, y);
                    top = top || texelFetch(cg_water_marks_tex, c, 0).r != 0u;
                    reach = reach || texelFetch(cg_water_marks_tex, c + ivec2(0, 2 * water_sim_size), 0).r != 0u;
                }
            }
            color = vec3(reach ? 2.0 : 0.05, top ? 2.0 : 0.05, 0.05);
        }
#endif
    }
#endif
    out_color = vec4(color, 1.0);
}

// The overworld's deferred pass (its last part, deferred2), after everything opaque is drawn and before the
// translucent things: lights each surface the G-buffer holds (see gbuffer.glsl) by the sun or the moon, the sky and
// the blocks (lighting.glsl), adds what glows or lit itself (colortex0), and draws the sky where nothing is, with
// the clouds the passes before worked out (see clouds.glsl). The translucent programs then draw over the lit scene,
// lighting themselves the same way.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex9;
uniform sampler2D depthtex0;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

#include "/lib/reflection.glsl"

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

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

// What last frame's picture showed where this frame's render pixel is (rgb, a 1), or a 0 where it was off the screen:
// the reflections on puddles and polished floors come from it, as this frame's isn't drawn yet.
vec4 previous_frame_at(vec2 pixel) {
    vec2 size = render_size();
    ivec2 p = ivec2(pixel);
    float d = texelFetch(depthtex0, p, 0).r;
    vec2 ndc = (vec2(p) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0;
    vec4 clip = previous_clip(view_to_scene(unproject(gbufferProjectionInverse, ndc, d)), gbufferPreviousProjection);
    if (clip.w <= 0.0) {
        return vec4(0.0);
    }
    vec2 prev = (clip.xy / clip.w * 0.5 + 0.5) * size;
    if (any(lessThan(prev, vec2(0.5))) || any(greaterThan(prev, size - 0.5))) {
        return vec4(0.0);
    }
    return vec4(texture(colortex3, prev / window_size()).rgb, 1.0);
}

// The world mirrored in a smooth surface at view (view space) along r (world space), in the frame's units: what the
// screen showed there, else the sky, as much of it as the surface sees (open_sky).
vec3 mirrored(vec3 view, vec3 r, float open_sky, float noise) {
    vec3 reflection = sky_radiance(normalize(vec3(r.x, max(r.y, 0.02), r.z))) * open_sky * exposure();
#if REFLECTION_QUALITY > 0
    vec3 hit = reflection_trace(depthtex0, view, mat3(gbufferModelView) * r, noise, hand_depth_limit);
    if (hit.z > 0.0) {
        vec4 seen = previous_frame_at(hit.xy);
        reflection = mix(reflection, seen.rgb, hit.z * seen.a);
    }
#endif
    return reflection;
}

#if defined SHADOW_MAP && defined CONTACT_SHADOWS
// Contact shadows: what the shadow map is too coarse for (a blade of grass, your feet on the ground, a flower's
// stem) still shades what lies right behind it from the light. The screen's depth is followed a short way towards
// the light from the surface (a little further far off, where a pixel covers more); anything in front of the path by
// less than a thin thing's thickness shadows it. 1 lit, 0 in shadow.
float contact_shadow(vec3 view, float noise) {
    float dist = -view.z;
    if (dist > 64.0) {
        return 1.0;
    }
    const int steps = 12;
    vec3 light_view = mat3(gbufferModelView) * g_light_dir.xyz;
    float len = 0.3 + 0.01 * dist;
    float thickness = 0.25 + 0.01 * dist;
    float bias = 0.01 + 0.002 * dist;
    vec2 size = render_size();
    vec2 jitter = vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec3 start = view + light_view * (0.02 + 0.002 * dist);
    for (int i = 0; i < steps; i++) {
        vec3 p = start + light_view * ((float(i) + noise) / float(steps) * len);
        vec4 clip = gbufferProjection * vec4(p, 1.0);
        if (clip.w <= 0.0) {
            break;
        }
        vec2 pixel = (clip.xy / clip.w * 0.5 + 0.5) * size + jitter;
        if (any(lessThan(pixel, vec2(0.0))) || any(greaterThanEqual(pixel, size))) {
            break;
        }
        float d = texelFetch(depthtex0, ivec2(pixel), 0).r;
        if (d < hand_depth_limit || d >= 1.0) {
            continue;
        }
        float scene_dist = -unproject(gbufferProjectionInverse, (pixel - jitter) / size * 2.0 - 1.0, d).z;
        float behind = -p.z - scene_dist;
        if (behind > bias && behind < thickness) {
            return 0.0;
        }
    }
    return 1.0;
}
#endif

#if AO_QUALITY > 0
#if AO_QUALITY >= 3
const int ao_directions = 4;
const int ao_steps = 8;
#elif AO_QUALITY == 2
const int ao_directions = 2;
const int ao_steps = 6;
#else
const int ao_directions = 1;
const int ao_steps = 5;
#endif
// How far around a surface its occlusion looks (blocks): what lies further off counts less from 40% of it out.
const float ao_radius = 1.5;

// Ambient occlusion from the screen's depth (GTAO: Jimenez et al. 2016, as Intel's XeGTAO lays it out). Along a few
// directions across the screen through the pixel, the highest the depth around it rises on either side (its two
// horizons); between them and the surface's normal, the share of the light all around (by its cosine) that comes in.
// view: the surface's view-space position (unjittered), normal: view space. noise_dir, noise_step: 0..1, different
// for each pixel and frame, which turn the directions and move the steps (the upscaler evens them out). 1: open.
float screen_ao(ivec2 texel, vec3 view, vec3 normal, float noise_dir, float noise_step) {
    vec2 size = render_size();
    // The radius in pixels, at most a quarter of the screen (very close up).
    float pixels = min(ao_radius * gbufferProjection[1][1] * 0.5 * size.y / -view.z, 0.25 * size.y);
    if (pixels < 2.0) {
        return 1.0;
    }
    vec2 jitter = vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec3 v = normalize(-view);
    const float falloff_range = 0.6 * ao_radius;
    const float falloff_mul = -1.0 / falloff_range;
    const float falloff_add = (ao_radius - falloff_range) / falloff_range + 1.0;
    float visibility = 0.0;
    for (int d = 0; d < ao_directions; d++) {
        float phi = (float(d) + noise_dir) * PI / float(ao_directions);
        vec2 omega = vec2(cos(phi), sin(phi));
        // The plane through the eye along this direction, and the normal's shadow on it: its length weighs the slice,
        // its angle from the eye is n.
        vec3 direction = vec3(omega, 0.0);
        vec3 ortho = direction - dot(direction, v) * v;
        vec3 axis = normalize(cross(ortho, v));
        vec3 projected = normal - axis * dot(normal, axis);
        float projected_length = length(projected);
        if (projected_length < 1e-4) {
            continue;
        }
        float cos_n = clamp(dot(projected, v) / projected_length, 0.0, 1.0);
        float n = (dot(ortho, projected) >= 0.0 ? 1.0 : -1.0) * acos(cos_n);
        // The horizons start at the surface's own plane on either side.
        float low_cos0 = cos(n + 0.5 * PI);
        float low_cos1 = cos(n - 0.5 * PI);
        float horizon_cos0 = low_cos0;
        float horizon_cos1 = low_cos1;
        for (int i = 0; i < ao_steps; i++) {
            float t = (float(i) + noise_step) / float(ao_steps);
            // More of the steps close in, the first at least a pixel and a bit away.
            vec2 offset = omega * (t * t * pixels + 1.3);
            for (int side = 0; side < 2; side++) {
                ivec2 at = texel + ivec2(round(side == 0 ? offset : -offset));
                if (any(lessThan(at, ivec2(0))) || any(greaterThanEqual(vec2(at), size))) {
                    continue;
                }
                float depth = texelFetch(depthtex0, at, 0).r;
                if (depth < hand_depth_limit || depth >= 1.0) {
                    continue; // the hand and the sky hide nothing
                }
                vec3 delta = unproject(gbufferProjectionInverse, (vec2(at) + 0.5 - jitter) / size * 2.0 - 1.0, depth) - view;
                float len = length(delta);
                float weight = clamp(len * falloff_mul + falloff_add, 0.0, 1.0);
                float c = dot(delta, v) / max(len, 1e-4);
                if (side == 0) {
                    horizon_cos0 = max(horizon_cos0, mix(low_cos0, c, weight));
                } else {
                    horizon_cos1 = max(horizon_cos1, mix(low_cos1, c, weight));
                }
            }
        }
        float h0 = -acos(clamp(horizon_cos1, -1.0, 1.0));
        float h1 = acos(clamp(horizon_cos0, -1.0, 1.0));
        h0 = n + clamp(h0 - n, -0.5 * PI, 0.5 * PI);
        h1 = n + clamp(h1 - n, -0.5 * PI, 0.5 * PI);
        float arc0 = (cos_n + 2.0 * h0 * sin(n) - cos(2.0 * h0 - n)) * 0.25;
        float arc1 = (cos_n + 2.0 * h1 * sin(n) - cos(2.0 * h1 - n)) * 0.25;
        visibility += projected_length * (arc0 + arc1);
    }
    return clamp(visibility / float(ao_directions), 0.0, 1.0);
}
#endif

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
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
        color = emissive + sky * exposure() * (1.0 - covered);
    } else if (material == MATERIAL_SELF) {
        color = emissive;
    } else {
        vec3 scene_pos = view_to_scene(view);
        vec3 albedo = srgb_to_linear(texelFetch(colortex1, texel, 0).rgb);
        vec2 occlusion = texelFetch(colortex9, texel, 0).rg;
        int kind = material == MATERIAL_LIT ? decode_surface_kind(occlusion.g) : SURFACE_PLAIN;
        bool foliage = kind == SURFACE_LEAVES;
        vec3 normal = decode_normal(surface.rg);
        // Minecraft's corners, and what the screen shows around a surface (plants have only theirs: every blade
        // would shade the next). In a tree the leaves seen through the gaps of the outer ones lie deeper in, so
        // they get less of the light all around: the tree has depth, without its outside going dark.
        float ao = occlusion.r;
#if AO_QUALITY > 0
        if (!lod && depth >= hand_depth_limit && material == MATERIAL_LIT) {
            float screen = screen_ao(texel, view, mat3(gbufferModelView) * normal, pixel_noise(), pixel_noise(1u));
            ao = min(ao, foliage ? mix(1.0, screen, 0.75) : screen);
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
        }
        albedo = soaked(albedo, max(wetness.y, puddle.y), porosity_of(kind)) * (1.0 - 0.25 * puddle.x);
        float contact = 1.0;
#if defined SHADOW_MAP && defined CONTACT_SHADOWS
        // Not on plants: in a meadow every blade would find another in front of it and the grass would go dark.
        if (!lod && depth >= hand_depth_limit && material == MATERIAL_LIT && (foliage || dot(normal, g_light_dir.xyz) > 0.0)) {
            contact = contact_shadow(view, pixel_noise());
        }
#endif
        vec3 lit = light_surface(albedo, normal, light, scene_pos, material == MATERIAL_PLANT, foliage, !lod, contact, ao)
                * exposure();
#if DEBUG_VIEW == 1 && defined LIT_GI
        lit = g_light_color.rgb * bounce_light(scene_pos, normal, light.y) / PI * exposure();
#elif DEBUG_VIEW == 2
        lit = vec3(ao * 10.0);
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
            lit = mix(lit, mirrored(view, reflect(dir, n), 1.0, pixel_noise()), f)
                    + sun_glint(n, dir, 0.03, vec3(0.02), glint_visibility(scene_pos, normal, light.y)) * exposure()
                    * puddle.x;
        } else if ((kind == SURFACE_METAL || kind == SURFACE_POLISHED) && !lod) {
            // Metal reflects in its own colour, blurred (its surface is rough); polished stone a little, sharper,
            // mostly at a glancing angle.
            bool metal = kind == SURFACE_METAL;
            vec3 f0 = metal ? albedo : vec3(0.04);
            vec3 f = fresnel_schlick(dot(-dir, normal), f0);
            float open_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, light.y);
            vec3 r = reflect(dir, normal);
            vec3 around;
            if (metal) {
                around = mix(sky_irradiance(r) / PI, sky_radiance(normalize(vec3(r.x, max(r.y, 0.02), r.z))), 0.35)
                        * open_sky * exposure();
            } else {
                around = mirrored(view, r, open_sky, pixel_noise());
            }
            float glint = glint_visibility(scene_pos, normal, light.y);
            lit = lit * (metal ? 0.6 : 1.0 - f.g) + around * f * (metal ? 0.6 : 0.8)
                    + sun_glint(normal, dir, metal ? 0.3 : 0.12, f0, glint) * exposure();
        }
        // Minecraft's fog: a cylinder around you in the open air, round in water and the like (Iris gives this pass
        // no fog shape).
        bool open_air = isEyeInWater == 0 && blindness == 0.0 && darknessFactor == 0.0;
        float dist = open_air ? max(length(scene_pos.xz), abs(scene_pos.y)) : length(scene_pos);
        color = emissive + apply_fog_lit(lit, dist, dir, fog_range_shaped(open_air ? 1 : 0));
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

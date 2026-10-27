// The overworld's water (its composite pass), after everything translucent is drawn and before DLSS: the water
// program only left its surface (see gbuffer.glsl), and this draws the water from it. From above: what lies under the
// surface, seen along the ray the surface bends, through the water between them (dimmed and turning blue-green with
// depth, hazy with the light the water scatters, see water_body.glsl), with the world reflected on top where the
// screen shows it (see reflection.glsl) and the sky and the sun elsewhere, and foam where the water meets the shore
// or something churns it. From under water: the water between you and everything you see, lit where it is, so the
// far water stays as bright as the light there wherever you are; and the surface from below, which shows the sky
// through a round window above you (light bends into the water) and mirrors the water all around it.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"
#include "/lib/water_body.glsl"
#include "/lib/water.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
#ifdef DISTANT_HORIZONS
// What Distant Horizons drew that is opaque (its water is drawn here too, see dh.fsh).
uniform sampler2D dhDepthTex1;
uniform mat4 dhProjection;
uniform mat4 dhProjectionInverse;
#define REFLECTION_DH_DEPTH dhDepthTex1
#endif

#include "/lib/reflection.glsl"
#define SKY_PROBE_DEPTH depthtex1
#define SKY_PROBE_DH_DEPTH dhDepthTex1
#include "/lib/sky_probe.glsl"

in vec2 v_uv;

/* RENDERTARGETS: 0,3 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_previous; // the frame kept for the next one's reflections (see gbuffer.glsl)

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

// Light (water's index of refraction) going into air.
const float water_ior = 1.333;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

float fresnel_water(float cos_theta) {
    return fresnel_schlick(cos_theta, vec3(0.02)).x;
}

// The sun's (or the moon's) glint on the water (view_dir from the eye), spread by the roughness of the waves too
// small for the pixel (their mean square slope m, see water_surface in water.glsl): a sharp glint on each wave up
// close, a broad glitter far off. (For small slopes GGX's alpha squared is the mean square slope, as Beckmann's is.)
vec3 water_glint(vec3 n, vec3 view_dir, float m, float lit) {
    return sun_glint(n, view_dir, sqrt(0.0009 + m), vec3(0.02), lit);
}

// The sky the water mirrors (rgb, its radiance) and how much of it (a, Fresnel), averaged over the slopes of the
// waves too small for the pixel (mean square slope m around its normal n), as Bruneton, Neyret and Holzschuch
// filter the sky over that spread: facets drawn from it (the slopes of many small waves together make a Gaussian),
// each counted by how much of it faces you. At a glancing view the facets tipped towards you mirror the sky higher
// up, and mirror less of it, so the far sea keeps its own colour as a real one does instead of turning into a pale
// mirror of the horizon. noise turns the facets from pixel to pixel.
vec4 water_sky_mirror(vec3 n, vec3 dir, float m, float noise) {
    const int taps = 8;
    float spread = sqrt(0.5 * m); // per axis
    vec3 sky = vec3(0.0);
    float f_sum = 0.0;
    float w_sum = 0.0;
    for (int i = 0; i < taps; i++) {
        float u = (float(i) + 0.5) / float(taps);
        float r = sqrt(-2.0 * log(1.0 - u)) * spread; // a Gaussian's radius (Box and Muller)
        float a = float(i) * 2.399963 + noise * 6.283185;
        vec3 facet = normalize(n + vec3(r * cos(a), 0.0, r * sin(a)));
        float facing = dot(-dir, facet);
        if (facing <= 0.0) {
            continue; // turned away: you can't see it
        }
        float w = facing / max(facet.y, 0.05); // how much of the surface it takes up as you see it
        vec3 refl = reflect(dir, facet);
        refl = normalize(vec3(refl.x, max(refl.y, 0.02), refl.z)); // a wave can't mirror the water under it
        float f = fresnel_water(facing);
        sky += w * f * sky_radiance(refl);
        f_sum += w * f;
        w_sum += w;
    }
    if (w_sum <= 0.0) {
        vec3 refl = reflect(dir, n);
        return vec4(sky_radiance(normalize(vec3(refl.x, max(refl.y, 0.02), refl.z))), fresnel_water(dot(-dir, n)));
    }
    return vec4(sky / max(f_sum, 1e-8), f_sum / w_sum);
}

#ifdef LIT_AIR
// What the deferred pass drew at a pixel (point: its unjittered centre) before it put the light in the air in front
// of it: the air up to the opaque surface there (or Distant Horizons' terrain, or the sky's start), as it worked it out.
vec3 air_taken_off(vec3 seen, vec2 point) {
    vec2 ndc = point / render_size() * 2.0 - 1.0;
    float d = texelFetch(depthtex1, ivec2(point), 0).r;
    float z = -unproject(gbufferProjectionInverse, ndc, d).z;
    if (d >= 1.0) {
        z = air_sky_start * -normalize(unproject(gbufferProjectionInverse, ndc, 1.0)).z;
#ifdef DISTANT_HORIZONS
        float dh = texelFetch(dhDepthTex1, ivec2(point), 0).r;
        if (dh < 1.0) {
            z = -unproject(dhProjectionInverse, ndc, dh).z;
        }
#endif
    }
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0));
    vec4 air = air_seen(point, z, dir);
    return (seen - air.rgb) / max(air.a, 1e-3);
}
#endif

// How far the opaque surface a pixel shows is (blocks), along the ray through ndc: Minecraft's, else Distant
// Horizons' terrain past it; -1 where there is only the sky.
float opaque_distance(ivec2 pixel, vec2 ndc) {
    float d = texelFetch(depthtex1, pixel, 0).r;
    if (d < 1.0) {
        return length(unproject(gbufferProjectionInverse, ndc, d));
    }
#ifdef DISTANT_HORIZONS
    float dh = texelFetch(dhDepthTex1, pixel, 0).r;
    if (dh < 1.0) {
        return length(unproject(dhProjectionInverse, ndc, dh));
    }
#endif
    return -1.0;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        out_color = vec4(0.0); // outside the frame while upscaling
        out_previous = out_color;
        return;
    }
    vec3 scene = texelFetch(colortex0, texel, 0).rgb;
    vec4 water = texelFetch(colortex4, texel, 0);
    bool surface = water.a > 0.5;
    bool inside = isEyeInWater == 1;
    float depth_all = texelFetch(depthtex0, texel, 0).r;
    if ((!surface && !inside) || depth_all < hand_depth_limit) {
        out_color = vec4(scene, 1.0); // no water here, or the hand in front of it
        out_previous = out_color;
        return;
    }

    // This pixel as drawn (jittered), the direction it looks in, and how far the opaque surface behind is.
    vec2 ndc = (vec2(texel) + 0.5) / size * 2.0 - 1.0;
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0));
    vec3 eye = gbufferModelViewInverse[3].xyz;
    float found = opaque_distance(texel, ndc);
    bool sky_behind = found < 0.0;
    float dist_opaque = sky_behind ? 1e4 : found;
    // The water's surface: how far it is, and how rough the waves too small for this pixel make it.
    vec2 water_far = surface ? texelFetch(colortex5, texel, 0).rg : vec2(0.0);
    float dist_water = water_far.r;
    float roughness = water_far.g;
    float dist_glass = texelFetch(colortex6, texel, 0).r;
    float noise = pixel_noise();

    vec3 color;
    if (inside) {
        // Under water: water up to the first surface that isn't (the water's own from below, glass, what is opaque).
        float end = dist_opaque;
        if (surface) {
            end = min(end, dist_water);
        }
        if (dist_glass > 0.0) {
            end = min(end, dist_glass);
        }
        vec3 beyond = scene;
        if (surface && dist_water <= end + 1e-3) {
            // The surface from below: facing you, it bends the light from above towards you through a window about
            // 97° across, and past its edge mirrors all of it (total internal reflection).
            vec3 n = decode_normal(water.rg);
            n = dot(n, dir) > 0.0 ? -n : n;
            float cos_i = dot(-dir, n);
            float sin2_t = water_ior * water_ior * (1.0 - cos_i * cos_i);
            float f = 1.0;
            if (sin2_t < 1.0) {
                f = fresnel_water(sqrt(1.0 - sin2_t));
                vec3 t = refract(dir, n, water_ior);
                if (sky_behind && dist_glass <= 0.0) {
                    beyond = sky_radiance(normalize(vec3(t.x, max(t.y, 0.02), t.z))) * exposure();
                }
            }
            // What it mirrors: the water below, and what the screen shows down there (the bottom, a boat's hull).
            vec3 p = eye + dir * dist_water;
            vec3 r = reflect(dir, n);
            float m_end = water_ray_limit;
            vec3 m_seen = vec3(0.0);
#if REFLECTION_QUALITY > 0
            vec3 hit = reflection_trace(depthtex1, mat3(gbufferModelView) * p + gbufferModelView[3].xyz,
                    mat3(gbufferModelView) * r, noise, hand_depth_limit);
            if (hit.z > 0.0) {
                ivec2 h = ivec2(hit.xy);
                vec3 o = mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse,
                        (vec2(h) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0,
                        texelFetch(depthtex1, h, 0).r) + gbufferModelViewInverse[3].xyz;
                m_end = mix(water_ray_limit, min(length(o - p), water_ray_limit), hit.z);
                m_seen = texelFetch(colortex0, h, 0).rgb * hit.z;
            }
#endif
            vec3 mirrored = vec3(0.0);
            vec3 m_through;
            water_ray(p, r, 0.0, m_end, 6, noise, mirrored, m_through);
            beyond = beyond * (1.0 - f) + (mirrored * exposure() + m_seen * m_through) * f;
        }
        vec3 light, through;
        water_ray(eye, dir, 0.0, end, water_steps_inside, noise, light, through);
        color = beyond * through + light * exposure();
    } else {
        // From above: the water under the surface, then the surface, reflecting the world where it sees it.
        vec3 n = decode_normal(water.rg);
        n = dot(n, dir) > 0.0 ? -n : n;
        vec3 p = eye + dir * dist_water;
        vec3 p_view = mat3(gbufferModelView) * p + gbufferModelView[3].xyz;
        // How much of the sky the surface sees: more than Minecraft's sky light says under something high (see
        // sky_grid.glsl and sky_probe.glsl).
        float sky_light_mc = water.b;
        float seen = -1.0;
        if (water.b < 0.999) {
            seen = sky_seen_around(p, vec3(0.0, 1.0, 0.0), water.b, noise, false, true);
            water.b = max(water.b, sky_seen_level(seen));
        }
        vec2 jitter = vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);

        // What lies under it, along the ray the surface bends: about where that ray meets the bottom, if the screen
        // shows water over something there (not the shore in front of it). Where it doesn't (that point is off the
        // screen or behind the hand), what the pixel itself shows under the surface, through as much water as the
        // bent ray would go through down to the same depth, so the two don't differ in how deep they look.
        // The deferred pass put the light in the air in front of what is under the water (see air.glsl), all the way
        // to it; the water's own light takes over at the surface, so take that air off again (the air in front of the
        // surface goes on at the end).
        vec3 under = scene;
#ifdef LIT_AIR
        vec2 unjittered = vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
        under = air_taken_off(scene, unjittered);
#endif
        vec3 under_from = eye;
        vec3 under_dir = dir;
        float under_a = dist_water;
        float under_b = dist_opaque;
        if (!sky_behind) {
            vec3 t = refract(dir, n, 1.0 / water_ior);
            float below = max(p.y - (eye.y + dir.y * dist_opaque), 0.0);
            under_from = p;
            under_dir = t;
            under_a = 0.0;
            under_b = min(below / max(-t.y, 0.05), dist_opaque - dist_water);
            vec3 q = p + t * min(dist_opaque - dist_water, 4.0);
            vec3 at = reflection_pixel(mat3(gbufferModelView) * q + gbufferModelView[3].xyz);
            if (at.z > 0.0 && all(greaterThanEqual(at.xy, vec2(0.0))) && all(lessThan(at.xy, size))) {
                ivec2 bent = ivec2(at.xy);
                vec2 bent_ndc = (vec2(bent) + 0.5 - jitter) / size * 2.0 - 1.0;
                bool hand = texelFetch(depthtex1, bent, 0).r < hand_depth_limit;
                float bent_dist = hand ? -1.0 : opaque_distance(bent, bent_ndc);
                if (texelFetch(colortex4, bent, 0).a > 0.5 && bent_dist > 0.0) {
                    vec3 o = eye + bent_dist
                            * normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, bent_ndc, 1.0));
                    if (bent_dist > texelFetch(colortex5, bent, 0).r) {
                        under = texelFetch(colortex0, bent, 0).rgb;
#ifdef LIT_AIR
                        under = air_taken_off(under, vec2(bent) + 0.5 - jitter);
#endif
                        under_from = p;
                        under_dir = normalize(o - p);
                        under_a = 0.0;
                        under_b = length(o - p);
                    }
                }
            }
        }
        // The shore around (see shore.glsl): a wall or the bank beside the water hides part of the sky from it, so
        // the water against it is a little darker (the light it scatters and the sky it mirrors) and the wall reads
        // as going down into it.
        vec2 shore = vec2(0.0, 1.0);
#ifdef LIT_SHORE
        // (Distant Horizons' water, which isn't in Minecraft's depth, isn't in the shadow pass either, so the map
        // doesn't know it: open water.)
        if (depth_all < 1.0) {
            shore = shore_around(p);
        }
#endif
        float hidden = 0.35 * smoothstep(0.06, 0.5, shore.x);

        vec3 light, through;
        water_ray(under_from, under_dir, under_a, under_b, 10, noise, light, through);
        vec3 body = under * through + light * exposure() * (1.0 - hidden);

        // The sky it mirrors and how much, over the spread of the waves too small to see.
        vec4 mirror = water_sky_mirror(n, dir, roughness, noise);
        float f = mirror.a;
        vec3 r = reflect(dir, n);
        r = normalize(vec3(r.x, max(r.y, 0.02), r.z)); // a wave can't turn the reflection down into the water
        float open_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, water.b);
        vec3 reflection = mirror.rgb * open_sky * exposure() * (1.0 - hidden);
        float screen_hit = 0.0;
#if REFLECTION_QUALITY > 0
        vec3 hit = reflection_trace(depthtex1, p_view, mat3(gbufferModelView) * r, noise, hand_depth_limit);
        if (hit.z > 0.0) {
            reflection = mix(reflection, texelFetch(colortex0, ivec2(hit.xy), 0).rgb, hit.z);
            screen_hit = hit.z;
        }
#endif
        // The sun's glint where the shadow map says it gets there; past it, where Minecraft's own sky light does (see
        // direct_visibility).
        float lit = smoothstep(12.0 / 15.0, 14.5 / 15.0, sky_light_mc);
#ifdef SHADOW_MAP
        vec3 coord = shadow_coord(p);
        if (all(greaterThan(coord, vec3(0.0))) && all(lessThan(coord, vec3(1.0)))) {
            lit = open_sky * texture(shadowtex1HW, vec3(coord.xy, coord.z - 0.00002));
        }
#endif
        color = body * (1.0 - f) + reflection * f + water_glint(n, dir, roughness, lit) * exposure();

        // Foam: where the ground closes in round the water (see shore.glsl), so it gathers in corners and round a
        // pillar and thins out along a straight wall, lapping in and out every few seconds; more of it the rougher
        // the water (its swell, the rain); and where something churns it (a wake). It drifts with the waves, is lit
        // by their slope, lets the water show through where it's thin, and goes on under the surface as bubbles.
        float churn = 0.0;
        float rough = 1.0;
#ifdef LIT_SHORE
        vec2 world = vec2(cameraPositionInt.xz & 4095) + cameraPositionFract.xz + p.xz;
        rough = mix(0.5, 1.0, max(cg_swell * smoothstep(0.2, 0.8, shore.y), cg_rain_here));
        churn = smoothstep(0.04, 0.45, shore.x * (0.7 + 0.6 * shore_lap(world))) * rough;
#else
        if (!sky_behind) {
            churn = 1.0 - smoothstep(0.05, 0.4, p.y - (eye.y + dir.y * dist_opaque));
        }
#endif
#ifdef LIT_WATER_RIPPLES
        churn = max(churn, smoothstep(0.4, 1.2, length(ripple_slope(p))));
#endif
        if (churn > 0.0) {
            vec3 w = wave_coord(p);
            w.xz -= n.xz * 0.004; // carried a little by the waves' slope
            vec2 drift = frameTimeCounter * vec2(0.031, -0.022);
            float bubbles = 0.5 * (wave_noise(w.xz * 9.0 + drift) + wave_noise(golden_turn * w.xz * 13.0 - drift));
            float clumps = wave_noise(golden_turn * w.xz * 2.7 - drift * 0.4);
            float foam = smoothstep(1.0 - churn, 1.2 - churn, bubbles * mix(0.88, 1.12, clumps));
            float under_foam = churn * smoothstep(0.35, 0.7, bubbles) * (1.0 - foam);
            if (foam > 0.0 || under_foam > 0.0) {
                vec3 white = light_surface(srgb_to_linear(vec3(0.8)), n, vec2(0.0, water.b), p, false, false, true, 1.0,
                        1.0, sky_light_mc) * exposure();
                // Bubbles just under the surface, through the water above them.
                color += white * (1.0 - f) * 0.12 * under_foam;
                // Thick foam a little shaded by its own bumps; thin foam lets the water through.
                color = mix(color, white * mix(0.85, 1.0, foam), foam * mix(0.55, 0.92, foam));
            }
        }
        // The light in the air and Minecraft's fog in front of it, as the deferred pass has them in front of what is
        // opaque.
        color = through_air(color, air_seen(vec2(texel) + 0.5 - jitter, -p_view.z, dir));
        color = apply_fog_lit(color, max(length(p.xz), abs(p.y)), dir, fog_range_shaped(1));
#if DEBUG_VIEW == 6
        color = vec3(sky_light_curve(sky_light_mc), max(seen, 0.0), seen < 0.0 ? 0.5 : 0.0) * 2.0;
#elif DEBUG_VIEW == 8
        color = vec3(f * 2.0, screen_hit, 0.25) * 2.0;
#endif
    }
    if (any(isnan(color))) {
        color = scene;
    }
#if DEBUG_VIEW == 3 || DEBUG_VIEW == 4
    color = scene; // what lies under the water as the deferred pass left it (see deferred.fsh)
#endif
    out_color = vec4(color, 1.0);
    out_previous = out_color;
}

// The overworld's water (its composite pass), after everything translucent is drawn and before DLSS: the water
// program only left its surface (see gbuffer.glsl), and this draws the water from it. From above: what lies under the
// surface seen through the water between them (dimmed and turning blue-green with depth, hazy with the light the
// water scatters, see water_body.glsl), with the sky and the sun reflected on top. From under water: the water
// between you and everything you see, lit where it is, so the far water stays as bright as the light there wherever
// you are; and the surface from below, which shows the sky through a round window above you (light bends into the
// water) and mirrors the water all around it.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"
#include "/lib/water_body.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

// Light (water's index of refraction) going into air.
const float water_ior = 1.333;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

float fresnel_schlick(float cos_theta) {
    return 0.02 + 0.98 * pow(1.0 - clamp(cos_theta, 0.0, 1.0), 5.0);
}

// The sun's (or the moon's) glint on the water: the light (klux) on a surface facing n, mirrored towards the eye
// (view_dir from the eye) by GGX microfacets, a little rougher far off so it doesn't sparkle from pixel to pixel.
vec3 water_glint(vec3 n, vec3 view_dir, float dist, float lit) {
    vec3 l = g_light_dir.xyz;
    vec3 v = -view_dir;
    float n_dot_l = dot(n, l);
    float n_dot_v = max(dot(n, v), 1e-3);
    if (n_dot_l <= 0.0 || lit <= 0.0) {
        return vec3(0.0);
    }
    vec3 h = normalize(l + v);
    float n_dot_h = max(dot(n, h), 0.0);
    float alpha = 0.03 + 0.0015 * dist;
    float a2 = alpha * alpha;
    float d = n_dot_h * n_dot_h * (a2 - 1.0) + 1.0;
    float ggx = a2 / (PI * d * d);
    float k = alpha * 0.5;
    float g = n_dot_l / (n_dot_l * (1.0 - k) + k) * n_dot_v / (n_dot_v * (1.0 - k) + k);
    float f = fresnel_schlick(dot(h, v));
    return g_light_color.rgb * lit * ggx * g * f / (4.0 * n_dot_v);
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        out_color = vec4(0.0); // outside the frame while upscaling
        return;
    }
    vec3 scene = texelFetch(colortex0, texel, 0).rgb;
    vec4 water = texelFetch(colortex4, texel, 0);
    bool surface = water.a > 0.5;
    bool inside = isEyeInWater == 1;
    float depth_all = texelFetch(depthtex0, texel, 0).r;
    if ((!surface && !inside) || depth_all < hand_depth_limit) {
        out_color = vec4(scene, 1.0); // no water here, or the hand in front of it
        return;
    }

    // This pixel as drawn (jittered), the direction it looks in, and how far the opaque surface behind is.
    vec2 ndc = (vec2(texel) + 0.5) / size * 2.0 - 1.0;
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0));
    vec3 eye = gbufferModelViewInverse[3].xyz;
    float depth_opaque = texelFetch(depthtex1, texel, 0).r;
    bool sky_behind = depth_opaque >= 1.0;
    float dist_opaque = sky_behind ? 1e4 : length(unproject(gbufferProjectionInverse, ndc, depth_opaque));
    float dist_water = surface ? texelFetch(colortex5, texel, 0).r : 0.0;
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
                f = fresnel_schlick(sqrt(1.0 - sin2_t));
                vec3 t = refract(dir, n, water_ior);
                if (sky_behind && dist_glass <= 0.0) {
                    beyond = sky_radiance(normalize(vec3(t.x, max(t.y, 0.02), t.z))) * exposure();
                }
            }
            vec3 mirrored = vec3(0.0);
            vec3 m_through;
            water_ray(eye + dir * dist_water, reflect(dir, n), 0.0, water_ray_limit, 6, noise, mirrored, m_through);
            beyond = beyond * (1.0 - f) + mirrored * exposure() * f;
        }
        vec3 light, through;
        water_ray(eye, dir, 0.0, end, water_steps_inside, noise, light, through);
        color = beyond * through + light * exposure();
    } else {
        // From above: the water under the surface, then the surface, reflecting the sky where it sees it.
        vec3 n = decode_normal(water.rg);
        n = dot(n, dir) > 0.0 ? -n : n;
        vec3 light, through;
        water_ray(eye, dir, dist_water, dist_opaque, 10, noise, light, through);
        vec3 body = scene * through + light * exposure();

        vec3 p = eye + dir * dist_water;
        float f = fresnel_schlick(dot(-dir, n));
        vec3 r = reflect(dir, n);
        r = normalize(vec3(r.x, max(r.y, 0.02), r.z));
        float open_sky = smoothstep(12.0 / 15.0, 14.5 / 15.0, water.b);
        vec3 reflection = sky_radiance(r) * open_sky;
        float lit = 1.0;
#ifdef SHADOW_MAP
        vec3 coord = shadow_coord(p);
        if (all(greaterThan(coord, vec3(0.0))) && all(lessThan(coord, vec3(1.0)))) {
            lit = texture(shadowtex1HW, vec3(coord.xy, coord.z - 0.00002));
        }
#endif
        lit *= open_sky;
        color = body * (1.0 - f) + (reflection * f + water_glint(n, dir, dist_water, lit)) * exposure();
        // Minecraft's fog in front of it, as the deferred pass fogs what is opaque.
        color = apply_fog_lit(color, max(length(p.xz), abs(p.y)), dir, fog_range_shaped(1));
    }
    if (any(isnan(color))) {
        color = scene;
    }
    out_color = vec4(color, 1.0);
}

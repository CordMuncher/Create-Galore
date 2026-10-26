// The overworld's deferred pass, first part: the cumulus' ray march (see clouds.glsl), one ray for every CLOUD_SCALE
// by CLOUD_SCALE render pixels, through a different point of that block each frame, so the gather pass
// (clouds_gather.csh) builds every pixel up over a few frames. Each ray sees the clouds as if nothing stood in front
// of them; the lighting pass puts them only where the sky shows (or, from above the clouds, over what lies under
// them). Ported from Alpha Piscium's clouds/RenderVolumetric.
//
// Each ray keeps the light the clouds scatter towards the eye, with the air in front of them (kcd/m², not exposed),
// how much of what is behind them gets through, and how far away they are (km), to find them again next frame.

#include "/lib/clouds.glsl"
#include "/lib/frame.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
#if CLOUD_SCALE == 1
const vec2 workGroupsRender = vec2(1.0, 1.0);
#elif CLOUD_SCALE == 2
const vec2 workGroupsRender = vec2(0.5, 0.5);
#else
const vec2 workGroupsRender = vec2(0.34, 0.34);
#endif

layout(rgba16f) writeonly uniform image2D cg_cloud_low_img;
layout(r16f) writeonly uniform image2D cg_cloud_dist_img;
uniform mat4 gbufferProjectionInverse;

// The light's way through the cloud above a step at pos (where the light's ray goes: a little spread around it, as
// light spreads inside a cloud): its optical depth, and HanPi's isotropic multiple scattering along it. Steps grow
// with the square of the distance, each placed at random within its own length.
void light_march(vec3 pos, vec3 light_ray, float coverage, vec2 place, float jitter, float boundary, out vec3 depth,
        out vec3 isotropic_ms) {
    depth = vec3(0.0);
    isotropic_ms = vec3(0.0);
    const float len = CLOUD_THICKNESS;
    for (int j = 0; j < cloud_light_steps; j++) {
        float x = (float(j) + 0.5) / float(cloud_light_steps);
        float step_len = 2.0 * x / float(cloud_light_steps) * len;
        float prefix = jitter * step_len;
        float offset = pow(float(j) / float(cloud_light_steps), 2.0) * len + prefix;
        vec3 p = pos + light_ray * offset;
        float h = length(p);
        if (h > cumulus_top) {
            break;
        }
        float density;
        float density_lod;
        float column;
#ifdef CLOUD_LIGHT_DETAIL
        bool detail = true;
#else
        bool detail = false;
#endif
        if (cumulus_density(vec3(p.x + place.x, p.y, p.z + place.y), linear_step(cumulus_bottom, cumulus_top, h), coverage,
                detail, density, density_lod, column)) {
            vec3 sigma_t = cumulus_extinction * density * cumulus_density_scale;
#ifdef CLOUD_ISOTROPIC_MS
            vec3 sigma_s = cumulus_scattering * density * cumulus_density_scale;
            vec3 u = depth + sigma_t * prefix;
            vec3 w = sigma_s * step_len * sigma_t / max(offset, 0.5 * step_len) * boundary;
            isotropic_ms += w * exp(-(0.001 + sqrt(0.003)) * u) * (1.0 - exp(-max(1.0 - cumulus_asymmetry, 0.0) * u));
#endif
            depth += sigma_t * step_len;
        }
    }
    isotropic_ms = 1.0 - exp(-max(isotropic_ms, 0.0));
}

void main() {
    ivec2 q = ivec2(gl_GlobalInvocationID.xy);
    vec2 size = render_size();
    ivec2 low_size = ivec2(ceil(size / float(CLOUD_SCALE)));
    if (any(greaterThanEqual(q, low_size))) {
        return;
    }

    // This ray's point of its block, in unjittered render pixels, and the direction it looks in.
    vec2 p = min((vec2(q) + cloud_offset()) * float(CLOUD_SCALE), size);
    vec4 view = gbufferProjectionInverse * vec4(p / size * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y), 0.0);

    vec3 in_scatter = vec3(0.0);
    float transmittance = 1.0;
    float dist = 0.0; // none: as far as can be

    // Where the ray meets the layer: from below, through its bottom; from above, through its top; from inside, here.
    float height_diff = 0.5 * (cumulus_bottom + cumulus_top) - origin.y;
    bool in_layer = abs(height_diff) < 0.5 * CLOUD_THICKNESS;
    float start = in_layer ? 0.0 : atm_ray_sphere(origin, dir, height_diff < 0.0 ? cumulus_top : cumulus_bottom);
    if ((in_layer || sign(height_diff) == sign(dir.y)) && start >= 0.0) {
        // And where it leaves it again, through the top or the bottom, at most cumulus_max_ray on.
        vec3 inside = origin + dir * (start + 0.1);
        float len_bottom = atm_ray_sphere(inside, dir, cumulus_bottom);
        float len_top = atm_ray_sphere(inside, dir, cumulus_top);
        float len = len_bottom;
        if (len_top > 0.0) {
            len = len_bottom > 0.0 ? min(len_top, len_bottom) : len_top;
        }
        len = len <= 0.0 ? cumulus_max_ray : min(len + 0.1, cumulus_max_ray);

        int steps = int(max(len / cumulus_max_ray * cloud_steps_max, cloud_steps_min));
        float step_len = len / float(steps + 1);
        float jitter = cloud_noise(q, 1u);
        float light_jitter = cloud_noise(q, 2u);
        vec3 light_dir;
        vec3 illuminance;
        cloud_light(cloud_noise(q, 3u), light_dir, illuminance);
        vec3 light_ray = cone_sample(light_dir, sun_angular_radius * 4.0, vec2(cloud_noise(q, 4u), cloud_noise(q, 5u)));
        vec3 phase = cumulus_phase(dot(dir, light_dir));
        vec3 ambient = cloud_ambient(dir);
        float coverage = cumulus_coverage();
        vec2 place = global(G_CLOUD_PLACE).xy;

        CloudAccum acc = CloudAccum(vec3(0.0), vec3(1.0));
        float weight_sum = 0.0;
        float dist_sum = 0.0;
        vec3 ray_start = origin + dir * start;
        for (int i = 0; i < steps; i++) {
            float t = (float(i) + jitter) * step_len;
            vec3 pos = ray_start + dir * t;
            float h = length(pos);
            float hf = linear_step(cumulus_bottom, cumulus_top, h);
            vec3 pattern_pos = vec3(pos.x + place.x, pos.y, pos.z + place.y);
            float density;
            float density_lod;
            float column;
            if (cumulus_density(pattern_pos, hf, coverage, true, density, density_lod, column)) {
                density *= cumulus_density_scale;
                density_lod *= cumulus_density_scale;
#ifdef CLOUD_ISOTROPIC_MS
                float boundary = cumulus_isotropic_ms_weight(pattern_pos, hf, column, coverage, light_dir);
#else
                float boundary = 0.0;
#endif
                vec3 light_depth;
                vec3 isotropic_ms;
                light_march(pos, light_ray, coverage, place, light_jitter, boundary, light_depth, isotropic_ms);
#ifndef CLOUD_ISOTROPIC_MS
                isotropic_ms = vec3(0.0);
#endif
                vec3 before = acc.transmittance;
                cloud_step(acc, cumulus_scattering, cumulus_extinction, phase, ambient, cloud_light_at(pos, h, light_dir, illuminance),
                        density, density_lod, light_depth, isotropic_ms, step_len);
                // How far away the cloud is: each step by how much of it shows.
                float w = dot(before - acc.transmittance, vec3(1.0 / 3.0));
                weight_sum += w;
                dist_sum += w * (start + t);
            }
            if (acc.transmittance.g < 0.01) {
                break;
            }
        }

        transmittance = dot(acc.transmittance, vec3(1.0 / 3.0));
        if (weight_sum > 1e-4) {
            dist = dist_sum / weight_sum;
            // The air between the eye and the cloud: its light where the cloud hides what is behind, and its haze
            // over the cloud (the lighting pass puts the sky behind through the cloud's transmittance).
            vec3 t_air;
            vec3 air = atm_segment(origin, dir, dist, 8, g_sun_dir.xyz, sun_illuminance, g_moon_dir.xyz, moon_illuminance(), t_air);
            in_scatter = t_air * acc.in_scatter + (1.0 - transmittance) * weather(air);
        }
    }

    imageStore(cg_cloud_low_img, q, vec4(in_scatter, transmittance));
    imageStore(cg_cloud_dist_img, q, vec4(dist));
}

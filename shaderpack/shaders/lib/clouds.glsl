// The overworld's clouds (see settings.glsl): cumulus, a lit volume ray marched at a fraction of the render size and
// built up over frames (clouds_march.csh, clouds_gather.csh), and cirrus, a thin sheet nine kilometres up, worked
// out for every pixel by the deferred pass. Both go in front of the sky and behind everything else (seen from above
// the cumulus, in front of what lies under them), and the cumulus shade the ground.
//
// Ported from Alpha Piscium (GPL-3.0), techniques/atmospherics/clouds, with its textures (textures/): Andrew
// Schneider's Nubis shapes (a coverage map eroded by billows and wisps over height curves), the clouds' media and
// phase functions from the OPAC database, Wo De Tian's multiple scattering approximation, and HanPi Volume Cloud's
// isotropic multiple scattering (AshenOneArt, MIT, https://github.com/AshenOneArt/HPVolumeCloud). The constants are
// moved from ACES AP0 into the pack's linear sRGB.
//
// Lengths are in km, as the air's (see atmosphere.glsl): the planet's centre is the origin, the camera straight
// above it. The shapes are looked up at the camera's place in the world plus the wind (frame_globals.csh); every
// cumulus pattern repeats each 128 km, so that place is kept within 128 km and stays exact anywhere in the world.

#if !defined LIB_CLOUDS
#define LIB_CLOUDS

#include "/lib/settings.glsl"
#include "/lib/globals.glsl"
#include "/lib/noise.glsl"
#include "/lib/sky.glsl"

uniform sampler2D cg_cloud_phase_tex;     // phase functions of cirrus, cumulus and stratus (RGBM, in AP0)
uniform sampler2D cg_cumulus_base_tex;    // the coverage's finer octaves, 32 km across
uniform sampler3D cg_cumulus_detail1_tex; // billows
uniform sampler3D cg_cumulus_detail2_tex; // finer billows, and wisps
uniform sampler3D cg_cumulus_curl_tex;    // curl noise that twists them
uniform sampler2D cg_cirrus_tex;
uniform sampler2D cg_cloud_amb_tex;       // the light around the clouds (cloud_ambient.csh)
uniform int frameCounter;

// Quality: how many render pixels a ray stands for (per side), the march's steps (fewer for short rays, more for
// long ones), the steps towards the light, and whether those see the billows.
#if CLOUD_QUALITY == 1
#define CLOUD_SCALE 3
const float cloud_steps_min = 24.0;
const float cloud_steps_max = 64.0;
const int cloud_light_steps = 4;
#elif CLOUD_QUALITY == 2
#define CLOUD_SCALE 2
const float cloud_steps_min = 32.0;
const float cloud_steps_max = 96.0;
const int cloud_light_steps = 6;
#elif CLOUD_QUALITY == 3
#define CLOUD_SCALE 2
const float cloud_steps_min = 48.0;
const float cloud_steps_max = 128.0;
const int cloud_light_steps = 8;
#define CLOUD_LIGHT_DETAIL
#define CLOUD_ISOTROPIC_MS
#else
#define CLOUD_SCALE 1
const float cloud_steps_min = 48.0;
const float cloud_steps_max = 128.0;
const int cloud_light_steps = 8;
#define CLOUD_LIGHT_DETAIL
#define CLOUD_ISOTROPIC_MS
#endif

// The layers, from the planet's centre.
const float cumulus_bottom = atm_bottom + CLOUD_HEIGHT;
const float cumulus_top = cumulus_bottom + CLOUD_THICKNESS;
const float cirrus_height = atm_bottom + 9.0;
const float cumulus_max_ray = 50.0; // km: cumulus farther than this are lost in the haze

// The media, per km at density 1: OPAC's continental cumulus (clean) and cirrus at -25 °C.
const vec3 cumulus_scattering = vec3(0.173960, 0.181482, 0.195547);
const vec3 cumulus_extinction = vec3(0.173961, 0.181482, 0.195547);
const vec3 cumulus_asymmetry = vec3(0.818549, 0.870445, 0.948640);
const float cumulus_droplet_radius = 5.77; // µm
const vec3 cirrus_scattering = vec3(5.898218, 6.217422, 6.741531);
const vec3 cirrus_extinction = vec3(5.898218, 6.217422, 6.741531);
const vec3 cirrus_asymmetry = vec3(0.749651, 0.786902, 0.849654);
const float cumulus_density_scale = 96.0 * CLOUD_DENSITY;

float linear_step(float a, float b, float x) {
    return clamp((x - a) / (b - a), 0.0, 1.0);
}

vec3 linear_step(float a, float b, vec3 x) {
    return clamp((x - a) / (b - a), 0.0, 1.0);
}

// ---- Phase functions: how much of the light a cloud scatters into each angle from its path.

const float uniform_phase = 1.0 / (4.0 * PI);

float phase_hg(float cos_theta, float g) {
    float g2 = g * g;
    return uniform_phase * (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * cos_theta, 1.5);
}

vec3 phase_hg(float cos_theta, vec3 g) {
    vec3 g2 = g * g;
    return uniform_phase * (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * cos_theta, vec3(1.5));
}

float phase_cornette_shanks(float cos_theta, float g) {
    float k = 3.0 / (8.0 * PI) * (1.0 - g * g) / (2.0 + g * g);
    return k * (1.0 + cos_theta * cos_theta) / pow(1.0 + g * g - 2.0 * g * cos_theta, 1.5);
}

float phase_draine(float cos_theta, float g, float alpha) {
    float g2 = g * g;
    float hg = (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * cos_theta, 1.5);
    return uniform_phase * hg * (1.0 + alpha * cos_theta * cos_theta) / (1.0 + alpha * (1.0 + 2.0 * g2) / 3.0);
}

// Jendersie and d'Eon's fit for water droplets of diameter d (µm).
float phase_hg_draine(float cos_theta, float d) {
    float g_hg = exp(-0.0990567 / (d - 1.67154));
    float g_d = exp(-2.20679 / (d + 3.91029) - 0.428934);
    float alpha = exp(3.62489 - 8.29288 / (d + 5.52825));
    float w_d = exp(-0.599085 / (d - 0.641583) - 0.665888);
    return mix(phase_hg(cos_theta, g_hg), phase_draine(cos_theta, g_d, alpha), w_d);
}

// OPAC's phase functions (row 0 cirrus, 1 cumulus, 2 stratus), packed towards the forward peak.
vec3 cloud_phase_table(float cos_theta, float row) {
    const mat3 ap0_to_srgb = mat3(
        2.5214008886, -0.2762140616, -0.0153202001,
        -1.1339957494, 1.3725955663, -0.1529925618,
        -0.3875618568, -0.0962823557, 1.1683871996);
    float x = acos(clamp(cos_theta, -1.0, 1.0));
    float u = clamp((0.672617934627 - 0.0713555761181 * x + 0.0299320735609 * x * x) * pow(x, 0.264767018876), 0.0, 1.0);
    vec4 rgbm = texture(cg_cloud_phase_tex, vec2(u, (row + 0.5) / 3.0));
    return max(ap0_to_srgb * (rgbm.rgb * rgbm.a), 0.0);
}

vec3 cumulus_phase(float cos_theta) {
    return mix(vec3(phase_hg_draine(cos_theta, cumulus_droplet_radius * 2.0)), cloud_phase_table(cos_theta, 1.0), 0.9);
}

vec3 cirrus_phase(float cos_theta) {
    return mix(phase_hg(cos_theta, cirrus_asymmetry), cloud_phase_table(cos_theta, 0.0), 0.6);
}

// ---- Sampling.

// Where in its CLOUD_SCALE by CLOUD_SCALE block of render pixels this frame's cumulus ray goes (0..1 across it).
vec2 cloud_offset() {
    return r2_seq2(uint(frameCounter));
}

// A random number for a pixel this frame, a different one for each seed: an R2 dither, which keeps neighbouring
// pixels' numbers apart, moved on each frame.
float cloud_noise(ivec2 pixel, uint seed) {
    vec2 p = vec2(pixel) + floor(r2_seq2(seed) * 128.0);
    return fract(p.x * 0.7548776662 + p.y * 0.56984029 + float(uint(frameCounter) & 1023u) * 0.41421356 + r2_seq1(seed));
}

// A direction within a cone of half_angle around center, evenly over its cap, for r (0..1 each).
vec3 cone_sample(vec3 center, float half_angle, vec2 r) {
    float cos_theta = 1.0 - r.y * (1.0 - cos(half_angle));
    float sin_theta = sqrt(max(0.0, 1.0 - cos_theta * cos_theta));
    float phi = 2.0 * PI * r.x;
    vec3 t = normalize(cross(abs(center.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0), center));
    vec3 b = cross(center, t);
    return normalize((t * cos(phi) + b * sin(phi)) * sin_theta + center * cos_theta);
}

// ---- The weather and the light.

// How much of the sky the cumulus cover: the option's share on a clear day, most of it in rain and nearly all of it
// in a thunderstorm.
float cumulus_coverage() {
    float rain = mix(max(CLOUD_COVERAGE, 0.8), 0.95, thunderStrength);
    return mix(CLOUD_COVERAGE, rain, rainStrength);
}

// The light the clouds are lit by: the sun until it is about 5° under the horizon (high up, they see it after the
// ground no longer does), then the moon. dither (0..1, a different one per pixel and frame) spreads the change
// over a few degrees; the gather pass blends it.
void cloud_light(float dither, out vec3 light_dir, out vec3 illuminance) {
    vec3 sun = g_sun_dir.xyz;
    float sun_factor = smoothstep(-0.115, -0.06, sun.y) + dither * 0.2 - 0.1;
    if (sun_factor >= 0.5) {
        light_dir = sun;
        illuminance = sun_illuminance;
    } else {
        light_dir = g_moon_dir.xyz;
        illuminance = moon_illuminance();
    }
}

// The light's illuminance at p (h from the centre) through the air, none where the planet is in the way.
vec3 cloud_light_at(vec3 p, float h, vec3 light_dir, vec3 illuminance) {
    if (atm_ray_sphere(p, light_dir, atm_bottom) >= 0.0) {
        return vec3(0.0);
    }
    return illuminance * atm_trans(h, dot(p / h, light_dir));
}

// The light around the clouds seen along dir: the sky above and the ground below, weighted by how much of each a
// cloud scatters towards the eye. A table of 16 by 16 directions, longitude across and latitude up (see
// cloud_ambient.csh), the same for both layers.
vec3 cloud_ambient_dir(ivec2 texel) {
    float lon = ((float(texel.x) + 0.5) / 16.0 - 0.5) * 2.0 * PI;
    float lat = (float(texel.y) / 15.0 - 0.5) * PI;
    return vec3(sin(lon) * cos(lat), sin(lat), -cos(lon) * cos(lat));
}

vec3 cloud_ambient(vec3 dir) {
    float u = atan(dir.x, -dir.z) / (2.0 * PI) + 0.5;
    float v = asin(clamp(dir.y, -1.0, 1.0)) / PI + 0.5;
    vec2 t = vec2(u * 16.0 - 0.5, v * 15.0);
    ivec2 i = ivec2(floor(t));
    vec2 f = t - vec2(i);
    int x0 = (i.x + 16) % 16;
    int x1 = (i.x + 17) % 16;
    int y0 = clamp(i.y, 0, 15);
    int y1 = min(y0 + 1, 15);
    vec3 a = mix(texelFetch(cg_cloud_amb_tex, ivec2(x0, y0), 0).rgb, texelFetch(cg_cloud_amb_tex, ivec2(x1, y0), 0).rgb, f.x);
    vec3 b = mix(texelFetch(cg_cloud_amb_tex, ivec2(x0, y1), 0).rgb, texelFetch(cg_cloud_amb_tex, ivec2(x1, y1), 0).rgb, f.x);
    return mix(a, b, f.y);
}

// ---- The cumulus' shapes.

// A Worley cell pattern, smoothed (Inigo Quilez's smooth Voronoi, MIT), its cells repeating every 44: 128 km at the
// coverage's scale.
float cloud_worley(vec2 x) {
    ivec2 center = ivec2(floor(x));
    vec2 f = fract(x);
    float f1 = 0.0;
    float f2 = 0.0;
    float m = 1.0;
    for (int iy = -1; iy <= 1; iy++) {
        for (int ix = -1; ix <= 1; ix++) {
            ivec2 cell = ((center + ivec2(ix, iy)) % 44 + 44) % 44;
            vec3 h = hash_to_float(hash_pcg4d(uvec4(uvec2(cell), 0x1919810u, 0x85EBCA77u)).xyz);
            float dist = distance(f, h.xy + vec2(ix, iy));
            float v = h.z * h.z * clamp(1.0 - dist, 0.0, 1.0);
            const float w = 0.35;
            float k = h.z * clamp((m - dist) / w * 0.5 + 0.5, 0.0, 1.0);
            m = mix(m, dist, k) - w * k * (1.0 - k) / (1.0 + 3.0 * w);
            if (f1 < v) {
                f2 = f1;
                f1 = v;
            } else if (f2 < v) {
                f2 = v;
            }
        }
    }
    return clamp(1.0 - m - f2, 0.0, 1.0);
}

// Where the cumulus are, at a place (km, the patterns' coordinates): the cells with finer octaves over them,
// thinned out to the coverage.
float cumulus_base_coverage(vec2 pos, float coverage) {
    float c = cloud_worley(pos * (11.0 / 32.0)) + texture(cg_cumulus_base_tex, pos / 32.0).r * 0.75;
    c = max(c - (1.0 - coverage * coverage) * 0.8, 0.0);
    return c * (1.0 - (1.0 - coverage) * (1.0 - coverage));
}

// The billows' and the wisps' share through the layer's height (Alpha Piscium's fitted curves).
float cumulus_height_billowy(vec4 xs) {
    return exp2(dot(vec4(20.938597, -83.765418, 114.27168, -53.99707), xs) - 2.8940248);
}

float cumulus_height_wisp(vec4 xs) {
    return exp2(dot(vec4(3.6801126, -163.09651, 320.59657, -163.29147), xs) - 1.755622) + 0.01273534;
}

// The cumulus' density at pos (x and z the patterns' coordinates, y the height from the centre), hf the height's
// fraction through the layer: the coverage shaped into towers that narrow and thicken upwards, eroded by billows
// and wisps (with detail; without, the smooth shape alone). density_lod: the smooth shape, for the light around.
// False where there is none.
bool cumulus_density(vec3 pos, float hf, float coverage, bool detail, out float density, out float density_lod,
        out float coverage_out) {
    float base = cumulus_base_coverage(pos.xz, coverage);
    coverage_out = clamp(base, 0.0, 1.0);
    vec4 xs = vec4(hf, hf * hf, hf * hf * hf, hf * hf * hf * hf);
    density = base;
    density *= 1.5 - pow(hf, 2.55);
    density *= exp2(-hf * 6.0);
    density *= clamp(1.0 - exp2(48.0 * (hf - 1.0)), 0.0, 1.0);
    density *= clamp(1.0 - exp2(-128.0 * hf), 0.0, 1.0);
    density_lod = density * (1.0 + hf * 16.0);

    const float threshold = 0.02;
    if (density <= threshold) {
        density = 0.0;
        return false;
    }
    if (!detail) {
        density = density_lod;
        return true;
    }

    float coverage_sqrt = sqrt(coverage);
    pos += global(G_CLOUD_DETAIL).xyz;
    vec3 curl = texture(cg_cumulus_curl_tex, pos * vec3(0.5, 0.65, 0.5)).xyz;
    curl *= 0.2 + 0.3 * hf * hf;
    float edge = pow(1.0 - hf, 5.0);
    curl *= linear_step(mix(threshold, 1.0, edge), 0.0, density);

    float low = texture(cg_cumulus_detail1_tex, pos * 0.5 + curl * 0.25).r;
    float high = texture(cg_cumulus_detail2_tex, pos + curl * 0.5).r;
    float billowy = (1.0 - low) * (1.0 - low) * 0.4 + pow(1.0 - high, 3.0) * 0.2;
    float bottom_detail = 1.0 - billowy * (1.0 - smoothstep(0.0, 0.1, hf)) * 2.0;
    billowy *= cumulus_height_billowy(xs) * coverage_sqrt;
    density = linear_step(clamp(billowy, 0.0, 1.0), 1.0, density);

    float wisp = texture(cg_cumulus_detail2_tex, pos + curl * 0.25).r;
    wisp *= coverage_sqrt * cumulus_height_wisp(xs);
    density = linear_step(clamp(wisp, 0.0, 1.0), 1.0, density);

    float hard = linear_step(0.0, 0.3, hf);
    float min_density = mix(0.005, 0.02, hard);
    density *= sqrt(smoothstep(min_density, min_density + mix(0.05, 0.01, hard), density));
    // Denser towards the tops, and shaped underneath by the billows.
    density *= 1.0 + hf * 16.0;
    density *= bottom_detail;
    return density > 0.0;
}

#ifdef CLOUD_ISOTROPIC_MS
// HanPi Volume Cloud's boundary weight for its isotropic multiple scattering: less where the cloud's top faces away
// from the light, and less near the base, where light leaks out of the cloud.
float cumulus_isotropic_ms_weight(vec3 pos, float hf, float column, float coverage, vec3 light_dir) {
    const float s = 0.05;
    float xp = clamp(cumulus_base_coverage(pos.xz + vec2(s, 0.0), coverage), 0.0, 1.0);
    float xn = clamp(cumulus_base_coverage(pos.xz - vec2(s, 0.0), coverage), 0.0, 1.0);
    float zp = clamp(cumulus_base_coverage(pos.xz + vec2(0.0, s), coverage), 0.0, 1.0);
    float zn = clamp(cumulus_base_coverage(pos.xz - vec2(0.0, s), coverage), 0.0, 1.0);
    vec3 normal = normalize(vec3(-(xp - xn) * CLOUD_THICKNESS / (2.0 * s), 1.0, -(zp - zn) * CLOUD_THICKNESS / (2.0 * s)));
    float top = mix(1.0, clamp((dot(normal, light_dir) + 0.5) / 1.5, 0.0, 1.0), 0.25);
    float bottom = 1.0 - exp(-max(hf, 0.0) / (0.1 * mix(1.0, 4.0, column)));
    return top * bottom;
}
#endif

// ---- Lighting a step.

struct CloudAccum {
    vec3 in_scatter;    // light scattered towards the eye so far
    vec3 transmittance; // how much of what is behind gets through so far
};

// One step of a march through a cloud medium: the light from the sun or the moon scattered towards the eye (through
// light_depth of cloud, light_air: its illuminance there before the cloud), the light around (ambient) and the
// light scattered many times (Wo De Tian's approximation, and HanPi's isotropic term), and what the step hides.
void cloud_step(inout CloudAccum acc, vec3 scattering, vec3 extinction, vec3 phase, vec3 ambient, vec3 light_air,
        float density, float density_lod, vec3 light_depth, vec3 isotropic_ms, float step_len) {
    vec3 s = scattering * density;
    vec3 e = extinction * density;
    vec3 t_step = exp(-e * step_len);
    vec3 light = light_air * exp(-light_depth);
    vec3 ambient_here = ambient * exp(-extinction * mix(density_lod, density, 0.8));

    vec3 irradiance = light * phase + ambient_here;
    irradiance += light_air * isotropic_ms * mix(vec3(uniform_phase), phase, 0.7);
    // Multiple scattering within a sphere of radius 2^-1.5 km around the step.
    const float ms_radius = 0.35355339;
    vec3 f_ms = (scattering / extinction) * (1.0 - exp(-ms_radius * e));
    f_ms = mix(f_ms, f_ms * 0.95, linear_step(0.9, 1.0, f_ms));
    irradiance += (light * uniform_phase + ambient_here) * f_ms / (1.0 - f_ms);

    vec3 in_scatter = irradiance * s;
    acc.in_scatter += (in_scatter - in_scatter * t_step) / max(e, vec3(1e-7)) * acc.transmittance;
    acc.transmittance *= t_step;
}

// ---- Cirrus.

// The cirrus' density at a place (km, their own coordinates): wisps from a texture at three scales, twisted by
// curl noise, where a larger noise lets them be.
float cirrus_density(vec2 pos) {
    vec2 curl = gradient_fbm_2d_grad(pos, 0.01, 0.8, 2.0, 3, rotate_2d(2.39996323));
    float coverage = gradient_fbm_2d(pos + vec2(-8.0, -4.0) + curl * 16.0, 0.04, 0.6, 1.9, 3, rotate_2d(0.25 * PI));
    coverage = pow(linear_step(0.5 - CIRRUS_COVERAGE * 1.5, 1.0, coverage), 3.0);
    float density = texture(cg_cirrus_tex, (pos + 0.114) * 0.12 + curl * 2.5).r * 0.125
            + texture(cg_cirrus_tex, (pos + 0.514) * 0.08 + curl * 0.8).r * 0.25
            + texture(cg_cirrus_tex, pos * 0.02 + curl * 0.4).r * 0.5;
    return coverage * density;
}

// The cirrus along dir from the camera: rgb the light they scatter towards the eye (kcd/m², not exposed), a how much
// of what is behind them gets through. One step a kilometre deep where the ray crosses their sheet.
vec4 cirrus(vec3 dir, float dither) {
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y), 0.0);
    float t = atm_ray_sphere(origin, dir, cirrus_height);
    if (t < 0.0 || dir.y < 0.0 && origin.y < cirrus_height) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    vec3 p = origin + dir * t;
    float density = cirrus_density(p.xz + global(G_CLOUD_PLACE).zw);
    if (density <= 1e-4) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    vec3 light_dir;
    vec3 illuminance;
    cloud_light(dither, light_dir, illuminance);
    float h = length(p);
    CloudAccum acc = CloudAccum(vec3(0.0), vec3(1.0));
    cloud_step(acc, cirrus_scattering, cirrus_extinction, cirrus_phase(dot(dir, light_dir)), cloud_ambient(dir),
            cloud_light_at(p, h, light_dir, illuminance), density, density * 0.5, vec3(0.0), vec3(0.0), 1.0);
    return vec4(acc.in_scatter, dot(acc.transmittance, vec3(1.0 / 3.0)));
}

// ---- Shadows on the ground.

// How much of the sun's (or the moon's) light the cumulus let through to scene_pos (blocks from the camera): their
// coverage where the light's ray crosses the middle of the layer. Cumulus are nearly opaque; their thin edges and
// the gaps between them let it through.
float cloud_shadow(vec3 scene_pos, vec3 light_dir) {
#ifdef CLOUD_SHADOWS
    vec3 p = vec3(scene_pos.x * 0.001, atm_height(scene_pos.y + cameraPosition.y), scene_pos.z * 0.001);
    float t = atm_ray_sphere(p, light_dir, 0.5 * (cumulus_bottom + cumulus_top));
    if (t < 0.0 || p.y > cumulus_top) {
        return 1.0;
    }
    float coverage = cumulus_base_coverage((p + light_dir * t).xz + global(G_CLOUD_PLACE).xy, cumulus_coverage());
    return 1.0 - 0.95 * smoothstep(0.02, 0.3, coverage);
#else
    return 1.0;
#endif
}

#endif

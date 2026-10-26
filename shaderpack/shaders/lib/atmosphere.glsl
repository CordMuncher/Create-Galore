// The overworld's air: Sébastien Hillaire's sky ("A Scalable and Production Ready Sky and Atmosphere Rendering
// Technique", EGSR 2020, and Epic Games' reference code, MIT), with the air, the haze and the ozone as Alpha Piscium
// (GPL-3.0) fits them to the U.S. Standard Atmosphere 1976 and Preetham's daylight model. Ported from Alpha Piscium's
// techniques/atmospherics/air, its constants moved from ACES AP0 into the pack's linear sRGB.
//
// Lengths are in km, from the planet's centre. One block is a metre, and sea level (y 62) is the ground.
//
// Three look-up tables, remade every frame by the begin passes (the haze changes through the day):
// - transmittance: how much light gets through the air from a height towards the top, by height and angle;
// - multiple scattering: the light the air scatters more than once, by height and the sun's angle (Hillaire's
//   isotropic approximation);
// - the sky view: the sky's radiance in every direction from where you are, by latitude and longitude.

#if !defined LIB_ATMOSPHERE
#define LIB_ATMOSPHERE

#ifndef PI
#define PI 3.14159265358979
#endif

uniform int worldTime;

const float atm_bottom = 6378.137;
const float atm_top = atm_bottom + 100.0;
const float atm_mie_height = 1.2;

// Scattering of the air (Rayleigh, also its extinction) and ozone's absorption, per km at sea level.
const vec3 atm_rayleigh = vec3(0.00507981, 0.01178463, 0.03004672);
const vec3 atm_ozone = vec3(4.81727e-5, 3.10798e-5, 0.0);
const float atm_mie_absorption = 0.1; // the haze's absorption, of its scattering
const float atm_mie_e = 2500.0;       // Klein-Nishina phase function, about as forward as g 0.7

// The look-up tables' sizes.
const ivec2 atm_trans_size = ivec2(256, 64);
const ivec2 atm_msctr_size = ivec2(32, 32);
const ivec2 atm_skyview_size = ivec2(256, 256);
const float atm_msctr_scale = 512.0; // the multiple scattering is small: stored times this for half floats

// The haze through the day (Alpha Piscium's defaults): thick at sunrise, clear in the afternoon. Preetham's
// turbidity, as 2^x.
float atm_turbidity_log2() {
    // Minecraft's day: 0 sunrise, 6000 noon, 12000 sunset, 18000 midnight.
    const float times[8] = float[8](21000.0, 23000.0, 1000.0, 6000.0, 9000.0, 12000.0, 15000.0, 18000.0);
    const float values[8] = float[8](4.5, 5.25, 4.0, 2.0, 1.0, 3.0, 3.5, 4.0);
    float t = float(worldTime % 24000);
    float result = values[7];
    for (int i = 0; i < 8; i++) {
        float start = times[i];
        float end = times[(i + 1) % 8];
        float span = mod(end - start, 24000.0);
        float into = mod(t - start, 24000.0);
        if (into < span) {
            result = mix(values[i], values[(i + 1) % 8], smoothstep(0.0, 1.0, into / span));
            break;
        }
    }
    return result;
}

struct Air {
    vec3 mie_scattering;
    vec3 mie_extinction;
};

Air atm_air() {
    // Preetham's haze coefficients, per km.
    float turbidity = exp2(atm_turbidity_log2());
    vec3 mie = max(vec3(-0.0053057, -0.00800144, -0.01284378) + vec3(0.00533341, 0.00804323, 0.01291086) * turbidity, 0.0);
    return Air(mie, mie * (1.0 + atm_mie_absorption));
}

// How dense the air (x), the haze (y) and the ozone (z, in 10^17 molecules/m³) are at a height above sea level.
vec3 atm_density(float altitude) {
    float rayleigh = exp2(0.00947927584794 - 0.138528179963 * altitude - 0.00235619411773 * altitude * altitude);
    float mie = exp(-altitude / atm_mie_height);
    float x = max(altitude, 0.0);
    float x2 = x * x;
    float x3 = x2 * x;
    float x4 = x2 * x2;
    float ozone = exp2(3.14463183276 + 0.0498300739786 * x - 0.13053950591 * x2 + 0.021937805502 * x3 - 0.000931031499395 * x4)
            + exp2(-15.9975955967 + 2.79421136239 * x - 0.128226752502 * x2 + 0.00249280242662 * x3 - 0.0000185558309121 * x4);
    return vec3(rayleigh, mie, ozone);
}

vec3 atm_extinction(Air air, vec3 density) {
    return atm_rayleigh * density.x + air.mie_extinction * density.y + atm_ozone * density.z;
}

// Where in the air a world height is: one block a metre, sea level the ground (and never under it).
float atm_height(float world_y) {
    return max(world_y - 62.0, 1.0) * 0.001 + atm_bottom;
}

float atm_phase_rayleigh(float cos_theta) {
    return 3.0 / (16.0 * PI) * (1.0 + cos_theta * cos_theta);
}

float atm_phase_mie(float cos_theta) {
    return atm_mie_e / (2.0 * PI * (atm_mie_e * (1.0 - cos_theta) + 1.0) * log(2.0 * atm_mie_e + 1.0));
}

// The nearest hit of a ray from origin along dir with a sphere around the planet's centre, -1 for none.
float atm_ray_sphere(vec3 origin, vec3 dir, float radius) {
    float b = dot(origin, dir);
    float c = dot(origin, origin) - radius * radius;
    float delta = b * b - c;
    if (delta < 0.0) {
        return -1.0;
    }
    float s = sqrt(delta);
    float t0 = -b - s;
    float t1 = -b + s;
    if (t1 < 0.0) {
        return -1.0;
    }
    return t0 >= 0.0 ? t0 : t1;
}

// ---- Transmittance table (Bruneton's parameterisation, as Hillaire's reference uses it).

vec2 atm_trans_uv(float height, float cos_zenith) {
    height = clamp(height, atm_bottom + 0.0001, atm_top - 0.0001);
    float H = sqrt(atm_top * atm_top - atm_bottom * atm_bottom);
    float rho = sqrt(max(0.0, height * height - atm_bottom * atm_bottom));
    float discriminant = height * height * (cos_zenith * cos_zenith - 1.0) + atm_top * atm_top;
    float d = max(0.0, -height * cos_zenith + sqrt(max(discriminant, 0.0)));
    float d_min = atm_top - height;
    float d_max = rho + H;
    return vec2((d - d_min) / (d_max - d_min), rho / H);
}

void atm_trans_params(vec2 uv, out float height, out float cos_zenith) {
    uv = clamp(uv, 1.0 / vec2(atm_trans_size), 1.0 - 1.0 / vec2(atm_trans_size));
    float H = sqrt(atm_top * atm_top - atm_bottom * atm_bottom);
    float rho = H * uv.y;
    height = sqrt(rho * rho + atm_bottom * atm_bottom);
    float d_min = atm_top - height;
    float d_max = rho + H;
    float d = d_min + uv.x * (d_max - d_min);
    cos_zenith = d == 0.0 ? 1.0 : clamp((H * H - rho * rho - d * d) / (2.0 * height * d), -1.0, 1.0);
}

// ---- Multiple scattering table: the sun's angle across, the height up.

vec2 atm_msctr_uv(float height, float cos_zenith) {
    vec2 uv = vec2(clamp(cos_zenith * 0.5 + 0.5, 0.0, 1.0), clamp((height - atm_bottom) / (atm_top - atm_bottom), 0.0, 1.0));
    vec2 size = vec2(atm_msctr_size);
    return (uv + 0.5 / size) * (size / (size + 1.0));
}

// ---- Sky view table: longitude across, latitude up, packed towards the horizon where the sky changes fastest.
// Longitude 0.5 looks north (-z), 0.75 east (+x).

vec2 atm_skyview_uv(vec3 dir) {
    float lat = asin(clamp(dir.y, -1.0, 1.0));
    float lon = atan(dir.x, -dir.z) / (2.0 * PI) + 0.5;
    float v = 0.5 + 0.5 * sign(lat) * sqrt(abs(lat) / (0.5 * PI));
    vec2 size = vec2(atm_skyview_size);
    return (vec2(lon, v) + 0.5 / size) * (size / (size + 1.0));
}

vec3 atm_skyview_dir(vec2 uv) {
    vec2 size = vec2(atm_skyview_size);
    uv = (uv - 0.5 / size) * (size / (size - 1.0));
    float s = sign(uv.y - 0.5);
    float t = 2.0 * abs(uv.y - 0.5);
    float lat = s * t * t * 0.5 * PI;
    float angle = (uv.x - 0.5) * 2.0 * PI;
    float cos_lat = cos(lat);
    return vec3(sin(angle) * cos_lat, sin(lat), -cos(angle) * cos_lat);
}

// ---- Using the tables (the passes after the begin passes that make them).

uniform sampler2D cg_trans_lut;
uniform sampler2D cg_msctr_lut;

vec3 atm_trans(float height, float cos_zenith) {
    return texture(cg_trans_lut, atm_trans_uv(height, cos_zenith)).rgb;
}

vec3 atm_msctr(float height, float cos_zenith) {
    return texture(cg_msctr_lut, atm_msctr_uv(height, cos_zenith)).rgb / atm_msctr_scale;
}

// Light scattered within a step of extinction e and transmittance t (Frostbite's energy-conserving integration).
vec3 atm_step_integral(vec3 in_scatter, vec3 e, vec3 t) {
    return (in_scatter - in_scatter * t) / max(e, vec3(1e-9));
}

// Rays under the horizon run on to a ground this far below sea level, so the haze glows on past the horizon line
// instead of ending at the planet's edge (Alpha Piscium's BOTTOM_OFFSET).
const float atm_below_ground = 8.0;

// Light from one source scattered towards the eye within a step at p (h from the centre), per unit of its
// illuminance: once (unless the planet shades p) and many times (the multiple scattering table).
vec3 atm_scatter_from(vec3 p, float h, vec3 light_dir, vec3 view_dir, vec3 rayleigh, vec3 mie, vec3 scattering) {
    vec3 up = p / h;
    float cos_zenith = dot(up, light_dir);
    float shadow = atm_ray_sphere(p, light_dir, atm_bottom - atm_below_ground) < 0.0 ? 1.0 : 0.0;
    float cos_theta = dot(view_dir, light_dir);
    vec3 single = shadow * atm_trans(h, cos_zenith) * (rayleigh * atm_phase_rayleigh(cos_theta) + mie * atm_phase_mie(cos_theta));
    return single + atm_msctr(h, cos_zenith) * scattering;
}

// The air's light towards the eye along a ray from origin for len km, lit by the sun and the moon (their
// illuminance above the air), in the given number of steps; transmittance: how much of what is behind gets through.
vec3 atm_segment(vec3 origin, vec3 dir, float len, int steps, vec3 sun_dir, vec3 sun_light, vec3 moon_dir, vec3 moon_light,
        out vec3 transmittance) {
    Air air = atm_air();
    float step_len = len / float(steps);
    vec3 radiance = vec3(0.0);
    vec3 depth = vec3(0.0);
    for (int i = 0; i < steps; i++) {
        vec3 p = origin + dir * ((float(i) + 0.5) * step_len);
        float h = max(length(p), atm_bottom);
        vec3 density = atm_density(h - atm_bottom);
        vec3 e = atm_extinction(air, density);
        vec3 t_step = exp(-e * step_len);
        vec3 rayleigh = atm_rayleigh * density.x;
        vec3 mie = air.mie_scattering * density.y;
        vec3 scattering = rayleigh + mie;
        vec3 in_scatter = atm_scatter_from(p, h, sun_dir, dir, rayleigh, mie, scattering) * sun_light
                + atm_scatter_from(p, h, moon_dir, dir, rayleigh, mie, scattering) * moon_light;
        radiance += exp(-depth) * atm_step_integral(in_scatter, e, t_step);
        depth += e * step_len;
    }
    transmittance = exp(-depth);
    return radiance;
}

#endif

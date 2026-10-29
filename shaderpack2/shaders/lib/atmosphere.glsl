// The air of a planet the size of Earth, after Hillaire's "A Scalable and Production Ready Sky and Atmosphere
// Rendering Technique" (2020): a table of how much light gets through the air (transmittance), one of the light
// scattered many times over (multiple scattering), and the sky as the camera sees it (the sky view), all redone each
// frame by program/sky.csh. Lengths in kilometres. Light comes in units of the sun's light above the air.

#if !defined LIB_ATMOSPHERE
#define LIB_ATMOSPHERE

#include "/lib/common.glsl"

const float atmo_bottom = 6360.0;
const float atmo_top = 6460.0;
const vec3 rayleigh_scattering = vec3(5.802, 13.558, 33.1) * 1e-3;
const float rayleigh_height = 8.0;
const float mie_scattering = 3.996e-3;
const float mie_extinction = 4.44e-3;
const float mie_height = 1.2;
const float mie_g = 0.8;
const vec3 ozone_absorption = vec3(0.650, 1.881, 0.085) * 1e-3;
const vec3 ground_albedo = vec3(0.3);

const ivec2 trans_size = ivec2(256, 64);
const ivec2 ms_size = ivec2(32, 32);
const ivec2 skyview_size = ivec2(192, 108);

// The tables share one image (cg_sky_img, 256 x 312): the transmittance at the bottom, the multiple scattering
// above it, then the sky view (the sun's sky, then the moon's, each skyview_size).
const ivec2 sky_atlas_size = ivec2(256, 312);
const ivec2 trans_origin = ivec2(0, 0);
const ivec2 ms_origin = ivec2(0, 64);
const ivec2 skyview_origin = ivec2(0, 96);

// A table's coordinates (0..1 over the table) in the image.
vec2 sky_atlas_uv(vec2 uv, ivec2 size, ivec2 origin) {
    return (uv * vec2(size) + vec2(origin)) / vec2(sky_atlas_size);
}

struct Medium {
    vec3 scattering_r;
    float scattering_m;
    vec3 extinction;
};

Medium medium_at(float r) {
    float h = max(r - atmo_bottom, 0.0);
    float density_r = exp(-h / rayleigh_height);
    float density_m = exp(-h / mie_height);
    float density_o = max(0.0, 1.0 - abs(h - 25.0) / 15.0);
    Medium m;
    m.scattering_r = rayleigh_scattering * density_r;
    m.scattering_m = mie_scattering * density_m;
    m.extinction = m.scattering_r + mie_extinction * density_m + ozone_absorption * density_o;
    return m;
}

float rayleigh_phase(float cos_theta) {
    return 3.0 / (16.0 * PI) * (1.0 + cos_theta * cos_theta);
}

// Cornette-Shanks, a Henyey-Greenstein that keeps the forward peak but behaves at the back.
float mie_phase(float cos_theta, float g) {
    float g2 = g * g;
    float k = 3.0 / (8.0 * PI) * (1.0 - g2) / (2.0 + g2);
    return k * (1.0 + cos_theta * cos_theta) / pow(max(1.0 + g2 - 2.0 * g * cos_theta, 1e-4), 1.5);
}

// Distance along a ray from radius r with cosine mu to the sphere of radius R (the far side), or -1 if it misses.
float ray_sphere_far(float r, float mu, float R) {
    float d = r * r * (mu * mu - 1.0) + R * R;
    return d < 0.0 ? -1.0 : -r * mu + sqrt(d);
}

// To the near side (for the ground), or -1.
float ray_sphere_near(float r, float mu, float R) {
    float d = r * r * (mu * mu - 1.0) + R * R;
    if (d < 0.0) {
        return -1.0;
    }
    float t = -r * mu - sqrt(d);
    return t >= 0.0 ? t : -1.0;
}

// The transmittance table's coordinates (Bruneton's): uv from radius and view cosine, and back.
vec2 trans_uv(float r, float mu) {
    float H = sqrt(atmo_top * atmo_top - atmo_bottom * atmo_bottom);
    float rho = sqrt(max(r * r - atmo_bottom * atmo_bottom, 0.0));
    float d = max(ray_sphere_far(r, mu, atmo_top), 0.0);
    float d_min = atmo_top - r;
    float d_max = rho + H;
    vec2 uv = vec2((d - d_min) / (d_max - d_min), rho / H);
    // To the texel centres.
    return (uv * vec2(trans_size - 1) + 0.5) / vec2(trans_size);
}

void trans_r_mu(vec2 uv, out float r, out float mu) {
    uv = (uv * vec2(trans_size) - 0.5) / vec2(trans_size - 1);
    float H = sqrt(atmo_top * atmo_top - atmo_bottom * atmo_bottom);
    float rho = H * uv.y;
    r = sqrt(rho * rho + atmo_bottom * atmo_bottom);
    float d_min = atmo_top - r;
    float d_max = rho + H;
    float d = d_min + uv.x * (d_max - d_min);
    mu = d == 0.0 ? 1.0 : (H * H - rho * rho - d * d) / (2.0 * r * d);
    mu = clamp(mu, -1.0, 1.0);
}

// The multiple scattering table: radius up the v axis, the sun's cosine along u.
vec2 ms_uv(float r, float mu_s) {
    vec2 uv = vec2(mu_s * 0.5 + 0.5, (r - atmo_bottom) / (atmo_top - atmo_bottom));
    return (clamp(uv, 0.0, 1.0) * vec2(ms_size - 1) + 0.5) / vec2(ms_size);
}

// The sky view: around the camera (u, measured from the light's direction, both sides alike) and up and down (v,
// finer near the horizon).
vec2 skyview_uv(vec3 dir, vec3 light_dir) {
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    float t = sqrt(abs(elevation) / (0.5 * PI));
    float v = 0.5 + 0.5 * (elevation >= 0.0 ? t : -t);
    vec2 a = normalize(dir.xz + vec2(1e-6, 0.0));
    vec2 b = normalize(light_dir.xz + vec2(1e-6, 0.0));
    float u = acos(clamp(dot(a, b), -1.0, 1.0)) / PI;
    vec2 uv = vec2(u, v);
    return (uv * vec2(skyview_size - 1) + 0.5) / vec2(skyview_size);
}

vec3 skyview_dir(vec2 uv, vec3 light_dir) {
    uv = (uv * vec2(skyview_size) - 0.5) / vec2(skyview_size - 1);
    float t = uv.y * 2.0 - 1.0;
    float elevation = sign(t) * t * t * 0.5 * PI;
    float azimuth = uv.x * PI + atan(light_dir.z, light_dir.x);
    return vec3(cos(elevation) * cos(azimuth), sin(elevation), cos(elevation) * sin(azimuth));
}

// The camera's radius: Minecraft's sea level on the ground of the planet, each block a metre up.
float camera_radius(float height) {
    return atmo_bottom + clamp((height - 63.0) * 0.001, 0.0005, 30.0);
}

#endif

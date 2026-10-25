// The begin pass that works out the frame's shared light (see globals.glsl), after the air's tables are remade:
// the sun's and the moon's light where you are, which of them casts the shadows, and the sky's light on a block's
// six faces, from 512 directions of the sky view table and the ground under the horizon.

#define GLOBALS_WRITE
#include "/lib/globals.glsl"
#include "/lib/sky.glsl"

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform vec3 shadowLightPosition;

shared vec3 shared_cube[64 * 6];

// The ground's colour, lighting the sky's lower half (grass, dirt, stone).
const float ground_albedo = 0.15;
const int sky_samples = 512; // over the upper half, 8 an invocation

void main() {
    uint index = gl_LocalInvocationIndex;
    vec3 sun_dir = sun_dir_world();
    vec3 moon_dir = moon_dir_world();
    float height = atm_height(cameraPosition.y);
    vec3 origin = vec3(0.0, height, 0.0);

    // The light from the sky on a surface facing each axis (+x, -x, +y, -y, +z, -z): the sky's radiance times the
    // cosine, over the half of the sky the surface sees. Worked out for each face rather than fitted with spherical
    // harmonics, which take light away from the side facing away from a bright part of the sky (the sunset's glow),
    // more in one colour than another.
    vec3 face[6] = vec3[6](vec3(0.0), vec3(0.0), vec3(0.0), vec3(0.0), vec3(0.0), vec3(0.0));
    const float d_omega = 2.0 * PI / float(sky_samples);
    for (int k = 0; k < 8; k++) {
        int i = int(index) * 8 + k;
        // A Fibonacci lattice over the upper half.
        float y = 1.0 - (float(i) + 0.5) / float(sky_samples);
        float r = sqrt(max(0.0, 1.0 - y * y));
        float phi = float(i) * 2.39996323;
        vec3 dir = vec3(r * cos(phi), y, r * sin(phi));
        vec3 radiance = sky_radiance(dir) * d_omega;
        face[0] += radiance * max(dir.x, 0.0);
        face[1] += radiance * max(-dir.x, 0.0);
        face[2] += radiance * dir.y;
        face[4] += radiance * max(dir.z, 0.0);
        face[5] += radiance * max(-dir.z, 0.0);
    }
    for (int j = 0; j < 6; j++) {
        shared_cube[index * 6u + uint(j)] = face[j];
    }
    barrier();
    for (uint stride = 32u; stride > 0u; stride >>= 1u) {
        if (index < stride) {
            for (uint j = 0u; j < 6u; j++) {
                shared_cube[index * 6u + j] += shared_cube[(index + stride) * 6u + j];
            }
        }
        barrier();
    }
    if (index != 0u) {
        return;
    }

    // The sun and the moon through the air, where they are above the planet's edge; rain hides them.
    float clear = 1.0 - 0.95 * rainStrength;
    vec3 sun_light = vec3(0.0);
    if (atm_ray_sphere(origin, sun_dir, atm_bottom) < 0.0) {
        sun_light = sun_illuminance * texture(cg_trans_lut, atm_trans_uv(height, sun_dir.y)).rgb * clear;
    }
    vec3 moon_light = vec3(0.0);
    if (atm_ray_sphere(origin, moon_dir, atm_bottom) < 0.0) {
        moon_light = moon_illuminance() * texture(cg_trans_lut, atm_trans_uv(height, moon_dir.y)).rgb * clear;
    }
    set_global(G_SUN_DIR, vec4(sun_dir, sun_dir.y));
    set_global(G_MOON_DIR, vec4(moon_dir, moon_dir.y));
    set_global(G_SUN_LIGHT, vec4(sun_light, 1.0));
    set_global(G_MOON_LIGHT, vec4(moon_light, 1.0));

    // The shadows come from the sun by day and the moon by night, as Iris draws the shadow map.
    vec3 light_dir = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
    set_global(G_LIGHT_DIR, vec4(light_dir, 0.0));
    set_global(G_LIGHT_COLOR, vec4(dot(light_dir, sun_dir) > 0.0 ? sun_light : moon_light, 1.0));

    // The ground under the horizon: lit by the sun, the moon and the sky, the same all over. A face sees all of it
    // looking down (the cosine over the lower half comes to π), half of it looking sideways, none looking up.
    vec3 sky_up = shared_cube[2];
    vec3 ground_irradiance = sun_light * max(sun_dir.y, 0.0) + moon_light * max(moon_dir.y, 0.0) + sky_up;
    vec3 ground = ground_albedo / PI * ground_irradiance;
    const float ground_view[6] = float[6](0.5 * PI, 0.5 * PI, 0.0, PI, 0.5 * PI, 0.5 * PI);
    for (int j = 0; j < 6; j++) {
        set_global(G_SKY_CUBE + j, vec4(shared_cube[j] + ground * ground_view[j], 0.0));
    }

    // The first frame (the image starts at zero): an exposure for what the sky lights the world with, until the
    // metering pass (exposure.csh, after DLSS) takes over.
    if (!exposure_ready(g_exposure_data)) {
        float lit = dot(ground, vec3(0.2126, 0.7152, 0.0722));
        float ev = clamp(log2(2.0 / max(lit, 1e-8)), -7.0, 12.0);
        set_global(G_EXPOSURE, vec4(exp2(ev), ev, log2(max(lit, 1e-8)), 0.0));
    }
}

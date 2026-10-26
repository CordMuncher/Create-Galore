// The begin pass that works out the frame's shared light (see globals.glsl), after the air's tables are remade:
// the sun's and the moon's light where you are, which of them casts the shadows, and the sky's light on a block's
// six faces, from 512 directions of the sky view table and the ground under the horizon.

#define GLOBALS_WRITE
#include "/lib/globals.glsl"
#include "/lib/sky.glsl"
#include "/lib/settings.glsl"

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform vec3 shadowLightPosition;
uniform ivec3 cameraPositionInt;
uniform vec3 cameraPositionFract;
uniform int worldDay;
#include "/lib/frame.glsl"
uniform vec4 lightningBoltPosition; // a lightning bolt this frame: xyz where it is from the camera, w 1

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
        sun_light = sun_illuminance * atm_trans(height, sun_dir.y) * clear;
    }
    vec3 moon_light = vec3(0.0);
    if (atm_ray_sphere(origin, moon_dir, atm_bottom) < 0.0) {
        moon_light = moon_illuminance() * atm_trans(height, moon_dir.y) * clear;
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
    // A lightning bolt lights the whole sky up for the moment it is there, bluish white, less from farther away.
    vec3 flash = vec3(0.0);
    if (lightningBoltPosition.w > 0.5) {
        flash = vec3(0.8, 0.85, 1.0) * 1.5 * clamp(200.0 / max(length(lightningBoltPosition.xyz), 1.0), 0.2, 1.0);
    }
    set_global(G_FLASH, vec4(flash, 0.0));
    const float sky_view[6] = float[6](0.5 * PI, 0.5 * PI, PI, 0.0, 0.5 * PI, 0.5 * PI);
    for (int j = 0; j < 6; j++) {
        set_global(G_SKY_CUBE + j, vec4(shared_cube[j] + ground * ground_view[j] + flash * sky_view[j], 0.0));
    }

#ifdef CLOUDS
    // The wind carries the clouds along with the game's clock (a day lasts 1200 s), so every player sees the same
    // ones; the cirrus, higher up, twice as fast. Whole metres a day, so the drift stays exact on any day: modulo
    // 128 km for the cumulus, whose patterns repeat by that, and for the cirrus, whose don't, starting over every
    // 4096 days (modulo 4096 km, to keep it exact).
    const uvec2 cumulus_wind = uvec2(vec2(5400.0, 4200.0) * CLOUD_WIND); // m a day
    const uvec2 cirrus_wind = cumulus_wind * 2u;
    uint day = uint(max(worldDay, 0));
    vec2 today = vec2(float(worldTime) / 24000.0);
    vec2 cumulus_drift = vec2((cumulus_wind * (day % 128000u)) % 128000u) + vec2(cumulus_wind) * today;
    vec2 cirrus_drift = vec2((cirrus_wind * (day % 4096u)) % 4096000u) + vec2(cirrus_wind) * today;
    // Where the camera is, in metres (blocks), modulo the same.
    vec2 camera_128 = vec2((cameraPositionInt.xz % 128000 + 128000) % 128000) + cameraPositionFract.xz;
    vec2 camera_4096 = vec2((cameraPositionInt.xz % 4096000 + 4096000) % 4096000) + cameraPositionFract.xz;
    set_global(G_CLOUD_PLACE, vec4(mod(camera_128 + cumulus_drift, 128000.0) * 0.001,
            mod(camera_4096 + cirrus_drift, 4096000.0) * 0.001));
    // The billows move through the cloud bodies a little, by frame time for smooth motion: whole 2 km steps an hour,
    // which their textures repeat by, so the counter going back to 0 each hour doesn't show. And a slow wobble.
    float t = frameTimeCounter;
    const float hour = 2.0 * PI / 3600.0;
    vec3 detail = vec3(8.0, 0.0, 4.0) * CLOUD_WIND * (t / 3600.0)
            + vec3(-0.15, 0.05, 0.09) * 0.006 * sin(t * hour * 344.0 + 0.5)
            + vec3(0.23, -0.06, 0.12) * 0.009 * sin(t * hour * 229.0 + 0.2);
    set_global(G_CLOUD_DETAIL, vec4(detail, 0.0));
#endif

#ifdef LIT_WATER_RIPPLES
    // The ripples step 60 times a second of the game's clock (see water.glsl): as many steps as ticks of that have
    // passed since last frame, at most 3. Their grid moves to centre on the camera's cell with the first step.
    vec4 water = global(G_WATER);
    vec4 grid = global(G_WATER_ORIGIN);
    float ticks = floor(frameTimeCounter * 60.0) - floor(water.z * 60.0);
    float steps = clamp(ticks < 0.0 ? ticks + 216000.0 : ticks, 0.0, 3.0);
    const int wrap = 1 << 20;
    ivec2 camera_cell = cameraPositionInt.xz * 4 + ivec2(floor(cameraPositionFract.xz * 4.0));
    ivec2 corner = ((camera_cell - 256) % wrap + wrap) % wrap;
    vec2 next_corner = steps > 0.0 ? vec2(corner) : grid.xy;
    set_global(G_WATER, vec4(steps, mod(water.y + water.x, 2.0), frameTimeCounter, 0.0));
    set_global(G_WATER_ORIGIN, vec4(next_corner, grid.xy));
#endif

    // The first frame (the image starts at zero): an exposure for what the sky lights the world with, until the
    // metering pass (exposure.csh, after DLSS) takes over.
    if (!exposure_ready(g_exposure_data)) {
        float lit = dot(ground, vec3(0.2126, 0.7152, 0.0722));
        float ev = clamp(log2(2.0 / max(lit, 1e-8)), -7.0, 12.0);
        set_global(G_EXPOSURE, vec4(exp2(ev), ev, log2(max(lit, 1e-8)), 0.0));
    }
}

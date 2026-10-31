// The wind and the game's clock, shared by the clouds (lib/clouds.glsl) and the water's waves (lib/water.glsl): the
// clouds drift with it, and the waves run before its steady part (towards the north-north-east, x half of z), so on
// open water the swell rolls the way the clouds go. And the noise volume both read (textures/cloud_noise.dat).

#if !defined LIB_WIND
#define LIB_WIND

#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler3D cg_cloud_noise; // see tools/cloud_noise.py
uniform int worldTime;            // the game's clock: ticks into the day
uniform int worldDay;             // and days

// The steady wind's way across the ground (x, z): a whole-number direction, so the waves' grids that follow it repeat
// evenly (see lib/water.glsl).
const ivec2 wind_way = ivec2(1, -2);

// The game's clock in ticks, within 1024 days: every movement below comes back to where it started in that time, so
// it never jumps.
int cloud_ticks() {
    return (worldDay & 1023) * 24000 + worldTime;
}

// The steady wind in 160ths of a block a tick (whole numbers, so the carry stays exact): CLOUD_SPEED blocks a second
// towards the north-north-east at 1 (x half of it, z all of it).
ivec2 cloud_steady_wind() {
    int k = int(CLOUD_SPEED * 2.0 + 0.5);
    return wind_way * (4 * k);
}

const float cloud_wander_ticks = 96000.0; // four days: the wind's direction wanders once round in that time
float cloud_wander_size() {
    return 0.4 * CLOUD_SPEED * (cloud_wander_ticks / 20.0) / TAU;
}

// How far the wind has carried the clouds (blocks, x and z, within 8192 either way).
vec2 cloud_drift() {
    int ticks = cloud_ticks();
    ivec2 v = cloud_steady_wind();
    vec2 steady = vec2(ivec2((ticks * v.x) % (8192 * 160), (ticks * v.y) % (8192 * 160))) / 160.0;
    float w = TAU * float(ticks % int(cloud_wander_ticks)) / cloud_wander_ticks;
    return steady + cloud_wander_size() * vec2(sin(w), -cos(w));
}

// The wind now, in blocks a second (to follow the clouds from frame to frame), as a 3D vector.
vec3 cloud_wind() {
    vec2 v = vec2(cloud_steady_wind()) / 8.0; // 160ths of a block a tick to blocks a second
    float w = TAU * float(cloud_ticks() % int(cloud_wander_ticks)) / cloud_wander_ticks;
    v += cloud_wander_size() * (TAU * 20.0 / cloud_wander_ticks) * vec2(cos(w), sin(w));
    return vec3(v.x, 0.0, v.y);
}

#endif

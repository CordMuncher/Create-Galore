// The End's light, in real units (see globals.glsl), for sky.glsl. The End has no day: one still, pale light stands
// high over it (where Minecraft's fixed noon puts the sun, which the shadow map follows), so every island, pillar and
// tower casts its shadow from it. The void all around glows a dim violet, from below more than from above, and lights
// the undersides of the islands. Haze lies between the islands, lit into shafts where the light falls past them (see
// air_volume.csh), and thickens into a mist over the void below, which the light from above lights. Beyond the haze:
// the nebula and the stars (end_sky.glsl).

#if !defined LIB_END
#define LIB_END

#include "/lib/settings.glsl"
#include "/lib/globals.glsl"
#include "/lib/end_sky.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

// The light from above (klux): pale, a little violet, about what a room gets from an overcast sky; luminance 1 times
// that.
const vec3 end_light_color = vec3(1.034, 0.979, 1.112);
const float end_light_lux = 0.05;
// The void's glow (luminance 1), and how much of it falls on a surface facing up, sideways and down (klux).
const vec3 end_void_color = vec3(1.19, 0.82, 2.17);
const float end_void_up = 0.004;
const float end_void_side = 0.005;
const float end_void_down = 0.008;
// The haze: how thick (per block) between the islands, and in the mist over the void at its thickest. Both lie in
// layers, so looking up or far down you see past them into the void, the nebula and the stars (end_sky.glsl): the
// haze from y -100 (full from -64) to y 180 (thinning from 100), the mist from y -80 to 40, thickest at -16 (where
// the islands' undersides end). How much of the light in it the haze sends on, and how much of that onwards
// (Henyey-Greenstein g), so it glows around the light when you look up towards it.
const float end_haze_density = 0.0008;
const float end_mist_density = 0.012;
const float end_haze_albedo = 0.85;
const float end_haze_g = 0.55;
// How far the haze reaches from you (blocks): it is the End's air, not a planet's; past this, the void.
const float end_haze_reach = 3000.0;
// The nebula and the stars, from end_space's colours (white 1) into kcd/m².
const float end_space_scale = 0.03;

vec3 end_light() {
    return end_light_color * end_light_lux * END_LIGHT;
}

// The void's light on a surface facing each axis (+x, -x, +y, -y, +z, -z, klux).
vec3 end_ambient(int face) {
    vec3 color = mix(vec3(1.0), end_void_color, 0.8);
    return color * (face == 2 ? end_void_up : face == 3 ? end_void_down : end_void_side);
}

// The void's light all around a point in the haze (kcd/m², the same from every way): the average of the faces'.
vec3 end_ambient_radiance() {
    return mix(vec3(1.0), end_void_color, 0.8) * ((end_void_up + end_void_down + 4.0 * end_void_side) / 6.0) / PI;
}

float end_hg(float cos_theta) {
    const float g = end_haze_g;
    return (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cos_theta, 1.5));
}

// The layers' shapes, as the places their thickness (0..1 with height) bends and by how much its slope changes there,
// so each is a sum of ramps (and its sum along y one of half squares).
const vec4 end_haze_bends = vec4(-100.0, -64.0, 100.0, 180.0);
const vec4 end_haze_slopes = vec4(1.0 / 36.0, -1.0 / 36.0, -1.0 / 80.0, 1.0 / 80.0);
const vec3 end_mist_bends = vec3(-80.0, -16.0, 40.0);
const vec3 end_mist_slopes = vec3(1.0 / 64.0, -1.0 / 64.0 - 1.0 / 56.0, 1.0 / 56.0);

float end_haze_layer(float y) {
    return dot(end_haze_slopes, max(y - end_haze_bends, 0.0));
}

float end_mist_layer(float y) {
    return dot(end_mist_slopes, max(y - end_mist_bends, 0.0));
}

// How thick the haze is at a height (world y, per block).
float end_haze(float y) {
    return END_HAZE * (end_haze_density * end_haze_layer(y) + end_mist_density * end_mist_layer(y));
}

// The haze summed from below up to a height (blocks of it at full density, weighted by density).
float end_haze_below(float y) {
    vec4 h = max(y - end_haze_bends, 0.0);
    vec3 m = max(y - end_mist_bends, 0.0);
    return 0.5 * (end_haze_density * dot(end_haze_slopes, h * h) + end_mist_density * dot(end_mist_slopes, m * m));
}

// How much haze lies along dir (world space) from from_t to end_haze_reach blocks out from you: its optical depth.
float end_haze_depth(vec3 dir, float from_t) {
    float length_t = max(end_haze_reach - from_t, 0.0);
    float y0 = cameraPosition.y + dir.y * from_t;
    float y1 = cameraPosition.y + dir.y * end_haze_reach;
    float depth;
    if (abs(dir.y) > 0.002) {
        depth = abs(end_haze_below(y1) - end_haze_below(y0)) / abs(dir.y);
    } else {
        // Near level, the layers barely change along the ray: their thickness halfway, times its length.
        depth = end_haze(0.5 * (y0 + y1)) / END_HAZE * length_t;
    }
    return END_HAZE * depth;
}

// The light the haze sends towards you from along dir (kcd/m² per unit of its optical depth): the void's glow and
// the light from above, more the nearer you look towards it. lit: how much of the light from above reaches it.
vec3 end_haze_light(vec3 dir, float lit) {
    return end_haze_albedo * (end_ambient_radiance() + end_light() * end_hg(dot(dir, g_light_dir.xyz)) * lit);
}

// Where the haze the sky shows begins (blocks out): the light in the air draws the haze nearer than that (see
// air.glsl), the sky the rest.
float end_sky_from() {
#if defined LIT_AIR && defined SHADOWS
    return shadowDistance; // (air.glsl's air_sky_start)
#elif defined LIT_AIR
    return 128.0;
#else
    return 0.0;
#endif
}

// The haze's light towards the eye from a direction, past where the light in the air ends.
vec3 sky_radiance(vec3 dir) {
    return end_haze_light(dir, 1.0) * (1.0 - exp(-end_haze_depth(dir, end_sky_from())));
}

// How much of what is beyond the haze gets through it.
vec3 sky_transmittance(vec3 dir) {
    return vec3(exp(-end_haze_depth(dir, end_sky_from())));
}

// Beyond the haze: the void, the nebula and the stars. (moon: the share of the moon's light the overworld draws its
// disc at, see sky.glsl; the End has none.)
vec3 sky_space(vec3 dir, float pixel_angle, float moon) {
    return end_space(dir, pixel_angle) * end_space_scale;
}

vec3 sky_full(vec3 dir, float pixel_angle, float moon) {
    return sky_radiance(dir) + sky_space(dir, pixel_angle, moon) * sky_transmittance(dir);
}

#endif

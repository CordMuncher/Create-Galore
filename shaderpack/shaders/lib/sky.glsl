// The overworld's sky in any direction: the air's light from the sky view table (see atmosphere.glsl), and the sun,
// the moon and the stars (stars.glsl) behind it, in real units (see globals.glsl). Rain greys it over, under the
// clouds (clouds.glsl) it brings. The Nether's and the End's are their own (nether.glsl, end.glsl), with the same
// functions: what the air sends towards you, what gets through it, and what lies beyond it.

#if !defined LIB_SKY
#define LIB_SKY

#include "/lib/atmosphere.glsl"
#include "/lib/celestial.glsl"
#include "/lib/stars.glsl"

uniform sampler2D cg_skyview_lut;
uniform sampler2D cg_moon_tex; // Minecraft's moon phases (the resource pack's), 4 by 2
uniform vec3 cameraPosition;
uniform float rainStrength;
uniform float thunderStrength;

// An overcast sky under rain: grey, much darker, a little blue; darker still in a thunderstorm.
vec3 weather(vec3 radiance) {
    vec3 grey = vec3(dot(radiance, vec3(0.2126, 0.7152, 0.0722))) * vec3(0.92, 0.97, 1.05) * (0.25 - 0.15 * thunderStrength);
    return mix(radiance, grey, 0.9 * rainStrength);
}

#if defined WORLD_NETHER
#include "/lib/nether.glsl"
#elif defined WORLD_END
#include "/lib/end.glsl"
#else
// The air's light towards the eye, from a direction (world space).
vec3 sky_radiance(vec3 dir) {
    return weather(texture(cg_skyview_lut, atm_skyview_uv(dir)).rgb);
}

// How much light from beyond the air gets through it to you, from a direction.
vec3 sky_transmittance(vec3 dir) {
    return atm_trans(atm_height(cameraPosition.y), dir.y);
}

#endif

// The sun's path turns about this axis (Iris's celestial frame: its y towards the sun, x this axis).
vec3 sun_path_axis() {
    float tilt = radians(sunPathRotation);
    return vec3(0.0, sin(tilt), cos(tilt));
}

// The sun's disc: its real size, limb-darkened (Neckel and Labs), its edge smoothed over a pixel so it holds still
// under the upscaler's jitter. pixel_angle: a render pixel's size in radians.
vec3 sun_disc(vec3 dir, vec3 sun_dir, float pixel_angle) {
    if (dot(dir, sun_dir) <= 0.0) {
        return vec3(0.0);
    }
    float s = sin_between(dir, sun_dir);
    float coverage = clamp((sun_angular_radius - s) / pixel_angle + 0.5, 0.0, 1.0);
    if (coverage <= 0.0) {
        return vec3(0.0);
    }
    float mu = sqrt(max(0.0, 1.0 - (s * s) / (sun_angular_radius * sun_angular_radius)));
    const vec3 u = vec3(0.397, 0.503, 0.652);
    vec3 darkening = (1.0 - u * (1.0 - mu)) / (1.0 - u / 3.0);
    return sun_illuminance / sun_solid_angle * darkening * coverage;
}

// The moon: Minecraft's own moon picture for tonight's phase, at the moon's real size, in Iris's celestial frame
// as Minecraft draws it.
vec3 moon_disc(vec3 dir, vec3 moon_dir, vec3 axis, float pixel_angle) {
    if (dot(dir, moon_dir) <= 0.0) {
        return vec3(0.0);
    }
    vec3 across = normalize(cross(axis, -moon_dir)); // the celestial frame's z, the sun's opposite side
    vec2 q = vec2(dot(dir, axis), dot(dir, across)) / moon_angular_radius; // -1..1 over the picture
    if (any(greaterThan(abs(q), vec2(1.0)))) {
        return vec3(0.0);
    }
    ivec2 cells = textureSize(cg_moon_tex, 0) / ivec2(4, 2);
    ivec2 cell = ivec2(moonPhase % 4, (moonPhase / 4) % 2);
    vec2 uv = (vec2(cell) + (q * 0.5 + 0.5)) / vec2(4.0, 2.0);
    vec3 albedo = texture(cg_moon_tex, uv).rgb;
    // The picture's sRGB, its brightest grey as the full moon's albedo.
    albedo = pow(albedo, vec3(2.2)) * 1.6;
    return moon_radiance() * albedo;
}

#if !defined WORLD_NETHER && !defined WORLD_END
// What is beyond the air in a direction: the sun, the moon and the stars, none where the planet is in the way.
vec3 sky_space(vec3 dir, float pixel_angle) {
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y), 0.0);
    if (atm_ray_sphere(origin, dir, atm_bottom) >= 0.0) {
        return vec3(0.0);
    }
    vec3 sun_dir = sun_dir_world();
    vec3 axis = sun_path_axis();
    return sun_disc(dir, sun_dir, pixel_angle) + moon_disc(dir, moon_dir_world(), axis, pixel_angle) + stars(dir, sun_dir, axis, pixel_angle);
}

// Everything you see in a direction where nothing else is: the air, and through it what is beyond it (rain hides
// that).
vec3 sky_full(vec3 dir, float pixel_angle) {
    return sky_radiance(dir) + sky_space(dir, pixel_angle) * sky_transmittance(dir) * (1.0 - rainStrength);
}
#endif

#endif

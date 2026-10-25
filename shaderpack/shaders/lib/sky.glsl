// The overworld's sky in any direction: the air's light from the sky view table (see atmosphere.glsl), and the sun,
// the moon and the stars behind it, in real units (see globals.glsl). Rain draws an overcast over all of it until
// the pack has clouds of its own.

#if !defined LIB_SKY
#define LIB_SKY

#include "/lib/atmosphere.glsl"
#include "/lib/celestial.glsl"

uniform sampler2D cg_skyview_lut;
uniform sampler2D cg_trans_lut;
uniform sampler2D cg_moon_tex; // Minecraft's moon phases (the resource pack's), 4 by 2
uniform vec3 cameraPosition;
uniform float rainStrength;
uniform float thunderStrength;

// An overcast sky under rain: grey, much darker, a little blue; darker still in a thunderstorm.
vec3 weather(vec3 radiance) {
    vec3 grey = vec3(dot(radiance, vec3(0.2126, 0.7152, 0.0722))) * vec3(0.92, 0.97, 1.05) * (0.25 - 0.15 * thunderStrength);
    return mix(radiance, grey, 0.9 * rainStrength);
}

// The air's light towards the eye, from a direction (world space).
vec3 sky_radiance(vec3 dir) {
    return weather(texture(cg_skyview_lut, atm_skyview_uv(dir)).rgb);
}

// How much light from beyond the air gets through it to you, from a direction.
vec3 sky_transmittance(vec3 dir) {
    return texture(cg_trans_lut, atm_trans_uv(atm_height(cameraPosition.y), dir.y)).rgb;
}

// The sun's path turns about this axis (Iris's celestial frame: its y towards the sun, x this axis).
vec3 sun_path_axis() {
    float tilt = radians(sunPathRotation);
    return vec3(0.0, sin(tilt), cos(tilt));
}

// The sine of the angle between two unit vectors, exact for small angles where acos isn't.
float sin_between(vec3 a, vec3 b) {
    return length(cross(a, b));
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

// One star at most in each cell of a cube's faces over the celestial sphere, 256 by 256 a face: about 7000 stars to
// the 6.5th magnitude, as many as you can see on a clear night, each with its own colour. Each is a point whose
// light spreads over about a pixel, so the upscaler sees the same star in every frame.
uvec3 star_hash(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

vec3 star_color(float kelvin) {
    // Planck's law at three wavelengths for the sRGB primaries, scaled to luminance 1.
    const vec3 wavelength = vec3(610.0, 550.0, 465.0) * 1e-9;
    vec3 planck = 1.0 / (pow(wavelength, vec3(5.0)) * (exp(0.014388 / (wavelength * kelvin)) - 1.0));
    return planck / dot(planck, vec3(0.2126, 0.7152, 0.0722));
}

vec3 stars(vec3 dir, vec3 sun_dir, vec3 axis, float pixel_angle) {
    vec3 local = vec3(dot(dir, axis), dot(dir, sun_dir), dot(dir, cross(axis, sun_dir)));
    vec3 a = abs(local);
    uint face;
    vec2 uv;
    if (a.x >= a.y && a.x >= a.z) {
        face = local.x > 0.0 ? 0u : 1u;
        uv = local.yz / a.x;
    } else if (a.y >= a.z) {
        face = local.y > 0.0 ? 2u : 3u;
        uv = local.xz / a.y;
    } else {
        face = local.z > 0.0 ? 4u : 5u;
        uv = local.xy / a.z;
    }
    const float cells = 256.0;
    vec2 cell = floor((uv * 0.5 + 0.5) * cells);
    uvec3 h = star_hash(uvec3(uvec2(cell), face));
    const float present = 7000.0 / (6.0 * cells * cells);
    float pick = float(h.x & 0xFFFFu) / 65535.0;
    if (pick >= present) {
        return vec3(0.0);
    }
    // Where in the cell, kept off its edges so the star's light stays inside it.
    vec2 offset = 0.25 + 0.5 * vec2(float(h.y & 0xFFFFu), float(h.y >> 16u)) / 65535.0;
    vec2 star_uv = (cell + offset) / cells * 2.0 - 1.0;
    vec3 star_local;
    float sign_face = (face & 1u) == 0u ? 1.0 : -1.0;
    if (face < 2u) {
        star_local = vec3(sign_face, star_uv);
    } else if (face < 4u) {
        star_local = vec3(star_uv.x, sign_face, star_uv.y);
    } else {
        star_local = vec3(star_uv, sign_face);
    }
    star_local = normalize(star_local);
    float s = sin_between(normalize(local), star_local);

    // Brightness: as many stars brighter than magnitude m as the sky has, about 10^(0.45 m + 0.9).
    float u = (pick / present) * 0.999 + 0.001;
    float magnitude = (log2(u * 7000.0) / 3.321928 - 0.9) / 0.45;
    // A magnitude 0 star gives 2.5e-9 klux, made brighter with the moon (see moon_boost).
    float flux = 2.5e-9 * exp2(-0.4 * 3.321928 * magnitude) * moon_boost * 2.0;
    float sigma = 0.7 * pixel_angle;
    float kelvin = 3000.0 + 9000.0 * pow(float(h.z & 0xFFFFu) / 65535.0, 2.0);
    return star_color(kelvin) * flux / (2.0 * PI * sigma * sigma) * exp(-0.5 * s * s / (sigma * sigma));
}

// Everything you see in a direction where nothing else is: the air, and behind it what is beyond it, unless the
// planet is in the way.
vec3 sky_full(vec3 dir, float pixel_angle) {
    vec3 color = sky_radiance(dir);
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y), 0.0);
    if (atm_ray_sphere(origin, dir, atm_bottom) < 0.0) {
        vec3 sun_dir = sun_dir_world();
        vec3 axis = sun_path_axis();
        vec3 beyond = sun_disc(dir, sun_dir, pixel_angle)
                + moon_disc(dir, moon_dir_world(), axis, pixel_angle)
                + stars(dir, sun_dir, axis, pixel_angle);
        color += beyond * sky_transmittance(dir) * (1.0 - rainStrength);
    }
    return color;
}

#endif

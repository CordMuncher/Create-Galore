// The stars, for any sky that shows them (the overworld's, sky.glsl, and the End's, end_sky.glsl).

#if !defined LIB_STARS
#define LIB_STARS

#include "/lib/celestial.glsl"

// The sine of the angle between two unit vectors, exact for small angles where acos isn't.
float sin_between(vec3 a, vec3 b) {
    return length(cross(a, b));
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

#endif

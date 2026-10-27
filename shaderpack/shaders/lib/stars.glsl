// The stars, for any sky that shows them (the overworld's, sky.glsl, and the End's, end_sky.glsl).

#if !defined LIB_STARS
#define LIB_STARS

#include "/lib/celestial.glsl"
#include "/lib/frame.glsl"
#include "/lib/noise.glsl"

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

// count: how many stars the whole sky holds; twinkle: whether they twinkle near the horizon (through the air).
vec3 star_field(vec3 dir, vec3 sun_dir, vec3 axis, float pixel_angle, float count, bool twinkle) {
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
    float present = count / (6.0 * cells * cells);
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
    float magnitude = (log2(u * count) / 3.321928 - 0.9) / 0.45;
    // A magnitude 0 star gives 2.5e-9 klux, made brighter with the moon (see moon_boost).
    float flux = 2.5e-9 * exp2(-0.4 * 3.321928 * magnitude) * moon_boost * 2.0;
    float sigma = 0.7 * pixel_angle;
    float kelvin = 3000.0 + 9000.0 * pow(float(h.z & 0xFFFFu) / 65535.0, 2.0);
    if (twinkle) {
        // The air's turbulence makes a star's light come and go a few times a second, the more air it comes
        // through: near the horizon, hardly at all overhead.
        float phase = float(h.z >> 16u) / 65535.0 * 6.2831853;
        float flicker = 0.6 * sin(frameTimeCounter * 7.3 + phase) + 0.4 * sin(frameTimeCounter * 11.9 + 2.3 * phase);
        flux *= 1.0 + 0.45 * flicker * (1.0 - smoothstep(0.05, 0.45, dir.y));
    }
    return star_color(kelvin) * flux / (2.0 * PI * sigma * sigma) * exp(-0.5 * s * s / (sigma * sigma));
}

vec3 stars(vec3 dir, vec3 sun_dir, vec3 axis, float pixel_angle) {
    return star_field(dir, sun_dir, axis, pixel_angle, 7000.0, false);
}

// The overworld's night sky: about 15000 stars, to the 7th magnitude (a dark country sky's, the faintest only just
// there), twinkling low down.
vec3 night_stars(vec3 dir, vec3 sun_dir, vec3 axis, float pixel_angle) {
    return star_field(dir, sun_dir, axis, pixel_angle, 15000.0, true);
}

// The Milky Way: the light of the galaxy's countless faint stars, a band about 15 degrees wide along a great circle
// of the stars' sphere (turning with them), brighter and wider towards the galaxy's centre, split down its middle by
// the dark lanes of its dust. About three times the dark night sky's own glow (22 magnitudes a square arcsecond) at
// its brightest, made as much brighter as the moon is (see moon_boost): a full moon's sky drowns it, as a real one
// does; on a dark night it shows. kcd/m².
vec3 milky_way(vec3 dir, vec3 sun_dir, vec3 axis) {
    vec3 local = vec3(dot(dir, axis), dot(dir, sun_dir), dot(dir, cross(axis, sun_dir)));
    const vec3 pole = vec3(0.4472136, 0.4472136, 0.7745967);   // the galaxy's pole, in the stars' frame
    const vec3 centre = vec3(0.7071068, -0.7071068, 0.0);   // towards its centre, on its plane
    float b = dot(local, pole);                                     // sin of the latitude
    float l = atan(dot(local, cross(pole, centre)), dot(local, centre)); // the longitude, 0 at the centre
    float towards = 0.5 + 0.5 * cos(l);
    float width = mix(0.11, 0.2, towards * towards);
    float band = exp(-0.5 * b * b / (width * width)) * mix(0.35, 1.0, towards * towards);
    float bulge = exp(-(l * l + b * b * 9.0) / 0.12);
    // Clouds of stars and the dust between them.
    float clouds = 0.65 + 0.35 * gradient_noise_2d(local.xy * 9.0 + local.z * 3.0)
            + 0.25 * gradient_noise_2d(local.yz * 23.0 + local.x * 7.0 + 17.0);
    float lanes = 1.0 - 0.75 * exp(-0.5 * b * b / 0.0016)
            * smoothstep(-0.2, 0.35, gradient_noise_2d(local.zx * 7.0 + local.y * 2.0 + 5.0)) * (1.0 - smoothstep(0.4, 1.9, abs(l)));
    vec3 tint = mix(star_color(6500.0), star_color(4800.0), 0.6 * bulge);
    const float peak = 3.0 * 1.7e-7; // kcd/m²: three times a 22 mag/arcsec² sky
    return tint * peak * moon_boost * max(band * clouds * lanes + 0.8 * bulge * lanes, 0.0);
}

#endif

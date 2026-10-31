// The sky as the camera sees it, in our look: brighter colours than the air alone gives, the way the look we follow
// paints the world. The light the sky gives surfaces is set apart from this (program/frame.csh).
// - The Overworld: the air's light from the sky view table (see atmosphere.glsl), pushed away from grey (a deep blue
//   by day, burning orange round the sun as it sets and pink on the far side, a deep blue night), the sun's disc and
//   its glow, the moon's disc with its phase and its glow, the stars in their colours and the Milky Way. All of it
//   turns with the sky. The clouds go over it (lib/clouds.glsl).
// - The End: a violet sky brighter towards the horizon, a nebula of slow drifting purple and green clouds, coloured
//   stars that twinkle, and the End's sun, pink-white, where its light comes from.
// - The Nether has no sky: the fog's colour (program/fog.fsh).
// Everything here is smooth from pixel to pixel or a point the size of a pixel that holds still, so the upscaler finds
// the same sky in every frame.

#if !defined LIB_SKY
#define LIB_SKY

#include "/lib/settings.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/globals.glsl"
#include "/lib/view.glsl"
#include "/lib/frame.glsl"

// --- Shared ----------------------------------------------------------------------------------------------------------

uniform int moonPhase; // 0 full .. 4 new .. 7
uniform float rainStrength; // 0 dry .. 1 raining (Minecraft eases it in and out)

// A colour's distance from grey scaled by a power of it (under 1 pulls it towards grey), keeping its brightness.
vec3 push_from_grey(vec3 c, float by) {
    vec3 pushed = pow(max(c, 1e-6), vec3(by));
    return pushed * (luminance(c) / max(luminance(pushed), 1e-6));
}

// Smooth noise over space (0..1): values at the corners of a unit grid, blended with a smooth curve.
float value_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    uvec3 c = uvec3(ivec3(i) + 32768);
    float n000 = hash33(c).x;
    float n100 = hash33(c + uvec3(1, 0, 0)).x;
    float n010 = hash33(c + uvec3(0, 1, 0)).x;
    float n110 = hash33(c + uvec3(1, 1, 0)).x;
    float n001 = hash33(c + uvec3(0, 0, 1)).x;
    float n101 = hash33(c + uvec3(1, 0, 1)).x;
    float n011 = hash33(c + uvec3(0, 1, 1)).x;
    float n111 = hash33(c + uvec3(1, 1, 1)).x;
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

// Octaves of it, each twice as fine and half as strong (0..1).
float fbm(vec3 p, int octaves) {
    float sum = 0.0;
    float weight = 0.5;
    float total = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += value_noise(p) * weight;
        total += weight;
        weight *= 0.5;
        p = p * 2.03 + vec3(17.1, 5.3, 11.7);
    }
    return sum / total;
}

// A render pixel's size in radians, at the middle of the view.
float pixel_angle() {
    return 2.0 / (gbufferProjection[1][1] * render_size().y);
}

// A star's colour from its temperature: Planck's law at three wavelengths for the sRGB primaries, luminance 1.
vec3 star_color(float kelvin) {
    const vec3 wavelength = vec3(610.0, 550.0, 465.0) * 1e-9;
    vec3 planck = 1.0 / (pow(wavelength, vec3(5.0)) * (exp(0.014388 / (wavelength * kelvin)) - 1.0));
    return planck / luminance(planck);
}

// The stars in a frame of their own: one at most in each cell of a cube's faces (256 x 256 a face), count of them over
// the whole sky, as many faint ones for each bright one as the real sky has (their brightness a little evened out, so
// the faint ones still show), their temperatures between cold and hot (most of them white to pale blue, a few orange).
// Each is a soft point about two pixels across, so the upscaler finds the same star in every frame and never averages
// it away. Only stars brighter than magnitude limit show (dusk and moonlight hide the faint ones first). twinkle: how
// much they flicker. At its brightest about 0.45.
vec3 star_field(vec3 local, float count, vec2 kelvin, float twinkle, float limit) {
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
    vec2 cell = min(floor((uv * 0.5 + 0.5) * cells), cells - 1.0);
    uvec3 h = pcg3d(uvec3(uvec2(cell), face + 101u));
    float present = count / (6.0 * cells * cells);
    float pick = float(h.x & 0xffffu) / 65535.0;
    if (pick >= present) {
        return vec3(0.0);
    }
    // Where in its cell, kept off the edges so its light stays inside the cell.
    vec2 offset = 0.3 + 0.4 * vec2(float(h.y & 0xffffu), float(h.y >> 16u)) / 65535.0;
    vec2 star_uv = (cell + offset) / cells * 2.0 - 1.0;
    float side = (face & 1u) == 0u ? 1.0 : -1.0;
    vec3 star = face < 2u ? vec3(side, star_uv) : face < 4u ? vec3(star_uv.x, side, star_uv.y) : vec3(star_uv, side);
    float s = length(cross(normalize(local), normalize(star)));

    // Brightness: as many stars brighter than magnitude m as the sky has, about 10^(0.45 m + 0.9) of them.
    float u = (pick / present) * 0.999 + 0.001;
    float magnitude = (log2(u * count) / 3.321928 - 0.9) / 0.45;
    float shows = 1.0 - smoothstep(limit - 1.0, limit, magnitude);
    if (shows <= 0.0) {
        return vec3(0.0);
    }
    float flux = exp2(-0.4 * 0.75 * 3.321928 * (magnitude - 1.0)) * shows; // 1 for a first-magnitude star
    float k = mix(kelvin.x, kelvin.y, pow(float(h.z & 0xffffu) / 65535.0, 0.6));
    if (twinkle > 0.0) {
        float phase = float(h.z >> 16u) / 65535.0 * TAU;
        float flicker = 0.6 * sin(frameTimeCounter * 3.1 + phase) + 0.4 * sin(frameTimeCounter * 5.3 + 2.3 * phase);
        flux *= max(1.0 + twinkle * flicker, 0.0);
    }
    float sigma = 0.75 * pixel_angle();
    // The peak scaled with the pixel's size, so a star sends the same light whatever the resolution.
    const float reference = 0.0011; // a pixel's angle at 1080 lines and 70 degrees
    return star_color(k) * (1.25 * min(flux, 4.0) * sqr(reference / sigma * 0.6)) * exp(-0.5 * s * s / (sigma * sigma));
}

#if !defined NO_SKY_LIGHT

// --- The Overworld ---------------------------------------------------------------------------------------------------

uniform sampler2D cg_sky_tex; // the air's tables (see lib/atmosphere.glsl)

// The sun's light above the air, for the sky as you see it (its air and its disc). The light the sun and the sky
// give surfaces is set apart from this, in program/frame.csh.
const float sun_illuminance = 2.3;
const float sun_radius = 0.045; // radians: about Minecraft's own sun

// The full moon's light above the air, for the night sky as you see it (the new moon's is a third of it).
float moon_illuminance() {
    return 0.17 * NIGHT_BRIGHTNESS * (0.35 + 0.65 * g_moon_dir.w);
}

vec3 air_transmittance(float r, float mu) {
    return texture(cg_sky_tex, sky_atlas_uv(trans_uv(r, mu), trans_size, trans_origin)).rgb;
}

// The clouds' shadows on the land: a map 4096 blocks across round the camera (16 blocks a texel, on a grid fixed to
// the world), each texel what gets through the clouds towards the sun (or the moon) from a point at the camera's
// height there (program/clouds.csh). A point higher or lower looks it up shifted along the light's direction.
const float cloud_shadow_texel = 16.0;

// The map's low corner, from the camera (blocks, x and z).
vec2 cloud_shadow_corner() {
    ivec2 cam = cameraPositionInt.xz;
    return vec2(((cam >> 4) << 4) - cam) - cameraPositionFract.xz - 128.0 * cloud_shadow_texel;
}

// How far a point's look-up shifts per block of height (the light's direction, never flatter than about 7 degrees).
vec2 cloud_shadow_shift() {
    return g_light_dir.xz / max(g_light_dir.y, 0.12);
}

// What gets through the clouds to a point (scene space) from the sun or the moon: 1 without clouds.
float cloud_shadow(vec3 scene_pos) {
#ifdef CLOUD_SHADOWS
#if defined VOLUME_CLOUDS
    vec2 xz = scene_pos.xz - cloud_shadow_shift() * scene_pos.y;
    vec2 uv = (xz - cloud_shadow_corner()) / (cloud_shadow_texel * vec2(cloud_shadow_size));
    float fade = smoothstep(0.0, 0.08, min(min(uv.x, uv.y), min(1.0 - uv.x, 1.0 - uv.y)));
    if (fade <= 0.0) {
        return 1.0;
    }
    uv = clamp(uv, 0.5 / vec2(cloud_shadow_size), 1.0 - 0.5 / vec2(cloud_shadow_size));
    float through = texture(cg_sky_tex, sky_atlas_uv(uv, cloud_shadow_size, cloud_shadow_origin)).r;
    return mix(1.0, through, fade);
#endif
#endif
    return 1.0;
}

// The night sky as you see it is darker than the light it gives: the look we follow keeps it a deep blue overhead so
// the stars and the Milky Way show, while surfaces keep the moonlit sky's light (program/frame.csh).
const float night_sky_seen = 0.3;

// The air's own light from a direction: the sun's sky, and the moon's times moon_share.
vec3 sky_air_lit(vec3 dir, float moon_share) {
    vec2 uv_sun = skyview_uv(dir, g_sun_dir.xyz);
    vec2 uv_moon = skyview_uv(dir, g_moon_dir.xyz);
    ivec2 both = skyview_size * ivec2(1, 2);
    vec3 sun_sky = texture(cg_sky_tex, sky_atlas_uv(vec2(uv_sun.x, uv_sun.y * 0.5), both, skyview_origin)).rgb;
    vec3 moon_sky = texture(cg_sky_tex, sky_atlas_uv(vec2(uv_moon.x, uv_moon.y * 0.5 + 0.5), both, skyview_origin)).rgb;
    return sun_sky * sun_illuminance + moon_sky * moon_illuminance() * moon_share;
}

// The light the air gives from a direction (for the light on surfaces).
vec3 sky_air(vec3 dir) {
    return sky_air_lit(dir, 1.0);
}

// The air's light from a direction as you see it.
vec3 sky_air_seen(vec3 dir) {
    return sky_air_lit(dir, night_sky_seen);
}

// A frame that turns with the sky, for the stars: the sun's direction, the axis it turns around, and the third.
mat3 celestial_frame() {
    vec3 s = g_sun_dir.xyz;
    float tilt = radians(sunPathRotation);
    vec3 axis = vec3(0.0, -sin(tilt), cos(tilt));
    axis = normalize(axis - dot(axis, s) * s);
    return mat3(s, cross(axis, s), axis);
}

// How far into dusk or dawn the sun is: 1 with the sun on the horizon, 0 well up or well down.
float twilight() {
    return 1.0 - smoothstep(0.0, 0.25, abs(g_sun_dir.w + 0.02));
}

// The air's light in our look: pushed away from grey, most at dusk and dawn; by day deeper overhead and paler and
// brighter towards the horizon, where you look through the most air; round the setting sun it burns orange, on the
// far side a pink band sits over the horizon (the sky's evening belt), and at night it stays a deep blue.
vec3 sky_look(vec3 c, vec3 dir) {
    float dusk = twilight();
    float day = smoothstep(-0.05, 0.1, g_sun_dir.w) * (1.0 - dusk);
    c = push_from_grey(c, mix(mix(1.25, 1.15, day), 1.55, dusk));
    float up = clamp(dir.y, 0.0, 1.0);
    float horizon = 1.0 - smoothstep(0.0, 0.35, up);
    c = mix(c, luminance(c) * vec3(0.93, 1.0, 1.12), 0.4 * horizon * day);
    c *= 1.0 + day * (0.22 * horizon - 0.15 * smoothstep(0.3, 1.0, up));
    vec2 flat_dir = normalize(dir.xz + vec2(1e-5, 0.0));
    vec2 flat_sun = normalize(g_sun_dir.xz + vec2(1e-5, 0.0));
    float toward_sun = dot(flat_dir, flat_sun) * 0.5 + 0.5; // 1 under the sun, 0 away
    float low = 1.0 - smoothstep(0.0, 0.45, max(dir.y, 0.0));
    vec3 glow = mix(vec3(1.0), vec3(1.45, 0.95, 0.7), sqr(toward_sun) * low * dusk);
    float belt = smoothstep(0.02, 0.1, dir.y) * (1.0 - smoothstep(0.12, 0.35, dir.y)) * sqr(1.0 - toward_sun);
    glow = mix(glow, vec3(1.3, 0.95, 1.15), belt * dusk);
    c *= glow;
    // Rain: the sky closes into grey, a little cool, and darker.
    c = mix(c, vec3(luminance(c)) * vec3(0.93, 0.97, 1.04), 0.8 * rainStrength) * mix(1.0, 0.55, rainStrength);
    return c;
}

// The sun's disc, its edge a little darker (limb darkening), and the glow round it.
vec3 sun_disc(vec3 dir, vec3 through) {
    float cos_d = dot(dir, g_sun_dir.xyz);
    float angle = acos(clamp(cos_d, -1.0, 1.0));
    // The glow: a tight one the bloom spreads, and a wide faint one, warmer as the sun sinks.
    float dusk = twilight();
    vec3 glow_color = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 0.6, 0.3), dusk);
    vec3 c = glow_color * (0.3 * exp(-angle / 0.022) + 0.05 * exp(-angle / 0.12)) * sun_illuminance;
    if (angle < sun_radius) {
        float r = angle / sun_radius;
        float limb = 1.0 - 0.6 * (1.0 - sqrt(max(0.0, 1.0 - r * r)));
        float edge = 1.0 - smoothstep(0.9, 1.0, r);
        // Bright enough to bloom, not the real sun's thousands.
        c += sun_illuminance * 6.0 * limb * edge;
    }
    return c * through;
}

// The moon: the real Moon's near side (textures/moon.png, from NASA's maps: tools/moon_map.py), MOON_SIZE times
// Minecraft's, lit like a ball from the side its phase says (full at phase 0, new at 4; waxing lit on the side it
// travels towards, waning on the other). Lit as the Moon is: its dust sends light back the way it came, so the full
// moon is evenly bright to its edge, and the slopes of its craters, steepened, cast their shade along the line
// between day and night, where they show the most. Its dark part is faintly lit by the Earth. A cool silvery white.
uniform sampler2D cg_moon; // rgb its colour (sRGB), a its height

float moon_radius() {
    return 0.042 * MOON_SIZE;
}

const float moon_relief = 10.0; // how much steeper than the real ones its slopes are drawn

vec3 moon_disc(vec3 dir, vec3 through) {
    vec3 m = g_moon_dir.xyz;
    float radius = moon_radius();
    float cos_d = dot(dir, m);
    if (cos_d < cos(radius * 1.2)) {
        return vec3(0.0);
    }
    float tilt = radians(sunPathRotation);
    vec3 axis = vec3(0.0, -sin(tilt), cos(tilt));
    vec3 across = normalize(cross(axis, m)); // the way it travels
    vec3 up = cross(m, across);
    vec2 uv = vec2(dot(dir, across), dot(dir, up)) / radius;
    float r = length(uv);
    float edge = 1.0 - smoothstep(1.0 - 1.5 * pixel_angle() / radius, 1.0, r);
    if (edge <= 0.0) {
        return vec3(0.0);
    }
    vec2 tc = uv * vec2(0.5, -0.5) + 0.5;
    // Read over at least what one screen pixel covers of the map (the map has no smaller copies): finer slopes, made
    // ten times steeper, would only sparkle black and white along the line between day and night. Four reads at the
    // corners of that square give the colour and the slopes across it.
    vec2 size = vec2(textureSize(cg_moon, 0));
    float span = max(1.0, 0.5 * pixel_angle() / radius * size.x);
    vec2 d = 0.5 * span / size;
    vec4 a = texture(cg_moon, tc + vec2(-d.x, -d.y));
    vec4 b = texture(cg_moon, tc + vec2(d.x, -d.y));
    vec4 c = texture(cg_moon, tc + vec2(-d.x, d.y));
    vec4 e = texture(cg_moon, tc + vec2(d.x, d.y));
    vec4 surface = 0.25 * (a + b + c + e);
    // Slopes in height per two texels, as moon_relief was set for.
    float hx = ((b.a + e.a) - (a.a + c.a)) / span;
    float hy = ((a.a + b.a) - (c.a + e.a)) / span;
    vec3 n = vec3(uv, sqrt(max(1.0 - r * r, 0.0)));
    vec3 bumped = normalize(n - vec3(hx, hy, 0.0) * moon_relief);

    float angle = float(moonPhase) * (PI / 4.0);
    vec3 sun_side = vec3(-sin(angle), 0.0, cos(angle));
    float mu0 = dot(bumped, sun_side);
    float mu = max(bumped.z, 0.05);
    // Lommel-Seeliger (flat to the edge at full moon) with a little Lambert in it.
    float shade = mu0 > 0.0 ? mix(mu0, 2.0 * mu0 / (mu0 + mu), 0.8) : 0.0;
    float day = smoothstep(-0.03, 0.04, dot(n, sun_side));
    shade *= day;

    vec3 albedo = srgb_to_linear(surface.rgb) / 0.38;
    albedo = mix(vec3(luminance(albedo)), albedo, 0.75);
    albedo = pow(albedo, vec3(1.25)) * vec3(0.88, 0.95, 1.08);
    float bright = 0.17 * NIGHT_BRIGHTNESS * 1.7;
    vec3 color = albedo * (shade + 0.02 * (1.0 - day)) * bright;
    return color * edge * through;
}

// The glow round the moon, cool and faint: a tight one about the disc's size and a wide faint one. It comes from the
// lit part only, so it shrinks with the phase and a new moon has none (the night's light keeps its third of a full
// moon's, in moon_illuminance, for play).
vec3 moon_glow(vec3 dir, vec3 through) {
    float angle = acos(clamp(dot(dir, g_moon_dir.xyz), -1.0, 1.0));
    float tight = 0.25 * exp(-max(angle - moon_radius(), 0.0) / (0.5 * moon_radius()));
    float lit = 0.17 * NIGHT_BRIGHTNESS * g_moon_dir.w;
    return vec3(0.75, 0.88, 1.12) * lit * (tight + 0.05 * exp(-angle / 0.25)) * through;
}

// The Milky Way: a soft band across the sky with darker lanes in it, warmer at its heart.
vec3 milky_way(vec3 local) {
    // The band's own frame: tilted against the sun's path, as the real one is.
    const vec3 band_normal = normalize(vec3(0.35, 0.55, 0.76));
    float across = dot(local, band_normal);
    float band = exp(-across * across / 0.03);
    if (band < 0.01) {
        return vec3(0.0);
    }
    float clouds = fbm(local * 6.0, 4);
    float lanes = smoothstep(0.35, 0.65, fbm(local * 11.0 + 3.0, 3));
    float heart = exp(-sqr(dot(local, normalize(vec3(0.9, -0.35, 0.2))) - 1.0) * 4.0);
    vec3 color = mix(vec3(0.75, 0.85, 1.1), vec3(1.15, 0.95, 0.75), heart);
    return color * band * clouds * clouds * mix(1.0, 0.2, lanes * band) * 0.045;
}

// The whole sky in a direction (without the clouds).
vec3 sky(vec3 dir, bool with_sun) {
    vec3 c = sky_look(sky_air_seen(dir), dir);
    float night = 1.0 - smoothstep(-0.15, 0.05, g_sun_dir.w);
    float cam_r = camera_radius(cameraPosition.y);
    vec3 through = air_transmittance(cam_r, max(dir.y, 0.0));
    if (dir.y > -0.02 && night > 0.0) {
        vec3 local = transpose(celestial_frame()) * dir;
        // The brightest come out first as the sky darkens; a bright moon high up hides the faintest.
        float moon_up = g_moon_dir.w * smoothstep(-0.05, 0.25, g_moon_dir.y);
        float limit = -1.5 + 10.0 * night - 1.2 * moon_up;
        vec3 night_sky = star_field(local, 18000.0, vec2(3500.0, 11000.0), 0.0, limit)
                + milky_way(local) * NIGHT_BRIGHTNESS * (1.0 - 0.5 * moon_up);
        c += night_sky * night * through * smoothstep(-0.02, 0.0, dir.y) * (1.0 - rainStrength);
    }
    // Rain hides the sun, the moon and the stars, as in Minecraft.
    c += (moon_glow(dir, through) + moon_disc(dir, through)) * (1.0 - rainStrength);
    if (with_sun) {
        c += sun_disc(dir, through) * (1.0 - rainStrength);
    }
    return c;
}

// The light on Minecraft's clouds: the sun's and the sky's from above, as on any surface, and through dusk and dawn
// the glow of the low sky in their own direction and the low sun itself, reddened by its long way through the air
// (high up, clouds keep it a little after the land has lost it), so clouds towards the setting sun burn orange and
// those across the sky catch the pink belt. Minecraft greys and darkens the clouds' own colour towards night; the
// light here comes on top of that.
vec3 cloud_light(vec3 dir) {
    vec3 c = g_light_color.rgb * 0.35 + g_sky_up.rgb * 1.2;
    float dusk = twilight();
    if (dusk > 0.0) {
        vec3 low = normalize(normalize(vec3(dir.x, 0.0, dir.z) + vec3(1e-4, 0.0, 0.0)) + vec3(0.0, 0.08, 0.0));
        vec2 flat_sun = normalize(g_sun_dir.xz + vec2(1e-5, 0.0));
        float toward_sun = dot(normalize(low.xz), flat_sun) * 0.5 + 0.5;
        c += sky_look(sky_air_seen(low), low) * dusk * mix(1.0, 3.0, sqr(toward_sun));
        float sun_seen = smoothstep(-0.08, 0.0, g_sun_dir.y);
        vec3 low_sun = air_transmittance(camera_radius(cameraPosition.y), max(g_sun_dir.y, 0.02)) * sun_illuminance;
        c += low_sun * vec3(1.0, 0.85, 0.95) * dusk * sun_seen * mix(0.2, 0.9, sqr(toward_sun));
    }
    return c;
}

#elif defined WORLD_END

// --- The End ---------------------------------------------------------------------------------------------------------

// The End's sky: violet, brighter towards the horizon; the nebula over it, slow drifting clouds of purple light with
// green in their thin parts, brightest low down; coloured stars that twinkle through it; and the End's sun, a small
// pink-white disc with a wide magenta glow, where its light comes from (g_light_dir, see program/frame.csh).
vec3 sky(vec3 dir, bool with_sun) {
    float up = clamp(dir.y, -1.0, 1.0);
    float low = 1.0 - abs(up);
    vec3 c = mix(vec3(0.008, 0.005, 0.016), vec3(0.024, 0.013, 0.042), low * low);

    // The nebula: two layers of noise turning slowly round the up axis in opposite ways (a turn an hour each, so
    // the clock going back to 0 on the hour makes no jump), the clouds where they agree.
    float turn = TAU * frameTimeCounter / 3600.0;
    mat2 spin = mat2(cos(turn), sin(turn), -sin(turn), cos(turn));
    vec3 a = vec3(spin * dir.xz, dir.y).xzy;
    vec3 b = vec3(transpose(spin) * dir.xz, dir.y).xzy;
    float body = fbm(a * 2.2, 5);
    float wisps = fbm(b * 4.6 + 9.0, 4);
    float cloud = smoothstep(0.42, 0.8, body * 0.75 + wisps * 0.35);
    float green = smoothstep(0.55, 0.75, wisps) * (1.0 - smoothstep(0.5, 0.9, cloud));
    vec3 nebula = mix(vec3(0.55, 0.22, 1.0), vec3(0.95, 0.35, 0.75), smoothstep(0.6, 1.0, cloud)) * cloud;
    nebula += vec3(0.12, 0.5, 0.3) * green * 0.6;
    c += nebula * 0.1 * (0.5 + 0.5 * low * low);

    c += star_field(dir, 5000.0, vec2(4500.0, 9000.0), 0.6, 99.0) * vec3(0.95, 0.85, 1.1) * 0.5;

    if (with_sun) {
        float angle = acos(clamp(dot(dir, g_light_dir.xyz), -1.0, 1.0));
        vec3 glow = vec3(0.9, 0.4, 0.75) * (0.08 * exp(-angle / 0.05) + 0.02 * exp(-angle / 0.35));
        float disc = 1.0 - smoothstep(0.025, 0.03, angle);
        c += glow + vec3(1.0, 0.75, 0.9) * disc * 1.5;
    }
    return c;
}

#else

// --- The Nether ------------------------------------------------------------------------------------------------------

uniform vec3 fogColor; // Minecraft's fog colour for the biome you're in, blended as you cross from one to the next

// The Nether's haze: its biome's fog colour (crimson red, warped violet, soul sand teal, basalt ash grey, the wastes'
// red) at one brightness whatever the biome, a little towards grey. The lava sea's glow and the haze under the
// ceiling are added along each view (program/fog.fsh).
vec3 nether_haze() {
    vec3 fog = srgb_to_linear(fogColor);
    vec3 hue = mix(vec3(1.0), fog / max(luminance(fog), 1e-5), 0.8);
    return hue * 0.022 * NIGHT_BRIGHTNESS;
}

// How thick it is (per block): from the fog colour's own brightness, so basalt deltas' ash and the soul sand valley's
// blue haze are the thickest and the warped forest the clearest (about half gone at 25, 35, 70 and 85 blocks).
float nether_thickness() {
    return mix(0.008, 0.028, smoothstep(0.03, 0.4, luminance(fogColor)));
}

vec3 sky(vec3 dir, bool with_sun) {
    return nether_haze();
}

#endif

#if defined NO_SKY_LIGHT
// No clouds here, so no shadows of them.
float cloud_shadow(vec3 scene_pos) {
    return 1.0;
}
#endif

#endif

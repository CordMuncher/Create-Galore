// Water's waves (its colour is in lib/water_color.glsl).
//
// Waves: four layers of smooth noise, each stretched into crests across its own direction and running along it at
// about the speed of real deep-water waves of its size. The biggest, the swell (crests about 14 blocks apart), runs
// with the clouds' steady wind (lib/wind.glsl) and only builds where there is open water up-wind of it; the others
// cross it from about 20 to 45 degrees either side, down to ripples a third of a block across. The crests bend a
// little along their length. Each layer's grid is laid on a whole-number direction, so the pattern repeats every
// 1024 blocks exactly and its time every hour exactly (frameTimeCounter's own repeat): it never jumps. Where a layer
// is finer than a pixel can show (far off, or seen at a grazing angle), it fades out and its slopes become
// roughness instead, which spreads the sun's glint and the reflection the way all those unseen waves would.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/common.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/lighting.glsl"
#include "/lib/wind.glsl"
#include "/lib/water_color.glsl"

// The light in the water about you, from the sky and the sun above as much of it as reaches where you are
// (eye_sky: Minecraft's smoothed sky light at your eyes, 0..1), and a faint glow of its own in the dark.
vec3 water_light_around(float eye_sky) {
    // The sun's own share is small when its shafts are drawn (program/water_shafts.csh adds it, shaded).
    vec3 lit = (g_light_color.rgb * (SHAFT_STRENGTH > 0.0 ? 0.1 : 0.25) + g_sky_up.rgb) * vanilla_sky_share(eye_sky);
    return lit + vec3(0.02, 0.04, 0.05) * CAVE_BRIGHTNESS * 0.2;
}

// --- Waves -----------------------------------------------------------------------------------------------------------

// Smooth gradient noise and its slope (d/dx, d/dy) on a grid of whole numbers that repeats every period cells (powers
// of two).
vec3 wave_noise(vec2 p, ivec2 period, uint seed) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    vec2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
    ivec2 c = ivec2(i);
    ivec2 c0 = c & (period - 1);
    ivec2 c1 = (c0 + 1) & (period - 1);
    float ha = hash33(uvec3(uvec2(c0.x, c0.y), seed)).x * TAU;
    float hb = hash33(uvec3(uvec2(c1.x, c0.y), seed)).x * TAU;
    float hc = hash33(uvec3(uvec2(c0.x, c1.y), seed)).x * TAU;
    float hd = hash33(uvec3(uvec2(c1.x, c1.y), seed)).x * TAU;
    vec2 ga = vec2(cos(ha), sin(ha));
    vec2 gb = vec2(cos(hb), sin(hb));
    vec2 gc = vec2(cos(hc), sin(hc));
    vec2 gd = vec2(cos(hd), sin(hd));
    float va = dot(ga, f);
    float vb = dot(gb, f - vec2(1.0, 0.0));
    float vc = dot(gc, f - vec2(0.0, 1.0));
    float vd = dot(gd, f - vec2(1.0, 1.0));
    float k1 = vb - va;
    float k2 = vc - va;
    float k4 = va - vb - vc + vd;
    float value = va + u.x * k1 + u.y * k2 + u.x * u.y * k4;
    vec2 slope = ga + u.x * (gb - ga) + u.y * (gc - ga) + u.x * u.y * (ga - gb - gc + gd)
               + du * (vec2(k1, k2) + vec2(u.y, u.x) * k4);
    return vec3(value, slope);
}

// The layers. way: the whole-number direction it runs (x, z); along, across: the grid's cell in blocks times the
// way's length (powers of two, so 1024 blocks hold a whole number of cells); height: how high its waves stand at
// full strength (blocks); periods: how many times its pattern runs through its repeat in an hour, which sets its
// speed (about half that of real deep-water waves twice its cell long: 2.4, 1.4, 0.85 and 0.54 blocks a second).
const int wave_layers = 4;
const ivec2 wave_way[4] = ivec2[](ivec2(1, -2), ivec2(1, -1), ivec2(0, -1), ivec2(3, -1));
const float wave_along[4] = float[](16.0, 4.0, 1.0, 1.0);
const float wave_across[4] = float[](64.0, 8.0, 2.0, 1.0);
const float wave_height[4] = float[](0.24, 0.085, 0.028, 0.0065);
const float wave_periods[4] = float[](19.0, 7.0, 3.0, 6.0);
// What rain does to each (its drops roughen the small waves, not the swell).
const float wave_rain[4] = float[](1.0, 2.5, 5.0, 5.0);

struct Waves {
    vec2 slope;  // d height / dx, d height / dz
    float rough; // the slopes' spread finer than a pixel (their variance), from the layers faded out
};

// The surface's waves at a point. local: the point within 1024 blocks (water_local); strength: how strong the waves
// are here (0 calm .. 1, more with wind and open sky); swell: how much room the swell has to build (0..1); rain: how
// hard it rains on it (0..1); footprint: how wide the spot one pixel covers there is (blocks).
Waves water_waves(vec2 local, float strength, float swell, float rain, float footprint) {
    float t = frameTimeCounter / 3600.0; // through the hour
    Waves w;
    w.slope = vec2(0.0);
    w.rough = 0.0;
    for (int k = 0; k < wave_layers; k++) {
        vec2 way = vec2(wave_way[k]);
        float way_len = length(way);
        float cell = wave_along[k] / way_len;
        float height = wave_height[k] * strength * mix(1.0, wave_rain[k], rain);
        if (k == 0) {
            height *= swell;
        }
        // The layer's typical slope, for the roughness it leaves where it fades.
        float typical = height / cell;
        float fade = 1.0 - smoothstep(0.3, 1.0, footprint / cell);
        w.rough += (1.0 - fade) * typical * typical * 0.5;
        if (fade <= 0.0 || height <= 0.0) {
            continue;
        }
        ivec2 period = ivec2(1024.0 / wave_along[k], 1024.0 / wave_across[k]);
        // The grid: across its way and along it, in cells.
        vec2 q = vec2(dot(local, way) / wave_along[k], dot(local, vec2(-way.y, way.x)) / wave_across[k]);
        q.x -= fract(wave_periods[k] * t) * float(period.x);
        // Crests bend along their length, unevenly: a slower noise of the same kind, over 4 cells along the crest
        // and 16 across, shifts them back and forth (it runs with the waves, so the bends travel with them).
        vec3 b = wave_noise(vec2(q.y / 4.0, q.x / 16.0), max(ivec2(period.y / 4, period.x / 16), 1), uint(5 + k * 31));
        float bend = 1.2 * b.x;
        vec2 bend_slope = 1.2 * vec2(b.z / 16.0, b.y / 4.0); // d bend / d q
        vec3 n = wave_noise(vec2(q.x + bend, q.y), period, uint(17 + k * 31));
        vec2 dq = vec2(n.y * (1.0 + bend_slope.x), n.z + n.y * bend_slope.y); // d noise / d q
        // Back to blocks: q.x = (x a + z b) / along, q.y = (-x b + z a) / across.
        vec2 d_world = vec2(dq.x * way.x / wave_along[k] - dq.y * way.y / wave_across[k],
                            dq.x * way.y / wave_along[k] + dq.y * way.x / wave_across[k]);
        w.slope += d_world * height * fade;
    }
    return w;
}

// A point (scene space) within 1024 blocks, for the waves: whole multiples of 1024 taken off the world position, so
// it stays exact far from the world's origin and the pattern (which repeats every 1024) doesn't change.
vec2 water_local(vec3 scene_pos) {
    return vec2(cameraPositionInt.xz & 1023) + cameraPositionFract.xz + scene_pos.xz;
}

// Calm and choppy stretches, hundreds of blocks across: the waves' strength there (0.4 .. 1.5).
float water_chop(vec3 scene_pos) {
    vec2 p = vec2(cameraPositionInt.xz & 8191) + cameraPositionFract.xz + scene_pos.xz;
    float a = textureLod(cg_cloud_noise, vec3(p.x / 2048.0, 0.29, p.y / 2048.0), 0.0).a;
    return mix(0.4, 1.5, smoothstep(0.3, 0.7, a));
}

vec3 wave_normal(Waves w) {
    return normalize(vec3(-w.slope.x, 1.0, -w.slope.y));
}

// Rain on the water: each drop makes a ring that spreads to 0.4 blocks and dies away, about one a second in each
// 0.24-block cell, at random times and places. The slope it adds (scaled by amount), and through rough what it adds
// where its rings are finer than a pixel.
vec2 rain_rings(vec2 local, float amount, float footprint, inout float rough) {
    const float cell = 0.24;
    const float period = 1.0 / 0.9; // seconds between one cell's drops (3240 of them each hour: a whole number)
    const float spread = 0.4;
    const float ripple = 0.07;
    float fade = 1.0 - smoothstep(0.012, 0.035, footprint);
    rough += (1.0 - fade) * amount * 0.06;
    if (fade <= 0.0 || amount <= 0.0) {
        return vec2(0.0);
    }
    vec2 g = local / cell;
    ivec2 base = ivec2(floor(g));
    vec2 slope = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 c = base + ivec2(x, y);
            uvec2 cu = uvec2(c + 65536);
            float phase = hash33(uvec3(cu, 90u)).x;
            float clock = frameTimeCounter / period + phase;
            float age = fract(clock);
            uint drop = uint(floor(clock));
            vec3 h = hash33(uvec3(cu, drop * 8u + 91u));
            vec2 centre = (vec2(c) + 0.1 + 0.8 * h.xy) * cell;
            vec2 d = local - centre;
            float r = length(d);
            float x_ring = r - spread * sqrt(age);
            float env = exp(-x_ring * x_ring / (0.035 * 0.035)) * sqr(1.0 - age);
            float s = cos(TAU * x_ring / ripple) * env;
            slope += d / max(r, 1e-3) * s;
        }
    }
    return slope * amount * 0.6 * fade;
}

#endif

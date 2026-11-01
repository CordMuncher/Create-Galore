// Water's waves, and the caustics they make (its colour is in lib/water_color.glsl).
//
// Waves: four layers of smooth noise, each stretched into crests across its own direction and running along it at
// 0.4 of the speed of real deep-water waves its size. The biggest, the swell (crests about 28 blocks apart), runs with
// the clouds' steady wind (lib/wind.glsl) and only builds where there is open water up-wind of it; the next (crests
// about 15 blocks apart) carries lakes and the sea, and is calmer on ponds and rivers too. The two small ones are
// weak, and the ripples show only near you. Each runs a golden angle (137.5 degrees) round from the one before, so no
// two run together and nothing marches one way. The crests bend a little along their length. Each layer's grid is
// laid on a whole-number direction, so the pattern repeats every 1024 blocks exactly and its time every hour exactly
// (frameTimeCounter's own repeat): it never jumps. Where a layer is finer than a pixel can show (far off, or seen at a
// grazing angle), it fades out and its slopes become roughness instead, which spreads the sun's glint and tilts the
// water you see towards you, the way all those unseen waves would.

#if !defined LIB_WAVES
#define LIB_WAVES

#include "/lib/common.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/wind.glsl"
#include "/lib/water_color.glsl"

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

// The layers. way: the whole-number direction it runs (x, z), each a golden angle round from the one before (-63, 76,
// 211 and 349 degrees); along, across: the grid's cell in blocks times the way's length (powers of two, so 1024 blocks
// hold a whole number of cells); height: how high its waves stand at full strength (blocks); periods: how many times
// its pattern runs through its repeat in an hour, which sets its speed (0.4 of real deep-water waves twice its cell
// long, crests that far apart: 2.6, 1.9, 1.2 and 0.6 blocks a second).
const int wave_layers = 4;
const ivec2 wave_way[4] = ivec2[](ivec2(1, -2), ivec2(1, 4), ivec2(-5, -3), ivec2(5, -1));
const float wave_along[4] = float[](32.0, 32.0, 16.0, 4.0);
const float wave_across[4] = float[](64.0, 64.0, 32.0, 8.0);
const float wave_height[4] = float[](0.5, 0.23, 0.033, 0.0047);
const float wave_periods[4] = float[](21.0, 28.0, 24.0, 11.0);
// What rain does to each (its drops roughen the small waves, not the swell).
const float wave_rain[4] = float[](1.0, 2.0, 6.0, 12.0);
// How far off (blocks) the two small layers fade from what you see (into roughness): the ripples only near you.
const vec2 wave_near[4] = vec2[](vec2(1e4, 2e4), vec2(1e4, 2e4), vec2(32.0, 80.0), vec2(12.0, 32.0));

struct Waves {
    vec2 slope;  // d height / dx, d height / dz
    float seen;  // the shown slopes' spread (their variance)
    float rough; // the slopes' spread finer than a pixel or too far to show (their variance), from the layers faded out
};

// The surface's waves at a point. local: the point within 1024 blocks (water_local); strength: how strong the waves
// are here (0 calm .. 1, more with wind and open sky); swell: how much room the swell has to build (0..1); rain: how
// hard it rains on it (0..1); footprint: how wide the spot one pixel covers there is (blocks); distance_away: how far
// from you it is (blocks; 0 for all of them).
Waves water_waves(vec2 local, float strength, float swell, float rain, float footprint, float distance_away) {
    float t = frameTimeCounter / 3600.0; // through the hour
    Waves w;
    w.slope = vec2(0.0);
    w.seen = 0.0;
    w.rough = 0.0;
    for (int k = 0; k < wave_layers; k++) {
        vec2 way = vec2(wave_way[k]);
        float way_len = length(way);
        float cell = wave_along[k] / way_len;
        float height = wave_height[k] * strength * mix(1.0, wave_rain[k], rain);
        if (k == 0) {
            height *= swell;
        } else if (k == 1) {
            height *= mix(0.4, 1.0, swell);
        }
        // The layer's typical slope, for the roughness it leaves where it fades.
        float typical = height / cell;
        float fade = (1.0 - smoothstep(0.3, 1.0, footprint / cell))
                   * (1.0 - smoothstep(wave_near[k].x, wave_near[k].y, distance_away));
        w.rough += (1.0 - fade) * typical * typical * 0.5;
        w.seen += fade * typical * typical * 0.5;
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

// The waves too fine to show (rough: their slopes' variance) tilt the water you see towards you: of all their facets,
// those turned towards you show more of themselves, and at a grazing look those turned away hide behind the crests.
// On average (each facet counted by how much of it you see) the slope towards you is rough / tan(elevation) looking
// down from high, about 1.25 times its spread at a grazing look. So far water mirrors the sky higher up (bluer than
// the horizon), reflects less and shows more of its own colour, as a real sea does. The slope to add (d height / dx,
// dz); to_eye: towards your eye.
vec2 facing_tilt(float rough, vec3 to_eye) {
    float across = length(to_eye.xz);
    if (rough <= 0.0 || across < 1e-4) {
        return vec2(0.0);
    }
    float tan_e = abs(to_eye.y) / across;
    return -to_eye.xz / across * (rough / (tan_e + 0.8 * sqrt(rough)));
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

// How the waves focus the light on what lies below blocks under the surface (caustics): the light through a spot of
// the surface, bent at its wave's slope (Snell's law), is followed down that far, and the patch it lands on compared
// with the patch it left (the light through a small step either way says how big both are): where the slopes bend
// the light together it lands on less and is brighter, where they spread it, darker. 1 for flat water and right under
// the surface. local: the spot on the surface right over the point (water_local); light_dir: towards the light;
// footprint: how fine the pattern may be there (blocks): waves smaller than that only blur it, so they don't count.
//
// How far down the bent light is followed: most of a real floor's light lines come from ripples of a few centimetres,
// far too fine to draw, which ride on the waves and bend the light far more than the drawn waves do, so it is followed
// as far as the drawn waves would need to bend it as much (and the lines follow the waves you see: bigger and slower
// where they are). That grows over the first blocks down and then holds: past there the ripples' lines have crossed
// and blur into each other. And the light is looked for where it left the surface (a step back along the way it was
// bent), and what the lines still add on average (more the stronger the waves: about a tenth at full strength, from
// a simulation of these waves) taken back off, so the floor gets as much light as under flat water, only gathered
// into lines.
const float caustic_reach = 52.0;

vec2 caustic_land(vec2 at, vec3 light_dir, float strength, float rain, float reach, float footprint) {
    vec3 n = wave_normal(water_waves(at, strength, 1.0, rain, footprint, 0.0));
    vec3 down = refract(-light_dir, n, 1.0 / water_ior);
    return down.xz * (reach / max(-down.y, 0.2));
}

float caustic_focus(vec2 local, vec3 light_dir, float strength, float rain, float below, float footprint) {
    float reach = caustic_reach * (1.0 - exp(-below));
    vec3 flat_down = refract(-light_dir, vec3(0.0, 1.0, 0.0), 1.0 / water_ior);
    vec2 flat_land = flat_down.xz * (reach / max(-flat_down.y, 0.2));
    vec2 from = local - (caustic_land(local, light_dir, strength, rain, reach, footprint) - flat_land);
    const float step = 0.05;
    vec2 land = caustic_land(from, light_dir, strength, rain, reach, footprint);
    vec2 land_x = caustic_land(from + vec2(step, 0.0), light_dir, strength, rain, reach, footprint);
    vec2 land_z = caustic_land(from + vec2(0.0, step), light_dir, strength, rain, reach, footprint);
    mat2 spread = mat2(1.0) + mat2((land_x - land) / step, (land_z - land) / step);
    float focus = clamp(1.0 / max(abs(determinant(spread)), 1e-4), 0.0, 4.0);
    return focus / (1.0 + 0.1 * pow(reach * strength / caustic_reach, 2.4));
}

#endif

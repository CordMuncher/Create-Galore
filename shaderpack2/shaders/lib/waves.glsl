// Water's waves, and the caustics they make (its colour is in lib/water_color.glsl).
//
// Waves: a wind sea in seven bands, from a swell with crests about 14 blocks apart down to ripples a fifth of a block
// apart, each band half the size of the one before. Every band is the same baked pattern (textures/water_waves.dat,
// tools/wave_noise.py: a whole band of waves running down the wind and about 45 degrees either side of it, as real
// wind waves do) laid down at its own size, turned over or shifted so no two bands line up, and swung at its own
// rate (0.4 of the speed of real deep-water waves its size), so crests travel, catch each other up and dissolve
// instead of sliding by as a fixed picture. The slopes are those of a real sea in a fresh breeze (each band as steep
// as the next, about 7 degrees in all on open water), so the surface has relief you can see, the sky in it is broken
// by the waves, and the sun lays a path of glitter on it. The wind is the clouds' steady wind (lib/wind.glsl). The two
// longest bands only build where there is open water up-wind, so a pond or a river stays rippled but calm while a
// lake or the sea rolls. Each band's grid is laid on the wind's whole-number direction, so the pattern repeats every
// 1024 blocks exactly and its time every hour exactly (frameTimeCounter's own repeat): it never jumps. Where a band
// is finer than a pixel can show (far off, or seen at a grazing angle), it fades out and its slopes become roughness
// instead, which spreads the sun's glint and the mirrored sky and tilts the water you see towards you, the way all
// those unseen waves would.

#if !defined LIB_WAVES
#define LIB_WAVES

#include "/lib/common.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/wind.glsl"
#include "/lib/water_color.glsl"

uniform sampler2D cg_water_waves; // one band of waves: rg its slope now, ba a quarter of a swing later (see above)

// The bands. span: the pattern's repeat across the wind's whole-number way (1, -2), in blocks times the way's length
// (powers of two, so 1024 blocks hold a whole number of repeats): the pattern is 114, 57, 29, 14, 7.2, 3.6 and 1.8
// blocks across, its strongest waves an eighth of that from crest to crest. steep: the band's slope (root mean
// square) at full strength. swings: how many times it swings in an hour, which sets its waves' speed (about 1.9, 1.3,
// 0.95, 0.65, 0.47, 0.33 and 0.24 blocks a second). shift: where the pattern is laid (so no two bands line up); every
// other band is also turned over across the wind. rain: what rain does to it (its drops roughen the small waves, not
// the swell).
const int wave_bands = 7;
const float wave_span[7] = float[](256.0, 128.0, 64.0, 32.0, 16.0, 8.0, 4.0);
const float wave_steep[7] = float[](0.036, 0.046, 0.05, 0.05, 0.046, 0.038, 0.03);
const float wave_swings[7] = float[](470.0, 665.0, 940.0, 1330.0, 1880.0, 2660.0, 3760.0);
const vec2 wave_shift[7] = vec2[](vec2(0.0, 0.0), vec2(0.37, 0.61), vec2(0.71, 0.19), vec2(0.13, 0.83),
                                  vec2(0.59, 0.41), vec2(0.89, 0.07), vec2(0.23, 0.67));
const float wave_rain[7] = float[](1.0, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0);
const float wave_tex_peak = 4.0; // tools/wave_noise.py's PEAK

struct Waves {
    vec2 slope;  // d height / dx, d height / dz
    float seen;  // the shown slopes' spread (their variance)
    float rough; // the slopes' spread finer than a pixel (their variance), from the bands faded out
};

// The surface's waves at a point. local: the point within 1024 blocks (water_local); strength: how strong the waves
// are here (0 calm .. 1, more with wind and open sky); swell: how much room the long waves have to build (0..1);
// rain: how hard it rains on it (0..1); footprint: how wide the spot one pixel covers there is (blocks).
Waves water_waves(vec2 local, float strength, float swell, float rain, float footprint) {
    float hour = frameTimeCounter / 3600.0;
    const vec2 way = vec2(wind_way);
    const float way_len = length(vec2(wind_way));
    // Along the wind and across it (to the wind's left), in blocks times the way's length.
    vec2 q = vec2(dot(local, way), dot(local, vec2(-way.y, way.x)));
    Waves w;
    w.slope = vec2(0.0);
    w.seen = 0.0;
    w.rough = 0.0;
    for (int k = 0; k < wave_bands; k++) {
        float steep = wave_steep[k] * strength * mix(1.0, wave_rain[k], rain);
        if (k == 0) {
            steep *= swell;
        } else if (k == 1) {
            steep *= mix(0.35, 1.0, swell);
        } else if (k == 2) {
            steep *= mix(0.7, 1.0, swell);
        }
        // Its strongest waves' length; it shows while a pixel is well under that.
        float crest = wave_span[k] / way_len / 8.0;
        float fade = 1.0 - smoothstep(0.12, 0.45, footprint / crest);
        w.rough += (1.0 - fade) * steep * steep;
        w.seen += fade * steep * steep;
        if (fade <= 0.0 || steep <= 0.0) {
            continue;
        }
        float flip = (k & 1) == 0 ? 1.0 : -1.0;
        vec2 uv = vec2(q.x, q.y * flip) / wave_span[k] + wave_shift[k];
        vec4 t = (textureLod(cg_water_waves, uv, 0.0) * 2.0 - 1.0) * wave_tex_peak;
        float swing = TAU * fract(wave_swings[k] * hour);
        vec2 s = t.xy * cos(swing) + t.zw * sin(swing); // d/du, d/dv, root mean square 1
        s.y *= flip;
        // Back to the world's x and z.
        w.slope += vec2(s.x * way.x - s.y * way.y, s.x * way.y + s.y * way.x) / way_len * (steep * fade);
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
// Which waves: those with crests about a block apart and longer. The finer ripples would focus the light within a
// block of the surface and cross it into a grain below that, so they are left out (caustic_fine), and what is left
// draws a net of light lines with meshes about a block across, drifting with the waves you see. How far down the
// bent light is followed (caustic_reach): it grows over the first blocks down and then holds at the depth where
// these waves' lines are sharpest: past there they would cross and blur into each other. The light is looked for
// where it left the surface (a step back along the way it was bent), so the floor gets about as much light as under
// flat water (within a few parts in a hundred, measured over these waves), only gathered into lines.
// For the look: the lines' brightest parts roll off softly towards twice the flat water's light (a hard cap at four
// read as white lines pasted on bright sand), and the lines lose their contrast with depth (by e every 10 blocks
// below the surface), as the light scattered on its way down fills in between them.
const float caustic_reach = 8.0;
const float caustic_fine = 0.22;

vec2 caustic_land(vec2 at, vec3 light_dir, float strength, float rain, float reach, float footprint) {
    vec3 n = wave_normal(water_waves(at, strength, 1.0, rain, footprint));
    vec3 down = refract(-light_dir, n, 1.0 / water_ior);
    return down.xz * (reach / max(-down.y, 0.2));
}

float caustic_focus(vec2 local, vec3 light_dir, float strength, float rain, float below, float footprint) {
    footprint = max(footprint, caustic_fine);
    float reach = caustic_reach * (1.0 - exp(-below * 0.5));
    vec3 flat_down = refract(-light_dir, vec3(0.0, 1.0, 0.0), 1.0 / water_ior);
    vec2 flat_land = flat_down.xz * (reach / max(-flat_down.y, 0.2));
    vec2 from = local - (caustic_land(local, light_dir, strength, rain, reach, footprint) - flat_land);
    const float step = 0.05;
    vec2 land = caustic_land(from, light_dir, strength, rain, reach, footprint);
    vec2 land_x = caustic_land(from + vec2(step, 0.0), light_dir, strength, rain, reach, footprint);
    vec2 land_z = caustic_land(from + vec2(0.0, step), light_dir, strength, rain, reach, footprint);
    mat2 spread = mat2(1.0) + mat2((land_x - land) / step, (land_z - land) / step);
    float focus = 1.0 / max(abs(determinant(spread)), 1e-4);
    float over = max(focus - 1.0, 0.0);
    focus = min(focus, 1.0) + over / (1.0 + over); // towards 2
    return 1.0 + (focus - 1.0) * exp(-below / 10.0);
}

#endif

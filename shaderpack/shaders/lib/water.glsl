// Water in the lit overworld: waves on every water surface, and ripples where something moves through it, both only
// in the surface's normal (the water's blocks stay where they are, so its motion vectors stay exact).
//
// The waves: Alpha Piscium's (GPL-3.0), layers of a smooth cellular noise (textures/wave_noise.bin), each turned by
// the golden angle from the last, stretched and drifting at its own speed, all warped by a slow curl field
// (wave_curl.bin), so no pattern lines up or repeats, from a boat or from the air. The sea gets the full swell, lakes,
// rivers and swamps less of it (by the biome you are in, see cg_swell in shaders.properties).
//
// The ripples: the wave equation on a grid of 512 by 512 cells around you, a quarter of a block each (128 blocks
// across), stepped 60 times a second whatever the frame rate (water_sim.csh, after the shadow pass). The shadow pass
// marks, in each cell, the height of the water's surface and how low and how high anything else that casts a shadow
// reaches there (you, mobs, boats, Sable ships, Create's contraptions and water wheels: whatever the shadow
// options let cast shadows). Where something reaches through the surface it pushes the water down, so a thing
// standing still leaves a dimple and a thing moving leaves ripples and a wake.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/settings.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

#ifdef LIT_WATER_RIPPLES
const int water_sim_size = 512;      // cells across
const float water_cells_per_block = 4.0;
const int water_wrap = 1 << 20;      // cell positions are kept modulo this (exact as floats)

// A height relative to the camera (blocks, -128..128) in the marks' 16 bits, 0 meaning none.
uint water_height_mark(float y) {
    return uint(clamp((y + 128.0) * 256.0, 1.0, 65535.0));
}

float water_height_from_mark(uint mark) {
    return float(mark) / 256.0 - 128.0;
}

// The cell a point (scene space) is in, counted from the corner of the grid centred on the camera's cell.
ivec2 water_cell(vec3 scene_pos, vec3 camera_fract) {
    return ivec2(floor((camera_fract.xz + scene_pos.xz) * water_cells_per_block))
            - ivec2(floor(camera_fract.xz * water_cells_per_block)) + water_sim_size / 2;
}

// A difference of two positions modulo water_wrap, back to -water_wrap/2..water_wrap/2.
ivec2 water_wrap_delta(ivec2 d) {
    return ((d % water_wrap) + water_wrap + water_wrap / 2) % water_wrap - water_wrap / 2;
}
#endif

#if !defined PROGRAM_SHADOW && !defined PROGRAM_WATER_SIM
#include "/lib/motion.glsl"
#include "/lib/globals.glsl"
#include "/lib/wet.glsl"

uniform sampler2D cg_wave_tex;      // R16, 1024 by 1024, repeating
uniform sampler2D cg_wave_curl_tex; // RG16 signed, 128 by 128, repeating
uniform float cg_swell;             // how much of the long waves there is where you are: 1 at sea

// The waves' coordinates per block.
const float wave_scale = 0.03;
// A turn by the golden angle: each layer of the waves lies across the last, so none line up.
const mat2 golden_turn = mat2(-0.737368878, 0.675490294, -0.675490294, -0.737368878);

float wave_noise(vec2 coord) {
    return textureLod(cg_wave_tex, coord * 0.175, 0.0).r;
}

vec2 wave_curl(vec2 coord) {
    return textureLod(cg_wave_curl_tex, coord, 0.0).rg;
}

// A slow, uneven sway, so the curl field doesn't drift in a straight line.
float wave_sway(float x) {
    return cos(1.1 * x + 0.5) * 0.95 + sin(1.6 * x + 1.5) * 0.43 + cos(3.7 * x + 2.2) * 0.15;
}

// Where a point (scene space) is in the waves' coordinates.
vec3 wave_coord(vec3 scene_pos) {
    return (vec3(cameraPositionInt) + cameraPositionFract + scene_pos) * wave_scale;
}

// The water's height (blocks, around 0) at a point in the waves' coordinates, at a time t (seconds). swell: how much
// of the long waves there is (1 out at sea); detail: how much of the shortest (0 far off, where they would only
// sparkle).
float wave_height_at(vec3 p, float swell, float detail, float t) {
    const vec2 drift_dir = vec2(0.777, -0.555);
    const vec2 sway_dir = vec2(-0.21, 0.15);
    vec2 drift = t * drift_dir * 0.1;

    vec2 origin = p.xz + p.y * 0.07; // water at another level gets other waves
    vec2 origin_curl = origin;
    vec2 curl = wave_curl(origin_curl * 0.11 + wave_sway(t * 4.5 + 6.9) * sway_dir * 0.0018 + drift * 0.11) * 0.061;
    vec2 coord = golden_turn * (origin - drift * 0.025);

    // The long waves (crests are the noise's dips: the cells' rims run as ridges).
    float depth = 0.4 * swell * wave_noise((coord + curl + drift * 0.29) * vec2(0.5, 0.8));

    coord = golden_turn * (coord * 2.23);
    curl = golden_turn * (curl * 1.85);
    origin_curl = golden_turn * origin_curl;
    curl += wave_curl(origin_curl * 0.45 - drift * 0.78 - (golden_turn * drift) * 1.57) * 0.026;
    depth -= 0.16 * mix(0.5, 1.0, detail) * wave_noise((coord + curl - drift * 0.96) * vec2(-0.85, 0.52));

    coord = golden_turn * (coord * 1.33 - drift * 1.87);
    curl = golden_turn * (curl * 1.35);
    depth -= 0.59 * mix(0.6, 1.0, swell) * wave_noise((coord + curl) * vec2(-0.13, -0.36));

    coord = golden_turn * (coord * 1.88 - drift * 1.58);
    curl = golden_turn * (curl * 1.35);
    origin_curl = golden_turn * origin_curl;
    curl += wave_curl(origin_curl * 0.98 + sin(t * 6.1 + 2.1) * 0.042 * sway_dir - drift * 2.17
            - (golden_turn * drift) * 1.63) * 0.068;
    depth -= 0.07 * detail * wave_noise((coord + curl) * vec2(-0.57, -0.69) + drift * vec2(3.2, -5.1));

    coord = golden_turn * (coord * 1.87 - drift * 1.68);
    curl = golden_turn * (curl * 1.73);
    depth -= 0.05 * detail * wave_noise((coord + curl) * vec2(0.4, 0.6) - drift * vec2(1.1, 1.1));
    return -depth;
}

// The water's height now.
float wave_height(vec3 p, float swell, float detail) {
    return wave_height_at(p, swell, detail, frameTimeCounter);
}

// The waves' slope (dh/dx, dh/dz) at a point (scene space), over a span of at least span blocks (wider far off, so
// a pixel shows the waves it covers on average rather than whichever one its centre falls on).
vec2 wave_slope(vec3 scene_pos, float swell, float detail, float span) {
    vec3 p = wave_coord(scene_pos);
    float e = max(span, 0.1);
    float h = wave_height(p, swell, detail);
    float hx = wave_height(p + vec3(e * wave_scale, 0.0, 0.0), swell, detail);
    float hz = wave_height(p + vec3(0.0, 0.0, e * wave_scale), swell, detail);
    return vec2(hx - h, hz - h) / e * WATER_WAVES;
}

#ifdef LIT_WATER_RIPPLES
#include "/lib/globals.glsl"

uniform sampler2D cg_water_tex;

// The ripples' height (blocks) at a point (scene space), between the cells' centres.
float ripple_height(vec2 cell, int half_x) {
    vec2 at = clamp(cell, vec2(0.5), vec2(water_sim_size) - 0.5) + vec2(half_x, 0.0);
    return texture(cg_water_tex, at / vec2(2 * water_sim_size, water_sim_size)).r;
}

// The ripples' slope (dh/dx, dh/dz) at a point on the water (scene space), from the state the last of this frame's
// steps left (see water_sim.csh).
vec2 ripple_slope(vec3 scene_pos) {
    vec4 water = global(G_WATER);
    vec4 origin = global(G_WATER_ORIGIN);
    int half_x = (int(water.y + water.x) & 1) * water_sim_size;
    // The point's cell, continuous, counted from the grid's corner.
    ivec2 camera_cell = cameraPositionInt.xz * 4 + ivec2(floor(cameraPositionFract.xz * water_cells_per_block));
    ivec2 corner = water_wrap_delta(camera_cell - ivec2(origin.xy));
    vec2 cell = vec2(corner) + (cameraPositionFract.xz + scene_pos.xz) * water_cells_per_block
            - floor(cameraPositionFract.xz * water_cells_per_block);
    if (any(lessThan(cell, vec2(2.0))) || any(greaterThan(cell, vec2(water_sim_size - 2)))) {
        return vec2(0.0);
    }
    float dx = ripple_height(cell + vec2(1.0, 0.0), half_x) - ripple_height(cell - vec2(1.0, 0.0), half_x);
    float dz = ripple_height(cell + vec2(0.0, 1.0), half_x) - ripple_height(cell - vec2(0.0, 1.0), half_x);
    // Fading out towards the grid's edges.
    vec2 edge = min(cell, vec2(water_sim_size) - cell);
    float fade = smoothstep(2.0, 48.0, min(edge.x, edge.y));
    return vec2(dx, dz) * (water_cells_per_block * 0.5) * fade * WATER_RIPPLES_STRENGTH;
}
#endif

// How the waves gather the sun's (or the moon's) light on what lies under them, depth blocks down towards the light,
// over a point (scene space) dist blocks from the eye, per colour: 1 on average. Each bit of the surface bends the
// light through it sideways by its slope, so under a crest, which curves like a lens, the light converges into bright
// lines, and under a trough it spreads thin: the light's density goes as 1 / the determinant of how the bending moves
// it (the waves' curvature times how far it bends per block down), the area ratio Evan Wallace's WebGL water draws
// its caustics by. The waves seen on the surface are too gentle to focus in a block or two of water, so the light
// here goes through ripples half their size (their steepness kept) and bends four times as far as water would bend
// it: a pool's net of bright lines in a block or two of water, broad and faint further down. Red bends a little less
// than blue, so the lines have coloured edges.
vec3 water_caustics(vec3 scene_pos, float depth, float dist) {
    float fade = smoothstep(0.2, 1.0, depth) * (1.0 - smoothstep(32.0, 80.0, dist));
    if (fade <= 0.0) {
        return vec3(1.0);
    }
    const float finer = 2.0;  // the ripples' size, as a share of the waves'
    const float e = 0.125;    // blocks
    vec3 p = wave_coord(scene_pos + g_light_dir.xyz * depth) * finer;
    const float de = e * finer * wave_scale;
    float t = frameTimeCounter * 1.5;
    float h = wave_height_at(p, cg_swell, 1.0, t);
    float hx0 = wave_height_at(p - vec3(de, 0.0, 0.0), cg_swell, 1.0, t);
    float hx1 = wave_height_at(p + vec3(de, 0.0, 0.0), cg_swell, 1.0, t);
    float hz0 = wave_height_at(p - vec3(0.0, 0.0, de), cg_swell, 1.0, t);
    float hz1 = wave_height_at(p + vec3(0.0, 0.0, de), cg_swell, 1.0, t);
    float hxz = wave_height_at(p + vec3(de, 0.0, de), cg_swell, 1.0, t);
    // The curvature of the ripples (their heights a share of the waves', as their size is).
    float hxx = (hx1 - 2.0 * h + hx0) / (e * e * finer);
    float hzz = (hz1 - 2.0 * h + hz0) / (e * e * finer);
    float hxz2 = (hxz - hx1 - hz1 + h) / (e * e * finer);
    // Past two blocks the light from many places on the surface crosses: the lines stay as they are, fainter deeper.
    float k = min(depth, 2.0) * WATER_WAVES;
    vec3 kc = k * vec3(0.97, 1.0, 1.03);
    vec3 det = (1.0 + kc * hxx) * (1.0 + kc * hzz) - kc * kc * hxz2 * hxz2;
    // Softened where it would focus to a point, and more so far off, where a pixel spans several lines.
    float soft = 0.03 + 0.3 * smoothstep(12.0, 48.0, dist);
    vec3 density = clamp(inversesqrt(det * det + soft), 0.15, 3.0) / (1.0 + 0.1 * k * k);
    return mix(vec3(1.0), density, fade * (1.0 - 0.6 * smoothstep(6.0, 24.0, depth)));
}

// Where the eye, looking at the water's flat top at scene_pos along view_dir (from the eye), meets the short waves'
// relief: they rise and fall about half a block (Waves: more or less), so seen at a slant the crests nearer the eye
// hide what lies behind them. The ray steps down through that half block until it is below the waves, then closes in
// on the crossing (the secant method, as Alpha Piscium does). Only up close; far off the waves are flat to the eye.
vec3 wave_parallax(vec3 scene_pos, vec3 view_dir, float dist) {
    float fade = 1.0 - smoothstep(16.0, 32.0, dist);
    if (fade <= 0.0 || view_dir.y > -0.02) {
        return scene_pos;
    }
    const int steps = 8;
    const float range = 0.63; // the short waves' heights: 0..range blocks
    float layer = 0.5 * WATER_WAVES * fade;
    vec2 across = view_dir.xz / -view_dir.y * layer; // sideways over the whole layer
    float len = length(across);
    across *= min(len, 4.0) / max(len, 1e-4);
    vec3 p = wave_coord(scene_pos);
    float t0 = 0.0;
    float d0 = 1.0; // the ray's height over the waves, as a share of the layer
    for (int i = 1; i <= steps; i++) {
        float t = float(i) / float(steps);
        t = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t); // finer near the top, where the crests are
        vec3 q = p + vec3(across.x, 0.0, across.y) * t * wave_scale;
        float d = (1.0 - t) - clamp(wave_height(q, 0.0, 1.0) / range, 0.0, 1.0);
        if (d <= 0.0) {
            // Two secant steps between the last point above the waves and this one below.
            float t1 = t;
            float d1 = d;
            for (int j = 0; j < 2; j++) {
                float tm = t1 - d1 * (t1 - t0) / (d1 - d0);
                vec3 qm = p + vec3(across.x, 0.0, across.y) * tm * wave_scale;
                float dm = (1.0 - tm) - clamp(wave_height(qm, 0.0, 1.0) / range, 0.0, 1.0);
                t0 = t1;
                d0 = d1;
                t1 = tm;
                d1 = dm;
            }
            return scene_pos + vec3(across.x, 0.0, across.y) * t1;
        }
        t0 = t;
        d0 = d;
    }
    return scene_pos + vec3(across.x, 0.0, across.y);
}

// The normal of a water surface facing up at a point (scene space), seen from dist blocks away, with sky light sky
// (0..1): the waves', the ripples' where they are, and the rings of raindrops while it rains on it.
vec3 water_normal(vec3 scene_pos, float dist, float sky) {
    float detail = 1.0 - smoothstep(24.0, 96.0, dist);
    // Over about a pixel's width at that distance (at 1440p and a 70° view, a thousandth of it).
    vec2 slope = wave_slope(scene_pos, cg_swell, detail, dist * 0.001);
#ifdef LIT_WATER_RIPPLES
    slope += ripple_slope(scene_pos);
#endif
    slope += rain_ripple_slope(scene_pos, cg_rain_here * smoothstep(14.0 / 15.0, 14.8 / 15.0, sky)
            * (1.0 - smoothstep(12.0, 32.0, dist)));
    return normalize(vec3(-slope.x, 1.0, -slope.y));
}
#endif

#endif

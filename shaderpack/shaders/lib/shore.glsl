// The shore around the water near you, in the lit overworld with shadows: where foam gathers, how open the water is
// (how big its waves get), and how high the water stands against a wall.
//
// The shadow pass marks the top of every water block within 128 blocks of you across (shadow.vsh: from the corners
// of the water's top faces, so none is missed however low the sun is, and a wall or a roof over the water leaves its
// column unmarked). Two passes after it (shore.csh) blur, across x and then across z, how much of the ground around
// each column isn't open water: closely (a Gaussian about a block wide) for the foam, which so gathers in corners
// and round the far side of a pillar and thins out along a straight wall; and widely (about ten blocks) for how open
// the water is, so a pool or a pond gets small waves and open water the full swell. They share cg_water_marks_img's
// last rows (Iris allows 16 images in all): the tops, the blur across x beside them, then the field.

#if !defined LIB_SHORE
#define LIB_SHORE

#include "/lib/settings.glsl"

#ifdef LIT_SHORE

const int shore_size = 256;     // columns across, a block each, centred on the block you are in (as the sky grid)
const int shore_row = 2940;     // where its rows begin in cg_water_marks_img
const int shore_near_reach = 3; // blocks the close blur reaches each way
const float shore_near_sigma = 1.0;
const int shore_wide_reach = 24;
const float shore_wide_sigma = 10.0;

// Where a column's water top, its blur across x and its field are kept.
ivec2 shore_top_texel(ivec2 column) {
    return ivec2(column.x, shore_row + column.y);
}

ivec2 shore_across_texel(ivec2 column) {
    return ivec2(shore_size + column.x, shore_row + column.y);
}

ivec2 shore_field_texel(ivec2 column) {
    return ivec2(column.x, shore_row + shore_size + column.y);
}

// A point's column (blocks from the grid's corner, continuous), from where it is in scene space.
vec2 shore_pos(vec3 scene_pos, vec3 camera_fract) {
    return scene_pos.xz + camera_fract.xz + float(shore_size / 2);
}

// A height relative to the camera (blocks) in a mark's 16 bits (a 64th of a block, -256 to 768), 0 meaning none.
uint shore_height_mark(float y) {
    return uint(clamp((y + 256.0) * 64.0, 1.0, 65535.0));
}

float shore_height_from_mark(uint mark) {
    return float(mark) / 64.0 - 256.0;
}

// Two shares (0..1) in a word, and back.
uint shore_pack(vec2 v) {
    uvec2 q = uvec2(clamp(v, 0.0, 1.0) * 65535.0 + 0.5);
    return (q.x << 16) | q.y;
}

vec2 shore_unpack(uint word) {
    return vec2(float(word >> 16), float(word & 0xffffu)) / 65535.0;
}

#if !defined PROGRAM_SHADOW && !defined PROGRAM_SHORE
#include "/lib/motion.glsl"

#ifndef CG_WATER_MARKS_TEX
#define CG_WATER_MARKS_TEX
uniform usampler2D cg_water_marks_tex; // cg_water_marks_img, for programs without image access
#endif

// The shore around a point on the water (scene space): x how much of the ground within a block or two isn't open
// water (0 out on open water, about a half at a straight wall, more in a corner), y how much of it within about ten
// blocks is (1 on open water, a few tenths in a pool). Towards the grid's edge and past it, open water.
vec2 shore_around(vec3 scene_pos) {
    vec2 g = shore_pos(scene_pos, cameraPositionFract) - 0.5; // between the columns' centres
    ivec2 c = ivec2(floor(g));
    if (any(lessThan(c, ivec2(0))) || any(greaterThanEqual(c, ivec2(shore_size - 1)))) {
        return vec2(0.0, 1.0);
    }
    vec2 f = g - vec2(c);
    vec2 a = shore_unpack(texelFetch(cg_water_marks_tex, shore_field_texel(c), 0).r);
    vec2 b = shore_unpack(texelFetch(cg_water_marks_tex, shore_field_texel(c + ivec2(1, 0)), 0).r);
    vec2 d = shore_unpack(texelFetch(cg_water_marks_tex, shore_field_texel(c + ivec2(0, 1)), 0).r);
    vec2 e = shore_unpack(texelFetch(cg_water_marks_tex, shore_field_texel(c + ivec2(1, 1)), 0).r);
    vec2 v = mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
    vec2 edge = min(g, vec2(shore_size - 1) - g);
    float fade = smoothstep(4.0, 28.0, min(edge.x, edge.y));
    return vec2(v.x * fade, mix(1.0, v.y, fade));
}

// The top of the water (scene space y) in the column a point (scene space) is in; -1e4 where there's none.
float shore_water_top(vec3 scene_pos) {
    ivec2 c = ivec2(floor(shore_pos(scene_pos, cameraPositionFract)));
    if (any(lessThan(c, ivec2(0))) || any(greaterThanEqual(c, ivec2(shore_size)))) {
        return -1e4;
    }
    uint top = texelFetch(cg_water_marks_tex, shore_top_texel(c), 0).r;
    return top == 0u ? -1e4 : shore_height_from_mark(top);
}

// The water lapping at the shore: 0..1, rising and falling every five seconds or so, a little later from place to
// place along the shore (world xz, blocks from the camera's whole block).
float shore_lap(vec2 world) {
    float phase = sin(world.x * 0.37 + sin(world.y * 0.23) * 2.0) + sin(world.y * 0.41 - world.x * 0.11);
    return 0.5 + 0.5 * sin(frameTimeCounter * 1.25 + phase * 1.6);
}

// How wet a surface at a point (scene space, facing normal) is from the water against it: a band from the water's
// top up to where the waves lap (a little higher where it laps, and where the water is rough), on a wall over the
// water or on the ground at its edge. 0..1.
float shore_wet(vec3 scene_pos, vec3 normal, float swell) {
    if (normal.y < -0.5) {
        return 0.0;
    }
    float top = -1e4;
    if (normal.y > 0.5) {
        // The ground at the water's edge: the water in the columns beside it.
        top = max(max(shore_water_top(scene_pos + vec3(0.5, 0.0, 0.0)), shore_water_top(scene_pos - vec3(0.5, 0.0, 0.0))),
                  max(shore_water_top(scene_pos + vec3(0.0, 0.0, 0.5)), shore_water_top(scene_pos - vec3(0.0, 0.0, 0.5))));
    } else {
        // A wall: the water in the column it faces.
        top = shore_water_top(scene_pos + vec3(normal.x, 0.0, normal.z) * 0.5);
    }
    float above = scene_pos.y - top;
    if (above < -1.0 || above > 1.0) {
        return 0.0;
    }
    vec2 world = vec2(cameraPositionInt.xz % 4096) + cameraPositionFract.xz + scene_pos.xz;
    float reach = (0.1 + 0.15 * swell) * (0.6 + 0.8 * shore_lap(world));
    return 1.0 - smoothstep(0.4 * reach, reach + 0.05, above);
}

#endif
#endif
#endif

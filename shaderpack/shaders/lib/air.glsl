// Light in the air, in the lit overworld: the haze the sun lights into shafts through the leaves and past the edges
// of the clouds, the dust in rooms and caves a sunbeam shows up, and the glow the air takes on around lamps, in the
// lamps' own colours. It's the same air as the sky's (atmosphere.glsl): far terrain fades into it the way the
// horizon does, bluer and paler with distance and thicker at sunrise.
//
// A volume of cells fixed to the camera, as Unreal's, Frostbite's (Hillaire 2015) and Assassin's Creed IV's (Wronski
// 2014) volumetric fog is: air_tile by air_tile render pixels across, and air_slices deep, closer together near you
// (each a little deeper than the one before) out to as far as the shadow map reaches. Two passes before the world is
// drawn (air_volume.csh):
// - the cells: each one works out the light its air scatters towards the eye (the sun and the moon where the shadow
//   map and the clouds let them through, the sky's light where the sky reaches it, the lamps' light from the light
//   volume) and how much light it takes out, at a point somewhere inside it that moves from frame to frame, and
//   blends that with what the cell held last frame at the same place in the world (90% of it). So every cell comes to
//   hold the average over all of it, and the volume holds still with no noise on the screen for the upscaler to see;
// - the sum: down each column of cells from the eye, the light all the air up to the end of each cell scatters
//   towards the eye, and how much of what lies beyond gets through it (Frostbite's energy-conserving integration).
// Every surface then takes the sum at its own depth, between the cells around it (air_between). Past the volume, the
// air is the sky's own, without shafts or dust: the sky takes the sky view table's (atmosphere.glsl, air.csh), and
// far land and water the same air worked out along their own ray (lit.glsl's air_far_between), so they fade into the
// haze the way the horizon does, smoothly, with no cells showing.
//
// Both passes keep their cells in colortex10 and colortex11, as a page of slices side by side, air_columns to a row:
// the cells pass writes this frame's into colortex10 from last frame's in colortex11; the sum copies them into
// colortex11 for the next frame and writes the sums over colortex10 (rgb the light scattered towards the eye, in the
// frame's units, a how much gets through).

#if !defined LIB_AIR
#define LIB_AIR

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/vanilla.glsl"

#ifdef LIT_AIR

/*
const int colortex10Format = RGBA16F;
const int colortex11Format = RGBA16F;
const bool colortex10Clear = false;
const bool colortex11Clear = false;
*/

// The cells' size in render pixels and how many slices deep the volume is, by Light in the Air's quality. The page's
// size in shaders.properties follows from them: air_columns times the window over air_tile across, the rows times it
// up, and a little more. At 2560 by 1440: about 0.45 million cells (Low), 0.9 million (Default), 2 million (High) and
// 3.5 million (Ultra). Unreal's volumetric fog takes 16 by 16 pixel cells and 64 slices at High (half a million at
// 1080p, about a millisecond on a PS4) and 8 by 8 by 128 at Epic, eight times as many.
#if AIR_QUALITY == 1
const int air_tile = 20;
const int air_slices = 48;
#elif AIR_QUALITY == 2
const int air_tile = 16;
const int air_slices = 64;
#elif AIR_QUALITY == 3
const int air_tile = 12;
const int air_slices = 80;
#else
const int air_tile = 10;
const int air_slices = 96;
#endif
const int air_columns = 8;

// The slices are spaced as log(depth / air_near + 1): about this many blocks apart close to you, wider further out.
const float air_near = 3.0;

// How far the sky takes the volume's air from (blocks): as far as the shadow map reaches, so the shafts show against
// the sky too.
#ifdef SHADOWS
const float air_sky_start = shadowDistance;
#else
const float air_sky_start = 128.0;
#endif

// The cells across one slice: as many as the window holds (the page's layout), and as many as the frame uses (fewer
// while upscaling).
ivec2 air_block() {
    return ivec2(ceil(window_size() / float(air_tile)));
}

ivec2 air_cells() {
    return ivec2(ceil(render_size() / float(air_tile)));
}

// Where cell c of slice k is on the page.
ivec2 air_texel(ivec2 c, int k) {
    return ivec2(k % air_columns, k / air_columns) * air_block() + c;
}

// Where on the page the exposure the cells were worked out with is kept (past the slices' columns, row 0).
ivec2 air_exposure_texel() {
    return ivec2(air_columns * air_block().x, 0);
}

// How deep (view depth, blocks) the volume reaches: in the overworld as far as the sky's table begins, past which the
// air is worked out along each pixel's ray (lit.glsl's air_far_between), so all its slices go to the air near you; in
// the Nether and the End, whose air has no table, a little past the end of the terrain, Distant Horizons' too.
float air_far() {
#ifdef NO_SKY_LIGHT
    float d = far;
#ifdef DISTANT_HORIZONS
    d = max(d, float(dhRenderDistance));
#endif
    return max(1.15 * d, 2.0 * air_sky_start);
#else
    return air_sky_start;
#endif
}

float air_log_scale() {
    return log2(air_far() / air_near + 1.0);
}

// The slice a view depth falls in (0 at the eye, air_slices at the far end), and back.
float air_slice_of(float z) {
    return float(air_slices) * log2(max(z, 0.0) / air_near + 1.0) / air_log_scale();
}

float air_depth_of(float s) {
    return air_near * (exp2(s / float(air_slices) * air_log_scale()) - 1.0);
}

#ifndef AIR_WRITE
uniform sampler2D colortex10;

// One slice's sum at a place across it (cells, between their centres), from colortex10.
vec4 air_sum_at(vec2 c, int k) {
    vec2 at = vec2(air_texel(ivec2(0), k)) + clamp(c, vec2(0.0), vec2(air_cells() - 1)) + 0.5;
    return texture(colortex10, at / vec2(textureSize(colortex10, 0)));
}

// The air between the eye and what lies at view depth z (blocks), seen at point (unjittered render pixels): rgb the
// light it scatters towards the eye (the frame's units), a how much of what lies behind gets through.
vec4 air_between(vec2 point, float z) {
    vec2 c = point / float(air_tile) - 0.5;
    // Each slice holds the sum up to its far end; nothing yet at the eye.
    float t = clamp(air_slice_of(z), 0.0, float(air_slices)) - 1.0;
    int k = int(floor(t));
    float f = t - float(k);
    vec4 nearer = k < 0 ? vec4(0.0, 0.0, 0.0, 1.0) : air_sum_at(c, k);
    vec4 further = air_sum_at(c, min(k + 1, air_slices - 1));
    return mix(nearer, further, f);
}
#endif

#endif
#endif

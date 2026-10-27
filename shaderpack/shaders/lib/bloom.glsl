// The bloom's pyramid (see bloom.csh): where each level lies in colortex8, and how the final pass reads it.

#if !defined LIB_BLOOM
#define LIB_BLOOM

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/weather.glsl"

/*
const int colortex8Format = R11F_G11F_B10F;
const bool colortex8Clear = false;
*/

const int bloom_levels = 7;
// How much the air around scatters the light, as rain and snow do (0 clear, 1 as much as heavy rain): the Nether's
// thick air much of that, so the lava and the lamps glow wide through it; the End's haze a little.
#if defined WORLD_NETHER && defined LIGHTING
#define bloom_air 0.6
#elif defined WORLD_END && defined LIGHTING
#define bloom_air 0.25
#else
#define bloom_air 0.0
#endif
// How much each level takes of the blur of the level below it (the rest its own): more spreads the glow wider. Rain
// and snow scatter the light more, so lamps glow wider and more through them.
#define bloom_spread (0.7 + 0.12 * max(weather_around(), bloom_air))
// How much of the frame the glow takes (and leaves it): a little, so only what is much brighter shows.
#define bloom_amount (0.08 * BLOOM * (1.0 + 1.5 * max(weather_around(), bloom_air)))

// A level's size (texels): level 1 half the window's, each one after half the one before.
ivec2 bloom_size(int level) {
    ivec2 s = ivec2(window_size()) / 2;
    for (int i = 1; i < level; i++) {
        s = max(s / 2, ivec2(1));
    }
    return s;
}

// A level's place in colortex8: xy its corner, zw its size. Level 1 at the bottom, level 2 above it, the smaller ones
// in a row to its right, a texel apart.
ivec4 bloom_rect(int level) {
    ivec2 s1 = bloom_size(1);
    if (level == 1) {
        return ivec4(0, 0, s1);
    }
    ivec2 s2 = bloom_size(2);
    if (level == 2) {
        return ivec4(0, s1.y + 1, s2);
    }
    int x = s2.x + 1;
    for (int i = 3; i < level; i++) {
        x += bloom_size(i).x + 1;
    }
    return ivec4(x, s1.y + 1, bloom_size(level));
}

#if !defined PROGRAM_BLOOM
uniform sampler2D colortex8;

// The glow at a window pixel: level 1 of the pyramid, which holds every level's blur by now.
vec3 bloom_at(vec2 window_pixel) {
    ivec4 rect = bloom_rect(1);
    vec2 at = clamp(window_pixel * 0.5, vec2(0.5), vec2(rect.zw) - 0.5);
    return textureLod(colortex8, at / vec2(textureSize(colortex8, 0)), 0.0).rgb;
}
#endif

#endif

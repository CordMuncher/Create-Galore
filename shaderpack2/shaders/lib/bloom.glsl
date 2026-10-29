// The bloom's image (see program/bloom.csh): levels 1 to 6 at a half, a quarter ... a 64th of the window, stacked
// with level 1 at the bottom; the way down in the left half, the way back up in the right.

#if !defined LIB_BLOOM
#define LIB_BLOOM

#include "/lib/post.glsl"

uniform sampler2D cg_bloom_tex;

const int bloom_levels = 6;

ivec2 bloom_size(int level) {
    return level == 0 ? ivec2(window_size()) : ivec2(ceil(window_size() / exp2(float(level))));
}

int bloom_offset(int level) {
    int y = 0;
    for (int k = 1; k < level; k++) {
        y += bloom_size(k).y;
    }
    return y;
}

ivec2 bloom_texel(int level, ivec2 texel) {
    return texel + ivec2(0, bloom_offset(level));
}

// A level at a point of its rectangle (0..1), blended between texels and kept inside the rectangle; up: the way
// back up's.
vec3 bloom_level_at(bool up, int level, vec2 uv) {
    vec2 size = vec2(bloom_size(level));
    vec2 atlas = vec2(textureSize(cg_bloom_tex, 0));
    vec2 p = clamp(uv * size, vec2(0.5), size - 0.5) + vec2(up ? floor(atlas.x * 0.5) : 0.0, float(bloom_offset(level)));
    return texture(cg_bloom_tex, p / atlas).rgb;
}

vec3 bloom_down_at(int level, vec2 uv) {
    return bloom_level_at(false, level, uv);
}

vec3 bloom_up_at(int level, vec2 uv) {
    return bloom_level_at(true, level, uv);
}

#endif

// The overworld's bloom, after DLSS (or FSR) at the window's size: light scatters a little in the eye and the lens,
// so what is much brighter than its surroundings (a torch's flame at night, the sun on water, lava) spreads a glow
// over them. The frame goes down a pyramid of ever smaller copies of itself, each blurred from the one before
// (BLOOM_DOWN 1 to 7), then back up (BLOOM_UP 6 to 1), each level taking in the blur of the one below; the final pass
// adds a little of the top level to the frame (see bloom_at). Nothing is cut off at a threshold: everything spreads,
// and only what is bright stands out.
//
// The pyramid lives in colortex8, half the window wide and as tall: level 1 (half the window's size) in its lower
// half, the smaller ones side by side above it (see bloom_rect).

#define PROGRAM_BLOOM
#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/bloom.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
// Enough groups for level 1 (half the window each way); smaller levels leave most of them idle.
const vec2 workGroupsRender = vec2(0.5, 0.5);

uniform sampler2D colortex0;
layout(r11f_g11f_b10f) uniform image2D colorimg8;

vec3 level_texel(int level, ivec2 p) {
    ivec4 rect = bloom_rect(level);
    return imageLoad(colorimg8, rect.xy + clamp(p, ivec2(0), rect.zw - 1)).rgb;
}

void main() {
    if (BLOOM <= 0.0) {
        return; // Bloom off: final.fsh doesn't read the pyramid
    }
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
#if defined BLOOM_DOWN
    ivec4 rect = bloom_rect(BLOOM_DOWN);
    if (any(greaterThanEqual(p, rect.zw))) {
        return;
    }
    vec3 sum = vec3(0.0);
#if BLOOM_DOWN == 1
    // From the frame: the 4 by 4 of its pixels around this texel's 2 by 2, in groups of four, the groups weighted by
    // their brightness (Karis) so a single bright pixel doesn't flicker into a blob.
    vec2 area = (cg_dlss_active > 0.5 ? window_size() : render_size()) / window_size();
    vec2 source = window_size() * area;
    float weight_sum = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            // Between four of the frame's pixels: their average, bilinear (in the render-size corner where the
            // frame is still that size).
            vec2 centre = vec2(p * 2 + ivec2(x, y) * 2) * area;
            vec3 c = max(textureLod(colortex0, clamp(centre, vec2(1.0), source - 1.0) / window_size(), 0.0).rgb, 0.0);
            float w = 1.0 / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722)));
            sum += c * w;
            weight_sum += w;
        }
    }
    sum /= weight_sum;
#else
    // From the level above: 4 by 4 of its texels, weighted 1 3 3 1 each way.
    const float w4[4] = float[4](1.0, 3.0, 3.0, 1.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            sum += level_texel(BLOOM_DOWN - 1, p * 2 + ivec2(x - 1, y - 1)) * (w4[x] * w4[y] / 64.0);
        }
    }
#endif
    imageStore(colorimg8, rect.xy + p, vec4(sum, 1.0));
#elif defined BLOOM_UP
    // This level (BLOOM_UP) takes in the level below, brought up with a 3 by 3 tent around where this texel falls in
    // it, as much as each level adds to the glow's spread.
    ivec4 rect = bloom_rect(BLOOM_UP);
    if (any(greaterThanEqual(p, rect.zw))) {
        return;
    }
    vec2 at = (vec2(p) + 0.5) * 0.5 - 0.5; // in the level below's texels
    ivec2 base = ivec2(floor(at));
    vec2 f = at - vec2(base);
    vec3 below = mix(mix(level_texel(BLOOM_UP + 1, base), level_texel(BLOOM_UP + 1, base + ivec2(1, 0)), f.x),
                     mix(level_texel(BLOOM_UP + 1, base + ivec2(0, 1)), level_texel(BLOOM_UP + 1, base + ivec2(1, 1)), f.x), f.y);
    vec3 here = level_texel(BLOOM_UP, p);
    imageStore(colorimg8, rect.xy + p, vec4(mix(here, below, bloom_spread), 1.0));
#endif
}

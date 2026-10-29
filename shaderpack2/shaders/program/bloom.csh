// The bloom: six ever smaller blurred copies of the frame (BLOOM_DOWN 1..6, each from the one before), then back
// up (BLOOM_UP 5..1), each adding the smaller one, softly spread, to its own. All in one image (cg_bloom_img, the way
// down in its left half, the way up in its right), level k stacked above level k - 1 (see lib/bloom.glsl).
//
// The first copy also takes in the bloom fog: far things count more in it the darker and murkier it is (none by
// day out in the open; dusk, night, caves, under water, rain), so far lights and far surfaces turn into a soft glow
// while the frame itself stays as it was. It starts slowly (a twentieth at 32 blocks, a quarter at 64, three
// quarters at 128), so what is near stays clear. Bright pixels are weighed down in the first copy, so a single
// sparkling pixel doesn't flicker into a blob.

#include "/lib/settings.glsl"
#include "/lib/bloom.glsl"
#include "/lib/globals.glsl"

layout(local_size_x = 8, local_size_y = 8) in;

#if defined BLOOM_DOWN
#define BLOOM_LEVEL BLOOM_DOWN
#else
#define BLOOM_LEVEL BLOOM_UP
#endif
const int level = BLOOM_LEVEL;
// A thread per texel of the level (Iris divides by the work group's size itself).
#if BLOOM_LEVEL == 1
const vec2 workGroupsRender = vec2(0.5, 0.5);
#elif BLOOM_LEVEL == 2
const vec2 workGroupsRender = vec2(0.25, 0.25);
#elif BLOOM_LEVEL == 3
const vec2 workGroupsRender = vec2(0.125, 0.125);
#elif BLOOM_LEVEL == 4
const vec2 workGroupsRender = vec2(0.0625, 0.0625);
#elif BLOOM_LEVEL == 5
const vec2 workGroupsRender = vec2(0.03125, 0.03125);
#else
const vec2 workGroupsRender = vec2(0.015625, 0.015625);
#endif

layout(rgba16f) writeonly uniform image2D cg_bloom_img;

#if defined BLOOM_DOWN
#if BLOOM_DOWN == 1
uniform sampler2D depthtex0;
uniform float near;
uniform float far;
uniform float rainStrength;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
#endif
#endif

#if defined BLOOM_DOWN && BLOOM_DOWN == 1
// How far off the point at a window position (0..1) is, from the depth buffer (render size).
float distance_at(vec2 uv) {
    ivec2 texel = ivec2(uv * render_size());
    float d = texelFetch(depthtex0, min(texel, ivec2(render_size()) - 1), 0).r;
    if (d >= 1.0) {
        return far;
    }
    return 2.0 * near * far / (far + near - (2.0 * d - 1.0) * (far - near));
}

// How strong the bloom fog is: 0 by day in the open, 1.5 as the sun sets rising to 3 at night, more in rain and
// caves, most under water.
float murk() {
    float open = float(eyeBrightnessSmooth.y) / 240.0;
    float night = 1.5 * (1.0 - smoothstep(0.0, 0.04, g_sun_dir.w)) + 1.5 * (1.0 - smoothstep(-0.37, 0.0, g_sun_dir.w));
#if defined NO_SKY_LIGHT
    night = 3.0;
    open = 1.0;
#endif
    if (isEyeInWater == 1) {
        return 8.0;
    }
    return (night + 4.0 * rainStrength * rainStrength) * open + 6.0 * (1.0 - open);
}

// How much of the bloom fog a point that far off takes (0..1).
float fog_share(float d) {
    return sqr(sqr(1.0 - exp(-d / 50.0)));
}
#endif

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = bloom_size(level);
    if (any(greaterThanEqual(texel, size))) {
        return;
    }
    vec2 uv = (vec2(texel) + 0.5) / vec2(size); // in this level's rectangle

#if defined BLOOM_DOWN
    // Four blended looks one texel of the bigger level apart on the diagonals: a 4 x 4 box.
    vec2 step_uv = 1.0 / vec2(bloom_size(level - 1));
    vec3 sum = vec3(0.0);
    float weight = 0.0;
#if BLOOM_DOWN == 1
    float fogginess = murk();
#endif
    for (int i = 0; i < 4; i++) {
        vec2 o = vec2((i & 1) == 0 ? -1.0 : 1.0, (i & 2) == 0 ? -1.0 : 1.0);
        vec2 at = uv + o * step_uv;
#if BLOOM_DOWN == 1
        vec3 c = frame_at(at);
        float boost = 1.0 + fogginess * fog_share(distance_at(at));
        c *= boost;
        float w = 1.0 / (1.0 + luminance(c) * 0.25);
#else
        vec3 c = bloom_down_at(level - 1, at);
        float w = 1.0;
#endif
        sum += c * w;
        weight += w;
    }
    imageStore(cg_bloom_img, bloom_texel(level, texel), vec4(sum / weight, 1.0));
#else
    // This level plus the smaller one, spread over a tent of 3 x 3 of its texels.
    vec2 step_uv = 1.0 / vec2(bloom_size(level + 1));
    vec3 smaller = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
            vec2 at = uv + vec2(x, y) * step_uv;
            smaller += w * (level + 1 == bloom_levels ? bloom_down_at(level + 1, at) : bloom_up_at(level + 1, at));
        }
    }
    vec3 own = bloom_down_at(level, uv);
    imageStore(cg_bloom_img, bloom_texel(level, texel) + ivec2(imageSize(cg_bloom_img).x / 2, 0), vec4(own + smaller, 1.0));
#endif
}

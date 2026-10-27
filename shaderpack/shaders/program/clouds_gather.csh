// The overworld's deferred pass, second part: the cumulus for every render pixel (see clouds_march.csh), built up
// from this frame's rays and the pixel's history, found where its clouds were last frame. Ported from Alpha
// Piscium's clouds/ss/Accum: Lanczos filters over both, sharper as the history grows; the history pulled into the
// range this frame's rays show around the pixel (Marty McFly's ellipsoid clipping), so moving clouds don't trail;
// up to 16 frames blended. The history alternates between two halves of its images (side by side, each the window's
// size), one written each frame while the other is read; the lighting pass reads the one written (see deferred2.fsh).
//
// OWN DENOISER: off under Ray Reconstruction, whose guide rules out rays on only some pixels (see the README).

#include "/lib/clouds.glsl"
#include "/lib/motion.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(1.0, 1.0);

uniform sampler2D cg_cloud_low_tex;
uniform sampler2D cg_cloud_dist_tex;
uniform sampler2D cg_cloud_hist_tex;
uniform sampler2D cg_cloud_len_tex;
layout(rgba16f) writeonly uniform image2D cg_cloud_hist_img;
layout(r16f) writeonly uniform image2D cg_cloud_len_img;
uniform mat4 gbufferProjectionInverse;

const float max_frames = 16.0;
const float confidence_curve = 4.0;  // how fast the filter sharpens as the history grows
const float variance_clipping = 0.25; // how hard the history is pulled in (more: less trailing, more flicker)

vec4 sinc(vec4 x) {
    return mix(vec4(1.0), sin(PI * x) / (PI * x), greaterThan(abs(x), vec4(1e-5)));
}

// Lanczos 2 weights for the four taps around t (0..1 past the second); w above 1 narrows the kernel, below widens it.
vec4 lanczos2(float t, float w) {
    vec4 x = vec4(t) + (vec4(1.0, 0.0, -1.0, -2.0) + 0.5) * w - 0.5;
    return sinc(x) * sinc(0.5 * x);
}

vec4 gaussian(float t) {
    vec4 x = vec4(t) + vec4(1.0, 0.0, -1.0, -2.0);
    return exp(-0.5 * x * x);
}

vec3 rgb_to_ycocg(vec3 c) {
    return mat3(0.25, 0.5, -0.25, 0.5, 0.0, 0.5, 0.25, -0.5, -0.25) * c;
}

vec3 ycocg_to_rgb(vec3 c) {
    return mat3(1.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0, 1.0, -1.0) * c;
}

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    vec2 size = render_size();
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        return;
    }
    // This frame's half of the history, and last frame's.
    int half_width = imageSize(cg_cloud_hist_img).x / 2;
    ivec2 write_half = ivec2((frameCounter & 1) == 0 ? 0 : half_width, 0);
    ivec2 read_half = ivec2(half_width, 0) - write_half;
    ivec2 low_size = ivec2(ceil(size / float(CLOUD_SCALE)));

    // Where this pixel's centre falls among this frame's rays (ray q went through (q + offset) * CLOUD_SCALE).
    vec2 center = vec2(texel) + 0.5;
    vec2 low = center / float(CLOUD_SCALE) - cloud_offset();
    ivec2 low_origin = ivec2(floor(low));
    vec2 low_fract = low - vec2(low_origin);

    // Last frame: where this pixel's clouds were (at the nearest ray's distance; the sky's, as far as can be, turn
    // with the camera alone), and their history there, unless the camera jumped.
    vec4 view = gbufferProjectionInverse * vec4(center / size * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
    float dist = texelFetch(cg_cloud_dist_tex, clamp(ivec2(floor(low + 0.5)), ivec2(0), low_size - 1), 0).r;
    vec4 clip_prev = dist > 0.0
            ? previous_clip(dir * dist * 1000.0, gbufferPreviousProjection)
            : gbufferPreviousProjection * vec4(mat3(gbufferPreviousModelView) * dir, 0.0);
    vec4 prev = vec4(0.0);
    float prev_frames = 0.0;
    if (clip_prev.w > 0.0 && all(lessThan(abs(clip_prev.xy), clip_prev.ww)) && length(camera_motion()) < 32.0) {
        vec2 prev_pixel = (clip_prev.xy / clip_prev.w * 0.5 + 0.5) * size - 0.5;
        ivec2 origin = ivec2(floor(prev_pixel));
        vec2 f = prev_pixel - vec2(origin);
        vec4 wx = lanczos2(f.x, 1.0);
        vec4 wy = lanczos2(f.y, 1.0);
        float weight_sum = 0.0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                ivec2 at = clamp(origin + ivec2(x - 1, y - 1), ivec2(0), ivec2(size) - 1);
                float w = wx[x] * wy[y];
                vec4 data = texelFetch(cg_cloud_hist_tex, read_half + at, 0);
                float frames = texelFetch(cg_cloud_len_tex, read_half + at, 0).r;
                prev += data * w;
                prev_frames += frames * w;
                weight_sum += w;
            }
        }
        prev /= weight_sum;
        prev_frames = clamp(prev_frames / weight_sum, 0.0, 64.0);
    }

    // This frame: its rays around the pixel, the filter sharper the more history there is, and their spread.
    float confidence = 1.0 - pow(1.0 - linear_step(0.0, max_frames, prev_frames), confidence_curve);
    vec4 wx = lanczos2(low_fract.x, confidence * 1.5);
    vec4 wy = lanczos2(low_fract.y, confidence * 1.5);
    vec4 gx = gaussian(low_fract.x);
    vec4 gy = gaussian(low_fract.y);
    vec4 current = vec4(0.0);
    float weight_sum = 0.0;
    vec4 lo = vec4(1e20);
    vec4 hi = vec4(-1e20);
    vec4 moment1 = vec4(0.0);
    vec4 moment2 = vec4(0.0);
    float moment_sum = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            ivec2 at = clamp(low_origin + ivec2(x - 1, y - 1), ivec2(0), low_size - 1);
            vec4 data = texelFetch(cg_cloud_low_tex, at, 0);
            float w = wx[x] * wy[y];
            current += data * w;
            weight_sum += w;
            lo = min(lo, data);
            hi = max(hi, data);
            vec4 ycocg = vec4(rgb_to_ycocg(data.rgb), data.a);
            float g = gx[x] * gy[y];
            moment1 += ycocg * g;
            moment2 += ycocg * ycocg * g;
            moment_sum += g;
        }
    }
    // (No ringing past what the rays show.)
    current = abs(weight_sum) > 0.001 ? clamp(current / weight_sum, lo, hi) : vec4(0.0, 0.0, 0.0, 1.0);
    moment1 /= moment_sum;
    moment2 /= moment_sum;

    // The history pulled towards this frame's spread around the pixel, in YCoCg.
    vec4 stddev = sqrt(max(moment2 - moment1 * moment1, 1e-10));
    float clipping = variance_clipping * cloud_noise(texel, 6u);
    vec3 scatter_prev = rgb_to_ycocg(prev.rgb);
    vec3 scatter_delta = scatter_prev - moment1.rgb;
    scatter_delta /= max(1.0, length(scatter_delta / stddev.rgb));
    prev.rgb = ycocg_to_rgb(mix(scatter_prev, moment1.rgb + scatter_delta, clipping));
    float trans_delta = prev.a - moment1.a;
    trans_delta /= max(1.0, abs(trans_delta / stddev.a));
    prev.a = mix(prev.a, moment1.a + trans_delta, clipping);

    float frames = min(prev_frames + 1.0, max_frames);
    vec4 result = mix(prev, current, 1.0 / frames);
    result = vec4(clamp(result.rgb, 0.0, 65000.0), clamp(result.a, 0.0, 1.0));
    imageStore(cg_cloud_hist_img, write_half + texel, result);
    imageStore(cg_cloud_len_img, write_half + texel, vec4(frames));
}

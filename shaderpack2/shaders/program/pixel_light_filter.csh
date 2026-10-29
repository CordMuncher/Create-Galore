// The traced per-pixel light (program/pixel_light.csh), brought back up to every pixel: last frame's result where
// this pixel's surface was (by its motion vector, so mobs, boats and Create's contraptions keep theirs), thrown away
// if the surface there was another one; then eight of the nearby samples, each weighed by how close it is and how
// alike its surface is (facing, depth). The old result is held within the new samples' range, so stale light can't
// linger, and counts as one sample against them: a short memory of a few frames, quick to follow a torch placed or
// taken away. Results in cg_plh_img: the diffuse light (a 1 where there is one), the highlights, and each pixel's
// facing and depth, one above the other, each in two halves that take turns (this frame's and last frame's).

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/motion.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const vec2 workGroupsRender = vec2(1.0, 1.0);

uniform sampler2D colortex7;

layout(rgba16f) readonly uniform image2D cg_pl_img; // see pixel_light.csh
layout(rgba16f) uniform image2D cg_plh_img;

// cg_plh_img holds three images one above the other: the diffuse light, the highlights, the facing and depth.
ivec2 spec_rows() {
    return ivec2(0, imageSize(cg_plh_img).y / 3);
}

ivec2 geometry_rows() {
    return 2 * spec_rows();
}

const int scale = PIXEL_LIGHT_SCALE;

float linear_depth(ivec2 texel, float depth) {
    return -pixel_to_view(texel, depth).z;
}

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    if (outside_render_area(vec2(texel) + 0.5)) {
        return;
    }
    int now_half = frameCounter & 1;
    ivec2 now = texel + ivec2(now_half * int(viewWidth), 0);
    float depth = texelFetch(depthtex0, texel, 0).r;
    if (depth >= 1.0) {
        imageStore(cg_plh_img, now, vec4(0.0));
        imageStore(cg_plh_img, now + spec_rows(), vec4(0.0));
        imageStore(cg_plh_img, now + geometry_rows(), vec4(0.0));
        return;
    }
    vec3 normal = decode_normal(texelFetch(colortex2, texel, 0).zw);
    float z = linear_depth(texel, depth);

    // New samples around this pixel.
    vec2 centre = (vec2(texel) + 0.5) / float(scale);
    vec3 sum = vec3(0.0);
    vec3 spec_sum = vec3(0.0);
    float weight = 0.0;
    vec3 lo = vec3(1e9);
    vec3 hi = vec3(-1e9);
    vec3 spec_lo = vec3(1e9);
    vec3 spec_hi = vec3(-1e9);
    ivec2 low_size = ivec2(ceil(render_size() / float(scale)));
    for (int i = 0; i < 8; i++) {
        // Gaussian spread around the pixel (Box-Muller), new each frame.
        vec3 u = hash33(uvec3(uvec2(texel), uint(frameCounter) * 8u + uint(i)));
        float r = sqrt(-2.0 * log(max(u.x, 1e-4))) * 0.75;
        vec2 offset = r * vec2(cos(TAU * u.y), sin(TAU * u.y));
        ivec2 q = ivec2(floor(centre + offset));
        if (any(lessThan(q, ivec2(0))) || any(greaterThanEqual(q, low_size))) {
            continue;
        }
        vec4 sample_light = imageLoad(cg_pl_img, q);
        if (sample_light.a < 0.5) {
            continue;
        }
        int turn = int(sample_light.a - 0.5);
        ivec2 at = q * scale + ivec2(turn % scale, turn / scale);
        float sample_depth = texelFetch(depthtex0, at, 0).r;
        vec3 sample_normal = decode_normal(texelFetch(colortex2, at, 0).zw);
        float sz = linear_depth(at, sample_depth);
        float d_px = length(vec2(at - texel));
        float w = exp(-d_px * d_px / (2.0 * sqr(1.2 * float(scale))))
                * pow(max(dot(normal, sample_normal), 0.0), 16.0)
                * exp(-abs(sz - z) / (0.03 * z + 0.05));
        if (w < 1e-3) {
            continue;
        }
        vec3 sample_spec = imageLoad(cg_pl_img, q + ivec2(imageSize(cg_pl_img).x / 2, 0)).rgb;
        sum += sample_light.rgb * w;
        spec_sum += sample_spec * w;
        weight += w;
        lo = min(lo, sample_light.rgb);
        hi = max(hi, sample_light.rgb);
        spec_lo = min(spec_lo, sample_spec);
        spec_hi = max(spec_hi, sample_spec);
    }

    // Last frame's, where this surface was.
    vec4 motion = texelFetch(colortex7, texel, 0);
    vec2 prev = vec2(texel) + 0.5 + (motion.a > 0.5 ? motion.rg : vec2(0.0));
    ivec2 prev_texel = ivec2(floor(prev));
    vec4 history = vec4(0.0);
    vec3 history_spec = vec3(0.0);
    if (!outside_render_area(prev) && all(greaterThanEqual(prev_texel, ivec2(0)))) {
        ivec2 then = prev_texel + ivec2((1 - now_half) * int(viewWidth), 0);
        vec4 geometry = imageLoad(cg_plh_img, then + geometry_rows());
        vec3 prev_normal = geometry.a > 0.5 ? decode_normal(geometry.xy) : vec3(0.0);
        if (dot(prev_normal, normal) > 0.8 && abs(geometry.z - z) < 0.06 * z + 0.1) {
            history = imageLoad(cg_plh_img, then);
            history_spec = imageLoad(cg_plh_img, then + spec_rows()).rgb;
        }
    }

    vec4 result;
    vec3 result_spec;
    if (weight > 0.0) {
        if (history.a > 0.5) {
            vec3 h = clamp(history.rgb, lo, hi);
            vec3 hs = clamp(history_spec, spec_lo, spec_hi);
            result = vec4((h + sum) / (1.0 + weight), 1.0);
            result_spec = (hs + spec_sum) / (1.0 + weight);
        } else {
            result = vec4(sum / weight, 1.0);
            result_spec = spec_sum / weight;
        }
    } else {
        result = history;
        result_spec = history_spec;
    }
    imageStore(cg_plh_img, now, result);
    imageStore(cg_plh_img, now + spec_rows(), vec4(result_spec, 1.0));
    imageStore(cg_plh_img, now + geometry_rows(), vec4(encode_normal(normal), z, 1.0));
}

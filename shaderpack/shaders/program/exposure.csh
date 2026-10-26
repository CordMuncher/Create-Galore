// The overworld's exposure, metered after DLSS (or FSR) for the next frame: the frame's average brightness (a
// log average over a grid, the middle counting most), brought towards a key that is bright by day and lets night
// and caves stay darker, as eyes do. It brightens a little slower than it darkens (see globals.glsl).

#define GLOBALS_WRITE
#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform sampler2D colortex0;
uniform float frameTime;

shared float shared_sum[256];
shared float shared_weight[256];

const float ev_min = -7.0;
const float ev_max = 12.0;

void main() {
    // The frame as it is now: DLSS's result at the window's size, else the render-size corner.
    vec2 area = (cg_dlss_active > 0.5 ? window_size() : render_size()) / window_size();
    vec4 previous = g_exposure_data;
    float current = exposure_ready(previous) ? previous.x : 1.0;

    // 4 by 4 points each, a 64 by 64 grid over the frame.
    uint index = gl_LocalInvocationIndex;
    float sum = 0.0;
    float weight = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec2 uv = (vec2(gl_LocalInvocationID.xy * 4u + uvec2(x, y)) + 0.5) / 64.0;
            vec3 c = textureLod(colortex0, uv * area, 0.0).rgb;
            float luminance = dot(c, vec3(0.2126, 0.7152, 0.0722)) / current; // kcd/m²
            if (!(luminance >= 0.0) || isinf(luminance)) {
                continue;
            }
            vec2 d = uv - 0.5;
            float w = exp(-4.0 * dot(d, d));
            sum += log2(max(luminance, 1e-8)) * w;
            weight += w;
        }
    }
    shared_sum[index] = sum;
    shared_weight[index] = weight;
    barrier();
    for (uint stride = 128u; stride > 0u; stride >>= 1u) {
        if (index < stride) {
            shared_sum[index] += shared_sum[index + stride];
            shared_weight[index] += shared_weight[index + stride];
        }
        barrier();
    }
    if (index != 0u) {
        return;
    }

    float metered = shared_sum[0] / max(shared_weight[0], 1e-6);
    // The frame's average lands here (log2 of the frame's units): by day about where the tone curve puts a lit grey
    // a little under the middle of the screen's range, in the dark lower, so a night looks like night.
    float key = mix(-0.25, 0.75, smoothstep(-10.0, -2.0, metered));
    float target = clamp(key - metered, ev_min, ev_max);
    float ev = exposure_ready(previous) ? previous.y : target;
    // About a second to see in a dark room after the sun, half that back out (eyes take longer, games less).
    float rate = target > ev ? 2.5 : 4.5;
    ev += (target - ev) * (1.0 - exp(-max(frameTime, 0.0) * rate));
    set_global(G_EXPOSURE, vec4(exp2(ev), ev, metered, 0.0));
}

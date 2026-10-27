// The exposure, metered after DLSS (or FSR) for the next frame. It meters the light falling on what the
// frame shows rather than the light it gives back, as Ghost of Tsushima does (Patry 2021): a surface counts as an 18%
// grey would look in its place (its brightness over its colour's), so a dark floor doesn't brighten the frame and
// snow doesn't darken it; the sky, water, what lights itself and what the G-buffer has no colour for count as they
// are. Over 64 by 64 points (the middle counting most) the darkest and the brightest tenth are left out, as Unreal's
// histogram metering does, and the rest averaged (log2). That is brought towards a key that is bright by day and lets
// night and caves stay darker, as eyes do. It brightens a little slower than it darkens (see globals.glsl).

#define GLOBALS_WRITE
#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"
#include "/lib/gbuffer.glsl"
#include "/lib/color.glsl"
#include "/lib/light_levels.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex4;
uniform float frameTime;

const float ev_min = -7.0;
#ifdef WORLD_NETHER
// The Nether is lit only by its lava, its lamps and its glowing air, far dimmer than a day but no night to the eye:
// it adapts further and keeps it a little brighter (see the key below).
const float ev_max = 14.0;
const float key_dark = 0.25;
#else
const float ev_max = 12.0;
const float key_dark = -0.25;
#endif

// The histogram: log2 of kcd/m² from starlight to the sun's disc, 256 bins of about a fifth of a stop.
const int bins = 256;
const float bin_low = -26.0;
const float bin_stops = 48.0;
// The share of the weight left out at either end.
const float metering_cut = 0.1;

shared uint shared_bins[bins];
shared float shared_sum[256];
shared float shared_weight[256];
shared vec2 shared_range;

// How bright (log2 kcd/m²) a point of the frame meters, from its colour now (frame units, over the exposure) and
// what the G-buffer holds there.
float metered_at(vec3 c, float current, ivec2 surface_texel) {
    float luminance = dot(c, vec3(0.2126, 0.7152, 0.0722)) / current; // kcd/m²
    vec4 surface = texelFetch(colortex2, surface_texel, 0);
    int material = decode_material(surface);
    bool water = texelFetch(colortex4, surface_texel, 0).a > 0.5;
    if (surface.a > 0.5 && (material == MATERIAL_LIT || material == MATERIAL_PLANT) && !water) {
        float albedo = dot(srgb_to_linear(texelFetch(colortex1, surface_texel, 0).rgb), vec3(0.2126, 0.7152, 0.0722));
        if (albedo > 0.01) {
            luminance *= 0.18 / albedo;
        }
    }
#ifdef WORLD_NETHER
    // Nothing in the Nether lights a grey card brighter than its lava and its lamps do close by: what glows brighter
    // (a lava sea filling the view) meters as that, so the eye doesn't shut down to the lava and leave the rocks
    // around it black.
    luminance = min(luminance, 0.18 * light_block_lux / 3.14159265);
#endif
    return log2(max(luminance, 1e-12));
}

void main() {
    // The frame as it is now: DLSS's result at the window's size, else the render-size corner; the G-buffer at the
    // render size, read at the nearest texel (as NVIDIA's guide has post effects read it).
    vec2 area = (cg_dlss_active > 0.5 ? window_size() : render_size()) / window_size();
    ivec2 last = ivec2(render_size()) - 1;
    vec4 previous = g_exposure_data;
    float current = exposure_ready(previous) ? previous.x : 1.0;

    uint index = gl_LocalInvocationIndex;
    shared_bins[index] = 0u;
    barrier();

    // 4 by 4 points each, a 64 by 64 grid over the frame.
    float values[16];
    float weights[16];
    for (int i = 0; i < 16; i++) {
        vec2 uv = (vec2(gl_LocalInvocationID.xy * 4u + uvec2(i & 3, i >> 2)) + 0.5) / 64.0;
        vec3 c = textureLod(colortex0, uv * area, 0.0).rgb;
        ivec2 surface_texel = min(ivec2(uv * render_size()), last);
        float v = metered_at(c, current, surface_texel);
        vec2 d = uv - 0.5;
        float w = (isnan(v) || isinf(v)) ? 0.0 : exp(-4.0 * dot(d, d));
        values[i] = v;
        weights[i] = w;
        int bin = clamp(int((v - bin_low) * (float(bins) / bin_stops)), 0, bins - 1);
        atomicAdd(shared_bins[bin], uint(w * 256.0 + 0.5));
    }
    barrier();

    // Where the darkest and the brightest tenth of the weight end, inside the bins they end in.
    if (index == 0u) {
        uint total = 0u;
        for (int b = 0; b < bins; b++) {
            total += shared_bins[b];
        }
        float low_at = metering_cut * float(total);
        float high_at = (1.0 - metering_cut) * float(total);
        vec2 range = vec2(bin_low, bin_low + bin_stops);
        float below = 0.0;
        bool low_found = false;
        for (int b = 0; b < bins; b++) {
            float count = float(shared_bins[b]);
            if (count <= 0.0) {
                continue;
            }
            float start = bin_low + float(b) * (bin_stops / float(bins));
            if (!low_found && below + count >= low_at) {
                range.x = start + (low_at - below) / count * (bin_stops / float(bins));
                low_found = true;
            }
            if (below + count >= high_at) {
                range.y = start + (high_at - below) / count * (bin_stops / float(bins));
                break;
            }
            below += count;
        }
        shared_range = range;
    }
    barrier();

    vec2 range = shared_range;
    float sum = 0.0;
    float weight = 0.0;
    for (int i = 0; i < 16; i++) {
        if (values[i] >= range.x && values[i] <= range.y) {
            sum += values[i] * weights[i];
            weight += weights[i];
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

    // Nothing between the two ends (the whole frame within a bin): where they meet.
    float metered = shared_weight[0] > 1e-6 ? shared_sum[0] / shared_weight[0] : 0.5 * (range.x + range.y);
    // The frame's average lands here (log2 of the frame's units): by day about where the tone curve puts a lit grey
    // a little under the middle of the screen's range, in the dark lower, so a night looks like night.
    float key = mix(key_dark, 0.75, smoothstep(-10.0, -2.0, metered));
    float target = clamp(key - metered, ev_min, ev_max);
    float ev = exposure_ready(previous) ? previous.y : target;
    // About a second to see in a dark room after the sun, half that back out (eyes take longer, games less).
    float rate = target > ev ? 2.5 : 4.5;
    ev += (target - ev) * (1.0 - exp(-max(frameTime, 0.0) * rate));
    set_global(G_EXPOSURE, vec4(exp2(ev), ev, metered, 0.0));
}

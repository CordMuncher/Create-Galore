// The depth of field's passes (see dof.glsl), after DLSS (or FSR), in colortex4 at half the window's size.
// DOF_FOCUS_PASS: where the lens focuses. DOF_PREPARE: the frame and each pixel's circle. DOF_TILES: the largest circle
// in front of the focus in each tile. DOF_GATHER: the far and the near blur.

#if defined DOF_FOCUS_PASS
#define GLOBALS_WRITE
#endif
#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"
#include "/lib/dof.glsl"

#if defined DOF_FOCUS_PASS

// The autofocus, as a camera's spot focus: what most of a spot in the middle of the view shows (the spot's centre
// counting most), sorted by distance into half-octave steps from a quarter of a block out; the lens focuses on the
// average of the busiest step and the steps either side, and moves there in dioptres over about a third of a second,
// as the eye refocuses. The hand doesn't count.
layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(1, 1, 1);

uniform float frameTime;

const int focus_steps = 24;
shared uint step_weight[focus_steps];
shared uint busiest;
shared uint inverse_sum;
shared uint weight_sum;

void main() {
    uint index = gl_LocalInvocationIndex;
    if (index < uint(focus_steps)) {
        step_weight[index] = 0u;
    }
    if (index == 0u) {
        inverse_sum = 0u;
        weight_sum = 0u;
    }
    barrier();

    // 16 by 16 points over a square spot a tenth of the view's height across.
    vec2 offset = (vec2(gl_LocalInvocationID.xy) + 0.5) / 16.0 - 0.5;
    vec2 point = 0.5 * render_size() + offset * 0.1 * render_size().y;
    float inverse_distance = dof_inverse_distance(ivec2(point));
    float weight = inverse_distance < 0.0 ? 0.0 : exp(-dot(offset, offset) / (2.0 * 0.25 * 0.25));
    // Its step: a quarter of a block and nearer the first, the sky and past 512 blocks the last.
    int s = inverse_distance <= 1.0 / 512.0 ? focus_steps - 1
            : clamp(int(2.0 * log2(4.0 / inverse_distance)), 0, focus_steps - 2);
    uint fixed_weight = uint(weight * 65536.0 + 0.5);
    atomicAdd(step_weight[s], fixed_weight);
    barrier();

    if (index == 0u) {
        uint best = 0u;
        for (int i = 0; i < focus_steps; i++) {
            if (step_weight[i] > step_weight[best]) {
                best = uint(i);
            }
        }
        busiest = best;
    }
    barrier();

    if (abs(s - int(busiest)) <= 1) {
        atomicAdd(inverse_sum, uint(weight * min(inverse_distance, 20.0) * 65536.0 + 0.5));
        atomicAdd(weight_sum, fixed_weight);
    }
    barrier();

    if (index == 0u) {
        vec4 last = global(G_FOCUS);
        float target = weight_sum > 0u ? float(inverse_sum) / float(weight_sum) : last.x;
        // The image starts empty when the pack loads: start in focus.
        bool ready = last.y == 1.0 && last.x >= 0.0 && last.x <= 20.0;
        float focus = ready ? mix(last.x, target, 1.0 - exp(-max(frameTime, 0.0) / 0.3)) : target;
        set_global(G_FOCUS, vec4(focus, 1.0, 0.0, 0.0));
    }
}

#elif defined DOF_PREPARE

// One invocation for each half-size pixel: the frame's 2 by 2 pixels averaged, with the smallest of their circles
// (the nearest in front of the focus if any lies there, so what is in front spreads; else the sharpest, so what is in
// focus doesn't spread into the blur behind it).
layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

uniform sampler2D colortex0;
layout(rgba16f) uniform image2D colorimg4;

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(p, dof_half()))) {
        return;
    }
    // The frame as it is now: DLSS's result at the window's size, else the render-size corner.
    vec2 area = (cg_dlss_active > 0.5 ? window_size() : render_size()) / window_size();
    float focus = dof_focus();
    vec3 sum = vec3(0.0);
    float circle = 1e9;
    for (int i = 0; i < 4; i++) {
        vec2 q = vec2(p * 2 + ivec2(i & 1, i >> 1)) + 0.5;
        sum += max(textureLod(colortex0, q * area / window_size(), 0.0).rgb, 0.0);
        circle = min(circle, dof_circle_at(ivec2(q * render_scale()), focus));
    }
    imageStore(colorimg4, p, vec4(sum * 0.25, circle * 0.5));
}

#elif defined DOF_TILES

// One group for each tile.
layout(local_size_x = 16, local_size_y = 16) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

layout(rgba16f) uniform image2D colorimg4;

shared uint largest;

void main() {
    if (gl_LocalInvocationIndex == 0u) {
        largest = 0u;
    }
    barrier();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (all(lessThan(p, dof_half()))) {
        // Positive floats sort as their bits do.
        atomicMax(largest, floatBitsToUint(max(-imageLoad(colorimg4, p).a, 0.0)));
    }
    barrier();
    if (gl_LocalInvocationIndex == 0u) {
        imageStore(colorimg4, dof_tiles_origin() + ivec2(gl_WorkGroupID.xy), vec4(uintBitsToFloat(largest), 0.0, 0.0, 0.0));
    }
}

#elif defined DOF_GATHER

// One invocation for each half-size pixel.
layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

layout(rgba16f) uniform image2D colorimg4;

// The points over a disc of radius 1, as Vogel's spiral lays them (the golden angle from one to the next, the radius
// growing as the square root of the count): evenly over the disc, with no rings or gaps.
vec2 disc_point(int i) {
    float r = sqrt((float(i) + 0.5) / float(dof_points));
    float a = float(i) * 2.39996323;
    return r * vec2(cos(a), sin(a));
}

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(p, dof_half()))) {
        return;
    }
    vec2 here = vec2(p) + 0.5;
    vec4 centre = texelFetch(colortex4, p, 0);

    // The far blur, over this pixel's own circle: the points behind the focus whose circles reach it.
    vec3 far = centre.rgb;
    if (centre.a > 0.5) {
        vec3 sum = centre.rgb;
        float weight = 1.0;
        for (int i = 0; i < dof_points; i++) {
            vec2 o = disc_point(i) * centre.a;
            vec4 s = dof_sample(ivec2(0), here + o);
            float w = s.a > 0.0 ? clamp(s.a - length(o) + 1.0, 0.0, 1.0) : 0.0;
            sum += s.rgb * w;
            weight += w;
        }
        far = sum / weight;
    }

    // The near blur, over the largest near circle of the tiles around that reaches this pixel: the points in front
    // whose circles reach it, each standing for its share of the disc against its own circle's area (scatter as
    // gather), so they cover this pixel as much as their spread light would.
    float reach = 0.0;
    ivec2 tile = p / dof_tile;
    int range = int(ceil(dof_max_radius * window_size().y * 0.5 / float(dof_tile)));
    for (int y = -range; y <= range; y++) {
        for (int x = -range; x <= range; x++) {
            ivec2 t = tile + ivec2(x, y);
            if (any(lessThan(t, ivec2(0))) || any(greaterThanEqual(t, dof_tiles()))) {
                continue;
            }
            float r = texelFetch(colortex4, dof_tiles_origin() + t, 0).r;
            vec2 low = vec2(t * dof_tile);
            vec2 gap = max(max(low - here, here - low - float(dof_tile)), 0.0);
            if (length(gap) <= r) {
                reach = max(reach, r);
            }
        }
    }
    vec4 near = vec4(0.0);
    if (reach > 0.5) {
        vec3 sum = vec3(0.0);
        float weight = 0.0;
        float share = reach * reach / float(dof_points);
        for (int i = 0; i < dof_points; i++) {
            vec2 o = disc_point(i) * reach;
            vec4 s = dof_sample(ivec2(0), here + o);
            float n = -s.a;
            if (n > 0.0) {
                float w = clamp(n - length(o) + 1.0, 0.0, 1.0) * min(1.0, share / (n * n));
                sum += s.rgb * w;
                weight += w;
            }
        }
        if (weight > 0.0) {
            float cover = min(weight, 1.0);
            near = vec4(sum / weight * cover, cover);
        }
    }

    imageStore(colorimg4, dof_far_origin() + p, vec4(far, 1.0));
    imageStore(colorimg4, dof_near_origin() + p, near);
}

#endif

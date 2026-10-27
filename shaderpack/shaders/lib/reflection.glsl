// Reflections from the screen: a ray from a surface, followed through the scene's depth until it passes just behind
// something; what the screen shows there is what the surface reflects. Off the screen, behind something the screen
// can't see past, or towards the sky, the caller falls back to the sky (dimmed where the surface sees little of it).
//
// The ray goes in steps that grow as it goes (short near the surface, for the contact, long far off), then halves
// its last step a few times to find where it went behind the surface it hit.

#if !defined LIB_REFLECTION
#define LIB_REFLECTION

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"

#if REFLECTION_QUALITY >= 3
const int reflection_steps = 40;
const int reflection_refine = 6;
#elif REFLECTION_QUALITY == 2
const int reflection_steps = 28;
const int reflection_refine = 5;
#else
const int reflection_steps = 18;
const int reflection_refine = 4;
#endif

// (The program that includes this declares gbufferProjection; one that defines REFLECTION_DH_DEPTH as Distant
// Horizons' opaque depth also declares that and dhProjection, and its rays find Distant Horizons' terrain past
// Minecraft's, so far land shows in the water.)

// A view-space distance (negative z) from a depth buffer's value, by the projection.
float reflection_view_z(float depth) {
    return -gbufferProjection[3][2] / (depth * 2.0 - 1.0 + gbufferProjection[2][2]);
}

// The view-space z of what the screen shows at a pixel (Minecraft's world, else Distant Horizons' past it), or
// -1e9 where it shows only the sky or the hand (which hides nothing).
float reflection_surface_z(sampler2D depth_tex, ivec2 pixel, float hand_limit) {
    float depth = texelFetch(depth_tex, pixel, 0).r;
    if (depth < 1.0) {
        return depth >= hand_limit ? reflection_view_z(depth) : -1e9;
    }
#ifdef REFLECTION_DH_DEPTH
    float dh = texelFetch(REFLECTION_DH_DEPTH, pixel, 0).r;
    if (dh < 1.0) {
        return -dhProjection[3][2] / (dh * 2.0 - 1.0 + dhProjection[2][2]);
    }
#endif
    return -1e9;
}

// Where a view-space point is on the screen, in render pixels (jittered as the frame is), with its clip w.
vec3 reflection_pixel(vec3 view) {
    vec4 clip = gbufferProjection * vec4(view, 1.0);
    vec2 pixel = (clip.xy / clip.w * 0.5 + 0.5) * render_size() + vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    return vec3(pixel, clip.w);
}

// Follows a ray from origin along dir (view space) through the depth in depth_tex. Returns the render pixel it hit
// (xy) and how far the hit can be trusted (z, 0..1: 0 for a miss, less towards the screen's edges). noise: 0..1, a
// different one each pixel and frame, which moves the steps (the upscaler evens it out). hand_limit: depths below it
// are the hand's, which hides nothing.
vec3 reflection_trace(sampler2D depth_tex, vec3 origin, vec3 dir, float noise, float hand_limit) {
    vec2 size = render_size();
    float dist = length(origin);
    float step_len = 0.1 + 0.015 * dist;
    vec3 before = origin;
    vec3 p = origin + dir * step_len * (0.25 + 0.75 * noise);
    for (int i = 0; i < reflection_steps; i++) {
        vec3 at = reflection_pixel(p);
        if (at.z <= 0.0 || any(lessThan(at.xy, vec2(0.0))) || any(greaterThanEqual(at.xy, size))) {
            break; // behind the eye or off the screen
        }
        float z = reflection_surface_z(depth_tex, ivec2(at.xy), hand_limit);
        if (z > -1e8) {
            float behind = z - p.z; // how far the ray is behind what the screen shows there
            // A thin wall the ray stepped across counts; passing well behind something (a tree seen past its edge)
            // doesn't.
            float thickness = max(1.5 * step_len, 0.25 + 0.02 * -p.z);
            if (behind > 0.0 && behind < thickness) {
                vec3 a = before;
                vec3 b = p;
                for (int j = 0; j < reflection_refine; j++) {
                    vec3 mid = 0.5 * (a + b);
                    vec3 m = reflection_pixel(mid);
                    float mz = reflection_surface_z(depth_tex, ivec2(clamp(m.xy, vec2(0.0), size - 1.0)), hand_limit);
                    if (mz - mid.z > 0.0) {
                        b = mid;
                    } else {
                        a = mid;
                    }
                }
                vec2 hit = reflection_pixel(b).xy;
                vec2 uv = hit / size;
                vec2 edge = min(uv, 1.0 - uv);
                float trust = smoothstep(0.0, 0.08, min(edge.x, edge.y));
                return vec3(hit, trust);
            }
        }
        before = p;
        step_len *= 1.25;
        p += dir * step_len;
    }
    return vec3(0.0);
}

#endif

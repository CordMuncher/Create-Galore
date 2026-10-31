// The sun's light shafts in the water around you (only while you are in it): along each look, how much sunlight
// the water scatters towards you, where the shadow map says the sun reaches (through the water above, focused by its
// waves: lib/shadow.glsl shadow_water_light), dimmed by the water between. Mostly forwards (towards the sun the
// shafts burn brightest), a little every way. At half the render size each way, 16 looks along each view at a
// different place each frame, kept over frames (a tenth of each new frame mixed in, the old held within what the new
// frame's 3 x 3 neighbours show, so it never trails); program/fog.fsh adds it. In colortex9: last frame's and this
// frame's take turns in the top right and bottom left quarters.

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/motion.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"
#include "/lib/water.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

layout(rgba16f) uniform image2D colorimg9;
uniform int isEyeInWater;

shared vec3 s_now[64];

const float shaft_reach = 32.0;

float henyey_greenstein(float cos_theta, float g) {
    float d = 1.0 + g * g - 2.0 * g * cos_theta;
    return (1.0 - g * g) / (4.0 * PI * d * sqrt(d));
}

void main() {
    ivec2 id = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = ivec2(render_size());
    ivec2 half_size = (size + 1) / 2;
    bool on = all(lessThan(id, half_size)) && isEyeInWater == 1 && SHAFT_STRENGTH > 0.0;
    vec3 now = vec3(0.0);
    vec3 dir = vec3(0.0, 0.0, 1.0);
    float reach = 0.0;
#if defined SUNLIGHT || defined WORLD_END
    if (on) {
        ivec2 texel = min(id * 2 + ivec2(frameCounter & 1, (frameCounter >> 1) & 1), size - 1);
        float depth = texelFetch(depthtex0, texel, 0).r;
        vec3 scene = view_to_scene_pos(pixel_to_view(texel, depth));
        dir = normalize(scene);
        reach = depth >= 1.0 ? shaft_reach : min(length(scene), shaft_reach);
        const int steps = 16;
        float dt = reach / float(steps);
        float jitter = fract(ign(vec2(id)) + 0.618034 * float(frameCounter & 255));
        vec3 sum = vec3(0.0);
        for (int i = 0; i < steps; i++) {
            float t = (float(i) + jitter) * dt;
            sum += shadow_water_light(dir * t) * exp(-water_extinction_inside * t);
        }
        float cos_theta = dot(dir, g_light_dir.xyz);
        float phase = mix(1.0 / (4.0 * PI), henyey_greenstein(cos_theta, 0.7), 0.7);
        now = sum * dt * water_scatter_inside * phase * g_light_color.rgb * cloud_shadow(vec3(0.0)) * SHAFT_STRENGTH;
    }
#endif
    uint me = gl_LocalInvocationIndex;
    s_now[me] = now;
    barrier();
    if (!all(lessThan(id, half_size))) {
        return;
    }
    bool even = (frameCounter & 1) == 0;
    ivec2 read_corner = even ? ivec2(half_size.x, 0) : ivec2(0, half_size.y);
    ivec2 write_corner = even ? ivec2(0, half_size.y) : ivec2(half_size.x, 0);
    if (!on) {
        imageStore(colorimg9, id + write_corner, vec4(0.0));
        return;
    }
    // The new frame's neighbours (within this group's tile) bound what is kept.
    ivec2 local = ivec2(gl_LocalInvocationID.xy);
    vec3 lo = now;
    vec3 hi = now;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 q = local + ivec2(x, y);
            if (all(greaterThanEqual(q, ivec2(0))) && all(lessThan(q, ivec2(8)))) {
                vec3 v = s_now[q.y * 8 + q.x];
                lo = min(lo, v);
                hi = max(hi, v);
            }
        }
    }
    // Where this look was last frame (taken at half its reach).
    vec4 clip = previous_clip(dir * max(reach * 0.5, 1.0), gbufferPreviousProjection);
    vec3 result = now;
    if (clip.w > 0.0) {
        vec2 was = (clip.xy / clip.w * 0.5 + 0.5) * vec2(half_size);
        ivec2 at = ivec2(floor(was));
        if (all(greaterThanEqual(at, ivec2(0))) && all(lessThan(at, half_size))) {
            vec4 old = imageLoad(colorimg9, at + read_corner);
            if (old.a > 0.5) {
                result = mix(clamp(old.rgb, lo, hi), now, 0.1);
            }
        }
    }
    imageStore(colorimg9, id + write_corner, vec4(result, 1.0));
}

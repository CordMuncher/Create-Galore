// The clouds (lib/clouds.glsl), at half the render size each way: each pixel looks through the cloud layer as far as
// the farthest of its four render pixels sees (the sky, or a mountain inside the clouds), gathering the cloud's light
// and what still gets through it, from a starting point along the look that moves a little each frame. Then this
// frame's result is mixed with last frame's at the same spot in the clouds (found by where most of the cloud is along
// the look and how far the wind carried it), held within the range of this frame's results around it so nothing
// stale lingers, and the mix over a few frames smooths away the moving start. Results in cg_cloud_img (see
// lib/clouds.glsl); the lighting pass (deferred_light.fsh) lays them over the sky and far terrain, and the DLSS pass
// gives cloudy sky the clouds' own motion.
//
// OWN DENOISER (temporal): the mix over frames comes off when Ray Reconstruction takes the clouds' noise instead (see
// the README's "Own denoisers and Ray Reconstruction").

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/motion.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

layout(rgba16f) uniform image2D cg_cloud_img;
layout(rgba16f) writeonly uniform image2D cg_sky_img; // the clouds' shadow map goes in its rows 312 on

#include "/lib/clouds.glsl"

uniform float frameTime;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

shared vec4 s_light[64];

// One texel of the clouds' shadow map (cloud_shadow in lib/sky.glsl): what gets through the clouds towards the light
// from the point at the camera's height under the texel's middle, through their body (eight looks across the layer).
void shadow_texel(ivec2 texel) {
    float through = 1.0;
#ifdef CLOUD_SHADOWS
    vec2 rel = cloud_shadow_corner() + (vec2(texel) + 0.5) * cloud_shadow_texel;
    vec2 shift = cloud_shadow_shift();
    float y0 = cameraPosition.y;
    float bottom = cloud_bottom();
    float lo = max(bottom, y0);
    float hi = bottom + cloud_depth;
    if (clouds_here() && hi > lo) {
        vec3 origin = cloud_origin();
        const int looks = 8;
        float dy = (hi - lo) / float(looks);
        float tau = 0.0;
        // The weather changes over kilometres: one look at it does for the whole way across the layer.
        vec2 mid = rel + shift * (0.5 * (lo + hi) - y0);
        CloudMap map = cloud_map(origin.xz + mid);
        for (int i = 0; i < looks; i++) {
            float y = lo + (float(i) + 0.5) * dy;
            vec2 xz = rel + shift * (y - y0);
            vec3 p = origin + vec3(xz.x, y - y0, xz.y);
            float h = (y - bottom) / cloud_depth;
            tau += cloud_body(p, h, map) * 0.15 * dy * length(vec3(shift, 1.0));
        }
        through = max(exp(-tau), 0.05);
    }
#endif
    imageStore(cg_sky_img, cloud_shadow_origin + texel, vec4(through, 0.0, 0.0, 1.0));
}

// How far the look through a render pixel goes before a surface stops it (cloud_far_sky for the sky and the hand).
float surface_distance(ivec2 texel) {
    texel = min(texel, ivec2(render_size()) - 1);
    float depth = texelFetch(depthtex0, texel, 0).r;
    if (depth >= 1.0) {
#ifdef DISTANT_HORIZONS
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
            vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
            return length(v.xyz / v.w);
        }
#endif
        return cloud_far_sky;
    }
    return is_hand_depth(depth) ? cloud_far_sky : length(pixel_to_view(texel, depth));
}

struct March {
    vec4 light;  // rgb the light, a what gets through
    float front; // where the cloud starts
    float mid;   // where most of it is
    float slab;  // halfway through the layer along the look (to follow the clouds where there are none)
};

March march(vec3 dir, float limit, float jitter) {
    March m;
    m.light = vec4(0.0, 0.0, 0.0, 1.0);
    m.front = cloud_far_sky;
    m.mid = cloud_far_sky;
    m.slab = 4000.0;
    if (!clouds_here()) {
        return m;
    }

    // The part of the look inside the layer, and inside the clouds' reach.
    float y0 = cameraPosition.y;
    float bottom = cloud_bottom();
    float top = bottom + cloud_depth;
    float t0 = 0.0;
    float t1 = 1e9;
    if (abs(dir.y) > 1e-5) {
        float ta = (bottom - y0) / dir.y;
        float tb = (top - y0) / dir.y;
        t0 = max(min(ta, tb), 0.0);
        t1 = max(ta, tb);
    } else if (y0 < bottom || y0 > top) {
        return m;
    }
    float flat_len = length(dir.xz);
    t1 = min(t1, cloud_reach / max(flat_len, 1e-4));
    m.slab = t1 > t0 ? 0.5 * (t0 + t1) : m.slab;
    t1 = min(t1, limit);
    if (t1 <= t0) {
        return m;
    }

    // Steps: about one every 6 blocks, at most CLOUD_STEPS; from inside the layer they start short and grow (squared
    // spacing), so the clouds round you keep their detail and the far ones still get looked at.
    float len = t1 - t0;
    int n = clamp(int(len / 6.0) + 4, 6, CLOUD_STEPS);
    float spread = t0 < 1.0 ? 2.0 : 1.0;

    vec4 lit = g_cloud_light;
    vec3 l = lit.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz;
    float cos_theta = dot(dir, l);
    // Mostly forwards (the silver edge and the sun burning through), some backwards.
    float phase[3];
    for (int o = 0; o < 3; o++) {
        float g = 0.7 * exp2(-float(o));
        phase[o] = mix(cloud_phase(cos_theta, g), cloud_phase(cos_theta, -0.25), 0.3);
    }
    // The lights in display values, as lib/lighting.glsl mixes them.
    const vec3 to_display = vec3(1.0 / 2.2);
    vec3 sun = pow(max(lit.rgb, 0.0), to_display);
    vec3 sky_light = pow(max(cloud_sky_light(dir), 0.0), to_display);
    // A little of the sunlit ground's light from below (green-brown).
    vec3 ground_light = pow(max(lit.rgb * max(l.y, 0.0) * vec3(0.10, 0.11, 0.08) + g_sky_up.rgb * 0.1, 0.0), to_display);
    vec3 sky_seen = sky_look(sky_air_seen(dir), dir);
    vec3 origin = cloud_origin();
    const float extinction = 0.15; // per block in the thickest cloud
    // The powder look: with the sun behind you, the outer billows (little cloud between them and the sun) a little
    // darker than the folds.
    float powder_amount = 0.6 * (0.5 - 0.5 * cos_theta);

    float through = 1.0;
    vec3 light = vec3(0.0);
    float mid_sum = 0.0;
    float weight_sum = 0.0;
    for (int i = 0; i < CLOUD_STEPS; i++) {
        if (i >= n) {
            break;
        }
        float s = pow((float(i) + jitter) / float(n), spread);
        float s_end = pow((float(i) + 1.0) / float(n), spread);
        float dt = len * (s_end - pow(float(i) / float(n), spread));
        float t = t0 + len * s;
        vec3 p = origin + dir * t;
        float h = (y0 + dir.y * t - bottom) / cloud_depth;
        if (h <= 0.0 || h >= 1.0) {
            continue;
        }
        float fade = 1.0 - smoothstep(1000.0, cloud_reach, t * flat_len);
        CloudMap map = cloud_map(p.xz);
        float body;
        float density = cloud_density(p, h, map, dt, body) * fade;
        if (density <= 0.002) {
            continue;
        }

        // The light's way out: three looks towards it, each standing for the stretch round it; the nearest takes the
        // billows (so the crevices between them shade), the others only the body.
        float shade = 0.0;
        for (int k = 0; k < 3; k++) {
            float d = k == 0 ? 3.0 : (k == 1 ? 12.0 : 30.0);
            float hl = h + l.y * d / cloud_depth;
            if (hl > 0.0 && hl < 1.0) {
                float near_body;
                float v = k == 0 ? cloud_density(p + l * d, hl, map, dt, near_body) : cloud_body(p + l * d, hl, map);
                shade += v * (k == 0 ? 6.0 : (k == 1 ? 12.0 : 24.0));
            }
        }
        float tau = shade * extinction;
        float direct = 0.0;
        for (int o = 0; o < 3; o++) {
            direct += exp2(-float(o)) * phase[o] * exp(-tau * exp2(-float(o)));
        }
        direct *= mix(1.0, 1.0 - exp(-tau * 2.0 - density * 1.5), powder_amount);
        // The sky's light from above: the undersides get less of it (in rain, with no sun, the closed-up layer
        // passes more of it round inside). The light mixes in display values like a surface's, up to about a sunlit
        // face's; the bright forward lobe round the sun goes on top as it is (in display values its peak would square
        // into a glare many times the sun's).
        float open = mix(mix(0.4, 0.65, rainStrength), 1.0, h) * (1.0 - 0.35 * body);
        float soft = min(direct, 1.2);
        vec3 sample_light = pow(sun * soft * 1.1 + sky_light * open + ground_light * (1.0 - h), vec3(2.2))
                          + lit.rgb * (direct - soft) * 0.15;
        // Far off, the clouds take on the sky's colour behind them and fade into the haze.
        float haze = (1.0 - exp(-t * flat_len / 1200.0)) * 0.95;
        sample_light = mix(sample_light, sky_seen, haze);

        float step_through = exp(-density * extinction * dt);
        float taken = through * (1.0 - step_through);
        light += sample_light * taken;
        if (m.front >= cloud_far_sky) {
            m.front = t;
        }
        mid_sum += t * taken;
        weight_sum += taken;
        through *= step_through;
        if (through < 0.02) {
            break;
        }
    }
    if (through < 0.02) {
        light /= 1.0 - through;
        through = 0.0;
    }
    m.light = vec4(light, through);
    if (weight_sum > 1e-4) {
        m.mid = mid_sum / weight_sum;
    }
    return m;
}

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    // The shadow map's texels, shared out over every invocation of the pass (at least a quarter of them each frame
    // even at the smallest render size: then some do more than one).
    uint invocations = gl_NumWorkGroups.x * gl_NumWorkGroups.y * 64u;
    uint index = (gl_WorkGroupID.y * gl_NumWorkGroups.x + gl_WorkGroupID.x) * 64u + gl_LocalInvocationIndex;
    for (uint k = index; k < 65536u; k += invocations) {
        shadow_texel(ivec2(k & 255u, k >> 8u));
    }

    ivec2 size = cloud_size();
    bool inside = all(lessThan(p, size));
    uint local = gl_LocalInvocationIndex;
    ivec2 slot = cloud_slot(frameCounter);
    ivec2 last_slot = cloud_slot(frameCounter + 1);
    ivec2 below = ivec2(0, cloud_half().y);

    March m;
    m.light = vec4(0.0, 0.0, 0.0, 1.0);
    float limit = 0.0;
    vec3 dir = vec3(0.0, 1.0, 0.0);
    if (inside) {
        // As far as the farthest of the four render pixels sees.
        ivec2 texel = p * 2;
        limit = max(max(surface_distance(texel), surface_distance(texel + ivec2(1, 0))),
                    max(surface_distance(texel + ivec2(0, 1)), surface_distance(texel + ivec2(1, 1))));
        // The look through the middle of the four, without the upscaler's jitter.
        vec2 ndc = (vec2(texel) + 1.0) / render_size() * 2.0 - 1.0;
        vec4 view = gbufferProjectionInverse * vec4(ndc, 1.0, 1.0);
        dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
        float jitter = fract(ign(vec2(p)) + float(frameCounter % 256) * GOLDEN);
        m = march(dir, limit, jitter);
        if (limit >= cloud_far_sky && m.light.a > 0.0) {
            // The high layer, behind the main one.
            vec4 high = cirrus(dir, g_cloud_light.rgb, cloud_sky_light(dir));
            m.light.rgb += m.light.a * high.rgb;
            m.light.a *= high.a;
        }
    }
    s_light[local] = m.light;
    barrier();
    if (!inside) {
        return;
    }

    // This frame's range round the pixel (within its work group).
    vec4 lo = m.light;
    vec4 hi = m.light;
    ivec2 at = ivec2(gl_LocalInvocationID.xy);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 q = at + ivec2(x, y);
            if (all(greaterThanEqual(q, ivec2(0))) && all(lessThan(q, ivec2(8)))
                    && all(lessThan(p + ivec2(x, y), size))) {
                vec4 v = s_light[q.y * 8 + q.x];
                lo = min(lo, v);
                hi = max(hi, v);
            }
        }
    }

    // Last frame's result at the same spot in the clouds.
    vec4 result = m.light;
    float follow = m.mid < cloud_far_sky ? m.mid : min(m.slab, limit);
    vec3 was = dir * follow - cloud_wind() * frameTime;
    vec4 clip = previous_clip(was, gbufferPreviousProjection);
    if (clip.w > 0.0) {
        vec2 q = ((clip.xy / clip.w) * 0.5 + 0.5) * render_size() * 0.5 - 0.5;
        if (all(greaterThanEqual(q, vec2(-0.5))) && all(lessThan(q, vec2(size) - 0.5))) {
            ivec2 base = ivec2(floor(q));
            vec2 f = q - vec2(base);
            vec4 history = vec4(0.0);
            for (int i = 0; i < 4; i++) {
                ivec2 o = ivec2(i & 1, i >> 1);
                float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
                history += imageLoad(cg_cloud_img, last_slot + clamp(base + o, ivec2(0), size - 1)) * w;
            }
            vec4 margin = (hi - lo) * 0.1;
            history = clamp(history, lo - margin, hi + margin);
            result = mix(history, m.light, 0.15);
        }
    }

    imageStore(cg_cloud_img, slot + p, result);
    imageStore(cg_cloud_img, slot + below + p, vec4(m.front, m.mid, limit, 0.0));
}

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
layout(rgba16f) writeonly uniform image2D cg_sky_img; // the clouds' shadow map and the sky mirror go in it

#include "/lib/clouds.glsl"
#include "/lib/sky_mirror.glsl"

uniform float frameTime;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

shared vec4 s_light[64];

// One texel of the clouds' shadow map (cloud_shadow in lib/sky.glsl): what gets through the clouds along the line
// towards the light from the floor of the layer under the texel's middle, from four heights on it to the top, through
// the clouds' body (three looks a quarter, twelve in all).
void shadow_texel(ivec2 cell) {
    vec4 through = vec4(1.0);
#ifdef CLOUD_SHADOWS
    vec2 shift = cloud_shadow_shift();
    float bottom = cloud_bottom();
    vec2 rel = cloud_shadow_middle(cell);
    vec3 origin = cloud_origin();
    float y0 = cameraPosition.y;
    const int looks = 3;
    const float dy = cloud_depth / 4.0 / float(looks);
    float step_len = dy * length(vec3(shift, 1.0));
    // The weather changes over kilometres: one look at it does for the whole way up the layer.
    vec2 mid = rel + shift * (0.5 * cloud_depth);
    CloudMap map = cloud_map(origin.xz + mid);
    vec4 tau = vec4(0.0); // each quarter's own
    for (int q = 0; q < 4; q++) {
        float sum = 0.0;
        for (int i = 0; i < looks; i++) {
            float up = (float(q * looks + i) + 0.5) * dy;
            vec2 xz = rel + shift * up;
            float y = bottom + up;
            sum += cloud_body(origin + vec3(xz.x, y - y0, xz.y), y, map);
        }
        tau[q] = sum * cloud_extinction * step_len;
    }
    // From each height to the top.
    vec4 above = vec4(tau.x + tau.y + tau.z + tau.w, tau.y + tau.z + tau.w, tau.z + tau.w, tau.w);
    through = max(exp(-above), vec4(0.05));
#endif
    imageStore(cg_sky_img, cloud_shadow_slot(cell), through);
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

// The clouds along a look from a point (from: scene space, off the camera) as far as limit. jitter: where in each
// step the look is taken (0..1); pixel: the angle the look stands for (radians).
March march(vec3 from, vec3 dir, float limit, float jitter, float pixel) {
    March m;
    m.light = vec4(0.0, 0.0, 0.0, 1.0);
    m.front = cloud_far_sky;
    m.mid = cloud_far_sky;
    m.slab = 4000.0;
    if (!clouds_here()) {
        return m;
    }

    // The part of the look inside the layer, and inside the clouds' reach.
    float y0 = cameraPosition.y + from.y;
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

    // Steps that grow with the distance (a fiftieth of it, never under six blocks nor under what a pixel covers
    // there), every one of them looked at, at a point along it that moves from frame to frame (the mix over frames
    // smooths the steps away): nothing is skipped on a guess, so a thin cloud far off is never missed on one frame and
    // found on the next. In clear air a step costs one look at the shapes' noise. Past half the steps they grow
    // faster, so a look from inside the layer still reaches the far clouds.
    vec4 lit = g_cloud_light;
    vec3 l = lit.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz;
    float cos_theta = dot(dir, l);
    vec3 sky_light = cloud_sky_light(dir);
    vec3 ground_light = cloud_ground_light(sky_light);
    vec3 sky_seen = sky_look(sky_air_seen(dir), dir);
    vec3 origin = cloud_origin() + from;

    // The weather is read every 64 blocks across the ground (it changes over hundreds) and evened out between.
    float map_span = 64.0 / max(flat_len, 0.25);
    float map_from = t0;
    CloudMap map_a = cloud_map((origin + dir * t0).xz);
    CloudMap map_b = cloud_map((origin + dir * (t0 + map_span)).xz);

    float through = 1.0;
    vec3 light = vec3(0.0);
    float mid_sum = 0.0;
    float weight_sum = 0.0;
    float t = t0;
    const int steps = CLOUD_STEPS * 4;
    for (int i = 0; i < steps; i++) {
        float rate = max(0.02, pixel * 2.0) * (1.0 + 0.06 * float(max(i - steps / 2, 0)));
        float dt = clamp(t * rate, 6.0, 128.0);
        float ts = t + dt * jitter;
        if (ts >= t1 || through < 0.01) {
            break;
        }
        t += dt;
        if (ts >= map_from + map_span) {
            map_from += map_span * floor((ts - map_from) / map_span);
            map_a = cloud_map((origin + dir * map_from).xz);
            map_b = cloud_map((origin + dir * (map_from + map_span)).xz);
        }
        CloudMap map = cloud_map_between(map_a, map_b, (ts - map_from) / map_span);
        vec3 p = origin + dir * ts;
        float y = y0 + dir.y * ts;
        // What one of these pixels covers there (blocks): detail finer than that, or than the step, would only
        // shimmer.
        float blur = max(dt, ts * pixel * 3.0);
        float inside;
        float density = cloud_density(p, y, map, blur, inside);
        density *= 1.0 - smoothstep(1500.0, cloud_reach, ts * flat_len);
        if (density <= 0.002) {
            continue;
        }

        // The light's way out: five looks towards it, each standing for the stretch of the way round it. Near by,
        // the first three take the billows, so the folds between them shade.
        float shade = 0.0;
        for (int j = 0; j < 5; j++) {
            float d = j == 0 ? 3.0 : (j == 1 ? 9.0 : (j == 2 ? 21.0 : (j == 3 ? 45.0 : 93.0)));
            float w = j == 0 ? 6.0 : (j == 1 ? 6.0 : (j == 2 ? 18.0 : (j == 3 ? 30.0 : 66.0)));
            float unused;
            shade += (j < 3 && blur < 24.0 ? cloud_density(p + l * d, y + l.y * d, map, max(blur, 4.0 + d), unused)
                                           : cloud_body(p + l * d, y + l.y * d, map)) * w;
        }
        float tau = shade * cloud_extinction;
        vec3 sample_light = cloud_shade(tau, cos_theta, cloud_above(inside, y, map), y - map.base, lit.rgb, sky_light,
                                        ground_light);
        // Far off, the clouds take on the sky's colour behind them and fade into the haze.
        float haze = (1.0 - exp(-ts * flat_len / 1200.0)) * 0.95;
        sample_light = mix(sample_light, sky_seen, haze);

        float step_through = exp(-density * cloud_extinction * dt);
        float taken = through * (1.0 - step_through);
        light += sample_light * taken;
        if (m.front >= cloud_far_sky) {
            m.front = ts;
        }
        mid_sum += ts * taken;
        weight_sum += taken;
        through *= step_through;
    }
    if (through < 0.01) {
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
#ifdef CLOUD_SHADOWS
    // A quarter of the shadow map's texels each frame (each 2 x 2 of the world's texels takes its turn at one), shared
    // out over every invocation of the pass; all of them when the window jumped (a teleport, a trip to another world).
    if (clouds_here()) {
        ivec2 low = cloud_shadow_low();
        ivec2 last_low = cloud_shadow_low(previousCameraPositionInt, previousCameraPositionFract);
        bool jumped = any(greaterThan(abs(low - last_low), ivec2(4))) || frameCounter < 4;
        uint count = jumped ? 65536u : 16384u;
        ivec2 turn = ivec2(frameCounter & 1, (frameCounter >> 1) & 1);
        uint invocations = gl_NumWorkGroups.x * gl_NumWorkGroups.y * 64u;
        uint index = (gl_WorkGroupID.y * gl_NumWorkGroups.x + gl_WorkGroupID.x) * 64u + gl_LocalInvocationIndex;
        for (uint k = index; k < count; k += invocations) {
            ivec2 at = jumped ? ivec2(k & 255u, k >> 8u) : ivec2(k & 127u, k >> 7u) * 2;
            // The world texel in the window at this place, on its turn.
            ivec2 cell = low + at;
            if (!jumped) {
                cell += (turn - cell) & 1;
            }
            shadow_texel(cell);
        }
    }
#endif

#ifdef VOLUME_CLOUDS
    // The sky mirror (lib/sky_mirror.glsl): an eighth of its texels each frame (all of them when the camera jumped),
    // each a look through the clouds from sea level under the camera, from a new point in its steps each time and
    // mixed half and half with what it held, so it settles smooth; then a quarter of the soft copy's, each the
    // average of the sixteen texels it covers.
    {
        bool jumped = distance(vec3(cameraPositionInt - previousCameraPositionInt), vec3(0.0)) > 64.0
                || frameCounter < 16;
        uint invocations = gl_NumWorkGroups.x * gl_NumWorkGroups.y * 64u;
        uint index = (gl_WorkGroupID.y * gl_NumWorkGroups.x + gl_WorkGroupID.x) * 64u + gl_LocalInvocationIndex;
        uint all_texels = uint(mirror_size.x * mirror_size.y);
        for (uint k = index; k < (jumped ? all_texels : all_texels / 8u); k += invocations) {
            uint cell = jumped ? k : k * 8u + uint(frameCounter & 7);
            ivec2 texel = ivec2(cell % uint(mirror_size.x), cell / uint(mirror_size.x));
            vec3 d = mirror_dir(texel, mirror_size);
            vec3 from = vec3(0.0, mirror_sea - cameraPosition.y, 0.0);
            vec4 fresh = vec4(0.0, 0.0, 0.0, 1.0);
            if (clouds_here()) {
                float jitter = fract(float(cell) * GOLDEN + float((frameCounter >> 3) & 63) * 0.7548777);
                fresh = march(from, d, cloud_far_sky, jitter, 0.012).light;
                vec4 high = cirrus(d, g_cloud_light.rgb, cloud_sky_light(d), from);
                fresh.rgb += fresh.a * high.rgb;
                fresh.a *= high.a;
            }
            vec4 old = texelFetch(cg_sky_tex, mirror_origin + texel, 0);
            bool usable = !jumped && old.a >= 0.0 && old.a <= 1.0;
            imageStore(cg_sky_img, mirror_origin + texel, usable ? mix(old, fresh, 0.5) : fresh);
        }
        uint soft_texels = uint(mirror_soft_size.x * mirror_soft_size.y);
        for (uint k = index; k < soft_texels / 4u; k += invocations) {
            uint cell = k * 4u + uint(frameCounter & 3);
            ivec2 texel = ivec2(cell % uint(mirror_soft_size.x), cell / uint(mirror_soft_size.x));
            vec4 sum = vec4(0.0);
            for (int j = 0; j < 16; j++) {
                vec4 v = texelFetch(cg_sky_tex, mirror_origin + texel * 4 + ivec2(j & 3, j >> 2), 0);
                sum += v.a >= 0.0 && v.a <= 1.0 ? v : vec4(0.0, 0.0, 0.0, 1.0);
            }
            imageStore(cg_sky_img, mirror_soft_origin + texel, sum / 16.0);
        }
    }
#endif

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
        m = march(vec3(0.0), dir, limit, jitter, 2.0 * pixel_angle()); // the clouds' pixels are twice the render's
        if (limit >= cloud_far_sky && m.light.a > 0.0) {
            // The high layer, behind the main one.
            vec4 high = cirrus(dir, g_cloud_light.rgb, cloud_sky_light(dir), vec3(0.0));
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
            vec4 held = clamp(history, lo - margin, hi + margin);
            // Where last frame's had to be pulled into this frame's range (an edge on the move), less of it is
            // kept, so a moving edge stays crisp instead of smearing over several frames.
            float pulled = clamp(2.0 * length(held - history) / (length(hi - lo) + 0.02), 0.0, 1.0);
            result = mix(held, m.light, mix(0.15, 0.5, pulled));
        }
    }

    imageStore(cg_cloud_img, slot + p, result);
    imageStore(cg_cloud_img, slot + below + p, vec4(m.front, m.mid, limit, 0.0));
}

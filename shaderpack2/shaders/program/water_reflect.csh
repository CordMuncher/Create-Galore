// The water's reflection (WATER_QUALITY 1 to 3): for each water pixel the forward pass marked (colortex6), a ray
// reflected off its waves. At High and Ultra it is traced through the block copy (lib/reflect.glsl), so the
// reflection is the same from wherever you look and holds what is off screen too; mobs, the hand's world and
// Create's moving machines are not in the copy, so a short look along the screen catches them where they are nearer
// than what the trace found. At Medium only that look along the screen (further), and the sky with its clouds where
// it finds nothing. At Medium and High one look per 2 x 2 pixels (the first water pixel of the square), with where it
// was taken from beside it, for program/fog.fsh to blend them back up by depth and normal; at Ultra one per pixel,
// reaching further.

#include "/lib/settings.glsl"
#define CLOUDS_NO_READ_BACK
#include "/lib/gbuffer_read.glsl"
#include "/lib/reflect.glsl"
#include "/lib/sky.glsl"
#include "/lib/water.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
#if WATER_QUALITY == 3
const vec2 workGroupsRender = vec2(1.0, 1.0);
#else
const vec2 workGroupsRender = vec2(0.5, 0.5);
#endif

uniform sampler2D colortex5; // the lit scene before water and glass (program/deferred_light.fsh)
uniform sampler2D colortex6;
uniform sampler2D depthtex1;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
layout(rgba16f) uniform image2D colorimg8;

struct ScreenHit {
    bool hit;
    vec3 color;
    float fade;     // 1 well inside the screen, less towards its edges
    float dist;     // blocks from where the look left the water
    int material;   // what the G-buffer says it hit (MAT_)
};

// What a look from view_start along view_dir finds on the screen: marched in steps that grow by half each time (the
// first 0.3 blocks, the longest 4), then, at a step that went behind something, halved four times between the last
// two to find where; if there it is still more than a block behind what the screen shows, it passed behind a thing
// (a pillar, a post) rather than into it, and goes on. (The screen only knows each thing's front: taking everything
// behind it as solid smears thin things into long dark streaks across the water.)
ScreenHit screen_reflect(vec3 view_start, vec3 view_dir, int steps) {
    ScreenHit h;
    h.hit = false;
    h.color = vec3(0.0);
    h.fade = 0.0;
    h.dist = 0.0;
    h.material = MAT_NONE;
    vec2 size = render_size();
    float step_len = 0.3;
    vec3 last = view_start;
    vec3 p = view_start;
    for (int i = 0; i < steps; i++) {
        last = p;
        p += view_dir * step_len;
        vec4 clip = gbufferProjection * vec4(p, 1.0);
        if (clip.w <= 0.0) {
            break;
        }
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
            break;
        }
        ivec2 tx = ivec2(uv * size);
        float d = texelFetch(depthtex1, tx, 0).r;
        if (d < 1.0 && !is_hand_depth(d)) {
            float scene_z = pixel_to_view(tx, d).z;
            float behind = scene_z - p.z;
            if (behind > 0.0) {
                vec3 a = last;
                vec3 b = p;
                for (int j = 0; j < 4; j++) {
                    vec3 m = 0.5 * (a + b);
                    vec4 mc = gbufferProjection * vec4(m, 1.0);
                    ivec2 mt = ivec2((mc.xy / mc.w * 0.5 + 0.5) * size);
                    float md = texelFetch(depthtex1, clamp(mt, ivec2(0), ivec2(size) - 1), 0).r;
                    if (md < 1.0 && pixel_to_view(mt, md).z > m.z) {
                        b = m;
                    } else {
                        a = m;
                    }
                }
                vec4 bc = gbufferProjection * vec4(b, 1.0);
                vec2 buv = clamp(bc.xy / bc.w * 0.5 + 0.5, vec2(0.0), vec2(1.0));
                ivec2 bt = clamp(ivec2(buv * size), ivec2(0), ivec2(size) - 1);
                float bd = texelFetch(depthtex1, bt, 0).r;
                if (bd >= 1.0 || pixel_to_view(bt, bd).z - b.z > 1.0 + step_len / 16.0) {
                    step_len = min(step_len * 1.5, 4.0);
                    continue; // passed behind it
                }
                vec2 edge = min(buv, 1.0 - buv);
                h.hit = true;
                h.fade = smoothstep(0.0, 0.08, min(edge.x, edge.y));
                h.color = textureLod(colortex5, buv * size / window_size(), 0.0).rgb;
                h.dist = distance(b, view_start);
                h.material = int(texelFetch(colortex1, ivec2(buv * size), 0).a * 255.0 + 0.5);
                return h;
            }
        }
        step_len = min(step_len * 1.5, 4.0);
    }
    return h;
}

void main() {
#if WATER_QUALITY == 3
    const int scale = 1;
    const float reach = 96.0;
    const int steps = 128;
#elif WATER_QUALITY == 2
    const int scale = 2;
    const float reach = 56.0;
    const int steps = 80;
#else
    const int scale = 2;
    const float reach = 64.0; // about what 24 looks along the screen reach (lib/reflect.glsl's steps unused)
    const int steps = 0;
#endif
    ivec2 id = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = ivec2(render_size());
    ivec2 out_size = (size + scale - 1) / scale;
    if (any(greaterThanEqual(id, out_size))) {
        return;
    }
    ivec2 texel = id * scale;
    vec4 water = vec4(0.0);
    for (int k = 0; k < scale * scale; k++) {
        ivec2 t = min(id * scale + ivec2(k & 1, k >> 1), size - 1);
        vec4 w = texelFetch(colortex6, t, 0);
        if (w.w > 0.5) {
            texel = t;
            water = w;
            break;
        }
    }
    if (water.w < 0.5) {
        imageStore(colorimg8, id, vec4(0.0));
#if WATER_QUALITY != 3
        imageStore(colorimg8, id + ivec2(out_size.x, 0), vec4(0.0));
#endif
        return;
    }

    vec3 view = pixel_to_view(texel, texelFetch(depthtex0, texel, 0).r);
    vec3 scene = view_to_scene_pos(view);
    float distance_away = length(scene);
    vec3 v = -scene / max(distance_away, 1e-4);
    vec3 n = decode_normal(water.xy);
    vec3 r = reflect(-v, n);
    // Never down into the surface it leaves (a wave's slope can turn a grazing look below the flat water).
    vec3 flat_n = isEyeInWater == 1 ? vec3(0.0, -1.0, 0.0) : vec3(0.0, 1.0, 0.0);
    float into = dot(r, flat_n);
    if (into < 0.03) {
        r = normalize(r + flat_n * (0.03 - into));
    }
    // A pixel's look widens with distance (its size on screen, at the scale it is traced), and more where the waves
    // too fine to see scatter it (the forward pass's spread, see gbuffers.fsh).
    float rough_spread = (1.0 - water.w) / 0.49 * 0.5;
    float spread = pixel_angle() * float(scale) * 1.5 + rough_spread;
    float dist;
    vec3 view_dir = mat3(gbufferModelView) * r;
#if WATER_QUALITY >= 2
    vec3 color = reflect_world(scene + flat_n * 0.02, r, distance_away * spread, spread, reach, steps, dist);
    // What the copy doesn't hold (mobs, Create's machines), from the screen where it is nearer.
    ScreenHit sh = screen_reflect(view, view_dir, 12);
    if (sh.hit && sh.dist < dist && (sh.material == MAT_ENTITY || sh.material == MAT_MACHINE)) {
        color = mix(color, sh.color, sh.fade);
        dist = mix(dist, sh.dist, sh.fade);
    }
#else
    ScreenHit sh = screen_reflect(view, view_dir, 24);
    vec3 color = reflect_sky(r);
    dist = reach;
    if (sh.hit && sh.material != MAT_HAND) {
        color = mix(color, sh.color, sh.fade);
        dist = mix(dist, sh.dist, sh.fade);
    }
    if (isEyeInWater == 1 && !sh.hit) {
        color = vec3(0.0); // under the surface, a look that finds nothing on the screen sees deep water
    }
#endif
    if (isEyeInWater == 1) {
        // From under the surface the mirror shows the water below, through all that water.
        color = water_inside(color, dist, water_light_around(float(eyeBrightnessSmooth.y) / 240.0));
    }
    color = clamp(color, 0.0, 64000.0);

    imageStore(colorimg8, id, vec4(color, dist));
#if WATER_QUALITY != 3
    imageStore(colorimg8, id + ivec2(out_size.x, 0), vec4(-view.z, water.xy, 1.0));
#endif
}

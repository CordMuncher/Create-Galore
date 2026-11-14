// The water's reflection (WATER_QUALITY 1 to 3): for each water pixel the forward pass marked (colortex6), a ray
// reflected off its waves. At High and Ultra it is traced through the block copy (lib/reflect.glsl), so the
// reflection is the same from wherever you look and holds what is off screen too; mobs, Create's moving machines,
// ships, plants and lamps are not in the copy (or not as solid blocks), so a short look along the screen catches them
// where they are nearer than what the trace found. At Medium only that look along the screen (further), and the sky
// with its clouds where it finds nothing. One look per pixel at every quality (a look per 2 x 2 pixels blended back
// up showed as blocks along every edge in the reflection); Ultra reaches further.

#include "/lib/settings.glsl"
#define CLOUDS_NO_READ_BACK
#include "/lib/gbuffer_read.glsl"
#include "/lib/reflect.glsl"
#include "/lib/sky.glsl"
#include "/lib/water.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(1.0, 1.0);

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
    vec3 pos;       // where (view space), just past the front of what it hit
};

// What a look from view_start along view_dir finds on the screen: marched in steps that grow by half each time (the
// first 0.3 blocks, the longest 4), then, at a step that went behind something, halved four times between the last
// two to find where; if there it is still more than a block behind what the screen shows, it passed behind a thing
// (a pillar, a post) rather than into it, and goes on. (The screen only knows each thing's front: taking everything
// behind it as solid smears thin things into long dark streaks across the water.) It goes no further than reach.
ScreenHit screen_reflect(vec3 view_start, vec3 view_dir, int steps, float reach) {
    ScreenHit h;
    h.hit = false;
    h.color = vec3(0.0);
    h.fade = 0.0;
    h.dist = 0.0;
    h.material = MAT_NONE;
    h.pos = view_start;
    vec2 size = render_size();
    float step_len = 0.3;
    vec3 last = view_start;
    vec3 p = view_start;
    float gone = 0.0;
    for (int i = 0; i < steps && gone < reach; i++) {
        last = p;
        p += view_dir * step_len;
        gone += step_len;
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
                h.pos = b;
                return h;
            }
        }
        step_len = min(step_len * 1.5, 4.0);
    }
    return h;
}

// Whether what the look along the screen found is something the block copy doesn't hold, so only the screen can show
// it: mobs, Create's machines, ships, plants and lamps (the copy keeps those out of its solid blocks), and anything
// past the copy's reach. What the copy does hold, its own trace shows the same from wherever you look.
bool screen_only(ScreenHit sh) {
    if (sh.material == MAT_ENTITY || sh.material == MAT_MACHINE) {
        return true;
    }
    ivec3 cell = ivec3(floor(vox_pos(view_to_scene_pos(sh.pos))));
    return !grid_live() || !vox_in_box(cell, 0) || (vox_word(cell) & VOX_SOLID) == 0u;
}

// From under the surface, what the surface mirrors is seen through the water twice: from what the look finds back to
// the surface, and from the surface to you. Past this many blocks in all, under a twentieth of even the blue is left
// (water_extinction_inside), so no look goes further: the far surface costs nothing, the near one a short look.
const float under_see = 48.0;
// And where the surface mirrors less than this share (looking up through it, inside the bright window to the sky),
// the dim floor in it can't be told from deep water.
const float under_least = 0.06;

void main() {
#if WATER_QUALITY == 3
    const float reach = 96.0;
    const int steps = 128;
    const int screen_steps = 24;
#elif WATER_QUALITY == 2
    const float reach = 56.0;
    const int steps = 80;
    const int screen_steps = 24;
#else
    const float reach = 64.0; // about what the looks along the screen reach (lib/reflect.glsl's steps unused)
    const int steps = 0;
    const int screen_steps = 32;
#endif
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(texel, ivec2(render_size())))) {
        return;
    }
    vec4 water = texelFetch(colortex6, texel, 0);
    if (water.w < 0.5) {
        return; // program/fog.fsh reads only water pixels
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
    // A pixel's look widens with distance (its size on screen), and more where the waves too fine to see scatter it
    // (the forward pass's spread, see gbuffers.fsh).
    float rough_spread = (1.0 - water.w) / 0.49 * 0.5;
    float spread = pixel_angle() * 1.5 + rough_spread;
    float dist = reach;
    vec3 view_dir = mat3(gbufferModelView) * r;
    // How far this pixel's look goes (see under_see).
    float look = reach;
    float screen_look = 1e9; // the look along the screen stops at the screen's edge or its last step
    if (isEyeInWater == 1) {
        look = water.z < under_least ? 0.0 : min(reach, under_see - distance_away);
        screen_look = look;
    }
    vec3 color = vec3(0.0);
    if (look > 1.0) {
#if WATER_QUALITY >= 2
        color = reflect_world(scene + flat_n * 0.02, r, distance_away * spread, spread, look, steps, dist);
        if (dist >= look) {
            dist = reach;
            if (isEyeInWater == 1) {
                color = vec3(0.0); // under the surface, a look that finds nothing sees deep water
            }
        }
        // What the copy doesn't hold (see screen_only), from the screen where it is nearer.
        ScreenHit sh = screen_reflect(view, view_dir, screen_steps, screen_look);
        if (sh.hit && sh.dist < dist && screen_only(sh)) {
            color = mix(color, sh.color, sh.fade);
            dist = mix(dist, sh.dist, sh.fade);
        }
#else
        ScreenHit sh = screen_reflect(view, view_dir, screen_steps, screen_look);
        if (isEyeInWater != 1) {
            color = reflect_sky(r, scene, spread); // under the surface, a look that finds nothing sees deep water
        }
        if (sh.hit && sh.material != MAT_HAND) {
            color = mix(color, sh.color, sh.fade);
            dist = mix(dist, sh.dist, sh.fade);
        }
#endif
    }
    if (isEyeInWater == 1) {
        // From under the surface the mirror shows the water below, through all that water.
        color = water_inside(color, dist, water_light_around(float(eyeBrightnessSmooth.y) / 240.0));
    }
    color = clamp(color, 0.0, 64000.0);

    imageStore(colorimg8, texel, vec4(color, dist));
}

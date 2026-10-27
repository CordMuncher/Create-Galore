// The overworld's deferred pass, third part: what the screen shows around each surface (see deferred.fsh, which
// lights it). How open the surface is to the light all around (its ambient occlusion), and the light the surfaces
// close around it bounce onto it, from last frame's picture: Therrien, Levesque and Gilet's visibility bitmask
// (2023), which takes GTAO's slices across the screen (Jimenez et al. 2016, as Intel's XeGTAO lays them out) and
// gives every surface along them a thickness, so light passes behind a fence post or a lamp post, and what blocks
// each part of the sky over the surface sends its own light there instead.
//
// One sample per direction and step for each pixel and frame, turned and moved by noise; the lighting pass blends
// them over frames before DLSS or FSR see them (see deferred.fsh).
//
// colortex13 (RGBA16F): rgb the bounced light (kcd/m², as a share of the light all around: a surface's own colour
// times it is the light it sends on), a how open the surface is (1 all of it); 0, 0, 0 and 1 where none is worked
// out (the sky, plants, the hand, Distant Horizons' terrain).
//
// And the share of the sky the sky grid's rays from each surface see (see sky_seen), a few rays each pixel and frame,
// which the lighting pass blends over frames too: colortex15's r (-1 where it traces none: Minecraft's sky light is
// full there, or the grid doesn't reach), its g last frame's blended share, passed on for the lighting pass.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"
#include "/lib/sky_grid.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D depthtex0;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
#ifdef LIT_SKY_GRID
uniform sampler2D colortex15;
#endif

in vec2 v_uv;

#ifdef LIT_SKY_GRID
/* RENDERTARGETS: 13,15 */
layout(location = 0) out vec4 out_screen;
layout(location = 1) out vec4 out_sky;
#else
/* RENDERTARGETS: 13 */
layout(location = 0) out vec4 out_screen;
#endif

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

#if AO_QUALITY >= 3
const int screen_directions = 4;
const int screen_steps = 8;
#elif AO_QUALITY == 2
const int screen_directions = 2;
const int screen_steps = 6;
#else
const int screen_directions = 1;
const int screen_steps = 5;
#endif
// How far around a surface it looks (blocks): what lies further off counts less from 40% of it out. Far enough that
// a room's corners and the floor under a ledge are clearly darker, and lamp light in them less.
const float screen_radius = 2.25;
// How thick whatever the screen shows is taken to be behind its front (blocks): a block's depth, a little more far
// off, where a pixel covers more.
const float screen_thickness = 0.75;
const float screen_thickness_far = 0.01; // per block away
// The bounced light fades out from this far (blocks), where it would only be a pixel or two and last frame's picture
// holds the air in front of it.
const vec2 screen_bounce_fade = vec2(32.0, 96.0);

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

// Each slice is the half circle over the surface (its normal at n from the eye, along the slice), in 32 equal
// sectors from n - π/2 to n + π/2; a bit set: something blocks that sector.
const float sector_angle = PI / 32.0;

// The sectors a span of angles from the eye (lo..hi) covers at least half of.
uint sector_mask(float lo, float hi, float n) {
    int a = int(round(clamp((lo - n) / PI + 0.5, 0.0, 1.0) * 32.0));
    int b = int(round(clamp((hi - n) / PI + 0.5, 0.0, 1.0) * 32.0));
    if (b <= a) {
        return 0u;
    }
    return b - a >= 32 ? 0xFFFFFFFFu : ((1u << uint(b - a)) - 1u) << uint(a);
}

// The share of the light all around (by its cosine on the surface) that comes in along a slice between the eye and
// angle h (signed, as n): GTAO's integral, from 0 out to h.
float slice_share(float h, float n, float cos_n, float sin_n) {
    return sign(h) * (0.25 * cos_n - 0.25 * cos(2.0 * h - n) + 0.5 * h * sin_n);
}

// The share of a slice's light the set sectors hold, run by run.
float sectors_share(uint bits, float n, float cos_n, float sin_n) {
    float share = 0.0;
    while (bits != 0u) {
        int a = findLSB(bits);
        uint rest = ~(bits >> uint(a));
        int len = rest == 0u ? 32 - a : findLSB(rest);
        float h0 = n - 0.5 * PI + float(a) * sector_angle;
        share += slice_share(h0 + float(len) * sector_angle, n, cos_n, sin_n) - slice_share(h0, n, cos_n, sin_n);
        bits &= len >= 32 ? 0u : ~(((1u << uint(len)) - 1u) << uint(a));
    }
    return share;
}

#ifdef SCREEN_BOUNCE
// The light a surface the screen shows at texel (view space view, seen from the lit surface along to, a unit vector
// in view space) sends towards that surface: what last frame's picture showed there (the same either way, for the
// matt surfaces most blocks are), without what it gives off by itself (the block light already brings a lamp's
// light, see lighting.glsl). kcd/m².
vec3 sent_light(ivec2 texel, vec3 view, vec3 to) {
    vec4 surface = texelFetch(colortex2, texel, 0);
    int material = decode_material(surface);
    if (material == MATERIAL_NONE || material == MATERIAL_SELF) {
        return vec3(0.0);
    }
    // Only its lit side, towards the surface (a plant's either).
    float facing = material == MATERIAL_PLANT ? 1.0
            : clamp(-dot(decode_normal(surface.rg), mat3(gbufferModelViewInverse) * to) * 5.0, 0.0, 1.0);
    if (facing <= 0.0) {
        return vec3(0.0);
    }
    vec4 clip = previous_clip(view_to_scene(view), gbufferPreviousProjection);
    if (clip.w <= 0.0) {
        return vec3(0.0);
    }
    vec2 size = render_size();
    vec2 prev = (clip.xy / clip.w * 0.5 + 0.5) * size;
    if (any(lessThan(prev, vec2(0.5))) || any(greaterThan(prev, size - 0.5))) {
        return vec3(0.0);
    }
    vec3 seen = textureLod(colortex3, prev / window_size(), 0.0).rgb - texelFetch(colortex0, texel, 0).rgb;
    return max(seen, 0.0) * (facing / exposure());
}
#endif

// What the screen shows around a surface at view (view space, unjittered) facing normal (view space): rgb the light
// the surfaces around it bounce onto it, a how open it is (see colortex13). noise_dir, noise_step: 0..1, different
// for each pixel and frame, which turn the slices and move the steps.
vec4 screen_light(ivec2 texel, vec3 view, vec3 normal, float noise_dir, float noise_step) {
    vec2 size = render_size();
    // The radius in pixels, at most a quarter of the screen (very close up).
    float pixels = min(screen_radius * gbufferProjection[1][1] * 0.5 * size.y / -view.z, 0.25 * size.y);
    if (pixels < 2.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    vec2 jitter = vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec3 v = normalize(-view);
    float dist = length(view);
    float thickness = screen_thickness + screen_thickness_far * dist;
    const float falloff_range = 0.6 * screen_radius;
    const float falloff_mul = -1.0 / falloff_range;
    const float falloff_add = (screen_radius - falloff_range) / falloff_range + 1.0;
#ifdef SCREEN_BOUNCE
    bool bounce = dist < screen_bounce_fade.y;
#endif
    vec3 bounced = vec3(0.0);
    float visibility = 0.0;
    for (int d = 0; d < screen_directions; d++) {
        float phi = (float(d) + noise_dir) * PI / float(screen_directions);
        vec2 omega = vec2(cos(phi), sin(phi));
        // The plane through the eye along this direction, and the normal's shadow on it: its length weighs the slice,
        // its angle from the eye is n.
        vec3 direction = vec3(omega, 0.0);
        vec3 ortho = direction - dot(direction, v) * v;
        vec3 axis = normalize(cross(ortho, v));
        vec3 projected = normal - axis * dot(normal, axis);
        float projected_length = length(projected);
        if (projected_length < 1e-4) {
            continue;
        }
        float cos_n = clamp(dot(projected, v) / projected_length, 0.0, 1.0);
        float n = (dot(ortho, projected) >= 0.0 ? 1.0 : -1.0) * acos(cos_n);
        float sin_n = sin(n);
        // Where the half circle meets the surface's own plane on either side (as cosines from the eye): far samples
        // fade towards it.
        float low_cos0 = cos(n + 0.5 * PI);
        float low_cos1 = cos(n - 0.5 * PI);
        uint blocked = 0u;
        for (int i = 0; i < screen_steps; i++) {
            float t = (float(i) + noise_step) / float(screen_steps);
            // More of the steps close in, the first at least a pixel and a bit away.
            vec2 offset = omega * (t * t * pixels + 1.3);
            for (int side = 0; side < 2; side++) {
                ivec2 at = texel + ivec2(round(side == 0 ? offset : -offset));
                if (any(lessThan(at, ivec2(0))) || any(greaterThanEqual(vec2(at), size))) {
                    continue;
                }
                float depth = texelFetch(depthtex0, at, 0).r;
                if (depth < hand_depth_limit || depth >= 1.0) {
                    continue; // the hand and the sky hide nothing
                }
                vec3 front = unproject(gbufferProjectionInverse, (vec2(at) + 0.5 - jitter) / size * 2.0 - 1.0, depth);
                vec3 delta = front - view;
                float len = length(delta);
                float weight = clamp(len * falloff_mul + falloff_add, 0.0, 1.0);
                if (weight <= 0.0) {
                    continue;
                }
                // The span it covers: from its front to its back, a thickness further from the eye.
                vec3 back = delta + normalize(front) * thickness;
                float low = side == 0 ? low_cos0 : low_cos1;
                float c_front = mix(low, dot(delta, v) / max(len, 1e-4), weight);
                float c_back = mix(low, dot(back, v) / max(length(back), 1e-4), weight);
                float h_front = acos(clamp(c_front, -1.0, 1.0));
                float h_back = acos(clamp(c_back, -1.0, 1.0));
                if (side == 1) {
                    h_front = -h_front;
                    h_back = -h_back;
                }
                uint covered = sector_mask(min(h_front, h_back), max(h_front, h_back), n);
                uint fresh = covered & ~blocked;
                if (fresh == 0u) {
                    continue;
                }
#ifdef SCREEN_BOUNCE
                // What it sends, over the sectors nothing nearer has blocked yet.
                if (bounce) {
                    bounced += projected_length * sectors_share(fresh, n, cos_n, sin_n) * sent_light(at, front, delta / max(len, 1e-4));
                }
#endif
                blocked |= covered;
            }
        }
        visibility += projected_length * (cos_n + n * sin_n - sectors_share(blocked, n, cos_n, sin_n));
    }
#ifdef SCREEN_BOUNCE
    bounced *= 1.0 - smoothstep(screen_bounce_fade.x, screen_bounce_fade.y, dist);
#endif
    return vec4(bounced, clamp(visibility, 0.0, float(screen_directions))) / float(screen_directions);
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    out_screen = vec4(0.0, 0.0, 0.0, 1.0);
#ifdef LIT_SKY_GRID
    out_sky = vec4(-1.0, texelFetch(colortex15, texel, 0).g, 0.0, 0.0);
#endif
    if (any(greaterThanEqual(vec2(texel) + 0.5, render_size()))) {
        return; // outside the frame while upscaling
    }
    float depth = texelFetch(depthtex0, texel, 0).r;
    vec4 surface = texelFetch(colortex2, texel, 0);
    int material = decode_material(surface);
    if (depth < hand_depth_limit || depth >= 1.0 || material == MATERIAL_NONE || material == MATERIAL_SELF) {
        return;
    }
    vec2 point = vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec3 view = unproject(gbufferProjectionInverse, point / render_size() * 2.0 - 1.0, depth);
    vec3 normal = decode_normal(surface.rg);
#ifdef LIT_SKY_GRID
    // Where Minecraft's sky light is less than full (see deferred.fsh); a plant's from above.
    vec3 scene_pos = view_to_scene(view);
    if (decode_light(surface).y < 0.999 && max(abs(scene_pos.x), abs(scene_pos.z)) < sky_grid_reach) {
        out_sky.r = sky_seen(scene_pos, material == MATERIAL_PLANT ? vec3(0.0, 1.0, 0.0) : normal, pixel_noise(2u));
    }
#endif
#if AO_QUALITY > 0
    if (material == MATERIAL_LIT) {
        out_screen = screen_light(texel, view, mat3(gbufferModelView) * normal, pixel_noise(), pixel_noise(1u));
    }
#endif
}

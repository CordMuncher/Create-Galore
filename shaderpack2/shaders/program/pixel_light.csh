// Coloured block light traced per pixel (the sharp layer): crisp contact shadows from a torch behind a fence post,
// light through chain links, stained glass tinting a wall, and a highlight from every nearby light on smooth
// surfaces. One light sample per PIXEL_LIGHT_SCALE x PIXEL_LIGHT_SCALE pixels, each frame at a different pixel of
// its square (so over a few frames every pixel gets one); program/pixel_light_filter.csh blends them back up.
//
// One work group per 16 x 16 samples shares one list of lights. Candidates: its own list from last frame and its
// neighbours' (the four next to it and the four four tiles away), the per-block list of the blocks it looks at,
// and one discovery walk per sample in a random direction off its surface, up to 30 blocks: a light it runs into
// joins. They are weighed over the box its surfaces span, sorted, and the 64 strongest kept. Then each sample, for
// each light in reach that its surface faces, up to PIXEL_LIGHT_TRACES of them: a cone trace through the distance
// field (the light's size over its distance: near lights give soft shadows, far ones sharp), a walk to a random
// point on the light for the colour of any glass on the way, and a GGX highlight. The lights that lit something
// become next frame's list. The held light (HAND_LIGHT) is always looked at.

#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/lighting.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
// One thread per sample: the window's size over the scale (Iris divides by the work group's size itself).
#if PIXEL_LIGHT_SCALE == 1
const vec2 workGroupsRender = vec2(1.0, 1.0);
#elif PIXEL_LIGHT_SCALE == 2
const vec2 workGroupsRender = vec2(0.5, 0.5);
#elif PIXEL_LIGHT_SCALE == 4
const vec2 workGroupsRender = vec2(0.25, 0.25);
#elif PIXEL_LIGHT_SCALE == 6
const vec2 workGroupsRender = vec2(0.16667, 0.16667);
#else
const vec2 workGroupsRender = vec2(0.33334, 0.33334);
#endif

uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;

// Diffuse (compressed; a: which pixel of the square + 1) in the left half, highlights (compressed) in the right.
layout(rgba16f) writeonly uniform image2D cg_pl_img;

// Each screen tile's list (in cg_data_img, see lib/data.glsl): its count, then up to 64 keys.
const uint screen_list_max = 64u;
const uint screen_list_stride = screen_list_max + 1u;

const int candidates_max = 256;
const int set_size = 512;
const float discovery_reach = 30.0;

shared uint s_set[set_size];
shared uint s_count;
shared uint s_key[candidates_max];
shared float s_score[candidates_max];
shared uint s_order[candidates_max];
shared uint s_lo[3];
shared uint s_hi[3];
shared uint s_any;
shared uint s_list_count;
shared vec3 s_pos[screen_list_max];
shared vec3 s_color[screen_list_max];
shared float s_reach[screen_list_max];
shared float s_radius[screen_list_max];
shared uint s_contribution[screen_list_max];

// Floats as uints that sort the same way (for atomicMin and atomicMax).
uint float_order(float f) {
    uint u = floatBitsToUint(f);
    return (u & 0x80000000u) != 0u ? ~u : u | 0x80000000u;
}

float order_float(uint u) {
    return uintBitsToFloat((u & 0x80000000u) != 0u ? u & 0x7fffffffu : ~u);
}

void add_candidate(uint key) {
    uint h = pcg3d(uvec3(key, 9u, 2u)).x & uint(set_size - 1);
    for (int i = 0; i < 16; i++) {
        uint was = atomicCompSwap(s_set[h], 0u, key + 1u);
        if (was == 0u) {
            uint at = atomicAdd(s_count, 1u);
            if (at < uint(candidates_max)) {
                s_key[at] = key;
            }
            return;
        }
        if (was == key + 1u) {
            return;
        }
        h = (h + 1u) & uint(set_size - 1);
    }
}

// Whether a tile's list fits the buffer (a window far over 4K at full detail has more tiles than it holds; those
// tiles go without one).
bool list_fits(uint base) {
    return base + screen_list_stride <= data_screen_lists_base + data_screen_lists_size;
}

void add_list(uint base) {
    if (!list_fits(base)) {
        return;
    }
    uint count = min(data_load(base), screen_list_max);
    for (uint j = 0u; j < count; j++) {
        add_candidate(data_load(base + 1u + j));
    }
}

void sort_candidates(uint index) {
    for (uint size = 2u; size <= uint(candidates_max); size <<= 1u) {
        for (uint stride = size >> 1u; stride > 0u; stride >>= 1u) {
            uint other = index ^ stride;
            if (other > index) {
                bool descending = (index & size) == 0u;
                uint a = s_order[index];
                uint b = s_order[other];
                if ((s_score[a] < s_score[b]) == descending) {
                    s_order[index] = b;
                    s_order[other] = a;
                }
            }
            barrier();
        }
    }
}

// The light that one point gets from a light at to, of colour (at a block) and reach, through a cone of radius at
// the light; adds its highlight to spec.
vec3 light_from(vec3 p, vec3 n, vec3 v, float smoothness, bool thin, vec3 to, vec3 color, float reach, float radius,
                inout vec3 spec, inout int traces, vec3 jitter) {
    vec3 to_light = to - p;
    float d = length(to_light);
    if (d >= reach || d < 1e-3) {
        return vec3(0.0);
    }
    vec3 dir = to_light / d;
    float facing = dot(n, dir);
    if (thin) {
        facing = abs(facing) * 0.8; // plants and leaves let light through
    }
    if (facing <= 0.0) {
        return vec3(0.0);
    }
    vec3 c = color * light_falloff(d, reach);
    if (max3(c) * facing < 1e-4) {
        return vec3(0.0);
    }
    traces++;
    float visible = cone_visibility(p, dir, d, 0.0, radius, 0.5, 64);
    if (visible <= 0.0) {
        return vec3(0.0);
    }
    vec3 aim = to + jitter * radius - p;
    float aim_d = length(aim);
    c *= visible * see_through_tint(p, aim / aim_d, aim_d);
#ifdef LIGHT_SPECULAR
    spec += c * ggx_specular(n, v, dir, smoothness, 0.04);
#endif
    return c * facing;
}

void main() {
    uint index = gl_LocalInvocationIndex;
    ivec2 sample_texel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 tile = ivec2(gl_WorkGroupID.xy);
    ivec2 tiles = ivec2(gl_NumWorkGroups.xy);
    uint own_base = data_screen_lists_base + uint(tile.x + tile.y * tiles.x) * screen_list_stride;
    const int scale = PIXEL_LIGHT_SCALE;

    if (index == 0u) {
        s_count = 0u;
        s_list_count = 0u;
        s_any = 0u;
        for (int i = 0; i < 3; i++) {
            s_lo[i] = 0xffffffffu;
            s_hi[i] = 0u;
        }
    }
    for (uint i = index; i < uint(set_size); i += 256u) {
        s_set[i] = 0u;
    }
    barrier();

    // This sample's pixel: a different one of its square each frame (7 shares no factor with any square's size).
    uint turn = (uint(frameCounter) * 7u + pcg3d(uvec3(uvec2(sample_texel), 5u)).x % 64u) % uint(scale * scale);
    ivec2 pixel = sample_texel * scale + ivec2(int(turn) % scale, int(turn) / scale);
    bool has_surface = !outside_render_area(vec2(pixel) + 0.5);
    float depth = has_surface ? texelFetch(depthtex0, pixel, 0).r : 1.0;
    has_surface = has_surface && depth < 1.0;

    Surface s;
    vec3 p = vec3(0.0);
    vec3 v = vec3(0.0, 1.0, 0.0);
    if (has_surface) {
        s = read_surface(pixel);
        vec3 view = pixel_to_view(pixel, depth);
        vec3 scene = view_to_scene_pos(view);
        v = normalize(-scene);
        bool hand = s.material == MAT_HAND;
        p = vox_pos(scene) + s.face * (hand ? 0.15 : 0.04);
        atomicMin(s_lo[0], float_order(p.x));
        atomicMin(s_lo[1], float_order(p.y));
        atomicMin(s_lo[2], float_order(p.z));
        atomicMax(s_hi[0], float_order(p.x));
        atomicMax(s_hi[1], float_order(p.y));
        atomicMax(s_hi[2], float_order(p.z));
        atomicOr(s_any, 1u);
    }
    barrier();
    if (s_any == 0u) {
        if (index == 0u && list_fits(own_base)) {
            data_store(own_base, 0u);
        }
        imageStore(cg_pl_img, sample_texel, vec4(0.0));
        imageStore(cg_pl_img, sample_texel + ivec2(imageSize(cg_pl_img).x / 2, 0), vec4(0.0));
        return;
    }
    vec3 lo = vec3(order_float(s_lo[0]), order_float(s_lo[1]), order_float(s_lo[2]));
    vec3 hi = vec3(order_float(s_hi[0]), order_float(s_hi[1]), order_float(s_hi[2]));

    // Candidates.
    if (index == 0u) {
        add_list(own_base);
    } else if (index <= 8u) {
        int k = int(index) - 1;
        ivec2 o = k < 4 ? ivec2(k == 0 ? 1 : k == 1 ? -1 : 0, k == 2 ? 1 : k == 3 ? -1 : 0)
                        : ivec2(k == 4 ? 4 : k == 5 ? -4 : 0, k == 6 ? 4 : k == 7 ? -4 : 0);
        ivec2 t = tile + o;
        if (all(greaterThanEqual(t, ivec2(0))) && all(lessThan(t, tiles))) {
            add_list(data_screen_lists_base + uint(t.x + t.y * tiles.x) * screen_list_stride);
        }
    } else if (index == 9u) {
        // The per-block list where this tile looks.
        ivec3 block_tile;
        if (block_tile_of((lo + hi) * 0.5, block_tile)) {
            uint base = block_tile_index(block_tile);
            uint count = min(data_load(base), tile_list_max);
            for (uint j = 0u; j < count; j++) {
                add_candidate(data_load(base + 1u + j));
            }
        }
    }
    if (has_surface) {
        vec3 u = hash33(uvec3(uvec2(pixel), uint(frameCounter) * 3u + 1u));
        vec3 dir = cosine_direction(s.face, u.xy);
        uint key;
        if (discover_light(p, dir, discovery_reach, key)) {
            add_candidate(key);
        }
    }
    barrier();

    // Weighed and sorted.
    uint candidates = min(s_count, uint(candidates_max));
    float score = -1.0;
    if (index < candidates) {
        Light l = light_from_key(s_key[index]);
        score = l.valid ? light_score(l, lo, hi) : 0.0;
    }
    s_score[index] = score;
    s_order[index] = index;
    barrier();
    sort_candidates(index);
    if (index < screen_list_max) {
        uint k = s_order[index];
        if (s_score[k] > 0.0) {
            Light l = light_from_key(s_key[k]);
            s_pos[index] = l.pos;
            s_color[index] = l.color;
            s_reach[index] = l.reach;
            // Half a block, less where the light sits in a tight spot (its shadows are then sharper).
            EmptyCube c = empty_cube(l.pos);
            vec3 room = min(l.pos - c.lo, c.hi - l.pos);
            float space = c.level < 0 ? 1.0 : max(min(room.x, min(room.y, room.z)), 0.01);
            s_radius[index] = 0.5 * min(1.0, space) * 0.6;
            s_contribution[index] = 0u;
            atomicMax(s_list_count, index + 1u);
        }
    }
    barrier();

    // This sample's light.
    vec3 diffuse = vec3(0.0);
    vec3 spec = vec3(0.0);
    if (has_surface) {
        bool thin = s.material == MAT_PLANT || s.material == MAT_LEAVES;
        int traces = 0;
        vec3 jitter = hash33(uvec3(uvec2(pixel), uint(frameCounter) * 3u + 2u)) * 2.0 - 1.0;
#ifdef HAND_LIGHT
        float held = float(max(heldBlockLightValue, heldBlockLightValue2));
        if (held > 0.0) {
            vec3 hand_scene = mat3(gbufferModelViewInverse) * vec3(0.25, -0.25, -0.35) + gbufferModelViewInverse[3].xyz;
            float level = max(10.0, 31.0 * held / 20.0);
            vec4 torch = light_kind(0);
            diffuse += light_from(p, s.normal, v, s.smoothness, thin, vox_pos(hand_scene), torch.rgb * light_brightness(level),
                                  light_reach(level), 0.12, spec, traces, jitter);
        }
#endif
        uint lights = s_list_count;
        for (uint j = 0u; j < lights && traces < PIXEL_LIGHT_TRACES; j++) {
            vec3 c = light_from(p, s.normal, v, s.smoothness, thin, s_pos[j], s_color[j], s_reach[j], s_radius[j],
                                spec, traces, jitter);
            if (max3(c) > 0.0) {
                diffuse += c;
                atomicMax(s_contribution[j], floatBitsToUint(max3(c)));
            }
        }
        imageStore(cg_pl_img, sample_texel, vec4(compress_light(diffuse), float(turn) + 1.0));
        imageStore(cg_pl_img, sample_texel + ivec2(imageSize(cg_pl_img).x / 2, 0), vec4(compress_light(spec), 1.0));
    } else {
        imageStore(cg_pl_img, sample_texel, vec4(0.0));
        imageStore(cg_pl_img, sample_texel + ivec2(imageSize(cg_pl_img).x / 2, 0), vec4(0.0));
    }
    barrier();

    // The lights that lit something here, for next frame.
    if (index == 0u && list_fits(own_base)) {
        uint n = 0u;
        for (uint j = 0u; j < s_list_count; j++) {
            if (uintBitsToFloat(s_contribution[j]) > 1e-4) {
                data_store(own_base + 1u + n, s_key[s_order[j]]);
                n++;
            }
        }
        data_store(own_base, n);
    }
}

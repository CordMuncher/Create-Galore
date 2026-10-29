// Reading the voxel copy of the world (see lib/voxel.glsl) and tracing rays through it.
//
// Each detail level has a distance field (cg_sdf_img, program/sdf.csh): per cell, how many cells away the nearest
// solid one is, counted the way a cube grows (the largest cube of empty cells around it). A ray leaps across that
// cube in one step, so empty space costs almost nothing, and a lookup uses the finest level that holds the point,
// so detail falls off with distance by itself. Three ways through:
// - trace_ray: to the first solid cell (bounce light, reflections);
// - cone_visibility: how much of a light gets past, softly, from the narrowest gap the ray squeezes through
//   against how wide the cone is there (one ray, no extra samples);
// - see_through_tint: a walk block by block to the first see-through block (stained glass, water), whose colour
//   tints the light.

#if !defined LIB_TRACE
#define LIB_TRACE

#include "/lib/voxel.glsl"

layout(r32ui) uniform uimage3D cg_vox_img;
layout(r8ui) uniform uimage3D cg_sdf_img;

// The distance field's texel for a slot at a level: the levels stack up the y axis.
ivec3 sdf_texel(ivec3 slot, int level) {
    return ivec3(slot.x, slot.y + level * vox_size.y, slot.z);
}

uint vox_word(ivec3 cell) {
    return imageLoad(cg_vox_img, vox_slot(cell, 0)).r;
}

uint sdf_at(ivec3 cell, int level) {
    return imageLoad(cg_sdf_img, sdf_texel(vox_slot(cell, level), level)).r;
}

// A block's average colour (linear) and opacity, from its cell (level 0); grey where nothing was drawn.
vec4 vox_color(ivec3 cell) {
    ivec3 slot = vox_slot(cell, 0);
    uint count = min(imageLoad(cg_vox_img, slot).r >> VOX_COUNT_SHIFT, 250u);
    if (count == 0u) {
        return vec4(0.35, 0.35, 0.35, 1.0);
    }
    uint rg = imageLoad(cg_vox_img, slot + vox_rg_offset).r;
    uint ba = imageLoad(cg_vox_img, slot + vox_ba_offset).r;
    vec4 c = vec4(float(rg & 0xffffu), float(rg >> 16u), float(ba & 0xffffu), float(ba >> 16u)) / (255.0 * float(count));
    return vec4(srgb_to_linear(c.rgb), c.a);
}

// The sky light a block's faces saw (0..1, in thirds), from its cell's 2 bits.
float vox_sky(uint word) {
    uint g = (word >> VOX_SKY_SHIFT) & 3u;
    return g == 0u ? 0.0 : g == 1u ? 1.0 / 3.0 : g == 3u ? 2.0 / 3.0 : 1.0;
}

// The empty cube around a point (vox_pos space): its corners, and the level it was read at. Empty when the cell
// itself is solid (lo > hi). Level -1 outside every box.
struct EmptyCube {
    vec3 lo;
    vec3 hi;
    int level;
    bool solid;
};

EmptyCube empty_cube(vec3 p) {
    EmptyCube c;
    c.level = vox_level(p);
    c.solid = false;
    c.lo = p;
    c.hi = p;
    if (c.level < 0) {
        return c;
    }
    ivec3 cell = vox_cell(p, c.level);
    int d = int(sdf_at(cell, c.level));
    float s = exp2(float(-c.level));
    c.solid = d == 0;
    c.lo = vec3(cell - d + 1) * s;
    c.hi = vec3(cell + d) * s;
    return c;
}

// How far along dir a ray from p inside a box leaves it.
float box_exit(vec3 p, vec3 inv_dir, vec3 lo, vec3 hi) {
    vec3 t = (mix(lo, hi, step(0.0, inv_dir)) - p) * inv_dir;
    return max(min(t.x, min(t.y, t.z)), 0.0);
}

vec3 safe_inverse(vec3 dir) {
    return 1.0 / mix(dir, vec3(1e-6), lessThan(abs(dir), vec3(1e-6)));
}

struct Hit {
    bool hit;
    float t;
    vec3 pos;    // vox_pos space
    int level;
};

// To the first solid cell along a ray from p (vox_pos space), up to max_t blocks. A miss leaves the box (or runs
// out of steps) with hit false and t where it stopped.
Hit trace_ray(vec3 p, vec3 dir, float max_t, int max_steps) {
    Hit h;
    h.hit = false;
    h.level = -1;
    vec3 inv_dir = safe_inverse(dir);
    float t = 0.0;
    for (int i = 0; i < max_steps; i++) {
        vec3 q = p + dir * t;
        EmptyCube c = empty_cube(q);
        if (c.level < 0) {
            break;
        }
        if (c.solid) {
            h.hit = true;
            h.level = c.level;
            break;
        }
        t += box_exit(q, inv_dir, c.lo, c.hi) + exp2(float(-c.level)) * 0.01;
        if (t >= max_t) {
            t = max_t;
            break;
        }
    }
    h.t = t;
    h.pos = p + dir * t;
    return h;
}

// How much of a light at distance dist along dir gets to p (0..1): the narrowest the empty space around the ray
// gets, against the cone's radius there, which runs from width_start at p to width_end at the light (the lamp's
// size). The empty space is the distance field's, from the middle of the cell the ray is in (a cell next to a solid
// one has half a cell), not from where the ray happens to be in it: the ray always enters a cell at its wall, which
// says nothing about how near the solid blocks are. The last stop_short blocks before the light are its own.
float cone_visibility(vec3 p, vec3 dir, float dist, float width_start, float width_end, float stop_short,
                      int max_steps) {
    vec3 inv_dir = safe_inverse(dir);
    float visible = 1.0;
    float t = 0.0;
    float end = dist - stop_short;
    for (int i = 0; i < max_steps && t < end; i++) {
        vec3 q = p + dir * t;
        int level = vox_level(q);
        if (level < 0) {
            break;
        }
        ivec3 cell = vox_cell(q, level);
        int d = int(sdf_at(cell, level));
        if (d == 0) {
            return 0.0;
        }
        float s = exp2(float(-level));
        float free_space = (float(d) - 0.5) * s;
        float width = mix(width_start, width_end, clamp(t / dist, 0.0, 1.0));
        visible = min(visible, free_space / (width + 0.02));
        vec3 lo = vec3(cell - d + 1) * s;
        vec3 hi = vec3(cell + d) * s;
        t += box_exit(q, inv_dir, lo, hi) + s * 0.01;
    }
    return smoothstep(0.0, 1.0, clamp(visible, 0.0, 1.0));
}

// The colour of the first see-through block (glass, water) between p and a point dist away along dir, walking the
// blocks one by one; white when there is none. The walk starts outside p's own block.
vec3 see_through_tint(vec3 p, vec3 dir, float dist) {
    ivec3 cell = ivec3(floor(p));
    ivec3 step_dir = ivec3(sign(dir));
    vec3 inv_dir = safe_inverse(dir);
    vec3 next = (vec3(cell) + step(0.0, dir) - p) * inv_dir; // to the next boundary on each axis
    vec3 delta = abs(inv_dir);
    float end = dist - 0.5;
    for (int i = 0; i < 64; i++) {
        float t;
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += delta.x;
            cell.x += step_dir.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += delta.y;
            cell.y += step_dir.y;
        } else {
            t = next.z;
            next.z += delta.z;
            cell.z += step_dir.z;
        }
        if (t >= end || !vox_in_box(cell)) {
            break;
        }
        uint word = vox_word(cell);
        if ((word & vox_see_bit(0)) != 0u) {
            vec4 c = vox_color(cell);
            vec3 hue = c.rgb / max(max3(c.rgb), 0.05);
            return mix(vec3(1.0), hue, clamp(c.a * 1.5, 0.0, 1.0)) * mix(1.0, max3(c.rgb), 0.5);
        }
    }
    return vec3(1.0);
}

// A walk block by block from p along dir, up to dist blocks, to the first block that holds something: if it is a
// light, its key (true). How the screen's light lists find lights nobody told them about.
bool discover_light(vec3 p, vec3 dir, float dist, out uint key) {
    key = 0u;
    ivec3 cell = ivec3(floor(p));
    ivec3 step_dir = ivec3(sign(dir));
    vec3 inv_dir = safe_inverse(dir);
    vec3 next = (vec3(cell) + step(0.0, dir) - p) * inv_dir;
    vec3 delta = abs(inv_dir);
    for (int i = 0; i < 96; i++) {
        float t;
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += delta.x;
            cell.x += step_dir.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += delta.y;
            cell.y += step_dir.y;
        } else {
            t = next.z;
            next.z += delta.z;
            cell.z += step_dir.z;
        }
        if (t >= dist || !vox_in_box(cell)) {
            return false;
        }
        uint word = vox_word(cell);
        if ((word & VOX_LIGHT) != 0u) {
            key = light_key(cameraPositionInt + cell);
            return true;
        }
        if ((word & vox_solid_bit(0)) != 0u) {
            return false;
        }
    }
    return false;
}

// The direction a surface near p faces, from how the distance field (level 0) grows around it; zero in the open.
vec3 vox_normal(ivec3 cell) {
    float dx = float(sdf_at(cell + ivec3(1, 0, 0), 0)) - float(sdf_at(cell - ivec3(1, 0, 0), 0));
    float dy = float(sdf_at(cell + ivec3(0, 1, 0), 0)) - float(sdf_at(cell - ivec3(0, 1, 0), 0));
    float dz = float(sdf_at(cell + ivec3(0, 0, 1), 0)) - float(sdf_at(cell - ivec3(0, 0, 1), 0));
    vec3 g = vec3(dx, dy, dz);
    return dot(g, g) > 0.01 ? normalize(g) : vec3(0.0);
}

#endif

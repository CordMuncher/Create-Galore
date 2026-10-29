// Reading the voxel copy of the world (see lib/voxel.glsl) and tracing rays through it.
//
// Each detail level has a distance field (cg_sdf_img, program/sdf.csh): per cell, how many cells away the nearest
// solid one is, counted the way a cube grows (the largest cube of empty cells around it). A ray leaps across that
// cube in one step, so empty space costs almost nothing, and a lookup uses the finest level that holds the point,
// so detail falls off with distance by itself. How much room a ray has (for soft shadows) comes from the same field
// read blended between cells, so it grows smoothly with the distance from a surface at every level, instead of in
// steps of a cell (which showed each level's cells up close, and made the finest level, around you, darker than the
// rest). Three ways through:
// - trace_ray: to the first solid cell (bounce light, reflections);
// - cone_visibility: how much of a light gets past, softly, from the narrowest gap the ray squeezes through
//   against how wide the cone is there (one ray, no extra samples);
// - see_through_tint: a walk block by block to the first see-through block (stained glass, water), whose colour
//   tints the light.

#if !defined LIB_TRACE
#define LIB_TRACE

#include "/lib/voxel.glsl"

layout(r32ui) uniform uimage3D cg_vox_img;
layout(r8) uniform image3D cg_sdf_img;
uniform sampler3D cg_sdf_tex; // the same, for blended reads

// The distance field's texel for a slot at a level: the levels stack up the y axis.
ivec3 sdf_texel(ivec3 slot, int level) {
    return ivec3(slot.x, slot.y + level * vox_size.y, slot.z);
}

uint vox_word(ivec3 cell) {
    return imageLoad(cg_vox_img, vox_slot(cell, 0)).r;
}

uint sdf_at(ivec3 cell, int level) {
    return uint(imageLoad(cg_sdf_img, sdf_texel(vox_slot(cell, level), level)).r * 255.0 + 0.5);
}

// The distance field at a point (vox_pos space) at a level, blended between the eight cells around it, in cells.
float sdf_blended(vec3 p, int level) {
    vec3 x = p * exp2(float(level)) - 0.5;
    ivec3 base = ivec3(floor(x));
    vec3 f = x - vec3(base);
    ivec3 slot = vox_slot(base, level);
    if (all(lessThan(slot, vox_size - 1))) {
        // The eight sit side by side: the hardware blends them.
        vec3 texel = vec3(sdf_texel(slot, level)) + 0.5 + f;
        return texture(cg_sdf_tex, texel / vec3(textureSize(cg_sdf_tex, 0))).r * 255.0;
    }
    // They wrap around the table's edge: one by one.
    float d = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 o = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        vec3 w3 = mix(1.0 - f, f, vec3(o));
        d += float(sdf_at(base + o, level)) * (w3.x * w3.y * w3.z);
    }
    return d;
}

// How much room there is around a point, in blocks: the blended field's distance to the nearest solid cell's face
// (half a cell less than to its middle). Near the edge of a level's box it eases into the next coarser level's
// answer, so a surface moving out of a level doesn't step.
float free_space(vec3 p, int level) {
    float room = max(sdf_blended(p, level) - 0.5, 0.0) * exp2(float(-level));
    int edge = vox_edge_distance(vox_cell(p, level), level);
    const int band = 12; // cells
    if (level > 0 && edge < band + 1) {
        float coarse = max(sdf_blended(p, level - 1) - 0.5, 0.0) * exp2(float(1 - level));
        room = mix(coarse, room, clamp(float(edge - 1) / float(band), 0.0, 1.0));
    }
    return room;
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

// The empty cube around a point (vox_pos space), read at no finer level than finest (see ray_finest): its corners,
// and the level it was read at. Empty when the cell itself is solid (lo > hi). Level -1 outside every box.
struct EmptyCube {
    vec3 lo;
    vec3 hi;
    int level;
    bool solid;
};

EmptyCube empty_cube(vec3 p, int finest) {
    EmptyCube c;
    c.level = min(vox_level(p), finest);
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

// A ray that grazes a surface creeps along it a cell at a time, and could use up its steps before it gets to what
// is in its way. So from its ninth step on, a step shorter than its share of the way left (the distance left over
// the steps left) has it read one level coarser from then on: bigger cells, so bigger steps, and a coarse cell is
// solid wherever a finer one is, so nothing gets skipped. Returns the finest level the ray may read next.
const int ray_grace_steps = 8;

int ray_finest(int finest, int level, int i, int max_steps, float stepped, float left) {
    int steps_left = max_steps - i - 1;
    if (i >= ray_grace_steps && steps_left > 0 && stepped * float(steps_left) < left) {
        return max(level - 1, 0);
    }
    return finest;
}

// To the first solid cell along a ray from p (vox_pos space), up to max_t blocks. A miss leaves the box (or runs
// out of steps, which ray_finest makes rare) with hit false and t where it stopped.
Hit trace_ray(vec3 p, vec3 dir, float max_t, int max_steps) {
    Hit h;
    h.hit = false;
    h.level = -1;
    vec3 inv_dir = safe_inverse(dir);
    float t = 0.0;
    int finest = VOXEL_LEVELS - 1;
    for (int i = 0; i < max_steps; i++) {
        vec3 q = p + dir * t;
        EmptyCube c = empty_cube(q, finest);
        if (c.level < 0) {
            break;
        }
        if (c.solid) {
            h.hit = true;
            h.level = c.level;
            break;
        }
        float stepped = box_exit(q, inv_dir, c.lo, c.hi) + exp2(float(-c.level)) * 0.01;
        t += stepped;
        if (t >= max_t) {
            t = max_t;
            break;
        }
        finest = ray_finest(finest, c.level, i, max_steps, stepped, max_t - t);
    }
    h.t = t;
    h.pos = p + dir * t;
    return h;
}

// Where a ray leaving a surface at p (facing face) starts. A fence post, a path or a slab doesn't fill its block,
// but at the coarser levels its cell counts as solid (at all of them, for the top of a path), so a ray from a point on
// it would stop before it got out, and which level is read depends on how far off the camera is: black patches that
// move as you walk. So when the cell p is in is solid at the level read there, the start moves out of it along
// the face, and a little further (so the ray has some room). One cell only: a solid cell beyond it is something else
// in the way (a wall the face looks at); start_inside says so.
vec3 trace_start(vec3 p, vec3 face) {
    int level = vox_level(p);
    if (level < 0) {
        return p;
    }
    ivec3 cell = vox_cell(p, level);
    if (sdf_at(cell, level) != 0u) {
        return p;
    }
    float s = exp2(float(-level));
    return p + face * (box_exit(p, safe_inverse(face), vec3(cell) * s, vec3(cell + 1) * s) + 0.04);
}

// Whether a ray's start is still inside something solid at the detail read there.
bool start_inside(vec3 p) {
    int level = vox_level(p);
    return level >= 0 && sdf_at(vox_cell(p, level), level) == 0u;
}

// How much of a light at distance dist along dir gets to p (0..1): the narrowest the empty space around the ray
// gets (free_space, where the ray is at each step), against the cone's radius there, which runs from width_start at
// p to width_end at the light (the lamp's size). The last stop_short blocks before the light are its own. A ray that
// leaves the voxel box counts as getting there; one that runs out of steps first doesn't (it used to, and a lamp
// behind a ridge lit a thin line along the foot of a wall: rays from there crept along the floor until their steps
// ran out; ray_finest now lets such rays reach the ridge).
float cone_visibility(vec3 p, vec3 dir, float dist, float width_start, float width_end, float stop_short,
                      int max_steps) {
    vec3 inv_dir = safe_inverse(dir);
    float visible = 1.0;
    float t = 0.0;
    float end = dist - stop_short;
    int finest = VOXEL_LEVELS - 1;
    bool left_box = false;
    for (int i = 0; i < max_steps && t < end; i++) {
        vec3 q = p + dir * t;
        int level = min(vox_level(q), finest);
        if (level < 0) {
            left_box = true;
            break;
        }
        ivec3 cell = vox_cell(q, level);
        int d = int(sdf_at(cell, level));
        if (d == 0) {
            return 0.0;
        }
        float s = exp2(float(-level));
        float width = mix(width_start, width_end, clamp(t / dist, 0.0, 1.0));
        visible = min(visible, free_space(q, level) / (width + 0.02));
        vec3 lo = vec3(cell - d + 1) * s;
        vec3 hi = vec3(cell + d) * s;
        float stepped = box_exit(q, inv_dir, lo, hi) + s * 0.01;
        t += stepped;
        finest = ray_finest(finest, level, i, max_steps, stepped, end - t);
    }
    if (t < end && !left_box) {
        return 0.0;
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
        if (t >= end || !vox_in_box(cell, 0)) {
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
        if (t >= dist || !vox_in_box(cell, 0)) {
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

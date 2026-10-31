// Reading the voxel copy of the world (see lib/voxel.glsl) and tracing rays through it.
//
// The copy has one cell per block and the same detail everywhere, fixed to the world, so a ray between two points
// gives the same answer from wherever the camera is. A distance field (cg_sdf_img, program/sdf.csh) says per cell how
// many cells away the nearest one that stops light is, counted the way a cube grows (the largest cube of empty cells
// around it), so a ray leaps across empty space in one step (green), and how far the nearest full block is (red), read
// blended between cells for how much room a light has around it. A ray that reaches a cell that stops light:
// - a full block stops it;
// - a part block (a slab, a path, a fence post, stairs) stops it only where one of its shape's boxes is, tested
//   exactly; a ray that misses them goes on, and how close it came softens its shadow. Where a box's faces have
//   see-through pixels (a trapdoor's windows, a grate's slots: VOX_HOLES), light gets through the open ones (box_open);
// - leaves let light through the gaps in their texture (VOX_HOLES), or a share of it where those aren't known.
// Three ways through:
// - trace_ray: to the first thing it hits (bounce light, reflections);
// - cone_visibility: how much of a light gets past, softly, from the narrowest gap the ray squeezes through
//   (measured exactly against the blocks beside it) against how wide the cone is there (one ray, no extra samples),
//   and the share of see-through pixels it crosses (blurred as wide as the cone is there);
// - see_through_tint: a walk block by block to the first see-through block (stained glass, water), whose colour
//   tints the light.

#if !defined LIB_TRACE
#define LIB_TRACE

#include "/lib/voxel.glsl"

layout(r32ui) uniform uimage3D cg_vox_img;
layout(rg8) uniform image3D cg_sdf_img;
uniform sampler3D cg_sdf_tex; // the same, for blended reads

// How much of the light one block of leaves lets through, where the Bridge has no masks of their see-through pixels.
const float leaf_pass = 0.35;

uint vox_word(ivec3 cell) {
    return imageLoad(cg_vox_img, vox_slot(cell, 0)).r;
}

// The distance field at a cell: to the nearest cell that stops light (anything: full blocks, part blocks, leaves).
uint sdf_at(ivec3 cell, int level) {
    return uint(imageLoad(cg_sdf_img, vox_slot(cell, 0)).g * 255.0 + 0.5);
}

// The distance to the nearest full block (red), blended between the eight cells around a point, in cells.
float sdf_full_blended(vec3 p) {
    vec3 x = p - 0.5;
    ivec3 base = ivec3(floor(x));
    vec3 f = x - vec3(base);
    ivec3 slot = vox_slot(base, 0);
    if (all(lessThan(slot, vox_size - 1))) {
        // The eight sit side by side: the hardware blends them.
        return texture(cg_sdf_tex, (vec3(slot) + 0.5 + f) / vec3(vox_size)).r * 255.0;
    }
    // They wrap around the table's edge: one by one.
    float d = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 o = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        vec3 w3 = mix(1.0 - f, f, vec3(o));
        d += imageLoad(cg_sdf_img, vox_slot(base + o, 0)).r * 255.0 * (w3.x * w3.y * w3.z);
    }
    return d;
}

// How much room there is around a point, in blocks, from the full blocks around it (part blocks and leaves are
// handled where a ray meets them).
float free_space(vec3 p, int level) {
    return max(sdf_full_blended(p) - 0.5, 0.0);
}

// A block's average colour (linear) and how much of it shows, from its palette entry; grey where nothing is known.
vec4 vox_color(ivec3 cell) {
    uint word = vox_word(cell);
    if ((word & VOX_KNOWN) == 0u) {
        return vec4(0.35, 0.35, 0.35, 1.0);
    }
    uint c = palette_word(word & VOX_ENTRY, 1u);
    vec4 v = vec4(uvec4(c, c >> 8u, c >> 16u, c >> 24u) & 255u) / 255.0;
    return vec4(srgb_to_linear(v.rgb), v.a);
}

// The sky light in a block (0..1).
float vox_sky(uint word) {
    return float((word >> VOX_SKY_SHIFT) & 15u) / 15.0;
}

// How far along dir a ray from p inside a box leaves it.
float box_exit(vec3 p, vec3 inv_dir, vec3 lo, vec3 hi) {
    vec3 t = (mix(lo, hi, step(0.0, inv_dir)) - p) * inv_dir;
    return max(min(t.x, min(t.y, t.z)), 0.0);
}

vec3 safe_inverse(vec3 dir) {
    return 1.0 / mix(dir, vec3(1e-6), lessThan(abs(dir), vec3(1e-6)));
}

// Where a ray from o enters a box, between t0 and t1, and the face it enters by; false when it doesn't. A box that
// holds the ray at t0 counts as entered there, unless skip_inside (the ray's own start: the surface it leaves).
bool box_entry(vec3 lo, vec3 hi, vec3 o, vec3 inv_dir, float t0, float t1, bool skip_inside, out float t,
               out vec3 normal) {
    vec3 ta = (lo - o) * inv_dir;
    vec3 tb = (hi - o) * inv_dir;
    vec3 near = min(ta, tb);
    float te = max3(near);
    float tx = min3(max(ta, tb));
    t = te;
    normal = vec3(0.0);
    if (tx < te || tx <= t0 || te >= t1) {
        return false;
    }
    int axis = near.x >= near.y && near.x >= near.z ? 0 : near.y >= near.z ? 1 : 2;
    normal[axis] = inv_dir[axis] > 0.0 ? -1.0 : 1.0;
    if (te < t0) {
        if (skip_inside) {
            return false;
        }
        t = t0;
    }
    return true;
}

// The open share of a square of half width h round a point c on a box's faces across an axis (both in 16ths, along
// the faces' a and b, see hole_word in lib/world.glsl), of the part of the square that lies on the faces (ext: their
// low a, low b, high a, high b). The square's edge cells count for as much of them as it covers, so the share changes
// smoothly as the point moves. With h 0: whether the cell under the point is open.
float hole_open(uint mask, vec2 c, float h, ivec4 ext) {
    if (mask == 0u) {
        return 0.0;
    }
    vec2 lo = max(c - h, vec2(ext.xy));
    vec2 hi = min(c + h, vec2(ext.zw));
    if (h <= 0.0 || any(lessThanEqual(hi - lo, vec2(1e-3)))) {
        ivec2 cell = clamp(ivec2(floor(c)), ext.xy, ext.zw - 1);
        return float((hole_row(mask, cell.y) >> uint(cell.x)) & 1u);
    }
    int i0 = int(floor(lo.x));
    int i1 = min(int(ceil(hi.x)) - 1, 15);
    int j0 = int(floor(lo.y));
    int j1 = min(int(ceil(hi.y)) - 1, 15);
    float w0 = i0 == i1 ? hi.x - lo.x : float(i0 + 1) - lo.x;
    float w1 = i0 == i1 ? 0.0 : hi.x - float(i1);
    uint inner = i1 - i0 >= 2 ? ((1u << uint(i1)) - 1u) & ~((2u << uint(i0)) - 1u) : 0u;
    float open = 0.0;
    for (int j = j0; j <= j1; j++) {
        float wj = min(float(j + 1), hi.y) - max(float(j), lo.y);
        uint row = hole_row(mask, j);
        open += wj * (float(bitCount(row & inner)) + w0 * float((row >> uint(i0)) & 1u)
                      + w1 * float((row >> uint(i1)) & 1u));
    }
    return open / ((hi.x - lo.x) * (hi.y - lo.y));
}

// How much of a cone of half width h (16ths; 0 a single ray) gets through box i (lo..hi in its cell) of a part block
// with see-through pixels: the open share round where the ray from o (in the cell's own coordinates) goes in, and
// round where it comes out, each on the faces it crosses there, whichever is less (a trapdoor's two faces have the
// same windows, so a ray through one window gets through both).
float box_open(uint table, uint i, vec3 lo, vec3 hi, vec3 o, vec3 dir, vec3 inv_dir, float h) {
    uvec3 masks = hole_masks(table, i);
    vec3 ta = (lo - o) * inv_dir;
    vec3 tb = (hi - o) * inv_dir;
    vec3 near = min(ta, tb);
    vec3 far = max(ta, tb);
    int in_axis = near.x >= near.y && near.x >= near.z ? 0 : near.y >= near.z ? 1 : 2;
    int out_axis = far.x <= far.y && far.x <= far.z ? 0 : far.y <= far.z ? 1 : 2;
    float open = 1.0;
    for (int k = 0; k < 2 && open > 0.0; k++) {
        int axis = k == 0 ? in_axis : out_axis;
        vec3 q = o + dir * (k == 0 ? near[axis] : far[axis]);
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        ivec4 ext = ivec4(round(vec4(lo[a], lo[b], hi[a], hi[b]) * 16.0));
        open = min(open, hole_open(masks[axis], vec2(q[a], q[b]) * 16.0, h, ext));
    }
    return open;
}

// The first of a part block's boxes a ray meets inside its cell (between t0 and t1): its distance, or -1. A ray
// through an open pixel of a box with see-through pixels misses that box.
float part_hit(ivec3 cell, uint word, vec3 o, vec3 inv_dir, float t0, float t1, bool skip_inside, out vec3 normal) {
    uint entry = word & VOX_ENTRY;
    uint table = (word & VOX_HOLES) != 0u ? hole_table(entry) : 0u;
    vec3 dir = 1.0 / inv_dir;
    float best = -1.0;
    normal = vec3(0.0);
    uint n = palette_box_count(entry);
    for (uint i = 0u; i < n; i++) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, i, lo, hi);
        if (any(lessThanEqual(hi, lo))) {
            continue;
        }
        float t;
        vec3 face;
        if (box_entry(vec3(cell) + lo, vec3(cell) + hi, o, inv_dir, t0, t1, skip_inside, t, face) && (best < 0.0 || t < best)) {
            if (table != 0u && box_open(table, i, lo, hi, o - vec3(cell), dir, inv_dir, 0.0) > 0.5) {
                continue; // through a hole
            }
            best = t;
            normal = face;
        }
    }
    return best;
}

// How much of a cone of half width h (16ths) gets through a part block with see-through pixels (VOX_HOLES) between
// t0 and t1: what each of the boxes the ray goes into lets through (box_open), together.
float part_pass(ivec3 cell, uint word, vec3 o, vec3 inv_dir, float t0, float t1, bool skip_inside, float h) {
    uint entry = word & VOX_ENTRY;
    uint table = hole_table(entry);
    vec3 dir = 1.0 / inv_dir;
    float pass = 1.0;
    uint n = palette_box_count(entry);
    for (uint i = 0u; i < n && pass > 0.0; i++) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, i, lo, hi);
        if (any(lessThanEqual(hi, lo))) {
            continue;
        }
        float t;
        vec3 face;
        if (box_entry(vec3(cell) + lo, vec3(cell) + hi, o, inv_dir, t0, t1, skip_inside, t, face)) {
            pass *= table != 0u ? box_open(table, i, lo, hi, o - vec3(cell), dir, inv_dir, h) : 0.0;
        }
    }
    return pass;
}

// How far a point is from a part block's boxes, in blocks. A box with see-through pixels doesn't count from inside it
// or from in front of a face of it that has them: what gets through those is worked out where a ray crosses the box
// (part_pass). From beside it (past its edges) it counts, as any box does.
float part_room(ivec3 cell, uint word, vec3 q) {
    uint entry = word & VOX_ENTRY;
    uint table = (word & VOX_HOLES) != 0u ? hole_table(entry) : 0u;
    float room = 1.0;
    uint n = palette_box_count(entry);
    for (uint i = 0u; i < n; i++) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, i, lo, hi);
        if (any(lessThanEqual(hi, lo))) {
            continue;
        }
        vec3 d = max(max(vec3(cell) + lo - q, q - vec3(cell) - hi), 0.0);
        if (table != 0u) {
            bvec3 outside = greaterThan(d, vec3(0.0));
            int sides = int(outside.x) + int(outside.y) + int(outside.z);
            if (sides <= 1) {
                uvec3 m = hole_masks(table, i);
                uint facing = sides == 0 ? m.x | m.y | m.z : outside.x ? m.x : outside.y ? m.y : m.z;
                if (facing != 0u) {
                    continue;
                }
            }
        }
        room = min(room, length(d));
    }
    return room;
}

// How far a point is from what stops light in the blocks around its own (full blocks, and part blocks' boxes), exactly,
// up to cap blocks (under half a block): only the blocks on the sides it is within cap of are read. Leaves don't count
// (a ray that crosses them loses part of its light there). The blended distance field can't stand in here: in an inside
// corner (a block's foot on the floor) it reads no room at all for nearly half a block, and part blocks aren't in it.
float near_room(vec3 q, float cap) {
    cap = min(cap, 0.49);
    ivec3 cell = ivec3(floor(q));
    vec3 f = q - vec3(cell);
    ivec3 side = ivec3(greaterThan(f, vec3(1.0 - cap))) - ivec3(lessThan(f, vec3(cap)));
    float room = cap;
    for (int i = 1; i < 8; i++) {
        ivec3 bits = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        if (any(greaterThan(bits, abs(side)))) {
            continue; // a side it isn't near
        }
        ivec3 n = cell + bits * side;
        uint word = vox_word(n);
        if ((word & VOX_SOLID) == 0u || (word & VOX_LEAVES) != 0u) {
            continue;
        }
        if ((word & VOX_PART) != 0u) {
            room = min(room, part_room(n, word, q));
        } else {
            room = min(room, length(max(max(vec3(n) - q, q - vec3(n + 1)), 0.0)));
        }
    }
    return room;
}

struct Hit {
    bool hit;
    float t;
    vec3 pos;    // vox_pos space
    int level;   // 0 (the copy has one level)
    vec3 normal; // the face it went in by
    ivec3 block; // the block it hit
};

// To the first thing a ray from p (vox_pos space) meets, up to max_t blocks. A miss leaves the box (or runs out of
// steps) with hit false and t where it stopped. The ray's own start is the surface it leaves: a part block's box that
// holds it doesn't count.
Hit trace_ray(vec3 p, vec3 dir, float max_t, int max_steps) {
    Hit h;
    h.hit = false;
    h.level = 0;
    h.normal = vec3(0.0);
    h.block = ivec3(0);
    vec3 inv_dir = safe_inverse(dir);
    ivec3 start = ivec3(floor(p));
    float t = 0.0;
    for (int i = 0; i < max_steps; i++) {
        vec3 q = p + dir * t;
        ivec3 cell = ivec3(floor(q));
        if (vox_edge_distance(cell, 0) < 1) {
            break;
        }
        uint d = sdf_at(cell, 0);
        if (d > 0u) {
            t += box_exit(q, inv_dir, vec3(cell - int(d) + 1), vec3(cell + int(d))) + 0.002;
        } else {
            float exit_t = t + box_exit(q, inv_dir, vec3(cell), vec3(cell + 1));
            uint word = vox_word(cell);
            float hit_t = -1.0;
            vec3 n = vec3(0.0);
            if ((word & VOX_SOLID) == 0u) {
                // Emptied since the distance field last looked (it follows within a frame or two).
            } else if ((word & (VOX_PART | VOX_HOLES)) != 0u) {
                // A part block, or leaves with see-through pixels.
                hit_t = part_hit(cell, word, p, inv_dir, t, exit_t + 1e-4, cell == start, n);
            } else if (cell != start) {
                float te;
                box_entry(vec3(cell), vec3(cell + 1), p, inv_dir, t - 1e-3, exit_t + 1e-3, false, te, n);
                hit_t = t;
            }
            if (hit_t >= 0.0) {
                h.hit = true;
                h.normal = n;
                h.block = cell;
                t = hit_t;
                break;
            }
            t = exit_t + 0.002;
        }
        if (t >= max_t) {
            t = max_t;
            break;
        }
    }
    h.t = t;
    h.pos = p + dir * t;
    return h;
}

// Whether a ray's start is inside a full block (a surface the copy doesn't hold, such as a mob, or the hand, pressed
// into one): no ray gets out of it.
bool start_inside(vec3 p) {
    ivec3 cell = ivec3(floor(p));
    return vox_edge_distance(cell, 0) >= 1 && vox_full(vox_word(cell));
}

// How much of a light at distance dist along dir gets to p (0..1): the narrowest the empty space around the ray
// gets (near_room: how close it passes the blocks and part blocks' boxes beside it), against the cone's radius there
// (always under half a block, so cells with nothing within a block are leapt over), which runs from
// width_start at p to width_end at the light (the lamp's size); each block of leaves on the way lets leaf_pass of
// it through. The last stop_short blocks before the light are its own (none for a lamp in the world: its cone is never
// wider than the room it has, program/voxel_light.csh). A ray that leaves the voxel box counts as
// getting there; one that runs out of steps first doesn't.
float cone_visibility(vec3 p, vec3 dir, float dist, float width_start, float width_end, float stop_short,
                      int max_steps) {
    vec3 inv_dir = safe_inverse(dir);
    ivec3 start = ivec3(floor(p));
    float visible = 1.0;
    float through = 1.0;
    float t = 0.0;
    float end = dist - stop_short;
    bool left_box = false;
    int i = 0;
    for (; i < max_steps && t < end; i++) {
        vec3 q = p + dir * t;
        ivec3 cell = ivec3(floor(q));
        if (vox_edge_distance(cell, 0) < 1) {
            left_box = true;
            break;
        }
        uint d = sdf_at(cell, 0);
        if (d > 1u) {
            // Nothing that stops light within a block: more room than any cone here is wide.
            t += box_exit(q, inv_dir, vec3(cell - int(d) + 1), vec3(cell + int(d))) + 0.002;
            continue;
        }
        float width = mix(width_start, width_end, clamp(t / dist, 0.0, 1.0)) + 0.02;
        float exit_t = t + box_exit(q, inv_dir, vec3(cell), vec3(cell + 1));
        float seg_end = min(exit_t, end);
        float mid_t = 0.5 * (t + seg_end);
        vec3 mid = p + dir * mid_t;
        float mid_width = mix(width_start, width_end, clamp(mid_t / dist, 0.0, 1.0)) + 0.02;
        if (d == 1u) {
            // Beside something: the room where the ray comes in and halfway through, measured exactly (a ray that
            // slips through where two blocks only touch at an edge comes in with none).
            visible = min(visible, min(near_room(q, width) / width, near_room(mid, mid_width) / mid_width));
            t = exit_t + 0.002;
            continue;
        }
        uint word = vox_word(cell);
        if ((word & VOX_SOLID) == 0u) {
            // Emptied since the distance field last looked.
        } else if ((word & VOX_LEAVES) != 0u) {
            // Leaves: through the gaps in their texture, or a share of the light without masks.
            through *= (word & VOX_HOLES) != 0u
                     ? part_pass(cell, word, p, inv_dir, t, seg_end, cell == start, clamp(mid_width * 16.0, 0.5, 3.0))
                     : leaf_pass;
            if (through <= 0.0) {
                return 0.0;
            }
        } else if ((word & VOX_PART) != 0u) {
            vec3 n;
            if ((word & VOX_HOLES) != 0u) {
                // The share of the cone that gets through the open pixels, blurred by how wide the cone is there.
                float pass = part_pass(cell, word, p, inv_dir, t, seg_end, cell == start,
                                       clamp(mid_width * 16.0, 0.5, 3.0));
                if (pass <= 0.0) {
                    return 0.0;
                }
                through *= pass;
            } else if (part_hit(cell, word, p, inv_dir, t, seg_end, cell == start, n) >= 0.0) {
                return 0.0;
            }
            float room = near_room(mid, mid_width);
            if (cell != start) {
                room = min(room, part_room(cell, word, mid));
            }
            visible = min(visible, room / mid_width);
        } else if (cell != start) {
            return 0.0;
        }
        t = exit_t + 0.002;
    }
    if (t < end && !left_box) {
        return 0.0;
    }
    return smoothstep(0.0, 1.0, clamp(visible, 0.0, 1.0)) * through;
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
        if ((word & VOX_SEE) != 0u) {
            vec4 c = vox_color(cell);
            vec3 hue = c.rgb / max(max3(c.rgb), 0.05);
            return mix(vec3(1.0), hue, clamp(c.a * 1.5, 0.0, 1.0)) * mix(1.0, max3(c.rgb), 0.5);
        }
    }
    return vec3(1.0);
}

// The direction a surface near a cell faces, from how the distance field grows around it; zero in the open.
vec3 vox_normal(ivec3 cell) {
    float dx = float(sdf_at(cell + ivec3(1, 0, 0), 0)) - float(sdf_at(cell - ivec3(1, 0, 0), 0));
    float dy = float(sdf_at(cell + ivec3(0, 1, 0), 0)) - float(sdf_at(cell - ivec3(0, 1, 0), 0));
    float dz = float(sdf_at(cell + ivec3(0, 0, 1), 0)) - float(sdf_at(cell - ivec3(0, 0, 1), 0));
    vec3 g = vec3(dx, dy, dz);
    return dot(g, g) > 0.01 ? normalize(g) : vec3(0.0);
}

// A point inside a cell to stand for its light: its middle, or for a part block whose boxes hold the middle, just
// above its highest box (a slab's top), or failing that out of it along normal.
vec3 cell_light_point(ivec3 cell, uint word, vec3 normal) {
    vec3 middle = vec3(cell) + 0.5;
    if ((word & VOX_PART) == 0u) {
        return middle - normal * 0.3;
    }
    uint entry = word & VOX_ENTRY;
    if (part_room(cell, word, middle) > 0.05) {
        return middle;
    }
    float top = 0.0;
    uint n = palette_box_count(entry);
    for (uint i = 0u; i < n; i++) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, i, lo, hi);
        top = max(top, hi.y);
    }
    if (top < 0.9) {
        return vec3(middle.x, float(cell.y) + top + 0.05, middle.z);
    }
    return middle + normal * 0.45;
}

#endif

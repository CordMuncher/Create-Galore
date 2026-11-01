// Reading the voxel copy of the world (see lib/voxel.glsl) and tracing rays through it.
//
// The copy has one cell per block and the same detail everywhere, fixed to the world, so a ray between two points
// gives the same answer from wherever the camera is. A distance field (cg_sdf_img, program/sdf.csh) says per cell how
// many cells away the nearest one that stops light is, counted the way a cube grows (the largest cube of empty cells
// around it), so a ray leaps across empty space in one step (green), and how far the nearest full block is (red), read
// blended between cells for how much room a light has around it. A ray that reaches a cell that stops light:
// - a full block stops it;
// - a part block (a slab, a path, a fence post, stairs) stops it only where one of its shape's boxes is, tested
//   exactly; a ray that misses them goes on, and how close it came softens its shadow. A box narrower than the
//   cone is wide there (a fence's rail in front of a torch's flame) only hides the share of the light it covers, so
//   a thin part's shadow is a soft partial one, as under a real flame of that size. Where a box's faces have
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
#include "/lib/blocks.glsl"

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

// Whether a point is inside something that stops light (a full block, or one of a part block's boxes).
bool stops_light_at(vec3 q) {
    ivec3 cell = ivec3(floor(q));
    uint word = vox_word(cell);
    if ((word & VOX_SOLID) == 0u || (word & VOX_LEAVES) != 0u) {
        return false;
    }
    return (word & VOX_PART) == 0u || part_room(cell, word, q) <= 0.0;
}

// The share of a cone of half width w round a ray (along dir, through m) that a box (lo..hi) hides, from the box's
// outline seen along the ray. The outline is where three strips cross, one for each way the box's edges run, and each
// strip hides its share of the cone across it: all of it deep inside, half at its edge, a strip thinner than the cone
// only as much as it covers. Where two strips each leave part of the cone (near a corner of the outline), what they
// leave together lies outside both, and the corner's angle sets how much that is: a quarter of the cone at a square
// corner, half where the outline runs on nearly straight, none between two edges that face apart. (Taking only the
// strip that hides the least, as 0.7 did, hid half the cone at every corner of the outline: a lantern's post threw
// spikes of shadow along its diagonals.) With beyond, an edge of the outline whose far side holds something else that
// stops light (the next slab of a floor, the rail going on into the next fence) is no way out. open_way: across the
// ray, the way the cone gets past the box (its open edges, by how much each leaves; zero where none does).
float box_cone_cover(vec3 m, vec3 lo, vec3 hi, vec3 dir, float w, bool beyond, out vec3 open_way) {
    vec3 rel = m - 0.5 * (lo + hi);
    vec3 size = hi - lo;
    // First the edges whose far side stops light (bits: strip k behind 1 << 2k, ahead 2 << 2k), on their own, as the
    // look-ups are long and little else needs keeping meanwhile (the pass runs more pixels at once).
    uint closed = 0u;
    if (beyond) {
        // Each edge of the outline that the cone gets past (strip k, side s: 0 behind, 1 ahead) is closed if, just
        // past it level with the ray, something stops light. (One loop with one look-up keeps the program small.)
        for (int e = 0; e < 6; e++) {
            int k = e >> 1;
            vec3 axis = vec3(0.0);
            axis[k] = 1.0;
            vec3 out_way = cross(dir, axis);
            float len = length(out_way);
            if (len < 0.05) {
                continue; // the ray runs along this way: no strip
            }
            out_way *= (e & 1) == 0 ? -1.0 / len : 1.0 / len;
            float out_by = 0.5 * dot(abs(out_way), size) - dot(out_way, rel);
            if (out_by < w && stops_light_at(m + out_way * (out_by + 0.02))) {
                closed |= 1u << uint(e);
            }
        }
    }
    vec3 left = vec3(0.0); // what each strip leaves of the cone
    // Its nearer way out (1: along cross(dir, axis), -1 against), over the length of that cross. Where the two ways
    // are within a quarter of the cone's width of as near, it goes over from one to the other, so that a ray right over a
    // post's middle doesn't flicker between them. (Only in that narrow band do two strips that run the same way, as
    // when the ray is square to the third way, count as overlapping less than wholly: a thin line of a little more
    // light down the middle of a post's shadow, where the lamp stands right above it. Getting that right too took
    // registers the pass can't spare.)
    vec3 way = vec3(0.0);
    for (int k = 0; k < 3; k++) {
        vec3 axis = vec3(0.0);
        axis[k] = 1.0;
        vec3 across = cross(dir, axis);
        float len = length(across);
        if (len < 0.05) {
            continue;
        }
        across /= len;
        float half_width = 0.5 * dot(abs(across), size);
        float at = dot(across, rel);
        vec2 out_by = vec2(half_width + at, half_width - at); // to the outline's side behind, ahead
        uint bits = closed >> uint(2 * k);
        out_by = mix(out_by, vec2(1e9), bvec2((bits & 1u) != 0u, (bits & 2u) != 0u));
        left[k] = 1.0 - clamp((min(out_by.y, w) + min(out_by.x, w)) / (2.0 * w), 0.0, 1.0);
        way[k] = clamp((out_by.x - out_by.y) * 4.0 / w, -1.0, 1.0) / len;
    }
    // How the ways out of two strips line up: the cosine between strip i's and j's is -lean[i] * lean[j].
    vec3 lean = dir * way;
    // Outside two strips at once: as much as both leave apart for a square corner, more as the corner opens out (up
    // to all of the lesser at a straight edge), none where the two edges face apart.
    float both01 = min(min(left.x, left.y), left.x * left.y * (1.0 - lean.x * lean.y));
    float both02 = min(min(left.x, left.z), left.x * left.z * (1.0 - lean.x * lean.z));
    float both12 = min(min(left.y, left.z), left.y * left.z * (1.0 - lean.y * lean.z));
    float leaves = clamp(left.x + left.y + left.z - both01 - both02 - both12, max3(left), 1.0);
    vec3 weight = left * way;
    vec3 out_way = weight.x * vec3(0.0, dir.z, -dir.y) + weight.y * vec3(-dir.z, 0.0, dir.x)
                 + weight.z * vec3(dir.y, -dir.x, 0.0);
    open_way = dot(out_way, out_way) > 1e-8 ? normalize(out_way) : vec3(0.0);
    return 1.0 - leaves;
}

// The straight measure that cone_visibility's shaping (smoothstep) turns into y.
float unshape(float y) {
    return 0.5 - sin(asin(1.0 - 2.0 * y) / 3.0);
}

// What two boxes hide of a cone together, as shares of it (shaped: smoothstep of box_cone_cover), the first hiding
// more, from how their open ways line up (agree: the cone gets past both the same way, the second hides nothing more;
// across each other: an inside corner of their outlines, the second hides its share of what the first leaves; facing
// apart: they hide opposite sides of the cone, and their shares add).
float hidden_together(float a, float b, float agree) {
    return min(1.0, agree >= 0.0 ? a + b * (1.0 - a) * (1.0 - agree) : a + b * (1.0 - a * (1.0 + agree)));
}

// How far a point is from what stops light in the blocks around its own (full blocks, and part blocks' boxes), exactly,
// up to cap blocks (under half a block): only the blocks on the sides it is within cap of are read. Leaves don't count
// (a ray that crosses them loses part of its light there). The blended distance field can't stand in here: in an inside
// corner (a block's foot on the floor) it reads no room at all for nearly half a block, and part blocks aren't in it.
// The same, kept apart for full blocks (what it returns) and part blocks' boxes (part).
float near_room_split(vec3 q, float cap, out float part) {
    cap = min(cap, 0.49);
    ivec3 cell = ivec3(floor(q));
    vec3 f = q - vec3(cell);
    ivec3 side = ivec3(greaterThan(f, vec3(1.0 - cap))) - ivec3(lessThan(f, vec3(cap)));
    float room = cap;
    part = cap;
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
            part = min(part, part_room(n, word, q));
        } else {
            room = min(room, length(max(max(vec3(n) - q, q - vec3(n + 1)), 0.0)));
        }
    }
    return room;
}

float near_room(vec3 q, float cap) {
    float part;
    float room = near_room_split(q, cap, part);
    return min(room, part);
}

// The room round a ray (going along dir) at q for a cone of half width w, as cone_visibility measures it: from the
// full blocks round q, as near_room_split (returned); and how much of the cone the part blocks' boxes in the blocks
// round q leave (part_visible; with own, those of q's own block too). A box within the cone's width of q hides what
// its outline seen along the ray covers (box_cone_cover: half the cone at the outline's edge, less at its corners),
// as long as it lies along the ray's own stretch (behind blocks back to its start, ahead on to the light: not a slab
// over the lamp the ray ends at). Two boxes join as hidden_together says: on opposite sides of the ray they add up (a
// ray that slips between two boxes that only touch, open trapdoors standing round a lantern and meeting at its
// corners, crosses neither, but the cone round it is covered on both sides). A box with see-through pixels counts as
// well where the ray only passes it: part_pass works out what gets through its open pixels where a ray goes through
// it, and a ray that just grazes its edge never does (the trace steps from block to block diagonally there), so
// without it the frame round a grate's or a trapdoor's holes would let a fine line of light through.
float near_room_cone(vec3 q, vec3 dir, float w, bool own, float behind, float ahead, float span, float widen,
                     out float part_visible) {
    float cap = min(w, 0.49);
    ivec3 cell = ivec3(floor(q));
    vec3 f = q - vec3(cell);
    // The blocks beside the faces that the stretch (q +- dir * span) comes within the cone's width of: the nearer face
    // of each way where it could come near both.
    vec3 reach_out = vec3(cap) + span * abs(dir);
    bvec3 upper = greaterThan(f, vec3(0.5));
    ivec3 side = ivec3(upper) * ivec3(greaterThan(f, 1.0 - reach_out))
               - ivec3(not(upper)) * ivec3(lessThan(f, reach_out));
    vec3 inv_dir = safe_inverse(dir);
    float room = cap;
    // Two looks at the same boxes, so that no list of them has to be kept: the first finds the box that hides the
    // most of the cone (the first such, if two hide as much), the second how much a box on the other side of the ray
    // hides on top of it. Only the first 8 boxes that hide any of it count. (A list here cost the block light passes
    // more than half their speed on a GPU: an array indexed in a loop takes registers from every pixel of the pass,
    // whether or not a part block is near, so fewer pixels run at once.)
    float strongest = -1.0;
    vec3 strongest_way = vec3(0.0);
    float strongest_hidden = 0.0;
    float other = 0.0;
    for (int look = 0; look < 2; look++) {
        if (look == 1 && strongest < 0.0) {
            break; // no part block's box hides any of it
        }
        strongest_hidden = smoothstep(0.0, 1.0, max(strongest, 0.0));
        int count = 0;
        for (int i = own ? 0 : 1; i < 8; i++) {
            ivec3 bits = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
            if (any(greaterThan(bits, abs(side)))) {
                continue; // a side it isn't near
            }
            ivec3 n = cell + bits * side;
            uint word = vox_word(n);
            if ((word & VOX_SOLID) == 0u || (word & VOX_LEAVES) != 0u) {
                continue;
            }
            if ((word & VOX_PART) == 0u) {
                if (look == 0) {
                    room = min(room, length(max(max(vec3(n) - q, q - vec3(n + 1)), 0.0)));
                }
                continue;
            }
            uint entry = word & VOX_ENTRY;
            bool holes = (word & VOX_HOLES) != 0u;
            uint boxes = palette_box_count(entry);
            vec3 local = q - vec3(n);
            for (uint b = 0u; b < boxes && count < 8; b++) {
                vec3 lo;
                vec3 hi;
                palette_box(entry, b, lo, hi);
                if (any(lessThanEqual(hi, lo))) {
                    continue;
                }
                float along = dot(0.5 * (lo + hi) - local, dir);
                // The stretch's point nearest the box, and the cone there: from the point level with the box's middle
                // to the box and back. (The point level with the middle alone missed a post's top that a ray skims
                // past at a slant: a half moon of light round the post.)
                float level = clamp(along, -span, span);
                level = clamp(dot(clamp(local + dir * level, lo, hi) - local, dir), -span, span);
                vec3 by = local + dir * level;
                float wb = w + widen * level;
                vec3 to = clamp(by, lo, hi) - by;
                if (dot(to, to) >= wb * wb) {
                    continue; // further than the cone is wide
                }
                // The block's own faces that the box reaches and the stretch passes within the cone's width of (bits:
                // axis k low 1 << 2k, high 2 << 2k), where it may run on into the next block (below).
                uvec3 low_near = uvec3(lessThanEqual(lo, vec3(0.0))) * uvec3(lessThan(abs(by - lo), vec3(wb)));
                uvec3 high_near = uvec3(greaterThanEqual(hi, vec3(1.0))) * uvec3(lessThan(abs(by - hi), vec3(wb)));
                uint near_faces = low_near.x | (high_near.x << 1u) | (low_near.y << 2u) | (high_near.y << 3u)
                                | (low_near.z << 4u) | (high_near.z << 5u);
                float reach = 0.5 * dot(abs(dir), hi - lo);
                if (along + reach < -behind || along - reach > ahead) {
                    continue; // wholly behind the ray's start or past the light
                }
                // A box that runs on into the same block beside it (the next slab of a floor, the next rail of a fence)
                // is taken with its copy there as one box, across each such face: the two leave the cone only round
                // their joint outline. (Taken one by one, each let light out at the seam: a tooth of light at every
                // seam.)
                for (int f = 0; f < 6 && near_faces != 0u; f++) {
                    if ((near_faces & (1u << uint(f))) == 0u) {
                        continue;
                    }
                    int k = f >> 1;
                    ivec3 nb = n;
                    nb[k] += (f & 1) == 0 ? -1 : 1;
                    if (((vox_word(nb) ^ word) & (VOX_ENTRY | VOX_PART)) == 0u) {
                        if ((f & 1) == 0) {
                            lo[k] -= 1.0;
                        } else {
                            hi[k] += 1.0;
                        }
                    }
                }
                vec3 way;
                float cover = box_cone_cover(local, lo, hi, dir, wb, false, way);
                if (cover <= 0.0) {
                    continue;
                }
                if (holes) {
                    // The ray goes through it (part_pass): a little inside its outline, as one that only clips its
                    // edge may never get there.
                    vec3 ta = (lo + 1e-3 - local) * inv_dir;
                    vec3 tb = (hi - 1e-3 - local) * inv_dir;
                    if (max3(min(ta, tb)) < min3(max(ta, tb))) {
                        continue;
                    }
                }
                count++;
                if (look == 0) {
                    if (cover > strongest) {
                        strongest = cover;
                        strongest_way = way;
                    }
                    continue;
                }
                float joined = hidden_together(strongest_hidden, smoothstep(0.0, 1.0, cover), dot(way, strongest_way));
                other = max(other, joined - strongest_hidden);
            }
        }
    }
    // The trace keeps its measure straight and shapes it once at the end (cone_visibility: the share of a round lamp a
    // straight edge leaves as it crosses). Two boxes join as shares (hidden_together) and the sum is turned back into
    // the straight measure: adding straight measures hid too much (a ray threading between a wall's arm and a
    // lantern's post went near black), and the most of either hid too little (the two meeting in an inside corner).
    if (strongest < 0.0) {
        part_visible = 1.0;
    } else if (other <= 0.0) {
        part_visible = 1.0 - strongest;
    } else {
        part_visible = unshape(clamp(1.0 - strongest_hidden - other, 0.0, 1.0));
    }
    return room;
}

// How much of a cone of half width w the boxes of a part block (without see-through pixels) hide where its middle
// ray goes through them (between t0 and t1; 0 where it misses them all). Seen along the ray, a box covers a patch
// round the ray (the box's outline from that side); the cone gets past it by the shortest way out of that patch,
// unless what lies that way stops light too (the next slab of a floor, the rail going on into the next fence). So
// a fence's rail, thinner than a torch's cone a few blocks out, hides only part of the light; a slab or a wall of
// them hides it all.
float part_cover(ivec3 cell, uint word, vec3 o, vec3 dir, vec3 inv_dir, float t0, float t1, bool skip_inside,
                 float w) {
    uint entry = word & VOX_ENTRY;
    float hidden = 0.0;   // as a share (shaped, see near_room_cone)
    float strongest = 0.0;
    uint strongest_way = 0u; // packed (kept as three, it took registers)
    uint n = palette_box_count(entry);
    for (uint i = 0u; i < n && hidden < 0.999; i++) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, i, lo, hi);
        if (any(lessThanEqual(hi, lo))) {
            continue;
        }
        lo += vec3(cell);
        hi += vec3(cell);
        float t;
        vec3 face;
        if (!box_entry(lo, hi, o, inv_dir, t0, t1, skip_inside, t, face)) {
            continue;
        }
        float t_out = min(min3(max((lo - o) * inv_dir, (hi - o) * inv_dir)), t1);
        vec3 m = o + dir * (0.5 * (t + t_out));
        // Boxes of one block that the ray goes into hide together (hidden_together: a wall's arm and its post meet in
        // an inside corner of their outline round the ray).
        vec3 way;
        float share = smoothstep(0.0, 1.0, box_cone_cover(m, lo, hi, dir, w, true, way));
        float agree = dot(way, unpackSnorm4x8(strongest_way).xyz);
        hidden = share > strongest ? hidden_together(share, hidden, agree) : hidden_together(hidden, share, agree);
        if (share > strongest) {
            strongest = share;
            strongest_way = packSnorm4x8(vec4(way, 0.0));
        }
    }
    return 1.0 - unshape(1.0 - hidden);
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
    float widen = (width_end - width_start) / dist; // how much wider the cone gets per block
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
        float mid_width = mix(width_start, width_end, clamp(mid_t / dist, 0.0, 1.0)) + 0.02;
        if (d == 1u) {
            // Beside something: the room where the ray comes in and halfway through, measured exactly (a ray that
            // slips through where two blocks only touch at an edge comes in with none); beside a part block's box,
            // its soft edge runs both ways from its outline (near_room_cone).
            // (Each taken in as soon as it is known: kept for one min, they took registers in the second look.)
            float part;
            float full = near_room_cone(q, dir, width, false, t, end - t, 0.0, 0.0, part);
            visible = min(visible, min(full / width, part));
            full = near_room_cone(p + dir * mid_t, dir, mid_width, false, mid_t, end - mid_t, mid_t - t, widen, part);
            visible = min(visible, min(full / mid_width, part));
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
            if ((word & VOX_HOLES) != 0u) {
                // The share of the cone that gets through the open pixels, blurred by how wide the cone is there.
                float pass = part_pass(cell, word, p, inv_dir, t, seg_end, cell == start,
                                       clamp(mid_width * 16.0, 0.5, 3.0));
                if (pass <= 0.0) {
                    return 0.0;
                }
                through *= pass;
                vec3 mid = p + dir * mid_t;
                float room = near_room(mid, mid_width);
                if (cell != start) {
                    room = min(room, part_room(cell, word, mid));
                }
                visible = min(visible, room / mid_width);
            } else {
                // The share of the cone its boxes hide where the ray goes through them (all of it for a box wider
                // than the cone), or, where it misses them, how close it passes.
                float cover = part_cover(cell, word, p, dir, inv_dir, t, seg_end, cell == start, mid_width);
                if (cover >= 0.999) {
                    return 0.0;
                }
                float part;
                float room = near_room_cone(p + dir * mid_t, dir, mid_width, cell != start, mid_t, end - mid_t,
                                            mid_t - t, widen, part);
                if (cover > 0.0) {
                    visible = min(visible, 1.0 - cover);
                }
                visible = min(visible, min(room / mid_width, part));
            }
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

// How high water stands in a cell, in blocks (0..1), for the corners of the surfaces round it: a full block where
// water is over it, else its own level (a water block's level is its palette box: still water 8/9, flowing water
// lower as it runs out; a waterlogged block holds still water); -1 for a block that stops things (it doesn't pull a
// corner down), 0 for open space. As Minecraft's water drawing takes them.
float water_level(ivec3 cell) {
    if (!vox_in_box(cell, 0)) {
        return -1.0;
    }
    uint word = vox_word(cell);
    if ((word & VOX_WATER) == 0u) {
        return (word & VOX_SOLID) != 0u ? -1.0 : 0.0;
    }
    ivec3 above = cell + ivec3(0, 1, 0);
    if (vox_in_box(above, 0) && (vox_word(above) & VOX_WATER) != 0u) {
        return 1.0;
    }
    uint entry = word & VOX_ENTRY;
    if ((word & VOX_SOLID) == 0u && int(palette_word(entry, 0u) & 0xffffu) == BLOCK_WATER
            && palette_box_count(entry) > 0u) {
        vec3 lo;
        vec3 hi;
        palette_box(entry, 0u, lo, hi);
        return hi.y;
    }
    return 8.0 / 9.0;
}

// One level's part in a corner's average: still water (from 0.8 up) counts ten times, open space as level 0, blocks
// that stop things not at all.
vec2 water_level_weight(float h) {
    return h >= 0.8 ? vec2(h * 10.0, 10.0) : h >= 0.0 ? vec2(h, 1.0) : vec2(0.0);
}

// The height of a water surface at one corner of its cell (level c), from the two cells beside that corner (a, b)
// and the one across it (diag): Minecraft's average, a full block where any of them has water over it.
float water_corner(float c, float a, float b, ivec3 diag) {
    if (a >= 1.0 || b >= 1.0) {
        return 1.0;
    }
    vec2 sum = water_level_weight(c) + water_level_weight(a) + water_level_weight(b);
    if (a > 0.0 || b > 0.0) {
        float d = water_level(diag);
        if (d >= 1.0) {
            return 1.0;
        }
        sum += water_level_weight(d);
    }
    return sum.x / max(sum.y, 1e-4);
}

// The top of a cell's water at its four corners (x, z: 00, 10, 01, 11), in blocks over the cell's floor, where it
// is drawn: flowing water slopes from block to block without steps.
vec4 water_corners(ivec3 cell) {
    float c = water_level(cell);
    float w = water_level(cell + ivec3(-1, 0, 0));
    float e = water_level(cell + ivec3(1, 0, 0));
    float n = water_level(cell + ivec3(0, 0, -1));
    float s = water_level(cell + ivec3(0, 0, 1));
    return vec4(water_corner(c, w, n, cell + ivec3(-1, 0, -1)), water_corner(c, e, n, cell + ivec3(1, 0, -1)),
                water_corner(c, w, s, cell + ivec3(-1, 0, 1)), water_corner(c, e, s, cell + ivec3(1, 0, 1)));
}

// How far a point is over the water's surface in its cell (negative: under it), the surface between its corners.
float over_water(ivec3 cell, vec4 corners, vec3 q) {
    vec2 f = clamp(q.xz - vec2(cell.xz), 0.0, 1.0);
    float top = mix(mix(corners.x, corners.y, f.x), mix(corners.z, corners.w, f.x), f.y);
    return q.y - float(cell.y) - top;
}

// How far a ray from p (vox_pos space) runs through water before it comes out of it (into air or anything else),
// walking the blocks one by one: the water a ray of sunlight crossed to reach p. In the water's top blocks the ray
// leaves through the surface where Minecraft draws it, each block's own height, sloped between its corners (flowing
// water is lower than still water, so a light path that took every top block as full drew a grid of block-sized
// steps on the ground under it). -1 when the walk leaves the copy or runs out of steps still in water (the shadow
// map's own depth is the fallback then), 0 when p isn't in water.
float water_path(vec3 p, vec3 dir, int max_steps) {
    ivec3 cell = ivec3(floor(p));
    if (!vox_in_box(cell, 0) || (vox_word(cell) & VOX_WATER) == 0u) {
        return 0.0;
    }
    ivec3 step_dir = ivec3(sign(dir));
    vec3 inv_dir = safe_inverse(dir);
    vec3 next = (vec3(cell) + step(0.0, dir) - p) * inv_dir;
    vec3 delta = abs(inv_dir);
    float t_in = 0.0;
    for (int i = 0; i < max_steps; i++) {
        float t_out = min3(next);
        ivec3 above = cell + ivec3(0, 1, 0);
        if (!vox_in_box(above, 0) || (vox_word(above) & VOX_WATER) == 0u) {
            // A top block: where the ray comes up through its surface, if it does in this block.
            vec4 corners = water_corners(cell);
            float over_in = over_water(cell, corners, p + dir * t_in);
            if (over_in >= 0.0) {
                return t_in;
            }
            float over_out = over_water(cell, corners, p + dir * t_out);
            if (over_out >= 0.0) {
                return t_in + (t_out - t_in) * (-over_in) / max(over_out - over_in, 1e-6);
            }
        }
        if (next.x < next.y && next.x < next.z) {
            next.x += delta.x;
            cell.x += step_dir.x;
        } else if (next.y < next.z) {
            next.y += delta.y;
            cell.y += step_dir.y;
        } else {
            next.z += delta.z;
            cell.z += step_dir.z;
        }
        t_in = t_out;
        if (!vox_in_box(cell, 0)) {
            return -1.0;
        }
        if ((vox_word(cell) & VOX_WATER) == 0u) {
            return t_out;
        }
    }
    return -1.0;
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

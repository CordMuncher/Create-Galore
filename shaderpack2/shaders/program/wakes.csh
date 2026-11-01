// Wakes (lib/wakes.glsl): one step of the ripples' two height fields each frame. Each cell's height is pulled towards
// its neighbours' (the wave equation: one field's ripples run out at 0.8 blocks a second, the other's at 2.2, so
// whatever moves faster than either trails a V of it, narrower the faster it goes, and something slower sends rings
// ahead of itself), the finest ripples smooth out within a moment (as water's own stickiness takes them), and all of
// it dies away over a few seconds, faster near the field's edge so nothing bounces back from it.
//
// What moves through the surface comes from the Create Galore Bridge (1.175.0 and later; an older one gives none):
// cg_wake_count of them in cg_wake0 to cg_wake7, nearest first, each
// - [0]: where (scene space; y the water's surface under it) and how much of it cuts the surface (0..1),
// - [1]: how fast it goes (blocks a second),
// - [2]: its outline at the surface: half its length along its heading, half its width, its heading (x, z),
// - [3]: x how round its ends are (1 a mob or a boat, less a ship's hull).
// The water it pushes aside rises in a band just outside its outline where it arrives and sinks where it leaves (the
// difference between where it is now and a frame ago), so a thing that stays still leaves the water flat, and the
// fields hold still inside its outline (the hull keeps the water out).

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"
#include "/lib/wakes.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const ivec3 workGroups = ivec3(64, 64, 1);

uniform float frameTime;
uniform int frameCounter;
uniform int cg_wake_count;
uniform mat4 cg_wake0;
uniform mat4 cg_wake1;
uniform mat4 cg_wake2;
uniform mat4 cg_wake3;
uniform mat4 cg_wake4;
uniform mat4 cg_wake5;
uniform mat4 cg_wake6;
uniform mat4 cg_wake7;

const vec2 wake_speeds = vec2(0.8, 2.2); // blocks a second, each field's
const float cell = 1.0 / float(wake_per_block);
const float wake_damping = 0.6;          // how fast the ripples die away (a second)
const float wake_smoothing = 0.01;       // how fast the finest ones smooth out (blocks squared a second)

mat4 mover(int i) {
    return i == 0 ? cg_wake0 : i == 1 ? cg_wake1 : i == 2 ? cg_wake2 : i == 3 ? cg_wake3 :
           i == 4 ? cg_wake4 : i == 5 ? cg_wake5 : i == 6 ? cg_wake6 : cg_wake7;
}

// How far a point (blocks, beside the outline's middle) lies outside a mover's outline (less than 0 inside): a
// rectangle with rounded ends.
float outline_distance(vec2 q, mat4 m) {
    vec2 way = m[2].zw;
    vec2 local = vec2(dot(q, way), dot(q, vec2(-way.y, way.x)));
    vec2 half_size = m[2].xy;
    float r = min(half_size.x, half_size.y) * m[3].x;
    vec2 d = abs(local) - (half_size - r);
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

// The water a mover pushes aside: all of it inside the outline, fading out over a band outside it.
float pushed(float outside, float band) {
    return 1.0 - smoothstep(0.0, band, outside);
}

// Whether the fields held a cell last frame as well (a cell they only now reach starts flat).
bool reached(ivec2 c, ivec2 lo, ivec2 last_lo) {
    return wake_in_window(c, lo) && wake_in_window(c, last_lo);
}

// A cell's heights in the two fields, last frame's.
vec2 last_heights(ivec2 c, ivec2 lo, ivec2 last_lo, uint from) {
    return reached(c, lo, last_lo) ? wake_sane(unpackHalf2x16(data_load(wake_word(from, c)))) : vec2(0.0);
}

void main() {
    ivec2 slot = ivec2(gl_GlobalInvocationID.xy);
    ivec2 lo = wake_window_lo(cameraPositionInt);
    ivec2 last_lo = wake_window_lo(previousCameraPositionInt);
    ivec2 c = lo + ((slot - lo) & (wake_side - 1)); // the world cell this slot holds this frame
    uint from = wake_state_base(frameCounter, false);
    uint from_rates = wake_state_base(frameCounter, true);
    uint to = wake_state_base(frameCounter + 1, false);
    uint to_rates = wake_state_base(frameCounter + 1, true);

    vec2 h = last_heights(c, lo, last_lo, from);
    vec2 v = reached(c, lo, last_lo) ? wake_sane(unpackHalf2x16(data_load(wake_word(from_rates, c)))) : vec2(0.0);
    // At the fields' edge a missing neighbour counts as this cell (the slope there is flat).
    vec2 xm = wake_in_window(c - ivec2(1, 0), lo) ? last_heights(c - ivec2(1, 0), lo, last_lo, from) : h;
    vec2 xp = wake_in_window(c + ivec2(1, 0), lo) ? last_heights(c + ivec2(1, 0), lo, last_lo, from) : h;
    vec2 zm = wake_in_window(c - ivec2(0, 1), lo) ? last_heights(c - ivec2(0, 1), lo, last_lo, from) : h;
    vec2 zp = wake_in_window(c + ivec2(0, 1), lo) ? last_heights(c + ivec2(0, 1), lo, last_lo, from) : h;
    vec2 dx = xp - xm, dz = zp - zm;
    data_store(wake_word(data_wake_slope, c), packHalf2x16(vec2(dx.x + dx.y, dz.x + dz.y) * (0.5 / cell)));

    // Never a longer step than the waves allow (below 30 frames a second they slow down a little).
    float dt = clamp(frameTime, 0.0, 1.0 / 30.0);
    vec2 lap = (xm + xp + zm + zp - 4.0 * h) / (cell * cell);
    v += wake_speeds * wake_speeds * lap * dt;
    // The last 3 blocks before the edge soak the ripples up.
    ivec2 from_lo = c - lo;
    float edge = float(min(min(from_lo.x, from_lo.y), min(wake_side - 1 - from_lo.x, wake_side - 1 - from_lo.y))) * cell;
    float damping = wake_damping + 6.0 * (1.0 - smoothstep(0.0, 3.0, edge));
    v *= exp(-damping * dt);
    h = (h + (v + wake_smoothing * lap) * dt) * exp(-0.25 * dt);

    // What moves through the surface here.
    vec2 here = vec2(c - cameraPositionInt.xz * wake_per_block) * cell + 0.5 * cell - cameraPositionFract.xz;
    float held = 0.0;
    for (int i = 0; i < min(cg_wake_count, 8); i++) {
        mat4 m = mover(i);
        float strength = m[0].w;
        vec2 q = here - m[0].xz;
        float size = min(m[2].x, m[2].y);
        if (strength <= 0.0 || dot(q, q) > sqr(max(m[2].x, m[2].y) + 2.0)) {
            continue;
        }
        float band = 0.35 + 0.25 * size;
        float now = outline_distance(q, m);
        float before = outline_distance(q + m[1].xz * dt, m);
        float speed = length(m[1].xz);
        // How high the water it pushes aside rises: more for a faster and a bigger one.
        float rise = 0.12 * strength * clamp(speed / 4.0, 0.3, 1.2) * clamp(sqrt(size / 0.7), 1.0, 2.0);
        // Going down into the water (or bobbing) lifts the water around it.
        h += rise * (pushed(now, band) - pushed(before, band)) - m[1].y * dt * 0.5 * strength * pushed(now, band);
        held = max(held, strength * clamp(0.5 - now / cell, 0.0, 1.0));
    }
    h *= 1.0 - held;
    v *= 1.0 - held;
    data_store(wake_word(to, c), packHalf2x16(h));
    data_store(wake_word(to_rates, c), packHalf2x16(v));
}

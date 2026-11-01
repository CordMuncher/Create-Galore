// The sun's shadows past the shadow map: mountains shading mountains and forests their floors, out to about 1000
// blocks round you, in Distant Horizons' terrain too, worked out once and kept.
// - The ground's height comes from the Create Galore Bridge (1.175.0 and later, with Distant Horizons): one height
//   for each 4 x 4 blocks (a cell), 512 x 512 cells round the camera, in the rows of cg_palette_tex after the faces'
//   (see far_height in program/far_shadow.csh). The window is fixed to the world: a cell keeps its slot (its place
//   modulo 512) while the window, which moves 16 cells at a time, keeps it in.
// - program/far_shadow.csh works out, for each cell, how high a point there must be to see the light past the ground
//   between it and the light (the highest of the cells towards the light, each less the light's climb from there),
//   a sixteenth of the map each frame, so each cell follows the sun within a quarter of a second, and keeps it in
//   cg_sky_img (rows 568 on: two cells a texel). It works that out twice: past all the ground, and past only the
//   ground the shadow map doesn't draw (further than its reach from the camera).
// - A point is in the far shadow where it is under that height (softer further off). Past the shadow map, and in
//   Distant Horizons' terrain, the first height decides; the map's last stretch fades into it (lib/shadow.glsl).
//   Where the map reaches, it shades what it draws itself, and the second height adds the far ground's shade. The
//   cell's own ground doesn't count (it would shade its own slope), so a single tree shades the cells beyond it, not
//   its own.
// Without the Bridge's heights (no Distant Horizons, an older Bridge) every cell is unknown and lit, as before.

#if !defined LIB_FAR_SHADOW
#define LIB_FAR_SHADOW

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"

#if !defined CG_SKY_TEX
#define CG_SKY_TEX
uniform sampler2D cg_sky_tex; // the air's tables and these heights (see lib/atmosphere.glsl)
#endif

const int far_size = 512;     // cells across the window
const float far_cell = 4.0;   // blocks a cell
const ivec2 far_origin = ivec2(0, 568); // in cg_sky_img: 256 x 512 texels, two cells each (x and x + 1)
const float far_unknown = -60000.0;    // the cell's ground isn't known: lit
const float far_open = -20000.0;       // nothing towards the light rises over it
// How far from the camera (blocks, x, y and z) the shadow map draws what casts: Iris draws only the chunk sections
// within shadowDistance * shadowDistanceRenderMul of it (a box), whole sections, so half one more.
const float far_map_reach = shadowDistance * shadowDistanceRenderMul + 8.0;

// A height that was worked out (garbage in an image never written yet, a NaN, isn't).
bool far_known(float h) {
    return h > -40000.0 && h < 30000.0;
}

// The window's low corner (cells): the camera's cell less 256, rounded down to a multiple of 16, as the Bridge works
// it out.
ivec2 far_low() {
    ivec2 cell = ivec2(floor(vec2(cameraPositionInt.xz) / far_cell));
    return ((cell - far_size / 2) >> 4) << 4;
}

// The cell a slot holds now.
ivec2 far_cell_of(ivec2 slot, ivec2 low) {
    return low + ((slot - low) & (far_size - 1));
}

// The texel a cell's results are kept in, and its pair of channels there: how high a point must be to see the light
// past all the ground towards it, then past only the ground the shadow map doesn't draw.
ivec2 far_texel(ivec2 cell) {
    ivec2 slot = cell & (far_size - 1);
    return far_origin + ivec2(slot.x >> 1, slot.y);
}

int far_channel(ivec2 cell) {
    return (cell.x & 1) * 2;
}

#if defined SUNLIGHT && !defined PROGRAM_SHADOW
// How much of the light gets past the far ground to a point (scene space): x past all of it (a point the shadow map
// doesn't reach), y past only what the map doesn't draw (one it does: the far mountain's shade on the valley you're
// in at sunset). 1 where nothing is known.
vec2 far_sun(vec3 scene_pos) {
    vec3 p = scene_pos + cameraPosition;
    vec2 at = p.xz / far_cell - 0.5;
    ivec2 base = ivec2(floor(at));
    vec2 f = at - vec2(base);
    ivec2 low = far_low();
    // Fading out over the window's outer 32 cells (128 blocks), where a move has just brought in cells still being
    // read and worked out.
    vec2 in_window = at - vec2(low);
    float edge = min(min(in_window.x, in_window.y), min(float(far_size - 1) - in_window.x, float(far_size - 1) - in_window.y));
    float fade = smoothstep(0.0, 32.0, edge);
    if (fade <= 0.0) {
        return vec2(1.0);
    }
    // A cell nothing shades counts as well under the point, so the shade's edge falls between the cells as it does
    // between two shaded ones.
    float open = p.y - 16.0;
    vec2 height = vec2(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 cell = base + o;
        vec4 t = texelFetch(cg_sky_tex, far_texel(cell), 0);
        vec2 h = (cell.x & 1) == 0 ? t.xy : t.zw;
        if (!far_known(h.x) || !far_known(h.y)) {
            continue;
        }
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) + 1e-3;
        height += max(h, vec2(open)) * w;
        total += w;
    }
    if (total <= 0.01) {
        return vec2(1.0);
    }
    height /= total;
    // Soft over a block or two up close, wider far off (the cells' heights are coarse); a cell's ground sits up to
    // a couple of blocks under the highest of its columns.
    float soft = 0.75 + length(scene_pos) * 0.004;
    vec2 lit = smoothstep(vec2(-soft), vec2(soft), p.y - height + 2.0 + soft);
    return mix(vec2(1.0), lit, fade);
}
#else
vec2 far_sun(vec3 scene_pos) {
    return vec2(1.0);
}
#endif

#endif

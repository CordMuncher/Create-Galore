// The far sun shadows' heights (lib/far_shadow.glsl): for each cell of the Bridge's far heights, how high a point
// there must be to see the sun (or the moon) past the ground towards it, and past only the ground the shadow map
// doesn't draw. A sixteenth of the map each frame (32 rows of texels, 2 cells each), so the whole of it follows the
// light within 16 frames; each cell looks along the light, a cell at a time, out to 640 blocks or until nothing in
// the world could rise over the light's way any more.

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"
#include "/lib/globals.glsl"
#include "/lib/world.glsl"
#include "/lib/far_shadow.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const ivec3 workGroups = ivec3(32, 4, 1);

layout(rgba16f) writeonly uniform image2D cg_sky_img;
uniform int frameCounter;

// The Bridge's far heights: in the palette's rows after the faces' (256 + 48 + 512), a word a cell (slot z * 512 +
// x), 4096 words a row: bits 0..15 the ground's top plus 4096, 16..23 which place the slot holds (the cell divided by
// 512, x and z, 4 bits each), 31 filled.
const int far_first_row = 816;
const float far_world_top = 384.0; // nothing rises over this

float far_height(ivec2 cell) {
    ivec2 slot = cell & (far_size - 1);
    int i = slot.y * far_size + slot.x;
    uint w = texelFetch(cg_palette_tex, ivec2(i & 4095, far_first_row + (i >> 12)), 0).r;
    uint place = uint((cell.x >> 9) & 15) | (uint((cell.y >> 9) & 15) << 4u);
    if ((w & 0x80000000u) == 0u || ((w >> 16u) & 255u) != place) {
        return far_unknown;
    }
    return float(w & 0xffffu) - 4096.0;
}

// Whether the shadow map leaves a cell's ground out (it draws only what lies within its reach of the camera).
bool past_map(ivec2 cell, float height) {
    vec2 d = abs((vec2(cell) + 0.5) * far_cell - cameraPosition.xz);
    return max(d.x, d.y) > far_map_reach || height > cameraPosition.y + far_map_reach;
}

// How high a point in this cell must be to see the light: the highest of the cells towards it, each less what the
// light's way climbs from there (the cell's own ground doesn't count); x all of them, y those the map leaves out.
vec2 lit_above(ivec2 cell, vec2 way, float rise) {
    float own = far_height(cell);
    if (!far_known(own)) {
        return vec2(far_unknown);
    }
    vec2 h = vec2(far_open);
    for (int k = 1; k <= 160; k++) {
        float drop = rise * float(k);
        if (far_world_top - drop < max(h.y, own - 8.0)) {
            break;
        }
        ivec2 there = cell + ivec2(round(way * float(k)));
        float hk = far_height(there);
        if (far_known(hk)) {
            h.x = max(h.x, hk - drop);
            if (past_map(there, hk)) {
                h.y = max(h.y, hk - drop);
            }
        }
    }
    return h;
}

void main() {
    ivec2 t = ivec2(gl_GlobalInvocationID.xy) + ivec2(0, (frameCounter & 15) * 32);
    ivec2 low = far_low();
    vec3 l = g_light_dir.xyz;
    float across = length(l.xz);
    vec4 result;
    for (int i = 0; i < 2; i++) {
        ivec2 cell = far_cell_of(ivec2(t.x * 2 + i, t.y), low);
        vec2 h;
        if (l.y <= 0.0 || across < 1e-3) {
            // The light overhead casts no far shadow; under the horizon there is none to cast.
            h = vec2(far_known(far_height(cell)) ? far_open : far_unknown);
        } else {
            h = lit_above(cell, l.xz / across, far_cell * l.y / across);
        }
        if (i == 0) {
            result.xy = h;
        } else {
            result.zw = h;
        }
    }
    imageStore(cg_sky_img, far_origin + t, result);
}

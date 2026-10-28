// The overworld's shadowcomp passes 9 and 10: the shore around the water near you (see shore.glsl), from the water's
// tops the shadow pass marked. Pass 9 blurs across x, pass 10 across z (SHORE_PASS: which of them this is): closely,
// how much of the ground isn't open water (past the grid's edge counted as water, so no foam gathers there); widely,
// how much of it is (only what lies in the grid counted).

#define PROGRAM_SHORE
#include "/lib/shore.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(16, 16, 1);

layout(r32ui) uniform uimage2D cg_water_marks_img;

void main() {
    ivec2 column = ivec2(gl_GlobalInvocationID.xy);
#if SHORE_PASS == 0
    const ivec2 across = ivec2(1, 0);
#else
    const ivec2 across = ivec2(0, 1);
#endif
    float near = 0.0;
    float near_weight = 0.0;
    float open = 0.0;
    float open_weight = 0.0;
    for (int i = -shore_wide_reach; i <= shore_wide_reach; i++) {
        ivec2 at = column + across * i;
        bool inside = all(greaterThanEqual(at, ivec2(0))) && all(lessThan(at, ivec2(shore_size)));
        vec2 v = vec2(0.0); // x not open water, y open water
        if (inside) {
#if SHORE_PASS == 0
            float water = imageLoad(cg_water_marks_img, shore_top_texel(at)).r != 0u ? 1.0 : 0.0;
            v = vec2(1.0 - water, water);
#else
            v = shore_unpack(imageLoad(cg_water_marks_img, shore_across_texel(at)).r);
#endif
        }
        float x = float(i);
        if (abs(i) <= shore_near_reach) {
            float w = exp(-0.5 * x * x / (shore_near_sigma * shore_near_sigma));
            near += w * v.x;
            near_weight += w;
        }
        if (inside) {
            float w = exp(-0.5 * x * x / (shore_wide_sigma * shore_wide_sigma));
            open += w * v.y;
            open_weight += w;
        }
    }
    uint word = shore_pack(vec2(near / near_weight, open / max(open_weight, 1e-6)));
#if SHORE_PASS == 0
    imageStore(cg_water_marks_img, shore_across_texel(column), uvec4(word, 0u, 0u, 0u));
#else
    imageStore(cg_water_marks_img, shore_field_texel(column), uvec4(word, 0u, 0u, 0u));
#endif
}

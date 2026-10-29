// Reading the per-block light (cg_blk_tex, see program/voxel_light.csh) and bounce light (cg_gi_tex, see
// program/gi.csh) at any point, smoothly: blended over the eight blocks around it, leaving out solid ones (which hold
// nothing), and fading out towards the edge of the voxel box, where Minecraft's own light takes over.

#if !defined LIB_VOXEL_SAMPLE
#define LIB_VOXEL_SAMPLE

#include "/lib/voxel.glsl"

uniform sampler3D cg_blk_tex;
uniform sampler3D cg_gi_tex;

// A per-block table at p (vox_pos space): rgb, and a how much of it came from blocks that hold something (0..1).
vec4 sample_block_table(sampler3D table, vec3 p) {
    vec3 c = p - 0.5;
    ivec3 base = ivec3(floor(c));
    vec3 f = c - vec3(base);
    ivec3 slot = vox_slot(base, 0);
    vec4 v;
    if (all(lessThan(slot, vox_size - 1))) {
        // The eight sit side by side in the table: the hardware blends them.
        v = texture(table, (vec3(slot) + 0.5 + f) / vec3(textureSize(table, 0)));
    } else {
        // They wrap around the table's edge: one by one.
        v = vec4(0.0);
        for (int i = 0; i < 8; i++) {
            ivec3 o = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
            vec3 w3 = mix(1.0 - f, f, vec3(o));
            v += texelFetch(table, vox_slot(base + o, 0), 0) * (w3.x * w3.y * w3.z);
        }
    }
    return v.a > 1e-3 ? vec4(v.rgb / v.a, v.a) : vec4(0.0);
}

// How far inside the voxel box a point is: 1 well inside, falling to 0 over the last part of the way to its edge.
float box_fade(vec3 p) {
    vec3 r = abs(p) / vec3(vox_half);
    return 1.0 - smoothstep(0.82, 0.96, max3(r));
}

#endif

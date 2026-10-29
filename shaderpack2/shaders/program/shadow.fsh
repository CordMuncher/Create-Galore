// The shadow pass's fragments (see shadow.vsh and shadow.gsh): the shadow map's depth and the colour of what
// light passes through, or, for a voxel triangle, the cells of the voxel copy it covers.
//
// A voxel fragment marks the cell just behind its face (a half cell in, against the face's normal) at its detail
// level and at every coarser one, so a cube becomes a shell one cell thick at each level; rays always meet the shell
// first. Its alpha decides: the most opaque of its texel and the four next to it, above 0.1 a cell, below 0.9 a
// see-through one (glass, water). The block's own cell (level 0) also gathers the average colour of what is drawn in
// it, from a few fragments per face.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/blocks.glsl"
#include "/lib/view.glsl"

uniform sampler2D gtexture;
uniform float frameTimeCounter;

#ifdef PROGRAM_SHADOW_TERRAIN
#include "/lib/voxel.glsl"

layout(r32ui) uniform uimage3D cg_vox_img; // the words, then the colour sums (see lib/voxel.glsl)

in vec2 fs_uv;
in vec4 fs_color;
in vec3 fs_scene;
in float fs_depth;
flat in int fs_block;
flat in int fs_voxel;
flat in vec3 fs_normal;
flat in uint fs_vox_bits;
#else
// Mobs and block entities come without a geometry stage.
in vec2 vs_uv;
in vec4 vs_color;
in vec3 vs_scene;
flat in int vs_block;
#define fs_uv vs_uv
#define fs_color vs_color
#define fs_scene vs_scene
#define fs_block vs_block
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

#ifdef PROGRAM_SHADOW_TERRAIN

// The colour samples per block cell stop at this many (so the sums never run over).
const uint color_samples_max = 250u;

void mark_voxels() {
    int level = fs_voxel & 15;
    int axis = (fs_voxel >> 4) & 3;

    float alpha = textureLod(gtexture, fs_uv, 0.0).a;
    alpha = max(alpha, textureLodOffset(gtexture, fs_uv, 0.0, ivec2(1, 0)).a);
    alpha = max(alpha, textureLodOffset(gtexture, fs_uv, 0.0, ivec2(-1, 0)).a);
    alpha = max(alpha, textureLodOffset(gtexture, fs_uv, 0.0, ivec2(0, 1)).a);
    alpha = max(alpha, textureLodOffset(gtexture, fs_uv, 0.0, ivec2(0, -1)).a);
    if (alpha <= 0.1) {
        return;
    }
    bool water = (fs_vox_bits & VOX_WATER) != 0u;
    bool see_through = alpha < 0.9 || water;

    // This fragment's cell at its level, from the pixel (across the plane) and the depth (along it), in cells from
    // the box's middle; then half a cell in behind the face.
    vec2 half_plane = axis == 0 ? vec2(vox_half.z, vox_half.y) : axis == 1 ? vec2(vox_half.x, vox_half.z) : vec2(vox_half.xy);
    vec2 across = floor(gl_FragCoord.xy) + 0.5 - half_plane;
    vec3 c = axis == 0 ? vec3(fs_depth, across.y, across.x) : axis == 1 ? vec3(across.x, fs_depth, across.y) : vec3(across, fs_depth);
    float s = exp2(float(level));
    vec3 inside = c / s - fs_normal * (0.5 / s);

    for (int l = level; l >= 0; l--) {
        ivec3 cell = vox_cell(inside, l);
        if (!vox_in_box(cell)) {
            continue;
        }
        uint bits = see_through ? vox_see_bit(l) : vox_solid_bit(l);
        if (l == 0) {
            bits |= fs_vox_bits;
        }
        ivec3 slot = vox_slot(cell, l);
        if ((imageLoad(cg_vox_img, slot).r & bits) != bits) {
            imageAtomicOr(cg_vox_img, slot, bits);
        }
        if (l == 0) {
            // The colour from a 2 x 2 grid of this face's fragments in the block (all of them at level 0).
            ivec2 sub = ivec2(floor(across)) & ((1 << level) - 1);
            int step = max((1 << level) / 2, 1);
            if (all(equal(sub % step, ivec2(step / 2)))) {
                uint count = imageAtomicAdd(cg_vox_img, slot, 1u << VOX_COUNT_SHIFT) >> VOX_COUNT_SHIFT;
                if (count < color_samples_max) {
                    vec4 texel = textureLod(gtexture, fs_uv, 0.0);
                    uvec4 q = uvec4(clamp(vec4(texel.rgb * fs_color.rgb, texel.a), 0.0, 1.0) * 255.0 + 0.5);
                    imageAtomicAdd(cg_vox_img, slot + vox_rg_offset, q.r | (q.g << 16u));
                    imageAtomicAdd(cg_vox_img, slot + vox_ba_offset, q.b | (q.a << 16u));
                }
            }
        }
    }
}

// Light under water: the bright net the waves focus it into, two layers drifting apart. Its own little cell noise:
// for each point, how close it is to the edge between its two nearest cells.
float cell_edge(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(x, y);
            vec2 h = hash33(uvec3(ivec3(cell + o + 4096.0, 9))).xy;
            vec2 r = o + 0.15 + 0.7 * h - f;
            float d = dot(r, r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return sqrt(d2) - sqrt(d1);
}

float caustics(vec2 world_xz, float t) {
    float a = cell_edge(world_xz * 0.85 + vec2(0.21, 0.13) * t);
    float b = cell_edge(world_xz * 1.2 + vec2(-0.12, 0.19) * t + 17.3);
    float net = (1.0 - smoothstep(0.0, 0.22, a)) + (1.0 - smoothstep(0.0, 0.22, b));
    return clamp(0.35 + 0.45 * net, 0.0, 1.0);
}

#endif

void main() {
#ifdef PROGRAM_SHADOW_TERRAIN
    if (fs_voxel >= 0) {
        mark_voxels();
        discard;
    }
#endif

    vec4 texel = texture(gtexture, fs_uv);
    float alpha = texel.a;
#ifdef PROGRAM_SHADOW_TERRAIN
    // Leaves far off cast from a blurrier copy of their texture, so their shadows don't flicker as you move.
    if (fs_block == BLOCK_LEAVES && dot(fs_scene, fs_scene) > 24.0 * 24.0) {
        alpha = textureLod(gtexture, fs_uv, 2.0).a * 1.4;
    }
#endif
    if (alpha < 0.1) {
        discard;
    }

#ifdef PROGRAM_SHADOW_WATER
    if (fs_block == BLOCK_WATER) {
        // Water: its colour times the caustics; alpha below a half says "water" (its depth absorbs the light).
        vec2 world = fs_scene.xz + cameraPosition.xz;
        vec3 tint = clamp(fs_color.rgb * 1.15, 0.0, 1.0);
        out_color = vec4(tint * caustics(world, frameTimeCounter), 0.0);
    } else {
        // Glass and the like: light takes on their colour, as strongly as they are opaque.
        vec3 tint = mix(vec3(1.0), texel.rgb * fs_color.rgb, clamp(alpha * 1.2, 0.0, 1.0));
        out_color = vec4(tint, 1.0);
    }
#else
    out_color = vec4(1.0);
#endif
}

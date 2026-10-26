// The overworld's shadow map: everything that casts a shadow, drawn from the sun (or the moon) straight, without
// warping, so every texel covers the same ground and the shadows hold still (see shadow.glsl). Iris sets the
// shadow camera's matrices as gl_ModelViewMatrix and gl_ProjectionMatrix, and snaps it to whole steps as you move.
//
// The world's blocks (shadow_solid, shadow_cutout, shadow_water: PROGRAM_SHADOW_TERRAIN) also mark what each block
// around you is for the coloured light and the bounce light (see light_volume.glsl): lights, solid cubes, tinting
// glass and leaves; and hand their colour and facing on for the bounce light (shadow.fsh). And for the ripples (see
// water.glsl, shadow.fsh) the water's surface, and everything else what reaches into it.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/gbuffer.glsl"

out vec2 v_uv;
out float v_alpha;

#if defined LIT_VOXELS || defined LIT_WATER_RIPPLES
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPositionFract;
#endif
#if defined PROGRAM_SHADOW_TERRAIN && (defined LIT_VOXELS || defined LIT_WATER_RIPPLES)
in vec4 mc_Entity; // the block's number in block.properties
#endif
#if defined PROGRAM_SHADOW_TERRAIN && defined LIT_GI && !defined PROGRAM_SHADOW_WATER
out vec3 v_tint;   // the vertex colour without Minecraft's face shading (the biome's grass and leaf colours)
out vec3 v_normal; // world space
#endif
#ifdef LIT_WATER_RIPPLES
out vec3 v_scene_pos;
#ifdef PROGRAM_SHADOW_WATER
flat out int v_water_top; // 1 on the top of water
#endif
#endif

#if defined PROGRAM_SHADOW_TERRAIN && defined LIT_VOXELS
#include "/lib/light_volume.glsl"
#include "/lib/color.glsl"

in vec4 at_midBlock;    // xyz from the vertex to its block's centre (1/64 blocks), w the light the block gives (0..15)
in vec2 mc_midTexCoord; // the middle of the vertex's face in the texture atlas

uniform sampler2D gtexture;
layout(r32ui) uniform uimage3D cg_voxel_img;

#ifdef LIT_COLORED_LIGHT

// The colours of the lights block.properties lists (linear, brightest channel 1).
vec3 listed_light_color(int id) {
    switch (id) {
        case 10101: return vec3(1.0, 0.12, 0.05);  // redstone
        case 10102: return vec3(0.15, 0.75, 1.0);  // soul fire
        case 10103: return vec3(0.75, 0.9, 1.0);   // sea lantern, beacon, conduit, end rod
        case 10104: return vec3(0.6, 0.25, 1.0);   // crying obsidian, amethyst, nether portal
        case 10105: return vec3(0.55, 1.0, 0.45);  // verdant froglight
        case 10106: return vec3(1.0, 0.6, 0.9);    // pearlescent froglight
        case 10107: return vec3(1.0, 0.4, 0.1);    // lava, magma
        case 10108: return vec3(0.1, 0.85, 1.0);   // sculk
        default: return vec3(0.7, 0.95, 0.85);     // glow lichen
    }
}

// Any other light's colour from its face's texture: its texels weighted to the bright and colourful ones (the flame,
// not the torch's stick), linear. uv: this vertex's corner of the face, which spans as far the other side of mid.
vec3 face_light_color(vec2 uv, vec2 mid) {
    vec2 atlas = vec2(textureSize(gtexture, 0));
    vec2 half_extent = abs(uv - mid);
    ivec2 lo = ivec2(floor((mid - half_extent) * atlas + 0.5));
    ivec2 hi = max(ivec2(floor((mid + half_extent) * atlas + 0.5)), lo + 1);
    // Every texel of a face up to 16 by 16, every few of a bigger one.
    ivec2 stride = max((hi - lo + 15) / 16, ivec2(1));
    vec3 sum = vec3(0.0);
    float weight_sum = 0.0;
    for (int y = 0; y < 16; y++) {
        for (int x = 0; x < 16; x++) {
            ivec2 t = lo + ivec2(x, y) * stride;
            if (any(greaterThanEqual(t, hi))) {
                continue;
            }
            vec4 texel = texelFetch(gtexture, t, 0);
            vec3 c = srgb_to_linear(texel.rgb);
            float peak = max(c.r, max(c.g, c.b));
            float saturation = peak > 1e-4 ? 1.0 - min(c.r, min(c.g, c.b)) / peak : 0.0;
            float w = texel.a * peak * peak * peak * (saturation + 0.05) * (saturation + 0.05) + 1e-8;
            sum += c * w;
            weight_sum += w;
        }
    }
    return sum / weight_sum;
}

// A light's colour (brightest channel 1): torchlight for the warm ones (whose texture's colour is close to a torch
// flame's), else its texture's colour, a little paler.
vec3 light_color(vec3 texture_color) {
    // A torch's flame as face_light_color sees it, in chromaticity (its share of red, green and blue).
    const vec3 flame = vec3(0.602, 0.355, 0.043);
    vec3 chroma = texture_color / max(texture_color.r + texture_color.g + texture_color.b, 1e-4);
    float own = smoothstep(0.15, 0.35, distance(chroma, flame));
    vec3 c = mix(vec3(1.0), texture_color / max(texture_color.r, max(texture_color.g, max(texture_color.b, 1e-4))), 0.8);
    vec3 torch = torch_color / torch_color.r;
    return mix(torch, c, own);
}
#endif

// uv: the vertex's place in the texture atlas.
void mark_block(vec3 scene_pos, vec2 uv) {
    ivec3 block = light_volume_block(scene_pos + at_midBlock.xyz / 64.0, cameraPositionFract);
    if (!in_light_volume(block)) {
        return;
    }
    int id = int(mc_Entity.x + 0.5);
#ifdef LIT_COLORED_LIGHT
    float level = at_midBlock.w;
    // One vertex of each face is enough for a light (they all read the same texture).
    if (level > 0.5 && (gl_VertexID & 3) == 0) {
        vec3 c = id >= BLOCK_LIGHT_RED && id <= BLOCK_LIGHT_LAST
                ? listed_light_color(id) : light_color(face_light_color(uv, mc_midTexCoord));
        imageAtomicMax(cg_voxel_img, block + ivec3(0, 0, section_lights * light_volume_depth), light_mark(level, c));
    }
#endif
#if defined PROGRAM_SHADOW_SOLID
    // A solid block stops the light if all its vertices sit on its corners (a full cube; a slab or a stair has some
    // inside the block).
    bool corner = all(greaterThan(abs(at_midBlock.xyz), vec3(31.5)));
    imageAtomicOr(cg_voxel_img, block, corner ? mark_solid : mark_partial);
#elif defined PROGRAM_SHADOW_WATER
    // Stained glass, ice, slime and honey tint the light through them; water lets it through as it is.
    if (id != BLOCK_WATER && (gl_VertexID & 3) == 0) {
        vec4 texel = textureLod(gtexture, mc_midTexCoord, 0.0);
        vec3 tint = mix(vec3(1.0), srgb_to_linear(texel.rgb), texel.a);
        imageAtomicOr(cg_voxel_img, block, tint_mark(tint / max(tint.r, max(tint.g, max(tint.b, 1e-4)))));
    }
#elif defined PROGRAM_SHADOW_CUTOUT
    if (id == BLOCK_LEAVES && (gl_VertexID & 3) == 0) {
        imageAtomicOr(cg_voxel_img, block, mark_foliage);
    }
#endif
}
#endif

void main() {
    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
    vec2 uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    v_uv = uv;
    v_alpha = gl_Color.a;
#if defined LIT_VOXELS || defined LIT_WATER_RIPPLES
    vec3 scene_pos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
#endif
#if defined PROGRAM_SHADOW_TERRAIN && defined LIT_VOXELS
    mark_block(scene_pos, uv);
#endif
#if defined PROGRAM_SHADOW_TERRAIN && defined LIT_GI && !defined PROGRAM_SHADOW_WATER
    v_normal = normalize(mat3(shadowModelViewInverse) * (gl_NormalMatrix * gl_Normal));
    v_tint = is_plant_normal(v_normal) ? gl_Color.rgb : gl_Color.rgb / face_shade(v_normal);
#endif
#ifdef LIT_WATER_RIPPLES
    v_scene_pos = scene_pos;
#ifdef PROGRAM_SHADOW_WATER
    vec3 normal = mat3(shadowModelViewInverse) * (gl_NormalMatrix * gl_Normal);
    v_water_top = int(mc_Entity.x + 0.5) == BLOCK_WATER && normal.y > 0.5 ? 1 : 0;
#endif
#endif
}

// The shadow pass's fragments (see shadow.vsh): the shadow map's depth, the colour of what light passes through, and
// where your own body is (what you hold looks past it, see shadow_light).
// (The voxel copy of the world no longer comes from this pass: the Create Galore Bridge keeps it, see lib/world.glsl.)

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/blocks.glsl"
#include "/lib/view.glsl"
#include "/lib/shadow.glsl"

uniform sampler2D gtexture;

in vec2 vs_uv;
in vec4 vs_color;
in vec3 vs_scene;
flat in int vs_block;

#ifdef PROGRAM_SHADOW_WATER
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;
#else
/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_player; // shadowcolor1: 1 where your own body is the nearest thing to the light
#endif

#if !defined PROGRAM_SHADOW_TERRAIN
uniform int entityId; // entity.properties
#endif

#ifdef PROGRAM_SHADOW_WATER
#include "/lib/water.glsl"

// How the waves focus the sunlight under the water (caustics): each texel's light, bent at its wave's slope (Snell's
// law), is followed 4 blocks down and the patch it lands on compared with the patch it left (the light from a
// texel's step either way says how big both are): where the slopes bend the light together it lands on less and is
// brighter, where they spread it, darker. 1 for flat water. (Worked out from the waves themselves, not from the
// texels around: those go wrong along the edges of the water's faces.)
float caustic_focus(vec3 scene_pos) {
    vec2 local = water_local(scene_pos);
    // This texel's size in blocks: how far it is from the map's middle, before the warp.
    float warped = length(gl_FragCoord.xy / float(shadowMapResolution) * 2.0 - 1.0);
    float r = warped * (1.0 - shadow_warp) / max(1.0 - warped * shadow_warp, 1e-3);
    float texel = 1.0 / shadow_texels_per_block(min(r, 1.0));
    float strength = WATER_WAVES * water_chop(scene_pos);
    float rain = g_water.x;
    // Where the light from here and from a texel's step either way lands, against where it left.
    vec2 land[3];
    for (int i = 0; i < 3; i++) {
        vec2 at = local + (i == 1 ? vec2(texel, 0.0) : i == 2 ? vec2(0.0, texel) : vec2(0.0));
        vec3 n = wave_normal(water_waves(at, strength, 1.0, rain, texel * 2.0));
        vec3 down = refract(-g_light_dir.xyz, n, 1.0 / water_ior);
        land[i] = down.xz * (4.0 / max(-down.y, 0.2));
    }
    mat2 spread = mat2(1.0) + mat2((land[1] - land[0]) / texel, (land[2] - land[0]) / texel);
    return clamp(1.0 / max(abs(determinant(spread)), 1e-4), 0.0, 4.0);
}
#endif

void main() {
    vec4 texel = texture(gtexture, vs_uv);
    float alpha = texel.a;
#ifdef PROGRAM_SHADOW_TERRAIN
    // Leaves far off cast from a blurrier copy of their texture, so their shadows don't flicker as you move.
    if (vs_block == BLOCK_LEAVES && dot(vs_scene, vs_scene) > 24.0 * 24.0) {
        alpha = textureLod(gtexture, vs_uv, 2.0).a * 1.4;
    }
#endif
    if (alpha < 0.1) {
        discard;
    }

#ifdef PROGRAM_SHADOW_WATER
    if (vs_block == BLOCK_WATER) {
        // Water: its biome's water colour (lib/water_color.glsl soaks up the light by it) and how its waves focus the
        // light, in alpha below a half (0.49 for four times the light), which also says "water".
#ifdef CAUSTICS
        float focus = caustic_focus(vs_scene);
#else
        float focus = 1.0;
#endif
        out_color = vec4(vs_color.rgb, 0.49 * focus / 4.0);
    } else {
        // Glass and the like: light takes on their colour, as strongly as they are opaque.
        vec3 tint = mix(vec3(1.0), texel.rgb * vs_color.rgb, clamp(alpha * 1.2, 0.0, 1.0));
        out_color = vec4(tint, 1.0);
    }
#else
    out_color = vec4(1.0);
#if defined PROGRAM_SHADOW_TERRAIN
    out_player = vec4(0.0);
#else
    out_player = vec4(entityId == ENTITY_PLAYER ? 1.0 : 0.0);
#endif
#endif
}

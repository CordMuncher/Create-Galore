// The shadow pass, drawn from the sun (or the moon): the shadow map (see lib/shadow.glsl) and, for the world's
// blocks (PROGRAM_SHADOW_TERRAIN: shadow_solid, shadow_cutout, shadow_water), the voxel copy of the world around
// you that the block light and the bounce light trace through (see shadow.gsh and lib/voxel.glsl). Iris hands this
// pass the shadow camera's matrices as gl_ModelViewMatrix and gl_ProjectionMatrix.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/shadow.glsl"

uniform mat4 shadowModelViewInverse;

#ifdef PROGRAM_SHADOW_TERRAIN
in vec4 mc_Entity;   // the block's number in block.properties
in vec4 at_midBlock; // xyz from the vertex to its block's middle (1/64 blocks), w the light the block gives (0..15)
#endif

out vec2 vs_uv;
out vec4 vs_color;    // the vertex colour: the biome's tint (Sodium puts the corner shading in alpha)
out vec3 vs_scene;    // scene space
out vec3 vs_normal;   // world space
out vec3 vs_mid;      // from the vertex to its block's middle, in blocks
out float vs_sky;     // Minecraft's sky light, 0..1
flat out int vs_block;
flat out float vs_emission; // the light the block gives, 0..15

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vs_scene = (shadowModelViewInverse * view).xyz;
    vec4 clip = gl_ProjectionMatrix * view;
    clip.xyz = shadow_warp_clip(clip.xyz);
    gl_Position = clip;

    vs_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    vs_color = gl_Color;
    vec3 normal = mat3(shadowModelViewInverse) * (gl_NormalMatrix * gl_Normal);
    vs_normal = dot(normal, normal) > 1e-8 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    vs_sky = clamp(gl_MultiTexCoord1.y / 240.0, 0.0, 1.0);
#ifdef PROGRAM_SHADOW_TERRAIN
    vs_mid = at_midBlock.xyz / 64.0;
    vs_block = int(mc_Entity.x + 0.5);
    vs_emission = at_midBlock.w;
#else
    vs_mid = vec3(0.0);
    vs_block = 0;
    vs_emission = 0.0;
#endif
}

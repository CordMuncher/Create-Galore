// The shadow pass, drawn from the sun (or the moon): the shadow map (see lib/shadow.glsl). Iris hands this pass the
// shadow camera's matrices as gl_ModelViewMatrix and gl_ProjectionMatrix.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/shadow.glsl"

uniform mat4 shadowModelViewInverse;

#ifdef PROGRAM_SHADOW_TERRAIN
in vec4 mc_Entity; // the block's number in block.properties
#endif

out vec2 vs_uv;
out vec4 vs_color;    // the vertex colour: the biome's tint (Sodium puts the corner shading in alpha)
out vec3 vs_scene;    // scene space
flat out int vs_block;

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vs_scene = (shadowModelViewInverse * view).xyz;
    vec4 clip = gl_ProjectionMatrix * view;
    clip.xyz = shadow_warp_clip(clip.xyz);
    gl_Position = clip;

    vs_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    vs_color = gl_Color;
#ifdef PROGRAM_SHADOW_TERRAIN
    vs_block = int(mc_Entity.x + 0.5);
#else
    vs_block = 0;
#endif
}

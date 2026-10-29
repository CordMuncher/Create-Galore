// Create's machines and contraptions in the shadow map, through Colorwheel (see shadow.vsh): Colorwheel hands the
// shadow camera's view over as gl_ModelViewMatrix. They cast shadows but stay out of the voxel copy of the world.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/shadow.glsl"

out vec2 v_uv;

void main() {
    vec4 clip = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
    clip.xyz = shadow_warp_clip(clip.xyz);
    gl_Position = clip;
    v_uv = gl_MultiTexCoord0.xy;
}

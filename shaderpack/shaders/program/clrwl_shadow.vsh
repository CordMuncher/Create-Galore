// Create's machines and contraptions in the overworld's shadow map, through Colorwheel (see shadow.vsh).

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"

out vec2 v_uv;

#ifdef LIT_WATER_RIPPLES
uniform mat4 shadowModelViewInverse;
out vec3 v_scene_pos;
#endif

void main() {
    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
    v_uv = gl_MultiTexCoord0.xy;
#ifdef LIT_WATER_RIPPLES
    v_scene_pos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
#endif
}

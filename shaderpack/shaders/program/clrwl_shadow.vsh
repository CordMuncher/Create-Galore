// Create's machines and contraptions in the overworld's shadow map, through Colorwheel (see shadow.vsh).

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/shadow_grid.glsl"

uniform mat4 shadowModelView;

out vec2 v_uv;

#ifdef LIT_WATER_RIPPLES
uniform mat4 shadowModelViewInverse;
out vec3 v_scene_pos;
#endif

void main() {
    vec4 shadow_view = gl_ModelViewMatrix * gl_Vertex;
    shadow_view.xy -= shadow_grid_shift(shadowModelView); // on the same grid as the world's (see shadow.vsh)
    gl_Position = gl_ProjectionMatrix * shadow_view;
    v_uv = gl_MultiTexCoord0.xy;
#ifdef LIT_WATER_RIPPLES
    v_scene_pos = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
#endif
}

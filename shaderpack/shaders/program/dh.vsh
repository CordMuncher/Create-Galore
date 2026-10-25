// Distant Horizons' terrain past the render distance: its own block colours, lit by the lightmap and fogged like the
// chunks in front of it, so the two meet without a seam. Iris hands its projection over as gl_ProjectionMatrix.

#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"

out vec4 v_color;
out vec3 v_fog_pos;

void main() {
    vec4 view_pos = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = place_on_screen(gl_ProjectionMatrix * view_pos);

    v_color = gl_Color;
    // Iris gives Distant Horizons' light at the lightmap's texel centres; Minecraft samples terrain between them.
    v_color.rgb *= lightmap_smooth(gl_MultiTexCoord1.xy - 1.0 / 32.0);
    v_fog_pos = view_to_scene(view_pos.xyz);
}

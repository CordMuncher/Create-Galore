// Distant Horizons' terrain and water. Its depth goes to dhDepthTex0, which the composite pass reads for its motion
// vectors and DLSS depth wherever Minecraft drew nothing.

#include "/lib/frame.glsl"
#include "/lib/vanilla.glsl"

in vec4 v_color;
in vec3 v_fog_pos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    out_color = vec4(apply_fog(v_color.rgb, v_fog_pos), v_color.a);
}

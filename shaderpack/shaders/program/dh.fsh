// Distant Horizons' terrain and water. Its depth goes to dhDepthTex0, which the composite pass reads for its motion
// vectors and DLSS depth wherever Minecraft drew nothing.
//
// Distant Horizons draws against a depth buffer of its own: its terrain before Minecraft's chunks, which then cover
// it, and its water after them, which nothing covers. So it only draws past the chunks Minecraft has (else its coarse
// copy shows through cave mouths and dug holes, and its water over the near shore), and its water only where
// Minecraft drew nothing.

#include "/lib/frame.glsl"
#include "/lib/vanilla.glsl"

#ifdef PROGRAM_DH_WATER
uniform sampler2D depthtex0;
#endif

in vec4 v_color;
in vec3 v_fog_pos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
#if defined PROGRAM_DH_TERRAIN || defined PROGRAM_DH_WATER
    // A chunk inside the render distance, where Minecraft's own chunks are (they are loaded in a cylinder).
    if (length(v_fog_pos.xz) < far - 16.0) {
        discard;
    }
#endif
#ifdef PROGRAM_DH_WATER
    if (texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r < 1.0) {
        discard;
    }
#endif
    out_color = vec4(apply_fog(v_color.rgb, v_fog_pos), v_color.a);
}

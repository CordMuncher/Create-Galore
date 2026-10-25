// Distant Horizons' terrain past the render distance: its own block colours, lit by the lightmap and fogged like the
// chunks in front of it, so the two meet without a seam. Iris hands its projection over as gl_ProjectionMatrix.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/lit.glsl"
#endif

out vec4 v_color;
out vec3 v_fog_pos;
#ifdef LIT_WORLD
out vec3 v_normal; // world space (Distant Horizons' terrain is never turned)
out vec2 v_level;  // block and sky light, 0..1
#ifdef PROGRAM_DH_GENERIC
flat out int v_dh_material; // what the object is made of (Iris's DH_BLOCK_ numbers)
#endif
#endif

void main() {
    vec4 view_pos = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = place_on_screen(gl_ProjectionMatrix * view_pos);

    v_color = gl_Color;
#ifdef LIT_WORLD
    // Its colours come with Minecraft's face shading in them, which the sun and the sky give now.
    v_normal = dot(gl_Normal, gl_Normal) > 1e-8 ? normalize(gl_Normal) : vec3(0.0, 1.0, 0.0);
#ifdef PROGRAM_DH_GENERIC
    v_dh_material = dhMaterialId; // its objects come shaded their own way (or not at all)
#else
    v_color.rgb /= face_shade(v_normal);
#endif
    // Iris gives Distant Horizons' light at the lightmap's texel centres (level / 16 + 1 / 32).
    v_level = clamp((gl_MultiTexCoord1.xy - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
#else
    // Iris gives Distant Horizons' light at the lightmap's texel centres; Minecraft samples terrain between them.
    v_color.rgb *= lightmap_smooth(gl_MultiTexCoord1.xy - 1.0 / 32.0);
#endif
    v_fog_pos = view_to_scene(view_pos.xyz);
}

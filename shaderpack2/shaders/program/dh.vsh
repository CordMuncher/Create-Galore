// Distant Horizons past the render distance: its terrain into the G-buffer like Minecraft's own (the deferred pass
// lights it without the shadow map or the traced light, which don't reach that far), its water lit here, and its
// other objects (beacon beams, its clouds). Iris hands its projection over as gl_ProjectionMatrix; its motion
// vectors and DLSS depth come from its depth in the DLSS pass (see dlss.fsh).

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"

out vec4 v_color;
out vec3 v_scene_pos;
out vec3 v_normal; // world space (Distant Horizons' terrain is never turned)
out vec2 v_light;  // block and sky light, 0..1
flat out int v_material; // what it is made of (Iris's DH_BLOCK_ numbers)

// Minecraft's shading of block faces by the way they turn, which Distant Horizons bakes into its colours.
float baked_face_shade(vec3 n) {
    return n.y > 0.5 ? 1.0 : n.y < -0.5 ? 0.5 : abs(n.z) > 0.5 ? 0.8 : 0.6;
}

void main() {
    vec4 view_pos = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = place_on_screen(gl_ProjectionMatrix * view_pos);
    v_scene_pos = view_to_scene(view_pos.xyz);

    v_normal = dot(gl_Normal, gl_Normal) > 1e-8 ? normalize(gl_Normal) : vec3(0.0, 1.0, 0.0);
    v_material = dhMaterialId;
    v_color = gl_Color;
#ifdef PROGRAM_DH_TERRAIN
    v_color.rgb /= baked_face_shade(v_normal); // the sun and the sky shade the faces here
#endif
    // Iris gives its light at the lightmap's texel centres (level / 16 + 1 / 32).
    v_light = clamp((gl_MultiTexCoord1.xy - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
}

// The overworld's shadow map: everything that casts a shadow, drawn from the sun (or the moon) straight, without
// warping, so every texel covers the same ground and the shadows hold still (see shadow.glsl). Iris sets the
// shadow camera's matrices as gl_ModelViewMatrix and gl_ProjectionMatrix, and snaps it to whole steps as you move.

out vec2 v_uv;
out float v_alpha;

void main() {
    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
    v_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    v_alpha = gl_Color.a;
}

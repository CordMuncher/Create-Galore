// The composite and final passes: one quad over the window.

out vec2 v_uv;

void main() {
    gl_Position = ftransform();
    v_uv = gl_MultiTexCoord0.xy;
}

// Create's machines and contraptions in the overworld's shadow map, through Colorwheel (see shadow.vsh).

out vec2 v_uv;

void main() {
    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
    v_uv = gl_MultiTexCoord0.xy;
}

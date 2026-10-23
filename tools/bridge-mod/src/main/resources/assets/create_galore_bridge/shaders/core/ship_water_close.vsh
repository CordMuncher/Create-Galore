#version 150

in vec3 Position;

// A quad over the whole screen.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}

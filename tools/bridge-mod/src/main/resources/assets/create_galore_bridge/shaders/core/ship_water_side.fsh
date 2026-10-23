#version 150

in float side;

out vec4 fragColor;

// A hull face, kept only on one side of the water's surface.
void main() {
    if (side <= 0.0) {
        discard;
    }
    fragColor = vec4(1.0);
}

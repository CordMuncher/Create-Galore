#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// The water's surface over the region's own coordinates, positive on the side to keep.
uniform vec4 CgPlane;

out float side;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    side = dot(CgPlane.xyz, Position) + CgPlane.w;
}

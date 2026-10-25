#version 150

uniform sampler2D FrontDepth;
uniform sampler2D BackDepth;
uniform sampler2D BackMark;
// 1 when the camera is inside a hull: the line of sight starts inside, as if behind a front face at the camera.
uniform float CameraInside;

out vec4 fragColor;

// The last front and back hull faces in front of the water's surface: when a front face is the last one, the line of
// sight is inside the hull where it meets the water, and the water to hide starts there. From a camera inside a hull,
// a line of sight that crossed no back face before the water never left the hull. Otherwise hide nothing.
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float front = texelFetch(FrontDepth, pixel, 0).r;
    float back = texelFetch(BackDepth, pixel, 0).r;
    // The mark, not the depth: a back face just past the camera is clamped to depth 0, the same as no face at all.
    bool noBack = texelFetch(BackMark, pixel, 0).r < 0.5;
    gl_FragDepth = front > back || (CameraInside > 0.5 && noBack) ? front : 1.0;
    fragColor = vec4(0.0);
}

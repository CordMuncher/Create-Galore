#version 150

uniform sampler2D FrontDepth;
uniform sampler2D BackDepth;

out vec4 fragColor;

// The last front and back hull faces in front of the water's surface: when a front face is the last one, the line of
// sight is inside the hull where it meets the water, and the water to hide starts there. Otherwise hide nothing.
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float front = texelFetch(FrontDepth, pixel, 0).r;
    float back = texelFetch(BackDepth, pixel, 0).r;
    gl_FragDepth = front > back ? front : 1.0;
    fragColor = vec4(0.0);
}

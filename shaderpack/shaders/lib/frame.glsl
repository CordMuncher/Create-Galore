// Where this frame lands on the screen: render size, the upscaler's jitter, and the corner the frame renders into
// while upscaling. Every program that draws the world goes through here.

#if !defined LIB_FRAME
#define LIB_FRAME

// Set by the Create Galore Bridge (see create_galore_dlss.properties). Without the Bridge they all read 0.
uniform float cg_dlss_active;    // 1 while DLSS or FSR takes this frame
uniform float cg_dlss_jitter_x;  // this frame's jitter in render pixels, -0.5..0.5
uniform float cg_dlss_jitter_y;
uniform float cg_mip_bias;       // texture mip bias for the render scale (negative while upscaling)

// The render size over the window's, per axis, while upscaling (1 otherwise). Create Deep Seas looks for this exact
// name in the programs to scale its hull depth buffers, so it is always written out, never hidden behind a #define.
uniform vec2 taau_render_scale;

uniform float viewWidth;
uniform float viewHeight;
uniform int frameCounter; // for what takes turns from frame to frame

vec2 window_size() {
    return vec2(viewWidth, viewHeight);
}

vec2 render_scale() {
    return taau_render_scale.x > 0.0 ? taau_render_scale : vec2(1.0);
}

// The Bridge rounds the scale so this is whole pixels.
vec2 render_size() {
    return window_size() * render_scale();
}

// A clip position as the projection gave it, moved onto the screen: squeezed into the lower left corner at the
// render size while upscaling, then shifted by this frame's jitter. One render pixel is one buffer pixel there.
vec4 place_on_screen(vec4 clip) {
    vec2 scale = render_scale();
    clip.xy = clip.xy * scale + clip.w * (scale - 1.0);
    clip.xy += vec2(cg_dlss_jitter_x, cg_dlss_jitter_y) * (2.0 / window_size()) * clip.w;
    return clip;
}

// Fragments outside the render corner belong to geometry that was off the screen before the squeeze.
bool outside_render_area(vec2 frag_coord) {
    return any(greaterThanEqual(frag_coord, render_size()));
}

#endif

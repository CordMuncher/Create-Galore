#version 330 compatibility
// The screen. With DLSS (or FSR) running, the Create Galore Bridge has already written its full-size result over
// colortex0. Without it, a frame the pack rendered smaller (upscaling on, the Bridge's DLSS off) is brought up to
// the window's size here with a Catmull-Rom filter. Either way the linear light goes back to sRGB for the screen:
// in the lit overworld with the depth of field (see dof.glsl), the bloom's glow (see bloom.csh), as the eye sees it in
// the dark (see night_vision.glsl), with the local exposure (see local_exposure.glsl), white balanced, through the
// tone curve (see tonemap.glsl) and with the colour controls (see grade.glsl), else as it is.

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/color.glsl"
#ifdef LIT_WORLD
#include "/lib/tonemap.glsl"
#include "/lib/bloom.glsl"
#include "/lib/globals.glsl"
#include "/lib/night_vision.glsl"
#include "/lib/local_exposure.glsl"
#include "/lib/grade.glsl"
#ifdef DEPTH_OF_FIELD
#include "/lib/dof.glsl"
#endif
#endif

uniform sampler2D colortex0;

in vec2 v_uv;

layout(location = 0) out vec4 out_color;

// Catmull-Rom weights of the four texels around a point t (0..1) past the second one.
vec4 catmull_rom_weights(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t,
                1.5 * t3 - 2.5 * t2 + 1.0,
                -1.5 * t3 + 2.0 * t2 + 0.5 * t,
                0.5 * t3 - 0.5 * t2);
}

// The render-size frame in the lower left corner, at this window pixel. Kept between the four nearest texels, so
// the filter sharpens without ringing around bright edges.
vec3 upscale(vec2 window_pixel) {
    ivec2 last = ivec2(render_size()) - 1;
    vec2 pos = window_pixel * render_scale() - 0.5; // in render texels, from the first texel's centre
    ivec2 base = ivec2(floor(pos));
    vec2 t = pos - vec2(base);
    vec4 wx = catmull_rom_weights(t.x);
    vec4 wy = catmull_rom_weights(t.y);

    vec3 sum = vec3(0.0);
    vec3 low = vec3(1e6);
    vec3 high = vec3(0.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec3 c = texelFetch(colortex0, clamp(base + ivec2(x - 1, y - 1), ivec2(0), last), 0).rgb;
            sum += c * (wx[x] * wy[y]);
            if (x == 1 || x == 2) {
                if (y == 1 || y == 2) {
                    low = min(low, c);
                    high = max(high, c);
                }
            }
        }
    }
    return clamp(sum, low, high);
}

void main() {
    vec3 color;
    if (cg_dlss_active > 0.5 || all(greaterThanEqual(render_scale(), vec2(1.0)))) {
        color = texelFetch(colortex0, ivec2(gl_FragCoord.xy), 0).rgb;
    } else {
        color = upscale(gl_FragCoord.xy);
    }
#ifdef LIT_WORLD
#ifdef DEPTH_OF_FIELD
    color = depth_of_field(color, gl_FragCoord.xy);
#endif
    color = mix(color, bloom_at(gl_FragCoord.xy), bloom_amount);
    // In the dark, as the rods see it, from the light in real units (the frame holds it times the exposure).
    float frame_exposure = exposure();
    color = night_vision(color / frame_exposure) * frame_exposure;
    color = local_exposure(color, gl_FragCoord.xy);
    color = white_balance(color);
    out_color = vec4(grade_screen(tone_map(color * exp2(EXPOSURE))), 1.0);
#else
    out_color = vec4(linear_to_srgb(color), 1.0);
#endif
}

// The last pass: the frame (from DLSS or FSR at the window's size, or brought up from the render corner here when
// the pack drew it smaller without them), its bloom, the fixed exposure, the tone curve and the grade (see
// lib/post.glsl), out to the screen in sRGB. A touch of noise keeps the dark gradients from banding.

#include "/lib/settings.glsl"
#include "/lib/bloom.glsl"
#include "/lib/bad_frame.glsl"

in vec2 v_uv;

layout(location = 0) out vec4 out_color;

// Catmull-Rom weights of the four texels around a point t (0..1) past the second one.
vec4 catmull_rom(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2);
}

// The render-size frame in the lower left corner at a window pixel, sharpened up and kept between its four nearest
// texels so bright edges don't ring.
vec3 upscaled(vec2 window_pixel) {
    ivec2 last = ivec2(render_size()) - 1;
    vec2 pos = window_pixel * render_scale() - 0.5;
    ivec2 base = ivec2(floor(pos));
    vec2 t = pos - vec2(base);
    vec4 wx = catmull_rom(t.x);
    vec4 wy = catmull_rom(t.y);
    vec3 sum = vec3(0.0);
    vec3 lo = vec3(1e6);
    vec3 hi = vec3(0.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec3 c = texelFetch(colortex0, clamp(base + ivec2(x - 1, y - 1), ivec2(0), last), 0).rgb;
            sum += c * (wx[x] * wy[y]);
            if ((x == 1 || x == 2) && (y == 1 || y == 2)) {
                lo = min(lo, c);
                hi = max(hi, c);
            }
        }
    }
    return clamp(sum, lo, hi);
}

void main() {
    vec2 window_pixel = gl_FragCoord.xy;
    vec3 c = frame_full_size() ? texelFetch(colortex0, ivec2(window_pixel), 0).rgb : upscaled(window_pixel);
    if (bad_value(c)) {
        c = vec3(0.0);
        note_bad(BAD_AFTER_DLSS);
    }

    vec2 uv = window_pixel / window_size();
#if DEBUG_VIEW != 0
    // A debug view: its values as they are, no bloom, exposure or tone curve.
    out_color = vec4(linear_to_srgb(clamp(c, 0.0, 1.0)), 1.0);
    return;
#endif
    vec3 bloom = bloom_up_at(1, uv) / float(bloom_levels);
    c = mix(c, bloom, clamp(0.12 * BLOOM_STRENGTH, 0.0, 1.0));

    c = grade(tone_curve(c * exposure_scale()));

    // A soft round vignette.
    vec2 centred = uv - 0.5;
    c *= 1.0 - 0.18 * smoothstep(0.25, 0.75, dot(centred, centred) * 2.0);

    vec3 srgb = linear_to_srgb(c);
    srgb += (hash33(uvec3(uvec2(window_pixel), uint(frameCounter))).x - 0.5) / 255.0;
    if (bad_value(srgb)) {
        srgb = vec3(0.0);
        note_bad(BAD_FINAL);
    }
#ifdef BAD_FRAME_MARK
    // The bad frame mark (lib/bad_frame.glsl), 12 pixels from the top left corner.
    vec2 corner = vec2(window_pixel.x, window_size().y - window_pixel.y);
    if (all(lessThan(corner, vec2(20.0))) && all(greaterThan(corner, vec2(8.0)))) {
        uint record = data_load(data_bad_frame);
        uint shown = (record >> 8u) & 255u;
        if ((record >> 16u) > 0u && shown != 0u) {
            srgb = (shown & BAD_FINAL) != 0u ? vec3(1.0)
                 : vec3((shown & BAD_BEFORE_DLSS) != 0u ? 1.0 : 0.0, (shown & BAD_AFTER_DLSS) != 0u ? 1.0 : 0.0,
                        (shown & BAD_GLOBALS) != 0u ? 1.0 : 0.0);
        }
    }
#endif
    out_color = vec4(srgb, 1.0);
}

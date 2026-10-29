// After the DLSS point: the frame at the window's size, the exposure and the tone curve.
//
// The exposure is fixed (no eye adaptation: the image never brightens in a cave or dims in the sun by itself; the
// light levels carry day, night and caves), shifted by the EXPOSURE option. The curve is a Reinhard-like shoulder
// with a firm knee, then more contrast in the highlights than in the shadows. The grade is plain: the colour comes
// from the lights, not a filter.

#if !defined LIB_POST
#define LIB_POST

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/frame.glsl"

uniform sampler2D colortex0;

const float base_exposure = 1.4;

float exposure_scale() {
    return exp2(EXPOSURE) * base_exposure;
}

// Whether colortex0 holds the frame at the window's size (DLSS or FSR brought it up, or it was drawn full size),
// rather than in the lower left corner at the render size.
bool frame_full_size() {
    return cg_dlss_active > 0.5 || all(greaterThanEqual(render_scale(), vec2(1.0)));
}

// The frame at a point of the window (0..1), blended between texels.
vec3 frame_at(vec2 uv) {
    vec2 scale = frame_full_size() ? vec2(1.0) : render_scale();
    vec2 size = window_size() * scale;
    vec2 p = clamp(uv * size, vec2(0.5), size - 0.5);
    return texture(colortex0, p / window_size()).rgb;
}

vec3 tone_curve(vec3 x) {
    x = max(x, 0.0);
    // The shoulder: x / sqrt(1 + x^2), half per channel (the brightest colours ease towards white) and half on the
    // brightness alone (they keep their hue), so sand in the noon sun stays sand-coloured.
    vec3 per_channel = x * inversesqrt(1.0 + x * x);
    float l = luminance(x);
    vec3 same_hue = x * (inversesqrt(1.0 + l * l));
    same_hue /= max(1.0, max3(same_hue));
    vec3 y = mix(per_channel, same_hue, 0.5);
    // Contrast: a power over 1, more in the mid-tones and highlights than in the shadows.
    y = pow(y, vec3(1.1) + 0.2 * sqrt(y));
    return y;
}

vec3 grade(vec3 c) {
    float grey = luminance(c);
    return max(mix(vec3(grey), c, SATURATION), 0.0);
}

#endif

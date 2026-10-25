// The overworld's tone mapping, after DLSS at the window's size: AgX (Troy Sobotka) with Alpha Piscium's
// (GPL-3.0) curve and look, ported from its techniques/displaytransform/DRT.glsl. Alpha Piscium credits the minimal
// AgX of Benjamin Wrensch (MIT), linlin's AgX (MIT) and GeforceLegend's faster curve.

#if !defined LIB_TONEMAP
#define LIB_TONEMAP

// The curve's contrast, centred on 20/33 of the log range.
vec3 agx_contrast(vec3 x) {
    const float scale0 = 59.507875;
    const float scale1 = 69.862789;
    x -= 20.0 / 33.0;
    vec3 below = vec3(floatBitsToUint(x) >> 31u); // 1 under the centre
    vec3 scale = scale1 + (scale0 - scale1) * below;
    vec3 power0 = 13.0 / 4.0 - 0.25 * below;
    vec3 power1 = -4.0 / 13.0 + (4.0 / 13.0 - 1.0 / 3.0) * below;
    return 2.0 * x * pow(1.0 + scale * pow(abs(x), power0), power1) + 0.5;
}

// Linear sRGB scene light (exposed) to sRGB-encoded display values.
vec3 tone_map(vec3 color) {
    const mat3 agx_in = mat3(
        0.842479062253094, 0.0423282422610123, 0.0423756549057051,
        0.0784335999999992, 0.878468636469772, 0.0784336,
        0.0792237451477643, 0.0791661274605434, 0.879142973793104);
    const mat3 agx_out = mat3(
        1.19687900512017, -0.0528968517574562, -0.0529716355144438,
        -0.0980208811401368, 1.15190312990417, -0.0980434501171241,
        -0.0990297440797205, -0.0989611768448433, 1.15107367264116);
    const float ev_range = 16.5;

    color = agx_in * max(color, 0.0);
    color = clamp(log2(max(color, 1e-10)), -0.5 * ev_range, 0.5 * ev_range);
    color = agx_contrast((color + 0.5 * ev_range) / ev_range);

    // Alpha Piscium's look: a little more slope and contrast, and colour.
    color = pow(max(color * 1.05, 0.0), vec3(1.1));
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = luma + 1.3 * (color - luma);

    return clamp(agx_out * color, 0.0, 1.0);
}

#endif

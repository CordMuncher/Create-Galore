// Colour encodings. The world programs draw in Minecraft's sRGB colours, as vanilla blends them; DLSS and FSR take
// linear light (the Bridge runs them as HDR), and the final pass encodes their result for the screen again.

#if !defined LIB_COLOR
#define LIB_COLOR

vec3 srgb_to_linear(vec3 c) {
    c = max(c, 0.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 linear_to_srgb(vec3 c) {
    c = max(c, 0.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

#endif

// The colour controls, all of which change nothing at their defaults. Before the tone curve, the white balance: the
// colour of a light at the chosen temperature (on the Planckian locus, Kim et al.'s fit, with a tint across it)
// shown as white, by a von Kries adaptation in Bradford's cone space, as Ghost of Tsushima has its artists pick the
// white (Patry 2021). After it, on the screen's colours, as Alpha Piscium has them: each primary's hue and
// saturation (turned about the white and drawn in or out in CIE xy, the white kept), and a mixer of hue, saturation
// and lightness by eight bands of hue, as photo editors have (red, orange, yellow, green, aqua, blue, purple,
// magenta; a colour between two bands takes from both).

#if !defined LIB_GRADE
#define LIB_GRADE

#include "/lib/settings.glsl"
#include "/lib/color.glsl"

const mat3 grade_xyz_from_rgb = mat3(
    0.4124564, 0.2126729, 0.0193339,
    0.3575761, 0.7151522, 0.1191920,
    0.1804375, 0.0721750, 0.9503041);
const mat3 grade_bradford = mat3(
    0.8951, -0.7502, 0.0389,
    0.2664, 1.7135, -0.0685,
    -0.1614, 0.0367, 1.0296);

// The chromaticity (CIE xy) of a light at a colour temperature (K, 1667 to 25000).
vec2 planckian_xy(float kelvin) {
    float t = 1000.0 / kelvin;
    float x = kelvin < 4000.0
            ? ((-0.2661239 * t - 0.2343589) * t + 0.8776956) * t + 0.179910
            : ((-3.0258469 * t + 2.1070379) * t + 0.2226347) * t + 0.240390;
    float y = kelvin < 2222.0 ? ((-1.1063814 * x - 1.34811020) * x + 2.18555832) * x - 0.20219683
            : kelvin < 4000.0 ? ((-0.9549476 * x - 1.37418593) * x + 2.09137015) * x - 0.16748867
            : ((3.0817580 * x - 5.87338670) * x + 3.75112997) * x - 0.37001483;
    return vec2(x, y);
}

vec3 xyz_of(vec2 xy) {
    return vec3(xy.x / xy.y, 1.0, (1.0 - xy.x - xy.y) / xy.y);
}

// Scene light (linear Rec.709) white balanced.
vec3 white_balance(vec3 color) {
#if WB_TEMPERATURE != 6500 || WB_TINT != 0
    // The chosen light's white goes to where 6500 K's lies: a higher temperature shows bluer light as white, so
    // warmer; a tint takes a greener light as white (a magenta tint to the frame), or a less green one.
    vec2 from_xy = planckian_xy(float(WB_TEMPERATURE));
    vec2 to_xy = planckian_xy(6500.0);
    vec2 along = normalize(planckian_xy(float(WB_TEMPERATURE) + 100.0) - from_xy);
    vec2 greener = vec2(along.y, -along.x);
    from_xy += greener * (sign(greener.y) * float(WB_TINT) * 0.0005);
    vec3 from = grade_bradford * xyz_of(from_xy);
    vec3 to = grade_bradford * xyz_of(to_xy);
    mat3 adapt = inverse(grade_xyz_from_rgb) * inverse(grade_bradford) * mat3(
        to.x / from.x, 0.0, 0.0,
        0.0, to.y / from.y, 0.0,
        0.0, 0.0, to.z / from.z) * grade_bradford * grade_xyz_from_rgb;
    color = max(adapt * color, 0.0);
#endif
    return color;
}

// A primary (CIE xy) turned about the white by degrees and drawn out from it by a factor.
vec2 grade_primary(vec2 primary, vec2 white, float degrees, float factor) {
    float a = radians(degrees);
    vec2 d = primary - white;
    return white + mat2(cos(a), sin(a), -sin(a), cos(a)) * d * factor;
}

// The hue (degrees), saturation and lightness of an sRGB-encoded colour.
vec3 grade_hsl(vec3 c) {
    float high = max(c.r, max(c.g, c.b));
    float low = min(c.r, min(c.g, c.b));
    float delta = high - low;
    float l = 0.5 * (high + low);
    if (delta < 1e-6) {
        return vec3(0.0, 0.0, l);
    }
    float s = delta / (1.0 - abs(2.0 * l - 1.0));
    float h = high == c.r ? mod((c.g - c.b) / delta, 6.0) : high == c.g ? (c.b - c.r) / delta + 2.0
            : (c.r - c.g) / delta + 4.0;
    return vec3(h * 60.0, s, l);
}

vec3 grade_rgb(vec3 hsl) {
    vec3 k = mod(vec3(0.0, 8.0, 4.0) + hsl.x / 30.0, 12.0);
    float a = hsl.y * min(hsl.z, 1.0 - hsl.z);
    return hsl.z - a * clamp(min(k - 3.0, 9.0 - k), -1.0, 1.0);
}

// The screen's colour (linear Rec.709, 0..1, after the tone curve) with the primaries.
vec3 grade_primaries(vec3 color) {
#if PRIMARY_RED_HUE != 0 || PRIMARY_RED_SAT != 0 || PRIMARY_GREEN_HUE != 0 || PRIMARY_GREEN_SAT != 0 || PRIMARY_BLUE_HUE != 0 || PRIMARY_BLUE_SAT != 0
    {
        // Each primary moved in xy, with its brightness kept, and the three scaled so white stays white.
        vec3 white = grade_xyz_from_rgb * vec3(1.0);
        vec2 white_xy = white.xy / (white.x + white.y + white.z);
        mat3 moved;
        for (int i = 0; i < 3; i++) {
            vec3 p = grade_xyz_from_rgb[i];
            vec2 xy = p.xy / (p.x + p.y + p.z);
            float hue = i == 0 ? float(PRIMARY_RED_HUE) : i == 1 ? float(PRIMARY_GREEN_HUE) : float(PRIMARY_BLUE_HUE);
            float sat = i == 0 ? float(PRIMARY_RED_SAT) : i == 1 ? float(PRIMARY_GREEN_SAT) : float(PRIMARY_BLUE_SAT);
            xy = grade_primary(xy, white_xy, hue * 0.25, 1.0 + sat * 0.01);
            moved[i] = xyz_of(xy) * p.y;
        }
        vec3 scale = inverse(moved) * white;
        mat3 calibrated = mat3(moved[0] * scale.x, moved[1] * scale.y, moved[2] * scale.z);
        color = clamp(inverse(grade_xyz_from_rgb) * calibrated * color, 0.0, 1.0);
    }
#endif
    return color;
}

// The screen's colour (sRGB-encoded) with the mixer.
vec3 grade_mixer(vec3 srgb) {
#ifdef COLOR_MIXER
    vec3 hsl = grade_hsl(srgb);
    if (hsl.y < 1e-6) {
        return srgb;
    }
    // The bands' hues; a hue between two takes from both by how near it is.
    const float centres[9] = float[9](0.0, 30.0, 60.0, 120.0, 180.0, 240.0, 300.0, 330.0, 360.0);
    const vec3 bands[8] = vec3[8](
        vec3(MIX_RED_HUE, MIX_RED_SAT, MIX_RED_LUM), vec3(MIX_ORANGE_HUE, MIX_ORANGE_SAT, MIX_ORANGE_LUM),
        vec3(MIX_YELLOW_HUE, MIX_YELLOW_SAT, MIX_YELLOW_LUM), vec3(MIX_GREEN_HUE, MIX_GREEN_SAT, MIX_GREEN_LUM),
        vec3(MIX_AQUA_HUE, MIX_AQUA_SAT, MIX_AQUA_LUM), vec3(MIX_BLUE_HUE, MIX_BLUE_SAT, MIX_BLUE_LUM),
        vec3(MIX_PURPLE_HUE, MIX_PURPLE_SAT, MIX_PURPLE_LUM), vec3(MIX_MAGENTA_HUE, MIX_MAGENTA_SAT, MIX_MAGENTA_LUM));
    vec3 change = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (hsl.x >= centres[i] && hsl.x < centres[i + 1]) {
            float t = (hsl.x - centres[i]) / (centres[i + 1] - centres[i]);
            change = mix(bands[i], bands[(i + 1) % 8], t);
        }
    }
    // Hue up to 30 degrees either way, saturation up to twice or none, lightness along a curve that leaves black
    // and white where they are; all faded in with the saturation, so greys stay grey.
    float colourful = smoothstep(0.0, 0.15, hsl.y);
    hsl.x = mod(hsl.x + change.x * 0.3 * colourful + 360.0, 360.0);
    hsl.y = clamp(hsl.y * (1.0 + change.y * 0.01), 0.0, 1.0);
    hsl.z = clamp(hsl.z + change.z * 0.002 * hsl.z * (1.0 - hsl.z) * 4.0 * colourful, 0.0, 1.0);
    return grade_rgb(hsl);
#else
    return srgb;
#endif
}

// The tone curve's result (sRGB-encoded) with the primaries and the mixer.
vec3 grade_screen(vec3 srgb) {
#if PRIMARY_RED_HUE != 0 || PRIMARY_RED_SAT != 0 || PRIMARY_GREEN_HUE != 0 || PRIMARY_GREEN_SAT != 0 || PRIMARY_BLUE_HUE != 0 || PRIMARY_BLUE_SAT != 0
    srgb = linear_to_srgb(grade_primaries(srgb_to_linear(srgb)));
#endif
    return grade_mixer(srgb);
}

#endif

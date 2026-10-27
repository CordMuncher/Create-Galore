// How eyes see in the dark (the Purkinje shift). Below a few cd/m² the rods take over from the cones: they see no
// colour, and they weigh light differently, blue and green brighter and red darker, so a poppy goes black by
// moonlight while the grass around it stays grey-green. Each pixel goes over to the rods' view by its own brightness
// as the CIE's mesopic photometry has it (CIE 191:2010: the cones alone above 5 cd/m², the rods alone below 0.005,
// between them by the log of the luminance), in the cool tint night is seen in. A lamp's light keeps its colour; the
// moonlit land around it doesn't.
//
// The rods' weights of linear Rec.709's red, green and blue come from CIE 1951's scotopic luminous efficiency, D65 and
// Smits' spectra for the primaries, as Ghost of Tsushima builds its matrix to the eye's receptors (Patry 2021); see
// tools/rod_weights.py. They are scaled so that white weighs 1: a grey keeps its brightness, only colours move.

#if !defined LIB_NIGHT_VISION
#define LIB_NIGHT_VISION

#include "/lib/settings.glsl"

const vec3 rod_weights = vec3(0.01204, 0.64799, 0.33997);
// What the rods' view looks like (luminance 1): a cool blue, a little towards the blue-green the rods' signal adds
// to colours (Cao et al. 2008).
const vec3 night_tint = vec3(0.86, 1.0, 1.14) / dot(vec3(0.86, 1.0, 1.14), vec3(0.2126, 0.7152, 0.0722));

// The light of a pixel (linear Rec.709, kcd/m²) as the eye sees it at that brightness.
vec3 night_vision(vec3 light) {
    float luminance = dot(light, vec3(0.2126, 0.7152, 0.0722)) * 1000.0; // cd/m²
    // CIE 191's adaptation coefficient m (1 the cones alone, 0 the rods alone), from the luminance.
    float cones = clamp(0.767 + 0.3334 * log2(max(luminance, 1e-9)) * 0.30103, 0.0, 1.0);
    vec3 rods = night_tint * dot(light, rod_weights);
    return mix(light, rods, (1.0 - cones) * NIGHT_VISION);
}

#endif

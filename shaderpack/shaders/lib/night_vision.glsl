// How eyes see in the dark (the Purkinje shift). In dim light the rods take over from the cones: they see no colour,
// and they weigh light differently, blue and green brighter and red darker, so a poppy goes black by moonlight while
// the grass around it stays grey-green. How much of a pixel the rods see is Hunt's rod sensitivity (1995) at its
// brightness, as Krawczyk, Myszkowski and Seidel's tone mapping has it (2005): half at 0.04 cd/m², a quarter at 0.12,
// a tenth at 0.36, in the cool tint night is seen in. Moonlit land (a few hundredths of a cd/m² under a real moon)
// is mostly the rods'; a wall in a lamp's light (half a cd/m² and more) keeps nearly all its colour.
//
// The moon and the stars are made brighter than real ones so nights can be seen by (see moon_boost); the part of a
// pixel's light they give counts at its real brightness, so the night still looks the way a real one does to the eye.
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

// The light of a pixel (linear Rec.709, kcd/m²) as the eye sees it at that brightness. moonlit: the share of it the
// moon and the stars give, boost how much brighter than real they are drawn.
vec3 night_vision(vec3 light, float moonlit, float boost) {
    float luminance = dot(light, vec3(0.2126, 0.7152, 0.0722)) * 1000.0; // cd/m²
    float seen = luminance * (1.0 - moonlit + moonlit / boost);
    float rods_share = 0.04 / (0.04 + seen);
    vec3 rods = night_tint * dot(light, rod_weights);
    return mix(light, rods, rods_share * NIGHT_VISION);
}

#endif

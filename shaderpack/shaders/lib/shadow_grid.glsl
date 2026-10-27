// The shadow map held still on the world (see shadow.glsl). Iris moves the sun's camera along with you in steps of
// shadowIntervalSize along the world's axes; seen from the sun those steps are no whole number of the map's texels,
// so each one would draw every shadow's edge a fraction of a texel over, and the edges would crawl as you walk. What
// draws into the map and what reads it shift the sun's view across by the part of a texel that keeps the texels'
// edges on the same places in the world: on a grid through a point fixed in the world near you (the corner of the
// 512-block square you are in, so the sums stay exact far from the world's origin; crossing into the next square
// moves the grid once).

#if !defined LIB_SHADOW_GRID
#define LIB_SHADOW_GRID

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"

// The shift (blocks, across the sun's view) to take off a point's place in the sun's view, for Iris's
// shadowModelView as shadow_model_view.
vec2 shadow_grid_shift(mat4 shadow_model_view) {
    const float texel = 2.0 * shadowDistance / float(shadowMapResolution);
    ivec3 corner = cameraPositionInt & ~511;
    vec3 to_corner = vec3(corner - cameraPositionInt) - cameraPositionFract;
    vec2 at = (mat3(shadow_model_view) * to_corner + shadow_model_view[3].xyz).xy;
    return at - texel * round(at / texel);
}

#endif

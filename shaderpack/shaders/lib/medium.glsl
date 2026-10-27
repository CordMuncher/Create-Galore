// Water as something light passes through, in the lit overworld: it takes red out of the light first, then green,
// so deep water turns blue-green and what lies under it too, and it scatters a little of the light around it back
// (the haze you see under water, lit where the light reaches it). Per block: about as clear as coastal sea water,
// where a wall ten blocks off is clearly hazier than one close by, and deep water keeps its dark blue-green.

#if !defined LIB_MEDIUM
#define LIB_MEDIUM

const vec3 water_absorption = vec3(0.30, 0.075, 0.05);
const vec3 water_scattering = vec3(0.038, 0.04, 0.04);
const vec3 water_extinction = water_absorption + water_scattering;

// What is left of light after dist blocks of water.
vec3 water_transmittance(float dist) {
    return exp(-water_extinction * max(dist, 0.0));
}

#endif

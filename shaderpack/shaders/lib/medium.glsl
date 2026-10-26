// Water as something light passes through, in the lit overworld: it takes red out of the light first, then green,
// so deep water turns blue-green and what lies under it too, and it scatters a little of the light around it back
// (the haze you see under water, lit where the light reaches it). Per block.

#if !defined LIB_MEDIUM
#define LIB_MEDIUM

const vec3 water_absorption = vec3(0.26, 0.05, 0.028);
const vec3 water_scattering = vec3(0.022, 0.024, 0.024);
const vec3 water_extinction = water_absorption + water_scattering;

// What is left of light after dist blocks of water.
vec3 water_transmittance(float dist) {
    return exp(-water_extinction * max(dist, 0.0));
}

#endif

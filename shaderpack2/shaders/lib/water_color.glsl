// Water's colour: the light it soaks up and the light it scatters, per block of water, shifted by the biome's water
// colour; and how much its surface reflects.
//
// Water soaks up red the most and blue the least (water_absorption), so what lies under it goes green-blue and then
// dark with depth, and it scatters a little light back towards you, so deep water has a body of its own colour
// instead of going black. A biome whose water Minecraft colours darker in a channel than the plain ocean's blue soaks
// up more of that channel (a third of the way to what that colour alone would say): a swamp's water goes murky
// green-brown, a warm ocean's clear turquoise, a cold one deep blue.

#if !defined LIB_WATER_COLOR
#define LIB_WATER_COLOR

#include "/lib/common.glsl"

const vec3 water_absorption = vec3(0.39, 0.14, 0.07); // per block
const float water_scatter = 0.01;                    // per block, the light it scatters (any colour)
// Light scattered more than once comes back too: the scattered light's share of the water's own glow.
const float water_scatter_boost = 3.0;
const vec3 water_extinction = water_absorption + water_scatter;
// Seen from inside: a little clearer and bluer (you can see further while you swim), and more scattered light, so
// the water round you glows (program/fog.fsh).
const vec3 water_absorption_inside = vec3(0.20, 0.08, 0.04);
const float water_scatter_inside = 0.03;
const vec3 water_extinction_inside = water_absorption_inside + water_scatter_inside;

// The water around you along a look d blocks long (program/fog.fsh, and what the surface mirrors from under it in
// program/water_reflect.csh): what is behind dims and turns blue-green, and the water's own light comes in. lit:
// the light in the water about you.
vec3 water_inside(vec3 color, float d, vec3 lit) {
    vec3 through = exp(-water_extinction_inside * d);
    vec3 scattered = lit * water_scatter_inside / water_extinction_inside * (1.0 - through) * vec3(0.55, 0.9, 1.0);
    return color * through + scattered;
}

// The plain ocean's water colour (Minecraft's 0x3F76E4), linear.
const vec3 water_ocean_color = vec3(0.0497, 0.1812, 0.7758);

// The water's absorption for its biome's water colour (sRGB, as the vertex colour gives it).
vec3 water_absorption_for(vec3 biome_srgb) {
    vec3 c = max(srgb_to_linear(biome_srgb), vec3(1e-3));
    vec3 darker = water_ocean_color / c; // over 1 where this water is darker than the ocean's in that channel
    return water_absorption * clamp(1.0 + (darker - 1.0) / 3.0, 0.5, 4.0);
}

// Snell's law at the surface: how much of the light is reflected (unpolarised), for the cosine between the look and
// the normal on the side it comes from, and eta the other side's index over this side's (1.333 from the air into
// water, 1 / 1.333 from inside). From inside, past about 48.6 degrees it all reflects.
float fresnel_dielectric(float cos_i, float eta) {
    float c = clamp(cos_i, 0.0, 1.0);
    float g2 = eta * eta - 1.0 + c * c;
    if (g2 <= 0.0) {
        return 1.0;
    }
    float g = sqrt(g2);
    float a = (g - c) / (g + c);
    float b = (c * (g + c) - 1.0) / (c * (g - c) + 1.0);
    return clamp(0.5 * a * a * (1.0 + b * b), 0.0, 1.0);
}

const float water_ior = 1.333;

#endif

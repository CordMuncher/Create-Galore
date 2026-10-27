// The Nether's light, in real units (see globals.glsl), for sky.glsl: no sun, no moon and no sky. The lava and every
// lamp light it, in their own colours, through Minecraft's block light and the light volume (see light_volume.glsl).
// All else is its air: thick, in the colour of the biome's own fog (Minecraft's, which it blends between biomes as you
// walk), glowing faintly by itself and brightly over the lava seas, and lighting everything a little from all
// around, the way Minecraft's Nether is never black. Far off, the world fades into that air; there is nothing behind
// it (no stars, no sky). The light in the air (air_volume.csh) draws the air between you and what you see, and the
// lighting takes its light all around from the frame's shared light (frame_globals.csh).

#if !defined LIB_NETHER
#define LIB_NETHER

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/vanilla.glsl"

#ifndef PI
#define PI 3.14159265358979
#endif

uniform float cg_nether_haze; // how much thicker the biome's air is than the wastes' (see shaders.properties)

// The light all around from the air, on a surface open to it (klux): about a tenth of what a torch gives a block
// next to it, as Minecraft's Nether has it (its ambient light).
const float nether_ambient_lux = 0.0025;
// How much of the light in it the air sends on (the rest it takes in).
const float nether_haze_albedo = 0.6;
// How thick the air is (per block, at Haze 1 in the Nether Wastes): about a fifth of the light gets through 96 blocks,
// Minecraft's own fog distance in the Nether.
const float nether_haze_density = 0.018;
// The lava's light, luminance 1 (its colour in the light volume, see block.properties).
const vec3 nether_lava_color = vec3(1.0, 0.4, 0.1) / 0.5056;
// The top of the lava seas, and how bright the air is just over them (kcd/m², times the lava's colour), fading to a
// tenth of that about 30 blocks up.
const float nether_lava_sea = 31.0;
const float nether_lava_glow = 0.006;
const float nether_lava_glow_height = 13.0;

float nether_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// The air's colour (luminance 1): the biome's fog colour, a little less deep. x: how bright the biome's air is, by
// how bright its fog is (the basalt deltas' grey haze more than the wastes' dark red).
vec4 nether_air_color() {
    vec3 fog = srgb_to_linear(fogColor);
    float l = nether_luma(fog);
    if (l < 1e-5) {
        return vec4(1.0, 1.0, 1.0, 0.6); // blindness and the like: the fog is black
    }
    return vec4(mix(vec3(1.0), fog / l, 0.75), sqrt(clamp(l / 0.01, 0.6, 2.5)));
}

// How thick the air is (per block), where you are.
float nether_haze() {
    return nether_haze_density * NETHER_HAZE * max(cg_nether_haze, 0.5);
}

// The air's own light, seen through enough of it that nothing behind shows (kcd/m²): the light all around it, as much
// of it as it sends on.
vec3 nether_air_radiance() {
    vec4 air = nether_air_color();
    return air.rgb * air.a * (nether_haze_albedo * nether_ambient_lux / PI);
}

// The light all around on a surface facing each axis (+x, -x, +y, -y, +z, -z, klux): the air's, in its colour (less
// deep than the air looks: it comes from all of it), a little more from below, where the lava seas are.
vec3 nether_ambient(int face) {
    vec4 air = nether_air_color();
    const float facing[6] = float[6](1.0, 1.0, 0.85, 1.25, 1.0, 1.0);
    return mix(vec3(1.0), air.rgb, 0.6) * air.a * nether_ambient_lux * facing[face];
}

// The lava seas' glow in the air over them at a height (world y, kcd/m²), lighting it from below.
vec3 nether_lava_sea_light(float y) {
    float over = max(y - nether_lava_sea, 0.0);
    return nether_lava_color * nether_lava_glow * exp(-over / nether_lava_glow_height);
}

// What the Nether shows where nothing is drawn (past the end of the world, or where the air is all there is), and
// what reflects there: its air.
vec3 sky_radiance(vec3 dir) {
    return nether_air_radiance();
}

// Nothing is behind the air.
vec3 sky_transmittance(vec3 dir) {
    return vec3(0.0);
}

vec3 sky_space(vec3 dir, float pixel_angle) {
    return vec3(0.0);
}

vec3 sky_full(vec3 dir, float pixel_angle) {
    return sky_radiance(dir);
}

#endif

// Water: its waves (lib/waves.glsl), its colour (lib/water_color.glsl) and the light in it around you.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/common.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/lighting.glsl"
#include "/lib/waves.glsl"
#include "/lib/water_color.glsl"

// The light in the water about you, from the sky and the sun above as much of it as reaches where you are
// (eye_sky: Minecraft's smoothed sky light at your eyes, 0..1), and a faint glow of its own in the dark.
vec3 water_light_around(float eye_sky) {
    // The sun's own share is small when its shafts are drawn (program/water_shafts.csh adds it, shaded).
    vec3 lit = (g_light_color.rgb * (SHAFT_STRENGTH > 0.0 ? 0.1 : 0.25) + g_sky_up.rgb) * vanilla_sky_share(eye_sky);
    return lit + vec3(0.02, 0.04, 0.05) * CAVE_BRIGHTNESS * 0.2;
}

#endif

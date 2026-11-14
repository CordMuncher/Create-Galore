// Water: its waves (lib/waves.glsl), its colour (lib/water_color.glsl) and the light in it around you.

#if !defined LIB_WATER
#define LIB_WATER

#include "/lib/common.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/lighting.glsl"
#include "/lib/waves.glsl"
#include "/lib/water_color.glsl"

// Where Minecraft's water hands over to Distant Horizons' (program/gbuffers.fsh, program/dh.fsh): over the last 16
// blocks (flat distance) before the render distance, a share of the pixels that grows from none to all, picked
// afresh each frame so the upscaler's history smooths it into a fade (one pass draws a pixel, the other leaves it).
// Minecraft's water lies a ninth of a block under its block's top and Distant Horizons' at the top, which along a
// grazing look puts the same pixel's two surfaces blocks apart, so both measure their distance where the look
// crosses the block's top and agree on who draws the pixel (else some pixels of the hand-over got neither: a dotted
// line across far water). far_blocks: the render distance.
bool dh_water_takes(vec3 scene_pos, vec2 frag, float far_blocks) {
    float top = ceil(scene_pos.y + cameraPosition.y - 0.01) - cameraPosition.y;
    float to_top = abs(scene_pos.y) > 0.25 ? clamp(top / scene_pos.y, 0.5, 1.5) : 1.0;
    float share = clamp((length(scene_pos.xz) * to_top - (far_blocks - 16.0)) / 16.0, 0.0, 1.0);
    float pick = fract(ign(floor(frag)) + float(frameCounter & 255) * GOLDEN);
    return pick < share;
}

// The light in the water about you, from the sky and the sun above as much of it as reaches where you are
// (eye_sky: Minecraft's smoothed sky light at your eyes, 0..1), and a faint glow of its own in the dark.
vec3 water_light_around(float eye_sky) {
    // The sun's own share is small when its shafts are drawn (program/water_shafts.csh adds it, shaded).
    vec3 lit = (g_light_color.rgb * (SHAFT_STRENGTH > 0.0 ? 0.1 : 0.25) + g_sky_up.rgb) * vanilla_sky_share(eye_sky);
    return lit + vec3(0.02, 0.04, 0.05) * CAVE_BRIGHTNESS * 0.2;
}

#endif

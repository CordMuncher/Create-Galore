// Minecraft's light levels as light in real units (see globals.glsl): its brightness curve for the sky's and the
// blocks' light levels, how much torchlight a level gives, and the sky's light on a surface. Shared by the lighting
// (lighting.glsl) and the light in the air (air_volume.csh).

#if !defined LIB_LIGHT_LEVELS
#define LIB_LIGHT_LEVELS

#include "/lib/settings.glsl"
#include "/lib/globals.glsl"

// A light level 15 surface gets light_block_lux of torchlight (torch_color, see light_volume.glsl): a little more than
// a real torch gives up close, so a room lit by its lamps reads as lit, not as a dim night.
const float light_block_lux = 0.024;

// Minecraft's brightness for a light level (0..1 for 0..15): its lightmap's curve.
float light_curve(float level) {
    return level / (4.0 - 3.0 * level);
}

// The sky's light for a sky light level (0..1): Minecraft's curve, to the power SKY_FALLOFF.
float sky_light_curve(float level) {
    return pow(light_curve(level), SKY_FALLOFF);
}

// The blocks' light for a block light level (0..1): Minecraft's curve, to the power BLOCK_FALLOFF.
float block_light_curve(float level) {
    return pow(light_curve(level), BLOCK_FALLOFF);
}

// The sky's light falling on a surface facing n: exact for a block's six faces (see frame_globals.csh), blended
// between them for any other direction.
vec3 sky_irradiance(vec3 n) {
    vec3 w = n * n / max(dot(n, n), 1e-8);
    return w.x * global(G_SKY_CUBE + (n.x >= 0.0 ? 0 : 1)).rgb
            + w.y * global(G_SKY_CUBE + (n.y >= 0.0 ? 2 : 3)).rgb
            + w.z * global(G_SKY_CUBE + (n.z >= 0.0 ? 4 : 5)).rgb;
}

// By day the blocks' light is at least this much in the frame (frame units, times BLOCK_LIGHT_DAY): a few times what
// lights the shade by day at the exposure's key, so a lantern visibly lights the shade around it.
const float light_block_day_frame = 16.0;

// The torchlight a light level 15 gives (klux), raised by day as light_block_day_frame says.
float block_light_lux() {
    return max(light_block_lux, light_block_day_frame * BLOCK_LIGHT_DAY / exposure());
}

#endif

// The Overworld's haze far off: the air between you and what you see, thickest at sea level, in the colour of the
// sky behind it, starting slowly. For the frame (program/fog.fsh) and anything that draws far water or land on its
// own.

#if !defined LIB_HAZE
#define LIB_HAZE

#include "/lib/settings.glsl"
#include "/lib/common.glsl"

// The average of e^((y - base) / scale) along a straight line from height y0 to y1: how much of the way a layer that
// thickens (scale > 0) or thins (scale < 0) upwards takes up.
float layer_average(float y0, float y1, float base, float scale) {
    float a = (y0 - base) / scale;
    float b = (y1 - base) / scale;
    if (abs(b - a) < 1e-3) {
        return exp(a);
    }
    return (exp(b) - exp(a)) / (b - a);
}

#if defined SUNLIGHT

#include "/lib/sky.glsl"

// The haze's thickness along a look between heights y0 and y1, against its thickness at sea level: the same up to
// Y 63, thinning out above it (by e every 60 blocks). So a look down from high up goes through the thin air up there
// as well as the thick air at the bottom.
float haze_share(float y0, float y1) {
    float lo = min(y0, y1);
    float hi = max(y0, y1);
    if (hi <= 63.0) {
        return 1.0;
    }
    if (lo >= 63.0) {
        return layer_average(lo, hi, 63.0, -60.0);
    }
    float below = (63.0 - lo) / (hi - lo);
    return below + (1.0 - below) * layer_average(63.0, hi, 63.0, -60.0);
}

// A colour seen dist blocks off along dir (scene space, from the camera), with the haze over it. It starts slowly
// (squared), so the land around you stays clear: at sea level about 0.05 at 64 blocks, 0.15 at 128, 0.35 at 256;
// thinner along a look through higher air; three and a half times thicker in rain, which closes the distance in,
// and a little thicker under a dry closed sky of the mod's weather (g_water.z, lib/weather.glsl).
// At the edge of what is drawn (far_end) it is all haze. solid: 0 for the sky itself, which already is that colour.
vec3 haze_over(vec3 color, vec3 dir, float dist, float solid, float far_end) {
    float d = min(dist, far_end);
    float density = 0.005 * haze_share(cameraPosition.y, cameraPosition.y + dir.y * d)
            * (1.0 + max(2.5 * rainStrength, 0.6 * g_water.z));
    float haze = sqr(1.0 - exp(-d * density));
    float border = smoothstep(far_end * 0.8, far_end, dist);
    haze = max(haze * mix(0.7, 0.95, rainStrength), border) * solid;
    if (haze <= 0.0) {
        return color;
    }
    // In the colour of the sky right behind what it hides (its horizon in that direction, or the sky itself above
    // it), so a far hill against the sunset fades into the sunset and one across from it into the blue.
    vec3 behind = normalize(vec3(dir.x, max(dir.y, 0.0) + 0.05, dir.z));
    vec3 haze_color = sky_look(sky_air_seen(behind), behind);
    // Mixed in display space, like the light on surfaces: in linear light even a little of the bright horizon
    // lifts dark land a lot, so the distance greys out.
    const vec3 to_display = vec3(1.0 / 2.2);
    vec3 shown = mix(pow(max(color, 0.0), to_display), pow(haze_color, to_display), haze);
    return pow(shown, vec3(2.2));
}

#endif

#endif

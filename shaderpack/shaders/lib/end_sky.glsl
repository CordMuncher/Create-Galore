// The End's own sky (see settings.glsl): a void full of stars with a nebula across it, violet and teal, brightest
// along a band and crossed by dark dust, still, as the End has no days. In Minecraft's own look it fades into the
// End's fog (each biome's own) at the horizon, in Minecraft's colours; with the lighting on, the End's haze lies in
// front of it instead (see end.glsl).
//
// The nebula's shapes come from the clouds' noise textures (Alpha Piscium's, see clouds.glsl); the stars are the
// overworld's (stars.glsl), in a frame of their own.

#if !defined LIB_END_SKY
#define LIB_END_SKY

#include "/lib/settings.glsl"
#include "/lib/color.glsl"
#include "/lib/vanilla.glsl"
#include "/lib/stars.glsl"

uniform sampler3D cg_cumulus_detail1_tex;
uniform sampler3D cg_cumulus_detail2_tex;
uniform sampler3D cg_cumulus_curl_tex;

// What lies beyond the End's air along dir (world space): the void, the nebula and the stars, linear, white 1.
// pixel_angle: a render pixel's size in radians, for the stars.
vec3 end_space(vec3 dir, float pixel_angle) {
    // The void itself, a little violet.
    vec3 sky = vec3(0.0015, 0.0006, 0.003);

    // The nebula: billows twisted by curl noise, at three scales, brighter along a band tilted across the sky.
    const vec3 band_normal = vec3(0.3647, 0.7815, 0.5064);
    float across = dot(dir, band_normal);
    float band = exp(-across * across / (2.0 * 0.22 * 0.22));
    vec3 p = dir * 1.3;
    vec3 warp = texture(cg_cumulus_curl_tex, p * 0.35).xyz;
    float cloud = texture(cg_cumulus_detail1_tex, p * 0.5 + warp * 0.25).r * 0.55
            + texture(cg_cumulus_detail1_tex, p * 1.3 + warp * 0.4 + 3.7).r * 0.3
            + texture(cg_cumulus_detail2_tex, p * 2.9 + warp * 0.6).r * 0.15;
    float glow = smoothstep(0.35, 0.8, cloud) * (0.12 + 0.88 * band);
    float hue = smoothstep(0.3, 0.7, texture(cg_cumulus_detail2_tex, p * 0.6 + 9.1).r);
    vec3 nebula = mix(vec3(0.30, 0.05, 0.50), vec3(0.03, 0.30, 0.34), hue) * glow * 0.35;
    // Its hot heart along the band, and the dust in front of it.
    nebula += vec3(0.55, 0.32, 0.65) * pow(glow, 3.0) * band * 0.25;
    float dust = smoothstep(0.5, 0.85, texture(cg_cumulus_detail2_tex, p * 4.3 + warp).r) * band;
    sky += nebula * (1.0 - 0.75 * dust) * END_NEBULA;

    // The stars, behind the dust (their brightness as the overworld's on a clear night, in this light).
    vec3 star_light = stars(dir, vec3(0.0, 1.0, 0.0), vec3(1.0, 0.0, 0.0), pixel_angle) * (2400.0 / moon_boost);
    sky += star_light * (1.0 - 0.9 * dust) * END_STARS;
    return sky;
}

// The End's sky along dir as Minecraft's own look has it, sRGB-encoded: fading into the End's fog at the horizon,
// where its islands fade into it.
vec3 end_sky(vec3 dir, float pixel_angle) {
    vec3 sky = mix(end_space(dir, pixel_angle), srgb_to_linear(fogColor), exp(-abs(dir.y) * 5.0) * 0.85);
    return linear_to_srgb(sky);
}

#endif

// What the lit overworld's programs share: Minecraft's vertex colours made into surface colours again, what glows
// by itself, the fog, and the sky's reflection on water and glass. Every result is in the frame's units (the
// light in kcd/m² times the exposure, see globals.glsl).

#if !defined LIB_LIT
#define LIB_LIT

#include "/lib/lighting.glsl"
#include "/lib/sky.glsl"
#include "/lib/vanilla.glsl"
#include "/lib/color.glsl"
#include "/lib/gbuffer.glsl"

uniform int isEyeInWater;       // 1 water, 2 lava, 3 powder snow
uniform float blindness;
uniform float darknessFactor;
uniform ivec2 eyeBrightnessSmooth; // the light where you are, 0..240 (block, sky)

// What glows by itself (glowing eyes, the beacon's beam, the enchantment glint, a charged creeper's swirl) looks on
// the screen as it does in Minecraft, whatever the exposure: a colour of 1 is this much in the frame.
const float glow_frame = 16.0;

vec3 glow(vec3 srgb) {
    return srgb_to_linear(srgb) * glow_frame;
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// What a block that gives light gives off by itself (kcd/m², not exposed), for its light level (0..1): the bright
// parts of its texture glow (a torch's flame, not its stick; a lantern's light, not its frame), as bright as a lamp's
// face at level 15, so they stand out by day and glow at night.
const float emission_luminance = 16.0 * EMISSION;

vec3 block_emission(vec3 srgb, float level) {
    vec3 c = srgb_to_linear(srgb);
    float peak = max(c.r, max(c.g, c.b));
    return c * smoothstep(0.2, 0.8, peak) * level * level * emission_luminance;
}

// What the fog fades into, looking along dir (world space), in kcd/m² (not exposed): the sky just over the horizon
// in the open air, else Minecraft's own fog colour (lava, powder snow) lit by the light where you are. (Water is the
// composite pass's, see water_body.fsh.)
vec3 fog_color_lit(vec3 dir) {
    if (isEyeInWater == 0) {
        vec3 horizon = normalize(vec3(dir.x, max(dir.y, 0.02), dir.z));
        return sky_radiance(horizon) * (1.0 - max(blindness, darknessFactor));
    }
    vec3 medium = srgb_to_linear(fogColor);
    if (isEyeInWater == 2) {
        return medium * glow_frame / exposure(); // lava glows
    }
    // The fog's hue, with the brightness of what the water (or the snow) scatters of the light around you.
    medium *= (isEyeInWater == 3 ? 0.6 : 0.12) / max(luminance(medium), 1e-4);
    vec2 eye = clamp(vec2(eyeBrightnessSmooth) / 240.0, 0.0, 1.0);
    vec3 ambient = (sky_irradiance(vec3(0.0, 1.0, 0.0)) + g_light_color.rgb * max(g_light_dir.y, 0.0)) * sky_light_curve(eye.y)
            + block_irradiance(eye.x, block_light_color(vec3(0.0), vec3(0.0)));
    return medium / PI * ambient * (1.0 - max(blindness, darknessFactor));
}

// Rain and snow where you are, faded in and out (see shaders.properties): 0..1 each.
uniform float cg_rain_here;
uniform float cg_snow_here;

// The haze of falling rain or snow over dist blocks in the open air: they let you see about a kilometre in rain,
// 400 blocks in a thunderstorm and 600 in snow. Indoors and underground (where little sky reaches you) there is
// none between you and what you see.
float weather_haze(float dist) {
    float density = cg_rain_here * mix(1.0 / 1000.0, 1.0 / 400.0, thunderStrength) + cg_snow_here / 600.0;
    float open_sky = smoothstep(0.4, 0.9, float(eyeBrightnessSmooth.y) / 240.0);
    return (1.0 - exp(-dist * density)) * open_sky * (1.0 - float(isEyeInWater != 0));
}

// Fog over something lit to color (frame units) at a fog distance dist, seen along dir, in a fog range (see
// fog_range), and the haze of the weather. Light that glows by itself fades out instead (fog_fade). Under water the
// composite pass draws the water between you and everything (see water_body.fsh) instead.
vec3 apply_fog_lit(vec3 color, float dist, vec3 dir, vec2 range) {
    if (isEyeInWater == 1) {
        return color;
    }
    float amount = 1.0 - (1.0 - fog_amount(dist, range.x, range.y)) * (1.0 - weather_haze(dist));
    return amount > 0.0 ? mix(color, fog_color_lit(dir) * exposure(), amount) : color;
}

// A surface its own program lights (translucent ones, particles, rain, clouds), in the frame's units.
vec3 forward_lit(vec3 srgb, vec3 normal, vec2 light, vec3 scene_pos, bool plant) {
    return light_surface(srgb_to_linear(srgb), normal, light, scene_pos, plant, true) * exposure();
}

// Water, glass and ice reflect the sky (Schlick's Fresnel for water) where they see it. color: the surface lit and
// fogged, as alpha-blended over what is behind it; view_dir from the eye.
vec4 with_sky_reflection(vec4 color, vec3 normal, vec3 view_dir, float sky) {
    float cos_theta = clamp(dot(-view_dir, normal), 0.0, 1.0);
    float f = 0.02 + 0.98 * pow(1.0 - cos_theta, 5.0);
    f *= smoothstep(12.0 / 15.0, 14.5 / 15.0, sky);
    vec3 dir = reflect(view_dir, normal);
    dir = normalize(vec3(dir.x, max(dir.y, 0.02), dir.z));
    vec3 reflection = sky_radiance(dir) * exposure();
    // The reflection on top, the surface and what is behind it seen through the rest.
    float alpha = 1.0 - (1.0 - f) * (1.0 - color.a);
    vec3 rgb = (reflection * f + color.rgb * color.a * (1.0 - f)) / max(alpha, 1e-4);
    return vec4(rgb, alpha);
}

// Rain wets what it falls on (see shaders.properties for cg_wet): how wet a surface with sky light sky (0..1)
// facing normal is, the tops most.
uniform float cg_wet;

float wetness_at(vec3 normal, float sky) {
    return cg_wet * smoothstep(13.5 / 15.0, 14.5 / 15.0, sky) * mix(0.35, 1.0, max(normal.y, 0.0));
}

// The film of water on a wet surface (wet 0..1): it reflects the sky (Schlick's Fresnel for water, the sky half
// blurred, as the surface under it is rough) over the surface's own light (frame units).
vec3 with_wet_film(vec3 color, vec3 normal, vec3 view_dir, float wet) {
    float cos_theta = clamp(dot(-view_dir, normal), 0.0, 1.0);
    float f = (0.02 + 0.98 * pow(1.0 - cos_theta, 5.0)) * wet;
    vec3 r = reflect(view_dir, normal);
    r = normalize(vec3(r.x, max(r.y, 0.02), r.z));
    vec3 reflection = mix(sky_irradiance(r) / PI, sky_radiance(r), 0.5) * exposure();
    return mix(color, reflection, f);
}

#endif

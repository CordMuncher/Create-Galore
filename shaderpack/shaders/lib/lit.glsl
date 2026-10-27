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
#include "/lib/material.glsl"

uniform int isEyeInWater;       // 1 water, 2 lava, 3 powder snow
uniform float blindness;
uniform float darknessFactor;
#include "/lib/weather.glsl"
#include "/lib/air.glsl"

// What glows by itself (glowing eyes, the beacon's beam, the enchantment glint, a charged creeper's swirl) looks on
// the screen as it does in Minecraft, whatever the exposure: a colour of 1 is this much in the frame.
const float glow_frame = 16.0;

vec3 glow(vec3 srgb) {
    return srgb_to_linear(srgb) * glow_frame;
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// What a block that gives light gives off by itself (in the frame's units, exposed), for its light level (0..1) and
// kind (GLOW_): the bright parts of its texture glow (a torch's flame, not its stick; a lantern's light, not its
// frame). By day as bright as a lamp's face, so they stand out; in the dark no more than a few times what the eyes
// have adapted to, so they glow in their own colours with their texture showing, rather than burning out white as a
// camera would see them. A whole glowing face (glowstone, a sea lantern, lava) fills much more of the view than a
// flame, so it is held lower still, and keeps its colour. Lava glows by its texture: the hot, bright streaks fully,
// the darker crust a little, so a lava fall shows its flow instead of one flat colour.
const float emission_luminance = 16.0;      // kcd/m² at level 15
const float emission_frame_max = 10.0;      // frame units at level 15
const float emission_frame_max_large = 4.0; // a whole face's

vec3 block_emission(vec3 srgb, float level, int kind) {
    vec3 c = srgb_to_linear(srgb);
    float peak = max(c.r, max(c.g, c.b));
    float glows = kind == GLOW_LAVA ? 0.12 + 0.88 * smoothstep(0.18, 0.7, luminance(c)) : smoothstep(0.2, 0.8, peak);
    float cap = kind == GLOW_SMALL ? emission_frame_max : emission_frame_max_large;
    return c * glows * level * level * min(emission_luminance * exposure(), cap) * EMISSION;
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

// Rain and snow where you are (cg_rain_here, cg_snow_here: see wet.glsl).
#include "/lib/wet.glsl"

// The haze of falling rain or snow over dist blocks in the open air: they let you see about 700 blocks in rain,
// 300 in a thunderstorm and 600 in snow. Indoors and underground (where little sky reaches you) there is none
// between you and what you see.
float weather_haze(float dist) {
    float density = cg_rain_here * mix(1.0 / 700.0, 1.0 / 300.0, thunderStrength) + cg_snow_here / 600.0;
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

// The sky's own air from start to dist blocks out along dir (scene space, from the eye): rgb the light it scatters
// towards the eye (frame units), a how much of what lies behind gets through. Worked out along the ray in a few steps
// with the same light as the sky's table (atmosphere.glsl, air.csh), so far land and water fade into the haze the way
// the horizon over them does, smoothly from pixel to pixel.
vec4 air_far_between(vec3 dir, float start, float dist) {
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y + gbufferModelViewInverse[3].y), 0.0) + dir * (start * 0.001);
    vec3 through;
    vec3 light = atm_segment(origin, dir, (dist - start) * 0.001, 6, sun_dir_world(), sun_illuminance, moon_dir_world(),
            moon_illuminance(), through);
    return vec4(weather(light) * exposure(), dot(through, vec3(0.2126, 0.7152, 0.0722)));
}

// The light in the air between the eye and what lies at view depth z (blocks), seen at point (unjittered render
// pixels) along dir (scene space, from the eye): rgb the light it scatters towards the eye (frame units), a how much
// of what lies behind gets through. The volume's (air.glsl) up to air_sky_start blocks out, with its shafts, dust and
// lamp glow; past that the sky's own air (air_far_between). None under water, in lava or in powder snow, which have
// their own.
vec4 air_seen(vec2 point, float z, vec3 dir) {
#ifdef LIT_AIR
    if (isEyeInWater == 0) {
        float along = max(dot(dir, -gbufferModelViewInverse[2].xyz), 1e-3); // view depth per block along dir
        float dist = z / along;
        if (dist <= air_sky_start) {
            return air_between(point, z);
        }
        vec4 near = air_between(point, air_sky_start * along);
        vec4 far = air_far_between(dir, air_sky_start, dist);
        return vec4(near.rgb + far.rgb * near.a, near.a * far.a);
    }
#endif
    return vec4(0.0, 0.0, 0.0, 1.0);
}

// The same for a fragment at scene_pos, frag_coord its gl_FragCoord.xy.
vec4 air_seen_at(vec3 scene_pos, vec2 frag_coord) {
    vec3 eye = scene_pos - gbufferModelViewInverse[3].xyz;
    float z = -dot(mat3(gbufferModelViewInverse)[2], eye);
    return air_seen(frag_coord - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y), z, normalize(eye));
}

// What lies behind seen through that air.
vec3 through_air(vec3 color, vec4 air) {
    return color * air.a + air.rgb;
}

// A surface its own program lights (translucent ones, particles, rain, clouds), in the frame's units.
vec3 forward_lit(vec3 srgb, vec3 normal, vec2 light, vec3 scene_pos, bool plant) {
    return light_surface(srgb_to_linear(srgb), normal, light, scene_pos, plant, true) * exposure();
}

// Schlick's Fresnel: how much a surface reflects (f0 head on) at an angle whose cosine is cos_theta.
vec3 fresnel_schlick(float cos_theta, vec3 f0) {
    return f0 + (1.0 - f0) * pow(1.0 - clamp(cos_theta, 0.0, 1.0), 5.0);
}

// The same for the light all around reflected off a rough surface (GGX alpha): less of a rise at a glancing angle,
// as much of a rough surface's reflection there comes from directions it reflects less of (Fdez-Agüera's fit of the
// split sum, as Lagarde's).
vec3 fresnel_rough(float cos_theta, vec3 f0, float alpha) {
    return f0 + (max(vec3(1.0 - sqrt(alpha)), f0) - f0) * pow(1.0 - clamp(cos_theta, 0.0, 1.0), 5.0);
}

// The sun's (or the moon's) light mirrored towards the eye by a surface facing n (view_dir from the eye), rough as
// GGX alpha, reflecting f0 head on, where lit (0..1) of the light reaches it: kcd/m², not exposed.
vec3 sun_glint(vec3 n, vec3 view_dir, float alpha, vec3 f0, float lit) {
    vec3 l = g_light_dir.xyz;
    vec3 v = -view_dir;
    float n_dot_l = dot(n, l);
    float n_dot_v = max(dot(n, v), 1e-3);
    if (n_dot_l <= 0.0 || lit <= 0.0) {
        return vec3(0.0);
    }
    vec3 h = normalize(l + v);
    float n_dot_h = max(dot(n, h), 0.0);
    float a2 = alpha * alpha;
    float d = n_dot_h * n_dot_h * (a2 - 1.0) + 1.0;
    float ggx = a2 / (PI * d * d);
    float k = alpha * 0.5;
    float g = n_dot_l / (n_dot_l * (1.0 - k) + k) * n_dot_v / (n_dot_v * (1.0 - k) + k);
    return g_light_color.rgb * lit * ggx * g * fresnel_schlick(dot(h, v), f0) / (4.0 * n_dot_v);
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

// Rain wets what it falls on (see shaders.properties for cg_wet and cg_damp): how wet a surface with sky light sky
// (0..1) facing normal is, the tops most: the film of water on it (x) and how damp it is under that (y), which dries
// more slowly.
vec2 wetness_at(vec3 normal, float sky) {
    return vec2(cg_wet, cg_damp) * smoothstep(13.5 / 15.0, 14.5 / 15.0, sky) * mix(0.35, 1.0, max(normal.y, 0.0));
}

// A surface's colour (albedo) when soaked by damp (0..1), by how much water it takes in (porosity, 0..1): water in
// its pores keeps the light bouncing inside it longer, so it comes out darker and deeper in colour, mid tones the
// most (Sébastien Lagarde's wet surfaces, Remember Me): towards its colour squared.
vec3 soaked(vec3 albedo, float damp, float porosity) {
    float soak = damp * porosity;
    return mix(albedo, albedo * albedo, 0.85 * soak) * (1.0 - 0.2 * soak);
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

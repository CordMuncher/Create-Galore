// The water's surface, as every pass that draws water shades it (Minecraft's in program/gbuffers.fsh, Distant
// Horizons' in program/dh.fsh, the preview in tools/view_tester), so the far sea is the same water as the near one:
// - the waves a look can see there and what the rest does as roughness (lib/waves.glsl);
// - how much the surface reflects (Fresnel, exact for water) and how far it spreads what it reflects;
// - the water's own colour: the light it scatters back from its depth, a clear blue in the sun (the body of a sea or
//   a lake, where no floor shows through);
// - the sun through the crests: with a low sun in front of you, the faces of the waves turned your way glow
//   blue-green with the light that came in through their backs.
// What lies under the water, the sky and the world it mirrors and the sun's glint are each pass's own.

#if !defined LIB_WATER_SHADE
#define LIB_WATER_SHADE

#include "/lib/settings.glsl"
#include "/lib/waves.glsl"
#include "/lib/sky.glsl"

struct WaterSurface {
    vec3 n;        // the surface's normal on your side, with the waves you can see
    float spread;  // how far the waves finer than a pixel scatter a reflection (radians): the sun's glint
    float soft;    // how soft what it mirrors is (radians): those and half of the waves you can see, which break
                   // a mirrored cloud or shore into streaks along the look unless it is already soft
    float cos_v;   // the cosine between that normal and the look
    float fresnel; // how much it reflects
    float facing;  // how steeply the waves there face you (their slope towards you, seen and unseen)
};

// The waves at a point of a level surface (scene space) as a look sees them. to_eye: towards your eye; open: how open
// to the sky the water is (0 in a cave .. 1, Minecraft's sky light: weaker waves under a roof); swell: how much room
// the long waves have to build (0..1); rain: how hard it rains on it. Flatter at a grazing look, where the waves
// would only break the reflection into noise; what that takes off is still there, finer than the eye follows:
// roughness.
Waves water_waves_seen(vec3 scene_pos, vec3 to_eye, float open, float swell, float rain, out float footprint) {
    // A pixel's spot on the water is as long as it is wide looking straight down, and drawn out along the look at a
    // grazing one: a wave must be longer than most of that to show.
    float grazing = abs(to_eye.y);
    footprint = length(scene_pos) * pixel_angle() * pow(max(grazing, 0.01), -0.8);
    float strength = WATER_WAVES * mix(0.3, 1.0, open) * water_chop(scene_pos);
    Waves w = water_waves(water_local(scene_pos), strength, swell, rain, footprint);
    float flatter = mix(0.35, 1.0, smoothstep(0.0, 0.25, grazing));
    w.slope *= flatter;
    w.rough += w.seen * (1.0 - flatter * flatter);
    return w;
}

// The surface those waves make. face: the water's own face on your side (up, or down from under it); inside: your eye
// is under water.
WaterSurface water_finish(Waves w, vec3 face, vec3 to_eye, bool inside) {
    WaterSurface s;
    vec2 towards = normalize(to_eye.xz + vec2(1e-5, 0.0));
    s.facing = max(dot(w.slope, towards), 0.0) + 0.5 * sqrt(w.rough);
    if (face.y > 0.0 && !inside) {
        w.slope += facing_tilt(w.rough, to_eye);
    }
    vec3 wave = wave_normal(w);
    s.n = face.y > 0.0 ? wave : -wave;
    // A wave's far side is hidden behind its crest: no normal turns away from the look.
    float turned = dot(s.n, to_eye);
    if (turned < 0.02) {
        s.n = normalize(s.n + to_eye * (0.02 - turned));
    }
    s.spread = 2.0 * sqrt(w.rough);
    s.soft = 2.0 * sqrt(w.rough + 0.5 * w.seen);
    s.cos_v = max(dot(s.n, to_eye), 0.0);
    s.fresnel = fresnel_dielectric(s.cos_v, inside ? 1.0 / water_ior : water_ior);
    return s;
}

// The light the water scatters towards you from depth blocks of it along the look, from the light that came in at
// the surface (light_here; dimmed on its way down as well as on its way back): it fades in over the first blocks and
// then holds, the water's own colour. extinction: what a look through it loses per block (water_view_extinction);
// through: what is left of what lies under it.
vec3 water_body(vec3 light_here, vec3 extinction, float depth, float cos_v, out vec3 through) {
    through = exp(-extinction * depth);
    float cos_t = sqrt(max(1.0 - (1.0 - cos_v * cos_v) / (water_ior * water_ior), 0.0));
    vec3 along = extinction * (1.0 + 1.15 * cos_t);
    return light_here * water_view_glow * (1.0 - exp(-along * depth)) / along;
}

// The sun through the crests (to add to the water's own light, under the surface's reflection). sun: the sun's light
// on the water there (shadows and all).
vec3 water_crest_glow(WaterSurface s, vec3 to_eye, vec3 sun) {
#if defined SUNLIGHT
    vec3 l = g_light_dir.xyz;
    vec2 flat_sun = normalize(l.xz + vec2(1e-5, 0.0));
    vec2 flat_look = -normalize(to_eye.xz + vec2(1e-5, 0.0));
    // In front of you and low: the light has to come through the wave towards you.
    float ahead = sqr(max(dot(flat_look, flat_sun), 0.0)) * (1.0 - smoothstep(0.25, 0.8, l.y));
    return sun * vec3(0.02, 0.22, 0.2) * ahead * min(s.facing * 4.0, 1.0);
#else
    return vec3(0.0);
#endif
}

#endif

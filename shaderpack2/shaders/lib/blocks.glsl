// The block numbers of block.properties (and the entity numbers of entity.properties), and what each kind of light
// gives.

#if !defined LIB_BLOCKS
#define LIB_BLOCKS

#include "/lib/settings.glsl"
#include "/lib/common.glsl"

#define BLOCK_WATER 1
#define BLOCK_PLANT 2
#define BLOCK_LEAVES 3
#define BLOCK_METAL 5
#define BLOCK_POLISHED 6
#define BLOCK_LIGHT 100 // and up: the kind of light (number - 100), see light_kind
#define LIGHT_KINDS 38

// entity.properties
#define ENTITY_PLAYER 1 // you (not other players)

// Kinds whose look the programs treat apart.
#define KIND_LAVA 15
#define KIND_FIRE 16
#define KIND_SOUL_FIRE 17
#define KIND_CAMPFIRE 18
#define KIND_SOUL_CAMPFIRE 19
#define KIND_MAGMA 23
#define KIND_END_PORTAL 34

// A kind of light's colour (rgb, 0..1, at full strength) and level (a, 0..31: how far it reaches and how bright it
// is). The colours lean towards strong, clear hues: a torch and a lantern a warm orange, a soul lantern a strong blue
// (little green in it, so where it meets warm light the two add up to white, not green), redstone red.
// A negative red means "take the colour from the texture" (flames, whose textures say it best).
vec4 light_kind(int kind) {
    switch (kind) {
        case 0: return vec4(1.0, 0.54, 0.24, 21.0);   // torch
        case 1: return vec4(1.0, 0.5, 0.2, 23.0);     // lantern
        case 2: return vec4(0.04, 0.72, 1.0, 21.0);   // soul torch
        case 3: return vec4(0.04, 0.72, 1.0, 23.0);   // soul lantern
        case 4: return vec4(1.0, 0.75, 0.55, 22.0);   // glowstone
        case 5: return vec4(1.0, 0.7, 0.4, 22.0);     // redstone lamp
        case 6: return vec4(1.0, 0.5, 0.2, 22.0);     // shroomlight
        case 7: return vec4(0.85, 0.95, 0.95, 22.0);  // sea lantern
        case 8: return vec4(1.0, 0.9, 0.85, 22.0);    // end rod
        case 9: return vec4(1.0, 0.6, 0.1, 23.0);     // ochre froglight
        case 10: return vec4(0.65, 1.0, 0.15, 23.0);  // verdant froglight
        case 11: return vec4(0.9, 0.25, 1.0, 23.0);   // pearlescent froglight
        case 12: return vec4(1.0, 0.05, 0.05, 16.0);  // redstone torch
        case 13: return vec4(1.0, 0.05, 0.05, 20.0);  // block of redstone
        case 14: return vec4(1.0, 0.05, 0.05, 16.0);  // lit redstone ore
        case 15: return vec4(1.0, 0.5, 0.16, 27.0);   // lava (one light per patch, see program/world.csh)
        case 16: return vec4(-1.0, 0.0, 0.0, 23.0);   // fire
        case 17: return vec4(-1.0, 0.0, 0.0, 23.0);   // soul fire
        case 18: return vec4(-1.0, 0.0, 0.0, 20.0);   // campfire
        case 19: return vec4(-1.0, 0.0, 0.0, 20.0);   // soul campfire
        case 20: return vec4(-1.0, 0.0, 0.0, 10.0);   // candles
        case 21: return vec4(0.95, 0.6, 0.3, 20.0);   // jack o'lantern
        case 22: return vec4(0.95, 0.5, 0.2, 17.0);   // lit furnace, smoker, blast furnace
        case 23: return vec4(0.9, 0.4, 0.05, 17.0);   // magma
        case 24: return vec4(0.55, 0.15, 1.0, 12.0);  // crying obsidian
        case 25: return vec4(0.55, 0.15, 1.0, 16.0);  // nether portal, respawn anchor
        case 26: return vec4(0.75, 0.45, 1.0, 10.0);  // amethyst
        case 27: return vec4(0.05, 1.0, 1.0, 11.0);   // sculk sensor
        case 28: return vec4(0.85, 0.05, 0.8, 11.0);  // spawner
        case 29: return vec4(0.8, 1.0, 0.85, 12.0);   // glow lichen
        case 30: return vec4(0.65, 1.0, 1.0, 11.0);   // sea pickle
        case 31: return vec4(0.75, 0.4, 0.1, 15.0);   // glow berries
        case 32: return vec4(0.35, 1.0, 0.95, 24.0);  // beacon
        case 33: return vec4(0.6, 0.95, 1.0, 16.0);   // conduit
        case 34: return vec4(0.7, 1.0, 0.8, 10.0);    // end portal and gateway
        case 35: return vec4(0.9, 0.4, 0.1, 14.0);    // brewing stand
        case 36: return vec4(0.8, 0.4, 0.1, 4.0);     // enchanting table
        case 37: return vec4(1.0, 0.65, 1.0, 18.0);   // chorus flower
        default: return vec4(-1.0, 0.0, 0.0, 0.0);
    }
}

// A light Minecraft knows but the list doesn't (a modded lamp): its level from Minecraft's (0..15).
float unlisted_light_level(float vanilla_level) {
    return max(10.0, 31.0 * vanilla_level / 20.0);
}

// A held light's colour and level (as light_kind gives them), from the held item's number (item.properties) and
// the light Minecraft says it gives; a torch's colour for one the list doesn't know.
vec4 held_light(int item, float vanilla_level) {
    int kind = item - BLOCK_LIGHT;
    vec4 l = kind >= 0 && kind < LIGHT_KINDS ? light_kind(kind) : vec4(light_kind(0).rgb, 0.0);
    if (l.r < 0.0) {
        // Kinds that take their colour from their texture when placed.
        l.rgb = kind == KIND_SOUL_FIRE || kind == KIND_SOUL_CAMPFIRE ? vec3(0.04, 0.72, 1.0)
              : vec3(1.0, 0.6, 0.3); // fire, campfires, candles
    }
    if (l.a <= 0.0) {
        l.a = unlisted_light_level(vanilla_level);
    }
    return l;
}

// A texture's colour made into a light's: the brightest channel stays, the others move away from it, by up to the
// point where the darkest would reach black (half way there by default), and to no more than three times as far.
vec3 light_color_from_texture(vec3 c) {
    float top = max3(c);
    float gap = top - min3(c);
    if (gap < 1e-4) {
        return c;
    }
    float stretch = min(mix(1.0, top / gap, 0.5 * LIGHT_SATURATION), 3.0);
    return max(vec3(top) - (vec3(top) - c) * stretch, 0.0);
}

// How bright a light of a level is, at a block's distance (see light_falloff).
float light_brightness(float level) {
    return sqr(1.5 * level / 32.0) * BLOCK_LIGHT_STRENGTH;
}

// How far a light of a level reaches, in blocks.
float light_reach(float level) {
    return BLOCK_LIGHT_REACH * level / 31.0;
}

// How much of a light arrives at a distance: falling with the square of the distance (softened right at the light,
// over light_soft_core blocks), eased to nothing at its reach, so each pool of light has a clear edge. The core keeps
// a lamp as bright at its foot as before and sends about twice as much a few blocks out, so light carries further
// and softer (halfway to the look we follow, which sends about five times as much).
const float light_soft_core = 2.2;

float light_falloff(float distance, float reach) {
    float edge = clamp(1.0 - sqr(sqr(distance / reach)), 0.0, 1.0);
    return edge * edge * light_soft_core / (distance * distance + light_soft_core);
}

// Log compression on the light's length: one lantern is bright, ten of them add up softly instead of blowing out.
// Surfaces use it as it is (times 4), never expanded again.
vec3 compress_light(vec3 c) {
    float len = length(c);
    return len > 1e-6 ? c * (log(1.0 + len) / len) : c;
}

vec3 expand_light(vec3 c) {
    float len = length(c);
    return len > 1e-6 ? c * ((exp(len) - 1.0) / len) : c;
}

#endif

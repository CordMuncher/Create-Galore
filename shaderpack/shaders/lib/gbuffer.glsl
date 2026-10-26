// The overworld's G-buffer: what the programs drawn before the deferred pass leave for it to light (see
// deferred.fsh). Only the lit overworld has one; everywhere else colortex0 is the scene itself.
//
// colortex0: light the surface gives off by itself or that its program already worked out (glowing eyes, the
//            enchantment glint, the beacon's beam, particles), in the frame's units; the deferred pass adds its
//            lighting to it. Cleared to black.
// colortex1: the surface's colour (RGBA8, sRGB-encoded): its texture times its tint, with Minecraft's smooth lighting
//            corners (ambient occlusion) in it. Blended the way the draw blends, so block cracks and mob shadows
//            darken what is under them, and a surface whose program lit it itself leaves black here.
// colortex2: the surface (RGBA16): rg the world-space normal (octahedral), b the material in the top two bits and
//            block and sky light below (7 bits each), a how much of it the draw writes. The programs blend it by that
//            alpha (see shaders.properties), always 0 or 1, so a draw over a surface (a mob's shadow, a charged
//            creeper's swirl) leaves the surface under it as it was. Cleared to 0: nothing drawn.

#if !defined LIB_GBUFFER
#define LIB_GBUFFER

#include "/lib/settings.glsl"

#ifdef LIT_WORLD
/*
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex1Format = RGBA8;
const int colortex2Format = RGBA16;
const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex2Clear = true;
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
#endif

#define MATERIAL_NONE 0  // nothing: the sky (or what the deferred pass shows behind something that only glows)
#define MATERIAL_LIT 1   // lit by the sun, the sky and the blocks
#define MATERIAL_PLANT 2 // lit the same, through its leaves from either side (grass, flowers, crops)
#define MATERIAL_SELF 3  // lit (or glowing) by its own program: colortex0 holds all of it (particles, the beam)
// Only between a translucent program's stages, never in the G-buffer:
#define MATERIAL_WATER 4

// The blocks block.properties numbers (mc_Entity.x).
#define BLOCK_WATER 10001
#define BLOCK_LIGHT_RED 10101 // 10101..10109: lights with colours of their own (see shadow.vsh)
#define BLOCK_LIGHT_LAST 10109

vec2 oct_wrap(vec2 v) {
    return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}

vec2 encode_normal(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
    vec2 e = n.y >= 0.0 ? n.xz : oct_wrap(n.xz);
    return e * 0.5 + 0.5;
}

vec3 decode_normal(vec2 e) {
    e = e * 2.0 - 1.0;
    vec3 n = vec3(e.x, 1.0 - abs(e.x) - abs(e.y), e.y);
    if (n.y < 0.0) {
        n.xz = oct_wrap(n.xz);
    }
    return normalize(n);
}

// The material and the block and sky light (0..1) as one 16-bit value.
float encode_material_light(int material, vec2 light) {
    vec2 q = floor(clamp(light, 0.0, 1.0) * 127.0 + 0.5);
    return (float(material) * 16384.0 + q.x * 128.0 + q.y) / 65535.0;
}

// weight: 1 to write the surface, 0 to leave the one under it.
vec4 encode_surface(vec3 normal, vec2 light, int material, float weight) {
    return vec4(encode_normal(normal), encode_material_light(material, light), weight);
}

int decode_material(vec4 data) {
    return int(floor(data.b * 65535.0 + 0.5)) >> 14;
}

vec2 decode_light(vec4 data) {
    int q = int(floor(data.b * 65535.0 + 0.5));
    return vec2((q >> 7) & 127, q & 127) / 127.0;
}

#endif

// The overworld's G-buffer: what the programs drawn before the deferred pass leave for it to light (see
// deferred.fsh). Only the lit overworld has one; everywhere else colortex0 is the scene itself.
//
// colortex0: light the surface gives off by itself or that its program already worked out (glowing eyes, the
//            enchantment glint, the beacon's beam, particles), in the frame's units; the deferred pass adds its
//            lighting to it. Cleared to black.
// colortex1: the surface's colour (RGBA8, sRGB-encoded): its texture times its tint. Blended the way the draw blends,
//            so block cracks and mob shadows darken what is under them, and a surface whose program lit it itself
//            leaves black here.
// colortex2: the surface (RGBA16): rg the world-space normal (octahedral), b the material in the top two bits and
//            block and sky light below (7 bits each), a how much of it the draw writes. The programs blend it by that
//            alpha (see shaders.properties), always 0 or 1, so a draw over a surface (a mob's shadow, a charged
//            creeper's swirl) leaves the surface under it as it was. Cleared to 0: nothing drawn.
// colortex9: the surface's occlusion and kind (RGBA8): r how open it is to the light all around, Minecraft's smooth
//            lighting corners (1 in the open, less in a corner or a crease, see separateAo in shaders.properties)
//            times its texture's own crevices (see material.glsl), g what it is made of (SURFACE_, over 7), b how much
//            of the sun's (or the moon's) light the texture's own relief lets reach it (1 all of it). Blended by alpha
//            as colortex2 is.
//            Cleared to 1, 0 and 1: what draws nothing here (Distant Horizons' terrain) has no corners.
// colortex12: the surface's material from the resource pack (RGBA8, see material.glsl): r how smooth, g what it
//            reflects (LabPBR's), b how porous (LabPBR's), all 0 where the pack has no maps for it (the pack's own
//            materials by its kind then). Blended by alpha as colortex2 is. Cleared to 0.
//
// And what the translucent water program leaves for the composite pass, which draws the water (see water_body.fsh):
// colortex4: the water's surface (RGBA16F): rg its normal (octahedral, its upper side's), b its sky light, a 1 where
//            there is water. The nearest drawn wins: water writes alpha 1, the other translucent blocks 0.
// colortex5: how far that surface is from the eye (blocks, R32F), likewise.
// colortex6: how far the nearest other translucent block (glass, ice) is from the eye, where there is one.
//
// colortex3: the frame as the water pass leaves it (R11F_G11F_B10F, kept from frame to frame), for the next frame's
//            reflections and the light surfaces bounce onto what is next to them, which are worked out before this
//            frame's picture exists.
//
// And what the screen shows around each surface (see screen_light.fsh): colortex13 this frame's (RGBA16F), colortex14
// the same blended over frames (RGBA16F, kept from frame to frame, see deferred.fsh). colortex15 (RG16F, kept from
// frame to frame): r the share of the sky the sky grid's rays from each surface see this frame, g the same blended
// over frames (see sky_rays_settled in deferred.fsh); -1 where they see none.

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
const int colortex9Format = RGBA8;
const bool colortex9Clear = true;
const vec4 colortex9ClearColor = vec4(1.0, 0.0, 1.0, 0.0);
const int colortex12Format = RGBA8;
const bool colortex12Clear = true;
const vec4 colortex12ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex4Format = RGBA16F;
const bool colortex4Clear = true;
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex5Format = R32F;
const bool colortex5Clear = true;
const vec4 colortex5ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex6Format = R32F;
const bool colortex6Clear = true;
const vec4 colortex6ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int colortex3Format = R11F_G11F_B10F;
const bool colortex3Clear = false;
const int colortex13Format = RGBA16F;
const bool colortex13Clear = false;
const int colortex14Format = RGBA16F;
const bool colortex14Clear = false;
const int colortex15Format = RG16F;
const bool colortex15Clear = false;
*/
#endif

#define MATERIAL_NONE 0  // nothing: the sky (or what the deferred pass shows behind something that only glows)
#define MATERIAL_LIT 1   // lit by the sun, the sky and the blocks
#define MATERIAL_PLANT 2 // lit the same, through its leaves from either side (grass, flowers, crops)
#define MATERIAL_SELF 3  // lit (or glowing) by its own program: colortex0 holds all of it (particles, the beam)
// Only between a translucent program's stages, never in the G-buffer:
#define MATERIAL_WATER 4

// What a surface is made of, as far as the light cares (colortex9's g, as SURFACE_ / 7).
#define SURFACE_PLAIN 0
#define SURFACE_LEAVES 1   // lets light through (see light_surface)
#define SURFACE_METAL 2    // shines (iron, gold, copper, brass)
#define SURFACE_POLISHED 3 // a soft sheen (polished stone, quartz, glazed terracotta, packed ice)
#define SURFACE_SOIL 4     // takes most rain in: puddles only in its deepest dips
#define SURFACE_SAND 5     // takes all rain in: damp, never a puddle (sand, gravel, snow)

int decode_surface_kind(float g) {
    return int(g * 7.0 + 0.5);
}

// The blocks block.properties numbers (mc_Entity.x).
#define BLOCK_WATER 10001
#define BLOCK_LEAVES 10002 // every block tagged as leaves, modded ones too
#define BLOCK_METAL 10003
#define BLOCK_POLISHED 10004
#define BLOCK_SOIL 10005
#define BLOCK_SAND 10006
#define BLOCK_LIGHT_RED 10101 // 10101..10109: lights with colours of their own (see shadow.vsh)
#define BLOCK_LIGHT_LAST 10109

// Grass, flowers and crops: their crossed quads face diagonally across the ground (and Minecraft doesn't shade them).
bool is_plant_normal(vec3 n) {
    return abs(n.y) < 0.01 && abs(abs(n.x) - abs(n.z)) < 0.01;
}

// The shading Minecraft gives each face of a block (Sodium bakes it into the vertex colour with oldLighting): up
// 1, down 0.5, north and south 0.8, east and west 0.6, by the face's nearest direction. The sun and the sky shade
// the faces now, so the programs take it out again (and the shadow pass, for the bounce light's colours).
float face_shade(vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) {
        return n.y > 0.0 ? 1.0 : 0.5;
    }
    return a.z >= a.x ? 0.8 : 0.6;
}

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

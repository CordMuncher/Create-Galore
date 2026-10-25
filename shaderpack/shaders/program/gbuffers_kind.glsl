// What each world program draws and how Minecraft 1.21.1 shades that, from the PROGRAM_ define its gbuffers_*
// file sets. Shared by gbuffers.vsh, gbuffers.fsh and the Colorwheel and Distant Horizons programs.
//
// Motion vectors go to colortex7 (see composite.fsh): rg the motion in render pixels towards last frame, b what
// drew the pixel (MOTION_WORLD, MOTION_HAND, or MOTION_NONE where the composite pass works it out from the depth),
// a whether the fragment takes the pixel over. Translucent programs blend colortex7 like colour, so a fragment that
// writes alpha 0 leaves what is behind it, and one that writes alpha 1 replaces it.

#if !defined PROGRAM_KIND
#define PROGRAM_KIND

#include "/lib/settings.glsl"

// Iris's entity vertex format (mobs, block entities, held items): the Bridge's cg_prev_pos, Minecraft's two light
// directions, the hurt and creeper flash (entityColor).
// (One condition per line: GLSL before 4.20 has no line continuation.)
#if defined PROGRAM_ENTITIES || defined PROGRAM_ENTITIES_TRANSLUCENT || defined PROGRAM_ENTITIES_GLOWING
#define ENTITY_FORMAT
#elif defined PROGRAM_BLOCK || defined PROGRAM_BLOCK_TRANSLUCENT || defined PROGRAM_SPIDEREYES
#define ENTITY_FORMAT
#elif defined PROGRAM_HAND || defined PROGRAM_HAND_WATER
#define ENTITY_FORMAT
#endif

#if defined ENTITY_FORMAT && !defined PROGRAM_SPIDEREYES
#define MOB_LIGHTING
#endif

#if defined PROGRAM_HAND || defined PROGRAM_HAND_WATER
#define HAND
#endif

#if !defined PROGRAM_BASIC && !defined PROGRAM_SKYBASIC && !defined PROGRAM_LIGHTNING
#define TEXTURED
#endif

// Things that move by themselves work out their own motion; the rest only claim the pixel (so the composite pass
// reprojects their depth) or, where they add light on top of what is there, leave it alone.
#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER || (defined ENTITY_FORMAT && !defined PROGRAM_SPIDEREYES) || defined COLORWHEEL
#define OWN_MOTION
#elif defined PROGRAM_SKYBASIC || defined PROGRAM_SKYTEXTURED || defined PROGRAM_WEATHER || defined PROGRAM_SPIDEREYES
#define NO_MOTION_CLAIM
#elif defined PROGRAM_LIGHTNING || defined PROGRAM_ARMOR_GLINT || defined PROGRAM_DAMAGEDBLOCK
#define NO_MOTION_CLAIM
#endif

// Light that adds itself to the scene fades into the fog instead of taking its colour.
#if defined PROGRAM_SPIDEREYES || defined PROGRAM_LIGHTNING || defined PROGRAM_ARMOR_GLINT
#define FOG_FADES
#endif

// Set by the Create Galore Bridge for each of Iris's shader keys (the vanilla shader a draw replaces), so programs
// that several of them share shade each like Minecraft: 1 known, 2 mob shading, 4 lightmap, 8 the lightmap filtered
// between levels (terrain's), 16 no texture (a name tag's background, Sodium's clouds), 32 drawn over something else
// (a charged creeper's swirl, a mob's shadow: the motion under it stays). Without the Bridge it stays 0 and every
// program takes its usual case.
#define KEY_KNOWN 1
#define KEY_MOB_SHADING 2
#define KEY_LIGHTMAP 4
#define KEY_LIGHTMAP_SMOOTH 8
#define KEY_UNTEXTURED 16
#define KEY_DRAWN_OVER 32

// The Bridge rewrites this exact line, in both stages, with the key's flags.
const int cg_key_flags = 0;

int key_flags() {
    if ((cg_key_flags & KEY_KNOWN) != 0) {
        return cg_key_flags;
    }
#if defined MOB_LIGHTING
    return KEY_MOB_SHADING | KEY_LIGHTMAP;
#elif defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    return KEY_LIGHTMAP | KEY_LIGHTMAP_SMOOTH;
#elif defined PROGRAM_PARTICLES || defined PROGRAM_WEATHER || defined PROGRAM_BASIC || defined PROGRAM_TEXTURED_LIT
    return KEY_LIGHTMAP;
#elif defined PROGRAM_CLOUDS
    return KEY_UNTEXTURED; // Sodium draws the clouds (Iris needs it), without a texture
#else
    return 0;
#endif
}

#ifdef LIT_WORLD
// How each program's draws are lit in the lit overworld (see deferred.fsh):
// - LIT_GBUFFER: they leave their surface for the deferred pass to light (terrain, mobs, block entities, the hand,
//   Create's machines). The LIT_SELF ones among them light themselves and leave only that (particles, lines, what
//   Iris draws through the textured programs), which holds whether Iris draws them before the deferred pass or after.
// - LIT_FORWARD: drawn after the deferred pass, over the lit scene, and lit by their own program (water and glass,
//   translucent held items, rain, clouds, Create's translucent parts).
// - LIT_GLOW: they only add light (glowing eyes, the enchantment glint, lightning).
// - LIT_SKY: Minecraft's sky, which the deferred pass draws instead.
#if defined PROGRAM_SKYBASIC || defined PROGRAM_SKYTEXTURED
#define LIT_SKY
#elif defined PROGRAM_WATER || defined PROGRAM_HAND_WATER || defined PROGRAM_WEATHER || defined PROGRAM_CLOUDS
#define LIT_FORWARD
#elif defined PROGRAM_CLRWL_TRANSLUCENT
#define LIT_FORWARD
#elif defined PROGRAM_SPIDEREYES || defined PROGRAM_ARMOR_GLINT || defined PROGRAM_LIGHTNING
#define LIT_GLOW
#elif defined PROGRAM_CLRWL_GLINT || defined PROGRAM_CLRWL_ADDITIVE || defined PROGRAM_CLRWL_LIGHTNING
#define LIT_GLOW
#else
#define LIT_GBUFFER
#if defined PROGRAM_PARTICLES || defined PROGRAM_TEXTURED || defined PROGRAM_TEXTURED_LIT || defined PROGRAM_BASIC
#define LIT_SELF
#endif
#endif
#endif

#endif

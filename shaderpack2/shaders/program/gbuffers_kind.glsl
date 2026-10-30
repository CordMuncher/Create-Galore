// What each world program draws, from the PROGRAM_ define its gbuffers_* file sets, and how the pack lights it.
// Shared by gbuffers.vsh, gbuffers.fsh and the Colorwheel and Distant Horizons programs.
//
// Motion vectors go to colortex7 (see composite1.fsh): rg the motion in render pixels towards last frame, b what
// drew the pixel (MOTION_WORLD, MOTION_HAND, or MOTION_NONE where composite1 works it out from the depth), a whether
// the fragment takes the pixel over.

#if !defined PROGRAM_KIND
#define PROGRAM_KIND

#include "/lib/settings.glsl"

// Iris's entity vertex format (mobs, block entities, held items): the Bridge's cg_prev_pos and the hurt flash.
#if defined PROGRAM_ENTITIES || defined PROGRAM_BLOCK || defined PROGRAM_SPIDEREYES
#define ENTITY_FORMAT
#elif defined PROGRAM_HAND || defined PROGRAM_HAND_WATER
#define ENTITY_FORMAT
#endif

#if defined PROGRAM_HAND || defined PROGRAM_HAND_WATER
#define HAND
#endif

#if !defined PROGRAM_BASIC && !defined PROGRAM_SKYBASIC && !defined PROGRAM_LIGHTNING
#define TEXTURED
#endif

// Things that move by themselves work out their own motion; the rest only claim the pixel (so composite1
// reprojects their depth) or, where they add light on top of what is there, leave it alone.
#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER || (defined ENTITY_FORMAT && !defined PROGRAM_SPIDEREYES) || defined COLORWHEEL
#define OWN_MOTION
#elif defined PROGRAM_SKYBASIC || defined PROGRAM_SKYTEXTURED || defined PROGRAM_WEATHER || defined PROGRAM_SPIDEREYES
#define NO_MOTION_CLAIM
#elif defined PROGRAM_LIGHTNING || defined PROGRAM_ARMOR_GLINT || defined PROGRAM_DAMAGEDBLOCK
#define NO_MOTION_CLAIM
#elif defined PROGRAM_BASIC
#define NO_MOTION_CLAIM
#endif

// Set by the Create Galore Bridge for each of Iris's shader keys (the vanilla shader a draw replaces): 1 known,
// 2 mob shading, 4 lightmap, 8 the lightmap filtered between levels (terrain's), 16 no texture, 32 drawn over
// something else (the motion under it stays). Without the Bridge it stays 0 and every program takes its usual case.
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
#if defined ENTITY_FORMAT && !defined PROGRAM_SPIDEREYES
    return KEY_MOB_SHADING | KEY_LIGHTMAP;
#elif defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    return KEY_LIGHTMAP | KEY_LIGHTMAP_SMOOTH;
#elif defined PROGRAM_PARTICLES || defined PROGRAM_WEATHER || defined PROGRAM_BASIC || defined PROGRAM_TEXTURED_LIT
    return KEY_LIGHTMAP;
#elif defined PROGRAM_CLOUDS
    return KEY_UNTEXTURED;
#else
    return 0;
#endif
}

// How each program's draws are lit:
// - LIT_GBUFFER: they leave their surface in the G-buffer for the deferred pass (terrain, mobs, block entities, the
//   hand, Create's machines).
// - LIT_FORWARD: drawn after the deferred pass, over the lit scene, and lit by their own program (water and glass,
//   particles, rain, clouds, Create's translucent parts).
// - LIT_GLOW: they only add light (glowing eyes, the enchantment glint, lightning, beacon beams).
// - LIT_SKY: Minecraft's sky, sun, moon and stars, which the deferred pass draws instead.
// - LIT_LINES: thin untextured things (the block outline, leads, debug lines), which Minecraft draws before the
//   deferred pass (the outline, leads) or after it: laid over the surface's colour in the G-buffer, where the
//   deferred pass lights them, and over the lit scene as they are; whichever comes too early is drawn over. (Lines
//   stay in gbuffers_basic: with a gbuffers_line of its own a pack has to widen lines itself, in the core profile.)
#if defined PROGRAM_SKYBASIC || defined PROGRAM_SKYTEXTURED
#define LIT_SKY
#elif defined PROGRAM_BASIC
#define LIT_LINES
#elif defined PROGRAM_WATER || defined PROGRAM_HAND_WATER || defined PROGRAM_WEATHER || defined PROGRAM_CLOUDS
#define LIT_FORWARD
#elif defined PROGRAM_PARTICLES || defined PROGRAM_TEXTURED || defined PROGRAM_TEXTURED_LIT
#define LIT_FORWARD
#elif defined PROGRAM_CLRWL_TRANSLUCENT
#define LIT_FORWARD
#elif defined PROGRAM_SPIDEREYES || defined PROGRAM_ARMOR_GLINT || defined PROGRAM_LIGHTNING || defined PROGRAM_BEACONBEAM
#define LIT_GLOW
#elif defined PROGRAM_CLRWL_GLINT || defined PROGRAM_CLRWL_ADDITIVE || defined PROGRAM_CLRWL_LIGHTNING
#define LIT_GLOW
#elif defined PROGRAM_DAMAGEDBLOCK || defined PROGRAM_CLRWL_DAMAGEDBLOCK
#define LIT_CRACKS
#else
#define LIT_GBUFFER
#endif

#endif

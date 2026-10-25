// The pack's options, as Iris shows them (shaders.properties lays out the screen, lang/ names them).

#if !defined LIB_SETTINGS
#define LIB_SETTINGS

// Upscaling: render the world smaller and let DLSS (or FSR) bring it up to the window's size. Off, they only
// anti-alias at full size (DLAA). The Create Galore Bridge reads both options from the pack
// (create_galore_dlss.properties) and sets the render size the programs see (taau_render_scale).
//#define UPSCALING
#define RENDER_SCALE 0.67 // [0.33 0.5 0.58 0.67 0.77 0.85]

#ifdef UPSCALING
const float upscaling_render_scale = RENDER_SCALE;
#endif

// Sky and light, in the overworld: a physically based sky with the sun, the moon and the stars, the world lit by
// them in real units (and by torches and every other light through Minecraft's light levels), tone mapped after
// DLSS. Off: Minecraft's own look, as in every other dimension (the End, the Nether and modded dimensions keep it
// until they get skies of their own).
#define LIGHTING
#define SHADOWS          // the sun's and the moon's shadows (a shadow map)
#define SHADOW_SOFTNESS 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
#define EXPOSURE 0.0     // [-2.0 -1.5 -1.0 -0.5 0.0 0.5 1.0 1.5 2.0]
#define NIGHT_LIGHT 1.0  // [0.25 0.5 1.0 1.5 2.0 3.0]

// What casts a shadow besides the world's blocks: mobs, block entities (chests, signs), you, and Create's machines
// and contraptions. shaders.properties and colorwheel.properties hand these to Iris and Colorwheel (Iris only lists
// an on/off option that some #ifdef names, hence the empty ones).
#define SHADOW_ENTITIES
#define SHADOW_BLOCK_ENTITIES
#define SHADOW_PLAYER
#define SHADOW_MACHINES
#ifdef SHADOW_ENTITIES
#endif
#ifdef SHADOW_BLOCK_ENTITIES
#endif
#ifdef SHADOW_PLAYER
#endif
#ifdef SHADOW_MACHINES
#endif

// This program draws the overworld with the pack's own sky and light (its copy in world0/, see world0.sh).
#ifdef WORLD_OVERWORLD
#ifdef LIGHTING
#define LIT_WORLD
#endif
#endif

#ifdef LIT_WORLD
// The sun's path leans towards the south, as it does away from the equator (Minecraft's own runs straight overhead).
const float sunPathRotation = -20.0; // [-40.0 -30.0 -20.0 -10.0 0.0 10.0 20.0 30.0 40.0]
#ifdef SHADOWS
const int shadowMapResolution = 2048; // [1024 2048 3072 4096 6144 8192]
const float shadowDistance = 128.0; // [64.0 96.0 128.0 160.0 192.0 256.0]
// The shadow camera follows you in steps of two blocks, so the shadows hold still as you move (no warping either:
// every texel covers the same ground everywhere).
const float shadowIntervalSize = 2.0;
const bool shadowHardwareFiltering = true;
#endif
#endif

#endif

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

// Coloured light, in the lit overworld with shadows on (the shadow pass finds the lights, see light_volume.glsl): each
// light's colour, read from its own texture, spread through the blocks around you, so soul lanterns light blue,
// redstone red and sea lanterns cool white, modded lights included. Strength: how much of a light's colour shows
// (0 every light is torchlight). Range: how many blocks across the volume the colours spread in is (half as high).
#define COLORED_LIGHT
#define COLORED_LIGHT_SATURATION 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define COLORED_LIGHT_RANGE 128      // [64 128 192 256]
#ifdef COLORED_LIGHT
#endif

// Water, in the lit overworld: small wind waves on every water surface (Waves: how high, 0 none), and ripples where
// you, mobs, boats, ships and Create's water wheels move through it, with wakes behind them (with shadows on: the
// shadow pass finds what reaches into the water, so what casts no shadow makes no ripples). Both only bend the
// light on the water (see water.glsl).
#define WATER_WAVES 1.0             // [0.0 0.5 1.0 1.5 2.0]
#define WATER_RIPPLES
#define WATER_RIPPLES_STRENGTH 1.0  // [0.5 1.0 1.5 2.0 3.0]
#ifdef WATER_RIPPLES
#endif

// Clouds, in the lit overworld: cumulus as a lit volume a kilometre and more up, and cirrus high above them, drifting
// with the wind (see clouds.glsl). They take over from Minecraft's clouds and cast shadows on the ground.
#define CLOUDS
#define CLOUD_QUALITY 3     // [1 2 3 4]
#define CLOUD_HEIGHT 1.0    // [0.3 0.5 0.75 1.0 1.25 1.5 2.0 3.0]
#define CLOUD_THICKNESS 2.0 // [0.5 1.0 1.5 2.0 2.5 3.0 4.0]
#define CLOUD_COVERAGE 0.5  // [0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0]
#define CLOUD_DENSITY 1.0   // [0.25 0.5 0.75 1.0 1.5 2.0 3.0]
#define CLOUD_WIND 1.0      // [0.0 0.5 1.0 1.5 2.0 3.0 4.0]
#define CIRRUS
#define CIRRUS_COVERAGE 0.4 // [0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0]
#define CLOUD_SHADOWS
#ifdef CLOUDS
#endif
#ifdef CIRRUS
#endif
#ifdef CLOUD_SHADOWS
#endif

// The End's own sky (its programs are in world1/): a nebula across a starry void, in place of Minecraft's (and
// BetterEnd's) sky, fading into the End's fog at the horizon. The End keeps Minecraft's own light for now. Off: its
// own sky too.
#define END_SKY
#define END_NEBULA 1.0 // [0.0 0.25 0.5 0.75 1.0 1.5 2.0]
#define END_STARS 1.0  // [0.0 0.5 1.0 1.5 2.0 3.0]
#ifdef END_SKY
#endif
#if defined WORLD_END && defined END_SKY
#define END_OWN_SKY
#endif

// This program draws the overworld with the pack's own sky and light (its copy in world0/, see dimensions.sh).
#ifdef WORLD_OVERWORLD
#ifdef LIGHTING
#define LIT_WORLD
#endif
#endif

#if defined LIT_WORLD && defined CLOUDS
#define LIT_CLOUDS
#endif

#if defined LIT_WORLD && defined SHADOWS && defined COLORED_LIGHT
#define LIT_COLORED_LIGHT
#endif

#if defined LIT_WORLD && defined SHADOWS && defined WATER_RIPPLES
#define LIT_WATER_RIPPLES
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

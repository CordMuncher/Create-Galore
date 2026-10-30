// The pack's options (Iris lists them on its screens from shaders.properties) and what follows from them.

#if !defined LIB_SETTINGS
#define LIB_SETTINGS

// The render resolution, DLSS, FSR and frame generation are set on the Create Galore Bridge's Upscaling & Display
// screen, not here (see create_galore_dlss.properties): the pack sizes everything from taau_render_scale.

// --- Sun shadows -----------------------------------------------------------------------------------------------------

const int shadowMapResolution = 2048; // [1024 2048 4096]
const float shadowDistance = 128.0; // [64.0 96.0 128.0 192.0 256.0]
const float shadowDistanceRenderMul = 1.0;
const float sunPathRotation = -20.0; // [-40.0 -30.0 -20.0 -10.0 0.0 10.0 20.0 30.0 40.0]
const bool shadowHardwareFiltering = true;
#define SHADOW_SOFTNESS 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
#define SHADOW_ENTITIES
#define SHADOW_BLOCK_ENTITIES
#define SHADOW_MACHINES // Create's machines and contraptions (see colorwheel.properties)
// What casts shadows is up to shaders.properties and colorwheel.properties; Iris lists a switch in the menus only when
// some #ifdef or #ifndef names it, hence these.
#ifdef SHADOW_ENTITIES
#endif
#ifdef SHADOW_BLOCK_ENTITIES
#endif
#ifdef SHADOW_MACHINES
#endif

// --- The voxel copy of the world -------------------------------------------------------------------------------------

// The box around you that the block light and the bounce light see: 0 is 96 x 64 x 96 blocks, 1 is 128 x 96 x 128,
// 2 is 256 x 128 x 256. Each detail level halves its width and doubles its detail, so the finest is near you.
#define VOXEL_BOX 1 // [0 1 2]
#define VOXEL_LEVELS 6 // [4 5 6]

// The sizes, and the work groups of 8 x 8 x 8 cells the passes over the box take, as plain numbers (Iris reads
// workGroups without working anything out).
#if VOXEL_BOX == 0
#define VOX_X 96
#define VOX_Y 64
#define VOX_GROUPS_X 12
#define VOX_GROUPS_Y 8
#elif VOXEL_BOX == 2
#define VOX_X 256
#define VOX_Y 128
#define VOX_GROUPS_X 32
#define VOX_GROUPS_Y 16
#else
#define VOX_X 128
#define VOX_Y 96
#define VOX_GROUPS_X 16
#define VOX_GROUPS_Y 12
#endif
// The distance field's passes cover every level, stacked up y.
#if VOXEL_BOX == 0 && VOXEL_LEVELS == 4
#define SDF_GROUPS_Y 32
#elif VOXEL_BOX == 0 && VOXEL_LEVELS == 5
#define SDF_GROUPS_Y 40
#elif VOXEL_BOX == 0
#define SDF_GROUPS_Y 48
#elif VOXEL_BOX == 2 && VOXEL_LEVELS == 4
#define SDF_GROUPS_Y 64
#elif VOXEL_BOX == 2 && VOXEL_LEVELS == 5
#define SDF_GROUPS_Y 80
#elif VOXEL_BOX == 2
#define SDF_GROUPS_Y 96
#elif VOXEL_LEVELS == 4
#define SDF_GROUPS_Y 48
#elif VOXEL_LEVELS == 5
#define SDF_GROUPS_Y 60
#else
#define SDF_GROUPS_Y 72
#endif
const ivec3 vox_size = ivec3(VOX_X, VOX_Y, VOX_X);
const ivec3 vox_tiles = vox_size / 8;

// --- Block light -----------------------------------------------------------------------------------------------------

// How far the strongest light (level 31) reaches, in blocks; a torch (level 20) reaches about two thirds as far.
#define BLOCK_LIGHT_REACH 30.0 // [15.0 20.0 25.0 30.0 40.0 50.0 70.0 100.0]
#define BLOCK_LIGHT_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define LIGHT_SATURATION 1.0 // [0.5 0.75 1.0 1.25 1.5]
#define PIXEL_LIGHT
// One traced light sample per this many pixels each way (it takes turns over the pixels it stands for).
#define PIXEL_LIGHT_SCALE 3 // [1 2 3 4 6]
#define PIXEL_LIGHT_TRACES 32 // [16 24 32 64 96]
#define HAND_LIGHT
#define LIGHT_SPECULAR

// --- Bounce light and occlusion --------------------------------------------------------------------------------------

#define GI
#define GI_RAYS 7 // [1 3 7 13]
#define GI_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define SSAO
#define AO_STRENGTH 1.0 // [0.0 0.5 0.75 1.0 1.25 1.5]

// --- Look ------------------------------------------------------------------------------------------------------------

// The exposure is fixed: the image never brightens in a cave or dims in the sun by itself. The light levels carry
// day, night and caves. This shifts it, in stops.
#define EXPOSURE 0.0 // [-2.0 -1.5 -1.0 -0.5 0.0 0.5 1.0 1.5 2.0]
#define BLOOM_STRENGTH 1.0 // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define LIGHT_FOG 1.0 // [0.0 0.5 0.75 1.0 1.5 2.0]
#define SATURATION 1.0 // [0.8 0.9 1.0 1.1 1.2 1.3]
#define NIGHT_BRIGHTNESS 1.0 // [0.5 0.75 1.0 1.5 2.0]
#define CAVE_BRIGHTNESS 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
// How fast Minecraft's smoothed sky light at the camera (eyeBrightnessSmooth: under a roof or out in the open)
// follows you, in seconds to halfway.
const float eyeBrightnessHalflife = 5.0;

// --- Debug -----------------------------------------------------------------------------------------------------------

// 0 off. 1 albedo, 2 normals, 3 sun shadow, 4 block light (per block), 5 block light (per pixel), 6 bounce light,
// 7 the voxel copy (what a ray from the camera hits first), 8 its distance field, 9 ambient occlusion, 14 the light
// layers side by side (see deferred_light.fsh).
#define DEBUG_VIEW 0 // [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14]
// A small square in the top left corner for about two seconds whenever a pass caught a value that is infinite or not
// a number (see lib/bad_frame.glsl): its colour says where.
#define BAD_FRAME_MARK
// DLSS and FSR get the surface behind a particle (its depth and motion) instead of the particle's own (see
// program/dlss.fsh): off to compare, for the rising torch smoke that looked like a piece of glass.
#define PARTICLES_SHOW_BACKGROUND

// --- What follows ----------------------------------------------------------------------------------------------------

#if defined WORLD_NETHER || defined WORLD_END
#define NO_SKY_LIGHT
#endif
#if !defined NO_SKY_LIGHT
#define SUNLIGHT
#endif

#endif

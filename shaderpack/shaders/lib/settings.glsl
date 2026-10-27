// The pack's options, as Iris shows them (shaders.properties lays out the screen, lang/ names them).

#if !defined LIB_SETTINGS
#define LIB_SETTINGS

// Upscaling: render the world smaller and let DLSS (or FSR) bring it up to the window's size. Off, they only
// anti-alias at full size (DLAA). The Create Galore Bridge reads both options from the pack
// (create_galore_dlss.properties) and sets the render size the programs see (taau_render_scale).
//#define UPSCALING
#define RENDER_SCALE 0.67 // [0.33 0.5 0.58 0.67 0.77 0.85]
// The noise what samples a little each frame steps by (soft shadows, ambient occlusion, reflections, the water's
// light), which the upscaler averages out over frames: 0 a smooth pattern made for TAA, 1 white noise (see
// pixel_noise in lighting.glsl). Which one DLSS resolves more cleanly is for the eye to judge.
#define NOISE_PATTERN 0 // [0 1]

#ifdef UPSCALING
const float upscaling_render_scale = RENDER_SCALE;
#endif

// Sky and light, in the overworld: a physically based sky with the sun, the moon and the stars, the world lit by
// them in real units (and by torches and every other light through Minecraft's light levels), tone mapped after
// DLSS. Off: Minecraft's own look, as in every other dimension (the End, the Nether and modded dimensions keep it
// until they get skies of their own).
#define LIGHTING
#define SHADOWS          // the sun's and the moon's shadows (a shadow map)
// How soft shadows grow away from what casts them (0: as the sun's own, sharp but for the shadow map's texels), and
// how many points of the shadow map each one takes (smoother soft edges).
#define SHADOW_SOFTNESS 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
#define SHADOW_QUALITY 3    // [1 2 3 4]
// Screen-space sun shadows: what the shadow map is too coarse for (grass, feet, a fence's rails, a block's edge up
// close) still shadows what lies behind it, followed through the screen's depth a pixel at a time towards the sun
// (see screen_shadow in deferred.fsh). (Iris only lists an on/off option that some #ifdef names.)
#define CONTACT_SHADOWS
#ifdef CONTACT_SHADOWS
#endif
#define EXPOSURE 0.0     // [-2.0 -1.5 -1.0 -0.5 0.0 0.5 1.0 1.5 2.0]
#define NIGHT_LIGHT 1.0  // [0.25 0.5 1.0 1.5 2.0 3.0]
// The moon as drawn, times its real size (see moon_disc in sky.glsl), and the Milky Way across the sky on dark nights.
#define MOON_SIZE 3.0    // [1.0 2.0 3.0 4.0 5.0]
#define MILKY_WAY
#ifdef MILKY_WAY
#endif
// How far colours go over to the rods' cool, colourless view in the dark, as eyes do (see night_vision.glsl): 0 not
// at all, 1 as the eye's own.
#define NIGHT_VISION 1.0 // [0.0 0.25 0.5 0.75 1.0]
// How much a bright part of the frame (a window from inside a room, the sky over dark land) is drawn in towards the
// rest, keeping its detail (see local_exposure.glsl): 0 not at all.
#define LOCAL_EXPOSURE 1.0 // [0.0 0.25 0.5 0.75 1.0]
// Colour (see grade.glsl), nothing changed at the defaults. The white balance: the colour temperature (K) of a light
// shown as white (higher is warmer), and a tint (green to magenta, 0 none).
#define WB_TEMPERATURE 6500 // [3000 3500 4000 4500 5000 5500 6000 6500 7000 7500 8000 9000 10000]
#define WB_TINT 0 // [-50 -45 -40 -35 -30 -25 -20 -15 -10 -5 0 5 10 15 20 25 30 35 40 45 50]
// Each primary's hue (turned up to 25 degrees about the white) and saturation (percent more or less).
#define PRIMARY_RED_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define PRIMARY_RED_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define PRIMARY_GREEN_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define PRIMARY_GREEN_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define PRIMARY_BLUE_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define PRIMARY_BLUE_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
// The mixer: hue, saturation and lightness by eight bands of hue (off until switched on).
//#define COLOR_MIXER
#ifdef COLOR_MIXER
#endif
#define MIX_RED_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_RED_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_RED_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_ORANGE_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_ORANGE_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_ORANGE_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_YELLOW_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_YELLOW_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_YELLOW_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_GREEN_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_GREEN_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_GREEN_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_AQUA_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_AQUA_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_AQUA_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_BLUE_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_BLUE_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_BLUE_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_PURPLE_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_PURPLE_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_PURPLE_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_MAGENTA_HUE 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_MAGENTA_SAT 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
#define MIX_MAGENTA_LUM 0 // [-100 -90 -80 -70 -60 -50 -40 -30 -20 -10 0 10 20 30 40 50 60 70 80 90 100]
// Depth of field, as a camera's lens has it (see dof.glsl; off until switched on): what lies nearer or further than
// the focus blurs, the more the wider the lens's aperture (its focal length (mm) over its f-number). The focus is
// what the middle of the view shows (0), or a distance (blocks).
//#define DEPTH_OF_FIELD
#ifdef DEPTH_OF_FIELD
#endif
#define DOF_FOCUS 0 // [0 1 2 3 4 5 6 8 10 12 16 20 24 32 48 64 96 128]
#define DOF_FOCAL_LENGTH 50 // [24 35 50 85 135]
#define DOF_F_STOP 1.4 // [1.0 1.4 2.0 2.8 4.0 5.6 8.0 11.0 16.0]
// How many points each pixel's blur takes: 1 fewer (faster), 3 more (smoother, larger blurs).
#define DOF_QUALITY 2 // [1 2 3]
// How fast the sky's light fades as a place sees less of the sky (into rooms, caves, forests): its sky light level's
// brightness to this power. 1: as Minecraft's sky light, which lights a room with a window almost as well as the
// shade outside; higher leaves more of a room to the light bouncing in through its window.
#define SKY_FALLOFF 1.0  // [1.0 1.5 2.0 3.0]
// How fast the blocks' light fades away from a light: its block light level's brightness to this power. 1: as
// Minecraft's, where one torch lights a room almost evenly; higher gathers the light into pools around each light.
#define BLOCK_FALLOFF 1.5 // [1.0 1.5 2.0 2.5 3.0]
// How much a lamp's light takes the shape a real lamp's does (with Coloured Light): falling off steeply close to it,
// so each lamp lights a pool around it, and lighting most what faces it. 0: as Minecraft's, the same on every face.
#define LAMP_SHAPE 1.0   // [0.0 0.5 1.0]

// Blocks that give light glow where their texture is bright (a torch's flame, a lantern's light, lava): how bright.
#define EMISSION 1.0     // [0.0 0.5 1.0 1.5 2.0 3.0 4.0]
// How much the blocks' light shows by day: 0 as bright as it is (a torch is lost in daylight, as in life), higher
// lets a lantern light the shade under a roof by day as it lights a room at night.
#define BLOCK_LIGHT_DAY 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]

// The glow around what is much brighter than what surrounds it (bloom): how much of it.
#define BLOOM 1.0        // [0.0 0.5 1.0 1.5 2.0 3.0]

// Ambient occlusion: the light all around (the sky's, the bounce light, the blocks') is less in creases, in corners
// and under things, as the screen's depth shows them (a visibility bitmask over GTAO's slices, see screen_light.fsh),
// on top of Minecraft's smooth lighting corners. More directions and steps for a steadier result. 0: Minecraft's
// corners only.
#define AO_QUALITY 2     // [0 1 2 3]
// Screen Bounce Light: what blocks the light all around a surface (in the ambient occlusion's reach) bounces its own
// light onto it instead, from last frame's picture: colour from the surfaces right next to it, finer than the bounce
// light's blocks. Needs Ambient Occlusion.
#define SCREEN_BOUNCE
#ifdef SCREEN_BOUNCE
#endif

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
// (0 every light is torchlight).
#define COLORED_LIGHT
#define COLORED_LIGHT_SATURATION 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
// How the colours look: 0 as lights of those colours would (a blue light looks dim next to a warm one as strong, as
// the eye sees blue), 1 stylized: every colour about as bright to the eye as torchlight, and deeper.
#define LIGHT_STYLE 0.75 // [0.0 0.25 0.5 0.75 1.0]
// How many blocks across (and half as high) the volume around you is that the coloured light and the bounce light
// spread through; past it lights are torchlight and nothing bounces.
#define LIGHT_RANGE 128 // [64 128 192 256]
#ifdef COLORED_LIGHT
#endif

// Bounce light (global illumination), in the lit overworld with shadows on: sunlight (and moonlight) that falls on
// the world and bounces off it, in the colour of what it bounced off, lighting rooms from their sunlit windows and
// floors, and the shade under trees and overhangs. Steady from frame to frame (no noise for the upscaler to smear):
// it spreads through a volume of blocks around you (see light_volume.glsl). Where the open sky lights a surface,
// the sky's light already holds the ground's bounce, so it adds little there.
#define GI
#define GI_STRENGTH 1.0 // [0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#ifdef GI
#endif

// Materials from the resource pack, in the lit overworld: a PBR resource pack's maps (LabPBR, as SPBR and most PBR
// packs make them, see material.glsl) give each pixel of a texture its own facing, how smooth it is and what it
// reflects (metals in their own colour), what glows, and its own crevices. Blocks without maps keep the pack's own
// materials (see block.properties). Parallax: the height in the normal maps gives the surface depth, a texture pixel
// at a time, with the sun's shadows inside it, out to Parallax Distance blocks; Parallax Depth: times how deep.
#define MATERIALS
#define PARALLAX
#define PARALLAX_DEPTH 1.0   // [0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define PARALLAX_DISTANCE 32 // [16 24 32 48 64]
#ifdef MATERIALS
#endif
#ifdef PARALLAX
#endif

// For tuning, not in the settings screens (set it in the pack's .txt): 1 shows only the bounce light, as it falls on
// a white surface; 2 only the ambient occlusion (white open, black shut); 3 how deep under water each point is and
// the caustics there; 4 why the shadow map does or doesn't find water over a point; 5 the leaves' crown shape; 6 how
// much of the sky each surface sees; 7 only the light the screen bounces, as it falls on a white surface (see
// deferred.fsh); 8 on the water, how much it mirrors (red, twice its Fresnel) and where the screen gives what it
// mirrors (green).
#define DEBUG_VIEW 0 // [0 1 2 3 4 5 6 7 8]

// Water, in the lit overworld: small wind waves on every water surface (Waves: how high, 0 none), and ripples where
// you, mobs, boats, ships and Create's water wheels move through it, with wakes behind them (with shadows on: the
// shadow pass finds what reaches into the water, so what casts no shadow makes no ripples). Both only bend the
// light on the water (see water.glsl).
#define WATER_WAVES 1.0             // [0.0 0.5 1.0 1.5 2.0]
// Reflections of the world on water (and puddles), from what the screen shows (see reflection.glsl): how far and how
// finely a reflected ray looks. 0: the sky only.
#define REFLECTION_QUALITY 2        // [0 1 2 3]
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

// Light in the air, in the lit overworld (see air.glsl): the haze the sun lights into shafts through the leaves and
// past the clouds' edges, dust indoors that a sunbeam shows up, and a glow around lamps in their own colours; far
// terrain fades into the same air as the sky. Quality: how fine the volume it's worked out in is (0: Minecraft's
// fog alone). Dust: how much the air in a room or a cave holds. Lamp Glow: how much the air around lamps glows.
#define AIR_QUALITY 3 // [0 1 2 3 4]
#define AIR_DUST 1.0  // [0.0 0.5 1.0 1.5 2.0 3.0]
#define LAMP_GLOW 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]

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

#if defined LIT_WORLD && defined SHADOWS && defined GI
#define LIT_GI
#endif

#if defined LIT_WORLD && AIR_QUALITY > 0
#define LIT_AIR
#endif

#if defined LIT_WORLD && defined MATERIALS
#define LIT_MATERIALS
#ifdef PARALLAX
#define LIT_PARALLAX
#endif
#endif

// The volume of blocks around you that the coloured light and the bounce light spread through.
#if defined LIT_COLORED_LIGHT || defined LIT_GI
#define LIT_VOXELS
#endif

// How much of the sky each point sees past the blocks around it (see sky_grid.glsl), from more rays the higher the
// ambient occlusion's quality.
#if defined LIT_WORLD && defined SHADOWS
#define LIT_SKY_GRID
#define LIT_SHORE // the shore around the water near you (see shore.glsl)
#if AO_QUALITY >= 3
#define SKY_RAYS 8
#elif AO_QUALITY == 2
#define SKY_RAYS 6
#elif AO_QUALITY == 1
#define SKY_RAYS 4
#else
#define SKY_RAYS 3
#endif
#endif

#ifdef LIT_WORLD
// The sun's path leans towards the south, as it does away from the equator (Minecraft's own runs straight overhead).
const float sunPathRotation = -20.0; // [-40.0 -30.0 -20.0 -10.0 0.0 10.0 20.0 30.0 40.0]
#ifdef SHADOWS
const int shadowMapResolution = 2048; // [1024 2048 3072 4096 6144 8192]
const float shadowDistance = 128.0; // [64.0 96.0 128.0 160.0 192.0 256.0]
// The shadow camera follows you in steps of two blocks, and the map's texels stay on a grid fixed in the world between
// them (see shadow_grid.glsl), so the shadows hold still as you move (no warping either: every texel covers the same
// ground everywhere).
const float shadowIntervalSize = 2.0;
// Hardware depth compares on shadowtex1 (what is opaque) as shadowtex1HW, for the shadows (see shadow.glsl); shadowtex1
// itself stays plain depth for their search and the bounce light's source (light_bounce.csh), and shadowtex0 has the
// water in it too.
const bool shadowHardwareFiltering1 = true;
// The colour (sRGB-ish: the square root of linear) and the facing (octahedral, rg) of what the light falls on, for the
// bounce light (see shadow.fsh), and in shadowcolor1's b where water lies over it; cleared to nothing every frame.
/*
const int shadowcolor0Format = RGBA8;
const int shadowcolor1Format = RGBA8;
const bool shadowcolor0Clear = true;
const vec4 shadowcolor0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool shadowcolor1Clear = true;
const vec4 shadowcolor1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
#endif
#endif

#endif

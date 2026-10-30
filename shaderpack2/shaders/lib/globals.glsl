// Values that one pass works out for the whole frame and every later pass reads (program/frame.csh), a texel each
// of a small image (cg_globals_img), read through its sampler. frame.csh itself (WRITE_GLOBALS) works them out in
// shared memory and stores them at the end.

#if !defined LIB_GLOBALS
#define LIB_GLOBALS

#define GLOBALS_COUNT 8

#if defined WRITE_GLOBALS
shared vec4 g_globals[GLOBALS_COUNT];
#define GLOBAL(i) g_globals[i]
#else
uniform sampler2D cg_globals_tex;
#define GLOBAL(i) texelFetch(cg_globals_tex, ivec2(i, 0), 0)
#endif

#define g_light_dir GLOBAL(0)   // xyz towards the sun or the moon, whichever lights the world (world space); w 1 sun, 0 moon
#define g_light_color GLOBAL(1) // rgb its light on a surface facing it, through the air at the camera
#define g_sky_up GLOBAL(2)      // rgb the sky's light on a surface facing up, out in the open
#define g_sky_side GLOBAL(3)    // rgb on a wall facing the horizon
#define g_sky_down GLOBAL(4)    // rgb on a surface facing down (the sky's share only)
#define g_horizon GLOBAL(5)     // rgb the Nether's and the End's haze (the Overworld's is the sky behind it)
#define g_sun_dir GLOBAL(6)     // xyz towards the sun itself (world space), w how far above the horizon (sin)
#define g_moon_dir GLOBAL(7)    // xyz towards the moon, w the moon's phase brightness (0 new .. 1 full)

#endif

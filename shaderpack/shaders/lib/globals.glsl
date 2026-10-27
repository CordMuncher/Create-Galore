// What the overworld's programs share each frame (row 0 of a 4096 by 35 image, see shaders.properties; the rows
// above hold the local exposure's grid, see local_exposure.glsl): the sun's and the moon's light where you are and
// the sky's light all around, worked out by the begin passes before anything is drawn, and the exposure, which the
// metering pass (exposure.csh, after DLSS) moves for the next frame. An image
// rather than a storage buffer: Iris turns the whole pack off if the video memory it is told is free (none, at
// times, when it is full or on some drivers) is less than a storage buffer asks for.
//
// Light is in real units: radiance in kcd/m² (1000 nits), illuminance in klux. The frame holds it times the
// exposure, so the sun at noon and a torch at midnight both fit half floats; the final pass tone maps it.

#if !defined LIB_GLOBALS
#define LIB_GLOBALS

#define G_SUN_DIR 0      // xyz towards the sun, world space; w how far it is above the horizon (sin)
#define G_MOON_DIR 1     // the same for the moon
#define G_SUN_LIGHT 2    // rgb the sun's illuminance on a surface facing it, through the air down to you
#define G_MOON_LIGHT 3   // rgb the same for the moon (its phase included)
#define G_LIGHT_DIR 4    // the light that casts the shadows (the sun by day, the moon by night), world space
#define G_LIGHT_COLOR 5  // rgb its illuminance, as G_SUN_LIGHT or G_MOON_LIGHT
#define G_SKY_CUBE 6     // 6 to 11: rgb the sky's (and the ground's) light on a surface facing +x, -x, +y, -y, +z, -z
#define G_EXPOSURE 12    // x the exposure (frame values per kcd/m²), y its log2, z the metered log2 luminance
#define G_CLOUD_PLACE 13 // xy where the cumulus' patterns are under you (km, modulo 128), zw the cirrus' (modulo 4096)
#define G_CLOUD_DETAIL 14 // xyz how far the wind has carried the cumulus' billows through their bodies (km)
#define G_FLASH 15       // rgb the radiance a lightning bolt lights the sky with this frame (kcd/m²)
#define G_WATER 16       // the ripples (see water.glsl): x steps this frame, y steps before it (modulo 2), z the clock
#define G_WATER_ORIGIN 17 // xy the ripples' grid corner after this frame's steps (cells, modulo 2^20), zw before them
#define G_FOCUS 18       // the depth of field's focus (see dof.csh): x its inverse distance (1/m), y 1 once worked out
#define G_CUT 19         // x 1 on a camera cut (see frame_globals.csh), y frames since the last one

#ifdef GLOBALS_WRITE
// The passes that work them out (frame_globals.csh, exposure.csh).
layout(rgba32f) uniform image2D cg_globals_img;
#define global(i) imageLoad(cg_globals_img, ivec2(i, 0))
void set_global(int i, vec4 value) {
    imageStore(cg_globals_img, ivec2(i, 0), value);
}
#elif defined READ_IMAGES
// (Read as an image where a pass is short of texture units, see marks.glsl.)
layout(rgba32f) readonly uniform image2D cg_globals_img;
#define global(i) imageLoad(cg_globals_img, ivec2(i, 0))
#else
uniform sampler2D cg_globals_tex;
#define global(i) texelFetch(cg_globals_tex, ivec2(i, 0), 0)
#endif

#define g_sun_dir global(G_SUN_DIR)
#define g_moon_dir global(G_MOON_DIR)
#define g_sun_light global(G_SUN_LIGHT)
#define g_moon_light global(G_MOON_LIGHT)
#define g_light_dir global(G_LIGHT_DIR)
#define g_light_color global(G_LIGHT_COLOR)
#define g_exposure_data global(G_EXPOSURE)

float exposure() {
    return g_exposure_data.x;
}

// Whether the exposure has been worked out yet (the image starts empty when the pack loads).
bool exposure_ready(vec4 data) {
    return data.x > 0.0 && data.y >= -8.0 && data.y <= 13.0;
}

// Frames since the camera last cut to somewhere else (0 on the cut itself): what builds up over frames starts afresh
// then rather than blending in what the last place looked like.
float frames_since_cut() {
    return global(G_CUT).y;
}

#endif

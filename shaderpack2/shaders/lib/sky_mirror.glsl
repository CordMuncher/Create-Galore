// The sky as water mirrors it: the sky with a quick look at its clouds from the water's own point (lib/clouds.glsl
// clouds_simple, so they hold still on it as you climb or drop), the same for the traced reflections
// (lib/reflect.glsl), the water's own at Low (program/gbuffers.fsh) and Distant Horizons' water (program/dh.fsh), so
// the far sea mirrors the clouds as the near one does. The sun's own disc is each water pass's glint.
//
// A program that doesn't read the clouds back (no cg_cloud_img) defines CLOUDS_NO_READ_BACK before including this.

#if !defined LIB_SKY_MIRROR
#define LIB_SKY_MIRROR

#include "/lib/settings.glsl"
#include "/lib/sky.glsl"
#ifdef VOLUME_CLOUDS
#include "/lib/clouds.glsl"
#endif

// The sky and its clouds along r, as a point (scene space) mirrors them.
vec3 reflect_sky(vec3 r, vec3 from) {
    vec3 c = sky(r, false);
#ifdef VOLUME_CLOUDS
    vec4 clouds = clouds_simple(r, from);
    c = c * clouds.a + clouds.rgb;
#endif
    return c;
}

#endif

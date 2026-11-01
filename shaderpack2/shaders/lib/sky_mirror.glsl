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

// The sky and its clouds along r, as a point (scene space) mirrors them. spread: how far (radians) the waves too fine
// to see spread the reflection; past a little, the clouds are looked at three times round r within it, turned by
// turn (0..1, a pixel's own, changing from frame to frame), so a rough or far sea mirrors them soft instead of as
// sharp blobs. (The sky itself is smooth enough as it is.)
vec3 reflect_sky(vec3 r, vec3 from, float spread, float turn) {
    vec3 c = sky(r, false);
#ifdef VOLUME_CLOUDS
    vec4 clouds;
    float s = min(0.5 * spread, 0.3);
    if (s < 0.01) {
        clouds = clouds_simple(r, from);
    } else {
        vec3 t;
        vec3 b;
        basis(r, t, b);
        clouds = vec4(0.0);
        for (int k = 0; k < 3; k++) {
            float a = TAU * (turn + float(k) / 3.0);
            vec3 d = normalize(r + (t * cos(a) + b * sin(a)) * s);
            clouds += clouds_simple(d, from);
        }
        clouds /= 3.0;
    }
    c = c * clouds.a + clouds.rgb;
#endif
    return c;
}

#endif

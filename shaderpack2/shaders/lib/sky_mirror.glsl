// The sky as water mirrors it: the sky itself along the mirrored look, and the clouds from a map of them worked out
// a little each frame (program/clouds.csh): the cloud layer as it looks from sea level under the camera, in every
// direction above the horizon (256 round, 96 up, finer towards the horizon where most water mirrors), and a soft
// copy for rough and far water. A mirrored look is followed from its own spot on the water up to the clouds' height
// and the map read towards where it gets there, so each stretch of water mirrors the clouds over it, they hold still
// on it as you climb or drop, and nothing in it flickers: no look of its own through the clouds per pixel. The same
// for the traced reflections (lib/reflect.glsl), the water's own at Low (program/gbuffers.fsh) and Distant Horizons'
// water (program/dh.fsh). The sun's own disc is each water pass's glint.

#if !defined LIB_SKY_MIRROR
#define LIB_SKY_MIRROR

#include "/lib/settings.glsl"
#include "/lib/sky.glsl"

#if defined VOLUME_CLOUDS && defined SUNLIGHT

const float mirror_sea = 63.0;    // the height the map is worked out from
const float mirror_clouds = 30.0; // how far over the layer's height most of the clouds are, for the mirrored looks

// A direction's place in the map (texels): round from +x, and up as the square root of the elevation.
vec2 mirror_place(vec3 dir, ivec2 size) {
    float az = atan(dir.z, dir.x) / TAU;
    float up = sqrt(asin(clamp(dir.y, 0.0, 1.0)) / (0.5 * PI));
    return vec2(fract(az) * float(size.x), up * float(size.y)) - 0.5;
}

// The direction of a texel of the map.
vec3 mirror_dir(ivec2 texel, ivec2 size) {
    float az = (float(texel.x) + 0.5) / float(size.x) * TAU;
    float e = sqr((float(texel.y) + 0.5) / float(size.y)) * 0.5 * PI;
    return vec3(cos(e) * cos(az), sin(e), cos(e) * sin(az));
}

// The map between its texels (it wraps round; a texel never worked out yet lets everything through).
vec4 mirror_read(vec2 at, ivec2 origin, ivec2 size) {
    ivec2 base = ivec2(floor(at));
    vec2 f = at - vec2(base);
    vec4 sum = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 q = base + o;
        q = ivec2(q.x & (size.x - 1), clamp(q.y, 0, size.y - 1));
        vec4 v = texelFetch(cg_sky_tex, origin + q, 0);
        v = v.a >= 0.0 && v.a <= 1.0 ? v : vec4(0.0, 0.0, 0.0, 1.0);
        sum += v * ((o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y));
    }
    return sum;
}

// The clouds a point (scene space) mirrors along r: their light (rgb, premultiplied) and what gets through them (a).
// spread: how far (radians) the waves too fine to see spread the reflection.
vec4 mirror_clouds_at(vec3 r, vec3 from, float spread) {
    if (!clouds_here() || r.y <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    // Where the look gets to the clouds' height, from the point the map was worked out at.
    vec3 p = vec3(from.x, from.y + cameraPosition.y - mirror_sea, from.z);
    float h = cloud_layer() + mirror_clouds - mirror_sea;
    vec3 d = r;
    if (p.y < h) {
        d = normalize(p + r * ((h - p.y) / max(r.y, 1e-3)));
    }
    vec4 sharp = mirror_read(mirror_place(d, mirror_size), mirror_origin, mirror_size);
    float soft_share = smoothstep(0.03, 0.25, spread);
    if (soft_share <= 0.0) {
        return sharp;
    }
    vec4 soft = mirror_read(mirror_place(d, mirror_soft_size), mirror_soft_origin, mirror_soft_size);
    return mix(sharp, soft, soft_share);
}

#endif

// The sky and its clouds along r, as a point (scene space) mirrors them. spread: how far (radians) the waves too fine
// to see spread the reflection: the clouds go soft with it. (The sky itself is smooth enough as it is.)
vec3 reflect_sky(vec3 r, vec3 from, float spread) {
    vec3 c = sky(r, false);
#if defined VOLUME_CLOUDS && defined SUNLIGHT
    vec4 clouds = mirror_clouds_at(r, from, spread);
    c = c * clouds.a + clouds.rgb;
#endif
    return c;
}

#endif

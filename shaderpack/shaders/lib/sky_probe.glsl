// How much of the sky a point sees, wherever the sky grid (see sky_grid.glsl) doesn't reach: more than 128 blocks off,
// and Distant Horizons' terrain. Minecraft's sky light can't tell a cave from the shade of a floating island, but the
// screen can see whether the points 8, 16, 32 and 64 blocks straight above a point are open air: nothing the camera
// sees lies in front of them. Under a cover at least that high, as far across as Minecraft's sky light says it reaches
// past the point (each level less is a block further in), the point sees the sky a disc that wide that high leaves
// open: H² / (H² + R²) of it (the light from the sky's directions as it falls on the ground). A cave's roof hides the
// first point, so caves stay dark; past the screen's edge a point counts as hidden.
//
// The program says what to read before including this: SKY_PROBE_DEPTH, the depth of what is opaque, and with
// Distant Horizons SKY_PROBE_DH_DEPTH, its terrain's (and declares gbufferModelView, gbufferProjection,
// gbufferProjectionInverse, and dhProjectionInverse with Distant Horizons).

#if !defined LIB_SKY_PROBE
#define LIB_SKY_PROBE

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/sky_grid.glsl"

// Whether the camera sees the point q (scene space) as open air: on the screen, with nothing it sees in front of it.
bool sky_probe_open(vec3 q) {
    vec3 view = mat3(gbufferModelView) * q + gbufferModelView[3].xyz;
    if (view.z > -0.1) {
        return false;
    }
    vec4 clip = gbufferProjection * vec4(view, 1.0);
    vec2 ndc = clip.xy / clip.w;
    if (any(greaterThan(abs(ndc), vec2(1.0)))) {
        return false;
    }
    ivec2 texel = ivec2((ndc * 0.5 + 0.5) * render_size());
    float nearest = 1e9; // how far in front of the camera the surface the screen shows there lies
    float depth = texelFetch(SKY_PROBE_DEPTH, texel, 0).r;
    if (depth < 1.0) {
        vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        nearest = -v.z / v.w;
    }
#ifdef DISTANT_HORIZONS
    float dh_depth = texelFetch(SKY_PROBE_DH_DEPTH, texel, 0).r;
    if (dh_depth < 1.0) {
        vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
        nearest = min(nearest, -v.z / v.w);
    }
#endif
    return nearest > -view.z + 1.0;
}

// The share of the sky a point (scene space) with sky light sky (0..1) sees, from how high the open air over it
// reaches on the screen. 0 where it can't tell (the point right over it is hidden).
float sky_seen_probe(vec3 scene_pos, float sky) {
    float open_to = 0.0;
    for (int i = 0; i < 4; i++) {
        float h = 8.0 * float(1 << i);
        if (!sky_probe_open(scene_pos + vec3(0.0, h, 0.0))) {
            break;
        }
        open_to = h;
    }
    // At sky light 0 the cover reaches at least 15 blocks past the point; take 24.
    float across = sky > 0.0 ? 15.0 * (1.0 - sky) + 0.5 : 24.0;
    return open_to * open_to / (open_to * open_to + across * across);
}

// The share of the sky a surface at scene_pos facing n with sky light sky sees: from the sky grid near you, and from
// the screen past it (and on Distant Horizons' terrain, lod), the one fading into the other over the grid's last
// 28 blocks. noise: 0..1, a different one each pixel and frame.
float sky_seen_around(vec3 scene_pos, vec3 n, float sky, float noise, bool lod) {
    float grid = 0.0;
    float seen = 0.0;
#ifdef LIT_SKY_GRID
    if (!lod) {
        grid = 1.0 - smoothstep(96.0, 124.0, max(abs(scene_pos.x), abs(scene_pos.z)));
        if (grid > 0.0) {
            seen = sky_seen(scene_pos, n, noise);
            if (seen < 0.0) {
                grid = 0.0; // past the grid's top or bottom, or facing down
            }
        }
    }
#endif
    if (grid < 1.0) {
        seen = mix(sky_seen_probe(scene_pos, sky), seen, grid);
    }
    return seen;
}

#endif

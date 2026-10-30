// The Overworld's clouds: one layer of heaped cumulus round Minecraft's cloud height, 48 blocks deep, drawn as a
// volume (program/clouds.csh) instead of Minecraft's flat blocks. What they are, and how the frame reads them back.
// - Shape: clumps from a noise volume (textures/cloud_noise.dat, see tools/cloud_noise.py), flat underneath and
//   rounded on top, their edges worn by a finer noise into round billows on top and wisps underneath; how much of the
//   sky they cover changes from place to place over a kilometre or so, and grows in rain until the sky closes up. The
//   clumps' noise is read twice, the second time turned and a little smaller, so together they never line up in rows
//   and only repeat every 1024 blocks, and which slice of it the clouds take drifts slowly from place to place.
// - Light: the sun's (or the moon's), dimmed by the cloud between a point and the light (three looks towards it, the
//   nearest through the billows, so their crevices shade) and scattered mostly forwards, so a cloud's edge round the
//   sun shines silver and the sun burns through thin parts; the sky's from above, so undersides go grey-blue; through
//   dusk and dawn the low sky's glow. Mixed like any surface's light (lib/lighting.glsl), then fading into the sky's
//   own colour with distance.
// - Wind: the clumps drift north at about 4.5 blocks a second, the billows on them a little slower and slowly rising,
//   so their edges churn as they go. Each noise moves a whole number of its repeats in the hour the clock runs, so the
//   clock going back to 0 on the hour makes no jump.
// - Reach: 5000 blocks, thinning out past 1500.
// They are worked out at half the render size each way and kept over frames (cg_cloud_img): this frame's half and
// last frame's side by side, each with its light (rgb, premultiplied) and what still gets through (a) above, and the
// distances (where the cloud starts, where most of it is, how far the look went) below.

#if !defined LIB_CLOUDS
#define LIB_CLOUDS

#include "/lib/sky.glsl"

uniform sampler3D cg_cloud_noise; // see tools/cloud_noise.py
uniform float cloudHeight;        // Minecraft's cloud height for this world (an Iris uniform; not a number without clouds)
uniform float rainStrength;

const float cloud_reach = 5000.0;
const float cloud_far_sky = 60000.0; // a look that went out to the sky, in the distances
const float cloud_depth = 48.0;

// Minecraft gives a world without clouds (the Nether, the End, some modded ones) no number for their height.
bool clouds_here() {
    return cloudHeight > -4096.0 && cloudHeight < 4096.0;
}

float cloud_bottom() {
    return cloudHeight - 12.0;
}

// Where the noise sits: positions are taken within 8192 blocks (every noise repeats evenly into it) so they stay
// exact far from the world's origin.
vec3 cloud_origin() {
    return vec3(cameraPositionInt & 8191) + cameraPositionFract;
}

// How far each noise repeats (blocks), and how fast the wind carries it (blocks a second, along -z): the clumps 16384
// blocks an hour, the billows 256 blocks an hour less (and 64 blocks up).
const float cloud_body_tile = 256.0;
const float cloud_detail_tile = 64.0;
const float cloud_wind_speed = 8192.0 * 2.0 / 3600.0;
const float cloud_detail_speed = (8192.0 * 2.0 - 256.0) / 3600.0;
const float cloud_rise_speed = 64.0 / 3600.0;
vec3 cloud_wind() {
    return vec3(0.0, 0.0, -1.0) * cloud_wind_speed;
}

// How much of the sky the clouds cover here (x, 0..1), and how far the clumps' noise is shifted up here (y, blocks:
// slowly, since the texture's blending between texels is only so fine and any steep use of it shows those steps).
vec2 cloud_map(vec2 xz) {
    vec2 at = xz + vec2(0.0, frameTimeCounter * cloud_wind_speed);
    float a = textureLod(cg_cloud_noise, vec3(at / 8192.0, 0.37).xzy, 0.0).a;
    float b = textureLod(cg_cloud_noise, vec3((at + vec2(1100.0, 0.0)) / 2048.0, 0.71).xzy, 0.0).a;
    float cover = 0.42 * CLOUD_AMOUNT + (a - 0.5) * 0.55 + (b - 0.5) * 0.2 + 0.6 * rainStrength;
    return vec2(clamp(cover, 0.0, 1.0), a * 160.0);
}

// The clumps' noise at q (0..1): read as it is and again turned about 37 degrees and a fifth smaller (each moves and
// wraps a whole number of repeats with the wind and with cloud_origin()), the two averaged and spread back out.
float cloud_lumps(vec3 q) {
    float a = textureLod(cg_cloud_noise, q / cloud_body_tile, 0.0).r;
    vec3 turned = vec3(q.x - 0.75 * q.z, q.y, 0.75 * q.x + q.z);
    float b = textureLod(cg_cloud_noise, turned / cloud_body_tile + vec3(0.0, 0.5, 0.0), 0.0).r;
    return clamp(0.5 + (a + b - 1.0) * 0.71, 0.0, 1.0);
}

// The cloud's body without its billows (0..1): clumps that thin towards the top, so the bigger the clump the higher
// it reaches (heaped tops), cut off flat a little above the bottom.
float cloud_body(vec3 p, float h, vec2 map) {
    float cover = map.x;
    if (cover <= 0.0) {
        return 0.0;
    }
    vec3 q = p + vec3(0.0, map.y, frameTimeCounter * cloud_wind_speed);
    float lumps = cloud_lumps(q);
    float profile = smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(0.2, 1.0, h) * 0.6);
    return clamp((lumps * profile - (1.0 - cover)) / max(cover * 0.6, 0.2), 0.0, 1.0);
}

// The cloud's density at p (0..1), and its body (for the far looks towards the light, which skip the billows).
float cloud_density(vec3 p, float h, vec2 map, out float body) {
    body = cloud_body(p, h, map);
    if (body <= 0.0) {
        return 0.0;
    }
    vec3 q = p + vec3(0.0, -frameTimeCounter * cloud_rise_speed, frameTimeCounter * cloud_detail_speed);
    vec4 n = textureLod(cg_cloud_noise, q / cloud_detail_tile, 0.0);
    float cells = n.g * 0.625 + n.b * 0.375;
    // Worn away between the cells on top (what's left is round billows) and at their hearts underneath (wisps).
    float worn = mix(cells, 1.0 - cells, smoothstep(0.0, 0.3, h)) * 0.55;
    // A cloud's edge is sharp: past it the cloud thickens within a block or two.
    return clamp((body - worn) / (1.0 - worn) * 2.5, 0.0, 1.0);
}

// Henyey-Greenstein, 1 for light scattered the same way in every direction.
float cloud_phase(float cos_theta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cos_theta, 1e-4), 1.5);
}

// The sky's light on the clouds (no sun), from above: what lights the surfaces facing up, and through dusk and dawn
// the glow of the low sky in that direction, orange towards the setting sun and pink across from it.
vec3 cloud_sky_light(vec3 dir) {
    vec3 c = g_sky_up.rgb * 1.2;
    float dusk = twilight();
    if (dusk > 0.0) {
        vec3 low = normalize(normalize(vec3(dir.x, 0.0, dir.z) + vec3(1e-4, 0.0, 0.0)) + vec3(0.0, 0.08, 0.0));
        vec2 flat_sun = normalize(g_sun_dir.xz + vec2(1e-5, 0.0));
        float toward_sun = dot(normalize(low.xz), flat_sun) * 0.5 + 0.5;
        c += sky_look(sky_air_seen(low), low) * dusk * mix(0.6, 1.6, sqr(toward_sun));
    }
    return c;
}

// --- Reading them back -----------------------------------------------------------------------------------------------

// Each half of the image (half the window each way), and this frame's.
ivec2 cloud_half() {
    return imageSize(cg_cloud_img) / 2;
}

ivec2 cloud_slot(int frame) {
    return ivec2((frame & 1) * cloud_half().x, 0);
}

// The clouds' half-size frame (whole pixels, never past the image's half).
ivec2 cloud_size() {
    return min(ivec2(ceil(render_size() * 0.5)), cloud_half());
}

struct CloudSample {
    vec4 light;  // rgb the clouds' light, a what gets through them
    float front; // where they start along the look, in blocks
    float mid;   // where most of them are
};

// The clouds in front of a render pixel whose surface is `dist` blocks away (cloud_far_sky for the sky): the four
// half-size pixels round it, each counted only where its look went at least that far (so the sky beside a mountain
// doesn't take the mountain's empty look), and nothing where the clouds start behind the surface.
CloudSample clouds_at(ivec2 texel, float dist) {
    CloudSample s;
    s.light = vec4(0.0, 0.0, 0.0, 1.0);
    s.front = cloud_far_sky;
    s.mid = cloud_far_sky;
    ivec2 size = cloud_size();
    vec2 q = (vec2(texel) + 0.5) * 0.5 - 0.5;
    ivec2 base = ivec2(floor(q));
    vec2 f = q - vec2(base);
    ivec2 slot = cloud_slot(frameCounter);
    ivec2 below = ivec2(0, cloud_half().y);
    vec4 light = vec4(0.0);
    vec2 dists = vec2(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 at = clamp(base + o, ivec2(0), size - 1);
        vec4 d = imageLoad(cg_cloud_img, slot + below + at);
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) + 1e-4;
        w *= d.b >= dist * 0.98 ? 1.0 : 1e-4;
        light += imageLoad(cg_cloud_img, slot + at) * w;
        dists += d.rg * w;
        total += w;
    }
    light /= total;
    dists /= total;
    if (dists.x < dist) {
        float behind = dist >= cloud_far_sky ? 1.0 : smoothstep(dists.x, dists.x + 16.0, dist);
        s.light = mix(vec4(0.0, 0.0, 0.0, 1.0), light, behind);
        s.front = dists.x;
        s.mid = dists.y;
    }
    return s;
}

#endif

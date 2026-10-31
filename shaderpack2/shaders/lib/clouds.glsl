// The Overworld's clouds: a layer of real clouds round Minecraft's cloud height, up to 64 blocks deep, drawn as a
// volume (program/clouds.csh) instead of Minecraft's flat blocks, and a high thin layer far above it. What they are,
// and how the frame reads them back.
// - The weather: a slow map, kilometres across, says how much of the sky the clouds cover from place to place (clear
//   blue, scattered, broken, nearly closed) and what kind of cloud is there: flat stratocumulus sheets with gaps,
//   small fair-weather cumulus, or big cumulus whose heaped tops grow tall where the cover is thick. It drifts with
//   the wind and changes from day to day; rain closes it into a grey deck.
// - Shape: clumps from a noise volume (textures/cloud_noise.dat, see tools/cloud_noise.py), flat underneath, their
//   edges worn by a finer noise into round billows on top and swirled wisps underneath and at the edges, with small
//   loose scraps beside and under the bigger ones. A cloud thickens gradually from its edge: thin parts and edges stay
//   see-through, only thick cores close up. The clumps' noise is read twice, the second time turned and a little
//   smaller, so together they never line up in rows and only repeat every 1024 blocks.
// - Light: the sun's (or the moon's), dimmed by the cloud between a point and the light (three looks towards it, the
//   nearest through the billows, so their crevices shade) and scattered mostly forwards, so a cloud's edge round the
//   sun shines silver and the sun burns through thin parts; with the sun behind you their outer billows are a little
//   darker than the folds (the "powder" look of real cumulus). The sky's light from above, so undersides go grey-blue,
//   and a little of the ground's from below; through dusk and dawn the low sky's glow. Far off they fade into the
//   air's colour.
// - The high layer: thin streaks and fine ripples (cirrus and cirrocumulus) on a flat sheet far above, patchy from day
//   to day, catching the light after the low clouds have lost it.
// - Wind and time: the game's clock moves them (so they don't jump and they stay put when you reload; with the
//   daylight cycle stopped they stop too), at about CLOUD_SPEED blocks a second, the wind's direction wandering over
//   days; the billows slowly turn over and rise as they go.
// - Reach: 5000 blocks, thinning out past 1500.
// They are worked out at half the render size each way and kept over frames (cg_cloud_img): this frame's half and
// last frame's side by side, each with its light (rgb, premultiplied) and what still gets through (a) above, and the
// distances (where the cloud starts, where most of it is, how far the look went) below. Their shadows on the land are
// a map in the sky's tables (cloud_shadow in lib/sky.glsl).

#if !defined LIB_CLOUDS
#define LIB_CLOUDS

#include "/lib/sky.glsl"

uniform sampler3D cg_cloud_noise; // see tools/cloud_noise.py
uniform float cloudHeight;        // Minecraft's cloud height for this world (an Iris uniform; not a number without clouds)
uniform float rainStrength;
uniform int worldTime;            // the game's clock: ticks into the day
uniform int worldDay;             // and days

const float cloud_reach = 5000.0;
const float cloud_far_sky = 60000.0; // a look that went out to the sky, in the distances
const float cloud_depth = 64.0;
const float cirrus_above = 900.0;    // the high layer, over the main layer's bottom

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

// How far each noise repeats (blocks).
const float cloud_body_tile = 256.0;
const float cloud_detail_tile = 64.0;
const float cloud_wisp_tile = 16.0;

// The game's clock in ticks, within 1024 days: every movement below comes back to where it started in that time, so
// it never jumps.
int cloud_ticks() {
    return (worldDay & 1023) * 24000 + worldTime;
}

// The steady wind in 160ths of a block a tick (whole numbers, so the carry stays exact): CLOUD_SPEED blocks a second
// towards the north-north-east at 1 (x half of it, z all of it).
ivec2 cloud_steady_wind() {
    int k = int(CLOUD_SPEED * 2.0 + 0.5);
    return ivec2(4 * k, -8 * k);
}

const float cloud_wander_ticks = 96000.0; // four days: the wind's direction wanders once round in that time
float cloud_wander_size() {
    return 0.4 * CLOUD_SPEED * (cloud_wander_ticks / 20.0) / TAU;
}

// How far the wind has carried the clouds (blocks, x and z, within 8192 either way).
vec2 cloud_drift() {
    int ticks = cloud_ticks();
    ivec2 v = cloud_steady_wind();
    vec2 steady = vec2(ivec2((ticks * v.x) % (8192 * 160), (ticks * v.y) % (8192 * 160))) / 160.0;
    float w = TAU * float(ticks % int(cloud_wander_ticks)) / cloud_wander_ticks;
    return steady + cloud_wander_size() * vec2(sin(w), -cos(w));
}

// The wind now, in blocks a second (to follow the clouds from frame to frame), as a 3D vector.
vec3 cloud_wind() {
    vec2 v = vec2(cloud_steady_wind()) / 8.0; // 160ths of a block a tick to blocks a second
    float w = TAU * float(cloud_ticks() % int(cloud_wander_ticks)) / cloud_wander_ticks;
    v += cloud_wander_size() * (TAU * 20.0 / cloud_wander_ticks) * vec2(cos(w), sin(w));
    return vec3(v.x, 0.0, v.y);
}

// How the billows turn over on the clumps: they slip back 64 blocks and rise 64 every 80 minutes of game time (one
// repeat of their noise).
vec3 cloud_churn() {
    float f = float(cloud_ticks() % 96000) / 96000.0;
    return vec3(0.0, -64.0 * f, 64.0 * f);
}

// How far the weather has changed: one repeat of its noise every 16 days.
float cloud_weather_turn() {
    return float(cloud_ticks() % 384000) / 384000.0;
}

struct CloudMap {
    float cover; // how much of the sky the clouds cover here (0..1)
    float kind;  // 0 flat stratocumulus, 1 cumulus
    float big;   // how much the cumulus here heaps up into big ones (0..1)
    float lift;  // how far the clumps' noise is shifted up here (blocks)
};

// The weather at a place (noise-space xz, before the wind).
CloudMap cloud_map(vec2 xz) {
    vec2 at = xz - cloud_drift();
    float turn = cloud_weather_turn();
    float a = textureLod(cg_cloud_noise, vec3(at / 8192.0, 0.37 + turn).xzy, 0.0).a;
    float b = textureLod(cg_cloud_noise, vec3((at + vec2(1100.0, 0.0)) / 2048.0, 0.71 + turn).xzy, 0.0).a;
    float c = textureLod(cg_cloud_noise, vec3((at + vec2(2900.0, 700.0)) / 4096.0, 0.13 - turn).xzy, 0.0).a;
    CloudMap m;
    float weather = a * 0.65 + b * 0.35;
    float cover = (0.06 + 0.8 * smoothstep(0.28, 0.74, weather)) * CLOUD_AMOUNT;
    m.cover = clamp(mix(cover, 1.0, 0.85 * rainStrength), 0.0, 1.0);
    m.kind = smoothstep(0.32, 0.6, c) * (1.0 - rainStrength);
    m.big = m.kind * smoothstep(0.45, 0.85, m.cover);
    m.lift = a * 160.0;
    return m;
}

// The clumps' noise at q (0..1): read as it is and again turned about 37 degrees and a fifth smaller (each moves and
// wraps a whole number of repeats with the wind and with cloud_origin()), the two averaged and spread back out.
float cloud_lumps(vec3 q) {
    float a = textureLod(cg_cloud_noise, q / cloud_body_tile, 0.0).r;
    vec3 turned = vec3(q.x - 0.75 * q.z, q.y, 0.75 * q.x + q.z);
    float b = textureLod(cg_cloud_noise, turned / cloud_body_tile + vec3(0.0, 0.5, 0.0), 0.0).r;
    return clamp(0.5 + (a + b - 1.0) * 0.71, 0.0, 1.0);
}

// The cloud's body without its billows (0..1). Clumps that thin towards their kind's top, so the bigger the clump the
// higher it reaches: flat sheets low down, small cumulus a third of the way up, big ones to the top of the layer.
float cloud_body(vec3 p, float h, CloudMap map) {
    if (map.cover <= 0.0) {
        return 0.0;
    }
    vec2 drift = cloud_drift();
    vec3 q = p + vec3(-drift.x, map.lift, -drift.y);
    // The outlines pushed about sideways by up to 12 blocks, so they come out ragged instead of smooth.
    vec2 push = textureLod(cg_cloud_noise, q / 128.0, 0.0).ra - 0.5;
    float lumps = cloud_lumps(q + vec3(push.x, 0.0, push.y) * 24.0);
    lumps = mix(0.3 + 0.7 * lumps, lumps, map.kind); // sheets: broad, with gaps
    float top = mix(mix(0.3, 0.45, map.kind), 1.0, map.big);
    float profile = smoothstep(0.0, 0.05, h) * (1.0 - smoothstep(top * 0.3, top, h));
    float above = lumps * profile - (1.0 - map.cover);
    float body = clamp(above / max(map.cover * 0.8, 0.4), 0.0, 1.0);
    // Scraps: small loose puffs low down just outside the clouds' edges.
    float near = smoothstep(-0.22, -0.04, above) * (1.0 - smoothstep(-0.04, 0.02, above));
    float small = textureLod(cg_cloud_noise, (q + vec3(31.0, 0.0, 17.0)) / cloud_detail_tile, 0.0).r;
    float scraps = smoothstep(0.62, 0.9, small) * near * (1.0 - smoothstep(0.1, 0.28, h)) * smoothstep(0.0, 0.04, h);
    return max(body, scraps * 0.45);
}

// The cloud's density at p (0..1), and its body (for the far looks towards the light, which skip the billows). step is
// the length of road the look takes this sample for (blocks): the billows and wisps finer than that would only
// flicker, so they give way to their average.
float cloud_density(vec3 p, float h, CloudMap map, float step, out float body) {
    body = cloud_body(p, h, map);
    if (body <= 0.0) {
        return 0.0;
    }
    vec2 drift = cloud_drift();
    vec3 q = p + vec3(-drift.x, 0.0, -drift.y) + cloud_churn();
    vec4 n = textureLod(cg_cloud_noise, q / cloud_detail_tile, 0.0);
    float cells = mix(0.48, n.g * 0.625 + n.b * 0.375, 1.0 - smoothstep(24.0, 96.0, step));
    // Worn away between the cells on top (what's left is round billows) and at their hearts underneath (wisps),
    // and finer still at the edges, swirled by the billows' own noise.
    float worn = mix(cells, 1.0 - cells, smoothstep(0.0, 0.3, h)) * 0.65;
    vec3 swirled = q + (n.rgb - 0.5) * 9.0;
    float fine = mix(0.48, textureLod(cg_cloud_noise, swirled / cloud_wisp_tile, 0.0).g, 1.0 - smoothstep(6.0, 24.0, step));
    worn += (1.0 - fine) * mix(0.42, 0.1, smoothstep(0.2, 0.8, body));
    // The billows' edges are crisp on top; underneath, where the wisps are, it thickens over a few blocks, and thin
    // parts stay see-through.
    float d = clamp((body - worn) / max(1.0 - worn, 0.05), 0.0, 1.0);
    return clamp(d * mix(1.2, 2.2, smoothstep(0.1, 0.4, h)), 0.0, 1.0);
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

// The high layer along a look (only up): its light (rgb, premultiplied) and what gets through it (a). Streaks drawn
// out along the wind, fine ripples in places, patchy from day to day; lit by the sun scattered forwards and by the
// sky, thin enough that it never hides the sky behind it.
vec4 cirrus(vec3 dir, vec3 sun_light, vec3 sky_light) {
    if (dir.y < 0.02 || !clouds_here()) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float height = cloud_bottom() + cirrus_above - cameraPosition.y;
    if (height <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float t = height / dir.y;
    vec2 drift = cloud_drift() * 2.0; // faster up there (8192 is still a whole number of every repeat below)
    vec2 at = cloud_origin().xz + dir.xz * t - drift;
    // Turned about 27 degrees, by whole numbers of every repeat below when a position wraps at 8192.
    vec2 w = vec2(at.x - 0.5 * at.y, 0.5 * at.x + at.y);
    float turn = cloud_weather_turn();
    float patches = textureLod(cg_cloud_noise, vec3(at / 8192.0, 0.55 + turn).xzy, 0.0).a;
    float streaks = textureLod(cg_cloud_noise, vec3(w.x / 2048.0, 0.21 + turn, w.y / 640.0), 0.0).a;
    float fine_streaks = textureLod(cg_cloud_noise, vec3(w.x / 512.0, 0.83, w.y / 160.0), 0.0).g;
    float ripples = textureLod(cg_cloud_noise, vec3(at / 256.0, 0.4 + turn).xzy, 0.0).b;
    float ripple_patch = smoothstep(0.6, 0.8, textureLod(cg_cloud_noise, vec3(at / 2048.0, 0.9).xzy, 0.0).a);
    float amount = smoothstep(0.5, 0.8, patches) * CLOUD_AMOUNT * (1.0 - rainStrength);
    float dots = smoothstep(0.5, 0.85, ripples) * ripple_patch;
    float wisp_soft = smoothstep(0.45, 0.95, streaks) * fine_streaks;
    float alpha = clamp((wisp_soft * 0.22 + dots * 0.12) * amount, 0.0, 0.3);
    // Fading out towards the horizon, where it gets very far away.
    alpha *= smoothstep(0.05, 0.3, dir.y);
    if (alpha <= 0.001) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float cos_theta = dot(dir, g_cloud_light.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz);
    float phase = mix(cloud_phase(cos_theta, 0.6), 1.0, 0.4);
    vec3 light = sun_light * phase * 0.4 + sky_light * 0.8;
    return vec4(light * alpha, 1.0 - alpha);
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

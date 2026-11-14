// The Overworld's clouds: a layer of real clouds whose bases gather round CLOUD_HEIGHT (Y 230 by default, always
// above the pollution's smog), drawn as a volume (program/clouds.csh) instead of Minecraft's flat blocks, and a high
// thin layer far above it. What they are, and how the frame reads them back.
// - The weather: a slow map, kilometres across, says how much of the sky the clouds cover from place to place (clear
//   blue, scattered, broken, nearly closed) and what kind of cloud is there: flat stratocumulus sheets with gaps,
//   fair-weather cumulus, or big cumulus that tower where the cover is thick. It drifts with the wind and changes from
//   day to day; rain closes it into a grey deck. With the Winds & Weather mod (and the Bridge's map of it,
//   lib/weather.glsl) how much cloud there is comes from the mod's weather regions instead, which stay where they are
//   in the world while the clouds blow through them: fair skies, broken cloud, and over its fronts a closed, lower,
//   deeper deck; over its storms dark towers on one. The clusters, the kinds and the shapes stay the pack's own.
// - Height: the bases rise and fall over a kilometre or two and from cloud to cloud, from 15 blocks under the layer's
//   height to 20 over it (sheets a little lower, cumulus a little higher). Sheets are 20 to 30 blocks deep, cumulus
//   45 to 70 tall, the big ones up to 150.
// - Shape: heaps of round lumps from a noise volume (textures/cloud_shape.dat, see tools/cloud_noise.py). A cloud is
//   where that noise is over a level; the level rises with height, slowly at first and then faster, so a cumulus
//   stands on a wide flat base and narrows into a dome or a tower, and a sheet keeps its width up to a flat top. How
//   far the noise is over the level says how far inside the cloud a point is, and the cloud thickens with that from
//   nothing at its outline: soft, see-through edges, a solid heart. A finer cell noise wears round billows into every
//   side of it (cauliflower), finer still up close. The shapes are read twice, the second time turned and a little
//   smaller, so they never line up in rows.
// - Light, in linear light like everything else: the sun's (or the moon's), dimmed by the cloud between a point and
//   the light (five looks towards it), scattered again and again inside (each time seeing less of that cloud, and
//   spreading more evenly), so a top in the sun is white, its shaded side and the folds between its billows grey, and
//   the base of a thick cloud dark; scattered mostly forwards, so a thin edge towards the sun shines silver and the
//   sun burns through thin cloud. The sky's light from above and the ground's from below fill the rest, less inside.
//   Through dusk and dawn the low sky's glow. Far off they fade into the air's colour; seen from above in front of
//   the land, the land's own haze stays behind them (program/fog.fsh).
// - The high layer: thin streaks and soft ripples (cirrus and cirrocumulus) on a flat sheet far above, patchy from day
//   to day, catching the light after the low clouds have lost it.
// - Wind and time: the game's clock moves them (so they don't jump and they stay put when you reload; with the
//   daylight cycle stopped they stop too), at about CLOUD_SPEED blocks a second, the wind's direction wandering over
//   days; the billows slowly turn over and rise as they go.
// - Reach: 5000 blocks, thinning out past 1500.
// They are worked out at half the render size each way and kept over frames (cg_cloud_img): this frame's half and
// last frame's side by side, each with its light (rgb, premultiplied) and what still gets through (a) above, and the
// distances (where the cloud starts, where most of it is, how far the look went) below. Their shadows on the land are
// a map in the sky's tables (cloud_shadow in lib/sky.glsl), and what water mirrors of them another (the sky mirror,
// lib/sky_mirror.glsl).

#if !defined LIB_CLOUDS
#define LIB_CLOUDS

#include "/lib/sky.glsl"
#include "/lib/wind.glsl"
#include "/lib/weather.glsl"

uniform sampler3D cg_cloud_shape; // see tools/cloud_noise.py

const float cloud_reach = 5000.0;
const float cloud_far_sky = 60000.0; // a look that went out to the sky, in the distances
const float cirrus_above = 900.0;    // the high layer, over where the main layer's bases gather

// The layer's heights (clouds_here, cloud_layer, cloud_bottom, cloud_depth) are in lib/sky.glsl, with the clouds'
// shadow map.

const float cloud_extinction = 0.12; // per block in a cloud's heart: half the light is gone in six blocks of it
// The shape noise's own units (a tenth of it is four or five blocks): how far over its level the noise must be for
// the cloud to be at its thickest (its outline is that sharp), and how far the billows move the outline in and out,
// the big ones and the small ones (from the deepest fold to the top of a bump).
const float cloud_edge = 0.035;
const float cloud_billow = 0.22;
const float cloud_fine = 0.06;
const float cloud_wear = cloud_billow + cloud_fine;

// Where the noise sits: positions are taken within 8192 blocks (every noise repeats evenly into it) so they stay
// exact far from the world's origin.
vec3 cloud_origin() {
    return vec3(cameraPositionInt & 8191) + cameraPositionFract;
}

// How far each noise repeats (blocks).
const float cloud_shape_tile = 512.0;
const float cloud_billow_tile = 128.0;
const float cloud_fine_tile = 32.0;

// How the billows turn over on the clouds: they slip back 128 blocks and rise 128 every 160 minutes of game time
// (one repeat of their noise).
vec3 cloud_churn() {
    float f = float(cloud_ticks() % 192000) / 192000.0;
    return vec3(0.0, -128.0 * f, 128.0 * f);
}

// How far the weather has changed: one repeat of its noise every 16 days.
float cloud_weather_turn() {
    return float(cloud_ticks() % 384000) / 384000.0;
}

struct CloudMap {
    float cover; // how much cloud there is here: 0 none .. 1 a closed sky (it fills 1 - sqrt(1 - cover) of the sky);
                 // over 1 (only with the mod's weather, lib/weather.glsl) a deck deeper than it needs to close
    float kind;  // 0 flat stratocumulus, 1 cumulus
    float lift;  // how far the shapes' noise is shifted up here (blocks)
    float base;  // where the clouds' bases are here (world Y)
    float tall;  // how high the strongest heaps here reach over their base (blocks)
    float shade; // how far into one of the mod's storms this is (0..1): its cloud is darker towards its base
};

// The weather at a place (noise-space xz, before the wind): three looks at the smooth noise, each for several things
// (its channels don't follow one another).
CloudMap cloud_map(vec2 xz) {
    vec2 at = xz - cloud_drift();
    float turn = cloud_weather_turn();
    vec4 wide = textureLod(cg_cloud_noise, vec3(at.x / 8192.0, 0.37 + turn, at.y / 8192.0), 0.0);
    vec4 mid = textureLod(cg_cloud_noise, vec3((at.x + 1100.0) / 2048.0, 0.71 + turn, at.y / 2048.0), 0.0);
    // Clouds come in groups: over a few hundred blocks the cover swells and thins, so they gather into clusters with
    // clear sky between instead of spreading out evenly. Turned, so the groups don't line up with the weather.
    vec2 g = vec2(at.x - 0.75 * at.y, 0.75 * at.x + at.y);
    vec4 near = textureLod(cg_cloud_noise, vec3(g.x / 1024.0, 0.29 + turn, g.y / 1024.0), 0.0);
    CloudMap m;
    float weather = wide.a * 0.65 + mid.a * 0.35;
    float cover = (0.1 + 0.62 * smoothstep(0.25, 0.75, weather)) * CLOUD_AMOUNT;
    float kind = smoothstep(0.25, 0.5, wide.r);
    float groups = 0.5; // how far the clusters swell and thin the cover
    float deck = 0.0;   // how far the mod's weather closes the sky here
    float storm = 0.0;  // and how far into a storm's dark towers
#if defined MOD_WEATHER
    Weather theirs = weather_at(xz - cloud_origin().xz);
    if (theirs.known > 0.0) {
        float c = weather_cover(theirs.amount);
        float closed = smoothstep(0.85, 1.0, c);
        // The Cloud Amount option thins or thickens fair and broken skies, never a closed one.
        cover = mix(cover, mix(c * CLOUD_AMOUNT, c, closed), theirs.known);
        deck = closed * theirs.known;
        storm = deck * smoothstep(0.4, 0.65, theirs.dark) * smoothstep(0.85, 1.05, theirs.heaped);
        // The mod's flat kinds (overcast, drizzle, fog, mist) are sheets. A deck is a slab whatever stands on it
        // (heaps alone would leave it thin between them, and the sun would burn through a closed sky).
        float theirs_kind = min(kind * smoothstep(0.45, 0.9, theirs.heaped), mix(1.0, 0.4, deck));
        kind = mix(kind, theirs_kind, theirs.known);
        // The clusters: none in a sky the mod has clear (it stays clear), none in a deck (it stays closed).
        groups *= mix(1.0, smoothstep(0.0, 0.135, c) * (1.0 - closed), theirs.known);
    }
#endif
    cover += (near.a - 0.5) * groups;
    m.cover = mix(clamp(cover, 0.0, 1.0 + (weather_deepest - 1.0) * deck), 1.0, rainStrength);
    m.kind = kind * (1.0 - rainStrength);
    float big = m.kind * smoothstep(0.4, 0.75, m.cover);
    // No two places read the shapes at the same height, so the noise's repeats never show as rows of the same cloud.
    m.lift = wide.a * 160.0 + near.g * 96.0;
    // Sheets sit a little lower than cumulus; a closed deck lower and more even, a rain deck lowest.
    float base = 24.0 * (mid.r - 0.5) + 14.0 * (near.r - 0.5) + mix(-3.0, 3.0, m.kind);
    base = mix(base, 0.35 * base - 6.0, deck);
    m.base = cloud_layer() + clamp(mix(base, -8.0, rainStrength), -15.0, 20.0);
    m.tall = mix(mix(20.0 + 10.0 * m.cover, 55.0 + 35.0 * m.cover, m.kind), 160.0, big);
    // A closed deck is as deep as the rain's and deeper the heavier the weather; a storm's towers fill the layer.
    m.tall = mix(mix(m.tall, max(m.tall, 70.0 + 60.0 * max(m.cover - 1.0, 0.0)), deck), 175.0, storm);
    m.tall = mix(m.tall, 70.0, rainStrength);
    m.shade = storm;
    return m;
}

// The weather part of the way from one place to another.
CloudMap cloud_map_between(CloudMap a, CloudMap b, float f) {
    CloudMap m;
    m.cover = mix(a.cover, b.cover, f);
    m.kind = mix(a.kind, b.kind, f);
    m.lift = mix(a.lift, b.lift, f);
    m.base = mix(a.base, b.base, f);
    m.tall = mix(a.tall, b.tall, f);
    m.shade = mix(a.shade, b.shade, f);
    return m;
}

// The shapes' noise at q (0..1): heaps of round lumps, 1 in a heap's heart; the share of it over a level v is
// 1 - sqrt(v).
float cloud_heaps(vec3 q) {
    return textureLod(cg_cloud_shape, q / cloud_shape_tile, 0.0).r;
}

// Where a point sits in the shapes' noise (p in noise space): carried by the wind and lifted as the weather says.
vec3 cloud_q(vec3 p, CloudMap map) {
    vec2 drift = cloud_drift();
    return p + vec3(-drift.x, map.lift, -drift.y);
}

// How the level the shape noise must reach rises with the height over the base (h 0..1 of the tallest heaps there):
// for cumulus with the square of it and more (a wide base, a dome, a tower where they grow big), for sheets only
// near the top (a slab with a flat top). And how fast it rises there.
float cloud_rise(float h, float kind) {
    return mix(smoothstep(0.3, 1.0, h), h * h * (0.5 + 0.5 * h), kind);
}

float cloud_rise_slope(float h, float kind) {
    float s = clamp((h - 0.3) / 0.7, 0.0, 1.0);
    return mix(6.0 * s * (1.0 - s) / 0.7, h * (1.0 + 1.5 * h), kind);
}

// How far inside a cloud a point is, in the shape noise's own units: 0 at its outline before the billows (they move
// it by half of cloud_wear either way), cloud_edge and more in its heart, well below 0 in clear air. In rain a closed
// deck. The base is flat: the cloud thickens over its lowest three blocks (and the billows only start above it, see
// cloud_density); and near the top of the layer the level rises a little more, so no billow is cut off flat there.
float cloud_inside(vec3 p, float y, CloudMap map) {
    float z = y - map.base;
    if (map.cover <= 0.0 || z <= 0.0 || z >= map.tall) {
        return -1.0;
    }
    float h = z / map.tall;
    // Under a third of the way up the noise is read half as fast with height, so a heap stands on its base like a
    // column instead of rounding off under itself: a flat bottom, as the air's dew point gives real ones.
    float low = 0.35 * map.tall;
    vec3 q = cloud_q(p, map);
    q.y += z < low ? 0.5 * (low - z) : 0.0;
    float n = cloud_heaps(q);
    float level = 1.0 - map.cover * (1.0 - cloud_rise(h, map.kind)) + 0.5 * cloud_wear * smoothstep(0.75, 1.0, h);
    return min(n - level, z * cloud_edge / 3.0);
}

// How thick the cloud is (0..1) that far inside, with its outline moved in by that much.
float cloud_thick(float inside, float worn) {
    return clamp((inside - worn) / cloud_edge, 0.0, 1.0);
}

// The cloud without its billows (0..1) at p (noise space) and height y (world): the billows' bumps and folds evened
// out. For the far looks towards the light, the shadows on the land and the mirror.
float cloud_body(vec3 p, float y, CloudMap map) {
    return cloud_thick(cloud_inside(p, y, map), -0.5 * cloud_edge);
}

// How many blocks of cloud are over a point that far inside, about (from how fast the level rises with height
// there): what keeps the sky's light from it.
float cloud_above(float inside, float y, CloudMap map) {
    float h = clamp((y - map.base) / map.tall, 0.0, 1.0);
    float slope = map.cover * cloud_rise_slope(h, map.kind) / map.tall; // noise units a block
    return min(max(inside, 0.0) / max(slope, 0.002), (1.0 - h) * map.tall);
}

// The cloud's density at p, height y (0..1). blur is how much this sample stands for (blocks: the road the look
// takes it for, or what a pixel covers there): billows finer than that would only shimmer, so they give way to their
// average. The billows are round bumps with folds between them (cell noise: 1 in a bump's heart), the big ones about
// 30 and 15 blocks across, the small ones 8 and 4: where the noise is strong the outline moves out, where it is weak
// it moves in, so the cloud's every side is rounds on rounds (cauliflower). Deep inside, where no fold reaches, the
// cloud is simply full.
float cloud_density(vec3 p, float y, CloudMap map, float blur, out float inside) {
    inside = cloud_inside(p, y, map);
    if (inside <= -0.5 * cloud_wear) {
        return 0.0;
    }
    if (inside >= 0.5 * cloud_wear + cloud_edge) {
        return 1.0;
    }
    vec2 drift = cloud_drift();
    vec3 qd = p + vec3(-drift.x, 0.0, -drift.y) + cloud_churn();
    vec4 n = textureLod(cg_cloud_noise, qd / cloud_billow_tile, 0.0);
    // No billows on the flat base: they grow in over the first sixteen blocks up.
    float grown = smoothstep(0.0, 16.0, y - map.base);
    float worn = cloud_billow * (0.5 - n.g) * (1.0 - smoothstep(24.0, 80.0, blur)) * grown;
    float near = 1.0 - smoothstep(4.0, 12.0, blur);
    if (near > 0.0) {
        // Swirled a little by the bigger noise, so the small bumps don't sit in rows.
        vec4 f = textureLod(cg_cloud_noise, (qd + (n.rgb - 0.5) * 6.0) / cloud_fine_tile, 0.0);
        worn += cloud_fine * (0.5 - (f.g * 0.6 + f.b * 0.4)) * near * grown;
    }
    return cloud_thick(inside, worn);
}

// Henyey-Greenstein, 1 for light scattered the same way in every direction.
float cloud_phase(float cos_theta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cos_theta, 1e-4), 1.5);
}

// How much of the sun's (or the moon's) light a point in a cloud sends on towards the eye, with tau of cloud between
// the point and the light (about 1 in the open, side on). Light scattered once sees all of that cloud and goes mostly
// on forwards (a strong lobe round the light, a weaker one back the way it came); light scattered again and again
// sees less of the cloud each time (0.35 of the depth a pass, each pass with 0.45 of the light of the one before)
// and spreads more evenly, so the body in the shade of its own top keeps some of the sun, as a real cloud's does, and
// only deep inside goes dim.
float cloud_key(float tau, float cos_theta) {
    float key = 0.0;
    float share = 1.0;
    float depth = 1.0;
    float lobe = 1.0;
    for (int o = 0; o < 4; o++) {
        float phase = mix(cloud_phase(cos_theta, -0.25 * lobe), cloud_phase(cos_theta, 0.7 * lobe), 0.6);
        key += share * exp(-tau * depth) * phase;
        share *= 0.45;
        depth *= 0.5;
        lobe *= 0.5;
    }
    return key * 1.1;
}

// The light of a point in a cloud towards the eye (linear): the sun's as above; the sky's from above, less the more
// cloud is over the point (above, blocks) down to a sixth; and the ground's from below, less the higher over the base
// (z, blocks). A cloud here stands for one ten times its size, so its heart and its base are in real shade.
vec3 cloud_shade(float tau, float cos_theta, float above, float z, vec3 sun, vec3 sky_light, vec3 ground_light) {
    vec3 fill = sky_light * mix(0.15, 1.0, exp(-above * 0.08)) + ground_light * exp(-z * 0.05);
    return sun * cloud_key(tau, cos_theta) + fill;
}

// The sky's light on the clouds (no sun), from above: what lights the surfaces facing up, and through dusk and dawn
// the glow of the low sky in that direction, orange towards the setting sun and pink across from it.
vec3 cloud_sky_light(vec3 dir) {
    vec3 c = g_sky_up.rgb;
    float dusk = twilight();
    if (dusk > 0.0) {
        vec3 low = normalize(normalize(vec3(dir.x, 0.0, dir.z) + vec3(1e-4, 0.0, 0.0)) + vec3(0.0, 0.08, 0.0));
        vec2 flat_sun = normalize(g_sun_dir.xz + vec2(1e-5, 0.0));
        float toward_sun = dot(normalize(low.xz), flat_sun) * 0.5 + 0.5;
        c += sky_look(sky_air_seen(low), low) * dusk * mix(0.5, 1.3, sqr(toward_sun));
    }
    return c;
}

// The light that reaches a cloud's underside from below: the sunlit (or moonlit) ground's, green-brown, and a share
// of the sky's from round the horizon.
vec3 cloud_ground_light(vec3 sky_light) {
    vec3 l = g_cloud_light.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz;
    return g_cloud_light.rgb * max(l.y, 0.0) * vec3(0.05, 0.055, 0.04) + sky_light * 0.15;
}

// The high layer along a look (only up): its light (rgb, premultiplied) and what gets through it (a). Streaks drawn
// out along the wind, fine ripples in places, patchy from day to day; lit by the sun scattered forwards and by the
// sky, thin enough that it never hides the sky behind it. From a point (scene space): the camera, or what water
// mirrors it at.
vec4 cirrus(vec3 dir, vec3 sun_light, vec3 sky_light, vec3 from) {
    if (dir.y < 0.02 || !clouds_here()) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float height = cloud_layer() + cirrus_above - cameraPosition.y - from.y;
    if (height <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float t = height / dir.y;
    vec2 drift = cloud_drift() * 2.0; // faster up there (8192 is still a whole number of every repeat below)
    vec2 at = cloud_origin().xz + from.xz + dir.xz * t - drift;
    // Turned about 27 degrees, by whole numbers of every repeat below when a position wraps at 8192.
    vec2 w = vec2(at.x - 0.5 * at.y, 0.5 * at.x + at.y);
    float turn = cloud_weather_turn();
    float patches = textureLod(cg_cloud_noise, vec3(at / 8192.0, 0.55 + turn).xzy, 0.0).a;
    float amount = smoothstep(0.5, 0.8, patches) * CLOUD_AMOUNT * (1.0 - rainStrength);
    // Fading out towards the horizon, where it gets very far away.
    amount *= smoothstep(0.05, 0.3, dir.y);
    if (amount <= 0.001) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float streaks = textureLod(cg_cloud_noise, vec3(w.x / 2048.0, 0.21 + turn, w.y / 640.0), 0.0).a;
    float fine_streaks = textureLod(cg_cloud_noise, vec3(w.x / 512.0, 0.83, w.y / 160.0), 0.0).g;
    // The ripples: soft rows across the streaks (smooth noise drawn out along them), broken up along their length.
    float rows = textureLod(cg_cloud_noise, vec3(w.x / 200.0, 0.4 + turn, w.y / 560.0), 0.0).a;
    float broken = textureLod(cg_cloud_noise, vec3(w.x / 300.0, 0.66 - turn, w.y / 300.0), 0.0).a;
    float ripple_patch = smoothstep(0.6, 0.8, textureLod(cg_cloud_noise, vec3(at / 2048.0, 0.9).xzy, 0.0).a);
    float ripples = smoothstep(0.35, 0.85, rows) * smoothstep(0.3, 0.7, broken) * ripple_patch;
    float wisp_soft = smoothstep(0.45, 0.95, streaks) * fine_streaks;
    float alpha = clamp((wisp_soft * 0.22 + ripples * 0.14) * amount, 0.0, 0.3);
    if (alpha <= 0.001) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float cos_theta = dot(dir, g_cloud_light.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz);
    float phase = mix(cloud_phase(cos_theta, 0.6), 1.0, 0.4);
    vec3 light = sun_light * phase * 0.4 + sky_light * 0.8;
    return vec4(light * alpha, 1.0 - alpha);
}

// --- Reading them back -----------------------------------------------------------------------------------------------
#if !defined CLOUDS_NO_READ_BACK

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
// doesn't take the mountain's empty look). A look that ended at about this surface (the four render pixels it stood
// for were on it) holds just the cloud in front of it, so it counts as it is; one that went on far past it (the sky
// beside it) counts only for the part of its cloud that starts before the surface, and none of it where its cloud
// starts behind.
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
    bool sky = dist >= cloud_far_sky;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 at = clamp(base + o, ivec2(0), size - 1);
        vec4 d = imageLoad(cg_cloud_img, slot + below + at);
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) + 1e-4;
        w *= d.b >= dist * 0.98 ? 1.0 : 1e-4;
        float in_front = sky || d.b <= dist * 1.1 ? 1.0 : smoothstep(d.r, d.r + 16.0, dist);
        light += mix(vec4(0.0, 0.0, 0.0, 1.0), imageLoad(cg_cloud_img, slot + at), in_front) * w;
        dists += d.rg * w;
        total += w;
    }
    s.light = light / total;
    dists /= total;
    if (dists.x < dist) {
        s.front = dists.x;
        s.mid = dists.y;
    }
    return s;
}

#endif // CLOUDS_NO_READ_BACK

#endif

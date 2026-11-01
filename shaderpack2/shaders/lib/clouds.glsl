// The Overworld's clouds: a layer of real clouds whose bases gather round CLOUD_HEIGHT (Y 230 by default, always
// above the pollution's smog), drawn as a volume (program/clouds.csh) instead of Minecraft's flat blocks, and a high
// thin layer far above it. What they are, and how the frame reads them back.
// - The weather: a slow map, kilometres across, says how much of the sky the clouds cover from place to place (clear
//   blue, scattered, broken, nearly closed) and what kind of cloud is there: flat stratocumulus sheets with gaps,
//   small fair-weather cumulus, or big cumulus that tower where the cover is thick. It drifts with the wind and
//   changes from day to day; rain closes it into a grey deck.
// - Height: the bases rise and fall over a kilometre or two and from cloud to cloud, from 15 blocks under the layer's
//   height to 20 over it (sheets a little lower, cumulus a little higher), so the layer never reads as one flat
//   floor. Sheets are 12 to 24 blocks deep, small cumulus 24 to 40 tall, the big ones up to 95.
// - Shape: clumps from a noise volume (textures/cloud_noise.dat, see tools/cloud_noise.py), gathered into groups by
//   a cover that swells and thins over a few hundred blocks (so they don't spread out evenly, one to each of the
//   noise's cells). Each rises from a flat base to a top that is higher the stronger the clump is there: a sheet's top
//   comes up quickly and stays flat, a cumulus's keeps rising towards its heart into a dome. Round billows are worn
//   into the clump itself, so they bulge from its sides as well as its top (cauliflower), and past them the cloud
//   closes up within a block or two, a crisp surface; swirled wisps hang under the base and, up close, a frayed edge
//   about a block across; small loose scraps hang beside and under the bigger ones. The clumps' noise is read twice,
//   the second time turned and a little smaller, so together they never line up in rows and only repeat every 1024
//   blocks.
// - Light: the sun's (or the moon's), dimmed by the cloud between a point and the light (four looks towards it, the
//   nearest through the billows, so their crevices shade) and scattered mostly forwards, so a cloud's edge round the
//   sun shines silver and the sun burns through thin parts; with the sun behind you their outer billows are a little
//   darker than the folds (the "powder" look of real cumulus). The sky's light from above, so undersides go grey-blue
//   (by night a little under the sky's own colour), and a little of the ground's from below; through dusk and dawn
//   the low sky's glow. Far off they fade into the air's colour; seen from above in front of the land, the land's own
//   haze stays behind them (program/fog.fsh).
// - The high layer: thin streaks and soft ripples (cirrus and cirrocumulus) on a flat sheet far above, patchy from day
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
#include "/lib/wind.glsl"

const float cloud_reach = 5000.0;
const float cloud_far_sky = 60000.0; // a look that went out to the sky, in the distances
const float cirrus_above = 900.0;    // the high layer, over where the main layer's bases gather

// The layer's heights (clouds_here, cloud_layer, cloud_bottom, cloud_depth) are in lib/sky.glsl, with the clouds'
// shadow map.

const float cloud_extinction = 0.4; // per block in the thickest cloud
// The sun's and the sky's light on the clouds, against a white face's (display values, see march() in clouds.csh).
const float cloud_sun_gain = 0.85;
const float cloud_sky_gain = 0.8;
const float cloud_lobe_gain = 0.1; // the forward lobe's, in linear light

// Where the noise sits: positions are taken within 8192 blocks (every noise repeats evenly into it) so they stay
// exact far from the world's origin.
vec3 cloud_origin() {
    return vec3(cameraPositionInt & 8191) + cameraPositionFract;
}

// How far each noise repeats (blocks).
const float cloud_body_tile = 256.0;
const float cloud_detail_tile = 64.0;
const float cloud_wisp_tile = 16.0;

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
    float base;  // where the clouds' bases are here (world Y)
    float tall;  // how high the strongest clumps here reach over their base (blocks)
};

// The weather at a place (noise-space xz, before the wind).
CloudMap cloud_map(vec2 xz) {
    vec2 at = xz - cloud_drift();
    float turn = cloud_weather_turn();
    float a = textureLod(cg_cloud_noise, vec3(at / 8192.0, 0.37 + turn).xzy, 0.0).a;
    float b = textureLod(cg_cloud_noise, vec3((at + vec2(1100.0, 0.0)) / 2048.0, 0.71 + turn).xzy, 0.0).a;
    float c = textureLod(cg_cloud_noise, vec3((at + vec2(2900.0, 700.0)) / 4096.0, 0.13 - turn).xzy, 0.0).a;
    // The bases: a slow swell over a kilometre or two, and a quicker one from cloud to cloud.
    float e = textureLod(cg_cloud_noise, vec3((at + vec2(300.0, 2100.0)) / 4096.0, 0.62 - turn).xzy, 0.0).a;
    float f = textureLod(cg_cloud_noise, vec3((at + vec2(700.0, 900.0)) / 1024.0, 0.47 + turn).xzy, 0.0).a;
    CloudMap m;
    float weather = a * 0.65 + b * 0.35;
    float cover = (0.06 + 0.8 * smoothstep(0.28, 0.74, weather)) * CLOUD_AMOUNT;
    m.cover = clamp(mix(cover, 1.0, 0.85 * rainStrength), 0.0, 1.0);
    m.kind = smoothstep(0.32, 0.6, c) * (1.0 - rainStrength);
    m.big = m.kind * smoothstep(0.45, 0.85, m.cover);
    m.lift = a * 160.0;
    // Sheets sit a little lower than cumulus; a rain deck lower still.
    float base = 24.0 * (e - 0.5) + 16.0 * (f - 0.5) + mix(-3.0, 3.0, m.kind);
    m.base = cloud_layer() + clamp(mix(base, -8.0, rainStrength), -15.0, 20.0);
    // Sheets 12 to 24 blocks deep, small cumulus 24 to 40 tall, the big ones up to 120 (towers taller than wide).
    m.tall = mix(mix(12.0 + 12.0 * m.cover, 24.0 + 16.0 * m.cover, m.kind), 120.0, m.big);
    m.tall = mix(m.tall, 50.0, rainStrength);
    // Clouds come in groups: over a few hundred blocks the cover swells and thins (two reads of the smooth noise,
    // turned apart), so the puffs gather into clusters with clear sky between instead of spreading out evenly, one to
    // each of the clumps' noise cells. Rain's closed deck stays closed.
    vec2 g1 = vec2(at.x - 0.75 * at.y, 0.75 * at.x + at.y);
    vec2 g2 = vec2(at.x + 0.5 * at.y, at.y - 0.5 * at.x);
    float group = textureLod(cg_cloud_noise, vec3(g1.x / 512.0, 0.29 + turn, g1.y / 512.0), 0.0).a * 0.6
                + textureLod(cg_cloud_noise, vec3(g2.x / 256.0, 0.83 - turn, g2.y / 256.0), 0.0).a * 0.4;
    m.cover = clamp(m.cover + (group - 0.5) * 0.9 * (1.0 - rainStrength), 0.0, 1.0);
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

// Where a point sits in the clumps' noise (p in noise space): carried by the wind and lifted as the weather says.
vec3 cloud_q(vec3 p, CloudMap map) {
    vec2 drift = cloud_drift();
    return p + vec3(-drift.x, map.lift, -drift.y);
}

// The clumps' noise at q, its outlines pushed about sideways by up to 12 blocks so they come out ragged instead of
// smooth, as the weather's kind and rain take it (0..1).
float cloud_clumps(vec3 q, CloudMap map) {
    vec2 push = textureLod(cg_cloud_noise, q / 128.0, 0.0).ra - 0.5;
    float lumps = cloud_lumps(q + vec3(push.x, 0.0, push.y) * 24.0);
    lumps = mix(0.3 + 0.7 * lumps, lumps, map.kind); // sheets: broad, with gaps
    return mix(lumps, 0.55 + 0.45 * lumps, rainStrength); // rain: one closed deck
}

// How strong a clump is (0 at its outline, 1 in the strongest hearts, below 0 outside), from its noise.
float cloud_strength(float clumps, CloudMap map) {
    return (clumps - (1.0 - map.cover)) / max(map.cover * 0.85, 0.15);
}

// The body (0..1: how far inside it a point is) at height z over the clouds' base, where the clump is that strong.
// Each clump has a top over its flat base that rises with how strong the clump is there, so the stronger it is the
// higher it reaches: a sheet's top comes up quickly and stays flat, a cumulus's keeps rising towards its heart into a
// dome, and where the big ones grow it towers. Under its top it thickens over ramp blocks; over its base within a
// block and a half (a hard, flat underside).
float cloud_body_of(float strength, float z, float ramp, CloudMap map) {
    float s = clamp(strength, 0.0, 1.0);
    float sheet_top = smoothstep(0.0, 0.6, s);
    float heap_top = pow(s, 0.75);
    float top = mix(mix(sheet_top, heap_top, map.kind), smoothstep(0.0, 0.5, s), rainStrength) * map.tall;
    return strength <= 0.0 ? 0.0 : clamp(min((top - z) / ramp, (z + 0.25) / 1.5), 0.0, 1.0);
}

// The thickening under a cloud's top: about a quarter of its height, so its billows have room (the far looks
// towards the light, which skip the billows, see the body as this soft dome).
float cloud_ramp(CloudMap map) {
    return clamp(0.25 * map.tall, 4.0, 14.0);
}

// Scraps: small loose puffs low down just outside the clouds' edges.
float cloud_scraps(vec3 q, float clumps, float z, CloudMap map) {
    float edge = clumps - (1.0 - map.cover);
    float near = smoothstep(-0.14, -0.03, edge) * (1.0 - smoothstep(-0.03, 0.01, edge));
    if (near <= 0.0) {
        return 0.0;
    }
    float small = textureLod(cg_cloud_noise, (q + vec3(31.0, 0.0, 17.0)) / cloud_detail_tile, 0.0).r;
    return smoothstep(0.62, 0.9, small) * near * (1.0 - smoothstep(5.0, 14.0, z)) * smoothstep(0.0, 2.0, z) * 0.45;
}

// The cloud's body without its billows (0..1) at p (noise space) and height y (world).
float cloud_body(vec3 p, float y, CloudMap map) {
    float z = y - map.base;
    if (map.cover <= 0.0 || z <= 0.0 || z >= map.tall) {
        return 0.0;
    }
    vec3 q = cloud_q(p, map);
    float clumps = cloud_clumps(q, map);
    float body = cloud_body_of(cloud_strength(clumps, map), z, cloud_ramp(map), map);
    return max(body, cloud_scraps(q, clumps, z, map));
}

// The cloud's density at p, height y (0..1), and its body (for the far looks towards the light, which skip the
// billows). blur is how much this sample stands for (blocks: the road the look takes it for, or what a pixel covers
// there): billows and wisps finer than that would only shimmer, so they give way to their average.
// The billows are round bumps worn into the clump itself before its top is found, so they shape its sides as much as
// its top: the clump's noise is worn away between the cells of a finer cell noise (bumps about 16 blocks across, and
// 8 on them), up to about 6 and 3 blocks deep, which also lowers the top between them (cauliflower). Cells only make
// bumps on an outline; the clumps themselves come from the bigger noise, so the bumps never line up into a honeycomb.
// Past the wear the cloud closes up within a block or two (a crisp surface). In the cloud's lowest few blocks the
// cells' hearts are worn instead (swirled wisps hanging under a flat base), and up close a finer fray frays the edge.
float cloud_density(vec3 p, float y, CloudMap map, float blur, out float body) {
    float z = y - map.base;
    body = 0.0;
    if (map.cover <= 0.0 || z <= 0.0 || z >= map.tall) {
        return 0.0;
    }
    vec3 q = cloud_q(p, map);
    float clumps = cloud_clumps(q, map);
    float ramp = cloud_ramp(map);
    body = cloud_body_of(cloud_strength(clumps, map), z, ramp, map);
    float scraps = cloud_scraps(q, clumps, z, map);
    body = max(body, scraps);
    if (body <= 0.0) {
        return 0.0;
    }
    vec2 drift = cloud_drift();
    vec3 qd = p + vec3(-drift.x, 0.0, -drift.y) + cloud_churn();
    vec4 n = textureLod(cg_cloud_noise, qd / cloud_detail_tile, 0.0);
    float detail = 1.0 - smoothstep(12.0, 48.0, blur);
    float bumps = mix(0.5, n.g, detail);  // 1 at a bump's heart
    float small = mix(0.5, n.b, 1.0 - smoothstep(6.0, 24.0, blur));
    float under = 1.0 - smoothstep(0.5, 2.5, z);
    // Worn on top and round the sides (in the clumps' noise, about 0.02 a block), more the higher up (a firm lower
    // body, a broken top); under the base the hearts instead.
    float high = clamp(z / map.tall, 0.0, 1.0);
    float worn = mix(((1.0 - bumps) * 0.12 + (1.0 - small) * 0.05) * mix(0.6, 1.7, high), bumps * 0.08, under);
    vec3 swirled = qd + (n.rgb - 0.5) * 9.0;
    float fine = mix(0.5, textureLod(cg_cloud_noise, swirled / cloud_wisp_tile, 0.0).a, 1.0 - smoothstep(3.0, 10.0, blur));
    worn += (1.0 - fine) * 0.03;
    // Up close (flying past), a finer fray still at the edges, about a block across.
    if (blur < 2.5) {
        float tiny = textureLod(cg_cloud_noise, swirled / 5.0 + vec3(0.37, 0.0, 0.61), 0.0).a;
        worn += (1.0 - tiny) * 0.02 * (1.0 - smoothstep(0.75, 2.5, blur));
    }
    worn *= 1.0 - 0.6 * rainStrength; // a rain deck is worn only a little
    float shaped = cloud_body_of(cloud_strength(clumps - worn, map), z, max(1.5, 0.5 * blur), map);
    float d = max(shaped, scraps * fine);
    return clamp(d * mix(1.6, 2.4, smoothstep(0.5, 4.0, z)), 0.0, 1.0);
}

// Henyey-Greenstein, 1 for light scattered the same way in every direction.
float cloud_phase(float cos_theta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cos_theta, 1e-4), 1.5);
}

// How much of the sun's (or the moon's) light reaches a point in a cloud with tau of cloud between it and the light
// (0..1). Light scattered once sees all of that cloud; light scattered again and again sees less of it each time (0.4
// of the depth a pass, each pass with half the light of the one before, eight passes), so the body in the shade of
// its own top keeps some of the sun, as a real cloud's does, and only deep inside goes dim.
float cloud_key(float tau) {
    float key = 0.0;
    float share = 1.0;
    float depth = 1.0;
    for (int o = 0; o < 8; o++) {
        key += share * exp(-tau * depth);
        share *= 0.5;
        depth *= 0.4;
    }
    return key / 1.9921875;
}

// The bright light scattered forwards round the sun (or the moon), on top of the rest: the silver edge, and the
// light burning through thin parts. Only the light that came straight through (tau) takes it, and only near the
// light's direction.
float cloud_lobe(float cos_theta, float tau) {
    return max(cloud_phase(cos_theta, 0.7) - 1.0, 0.0) * exp(-tau);
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
    float streaks = textureLod(cg_cloud_noise, vec3(w.x / 2048.0, 0.21 + turn, w.y / 640.0), 0.0).a;
    float fine_streaks = textureLod(cg_cloud_noise, vec3(w.x / 512.0, 0.83, w.y / 160.0), 0.0).g;
    // The ripples: soft rows across the streaks (smooth noise drawn out along them), broken up along their length.
    float rows = textureLod(cg_cloud_noise, vec3(w.x / 200.0, 0.4 + turn, w.y / 560.0), 0.0).a;
    float broken = textureLod(cg_cloud_noise, vec3(w.x / 300.0, 0.66 - turn, w.y / 300.0), 0.0).a;
    float ripple_patch = smoothstep(0.6, 0.8, textureLod(cg_cloud_noise, vec3(at / 2048.0, 0.9).xzy, 0.0).a);
    float amount = smoothstep(0.5, 0.8, patches) * CLOUD_AMOUNT * (1.0 - rainStrength);
    float ripples = smoothstep(0.35, 0.85, rows) * smoothstep(0.3, 0.7, broken) * ripple_patch;
    float wisp_soft = smoothstep(0.45, 0.95, streaks) * fine_streaks;
    float alpha = clamp((wisp_soft * 0.22 + ripples * 0.14) * amount, 0.0, 0.3);
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

// A quick look at the clouds in a direction from a point (scene space), for what water mirrors (lib/sky_mirror.glsl):
// the layer looked through at four heights for its body alone (no billows: a reflection's look is wide and broken
// by the waves anyway), lit as the march lights it with one look towards the light, then the high layer. rgb the
// light (premultiplied), a what gets through. From the point the water mirrors them at, so they hold still on it as
// you climb or drop. From above the layer, nothing.
vec4 clouds_simple(vec3 dir, vec3 from) {
    float from_y = from.y + cameraPosition.y;
    if (dir.y < 0.01 || !clouds_here() || from_y > cloud_bottom()) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    vec4 lit = g_cloud_light;
    vec3 l = lit.w > 0.5 ? g_sun_dir.xyz : g_moon_dir.xyz;
    float cos_theta = dot(dir, l);
    float night = 1.0 - smoothstep(0.05, 0.5, luminance(lit.rgb)); // as the march (program/clouds.csh)
    float base_open = mix(mix(0.35, 0.6, rainStrength), 0.5, night);
    const vec3 to_display = vec3(1.0 / 2.2);
    vec3 sun = pow(max(lit.rgb, 0.0), to_display);
    vec3 sky_light = pow(max(cloud_sky_light(dir), 0.0), to_display);
    vec3 sky_seen = sky_look(sky_air_seen(dir), dir);
    vec3 origin = cloud_origin();
    float flat_len = length(dir.xz);
    float through = 1.0;
    vec3 light = vec3(0.0);
    const float layer_step = 20.0;
    for (int k = 0; k < 4; k++) {
        float y = cloud_bottom() + (float(k) + 0.5) * layer_step;
        float t = (y - from_y) / dir.y;
        if (t * flat_len > cloud_reach) {
            break;
        }
        vec3 p = origin + from + dir * t;
        CloudMap map = cloud_map(p.xz);
        float body = cloud_body(p, y, map);
        if (body <= 0.0) {
            continue;
        }
        // Lit as the march lights them (one look towards the light instead of four).
        float tau = cloud_body(p + l * 10.0, y + l.y * 10.0, map) * cloud_extinction * 10.0;
        float up = clamp((y - map.base) / map.tall, 0.0, 1.0);
        float open = mix(base_open, 1.0, up) * (1.0 - 0.4 * body);
        vec3 sample_light = pow(sun * cloud_key(tau) * cloud_sun_gain + sky_light * open * cloud_sky_gain, vec3(2.2))
                          + lit.rgb * cloud_lobe(cos_theta, tau) * cloud_lobe_gain;
        float haze = (1.0 - exp(-t * flat_len / 1200.0)) * 0.95;
        sample_light = mix(sample_light, sky_seen, haze);
        float step_through = exp(-body * 1.5 * cloud_extinction * layer_step / max(dir.y, 0.05) * 0.25);
        light += sample_light * through * (1.0 - step_through);
        through *= step_through;
    }
    vec4 high = cirrus(dir, lit.rgb, cloud_sky_light(dir), from);
    light += through * high.rgb;
    through *= high.a;
    return vec4(light, through);
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

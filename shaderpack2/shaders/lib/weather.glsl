// The weather far round the camera as the Winds & Weather mod has it, from the Create Galore Bridge (1.194.0 and
// later; see the Bridge's WeatherMap). The mod makes the world's weather in regions about a kilometre across: fair
// here, a front of heavy cloud over there, a storm somewhere. It draws its regions' clouds in place of Minecraft's,
// which this pack switches off for its own, so without this the pack's sky would know nothing of them.
// - The map: one word for each point of a grid 160 blocks apart, 64 x 64 points round the camera (10 kilometres
//   across, the clouds' whole reach), in the row of cg_palette_tex after the far heights' (the pack declares the image
//   881 rows tall for it). A point keeps its slot (its number modulo 64, x and z) while the window, which follows the
//   camera a point at a time, keeps it in.
// - A word is the mod's own cloud field at the point (what its clouds would look like there): bits 0..11 how much
//   cloud (0 its clearest sky .. 4095 its heaviest thunderstorm), 12..18 how dark it draws the cloud (0 white .. 127
//   black), 19..25 how heaped the cloud is (hundredths: 20 a flat sheet .. 120 towers), 26..30 how thick its layer is
//   (pairs of blocks), 31 filled. The game's own rain is not in it: that is rainStrength, as ever.
// - Without the map (no Bridge or an older one, no Winds & Weather, its weather regions switched off, or the
//   MOD_WEATHER option off) every point reads "not known", and the clouds keep to the pack's own weather.
// What the pack makes of it is in lib/clouds.glsl (how much cloud, which kind, how deep) and program/frame.csh (the
// light under a closed sky).

#if !defined LIB_WEATHER
#define LIB_WEATHER

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/view.glsl"

#if !defined CG_PALETTE_TEX
#define CG_PALETTE_TEX
uniform usampler2D cg_palette_tex;
#endif

const int weather_row = 880;     // in cg_palette_tex
const int weather_cell = 160;    // blocks between the map's points
const int weather_size = 64;     // points across the window

struct Weather {
    float known;  // 0 not known .. 1 known (between: part of the points round the place are)
    float amount; // how much cloud the mod has there: 0 its clearest sky .. 1 its heaviest thunderstorm
    float dark;   // how dark it draws that cloud: 0 white .. 1 black
    float heaped; // how heaped: 0.2 a flat sheet .. 1.2 towers
};

// The mod's weather at a place (blocks from the camera, x and z): the four points round it, evened out. Places past
// the window read its edge (a point in from it, as the Bridge's camera and the pack's can be a frame apart).
Weather weather_at(vec2 rel) {
    Weather w = Weather(0.0, 0.0, 0.0, 1.0);
#if defined MOD_WEATHER
    // Whole numbers first, so the points stay exact far from the world's origin; lifted by a multiple of the cell
    // and of the window (200000 points) so they never go negative.
    ivec2 lifted = cameraPositionInt.xz + weather_cell * 200000;
    ivec2 camera_point = lifted / weather_cell;
    vec2 p = (vec2(lifted - camera_point * weather_cell) + cameraPositionFract.xz + rel) / float(weather_cell);
    p = clamp(p, vec2(1.0 - float(weather_size / 2)), vec2(float(weather_size / 2) - 2.001));
    ivec2 low = ivec2(floor(p));
    vec2 f = p - vec2(low);
    float known = 0.0;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 slot = (camera_point + low + o) & (weather_size - 1);
        uint word = texelFetch(cg_palette_tex, ivec2(slot.y * weather_size + slot.x, weather_row), 0).r;
        float share = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) * float(word >> 31u);
        known += share;
        sum += share * vec3(float(word & 4095u), float((word >> 12u) & 127u), float((word >> 19u) & 127u));
    }
    if (known > 0.0) {
        sum /= known;
        w = Weather(known, sum.x / 4095.0, sum.y / 127.0, sum.z / 100.0);
    }
#endif
    return w;
}

// The pack's cloud cover (the cover of lib/clouds.glsl's CloudMap, 0..1) for that much of the mod's cloud. A cover c
// fills 1 - sqrt(1 - c) of the sky (weather_sky): 0.44 a quarter of it, 0.75 half, 0.96 four fifths, 1 all of it.
// The mod's own weather, left to itself, keeps to a narrow middle of its range, so the curve is steep there:
// - under 0.08 (its "none") no cloud at all; 0.2 (its "clear" at its strongest) a wisp here and there;
// - 0.31 (a twentieth of a world is under it; its "breeze") fair: a few small clouds;
// - 0.46 (half a world is under it) a quarter of the sky, the pack's own middle;
// - 0.52 (a quarter of a world is over it) broken cloud, two fifths of the sky; 0.55 (its "overcast" at its
//   strongest) over half;
// - 0.6 and over (a twentieth of a world: its wet cores, and the regions a command sets to rain or a storm) closed,
//   and the cover goes on past 1 (to weather_deepest at 0.76 and over: its storms): a deck deeper than it needs to
//   close, which is what makes heavier weather darker from below.
const float weather_deepest = 1.6;
float weather_cover(float amount) {
    float low = 0.135 * sqr(clamp((amount - 0.08) / 0.232, 0.0, 1.0));
    return clamp(low + 2.1 * clamp(amount - 0.312, 0.0, 0.144) + 3.8 * max(amount - 0.456, 0.0), 0.0, weather_deepest);
}

// How much of the sky a cover fills (0..1).
float weather_sky(float cover) {
    return 1.0 - sqrt(max(1.0 - cover, 0.0));
}

// How far the mod's weather closes the sky over a place (blocks from the camera): 0 open or not known .. 1 a deck.
float weather_closed(vec2 rel) {
    Weather w = weather_at(rel);
    return smoothstep(0.55, 1.0, weather_sky(weather_cover(w.amount))) * w.known;
}

#endif

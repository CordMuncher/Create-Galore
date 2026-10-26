// The weather where you are, as shaders.properties smooths it (0..1 each).

#if !defined LIB_WEATHER
#define LIB_WEATHER

uniform float cg_rain_here; // rain falling where you are, faded in and out over a few seconds
uniform float cg_snow_here; // the same for snow
uniform float cg_wet;       // the film of water rain leaves on everything, drying soon after it stops
uniform float cg_damp;      // how damp what rain fell on is: slower to dry than the film's shine
uniform float cg_puddle;    // how far the puddles have filled: 1 after a good rain, falling to 0 as they dry
uniform ivec2 eyeBrightnessSmooth; // the light where you are, 0..240 (block, sky)

// How much rain or snow is around you: none indoors and underground, where little sky reaches you.
float weather_around() {
    return max(cg_rain_here, cg_snow_here) * smoothstep(0.4, 0.9, float(eyeBrightnessSmooth.y) / 240.0);
}

#endif

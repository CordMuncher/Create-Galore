// The light in the air's two passes (see air.glsl), before the world is drawn: the cells (AIR_CELLS, prepare), then
// their sums down each column (AIR_SUM, prepare1).

#define AIR_WRITE
#include "/lib/settings.glsl"
#include "/lib/air.glsl"
#include "/lib/globals.glsl"
#include "/lib/motion.glsl"

#ifndef LIT_AIR
// Light in the air off: nothing to do.
layout(local_size_x = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

void main() {
}
#else

layout(local_size_x = 8, local_size_y = 8) in;

layout(rgba16f) uniform image2D colorimg10;

uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

// The view-space direction through a point (unjittered render pixels), scaled to go one block deeper per block.
vec3 view_ray(vec2 point) {
    vec3 v = unproject(gbufferProjectionInverse, point / render_size() * 2.0 - 1.0, 1.0);
    return v / -v.z;
}

#if defined AIR_CELLS

// Enough invocations for the page (see shaders.properties' sizes for colortex10 and colortex11).
#if AIR_QUALITY == 1
const vec2 workGroupsRender = vec2(0.53, 0.53);
#elif AIR_QUALITY == 2
const vec2 workGroupsRender = vec2(0.69, 1.06);
#elif AIR_QUALITY == 3
const vec2 workGroupsRender = vec2(1.03, 1.56);
#else
const vec2 workGroupsRender = vec2(1.37, 2.06);
#endif

#include "/lib/sky.glsl"
#include "/lib/light_levels.glsl"
#include "/lib/noise.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky_grid.glsl"
#include "/lib/light_volume.glsl"
#ifdef LIT_CLOUDS
#include "/lib/clouds.glsl"
#endif

uniform sampler2D colortex11; // last frame's cells

// Indoors and in caves (under a roof, between walls), the air holds dust (per block) that a sunbeam through a window
// or a gap shows up, and that glows around lamps; out under the sky the haze is the sky's own, under an overhang, a
// bridge or a floating island too (with only a roof over it, the air would light up as a pale box under it).
const float air_dust = 0.002 * AIR_DUST;
// How much of the lamps' light the air scatters around them (per block): enough for a soft glow in their colour
// close around them.
const float air_glow = 0.03 * LAMP_GLOW;
// How far up a cell looks for a roof over it (blocks).
const int air_roof_reach = 24;
// Of last frame's cell, how much each frame keeps. OWN DENOISER, kept under Ray Reconstruction: the volume is a few
// cells per hundred pixels, read smoothly, never per-pixel noise (see the README).
const float air_history = 0.9;

// Henyey-Greenstein's phase function, for the dust: mostly onwards, some all around.
float dust_phase(float cos_theta) {
    const float g = 0.5;
    return (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cos_theta, 1.5));
}

// How much of the sun's (or the moon's) light the shadow map lets through to a point in the air (scene space), from
// four taps spread over size blocks around it; 1 past the shadow map, fading into that at its edge.
float air_shadow(vec3 p, float size) {
#ifdef SHADOW_MAP
    vec3 coord = shadow_coord(p);
    float edge = max(length(p.xz), abs(p.y)) / shadowDistance;
    float fade = smoothstep(0.85, 1.0, edge);
    if (fade >= 1.0 || any(lessThan(coord, vec3(0.0))) || any(greaterThan(coord, vec3(1.0)))) {
        return 1.0;
    }
    float spread = 0.25 * size / (2.0 * shadowDistance);
    float lit = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 offset = vec2((i & 1) == 0 ? -spread : spread, (i & 2) == 0 ? -spread : spread);
        lit += texture(shadowtex1HW, vec3(coord.xy + offset, coord.z - 0.0001));
    }
    return mix(lit * 0.25, 1.0, fade);
#else
    return 1.0;
#endif
}

// Whether a point (scene space) is in water: under the water's surface the shadow map has above it; past the shadow
// map, below the sea's top. The water pass draws the water's own light (water_body.fsh).
bool air_in_water(vec3 p) {
#ifdef SHADOW_MAP
    vec3 coord = shadow_coord(p);
    if (all(greaterThan(coord, vec3(0.0))) && all(lessThan(coord, vec3(1.0)))) {
        return shadow_water_depth(p) > 0.0;
    }
#endif
#ifdef NO_SKY_LIGHT
    return false; // no sea
#else
    return p.y + cameraPosition.y < 62.5;
#endif
}

// The light the sky's light gets to a point from one direction over the sky (rays turn with seed): 1 if the ray
// leaves the blocks around you, 0 if a block stops it. walled: under a roof (a block over the point within
// air_roof_reach blocks), 1 if a ray out low over the horizon meets a block too, else 0, so that over frames it
// comes to how closed in the point is around it (a room's walls, a cave's; not an overhang's open sides). Past the
// blocks the grid has (see sky_grid.glsl), open and no roof.
float air_sky_open(vec3 p, uint seed, out float walled) {
    walled = 0.0;
#ifdef LIT_SKY_GRID
    vec3 o = sky_grid_pos(p, cameraPositionFract);
    if (any(lessThan(o, vec3(0.0))) || any(greaterThanEqual(o, vec3(float(sky_grid_size), float(sky_grid_height),
            float(sky_grid_size))))) {
        return 1.0;
    }
    int y = int(floor(o.y));
    if (sky_grid_marked(ivec2(floor(o.xz)), 0, y + 1, y + air_roof_reach)) {
        vec2 w = r2_seq2(seed + 3u);
        float low = 0.02 + 0.33 * w.x;
        float rw = sqrt(1.0 - low * low);
        float pw = 6.2831853 * w.y;
        walled = sky_ray_open(o, vec3(rw * cos(pw), low, rw * sin(pw))) ? 0.0 : 1.0;
    }
    // Spread evenly over the sky above the horizon (by solid angle: the air takes the light from every way alike).
    vec2 u = r2_seq2(seed);
    float up = 0.05 + 0.95 * u.x;
    float r = sqrt(max(0.0, 1.0 - up * up));
    float phi = 6.2831853 * u.y;
    return sky_ray_open(o, vec3(r * cos(phi), up, r * sin(phi))) ? 1.0 : 0.0;
#else
    return 1.0;
#endif
}

// Last frame's cells at a place in the volume (cells across, slices deep, between their centres): from colortex11.
vec4 air_history_at(vec2 c, float s) {
    vec2 size = vec2(textureSize(colortex11, 0));
    c = clamp(c, vec2(0.0), vec2(air_cells() - 1));
    int k = clamp(int(floor(s)), 0, air_slices - 1);
    float f = clamp(s - float(k), 0.0, 1.0);
    vec4 a = texture(colortex11, (vec2(air_texel(ivec2(0), k)) + c + 0.5) / size);
    vec4 b = texture(colortex11, (vec2(air_texel(ivec2(0), min(k + 1, air_slices - 1))) + c + 0.5) / size);
    return mix(a, b, f);
}

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 block = air_block();
    ivec2 slot = texel / block;
    int k = slot.y * air_columns + slot.x;
    ivec2 c = texel - slot * block;
    if (slot.x >= air_columns || k >= air_slices || any(greaterThanEqual(c, air_cells()))) {
        return;
    }

    // A point somewhere in the cell, a different one each frame (the R2 sequence, started at a different place for
    // each cell so neighbours don't move together).
    uint seed = hash_pcg3d(uvec3(uvec2(c), uint(k))).x;
    uint frame = uint(frameCounter);
    vec3 j = r2_seq3(frame + seed);
    vec3 ray = view_ray((vec2(c) + j.xy) * float(air_tile));
    float z = air_depth_of(float(k) + j.z);
    vec3 p = view_to_scene(ray * z);
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * ray);
    // The cell's width there (blocks), for how far apart its shadow taps go.
    float width = z * float(air_tile) * 2.0 / (gbufferProjection[1][1] * render_size().y);

    vec3 scattered = vec3(0.0); // per block, kcd/m²
    float extinction = 0.0;     // per block
#ifdef WORLD_NETHER
    // The Nether's thick air (see nether.glsl): its own light in it, and over the frames as much of it as the air
    // around sees (in a cavern all of it, in a tunnel or a fortress's hall a little: rays that get 32 blocks away, see
    // sky_ray_open); brighter over the lava seas where it is open to them; and the lamps' and the lava's light in their
    // colours where the light volume has it, which the thick air carries further than the overworld's dust does.
    bool open_air = !air_in_water(p);
    if (open_air) {
        float haze = nether_haze();
        float walled;
        float open = air_sky_open(p, frame * 7u + seed, walled);
        scattered += haze * (nether_air_radiance() * mix(0.3, 1.0, open)
                + nether_lava_sea_light(p.y + cameraPosition.y) * open);
        extinction += haze;
    }
    const float glow_per_haze = 0.5;
#elif defined WORLD_END
    // The End's haze and the mist over the void (see end.glsl), lit by the void's glow all around and by the light
    // from above where the shadow map lets it through: shafts past the islands, the pillars and the towers.
    bool open_air = !air_in_water(p);
    if (open_air) {
        float haze = end_haze(p.y + cameraPosition.y);
        scattered += haze * end_haze_light(dir, air_shadow(p, width));
        extinction += haze;
    }
    const float glow_per_haze = 0.25;
#else
    if (!air_in_water(p)) {
        // The sky's air and haze here.
        float h = atm_height(p.y + cameraPosition.y);
        Air air = atm_air();
        vec3 density = atm_density(h - atm_bottom);
        vec3 rayleigh = atm_rayleigh * density.x * 0.001;
        vec3 mie = air.mie_scattering * density.y * 0.001;
        extinction = dot(atm_extinction(air, density), vec3(1.0 / 3.0)) * 0.001;

        // The sun and the moon through the air above the point, where the planet doesn't hide them; rain hides them
        // too, as it greys the sky.
        vec3 origin = vec3(0.0, h, 0.0);
        vec3 sun_dir = g_sun_dir.xyz;
        vec3 moon_dir = g_moon_dir.xyz;
        float clear = 1.0 - 0.95 * rainStrength;
        vec3 sun = atm_ray_sphere(origin, sun_dir, atm_bottom) < 0.0 ? sun_illuminance * atm_trans(h, sun_dir.y) : vec3(0.0);
        vec3 moon = atm_ray_sphere(origin, moon_dir, atm_bottom) < 0.0 ? moon_illuminance() * atm_trans(h, moon_dir.y) : vec3(0.0);
        float lit = air_shadow(p, width) * clear;
#ifdef LIT_CLOUDS
        lit *= cloud_shadow(p, g_light_dir.xyz);
#endif
        float walled;
        float open = air_sky_open(p, frame * 7u + seed, walled);
        float cos_sun = dot(dir, sun_dir);
        float cos_moon = dot(dir, moon_dir);
        scattered += (rayleigh * atm_phase_rayleigh(cos_sun) + mie * atm_phase_mie(cos_sun)) * sun * lit
                + (rayleigh * atm_phase_rayleigh(cos_moon) + mie * atm_phase_mie(cos_moon)) * moon * lit;
        // The light scattered more than once (Hillaire's table), as much as the sky reaches the point.
        scattered += (atm_msctr(h, sun_dir.y) * sun_illuminance + atm_msctr(h, moon_dir.y) * moon_illuminance())
                * (rayleigh + mie) * open * (1.0 - 0.75 * rainStrength);

        // Dust indoors: lit by the sun and the moon where they get in, and by the sky through the openings (a diffuse
        // light from above lights it with about 0.57 of its radiance from all around).
        if (walled > 0.0) {
            scattered += air_dust * walled * ((sun * dust_phase(cos_sun) + moon * dust_phase(cos_moon)) * lit
                    + sky_irradiance(vec3(0.0, 1.0, 0.0)) * (0.57 / PI) * open);
            extinction += air_dust * walled;
        }

#ifdef LIT_COLORED_LIGHT
        // The lamps' light where the light volume has it, all around (see block_light_color).
        vec3 at;
        float fade = light_volume_at(p, vec3(0.0), 0.0, at);
        if (fade > 0.0) {
            vec3 light = light_volume_sample(at, section_color);
            float peak = max(light.r, max(light.g, light.b));
            if (peak > 1e-3) {
                scattered += air_glow * block_light_tint(light) * block_light_lux() * block_light_curve(peak) * fade
                        / (4.0 * PI);
            }
        }
#endif
    }
#endif
#if defined NO_SKY_LIGHT && defined LIT_COLORED_LIGHT
    // The lamps' and the lava's light in the air around them.
    if (open_air) {
        vec3 at;
        float fade = light_volume_at(p, vec3(0.0), 0.0, at);
        if (fade > 0.0) {
            vec3 light = light_volume_sample(at, section_color);
            float peak = max(light.r, max(light.g, light.b));
            if (peak > 1e-3) {
                scattered += (air_glow + glow_per_haze * extinction) * block_light_tint(light) * block_light_lux()
                        * block_light_curve(peak) * fade / (4.0 * PI);
            }
        }
    }
#endif
    vec4 cell = vec4(scattered * exposure(), extinction * 1000.0);

    // Last frame's cell where this cell's middle was then, in last frame's exposure; none where it was off the screen
    // or beyond the volume.
    vec3 middle = view_to_scene(view_ray((vec2(c) + 0.5) * float(air_tile)) * air_depth_of(float(k) + 0.5));
    vec4 clip = previous_clip(middle, gbufferPreviousProjection);
    float before = texelFetch(colortex11, air_exposure_texel(), 0).r;
    if (clip.w > 0.0 && before > 0.0 && !isnan(before) && !isinf(before)) {
        vec2 prev_c = (clip.xy / clip.w * 0.5 + 0.5) * render_size() / float(air_tile) - 0.5;
        float prev_s = air_slice_of(clip.w) - 0.5;
        if (all(greaterThanEqual(prev_c, vec2(-0.5))) && all(lessThanEqual(prev_c, vec2(air_cells()) - 0.5))
                && prev_s >= -0.5 && prev_s <= float(air_slices) - 0.5) {
            vec4 history = air_history_at(prev_c, prev_s);
            history.rgb *= exposure() / before;
            if (!any(isnan(history)) && !any(isinf(history))) {
                cell = mix(cell, history, air_history);
            }
        }
    }
    imageStore(colorimg10, texel, cell);
}

#elif defined AIR_SUM

// One invocation for each column of cells.
#if AIR_QUALITY == 1
const vec2 workGroupsRender = vec2(0.07, 0.07);
#elif AIR_QUALITY == 2
const vec2 workGroupsRender = vec2(0.09, 0.09);
#elif AIR_QUALITY == 3
const vec2 workGroupsRender = vec2(0.13, 0.13);
#else
const vec2 workGroupsRender = vec2(0.17, 0.17);
#endif

layout(rgba16f) uniform image2D colorimg11;

void main() {
    ivec2 c = ivec2(gl_GlobalInvocationID.xy);
    if (c == ivec2(0)) {
        imageStore(colorimg11, air_exposure_texel(), vec4(exposure(), 0.0, 0.0, 1.0));
    }
    if (any(greaterThanEqual(c, air_cells()))) {
        return;
    }
    // Blocks along the column's ray per block of view depth.
    float stretch = length(view_ray((vec2(c) + 0.5) * float(air_tile)));
    vec3 light = vec3(0.0);
    float through = 1.0;
    float near = 0.0;
    for (int k = 0; k < air_slices; k++) {
        ivec2 t = air_texel(c, k);
        vec4 cell = imageLoad(colorimg10, t);
        imageStore(colorimg11, t, cell);
        float far_end = air_depth_of(float(k + 1));
        float len = (far_end - near) * stretch;
        float extinction = cell.a * 0.001;
        float step_through = exp(-extinction * len);
        // The light scattered along the cell, fading as it goes (Frostbite's energy-conserving integration).
        light += through * (extinction > 1e-8 ? cell.rgb * ((1.0 - step_through) / extinction) : cell.rgb * len);
        through *= step_through;
        imageStore(colorimg10, t, vec4(light, through));
        near = far_end;
    }
}

#endif
#endif

// How much of the sky a point sees past the blocks around it, in the lit overworld with shadows. Minecraft's sky light
// only says how far a point is from a column of air open to the sky, not how high what covers it is: under a floating
// island, a high bridge or a tall hall's roof it goes as dark as in a cave, though the sky's light comes in under
// them from all around. Unreal lights its sky the same way this does: rays (its cones) from each pixel through a copy
// of the world around the camera (its distance fields), finer near the pixel and coarser further out.
//
// The shadow pass marks every solid block, every leaf block and the water within 128 blocks of you across, from 64
// below you to 191 above (shadow.vsh): a bit for each block of its column, 32 to a word, in the rows of
// cg_water_marks_img below the ripples' marks. Under water a surface keeps Minecraft's sky light, which dims a level
// for each block of water above it as the water dims the sky's light (the lighting dims it its own way where it knows
// how deep the water is, see light_surface). Glass, plants and things that aren't blocks let the sky through. The pass
// after it (sky_grid.csh) makes six coarser copies, each column of one marking what any of 2 by 2 of the one before
// has, for a ray's longer steps further out. A ray towards the sky is open if it leaves the grid without passing a
// marked block: every block in a column cell between the heights a step goes through counts, so a step never jumps a
// roof.
//
// The lighting takes the more of Minecraft's sky light and of the sky this sees (see sky_seen_level), so a point is
// never darker than Minecraft's light makes it, and a closed room stays dark: its walls and roof stop every ray.

#if !defined LIB_SKY_GRID
#define LIB_SKY_GRID

#include "/lib/settings.glsl"

#ifdef LIT_SKY_GRID

const int sky_grid_size = 256;  // columns across, a block each
const int sky_grid_below = 64;  // blocks below you the grid begins
const int sky_grid_height = 256; // blocks up
const int sky_grid_words = 8;   // words of 32 blocks to a column
const int sky_grid_levels = 7;  // the grid and its coarser copies (2, 4, ... 64 blocks to a column)
const int sky_grid_width = 512; // cg_water_marks_img's width
// How far across from you (blocks, along x or z) the grid's rays are traced, short of its edge: they fade into the
// screen's probe over the last 28 (see sky_probe.glsl).
const float sky_grid_reach = 124.0;

// Where each copy's rows begin in the image: its 8 words side by side, as many to a row as fit.
int sky_grid_level_row(int level) {
    switch (level) {
        case 0: return 1536;
        case 1: return 2560;
        case 2: return 2816;
        case 3: return 2880;
        case 4: return 2912;
        case 5: return 2928;
        default: return 2936;
    }
}

// The texel holding one word of one column of a copy.
ivec2 sky_grid_texel(ivec2 column, int word, int level) {
    int size = sky_grid_size >> level;
    int per_row = sky_grid_width / size;
    return ivec2((word % per_row) * size + column.x, sky_grid_level_row(level) + (word / per_row) * size + column.y);
}

// A point's place in the grid (blocks from its lowest corner), from where it is in scene space.
vec3 sky_grid_pos(vec3 scene_pos, vec3 camera_fract) {
    return scene_pos + camera_fract + vec3(float(sky_grid_size / 2), float(sky_grid_below), float(sky_grid_size / 2));
}

#if !defined PROGRAM_SHADOW && !defined PROGRAM_SKY_GRID

#ifndef CG_WATER_MARKS_TEX
#define CG_WATER_MARKS_TEX
uniform usampler2D cg_water_marks_tex; // cg_water_marks_img, for programs without image access
#endif

// Whether a copy's column cell has a block anywhere from height y0 to y1 (y0 <= y1, grid blocks).
bool sky_grid_marked(ivec2 cell, int level, int y0, int y1) {
    y0 = max(y0, 0);
    y1 = min(y1, sky_grid_height - 1);
    for (int word = y0 >> 5; word <= (y1 >> 5); word++) {
        int lo = max(y0 - word * 32, 0);
        int hi = min(y1 - word * 32, 31);
        uint mask = (hi == 31 ? 0xffffffffu : (1u << uint(hi + 1)) - 1u) & ~((1u << uint(lo)) - 1u);
        if ((texelFetch(cg_water_marks_tex, sky_grid_texel(cell, word, level), 0).r & mask) != 0u) {
            return true;
        }
    }
    return false;
}

// Whether a ray from o (grid space) along d (pointing up) gets out to the sky: a block at a time for the first 4 blocks,
// then twice as far each time in cells as wide as the step (2 blocks to 4 blocks out, 4 to 8, ... 64 to 192).
// In the Nether, whose roof is everywhere, open means it gets nether_open_reach blocks away.
const float nether_open_reach = 32.0;

bool sky_ray_open(vec3 o, vec3 d) {
    vec3 a = o;
    float t = 0.0;
    for (int i = 0; i < 16; i++) {
        int level = i < 4 ? 0 : min((i - 2) >> 1, sky_grid_levels - 1);
#ifdef WORLD_NETHER
        if (t >= nether_open_reach) {
            return true;
        }
#endif
        t += float(1 << level);
        vec3 b = o + d * t;
        if (b.y >= float(sky_grid_height) || any(lessThan(b.xz, vec2(0.0)))
                || any(greaterThanEqual(b.xz, vec2(float(sky_grid_size))))) {
            return true; // out of the grid: nothing marked over it
        }
        // The step goes through the cells its ends are in (a step is never longer than a cell is wide), all of
        // them between the heights it goes through.
        int y0 = int(floor(min(a.y, b.y)));
        int y1 = int(floor(max(a.y, b.y)));
        if (i == 0) {
            y0 = max(y0, int(floor(o.y)) + 1); // not the block the ray starts in (a carpet, a snow layer)
        }
        ivec2 cell_a = ivec2(floor(a.xz)) >> level;
        ivec2 cell_b = ivec2(floor(b.xz)) >> level;
        if (sky_grid_marked(cell_b, level, y0, y1) || (cell_a != cell_b && sky_grid_marked(cell_a, level, y0, y1))) {
            return false;
        }
        a = b;
    }
    return true;
}

// The share of the sky a surface at scene_pos facing n sees past the blocks around it (the sky above the horizon,
// weighted as the light from it falls on the surface), from rays turned by noise (0..1, different each pixel and
// frame: the upscaler evens it out). -1 where the grid doesn't reach, or the surface faces too far down to see sky.
float sky_seen(vec3 scene_pos, vec3 n, float noise) {
    vec3 o = sky_grid_pos(scene_pos + n * 0.55, cameraPositionFract);
    if (any(lessThan(o, vec3(0.0))) || any(greaterThanEqual(o, vec3(float(sky_grid_size), float(sky_grid_height),
            float(sky_grid_size))))) {
        return -1.0;
    }
    vec3 t = normalize(abs(n.y) < 0.99 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(n, t);
    float open = 0.0;
    float rays = 0.0;
    for (int i = 0; i < SKY_RAYS; i++) {
        // Spread evenly over the hemisphere as its light falls (more near the normal), turned by the noise.
        float u = (float(i) + 0.5) / float(SKY_RAYS);
        float r = sqrt(u);
        float phi = 6.2831853 * (float(i) * 0.618034 + noise);
        vec3 d = t * (r * cos(phi)) + b * (r * sin(phi)) + n * sqrt(1.0 - u);
        if (d.y < 0.05) {
            continue; // below the horizon: the ground's light, not the sky's
        }
        rays += 1.0;
        open += sky_ray_open(o, d) ? 1.0 : 0.0;
    }
    return rays > 0.0 ? open / rays : -1.0;
}

#endif
#endif

// The sky light level (0..1) at which Minecraft's sky light gives as much of the sky's light as a surface seeing this
// share of the sky gets (see sky_light_curve), so the sky it sees can stand in for it.
float sky_seen_level(float seen) {
    float c = pow(clamp(seen, 0.0, 1.0), 1.0 / SKY_FALLOFF);
    return 4.0 * c / (1.0 + 3.0 * c);
}

#endif

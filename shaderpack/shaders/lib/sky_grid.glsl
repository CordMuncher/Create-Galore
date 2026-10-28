// The blocks around you that the sky's light can't get through, in the lit overworld with shadows, for rays towards
// the sky: the sky visibility's from each block of the light volume (sky_visibility.csh) and the light in the air's
// (air_volume.csh). Minecraft's sky light only says how far a point is from a column of air open to the sky, not how
// high what covers it is: under a floating island, a high bridge or a tall hall's roof it goes as dark as in a cave,
// though the sky's light comes in under them from all around. Unreal lights its sky the same way: rays (its cones)
// through a copy of the world around the camera (its distance fields), finer near where they start and coarser
// further out.
//
// The shadow pass marks every solid block, every leaf block and the water within 128 blocks of you across, from 64
// below you to 191 above (shadow.vsh): a bit for each block of its column, 32 to a word, in the rows of
// cg_water_marks_img below the ripples' marks. Glass, plants and things that aren't blocks let the sky through. It
// sees a block only by its faces, and Minecraft draws none between two solid blocks, so it also marks which of them
// have a face up and which a face down, and the pass after it fills in the inside of the ground from those
// (sky_fill.csh): rock all the way through, not a shell a block thick around each cave. The passes after that
// (sky_grid.csh) make six coarser copies, each column of one marking what any of 2 by 2 of the one before has, for a
// ray's longer steps further out. A ray towards the sky is open if it leaves the grid without
// passing a marked block: every block in a column cell between the heights a step goes through counts, so a step never
// jumps a roof.

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
// How far across from you (blocks, along x or z) the grid's rays are traced, short of its edge.
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

// The texel holding one word of one column of the blocks with a face up (up) or a face down, laid out as the grid.
ivec2 sky_grid_face_texel(ivec2 column, int word, bool up) {
    return sky_grid_texel(column, word, 0) + ivec2(0, (up ? 3452 : 4476) - sky_grid_level_row(0));
}

// A point's place in the grid (blocks from its lowest corner), from where it is in scene space.
vec3 sky_grid_pos(vec3 scene_pos, vec3 camera_fract) {
    return scene_pos + camera_fract + vec3(float(sky_grid_size / 2), float(sky_grid_below), float(sky_grid_size / 2));
}

#if !defined PROGRAM_SHADOW && !defined PROGRAM_SKY_GRID

#include "/lib/marks.glsl"

// Whether a copy's column cell has a block anywhere from height y0 to y1 (y0 <= y1, grid blocks).
bool sky_grid_marked(ivec2 cell, int level, int y0, int y1) {
    y0 = max(y0, 0);
    y1 = min(y1, sky_grid_height - 1);
    for (int word = y0 >> 5; word <= (y1 >> 5); word++) {
        int lo = max(y0 - word * 32, 0);
        int hi = min(y1 - word * 32, 31);
        uint mask = (hi == 31 ? 0xffffffffu : (1u << uint(hi + 1)) - 1u) & ~((1u << uint(lo)) - 1u);
        if ((water_marks(sky_grid_texel(cell, word, level)) & mask) != 0u) {
            return true;
        }
    }
    return false;
}

// Whether a ray from o (grid space) along d (pointing up) gets out to the sky: a block at a time for the first fine
// blocks, then twice as far each time in cells as wide as the step (two steps of 2 blocks, two of 4, ... two of 64).
// A coarse cell counts every block in it, so past the fine steps a ray only gets through an opening as wide as the
// cell: more fine steps for a ray that has to find a window across a room. In the Nether, whose roof is everywhere,
// open means it gets nether_open_reach blocks away.
const float nether_open_reach = 32.0;

bool sky_ray_open(vec3 o, vec3 d, int fine) {
    vec3 a = o;
    float t = 0.0;
    for (int i = 0; i < fine + 12; i++) {
        int level = i < fine ? 0 : min((i - fine + 2) >> 1, sky_grid_levels - 1);
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

bool sky_ray_open(vec3 o, vec3 d) {
    return sky_ray_open(o, d, 4);
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

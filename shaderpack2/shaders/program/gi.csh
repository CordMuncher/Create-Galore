// The bounce light, per block: one value per cell of the voxel box, kept from frame to frame (cg_gi_img, with how
// many rays it holds alongside). What makes a red floor tint the walls, a lit room spill light through its
// door, a lava lake light its cave's ceiling, and deep rooms darker than the sky light alone would say.
//
// Each frame, for each cell in view (and a little around it):
// - solid cells hold nothing;
// - cells in the open (more than a block from anything, not see-through) take the average of their six neighbours,
//   which carries the bounce light into the air without tracing;
// - cells by a surface send GI_RAYS rays over the surface's half of the sphere, up to 30 blocks through the distance
//   field. A ray that hits takes what lights the spot it hit (the block light there, its own bounce light, the sun
//   through the real shadow map) times that block's colour; and some sky, the more the further it got. A ray that
//   gets away brings the sky. The sky is scaled by the sky light the cell's neighbours saw.
// The new rays join the old ones, which fade (by about 1% per frame at 60 frames a second), so the light settles
// over a second or two and holds several bounces (each hit reads the bounce light already there). More than
// far_tiles blocks from you, half the tiles trace each frame (a checkerboard): the light there settles a little
// slower, where nobody looks closely, and most of the box is that far.
//
// OWN DENOISER (a world-space store that gathers rays over frames): a light cache rather than a screen filter, so it
// can likely stay under Ray Reconstruction (see the README's "Own denoisers and Ray Reconstruction").

#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
#include "/lib/view.glsl"
#include "/lib/globals.glsl"
#include "/lib/shadow.glsl"

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
const ivec3 workGroups = ivec3(VOX_GROUPS_X, VOX_GROUPS_Y, VOX_GROUPS_X);

uniform int frameCounter;
uniform float frameTime;
uniform ivec2 eyeBrightnessSmooth;

layout(rgba16f) readonly uniform image3D cg_blk_img;
layout(rgba16f) uniform image3D cg_gi_img; // the light, then (the second half along x) how many rays it holds

const ivec3 gi_count_offset = ivec3(VOX_X, 0, 0);

shared uint s_active;
shared uint s_trace_count;
shared uint s_trace[512];

const float gi_reach = 30.0;
const float gi_memory = 700.0; // rays a cell holds at most
const float far_tiles = 24.0;

bool in_view(vec3 centre, float radius, float margin) {
    vec3 view = (gbufferModelView * vec4(centre - cameraPositionFract, 1.0)).xyz;
    mat4 m = gbufferProjection;
    vec4 row0 = vec4(m[0][0], m[1][0], m[2][0], m[3][0]);
    vec4 row1 = vec4(m[0][1], m[1][1], m[2][1], m[3][1]);
    vec4 row2 = vec4(m[0][2], m[1][2], m[2][2], m[3][2]);
    vec4 row3 = vec4(m[0][3], m[1][3], m[2][3], m[3][3]);
    vec4 planes[5] = vec4[](row3 + row0, row3 - row0, row3 + row1, row3 - row1, row3 + row2);
    for (int i = 0; i < 5; i++) {
        float len = length(planes[i].xyz);
        if (dot(planes[i].xyz, view) + planes[i].w < -(radius + margin) * len) {
            return false;
        }
    }
    return true;
}

// The sky's light from a direction, on the scale of the light on a surface.
vec3 sky_from(vec3 dir) {
    return dir.y >= 0.0 ? mix(g_sky_side.rgb, g_sky_up.rgb, dir.y) : mix(g_sky_side.rgb, g_sky_down.rgb, -dir.y);
}

// The sun (or moon) on a spot: one hardware look-up in the shadow map.
float sun_reaches(vec3 vox_point, vec3 normal) {
#if defined SUNLIGHT || defined WORLD_END
    vec3 scene_pos = vox_point - cameraPositionFract + normal * 0.05;
    vec4 c = shadow_coord(scene_pos);
    if (any(lessThan(c.xy, vec2(0.0))) || any(greaterThan(c.xy, vec2(1.0)))) {
        return 1.0;
    }
    return texture(shadowtex1HW, vec3(c.xy, c.z - 0.0005));
#else
    return 0.0;
#endif
}

vec3 load_light(ivec3 cell) {
    if (!vox_in_box(cell, 0)) {
        return vec3(0.0);
    }
    return expand_light(imageLoad(cg_blk_img, vox_slot(cell, 0)).rgb);
}

vec4 load_gi(ivec3 cell) {
    if (!vox_in_box(cell, 0)) {
        return vec4(0.0);
    }
    return imageLoad(cg_gi_img, vox_slot(cell, 0));
}

vec3 trace_cell(ivec3 cell, ivec3 slot) {
    vec3 normal = vox_normal(cell);
    bool has_normal = dot(normal, normal) > 0.5;
    vec3 p = vec3(cell) + 0.5 - normal * 0.3;

    // The sky its neighbours' faces saw, nudged up as you yourself see more of it.
    uint seen = 0u;
    for (int i = 0; i < 6; i++) {
        ivec3 o = ivec3(i == 0 ? 1 : i == 1 ? -1 : 0, i == 2 ? 1 : i == 3 ? -1 : 0, i == 4 ? 1 : i == 5 ? -1 : 0);
        if (vox_in_box(cell + o, 0)) {
            seen |= vox_word(cell + o);
        }
    }
#if defined NO_SKY_LIGHT
    float sky = 1.0; // the Nether's and the End's air light is everywhere; what the rays find shades it
#else
    float sky = vox_sky(seen);
    sky = mix(sky, 1.0, 0.4 * float(eyeBrightnessSmooth.y) / 240.0);
#endif

    vec3 sum = vec3(0.0);
    for (int r = 0; r < GI_RAYS; r++) {
        vec3 u = hash33(uvec3(slot) + uvec3(uint(r) * 97u, uint(frameCounter) * 131u, uint(r) * 7u + uint(frameCounter) * 3u));
        vec3 dir;
        if (has_normal) {
            dir = cosine_direction(normal, u.xy);
        } else {
            float z = u.x * 2.0 - 1.0;
            float a = TAU * u.y;
            dir = vec3(sqrt(1.0 - z * z) * vec2(cos(a), sin(a)), z);
        }
        Hit h = trace_ray(p, dir, gi_reach, 70);
        vec3 from_sky = sky_from(dir) * sky;
        if (h.hit) {
            // The face it went in by: the axis whose cell wall the hit is nearest.
            float cell_size = exp2(float(-h.level));
            vec3 f = fract(h.pos / cell_size);
            vec3 wall = min(f, 1.0 - f);
            int axis = wall.x < wall.y && wall.x < wall.z ? 0 : wall.y < wall.z ? 1 : 2;
            vec3 hit_normal = vec3(0.0);
            hit_normal[axis] = -sign(dir[axis]);
            ivec3 block = ivec3(floor(h.pos - hit_normal * 0.01));
            ivec3 front = ivec3(floor(h.pos + hit_normal * 0.5));
            vec4 color = vox_color(block);
            vec4 gi_there = load_gi(front);
            vec3 lit = load_light(front) + gi_there.rgb * (gi_there.a > 0.0 ? 1.0 : 0.0);
            float sun_facing = dot(hit_normal, g_light_dir.xyz);
            if (sun_facing > 0.0) {
                lit += g_light_color.rgb * sun_facing * sun_reaches(h.pos, hit_normal);
            }
            sum += color.rgb * lit + from_sky * sqr(h.t / gi_reach);
        } else {
            sum += from_sky;
        }
    }
    return sum / float(GI_RAYS);
}

void main() {
    ivec3 tile = ivec3(gl_WorkGroupID);
    ivec3 tile_min = tile * 8 - vox_half;
    ivec3 cell = tile_min + ivec3(gl_LocalInvocationID);
    uint index = gl_LocalInvocationIndex;
    ivec3 slot = vox_slot(cell, 0);

    if (index == 0u) {
        s_active = in_view(vec3(tile_min) + 4.0, 7.0, 10.0) ? 1u : 0u;
        s_trace_count = 0u;
    }
    barrier();
    bool fresh = !vox_was_in_box(cell, 0);
    if (fresh) {
        imageStore(cg_gi_img, slot + gi_count_offset, vec4(0.0));
        imageStore(cg_gi_img, slot, vec4(0.0));
    }
    if (s_active == 0u) {
        return;
    }

    uint word = vox_word(cell);
    if ((word & vox_solid_bit(0)) != 0u) {
        imageStore(cg_gi_img, slot, vec4(0.0));
        imageStore(cg_gi_img, slot + gi_count_offset, vec4(0.0));
    } else if (sdf_at(cell, 0) >= 2u && (word & vox_see_bit(0)) == 0u) {
        // In the open: the average of the neighbours.
        vec4 sum = vec4(0.0);
        for (int i = 0; i < 6; i++) {
            ivec3 o = ivec3(i == 0 ? 1 : i == 1 ? -1 : 0, i == 2 ? 1 : i == 3 ? -1 : 0, i == 4 ? 1 : i == 5 ? -1 : 0);
            vec4 g = load_gi(cell + o);
            sum += vec4(g.rgb * g.a, g.a);
        }
        if (sum.a > 0.0) {
            imageStore(cg_gi_img, slot, vec4(sum.rgb / sum.a, 1.0));
        }
    } else if (length(vec3(tile_min) + 4.0) < far_tiles || (tile.x + tile.y + tile.z + frameCounter) % 2 == 0) {
        s_trace[atomicAdd(s_trace_count, 1u)] = index;
    }
    barrier();

    // The cells by a surface, packed together so the tracing threads run side by side.
    if (index < s_trace_count) {
        uint i = s_trace[index];
        ivec3 c = tile_min + ivec3(int(i % 8u), int((i / 8u) % 8u), int(i / 64u));
        ivec3 s = vox_slot(c, 0);
        vec3 fresh_light = trace_cell(c, s);
        vec4 old = imageLoad(cg_gi_img, s);
        float count = old.a > 0.0 ? imageLoad(cg_gi_img, s + gi_count_offset).r * pow(0.99, frameTime * 60.0) : 0.0;
        vec3 average = (old.rgb * count + fresh_light * float(GI_RAYS)) / (count + float(GI_RAYS));
        imageStore(cg_gi_img, s, vec4(average, 1.0));
        imageStore(cg_gi_img, s + gi_count_offset, vec4(min(count + float(GI_RAYS), gi_memory)));
    }
}

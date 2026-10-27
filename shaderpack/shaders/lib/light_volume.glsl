// Coloured light and bounce light, in the lit overworld (see settings.glsl): a volume of blocks centred on you that
// the block lights' colours and the sunlight bouncing off the world spread through.
//
// The shadow pass marks what each block is (shadow.vsh): a light, with its light level and a colour read from its
// own texture (so every modded light has one without any list of blocks); a solid cube, which stops the light; or
// see-through (stained glass, ice), which tints it. Iris clears those marks every frame. The light flood
// (light_flood.csh) then spreads each light one block a frame out of last frame's volume, losing a light level per
// block as Minecraft's light does and keeping its colour, so lights side by side mix their colours where they meet.
//
// The volume only gives the light's colour; how bright it is stays Minecraft's light level, so the light never
// reaches further than Minecraft's does and never leaks where Minecraft's doesn't. Past the volume, or where a light's
// blocks aren't drawn, the light keeps the pack's torchlight colour.
//
// The bounce light: the shadow map also keeps the colour and the facing of every surface the sun (or the moon) lights
// (shadow.fsh), and light_bounce.csh adds the light each one reflects into the blocks in front of it. The flood
// spreads it through the volume as well, so it stops at solid blocks, dims through leaves, and lights a room from its
// sunlit floor in the floor's colour. It keeps apart the light by which way the surface it last left faces (up, down
// and the four sides): a surface takes the light of every facing but its own, since no surface can light another
// that faces the same way (a sunlit floor doesn't light itself or the floor beside it). Each solid block next to it
// sends back what reaches it as light of its own facing. Nothing in either is random, so the upscaler sees the same
// light every frame.

#if !defined LIB_LIGHT_VOLUME
#define LIB_LIGHT_VOLUME

#include "/lib/settings.glsl"

// Torchlight: a warm 3000 K or so, luminance 1. The colour of every light without coloured light, and of the warm ones
// (torches, lanterns, glowstone, fire) with it.
const vec3 torch_color = vec3(1.453, 0.916, 0.494);

#ifdef LIT_VOXELS

// The volume's size in blocks (x and z across, y up): LIGHT_RANGE across, half of it up.
const ivec3 light_volume_size = ivec3(LIGHT_RANGE, LIGHT_RANGE / 2, LIGHT_RANGE);

// Both images hold several volumes (sections) one above another along y. The light image holds two of each along z,
// taking turns: one written each frame while the other, last frame's, is read.
//
// The marks image: what each block is, the light it gives, and (with bounce light) what the sunlit surfaces around it
// reflect into it, summed in fixed point (bounce_scale is 1), two sums to a texel: the light's red, green and blue,
// then how much of it each facing sent (luminance), in the order of facing_dir.
const int section_marks = 0;
const int section_lights = 1;
const int section_bounce_added = 2;
const float bounce_scale = 2048.0;
// The light image: the coloured light; then (with bounce light) the bounce light's colour (from every facing, for its
// colour only), and each facing's (luminance), three to a texel; last the trees' crowns (see canopy_shape).
const int section_color = 0;
const int section_bounce = 1;
const int section_bounce_facing = 2;
#ifdef LIT_GI
const int marks_sections = 7;
const int light_sections = 5;
const int section_canopy = 4;
#else
const int marks_sections = 2;
const int light_sections = 2;
const int section_canopy = 1;
#endif

ivec3 marks_texel(ivec3 block, int section) {
    return block + ivec3(0, section * light_volume_size.y, 0);
}

ivec3 light_texel(ivec3 block, int section, int turn) {
    return block + ivec3(0, section * light_volume_size.y, turn * light_volume_size.z);
}

// What a block is, in the marks' first section (each bit set by any of its vertices, see shadow.vsh).
const uint mark_solid = 1u;      // a vertex of a solid block at one of the block's corners
const uint mark_partial = 2u;    // a vertex of a solid block inside the block's bounds (a slab, a stair, a fence)
const uint mark_tinted = 4u;     // see-through, tinting what passes; bits 8.. the tint (7 bits each, red first)
const uint mark_foliage = 8u;    // leaves, which the bounce light only partly gets through

// The bounce light's facings: +x, -x, +y, -y, +z, -z (each one's opposite is facing ^ 1).
vec3 facing_dir(int facing) {
    vec3 d = vec3(0.0);
    d[facing >> 1] = (facing & 1) == 0 ? 1.0 : -1.0;
    return d;
}

// The facing nearest a normal's.
int facing_of(vec3 n) {
    vec3 a = abs(n);
    int axis = a.x > a.y && a.x > a.z ? 0 : (a.y > a.z ? 1 : 2);
    return axis * 2 + (n[axis] < 0.0 ? 1 : 0);
}

const vec3 bounce_luma = vec3(0.2126, 0.7152, 0.0722);
// The marks' bounce sums: 0 to 2 the red, green and blue, 3 + facing that facing's (see section_bounce_added).
ivec3 bounce_added_texel(ivec3 block, int sum) {
    return marks_texel(block, section_bounce_added + sum / 2);
}
uint bounce_added_bits(int sum, float amount) {
    return uint(amount * bounce_scale + 0.5) << (16u * uint(sum & 1));
}
float bounce_added_value(uint texel, int sum) {
    return float((texel >> (16u * uint(sum & 1))) & 0xFFFFu) / bounce_scale;
}

// The light spreading through the open: each block passes on all it takes from its neighbours (it only fades as it
// spreads out); through leaves, half. What a solid block next to it sends back of what reaches it (a middling surface,
// between dark stone and white concrete, as the flood carries it): the bounces after the first, which fill a room from
// its sunlit patch.
const float bounce_pass = 1.0;
const float bounce_foliage_pass = 0.5;
const float bounce_wall_reflect = 0.85;
// The surfaces put their light 1.5 blocks in front of them (half a block where there's no room), and a surface reads
// it 1 block in front: as near as the flood's light comes to what the surfaces really send each other (checked
// against radiosity in rooms lit by a patch of floor or a wall), to within about 2 times. Its scale to irradiance:
const float bounce_add_offset = 1.5;
const float bounce_read_offset = 1.0;
const float bounce_peak = 2.7;

// The volume's block at a position relative to the camera (scene space): blocks counted from the volume's corner.
ivec3 light_volume_block(vec3 scene_pos, vec3 camera_fract) {
    return ivec3(floor(scene_pos + camera_fract)) + light_volume_size / 2;
}

bool in_light_volume(ivec3 block) {
    return all(greaterThanEqual(block, ivec3(0))) && all(lessThan(block, light_volume_size));
}

// Whether a block's marks make it stop the light: a solid cube, not a slab or a stair.
bool marks_solid(uint marks) {
    return (marks & (mark_solid | mark_partial)) == mark_solid;
}

// A light's mark: its level in the top bits, so that of the marks its vertices leave the brightest wins (the order
// they come in doesn't matter), and its colour (8 bits each).
uint light_mark(float level, vec3 color) {
    uvec3 c = uvec3(clamp(color, 0.0, 1.0) * 255.0 + 0.5);
    return (uint(clamp(level, 0.0, 15.0)) << 24u) | (c.r << 16u) | (c.g << 8u) | c.b;
}

// A light's light at its own block from its mark: its colour (brightest channel 1) times its level (0..1).
vec3 light_from_mark(uint mark) {
    float level = float(mark >> 24u) / 15.0;
    vec3 color = vec3(uvec3(mark >> 16u, mark >> 8u, mark) & 0xFFu) / 255.0;
    return color / max(max(color.r, max(color.g, color.b)), 1e-3) * level;
}

uint tint_mark(vec3 tint) {
    uvec3 c = uvec3(clamp(tint, 0.0, 1.0) * 127.0 + 0.5);
    return mark_tinted | (c.r << 8u) | (c.g << 15u) | (c.b << 22u);
}

vec3 tint_from_mark(uint mark) {
    return vec3(uvec3(mark >> 8u, mark >> 15u, mark >> 22u) & 0x7Fu) / 127.0;
}

#endif

#if !defined PROGRAM_SHADOW && !defined PROGRAM_LIGHT_FLOOD && !defined PROGRAM_LIGHT_BOUNCE

#ifdef LIT_VOXELS
#include "/lib/motion.glsl"

uniform sampler3D cg_light_tex;

// Where the light on a surface at scene_pos facing normal is in the volume: offset blocks in front of the surface (at,
// in blocks from the volume's corner); and how much of it to take, fading out towards the volume's edges so the
// change past them isn't seen as a line (0 past them).
float light_volume_at(vec3 scene_pos, vec3 normal, float offset, out vec3 at) {
    at = scene_pos + normal * offset + cameraPositionFract + vec3(light_volume_size / 2);
    vec3 edge = min(at, vec3(light_volume_size) - at);
    return smoothstep(1.0, 8.0, min(edge.x, min(edge.y, edge.z)));
}

// This frame's light (the flood wrote it after the shadow pass) of one of the light image's sections at a point inside
// the volume, between its blocks' centres.
vec3 light_volume_sample(vec3 at, int section) {
    vec3 p = at + vec3(0.0, float(section * light_volume_size.y), float((frameCounter & 1) * light_volume_size.z));
    return texture(cg_light_tex, p / vec3(light_volume_size * ivec3(1, light_sections, 2))).rgb;
}

// The shape of the tree crown a leaf is part of, the way stylised trees are shaded from a smooth shape around their
// leaves rather than from each leaf's own: from how many leaves there are around each block (light_flood.csh),
// smoothed over about one block (r) and about two (g). xyz: the way out of the crown (not normalised; about 0.5 long
// at the crown's edge, 0 deep inside it or where there is no crown), w: how deep in it the point is (0 out in the
// open, 0.5 at the crown's edge, 1 deep inside).
vec4 canopy_shape(vec3 scene_pos) {
    vec3 at;
    float fade = light_volume_at(scene_pos, vec3(0.0), 0.0, at);
    if (fade <= 0.0) {
        return vec4(0.0);
    }
    vec2 x = light_volume_sample(at + vec3(1.0, 0.0, 0.0), section_canopy).rg
            - light_volume_sample(at - vec3(1.0, 0.0, 0.0), section_canopy).rg;
    vec2 y = light_volume_sample(at + vec3(0.0, 1.0, 0.0), section_canopy).rg
            - light_volume_sample(at - vec3(0.0, 1.0, 0.0), section_canopy).rg;
    vec2 z = light_volume_sample(at + vec3(0.0, 0.0, 1.0), section_canopy).rg
            - light_volume_sample(at - vec3(0.0, 0.0, 1.0), section_canopy).rg;
    vec3 out_of = -(vec3(x.g, y.g, z.g) + 0.5 * vec3(x.r, y.r, z.r));
    return vec4(out_of, light_volume_sample(at, section_canopy).g) * fade;
}
#endif

#ifdef LIT_COLORED_LIGHT
// The block light's colour for the volume's light (rgb, any brightness): as bright as torchlight in all (red, green
// and blue summed), so a light level means what it means elsewhere; with LIGHT_STYLE, brought towards torchlight's
// brightness to the eye (blue the most, as it looks dimmest).
vec3 block_light_tint(vec3 light) {
    const vec3 torch = torch_color;
    vec3 colored = light * ((torch.r + torch.g + torch.b) / max(light.r + light.g + light.b, 1e-6));
    const vec3 luma = vec3(0.2126, 0.7152, 0.0722);
    return colored * pow(dot(torch, luma) / max(dot(colored, luma), 0.05), 0.6 * LIGHT_STYLE);
}

// The colour of the block light on a surface at scene_pos facing normal: the volume's light in the block in front
// of the surface, as far as the volume has any light there (and COLORED_LIGHT_SATURATION says, deeper with
// LIGHT_STYLE), else torchlight (see block_light_tint).
vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    const vec3 torch = torch_color;
    vec3 at;
    float fade = light_volume_at(scene_pos, normal, 0.5, at);
    if (fade <= 0.0) {
        return torch;
    }
    vec3 light = light_volume_sample(at, section_color);
    float peak = max(light.r, max(light.g, light.b));
    if (peak <= 1e-4) {
        return torch;
    }
    float weight = smoothstep(0.0, 2.0 / 15.0, peak) * fade * COLORED_LIGHT_SATURATION * (1.0 + 0.4 * LIGHT_STYLE);
    return max(mix(torch, block_light_tint(light), weight), 0.0);
}
#else
vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    return torch_color;
}
#endif

#ifdef LIT_GI
// The sunlight (or moonlight) bouncing off the world onto a surface at scene_pos facing normal, as a share of the
// light's own (times g_light_color it is irradiance): the volume's bounce light in front of it, of every facing but
// the surface's own (one facing a little aslant takes a little of its nearest facings'). sky: the surface's sky light
// (0..1). Where a surface sees the open sky, the sky's light already holds the ground's bounce (see
// frame_globals.csh), so this adds little there.
vec3 bounce_light(vec3 scene_pos, vec3 normal, float sky) {
    vec3 at;
    float fade = light_volume_at(scene_pos, normal, bounce_read_offset, at);
    if (fade <= 0.0) {
        return vec3(0.0);
    }
    vec3 color = light_volume_sample(at, section_bounce);
    vec3 facing_a = light_volume_sample(at, section_bounce_facing);
    vec3 facing_b = light_volume_sample(at, section_bounce_facing + 1);
    float received = dot(facing_a, 1.0 - max(vec3(normal.x, -normal.x, normal.y), 0.0))
            + dot(facing_b, 1.0 - max(vec3(-normal.y, normal.z, -normal.z), 0.0));
    float luma = dot(color, bounce_luma);
    if (received <= 0.0 || luma <= 0.0) {
        return vec3(0.0);
    }
#ifdef NO_SKY_LIGHT
    float open_sky = 0.0; // the End's void glow holds no light bounced off the ground
#else
    float open_sky = smoothstep(11.0 / 15.0, 1.0, sky);
#endif
    return color * (received / luma * fade * (1.0 - 0.9 * open_sky) * GI_STRENGTH / bounce_peak);
}
#endif

#endif

#endif

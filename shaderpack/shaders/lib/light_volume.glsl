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
// spreads it through the volume as well, each block passing on most of what it takes from its neighbours, so it fades
// within a few blocks, stops at solid blocks, dims through leaves, and lights a room from its sunlit floor in the
// floor's colour. Nothing in either is random, so the upscaler sees the same light every frame.

#if !defined LIB_LIGHT_VOLUME
#define LIB_LIGHT_VOLUME

#include "/lib/settings.glsl"

// Torchlight: a warm 3000 K or so, luminance 1. The colour of every light without coloured light, and of the warm ones
// (torches, lanterns, glowstone, fire) with it.
const vec3 torch_color = vec3(1.453, 0.916, 0.494);

#ifdef LIT_VOXELS

// The volume's size in blocks (x and z across, y up): LIGHT_RANGE across, half of it up.
const ivec3 light_volume_size = ivec3(LIGHT_RANGE, LIGHT_RANGE / 2, LIGHT_RANGE);
// Both images hold several volumes one after another along z (sections), each light_volume_depth deep.
const int light_volume_depth = LIGHT_RANGE;

// The marks image: what each block is, the light it gives, and (with bounce light) the sunlight its neighbours'
// surfaces reflect into it, red, green and blue in three sections, in fixed point (bounce_scale is 1).
const int section_marks = 0;
const int section_lights = 1;
const int section_bounce_added = 2;
const float bounce_scale = 4096.0;
// The light image: the coloured light twice, then (with bounce light) the bounce light twice, each taking turns (one
// written each frame while the other is read).
const int section_color = 0;
const int section_bounce = 2;
#ifdef LIT_GI
const int light_sections = 4;
#else
const int light_sections = 2;
#endif

// What a block is, in the marks' first section (each bit set by any of its vertices, see shadow.vsh).
const uint mark_solid = 1u;      // a vertex of a solid block at one of the block's corners
const uint mark_partial = 2u;    // a vertex of a solid block inside the block's bounds (a slab, a stair, a fence)
const uint mark_tinted = 4u;     // see-through, tinting what passes; bits 8.. the tint (7 bits each, red first)
const uint mark_foliage = 8u;    // leaves, which the bounce light only partly gets through

// Of the bounce light a block takes from its neighbours, how much it passes on (the rest is lost, as light is to the
// surfaces around it that aren't in the volume): it falls to a third about 2.5 blocks from where it bounced. Through
// leaves, half of that.
const float bounce_pass = 0.97;
const float bounce_foliage_pass = 0.5;
// A sunlit floor reflecting some light into the block over it leaves about 4 times that there, once the flood has
// settled (4.03 for bounce_pass 0.97); a surface right over the floor sees nothing but floor and gets all of it once.
const float bounce_peak = 4.0;

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

// Where the light on a surface at scene_pos facing normal is in the volume: the block in front of the surface (at,
// in blocks from the volume's corner); and how much of it to take, fading out towards the volume's edges so the
// change past them isn't seen as a line (0 past them).
float light_volume_at(vec3 scene_pos, vec3 normal, out vec3 at) {
    at = scene_pos + normal * 0.5 + cameraPositionFract + vec3(light_volume_size / 2);
    vec3 edge = min(at, vec3(light_volume_size) - at);
    return smoothstep(1.0, 8.0, min(edge.x, min(edge.y, edge.z)));
}

// This frame's light (the flood wrote it after the shadow pass) of one of the volume's sections (section_color,
// section_bounce) at a point inside the volume, between its blocks' centres.
vec3 light_volume_sample(vec3 at, int section) {
    float z = at.z + float((section + (frameCounter & 1)) * light_volume_depth);
    return texture(cg_light_tex, vec3(at.xy, z) / vec3(light_volume_size.xy, light_sections * light_volume_depth)).rgb;
}
#endif

#ifdef LIT_COLORED_LIGHT
// The colour of the block light on a surface at scene_pos facing normal: the volume's light in the block in front
// of the surface, as far as the volume has any light there (and COLORED_LIGHT_SATURATION says), else torchlight; as
// bright as torchlight in all (red, green and blue summed), so a light level means what it means elsewhere.
vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    const vec3 torch = torch_color;
    vec3 at;
    float fade = light_volume_at(scene_pos, normal, at);
    if (fade <= 0.0) {
        return torch;
    }
    vec3 light = light_volume_sample(at, section_color);
    float peak = max(light.r, max(light.g, light.b));
    if (peak <= 1e-4) {
        return torch;
    }
    float weight = smoothstep(0.0, 2.0 / 15.0, peak) * fade * COLORED_LIGHT_SATURATION;
    vec3 colored = light * ((torch.r + torch.g + torch.b) / (light.r + light.g + light.b));
    return max(mix(torch, colored, weight), 0.0);
}
#else
vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    return torch_color;
}
#endif

#ifdef LIT_GI
// The sunlight (or moonlight) bouncing off the world onto a surface at scene_pos facing normal, as a share of the
// light's own (times g_light_color it is irradiance): the volume's bounce light in the block in front of it. sky: the
// surface's sky light (0..1). Where a surface sees the open sky, the sky's light already holds the ground's bounce
// (see frame_globals.csh), so this adds little there.
vec3 bounce_light(vec3 scene_pos, vec3 normal, float sky) {
    vec3 at;
    float fade = light_volume_at(scene_pos, normal, at);
    if (fade <= 0.0) {
        return vec3(0.0);
    }
    float open_sky = smoothstep(11.0 / 15.0, 1.0, sky);
    return light_volume_sample(at, section_bounce) * (fade * (1.0 - 0.9 * open_sky) * GI_STRENGTH / bounce_peak);
}
#endif

#endif

#endif

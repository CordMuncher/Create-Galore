// Coloured light, in the lit overworld (see settings.glsl): the colour of the block light around you, from a volume
// of blocks centred on you that the light spreads through.
//
// The shadow pass marks what each block is (shadow.vsh): a light, with its light level and a colour read from its
// own texture (so every modded light has one without any list of blocks); a solid cube, which stops the light; or
// see-through (stained glass, ice), which tints it. Iris clears those marks every frame. The light flood
// (light_flood.csh) then spreads each light one block a frame out of last frame's volume, losing a light level per
// block as Minecraft's light does and keeping its colour, so lights side by side mix their colours where they meet.
// Nothing in it is random, so the upscaler sees the same light every frame.
//
// The volume only gives the light's colour; how bright it is stays Minecraft's light level, so the light never
// reaches further than Minecraft's does and never leaks where Minecraft's doesn't. Past the volume, or where a light's
// blocks aren't drawn, the light keeps the pack's torchlight colour.

#if !defined LIB_LIGHT_VOLUME
#define LIB_LIGHT_VOLUME

#include "/lib/settings.glsl"

// Torchlight: a warm 3000 K or so, luminance 1. The colour of every light without coloured light, and of the warm ones
// (torches, lanterns, glowstone, fire) with it.
const vec3 torch_color = vec3(1.453, 0.916, 0.494);

#ifdef LIT_COLORED_LIGHT

// The volume's size in blocks (x and z across, y up): COLORED_LIGHT_RANGE across, half of it up.
const ivec3 light_volume_size = ivec3(COLORED_LIGHT_RANGE, COLORED_LIGHT_RANGE / 2, COLORED_LIGHT_RANGE);
// Both images hold two volumes along z: the marks (what each block is, then its light), and the light twice, taking
// turns (one written each frame while the other is read).
const int light_volume_depth = COLORED_LIGHT_RANGE;

// What a block is, in the first of the marks' volumes (each bit set by any of its vertices, see shadow.vsh).
const uint mark_solid = 1u;      // a vertex of a solid block at one of the block's corners
const uint mark_partial = 2u;    // a vertex of a solid block inside the block's bounds (a slab, a stair, a fence)
const uint mark_tinted = 4u;     // see-through, tinting what passes; bits 8.. the tint (7 bits each, red first)

// The volume's block at a position relative to the camera (scene space): blocks counted from the volume's corner.
ivec3 light_volume_block(vec3 scene_pos, vec3 camera_fract) {
    return ivec3(floor(scene_pos + camera_fract)) + light_volume_size / 2;
}

bool in_light_volume(ivec3 block) {
    return all(greaterThanEqual(block, ivec3(0))) && all(lessThan(block, light_volume_size));
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

#if !defined PROGRAM_SHADOW_TERRAIN && !defined PROGRAM_LIGHT_FLOOD
#include "/lib/motion.glsl"

uniform sampler3D cg_light_tex;

// The colour of the block light on a surface at scene_pos facing normal: the volume's light in the block in front
// of the surface, as far as the volume has any light there (and COLORED_LIGHT_SATURATION says), else torchlight; as
// bright as torchlight in all (red, green and blue summed), so a light level means what it means elsewhere.
vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    const vec3 torch = torch_color;
    vec3 at = scene_pos + normal * 0.5 + cameraPositionFract + vec3(light_volume_size / 2);
    if (any(lessThan(at, vec3(1.0))) || any(greaterThan(at, vec3(light_volume_size) - 1.0))) {
        return torch;
    }
    // This frame's volume (the flood wrote it after the shadow pass), sampled between its blocks' centres.
    float z = at.z + ((frameCounter & 1) == 0 ? 0.0 : float(light_volume_depth));
    vec3 light = texture(cg_light_tex, vec3(at.xy, z) / vec3(light_volume_size.xy, 2 * light_volume_depth)).rgb;
    float peak = max(light.r, max(light.g, light.b));
    if (peak <= 1e-4) {
        return torch;
    }
    // Fading out at the volume's edges, so the change to torchlight past them isn't seen as a line.
    vec3 edge = min(at, vec3(light_volume_size) - at);
    float fade = smoothstep(1.0, 8.0, min(edge.x, min(edge.y, edge.z)));
    float weight = smoothstep(0.0, 2.0 / 15.0, peak) * fade * COLORED_LIGHT_SATURATION;
    vec3 colored = light * ((torch.r + torch.g + torch.b) / (light.r + light.g + light.b));
    return max(mix(torch, colored, weight), 0.0);
}
#endif

#elif !defined PROGRAM_SHADOW_TERRAIN

vec3 block_light_color(vec3 scene_pos, vec3 normal) {
    return torch_color;
}

#endif

#endif

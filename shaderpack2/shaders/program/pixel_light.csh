// Coloured block light traced per pixel (the sharp layer): crisp contact shadows from a torch behind a fence post,
// stained glass tinting a wall, and a highlight from every nearby light on smooth surfaces. One light sample per
// PIXEL_LIGHT_SCALE x PIXEL_LIGHT_SCALE pixels, each frame at a different pixel of its square (so over a few frames
// every pixel gets one); program/pixel_light_filter.csh blends them back up.
//
// Each sample takes the lights of the 8-block tile of the world its surface is in (the records program/voxel_light.csh
// keeps, those that lit something there first, then the strongest), never a list that depends on what the screen
// shows: the same spot gets the same lights from wherever you look at it. For each light in reach that its surface
// faces, up to PIXEL_LIGHT_TRACES of them: a cone trace through the voxel copy (the light's size over its distance:
// near lights give soft shadows, far ones sharp), a walk to a random point on the light for the colour of any glass
// on the way, and a GGX highlight. The held light (HAND_LIGHT) is always looked at.

#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/lighting.glsl"
#include "/lib/voxel_sample.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
// One thread per sample: the window's size over the scale (Iris divides by the work group's size itself).
#if PIXEL_LIGHT_SCALE == 1
const vec2 workGroupsRender = vec2(1.0, 1.0);
#elif PIXEL_LIGHT_SCALE == 2
const vec2 workGroupsRender = vec2(0.5, 0.5);
#elif PIXEL_LIGHT_SCALE == 4
const vec2 workGroupsRender = vec2(0.25, 0.25);
#elif PIXEL_LIGHT_SCALE == 6
const vec2 workGroupsRender = vec2(0.16667, 0.16667);
#else
const vec2 workGroupsRender = vec2(0.33334, 0.33334);
#endif

uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
uniform int heldItemId;  // item.properties: a light's number, as in block.properties
uniform int heldItemId2;

// Diffuse (compressed; a: which pixel of the square + 1) in the left half, highlights (compressed) in the right.
layout(rgba16f) writeonly uniform image2D cg_pl_img;

// The light that one point gets from a light at to, of colour (at a block) and reach, through a cone of radius at
// the light, traced from start up to own blocks short of the light; adds its highlight to spec.
vec3 light_from(vec3 p, vec3 start, vec3 n, vec3 v, float smoothness, bool thin, vec3 to, vec3 color, float reach,
                float radius, float own, inout vec3 spec, inout int traces, vec3 jitter) {
    vec3 to_light = to - p;
    float d = length(to_light);
    if (d >= reach || d < 1e-3) {
        return vec3(0.0);
    }
    vec3 dir = to_light / d;
    float facing = dot(n, dir);
    if (thin) {
        facing = abs(facing) * 0.8; // plants and leaves let light through
    }
    if (facing <= 0.0) {
        return vec3(0.0);
    }
    vec3 c = color * light_falloff(d, reach);
    if (max3(c) * facing < 1e-4) {
        return vec3(0.0);
    }
    traces++;
    vec3 from_start = to - start;
    float start_d = length(from_start);
    float visible = cone_visibility(start, from_start / max(start_d, 1e-3), start_d, 0.0, radius, own, 64);
    if (visible <= 0.0) {
        return vec3(0.0);
    }
    vec3 aim = to + jitter * radius - p;
    float aim_d = length(aim);
    c *= visible * see_through_tint(p, aim / aim_d, aim_d);
#ifdef LIGHT_SPECULAR
    spec += c * ggx_specular(n, v, dir, smoothness, 0.04);
#endif
    return c * facing;
}

void main() {
    ivec2 sample_texel = ivec2(gl_GlobalInvocationID.xy);
    const int scale = PIXEL_LIGHT_SCALE;
    ivec2 spec_texel = sample_texel + ivec2(imageSize(cg_pl_img).x / 2, 0);

    // This sample's pixel: a different one of its square each frame (7 shares no factor with any square's size).
    uint turn = (uint(frameCounter) * 7u + pcg3d(uvec3(uvec2(sample_texel), 5u)).x % 64u) % uint(scale * scale);
    ivec2 pixel = sample_texel * scale + ivec2(int(turn) % scale, int(turn) / scale);
    bool has_surface = !outside_render_area(vec2(pixel) + 0.5);
    float depth = has_surface ? texelFetch(depthtex0, pixel, 0).r : 1.0;
    if (!has_surface || depth >= 1.0) {
        imageStore(cg_pl_img, sample_texel, vec4(0.0));
        imageStore(cg_pl_img, spec_texel, vec4(0.0));
        return;
    }

    Surface s = read_surface(pixel);
    vec3 view = pixel_to_view(pixel, depth);
    vec3 scene = view_to_scene_pos(view);
    vec3 v = normalize(-scene);
    bool hand = s.material == MAT_HAND;
    vec3 p = vox_pos(scene) + s.face * (hand ? 0.15 : 0.04);

    if (start_inside(p)) {
        // A surface the copy doesn't hold (a mob, the hand) pressed into a full block: no ray gets out, so the light
        // of the block in front stands in.
        vec3 block_light = sample_block_table(cg_blk_tex, p + s.face * 0.5).rgb;
        imageStore(cg_pl_img, sample_texel, vec4(block_light, float(turn) + 1.0));
        imageStore(cg_pl_img, spec_texel, vec4(0.0, 0.0, 0.0, 1.0));
        return;
    }

    bool thin = s.material == MAT_PLANT || s.material == MAT_LEAVES;
    int traces = 0;
    vec3 diffuse = vec3(0.0);
    vec3 spec = vec3(0.0);
    vec3 jitter = hash33(uvec3(uvec2(pixel), uint(frameCounter) * 3u + 2u)) * 2.0 - 1.0;
#ifdef HAND_LIGHT
    // Each hand's light, in its own colour (item.properties), from where that hand holds it.
    for (int h = 0; h < 2; h++) {
        int held = h == 0 ? heldBlockLightValue : heldBlockLightValue2;
        if (held <= 0) {
            continue;
        }
        vec3 hand_view = vec3(h == 0 ? 0.25 : -0.25, -0.25, -0.35);
        vec3 hand_scene = mat3(gbufferModelViewInverse) * hand_view + gbufferModelViewInverse[3].xyz;
        vec4 kind = held_light(h == 0 ? heldItemId : heldItemId2, float(held));
        // The last half block is the hand's own (it can sit inside a block beside you).
        diffuse += light_from(p, p, s.normal, v, s.smoothness, thin, vox_pos(hand_scene),
                              kind.rgb * light_brightness(kind.a), light_reach(kind.a), 0.12, 0.5, spec, traces,
                              jitter);
    }
#endif
    ivec3 tile;
    if (block_tile_of(p, tile)) {
        uint records = record_index(tile);
        uint count = min(data_load(records), record_max);
        for (uint j = 0u; j < count && traces < PIXEL_LIGHT_TRACES; j++) {
            float radius;
            Light l = load_record(records + 1u + 3u * j, radius);
            diffuse += light_from(p, p, s.normal, v, s.smoothness, thin, l.pos, l.color, l.reach, radius, 0.0, spec,
                                  traces, jitter);
        }
    }
    imageStore(cg_pl_img, sample_texel, vec4(compress_light(diffuse), float(turn) + 1.0));
    imageStore(cg_pl_img, spec_texel, vec4(compress_light(spec), 1.0));
}

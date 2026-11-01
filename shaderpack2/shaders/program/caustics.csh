// How the waves focus the sun's (or the moon's) light on what lies under water (caustics, lib/waves.glsl), worked out
// once per 2 x 2 pixels before the lighting (program/deferred_light.fsh) takes it, blended, for every pixel: the
// pattern is no finer than two pixels anyway (caustic_focus's footprint), and each look at it evaluates the waves four
// times. The 2 x 2's own pixel walks the block copy up to the surface along the light (water_path); a pixel out of the
// water, or one the copy doesn't reach, gets 1 (no focusing). Into colortex9's bottom left quarter (the light shafts
// under water take two others, program/water_shafts.csh).

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/shadow.glsl"
#include "/lib/trace.glsl"
#include "/lib/water.glsl"

layout(local_size_x = 8, local_size_y = 8) in;
const vec2 workGroupsRender = vec2(0.5, 0.5);

layout(rgba16f) writeonly uniform image2D colorimg9;

void main() {
    ivec2 id = ivec2(gl_GlobalInvocationID.xy);
    ivec2 half_size = (ivec2(render_size()) + 1) / 2;
    if (any(greaterThanEqual(id, half_size))) {
        return;
    }
    float focus = 1.0;
#if defined CAUSTICS && (defined SUNLIGHT || defined WORLD_END)
    ivec2 pixel = id * 2;
    float depth = texelFetch(depthtex0, pixel, 0).r;
    vec3 l = g_light_dir.xyz;
    if (depth < 1.0 && l.y > 0.0) {
        Surface s = read_surface(pixel);
        if (s.material != MAT_HAND) {
            vec3 scene = view_to_scene_pos(pixel_to_view(pixel, depth));
            float path = water_path(vox_pos(scene) + s.face * 0.05, l, 48);
            if (path > 0.0) {
                // As shadow_light works it out for a pixel of its own (lib/shadow.glsl), with a footprint twice as
                // wide: one look per two pixels each way.
                vec3 lit = shadow_offset_point(scene, s.face, l);
                vec3 surface = lit + l * path;
                float below = path * l.y;
                float size = length(lit) * 2.0 / (gbufferProjection[1][1] * render_size().y);
                focus = caustic_focus(water_local(surface), l, WATER_WAVES * water_chop(surface), g_water.x, below,
                                      max(below * 0.05, size * 4.0));
            }
        }
    }
#endif
    imageStore(colorimg9, id, vec4(focus, 0.0, 0.0, 1.0));
}

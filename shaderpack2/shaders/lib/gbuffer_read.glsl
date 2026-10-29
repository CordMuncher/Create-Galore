// Reading the G-buffer back (see lib/buffers.glsl) in the passes after it: a pixel's surface and where it is.

#if !defined LIB_GBUFFER_READ
#define LIB_GBUFFER_READ

#include "/lib/buffers.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"

uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex4;
uniform sampler2D depthtex0;

// Iris squeezes the hand's depth into the middle of the range (MC_HAND_DEPTH of it).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

bool is_hand_depth(float depth) {
    return depth < hand_depth_limit;
}

// A pixel's place in view space, from its depth (the hand's unsqueezed), without this frame's jitter.
vec3 pixel_to_view(ivec2 texel, float depth) {
    if (is_hand_depth(depth)) {
        depth = (depth - 0.5) / MC_HAND_DEPTH + 0.5;
    }
    vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
    vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

vec3 view_to_scene_pos(vec3 view) {
    return mat3(gbufferModelViewInverse) * view + gbufferModelViewInverse[3].xyz;
}

Surface read_surface(ivec2 texel) {
    Surface s;
    vec4 a = texelFetch(colortex1, texel, 0);
    vec4 n = texelFetch(colortex2, texel, 0);
    vec4 l = texelFetch(colortex3, texel, 0);
    vec4 m = texelFetch(colortex4, texel, 0);
    s.albedo = srgb_to_linear(a.rgb);
    s.material = int(a.a * 255.0 + 0.5);
    s.normal = decode_normal(n.xy);
    s.face = decode_normal(n.zw);
    s.block = l.x;
    s.sky = l.y;
    s.ao = l.z;
    s.emission = l.w;
    s.smoothness = m.x;
    s.metal = m.y;
    s.subsurface = m.z;
    return s;
}

#endif

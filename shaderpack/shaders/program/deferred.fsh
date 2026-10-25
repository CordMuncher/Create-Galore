// The overworld's deferred pass, after everything opaque is drawn and before the translucent things: lights each
// surface the G-buffer holds (see gbuffer.glsl) by the sun or the moon, the sky and the blocks (lighting.glsl), adds
// what glows or lit itself (colortex0), and draws the sky where nothing is. The translucent programs then draw over
// the lit scene, lighting themselves the same way.

#include "/lib/settings.glsl"
#include "/lib/motion.glsl"
#include "/lib/lit.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
#endif

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        out_color = vec4(0.0); // outside the frame while upscaling
        return;
    }

    // This pixel's centre without the jitter, in normalised device coordinates, and the direction it looks in.
    vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0;
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0));

    vec3 emissive = texelFetch(colortex0, texel, 0).rgb;
    vec4 surface = texelFetch(colortex2, texel, 0);
    int material = decode_material(surface);
    float depth = texelFetch(depthtex0, texel, 0).r;

    bool lod = false;
    vec3 view = vec3(0.0);
    if (depth < 1.0) {
        bool hand = depth < hand_depth_limit;
        view = unproject(gbufferProjectionInverse, ndc, hand ? (depth - 0.5) / MC_HAND_DEPTH + 0.5 : depth);
    } else {
#ifdef DISTANT_HORIZONS
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            view = unproject(dhProjectionInverse, ndc, dh_depth);
            lod = true;
        } else {
            material = MATERIAL_NONE;
        }
#else
        material = MATERIAL_NONE;
#endif
    }

    vec3 color;
    if (material == MATERIAL_NONE) {
        // The sky, behind whatever only blended or glowed over it (smoke, lightning). A render pixel's size, for
        // the sun's edge and the stars.
        float pixel_angle = 2.0 / (gbufferProjection[1][1] * size.y);
        vec3 sky = sky_full(dir, pixel_angle);
        // Under water, in lava or powder snow, and blind, the fog hides the sky.
        if (isEyeInWater != 0 || blindness > 0.0 || darknessFactor > 0.0) {
            vec2 range = fog_range();
            sky = mix(sky, fog_color_lit(dir), range.y > range.x ? 1.0 : 0.0);
        }
        float covered = texelFetch(colortex1, texel, 0).a;
        color = emissive + sky * exposure() * (1.0 - covered);
    } else if (material == MATERIAL_SELF) {
        color = emissive;
    } else {
        vec3 scene_pos = view_to_scene(view);
        vec3 albedo = srgb_to_linear(texelFetch(colortex1, texel, 0).rgb);
        vec3 normal = decode_normal(surface.rg);
        vec3 lit = light_surface(albedo, normal, decode_light(surface), scene_pos, material == MATERIAL_PLANT, !lod) * exposure();
        // Minecraft's fog: a cylinder around you in the open air, round in water and the like (Iris gives this pass
        // no fog shape).
        bool open_air = isEyeInWater == 0 && blindness == 0.0 && darknessFactor == 0.0;
        float dist = open_air ? max(length(scene_pos.xz), abs(scene_pos.y)) : length(scene_pos);
        color = emissive + apply_fog_lit(lit, dist, dir, fog_range_shaped(open_air ? 1 : 0));
    }
    out_color = vec4(color, 1.0);
}

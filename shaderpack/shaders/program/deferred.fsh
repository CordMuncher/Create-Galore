// The overworld's deferred pass (its last part, deferred2), after everything opaque is drawn and before the
// translucent things: lights each surface the G-buffer holds (see gbuffer.glsl) by the sun or the moon, the sky and
// the blocks (lighting.glsl), adds what glows or lit itself (colortex0), and draws the sky where nothing is, with
// the clouds the passes before worked out (see clouds.glsl). The translucent programs then draw over the lit scene,
// lighting themselves the same way.

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

#ifdef LIT_CLOUDS
#include "/lib/clouds.glsl"

uniform sampler2D cg_cloud_hist_tex;

// The cumulus in front of what a pixel sees (rgb their light with the air in front of them, a how much of what is
// behind gets through), from the image the gather pass wrote this frame (see clouds_gather.csh), at the point the
// pixel sees (in unjittered render pixels).
vec4 cumulus_at(vec2 point) {
    vec2 size = window_size();
    vec2 at = clamp(point, vec2(0.5), render_size() - 0.5) + vec2((frameCounter & 1) == 0 ? 0.0 : size.x, 0.0);
    return texture(cg_cloud_hist_tex, at / vec2(2.0 * size.x, size.y));
}

// The sky with the clouds: the cumulus over the air, and the cirrus between the air and what is beyond it.
vec3 sky_with_clouds(vec3 dir, float pixel_angle, vec2 point, ivec2 texel) {
    vec3 beyond = sky_space(dir, pixel_angle) * (1.0 - rainStrength);
#ifdef CIRRUS
    vec4 ci = cirrus(dir, cloud_noise(texel, 7u));
    beyond = ci.rgb + ci.a * beyond;
#endif
    vec4 cu = cumulus_at(point);
    return (sky_radiance(dir) + beyond * sky_transmittance(dir)) * cu.a + cu.rgb;
}
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
    vec2 point = vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y);
    vec2 ndc = point / size * 2.0 - 1.0;
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
#ifdef LIT_CLOUDS
        vec3 sky = sky_with_clouds(dir, pixel_angle, point, texel);
#else
        vec3 sky = sky_full(dir, pixel_angle);
#endif
        // Under water, in lava or powder snow, and blind, the fog hides the sky.
        if (isEyeInWater != 0 || blindness > 0.0 || darknessFactor > 0.0) {
            vec2 range = fog_range();
            sky = mix(sky, fog_color_lit(dir), range.y > range.x ? 1.0 : 0.0);
        }
        sky += global(G_FLASH).rgb; // lightning
        float covered = texelFetch(colortex1, texel, 0).a;
        color = emissive + sky * exposure() * (1.0 - covered);
    } else if (material == MATERIAL_SELF) {
        color = emissive;
    } else {
        vec3 scene_pos = view_to_scene(view);
        vec3 albedo = srgb_to_linear(texelFetch(colortex1, texel, 0).rgb);
        vec3 normal = decode_normal(surface.rg);
        vec2 light = decode_light(surface);
        // Rain darkens what it wets and leaves a film of water on it (not on plants, which shed it).
        float wet = material == MATERIAL_LIT ? wetness_at(normal, light.y) : 0.0;
        albedo *= 1.0 - 0.35 * wet;
        vec3 lit = light_surface(albedo, normal, light, scene_pos, material == MATERIAL_PLANT, !lod) * exposure();
        if (wet > 0.0) {
            lit = with_wet_film(lit, normal, dir, wet);
        }
        // Minecraft's fog: a cylinder around you in the open air, round in water and the like (Iris gives this pass
        // no fog shape).
        bool open_air = isEyeInWater == 0 && blindness == 0.0 && darknessFactor == 0.0;
        float dist = open_air ? max(length(scene_pos.xz), abs(scene_pos.y)) : length(scene_pos);
        color = emissive + apply_fog_lit(lit, dist, dir, fog_range_shaped(open_air ? 1 : 0));
#ifdef LIT_CLOUDS
        // From above the cumulus, they hide what lies under them.
        if (atm_height(cameraPosition.y) > cumulus_bottom && atm_height(scene_pos.y + cameraPosition.y) < cumulus_bottom) {
            vec4 cu = cumulus_at(point);
            color = color * cu.a + cu.rgb * exposure();
        }
#endif
    }
    out_color = vec4(color, 1.0);
}

// The DLSS point (composite2, see create_galore_dlss.properties). This pass hands DLSS (or FSR) what it needs, and
// the Create Galore Bridge runs it right after this pass, writing its result over this pass's output:
// - the scene in linear light, before exposure and tone (colortex0), jittered and not anti-aliased;
// - motion vectors in render pixels towards last frame, unjittered (cg_motion_img): a surface's own where it moves
//   by itself (colortex7), else where the camera's motion takes its depth, Distant Horizons' included; over cloudy
//   sky, the clouds' own (lib/clouds.glsl);
// - DLSS depth, 1 at the near plane falling towards 0 far away, 0 for the sky (cg_depth_img);
// - over a particle, the depth and motion of the surface behind it. Particles have no motion of their own to give,
//   and taken at their own depth DLSS fetched last frame's picture from the wrong place round a slow, dim one (torch
//   smoke), so it showed a shifted piece of what was behind it, like a floating piece of glass. Now DLSS keeps the
//   background where it belongs and the particle counts as new each frame;
// - the exposure the final pass gives the scene (cg_exposure_img).
// While upscaling, the frame fills the lower left corner of the buffers at the render size and only that is handed
// over. Without the Bridge, or with DLSS off, the final pass shows this pass's output itself.

#include "/lib/settings.glsl"
#include "/lib/buffers.glsl"
#include "/lib/motion.glsl"
#include "/lib/post.glsl"
#include "/lib/bad_frame.glsl"

uniform sampler2D colortex7;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1; // without particles and see-through things
uniform float near;
uniform float cg_dlss_object_motion; // 0 while the Bridge tests without the motion of things that move by themselves

#ifdef VOLUME_CLOUDS
uniform float frameTime;
layout(rgba16f) readonly uniform image2D cg_cloud_img; // see clouds.csh
#include "/lib/clouds.glsl"
#endif

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
uniform mat4 dhPreviousProjection;
#endif

layout(rg16f) writeonly uniform image2D cg_motion_img;
layout(r32f) writeonly uniform image2D cg_depth_img;
layout(r32f) writeonly uniform image2D cg_exposure_img;

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

const float hand_depth_limit = 0.5 + 0.5 * MC_HAND_DEPTH;

vec3 unproject(mat4 projection_inverse, vec2 ndc, float depth) {
    vec4 view = projection_inverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return view.xyz / view.w;
}

// Motion in render pixels from this pixel (ndc) to where its surface was last frame.
vec2 motion_to(vec4 clip_prev, vec2 ndc, vec2 size) {
    return clip_prev.w > 0.0 ? (clip_prev.xy / clip_prev.w - ndc) * 0.5 * size : vec2(0.0);
}

float dlss_depth(vec3 view) {
    return clamp(near / max(-view.z, near), 0.0, 1.0);
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
    if (any(greaterThanEqual(vec2(texel) + 0.5, size))) {
        out_color = vec4(0.0); // outside the frame while upscaling
        return;
    }

    // No NaN or infinity reaches DLSS.
    vec3 scene_color = texelFetch(colortex0, texel, 0).rgb;
    if (bad_value(scene_color)) {
        scene_color = vec3(0.0);
        note_bad(BAD_BEFORE_DLSS);
    }
    out_color = vec4(clamp(scene_color, 0.0, 4096.0), 1.0);

    // This pixel's centre without the jitter, in normalised device coordinates.
    vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0;
    vec4 own = texelFetch(colortex7, texel, 0);
#ifdef PARTICLES_SHOW_BACKGROUND
    bool particle = own.a > 0.5 && abs(own.b - MOTION_PARTICLE) < 0.5;
#else
    bool particle = false;
#endif
    float depth = particle ? texelFetch(depthtex1, texel, 0).r : texelFetch(depthtex0, texel, 0).r;
    vec2 motion = vec2(0.0);
    float depth_out = 0.0;

    if (depth < 1.0) {
        bool hand = depth < hand_depth_limit;
        float z = hand ? (depth - 0.5) / MC_HAND_DEPTH + 0.5 : depth;
        vec3 view = unproject(gbufferProjectionInverse, ndc, z);
        depth_out = dlss_depth(view);
        // The hand moves with the camera; the rest of the world stands still unless it said otherwise.
        motion = hand ? vec2(0.0) : motion_to(previous_clip(view_to_scene(view), gbufferPreviousProjection), ndc, size);
    } else {
#ifdef DISTANT_HORIZONS
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
#else
        float dh_depth = 1.0;
#endif
        if (dh_depth < 1.0) {
#ifdef DISTANT_HORIZONS
            vec3 view = unproject(dhProjectionInverse, ndc, dh_depth);
            depth_out = dlss_depth(view);
            motion = motion_to(previous_clip(view_to_scene(view), dhPreviousProjection), ndc, size);
#endif
        } else {
            // The sky only turns with the camera.
            vec3 direction = mat3(gbufferModelViewInverse) * unproject(gbufferProjectionInverse, ndc, 1.0);
            vec4 clip_prev = gbufferPreviousProjection * vec4(mat3(gbufferPreviousModelView) * direction, 0.0);
            depth_out = 0.0;
            motion = motion_to(clip_prev, ndc, size);
#ifdef VOLUME_CLOUDS
            // Where clouds cover it, theirs: where most of the cloud is along the look, carried by the wind.
            if (clouds_here()) {
                CloudSample c = clouds_at(texel, cloud_far_sky);
                float cover = smoothstep(0.1, 0.5, 1.0 - c.light.a);
                if (cover > 0.0 && c.mid < cloud_far_sky) {
                    vec3 was = normalize(direction) * c.mid - cloud_wind() * frameTime;
                    vec2 cloud_motion = motion_to(previous_clip(was, gbufferPreviousProjection), ndc, size);
                    motion = mix(motion, cloud_motion, cover);
                }
            }
#endif
        }
    }

    if (own.a > 0.5 && own.b > 0.5 && !particle && cg_dlss_object_motion > 0.5) {
        motion = own.rg;
    }

    imageStore(cg_motion_img, texel, vec4(motion, 0.0, 0.0));
    imageStore(cg_depth_img, texel, vec4(depth_out));
    if (texel == ivec2(0)) {
        imageStore(cg_exposure_img, ivec2(0), vec4(exposure_scale()));
    }
}

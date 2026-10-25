// Motion vectors: where each pixel's surface was on the screen last frame, for DLSS and FSR (and later for every
// effect that reuses past frames). Programs work out the unjittered clip position of their vertex now and last
// frame; the fragment turns the pair into render pixels.
//
// Scene space here is world-aligned and relative to the camera now. Last frame's position of a vertex comes from:
// - the camera alone, for anything standing still (terrain, Distant Horizons, clouds, particles);
// - the Bridge's per-vertex record (cg_prev_pos) for mobs, block entities and the hand;
// - the Bridge's per-draw matrix (cg_object_motion) for Sable ships;
// - the Bridge's Colorwheel hook (cg_flw_prev_pos) for Create's contraptions and machines.

#if !defined LIB_MOTION
#define LIB_MOTION

#include "/lib/frame.glsl"

uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferPreviousProjection;

uniform ivec3 cameraPositionInt;
uniform ivec3 previousCameraPositionInt;
uniform vec3 cameraPositionFract;
uniform vec3 previousCameraPositionFract;

// How far the camera moved since last frame, in blocks. Whole and fractional parts apart, so it stays exact far
// from the world's origin.
vec3 camera_motion() {
    return vec3(cameraPositionInt - previousCameraPositionInt) + (cameraPositionFract - previousCameraPositionFract);
}

vec3 view_to_scene(vec3 view_pos) {
    return mat3(gbufferModelViewInverse) * view_pos + gbufferModelViewInverse[3].xyz;
}

// Last frame's clip position of a point that was at scene_prev (relative to the camera now).
vec4 previous_clip(vec3 scene_prev, mat4 previous_projection) {
    vec3 view_prev = mat3(gbufferPreviousModelView) * (scene_prev + camera_motion()) + gbufferPreviousModelView[3].xyz;
    return previous_projection * vec4(view_prev, 1.0);
}

// Motion in render pixels, from where the surface is now towards where it was, out of unjittered clip positions.
// A point behind the camera in either frame has none.
vec2 motion_vector(vec4 clip_now, vec4 clip_prev) {
    if (clip_now.w <= 0.0 || clip_prev.w <= 0.0) {
        return vec2(0.0);
    }
    return (clip_prev.xy / clip_prev.w - clip_now.xy / clip_now.w) * 0.5 * render_size();
}

// What drew a pixel, kept in colortex7's blue channel for the DLSS pass.
#define MOTION_NONE 0.0  // nothing with motion of its own (sky, rain): the DLSS pass works it out from the camera
#define MOTION_WORLD 1.0
#define MOTION_HAND 2.0
#define MOTION_LOD 3.0   // Distant Horizons

#endif

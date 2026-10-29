// Every world program's vertex stage: the clip position now and last frame for motion vectors, the frame's place on
// the screen (see frame.glsl), and the surface for the G-buffer or the forward lighting. Colorwheel's programs
// (Create's machines and contraptions through Flywheel, COLORWHEEL) come through here too: Colorwheel turns
// gl_Vertex, gl_Normal, gl_Color and gl_MultiTexCoord0 into Flywheel's vertex, and gl_ModelViewMatrix into its view.

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/blocks.glsl"

out vec2 v_uv;
out vec4 v_color;      // Minecraft's vertex colour: the biome tint, and for entities the hurt flash's base
out vec4 v_clip_now;
out vec4 v_clip_prev;
out vec3 v_normal;     // world space
out vec3 v_scene_pos;
out vec2 v_light;      // block and sky light, 0..1
out float v_ao;        // the corner shading Sodium hands over (separateAo), 1 in the open
flat out int v_block;  // the block's number in block.properties (terrain and water), else 0
flat out float v_emission; // the light the block gives, 0..1

#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
in vec4 mc_Entity;
in vec4 at_midBlock; // xyz towards the block's middle (in 1/64 blocks), w the light it gives (0..15)
#endif

#ifdef ENTITY_FORMAT
// Where the Create Galore Bridge had this vertex last frame, in Iris's entity format: w 2 in the vertex's own space,
// w 3 the same for the hand (which moves with the camera), else not known. Keep this line exactly as it is: the
// Bridge finds it to keep Veil's programs (levitite) working.
layout(location = 9) in vec4 cg_prev_pos;
#endif

#ifdef COLORWHEEL
// Where Flywheel had this vertex last frame (w 1), in Flywheel's world space: the Create Galore Bridge's Colorwheel
// hook works it out and sets it before this main runs; w stays 0 without the Bridge.
vec4 cg_flw_prev_pos = vec4(0.0);
#endif

#if defined OWN_MOTION && !defined HAND && !defined COLORWHEEL
// Set by the Bridge while Sable draws a ship: takes a vertex from where it is now (relative to the camera) to where
// the ship had it last frame. All 0 for everything else.
uniform mat4 cg_object_motion;
#endif

#ifdef OWN_MOTION
// Last frame's clip position of this vertex, unjittered.
vec4 previous_clip_position(vec3 scene_pos, vec4 clip) {
#if defined COLORWHEEL
    // A spinning shaft or a contraption on the move: last frame's position as Flywheel had it.
    bool moved = cg_flw_prev_pos.w > 0.0 && cg_flw_prev_pos.xyz != gl_Vertex.xyz;
    vec3 scene_prev = moved ? view_to_scene((gl_ModelViewMatrix * vec4(cg_flw_prev_pos.xyz, 1.0)).xyz) : scene_pos;
    return previous_clip(scene_prev, gbufferPreviousProjection);
#else
#ifdef HAND
    vec4 clip_prev = clip; // the hand moves with the camera
#else
    vec3 scene_prev = cg_object_motion[3][3] != 0.0 ? (cg_object_motion * vec4(scene_pos, 1.0)).xyz : scene_pos;
#endif
#ifdef ENTITY_FORMAT
    bool drawn_moving = (cg_prev_pos.w == 2.0 || cg_prev_pos.w == 3.0) && cg_prev_pos.xyz != gl_Vertex.xyz;
    if (drawn_moving && cg_prev_pos.w == 3.0) {
        // Its own motion on the screen, through this frame's matrices.
        return gl_ProjectionMatrix * (gl_ModelViewMatrix * vec4(cg_prev_pos.xyz, 1.0));
    }
#ifndef HAND
    if (drawn_moving) {
        scene_prev = view_to_scene((gl_ModelViewMatrix * vec4(cg_prev_pos.xyz, 1.0)).xyz);
    }
#endif
#endif
#ifdef HAND
    return clip_prev;
#else
    return previous_clip(scene_prev, gbufferPreviousProjection);
#endif
#endif
}
#endif

void main() {
    vec4 view_pos = gl_ModelViewMatrix * gl_Vertex;
    vec4 clip = gl_ProjectionMatrix * view_pos;
    vec3 scene_pos = view_to_scene(view_pos.xyz);

    v_clip_now = clip;
#ifdef OWN_MOTION
    v_clip_prev = previous_clip_position(scene_pos, clip);
#else
    v_clip_prev = clip;
#endif
    gl_Position = place_on_screen(clip);

#if defined COLORWHEEL
    v_uv = gl_MultiTexCoord0.xy;
#elif defined TEXTURED
    v_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
#else
    v_uv = vec2(0.0);
#endif

    v_color = gl_Color;
    v_ao = 1.0;
#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    // Sodium hands the smooth lighting's corners over in the alpha (separateAo).
    v_ao = gl_Color.a;
    v_color.a = 1.0;
#endif

#ifdef COLORWHEEL
    // Flywheel's normal is in world space already; its light comes with the fragment (see gbuffers.fsh).
    vec3 normal = gl_Normal;
    v_light = vec2(0.0);
#else
    vec3 normal = mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal);
    // Block and sky light as Minecraft packs them (0..240).
    v_light = clamp(gl_MultiTexCoord1.xy / 240.0, 0.0, 1.0);
#endif
    v_normal = dot(normal, normal) > 1e-8 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    v_scene_pos = scene_pos;

    v_block = 0;
    v_emission = 0.0;
#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    v_block = int(mc_Entity.x + 0.5);
    v_emission = clamp(at_midBlock.w / 15.0, 0.0, 1.0);
#endif
}

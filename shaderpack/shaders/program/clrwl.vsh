// Create's machines, contraptions and trains, and what rides on Sable's ships, as Colorwheel draws them through
// Flywheel: the same look and motion vectors as the rest of the world. Colorwheel turns gl_Vertex, gl_Normal,
// gl_Color and gl_MultiTexCoord0 into Flywheel's vertex, and gl_ModelViewMatrix into its view.

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"

out vec2 v_uv;
out float v_fog_dist;
out vec4 v_clip_now;
out vec4 v_clip_prev;
#ifdef LIT_WORLD
out vec3 v_normal;    // world space: Colorwheel hands Flywheel's normal over as gl_Normal
out vec3 v_scene_pos;
#endif

// Where Flywheel had this vertex last frame (w 1), in Flywheel's world space: the Create Galore Bridge's Colorwheel
// hook works it out and sets it before this main runs; w stays 0 without the Bridge.
vec4 cg_flw_prev_pos = vec4(0.0);

void main() {
    vec4 view_pos = gl_ModelViewMatrix * gl_Vertex;
    vec4 clip = gl_ProjectionMatrix * view_pos;
    vec3 scene_pos = view_to_scene(view_pos.xyz);

    // A spinning shaft or a contraption on the move: last frame's position as Flywheel had it.
    bool moved = cg_flw_prev_pos.w > 0.0 && cg_flw_prev_pos.xyz != gl_Vertex.xyz;
    vec3 scene_prev = moved ? view_to_scene((gl_ModelViewMatrix * vec4(cg_flw_prev_pos.xyz, 1.0)).xyz) : scene_pos;

    v_clip_now = clip;
    v_clip_prev = previous_clip(scene_prev, gbufferPreviousProjection);
    gl_Position = place_on_screen(clip);

    v_uv = gl_MultiTexCoord0.xy;
    v_fog_dist = fog_distance(scene_pos);
#ifdef LIT_WORLD
    v_normal = gl_Normal;
    v_scene_pos = scene_pos;
#endif
}

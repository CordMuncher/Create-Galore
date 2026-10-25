// Every world program's vertex stage: Minecraft's own vertex colour, lighting and fog distance (see vanilla.glsl),
// the clip position now and last frame for motion vectors, and the frame's place on the screen (see frame.glsl).

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"

out vec2 v_uv;
out vec4 v_color;   // Minecraft's vertexColor: tint, mob shading and (for terrain) the lightmap
out vec3 v_light;   // the lightmap where Minecraft applies it after the hurt flash (mobs, particles); else 1
out vec3 v_fog_pos; // what the fog distance is measured from
out vec4 v_clip_now;
out vec4 v_clip_prev;

#ifdef ENTITY_FORMAT
// Where the Create Galore Bridge had this vertex last frame, in Iris's entity format: w 2 in the vertex's own space,
// w 3 the same for the hand (which moves with the camera), else not known. Keep this line exactly as it is: the
// Bridge finds it to keep Veil's programs (levitite) working.
layout(location = 9) in vec4 cg_prev_pos;
#endif

#if defined OWN_MOTION && !defined HAND
// Set by the Bridge while Sable draws a ship: takes a vertex from where it is now (relative to the camera) to where
// the ship had it last frame. All 0 for everything else.
uniform mat4 cg_object_motion;
#endif

#ifdef OWN_MOTION
// Last frame's clip position of this vertex, unjittered.
vec4 previous_clip_position(vec3 scene_pos, vec4 clip) {
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

#ifdef TEXTURED
    v_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
#else
    v_uv = vec2(0.0);
#endif

    int flags = key_flags();
    v_color = gl_Color;
    v_light = vec3(1.0);
    if ((flags & KEY_MOB_SHADING) != 0) {
        v_color.rgb *= mob_shade(gl_Normal);
    }
    if ((flags & KEY_LIGHTMAP) != 0) {
        // Block and sky light as Minecraft packs them (0..240), over 256.
        vec2 light = gl_MultiTexCoord1.xy / 256.0;
        if ((flags & KEY_LIGHTMAP_SMOOTH) != 0) {
            v_color.rgb *= lightmap_smooth(light);
        } else {
            v_light = lightmap_texel(light);
        }
    }

#if defined PROGRAM_CLOUDS || defined HAND
    v_fog_pos = view_pos.xyz;
#elif defined PROGRAM_BEACONBEAM
    v_fog_pos = vec3(0.0, 0.0, -view_pos.z); // the beam's fog goes by depth along the view
#else
    v_fog_pos = scene_pos;
#endif
}

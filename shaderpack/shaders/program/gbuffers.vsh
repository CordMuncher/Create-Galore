// Every world program's vertex stage: Minecraft's own vertex colour, lighting and fog distance (see vanilla.glsl),
// the clip position now and last frame for motion vectors, and the frame's place on the screen (see frame.glsl).
// In the lit overworld the vertex keeps its surface colour, normal and light levels instead (see lit.glsl).

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/vanilla.glsl"
#ifdef LIT_WORLD
#include "/lib/gbuffer.glsl"
#endif

out vec2 v_uv;
out vec4 v_color;   // Minecraft's vertexColor: tint, mob shading and (for terrain) the lightmap
out vec3 v_light;   // the lightmap where Minecraft applies it after the hurt flash (mobs, particles); else 1
out float v_fog_dist; // Minecraft's vertexDistance (see fog_distance)
out vec4 v_clip_now;
out vec4 v_clip_prev;
#ifdef LIT_WORLD
out vec3 v_normal;    // world space
out vec2 v_level;     // block and sky light, 0..1
out vec3 v_scene_pos;
flat out int v_material;
#endif

#if defined LIT_WORLD && (defined PROGRAM_WATER || defined PROGRAM_TERRAIN)
in vec4 mc_Entity; // the block's number in block.properties
#endif
#if defined LIT_WORLD && defined PROGRAM_TERRAIN
in vec4 at_midBlock;     // w the light the block gives (0..15)
flat out float v_emission; // that, 0..1
flat out int v_surface;  // what it is made of (SURFACE_)
out float v_ao;          // Minecraft's smooth lighting corners: 1 in the open, less in a corner
#endif
#ifdef LIT_RELIEF
in vec2 mc_midTexCoord;  // the middle of the quad's texture coordinates (Iris's)
out vec4 v_region;       // the quad's part of the atlas, for the relief: xy where it starts, zw its size
#endif

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
#ifdef LIT_RELIEF
    // Each corner of a quad lies as far from its middle as the others: the same part of the atlas at every corner.
    vec2 half_size = abs(v_uv - mc_midTexCoord);
    v_region = vec4(mc_midTexCoord - half_size, 2.0 * half_size);
#endif

    int flags = key_flags();
    v_color = gl_Color;
    v_light = vec3(1.0);
#if (defined PROGRAM_TERRAIN || defined PROGRAM_WATER) && defined LIGHTING
    // With the lighting on, Sodium hands the smooth lighting's corners over in the alpha (separateAo, see
    // shaders.properties): the lit overworld keeps them for the light all around (see light_surface), and every
    // other dimension puts them back into the colour, as vanilla has them.
    v_color.a = 1.0;
#if defined LIT_WORLD && defined PROGRAM_TERRAIN
    v_ao = gl_Color.a;
#elif !defined LIT_WORLD
    v_color.rgb *= gl_Color.a;
#endif
#endif
#ifdef LIT_WORLD
    vec3 normal = gl_NormalMatrix * gl_Normal;
    normal = dot(normal, normal) > 1e-8 ? normalize(mat3(gbufferModelViewInverse) * normal) : vec3(0.0, 1.0, 0.0);
    v_normal = normal;
    v_scene_pos = scene_pos;
    v_material = MATERIAL_LIT;
    // Block and sky light as Minecraft packs them (0..240).
    v_level = clamp(gl_MultiTexCoord1.xy / 240.0, 0.0, 1.0);
#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    if (is_plant_normal(normal)) {
        v_material = MATERIAL_PLANT;
    } else {
        v_color.rgb /= face_shade(normal);
    }
#endif
#ifdef PROGRAM_WATER
    if (int(mc_Entity.x + 0.5) == BLOCK_WATER) {
        v_material = MATERIAL_WATER;
    }
#endif
#ifdef PROGRAM_TERRAIN
    v_emission = clamp(at_midBlock.w / 15.0, 0.0, 1.0);
    int block = int(mc_Entity.x + 0.5);
    v_surface = block == BLOCK_LEAVES ? SURFACE_LEAVES : block == BLOCK_METAL ? SURFACE_METAL
            : block == BLOCK_POLISHED ? SURFACE_POLISHED : block == BLOCK_SOIL ? SURFACE_SOIL
            : block == BLOCK_SAND ? SURFACE_SAND : SURFACE_PLAIN;
#endif
#else
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
#endif

#if defined PROGRAM_SKYBASIC
    v_fog_dist = length(scene_pos); // the sky's fog is round
#elif defined PROGRAM_CLOUDS || defined HAND
    v_fog_dist = fog_distance(view_pos.xyz);
#elif defined PROGRAM_BEACONBEAM
    v_fog_dist = abs(view_pos.z); // the beam's fog goes by depth along the view
#else
    v_fog_dist = fog_distance(scene_pos);
#endif
}

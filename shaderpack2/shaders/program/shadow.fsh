// The shadow pass's fragments (see shadow.vsh): the shadow map's depth, the colour of what light passes through, and
// where your own body is (what you hold looks past it, see shadow_light).
// (The voxel copy of the world no longer comes from this pass: the Create Galore Bridge keeps it, see lib/world.glsl.)

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/blocks.glsl"
#include "/lib/view.glsl"

uniform sampler2D gtexture;
uniform float frameTimeCounter;

in vec2 vs_uv;
in vec4 vs_color;
in vec3 vs_scene;
flat in int vs_block;

#ifdef PROGRAM_SHADOW_WATER
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;
#else
/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 out_color;
layout(location = 1) out vec4 out_player; // shadowcolor1: 1 where your own body is the nearest thing to the light
#endif

#if !defined PROGRAM_SHADOW_TERRAIN
uniform int entityId; // entity.properties
#endif

#ifdef PROGRAM_SHADOW_TERRAIN

// Light under water: the bright net the waves focus it into, two layers drifting apart. Its own little cell noise:
// for each point, how close it is to the edge between its two nearest cells.
float cell_edge(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(x, y);
            vec2 h = hash33(uvec3(ivec3(cell + o + 4096.0, 9))).xy;
            vec2 r = o + 0.15 + 0.7 * h - f;
            float d = dot(r, r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return sqrt(d2) - sqrt(d1);
}

float caustics(vec2 world_xz, float t) {
    float a = cell_edge(world_xz * 0.85 + vec2(0.21, 0.13) * t);
    float b = cell_edge(world_xz * 1.2 + vec2(-0.12, 0.19) * t + 17.3);
    float net = (1.0 - smoothstep(0.0, 0.22, a)) + (1.0 - smoothstep(0.0, 0.22, b));
    return clamp(0.35 + 0.45 * net, 0.0, 1.0);
}

#endif

void main() {
    vec4 texel = texture(gtexture, vs_uv);
    float alpha = texel.a;
#ifdef PROGRAM_SHADOW_TERRAIN
    // Leaves far off cast from a blurrier copy of their texture, so their shadows don't flicker as you move.
    if (vs_block == BLOCK_LEAVES && dot(vs_scene, vs_scene) > 24.0 * 24.0) {
        alpha = textureLod(gtexture, vs_uv, 2.0).a * 1.4;
    }
#endif
    if (alpha < 0.1) {
        discard;
    }

#ifdef PROGRAM_SHADOW_WATER
    if (vs_block == BLOCK_WATER) {
        // Water: its colour times the caustics; alpha below a half says "water" (its depth absorbs the light).
        vec2 world = vs_scene.xz + cameraPosition.xz;
        vec3 tint = clamp(vs_color.rgb * 1.15, 0.0, 1.0);
        out_color = vec4(tint * caustics(world, frameTimeCounter), 0.0);
    } else {
        // Glass and the like: light takes on their colour, as strongly as they are opaque.
        vec3 tint = mix(vec3(1.0), texel.rgb * vs_color.rgb, clamp(alpha * 1.2, 0.0, 1.0));
        out_color = vec4(tint, 1.0);
    }
#else
    out_color = vec4(1.0);
#if defined PROGRAM_SHADOW_TERRAIN
    out_player = vec4(0.0);
#else
    out_player = vec4(entityId == ENTITY_PLAYER ? 1.0 : 0.0);
#endif
#endif
}

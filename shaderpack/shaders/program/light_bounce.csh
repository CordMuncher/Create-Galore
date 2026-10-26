// The overworld's bounce light's source, after the shadow pass (see light_volume.glsl). Every few texels of the shadow
// map (about half a block apart), the surface the sun (or the moon) lights there reflects its own colour of what a
// beam that wide brings it (whichever way the surface faces, the beam all lands on it) into the blocks in front of
// it: shared between the eight block centres around that point by how near each is, so it moves smoothly as the
// samples move across the blocks, never into a solid one. What the clouds' shadow takes is left out of it; the
// light's own colour is left for the lit surfaces to multiply in.

#define PROGRAM_LIGHT_BOUNCE
#include "/lib/light_volume.glsl"
#include "/lib/gbuffer.glsl"
#include "/lib/globals.glsl"
#include "/lib/motion.glsl"
#ifdef LIT_CLOUDS
#include "/lib/clouds.glsl"
#endif

layout(local_size_x = 16, local_size_y = 16) in;
// Samples across: as wide as the volume can be as the light sees it (1.5 LIGHT_RANGE) half a block apart, and a few
// more for the shadow map's steps as you move (Iris takes only plain numbers here).
#if LIGHT_RANGE == 64
const ivec3 workGroups = ivec3(13, 13, 1);
#elif LIGHT_RANGE == 192
const ivec3 workGroups = ivec3(37, 37, 1);
#elif LIGHT_RANGE == 256
const ivec3 workGroups = ivec3(49, 49, 1);
#else
const ivec3 workGroups = ivec3(25, 25, 1);
#endif

layout(r32ui) uniform uimage3D cg_voxel_img;
uniform sampler2D shadowtex0;   // depth (no hardware compares on this one, see settings.glsl)
uniform sampler2D shadowcolor0; // the lit surface's colour (shadow.fsh)
uniform sampler2D shadowcolor1; // and its facing
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjectionInverse;

// The shadow map's texel (blocks), the samples' step in texels, and the beam each sample stands for (blocks²).
const float bounce_texel = 2.0 * shadowDistance / float(shadowMapResolution);
const int bounce_stride = max(int(0.5 / bounce_texel + 0.5), 1);
const float bounce_sample_area = (float(bounce_stride) * bounce_texel) * (float(bounce_stride) * bounce_texel);

void main() {
    // The samples sit every bounce_stride texels from the shadow map's corner, so between its steps as you move (when
    // it holds still on the world) they land on the same places.
    vec4 origin = shadowProjection * (shadowModelView * vec4(0.0, 0.0, 0.0, 1.0));
    ivec2 centre = ivec2(floor((origin.xy * 0.5 + 0.5) * float(shadowMapResolution))) / bounce_stride;
    ivec2 samples = ivec2(gl_NumWorkGroups.xy * gl_WorkGroupSize.xy);
    ivec2 texel = (centre - samples / 2 + ivec2(gl_GlobalInvocationID.xy)) * bounce_stride + bounce_stride / 2;
    if (any(lessThan(texel, ivec2(0))) || any(greaterThanEqual(texel, ivec2(shadowMapResolution)))) {
        return;
    }
    float depth = texelFetch(shadowtex0, texel, 0).r;
    vec4 color = texelFetch(shadowcolor0, texel, 0);
    // Nothing there, or something that isn't a block (a mob, a ship, a contraption: shadow.fsh leaves those black).
    if (depth >= 1.0 || color.a < 0.5 || max(color.r, max(color.g, color.b)) < 1.0 / 255.0) {
        return;
    }
    vec3 albedo = color.rgb * color.rgb;
    vec3 normal = decode_normal(texelFetch(shadowcolor1, texel, 0).xy);
    vec3 ndc = vec3((vec2(texel) + 0.5) / float(shadowMapResolution), depth) * 2.0 - 1.0;
    vec4 view = shadowProjectionInverse * vec4(ndc, 1.0);
    vec3 scene_pos = (shadowModelViewInverse * vec4(view.xyz / view.w, 1.0)).xyz;

    vec3 flux = albedo * bounce_sample_area;
#ifdef LIT_CLOUDS
    flux *= cloud_shadow(scene_pos, g_light_dir.xyz);
#endif

    // The point in front of the surface, counted in blocks from the volume's corner, from the first of the eight block
    // centres around it.
    vec3 at = scene_pos + normal * 0.5 + cameraPositionFract + vec3(light_volume_size / 2) - 0.5;
    ivec3 base = ivec3(floor(at));
    vec3 f = at - vec3(base);
    float weights[8];
    float total = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 corner = ivec3(i & 1, (i >> 1) & 1, i >> 2);
        ivec3 block = base + corner;
        vec3 w = mix(1.0 - f, f, vec3(corner));
        weights[i] = w.x * w.y * w.z;
        if (!in_light_volume(block) || marks_solid(imageLoad(cg_voxel_img, block).r)) {
            weights[i] = 0.0;
        }
        total += weights[i];
    }
    if (total < 1e-3) {
        return;
    }
    vec3 scaled = flux * (bounce_scale / total);
    for (int i = 0; i < 8; i++) {
        if (weights[i] <= 0.0) {
            continue;
        }
        ivec3 block = base + ivec3(i & 1, (i >> 1) & 1, i >> 2);
        uvec3 add = uvec3(scaled * weights[i] + 0.5);
        for (int c = 0; c < 3; c++) {
            if (add[c] != 0u) {
                imageAtomicAdd(cg_voxel_img, block + ivec3(0, 0, (section_bounce_added + c) * light_volume_depth), add[c]);
            }
        }
    }
}

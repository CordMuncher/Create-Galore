// The End's deferred pass (world1/), after everything opaque is drawn and before the translucent things: its own
// sky (see end_sky.glsl) where nothing was drawn, unless something hides the sky (blindness, being under water or
// in lava), where the fog Minecraft cleared the frame to stays.

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/view.glsl"
#include "/lib/end_sky.glsl"

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform int isEyeInWater;
uniform float blindness;
uniform float darknessFactor;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
#endif

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 size = render_size();
    vec4 color = texelFetch(colortex0, texel, 0);
    out_color = color;
    if (any(greaterThanEqual(vec2(texel) + 0.5, size)) || isEyeInWater != 0 || blindness > 0.0 || darknessFactor > 0.0) {
        return;
    }
    if (texelFetch(depthtex0, texel, 0).r < 1.0) {
        return;
    }
#ifdef DISTANT_HORIZONS
    if (texelFetch(dhDepthTex0, texel, 0).r < 1.0) {
        return;
    }
#endif
    // The direction this pixel looks in, its centre without the jitter, and a render pixel's size.
    vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / size * 2.0 - 1.0;
    vec4 view = gbufferProjectionInverse * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
    float pixel_angle = 2.0 / (gbufferProjection[1][1] * size.y);
    out_color = vec4(end_sky(dir, pixel_angle), 1.0);
}

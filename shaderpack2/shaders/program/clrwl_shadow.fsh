// Flywheel's material code cuts out what its textures leave open; the rest blocks the light.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"

uniform sampler2D gtexture;

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    vec4 color;
    vec2 light;
    float ao;
    vec4 overlay;
    clrwl_computeFragment(texture(gtexture, v_uv), color, light, ao, overlay);
    out_color = vec4(1.0);
}

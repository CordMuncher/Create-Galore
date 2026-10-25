// Flywheel's material code cuts out what its textures leave open; the rest casts a shadow.

uniform sampler2D gtexture;

in vec2 v_uv;

void main() {
    vec4 color;
    vec2 light;
    float ao;
    vec4 overlay;
    // Discards what the material cuts out.
    clrwl_computeFragment(texture(gtexture, v_uv), color, light, ao, overlay);
}

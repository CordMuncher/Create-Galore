// The shadow map's depth only: what a texture cuts out (leaves, grass, fences) casts no shadow.

uniform sampler2D gtexture;
uniform float alphaTestRef;

in vec2 v_uv;
in float v_alpha;

void main() {
    if (texture(gtexture, v_uv).a * v_alpha < max(alphaTestRef, 0.1)) {
        discard;
    }
}

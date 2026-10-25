// Minecraft's own look, as its 1.21.1 shaders draw it: the lightmap, the fog and the two light directions that shade
// mobs and block entities. The pure anti-aliasing preset is exactly this, so every formula here follows
// assets/minecraft/shaders (core and include) of 1.21.1.

#if !defined LIB_VANILLA
#define LIB_VANILLA

uniform sampler2D lightmap;

uniform float fogStart;
uniform float fogEnd;
uniform int fogShape;     // 0 sphere, 1 cylinder
uniform vec3 fogColor;
uniform float far;        // the render distance in blocks
uniform bool hasCeiling;  // the Nether and dimensions like it

#ifdef DISTANT_HORIZONS
uniform int dhRenderDistance; // in blocks
#endif

// Terrain: per vertex, between the lightmap's texels (light.glsl). light is the vertex's light as Minecraft packs
// it, block and sky light times 16 (0..240), divided by 256.
vec3 lightmap_smooth(vec2 light) {
    return texture(lightmap, clamp(light, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
}

// Mobs, block entities, particles and rain: the lightmap texel of the light level, unfiltered.
vec3 lightmap_texel(vec2 light) {
    return texelFetch(lightmap, clamp(ivec2(light * 16.0 + 1.0 / 512.0), ivec2(0), ivec2(15)), 0).rgb;
}

// The two fixed lights that shade mobs, block entities and held items (Lighting.setupLevel), in world directions;
// the Nether and the like light the second one from below (setupNetherLevel). The hand's normals are in its own
// space, which Minecraft dots with the same directions, so it stays lit the same way whichever way you look.
float mob_shade(vec3 normal) {
    const vec3 light0 = vec3(0.2, 1.0, -0.7) / 1.2369317;
    vec3 light1 = vec3(-0.2, hasCeiling ? -1.0 : 1.0, 0.7) / 1.2369317;
    float l0 = max(0.0, dot(light0, normal));
    float l1 = max(0.0, dot(light1, normal));
    return min(1.0, (l0 + l1) * 0.6 + 0.4);
}

// Fog distance (fog.glsl): round, or for terrain fog a cylinder around the camera. Minecraft measures it at each
// vertex and lets it run straight across the triangle (vertexDistance), which puts a little more fog inside a big
// triangle, like the sky's, than measuring every pixel would: the programs do the same.
float fog_distance(vec3 pos) {
    return fogShape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));
}

// How far into the fog a distance is, 0..1 (linear_fog's mix factor). A range that ends where it starts is no fog
// (Minecraft's own "no fog" starts it at the largest float; a program Iris gives no fog uniforms reads 0 and 0).
float fog_amount(float dist, float start, float end) {
    if (dist <= start || end <= start) {
        return 0.0;
    }
    return dist < end ? smoothstep(start, end, dist) : 1.0;
}

// The fog range for this draw. With Distant Horizons the ordinary distance fog moves out to the edge of its terrain,
// the way Minecraft's own sits at the edge of the render distance, so the two meet without a seam. Fog in water,
// lava, powder snow, blindness and foggy biomes ends well before the render distance and stays as it is. shape:
// the fog's shape (fogShape), for passes Iris gives none.
vec2 fog_range_shaped(int shape) {
#ifdef DISTANT_HORIZONS
    if (dhRenderDistance > 0 && shape == 1 && fogEnd >= 0.95 * far) {
        float end = float(dhRenderDistance);
        return vec2(end - clamp(end / 10.0, 4.0, 64.0), end);
    }
#endif
    return vec2(fogStart, fogEnd);
}

vec2 fog_range() {
    return fog_range_shaped(fogShape);
}

vec3 apply_fog(vec3 color, float dist) {
    vec2 range = fog_range();
    return mix(color, fogColor, fog_amount(dist, range.x, range.y));
}

// What glows and adds itself to the scene (enchantment glint, eyes, lightning) fades out in the fog instead
// (linear_fog_fade).
float fog_fade(float dist) {
    vec2 range = fog_range();
    if (dist <= range.x || range.y <= range.x) {
        return 1.0;
    }
    return dist >= range.y ? 0.0 : smoothstep(range.y, range.x, dist);
}

#endif

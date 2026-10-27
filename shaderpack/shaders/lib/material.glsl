// Materials: how a surface reflects light, per pixel of its texture where the resource pack has maps for it (see
// material_maps.glsl), else by what block.properties says the block is made of (SURFACE_, see gbuffer.glsl).
//
// The maps are LabPBR 1.3's (shaderLABS' wiki), the format SPBR and most PBR resource packs use. Iris binds them
// beside each texture as normals and specular:
// - normals: rg the facing in the texture's own space (x along it, y down it; z follows), b the texture's own
//   occlusion (its crevices; 1 none), a its height (1 the top, 0 a quarter of the texture's width down).
// - specular: r how smooth (perceptually: GGX's roughness is (1 - r)²), g how much it reflects head on (F0, linear, up
//   to 229/255) or which metal it is (230 to 237 eight measured ones, tinted by the albedo; above them a metal that
//   reflects its albedo), b how porous (up to 64/255) or how much light passes into it (above), a how brightly it
//   glows (up to 254/255; 255 not at all).
// Iris fills the maps of a texture the pack has none for with a flat facing and a specular of all 0, which no real
// material has: the pack's own material then.

#if !defined LIB_MATERIAL
#define LIB_MATERIAL

#include "/lib/gbuffer.glsl"

struct Material {
    float alpha;    // GGX roughness (alpha)
    vec3 f0;        // how much it reflects head on
    float metal;    // 1 a metal, reflecting in its own colour; 0 a colour lit through a clear sheen
    float porosity; // how much rain it takes in, 0..1
};

// How much water a surface of a kind (SURFACE_) takes in: sand and soil the most, leaves, metal and polished stone
// hardly (the water stays on them as a film).
float porosity_of(int kind) {
    return kind == SURFACE_SAND ? 1.0 : kind == SURFACE_SOIL ? 0.9 : kind == SURFACE_LEAVES ? 0.25
            : kind == SURFACE_METAL ? 0.05 : kind == SURFACE_POLISHED ? 0.15 : 0.6;
}

// The pack's own materials by kind: a faint, broad sheen on everything (as every dielectric has, F0 4%), sharper on
// polished stone; metal reflects its own colour.
Material material_of_kind(int kind, vec3 albedo) {
    Material m;
    m.alpha = kind == SURFACE_POLISHED ? 0.12 : kind == SURFACE_METAL ? 0.3 : kind == SURFACE_LEAVES ? 0.5
            : kind == SURFACE_SAND || kind == SURFACE_SOIL ? 0.8 : 0.6;
    m.f0 = kind == SURFACE_METAL ? albedo : vec3(0.04);
    m.metal = kind == SURFACE_METAL ? 1.0 : 0.0;
    m.porosity = porosity_of(kind);
    return m;
}

#ifdef LIT_MATERIALS
// LabPBR's measured metals (230 iron, gold, aluminium, chrome, copper, lead, platinum, 237 silver): F0 from their
// complex indices of refraction, ((n - 1)² + k²) / ((n + 1)² + k²) for red, green and blue.
const vec3 labpbr_metal_f0[8] = vec3[8](
    vec3(0.531, 0.512, 0.496), vec3(0.944, 0.776, 0.373), vec3(0.912, 0.914, 0.920), vec3(0.556, 0.555, 0.555),
    vec3(0.926, 0.721, 0.504), vec3(0.632, 0.626, 0.641), vec3(0.679, 0.642, 0.588), vec3(0.962, 0.949, 0.922));

// Whether colortex12 holds a material from the maps.
bool has_maps(vec3 data) {
    return any(greaterThan(data, vec3(0.0)));
}

// The material from colortex12's rgb (r the smoothness, already widened where the facing changes fast across a pixel,
// see material_maps.glsl; g and b LabPBR's), on a surface of a kind with albedo (linear). What the maps leave at 0
// (no reflectance or porosity given) is the kind's.
Material material_from_maps(vec3 data, int kind, vec3 albedo) {
    Material m = material_of_kind(kind, albedo);
    float rough = 1.0 - data.r;
    m.alpha = max(rough * rough, 0.002);
    int g = int(data.g * 255.0 + 0.5);
    if (g >= 230) {
        m.metal = 1.0;
        // A measured metal takes its albedo's hue (at its own strength); the rest reflect their albedo.
        m.f0 = g <= 237 ? labpbr_metal_f0[g - 230] * albedo / max(max(albedo.r, max(albedo.g, albedo.b)), 0.05)
                : albedo;
    } else {
        m.metal = 0.0;
        m.f0 = vec3(g > 0 ? data.g : 0.04);
    }
    int b = int(data.b * 255.0 + 0.5);
    if (b > 0 && b <= 64) {
        m.porosity = float(b) / 64.0;
    }
    return m;
}
#endif

// The sun's (or the moon's) light the texture's own relief lets reach a point whose crevices leave ao (0..1) of the
// directions around it open, lit at n_dot_l: Call of Duty WWII's micro-shadowing (Chan 2018), which takes the open
// directions as a cone around the facing (as wide as ao says, sin² of its half angle) and lets the light fade out
// past its edge. A point with no crevices around it is left as it is.
float micro_shadow(float n_dot_l, float ao) {
    if (ao >= 0.999) {
        return 1.0;
    }
    float cos_cone = sqrt(clamp(1.0 - ao, 0.0, 1.0));
    float v = clamp(n_dot_l / max(cos_cone, 1e-3), 0.0, 1.0);
    return v * v;
}

#endif

// Hashes, low-discrepancy sequences and gradient noise, for the clouds. Ported from Alpha Piscium (GPL-3.0),
// util/Hash.glsl, util/Rand.glsl and util/noise: Mark Jarzynski and Marc Olano's PCG hashes ("Hash Functions for GPU
// Rendering", JCGT 2020), Martin Roberts' R2 sequences, and Inigo Quilez's gradient noise (MIT).

#if !defined LIB_NOISE
#define LIB_NOISE

uvec3 hash_pcg3d(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v = v ^ (v >> 16u);
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

uvec4 hash_pcg4d(uvec4 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.w;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v.w += v.y * v.z;
    v = v ^ (v >> 16u);
    v.x += v.y * v.w;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v.w += v.y * v.z;
    return v;
}

// The top 23 bits of a hash as a float in [0, 1).
vec2 hash_to_float(uvec2 v) {
    return uintBitsToFloat(0x3f800000u | (v >> 9u)) - 1.0;
}

vec3 hash_to_float(uvec3 v) {
    return uintBitsToFloat(0x3f800000u | (v >> 9u)) - 1.0;
}

// The R2 sequence's i-th point in one, two and three dimensions: evenly spread in [0, 1) however many are taken (i
// wraps at 65536, where float precision would start to run out).
float r2_seq1(uint i) {
    const float a = 1.0 - 1.0 / 1.6180339887498948;
    return 1.0 - fract(a * float(i & 0xFFFFu) - 0.5);
}

vec2 r2_seq2(uint i) {
    const float g = 1.32471795724474602596;
    const vec2 a = 1.0 - vec2(1.0 / g, 1.0 / (g * g));
    return 1.0 - fract(a * float(i & 0xFFFFu) - 0.5);
}

vec3 r2_seq3(uint i) {
    const float g = 1.22074408460575947536;
    const vec3 a = 1.0 - vec3(1.0 / g, 1.0 / (g * g), 1.0 / (g * g * g));
    return 1.0 - fract(a * float(i & 0xFFFFu) - 0.5);
}

// ---- Gradient noise in 2D, with a quintic interpolant.

vec2 gradient_2d_hash(uvec2 cell) {
    return hash_to_float(hash_pcg3d(uvec3(cell, 0x85EBCA77u)).xy) * 2.0 - 1.0;
}

// The noise's value (about -0.7..0.7) at x.
float gradient_noise_2d(vec2 x) {
    uvec2 i = uvec2(ivec2(floor(x)));
    vec2 w = fract(x);
    vec2 u = w * w * w * (w * (w * 6.0 - 15.0) + 10.0);
    float va = dot(gradient_2d_hash(i), w);
    float vb = dot(gradient_2d_hash(i + uvec2(1, 0)), w - vec2(1.0, 0.0));
    float vc = dot(gradient_2d_hash(i + uvec2(0, 1)), w - vec2(0.0, 1.0));
    float vd = dot(gradient_2d_hash(i + uvec2(1, 1)), w - vec2(1.0, 1.0));
    return mix(mix(va, vb, u.x), mix(vc, vd, u.x), u.y);
}

// The noise's gradient at x.
vec2 gradient_noise_2d_grad(vec2 x) {
    uvec2 i = uvec2(ivec2(floor(x)));
    vec2 w = fract(x);
    vec2 u = w * w * w * (w * (w * 6.0 - 15.0) + 10.0);
    vec2 du = 30.0 * w * w * (w * (w - 2.0) + 1.0);
    vec2 ga = gradient_2d_hash(i);
    vec2 gb = gradient_2d_hash(i + uvec2(1, 0));
    vec2 gc = gradient_2d_hash(i + uvec2(0, 1));
    vec2 gd = gradient_2d_hash(i + uvec2(1, 1));
    float va = dot(ga, w);
    float vb = dot(gb, w - vec2(1.0, 0.0));
    float vc = dot(gc, w - vec2(0.0, 1.0));
    float vd = dot(gd, w - vec2(1.0, 1.0));
    vec2 g = mix(mix(ga, gb, u.x), mix(gc, gd, u.x), u.y);
    vec2 d = mix(vec2(vb, vc) - va, vd - vec2(vc, vb), u.yx);
    return g + du * d;
}

mat2 rotate_2d(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c);
}

// Fractal sums of octaves, each turned by rotation and scaled by lacunarity from the one before.
float gradient_fbm_2d(vec2 x, float frequency, float persistence, float lacunarity, int octaves, mat2 rotation) {
    float value = 0.0;
    float amplitude = 1.0;
    x *= frequency;
    for (int i = 0; i < octaves; i++) {
        value += gradient_noise_2d(x) * amplitude;
        amplitude *= persistence;
        x = (rotation * x) * lacunarity;
    }
    return value;
}

vec2 gradient_fbm_2d_grad(vec2 x, float frequency, float persistence, float lacunarity, int octaves, mat2 rotation) {
    vec2 value = vec2(0.0);
    float amplitude = 1.0;
    x *= frequency;
    for (int i = 0; i < octaves; i++) {
        value += gradient_noise_2d_grad(x) * amplitude;
        amplitude *= persistence;
        x = (rotation * x) * lacunarity;
    }
    return value;
}

#endif

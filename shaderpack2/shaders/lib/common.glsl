// Small helpers every pass uses: constants, colour, normal encoding and hashes.

#if !defined LIB_COMMON
#define LIB_COMMON

const float PI = 3.14159265358979;
const float TAU = 6.28318530717959;
const float GOLDEN = 0.61803398874989;

float sqr(float x) {
    return x * x;
}

float max3(vec3 v) {
    return max(v.x, max(v.y, v.z));
}

float min3(vec3 v) {
    return min(v.x, min(v.y, v.z));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 srgb_to_linear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 linear_to_srgb(vec3 c) {
    c = max(c, 0.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

// Octahedral normal encoding, 0..1 per channel.
vec2 oct_wrap(vec2 v) {
    return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}

vec2 encode_normal(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
    n.xy = n.z >= 0.0 ? n.xy : oct_wrap(n.xy);
    return n.xy * 0.5 + 0.5;
}

vec3 decode_normal(vec2 e) {
    e = e * 2.0 - 1.0;
    vec3 n = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    float t = clamp(-n.z, 0.0, 1.0);
    n.xy += vec2(n.x >= 0.0 ? -t : t, n.y >= 0.0 ? -t : t);
    return normalize(n);
}

// Integer hashes (Jarzynski and Olano's PCG3D), for noise that is the same on every GPU.
uvec3 pcg3d(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

vec3 hash33(uvec3 v) {
    return vec3(pcg3d(v)) * (1.0 / 4294967296.0);
}

// Interleaved gradient noise (Jimenez 2014): a screen pattern that a few neighbours average out.
float ign(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// Builds two directions at right angles to n.
void basis(vec3 n, out vec3 t, out vec3 b) {
    float s = n.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (s + n.z);
    float c = n.x * n.y * a;
    t = vec3(1.0 + s * n.x * n.x * a, s * c, -s * n.x);
    b = vec3(c, s + n.y * n.y * a, -n.y);
}

vec3 cosine_direction(vec3 n, vec2 u) {
    float r = sqrt(u.x);
    float phi = TAU * u.y;
    vec3 t, b;
    basis(n, t, b);
    return normalize(t * (r * cos(phi)) + b * (r * sin(phi)) + n * sqrt(max(0.0, 1.0 - u.x)));
}

#endif

// The shadow pass's geometry stage for the world's blocks: each triangle twice. Once into the shadow map as it is,
// and once for the voxel copy of the world: laid flat on the plane its face turns to most, at the finest detail
// level whose box holds it, into the corner of the map where each fragment stands for one cell (shadow.fsh marks
// the cells and draws nothing). So the rasteriser does the voxelising, and the texture's alpha decides which cells a
// chain or a fence fills.
//
// Lights don't go into the copy: each triangle of a light adds its place and colour to the light hash (see
// lib/voxel.glsl), and its block's cell says it holds one. Plants (cross-shaped models, and block.properties'
// plants) and very small triangles stay out, so grass and flowers don't turn the light into noise.

#define PROGRAM_SHADOW
#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"
#include "/lib/blocks.glsl"

layout(triangles) in;
layout(triangle_strip, max_vertices = 6) out;

uniform sampler2D gtexture;
layout(r32ui) uniform uimage3D cg_vox_img;

in vec2 vs_uv[];
in vec4 vs_color[];
in vec3 vs_scene[];
in vec3 vs_normal[];
in vec3 vs_mid[];
in float vs_sky[];
flat in int vs_block[];
flat in float vs_emission[];

out vec2 fs_uv;
out vec4 fs_color;
out vec3 fs_scene;
out float fs_depth;        // voxel triangles: the cell coordinate along the axis the face was laid flat on
flat out int fs_block;
flat out int fs_voxel;     // -1 for the shadow map, else the detail level | the axis << 4
flat out vec3 fs_normal;   // the face's (world space, pointing out of it)
flat out uint fs_vox_bits; // voxel triangles: the bits its block's own cell takes (water, sky light)

// Minecraft's sky light (0..1) as 2 bits that OR to something in between (see lib/voxel.glsl).
uint sky_bits(float sky) {
    int step = int(sky * 15.0 + 0.5);
    uint v = step <= 4 ? 0u : step <= 8 ? 1u : step <= 12 ? 3u : 2u;
    return v << VOX_SKY_SHIFT;
}

void emit_shadow_triangle() {
    for (int i = 0; i < 3; i++) {
        gl_Position = gl_in[i].gl_Position;
        fs_uv = vs_uv[i];
        fs_color = vs_color[i];
        fs_scene = vs_scene[i];
        fs_depth = 0.0;
        fs_block = vs_block[i];
        fs_voxel = -1;
        fs_normal = vs_normal[i];
        fs_vox_bits = 0u;
        EmitVertex();
    }
    EndPrimitive();
}

// Adds this triangle to its block's entry in the light hash: where in the block it glows most and in what colour,
// both from a few looks at its texture (a torch's flame, not its stick).
void add_light(ivec3 block, vec3 p[3], int kind, float level) {
    uint base = light_claim(light_key(cameraPositionInt + block));
    if (base == light_none) {
        return;
    }
    vec3 centre = (p[0] + p[1] + p[2]) / 3.0;
    vec2 uv_centre = (vs_uv[0] + vs_uv[1] + vs_uv[2]) / 3.0;
    vec3 place = vec3(0.0);
    vec3 color = vec3(0.0);
    float weight = 0.0;
    for (int i = 0; i < 4; i++) {
        // The corners, a little in from the edge (so the atlas's next sprite stays out), and the middle.
        vec3 at = i < 3 ? mix(centre, p[i], 0.8) : centre;
        vec2 uv = i < 3 ? mix(uv_centre, vs_uv[i], 0.8) : uv_centre;
        vec4 texel = textureLod(gtexture, uv, 0.0);
        vec3 c = srgb_to_linear(texel.rgb * vs_color[min(i, 2)].rgb);
        float w = texel.a * (luminance(c) * luminance(c) + 0.002);
        place += at * w;
        color += c * w;
        weight += w;
    }
    vec3 local = clamp(place / max(weight, 1e-6) - vec3(block), 0.03, 0.97);
    vec4 listed = light_kind(kind);
    vec3 c = listed.r >= 0.0 ? listed.rgb : light_color_from_texture(color / max(weight, 1e-6));
    uvec3 q = uvec3(local * 32.0 + 0.5);
    uvec3 qc = uvec3(clamp(c, 0.0, 1.0) * 32.0 + 0.5);
    data_add(base + 1u, q.x | (q.y << 16u));
    data_add(base + 2u, q.z | (1u << 16u));
    data_add(base + 3u, qc.r | (qc.g << 16u));
    data_add(base + 4u, qc.b);
    data_max(base + 5u, (uint(level + 0.5) << 8u) | uint(kind + 1));
}

void voxelise() {
    vec3 p[3] = vec3[](vox_pos(vs_scene[0]), vox_pos(vs_scene[1]), vox_pos(vs_scene[2]));
    vec3 n = cross(p[1] - p[0], p[2] - p[0]);
    float area = 0.5 * length(n);
    if (area < 1e-6) {
        return;
    }
    n = normalize(n);
    if (dot(n, vs_normal[0]) < 0.0) {
        n = -n; // pointing out of the face, as Minecraft's normal does
    }

    int id = vs_block[0];
    ivec3 block = ivec3(floor(p[0] + vs_mid[0]));
    if (!vox_in_box(block)) {
        return;
    }
    uint own_bits = sky_bits(vs_sky[0]);

    // A light: into the hash, never into the copy (a light never shadows itself).
    bool listed = id >= BLOCK_LIGHT && id < BLOCK_LIGHT + LIGHT_KINDS;
    if (listed || vs_emission[0] >= 7.0) {
        int kind = listed ? id - BLOCK_LIGHT : -1;
        float level = listed ? light_kind(kind).a : unlisted_light_level(vs_emission[0]);
        add_light(block, p, kind, level);
        imageAtomicOr(cg_vox_img, vox_slot(block, 0), VOX_LIGHT | own_bits);
        return;
    }

    if (id == BLOCK_PLANT || (abs(n.y) < 0.2 && abs(abs(n.x) - abs(n.z)) < 0.2)) {
        return;
    }

    // The finest level whose box holds the whole triangle.
    vec3 lo = min(p[0], min(p[1], p[2]));
    vec3 hi = max(p[0], max(p[1], p[2]));
    int level = VOXEL_LEVELS - 1;
    for (; level > 0; level--) {
        float s = exp2(float(level));
        if (vox_in_box(ivec3(floor(lo * s)) - 1) && vox_in_box(ivec3(floor(hi * s)) + 1)) {
            break;
        }
    }

    // Too small to matter at its level (a chain link far off).
    float longest = max(distance(p[0], p[1]), max(distance(p[1], p[2]), distance(p[2], p[0])));
    if (longest < 0.6 * exp2(-0.42 * float(level))) {
        return;
    }

    // A full face on its block's boundary that faces into its own block (the inside of a water surface): behind it
    // is the next block, which it doesn't fill.
    if (area > 0.4) {
        float l01 = distance(p[0], p[1]);
        float l12 = distance(p[1], p[2]);
        float l20 = distance(p[2], p[0]);
        vec3 face_centre = l01 >= l12 && l01 >= l20 ? (p[0] + p[1]) * 0.5 : l12 >= l20 ? (p[1] + p[2]) * 0.5 : (p[2] + p[0]) * 0.5;
        vec3 block_centre = p[0] + vs_mid[0];
        if (abs(dot(face_centre - block_centre, n) + 0.5) < 0.06) {
            return;
        }
    }

    if (id == BLOCK_WATER) {
        own_bits |= VOX_WATER;
    }

    // Laid flat on the plane the face turns to most: u and v across it, w along it, in cells from the box's middle.
    vec3 a = abs(n);
    int axis = a.x > a.y && a.x > a.z ? 0 : a.y > a.z ? 1 : 2;
    vec2 half_plane = axis == 0 ? vec2(vox_half.z, vox_half.y) : axis == 1 ? vec2(vox_half.x, vox_half.z) : vec2(vox_half.xy);
    float s = exp2(float(level));
    vec2 uv[3];
    float w[3];
    for (int i = 0; i < 3; i++) {
        vec3 c = p[i] * s;
        uv[i] = axis == 0 ? c.zy : axis == 1 ? c.xz : c.xy;
        w[i] = axis == 0 ? c.x : axis == 1 ? c.y : c.z;
    }
    // Counter-clockwise on the screen, whichever way the face turns (in case the pass culls back faces).
    vec2 e1 = uv[1] - uv[0];
    vec2 e2 = uv[2] - uv[0];
    bool flip = e1.x * e2.y - e1.y * e2.x < 0.0;
    for (int k = 0; k < 3; k++) {
        int i = flip ? (3 - k) % 3 : k;
        vec2 pixel = uv[i] + half_plane;
        gl_Position = vec4(pixel / float(shadowMapResolution) * 2.0 - 1.0, -0.999, 1.0);
        fs_uv = vs_uv[i];
        fs_color = vs_color[i];
        fs_scene = vs_scene[i];
        fs_depth = w[i];
        fs_block = id;
        fs_voxel = level | (axis << 4);
        fs_normal = n;
        fs_vox_bits = own_bits;
        EmitVertex();
    }
    EndPrimitive();
}

void main() {
    emit_shadow_triangle();
    voxelise();
}

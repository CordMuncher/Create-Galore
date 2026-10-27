// The resource pack's material maps on a surface as it's drawn (see material.glsl): its facing, its crevices, how
// smooth it is and what it reflects, what glows; and on blocks its relief, by parallax: the view ray followed down
// into the height the normal map holds a texture pixel at a time, as if every pixel were a column of its own height
// (a 16x texture's pixels read as sharp-edged steps that way, where parallax in smooth steps smears them), and the
// point it meets shaded by the same columns towards the sun (Tatarchuk's parallax occlusion mapping, stepped through
// the pixels' grid as a voxel ray is, Amanatides and Woo).
//
// The texture's frame on the surface comes from how its texture coordinates change across the screen (Schüler 2006),
// which also says how many texture pixels a block holds, so it needs nothing from the vertices but the quad's own
// part of the atlas for the relief (v_region, see gbuffers.vsh).

#if !defined LIB_MATERIAL_MAPS
#define LIB_MATERIAL_MAPS

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/material.glsl"

uniform sampler2D normals;
uniform sampler2D specular;
#ifdef LIT_RELIEF
uniform ivec2 atlasSize;
#endif

// What the maps say about a pixel of a surface.
struct Maps {
    vec2 uv;          // where the textures are read (moved by the relief)
    vec3 normal;      // the facing, world space
    float ao;         // the texture's own crevices (1 none)
    float sun;        // how much of the sun's (or the moon's) light its relief lets reach it
    vec4 spec;        // the specular map as it is (all 0: none)
    float depth;      // how far along the view the relief took the point in (blocks)
};

// How fast u and v grow per block along a surface (world space) facing n at pos: their gradients across it.
void texture_gradients(vec3 pos, vec2 uv, vec3 n, out vec3 grad_u, out vec3 grad_v) {
    vec3 dp1 = dFdx(pos);
    vec3 dp2 = dFdy(pos);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, n);
    vec3 dp1perp = cross(n, dp1);
    float det = dot(dp1, dp2perp);
    float inv = abs(det) > 1e-12 ? 1.0 / det : 0.0;
    grad_u = (dp2perp * duv1.x + dp1perp * duv2.x) * inv;
    grad_v = (dp2perp * duv1.y + dp1perp * duv2.y) * inv;
}

// The facing a normal map texel holds (LabPBR's rg: x along u, y along v, z out of the surface), as unit vector in the
// texture's frame; flat where it holds none.
vec3 map_facing(vec2 rg) {
    vec2 xy = rg * 2.0 - 1.0;
    float zz = 1.0 - dot(xy, xy);
    return zz > 0.0 ? vec3(xy, sqrt(zz)) : vec3(0.0, 0.0, 1.0);
}

#ifdef LIT_RELIEF
// How deep the relief goes at its lowest (blocks): LabPBR's height 0 is a quarter of the texture's width down, and a
// block's texture is a block wide.
const float relief_depth = 0.25 * PARALLAX_DEPTH;
const int relief_steps = 48;
const int relief_shadow_steps = 24;

// How deep below the surface (blocks) the top of a texture pixel's column is, for depth the deepest a column goes.
// cell: the pixel within the quad's part of the atlas (region: where that starts, how many pixels across), wrapping
// round it as the texture repeats.
float column_depth(ivec2 cell, ivec4 region, float depth) {
    ivec2 wrapped = cell - region.zw * ivec2(floor(vec2(cell) / vec2(region.zw)));
    return (1.0 - texelFetch(normals, region.xy + wrapped, 0).a) * depth;
}

// Follows a ray from a point (in the region's pixels) along a direction (pixels per block of depth) down into the
// columns, as far as depth: how deep it met one (blocks), where (pixels), and the facing it met in the texture's
// frame (the top of a column, or the side of one towards the ray).
float relief_trace(vec2 start, vec2 dir, ivec4 region, float depth, out vec2 at, out vec3 facing, out ivec2 hit_cell) {
    ivec2 cell = ivec2(floor(start));
    ivec2 step_dir = ivec2(sign(dir));
    vec2 inv = vec2(abs(dir.x) > 1e-6 ? 1.0 / dir.x : 1e6, abs(dir.y) > 1e-6 ? 1.0 / dir.y : 1e6);
    float top = column_depth(cell, region, depth);
    facing = vec3(0.0, 0.0, 1.0);
    for (int i = 0; i < relief_steps; i++) {
        // How deep the ray is where it leaves this pixel across x and across y.
        vec2 next = vec2(cell) + step(0.0, dir);
        vec2 leave = abs((next - start) * inv);
        float out_depth = min(leave.x, leave.y);
        if (top <= out_depth) {
            at = start + dir * top;
            hit_cell = cell;
            return top; // the top of this column
        }
        bool across_x = leave.x < leave.y;
        if (across_x) {
            cell.x += step_dir.x;
        } else {
            cell.y += step_dir.y;
        }
        top = column_depth(cell, region, depth);
        if (top <= out_depth) {
            // The side of the next column, facing back along the ray.
            facing = across_x ? vec3(-float(step_dir.x), 0.0, 0.0) : vec3(0.0, -float(step_dir.y), 0.0);
            at = start + dir * out_depth;
            hit_cell = cell;
            return out_depth;
        }
    }
    at = start + dir * top;
    hit_cell = cell;
    return top;
}

// Whether the columns let the light (dir: pixels across per block it rises) reach a point at depth (blocks) in a
// pixel cell, at at (pixels): 1 lit, 0 a column stands in the way.
float relief_shadow(vec2 at, ivec2 cell, float point_depth, vec2 dir, ivec4 region, float depth) {
    vec2 inv = vec2(abs(dir.x) > 1e-6 ? 1.0 / dir.x : 1e6, abs(dir.y) > 1e-6 ? 1.0 / dir.y : 1e6);
    ivec2 step_dir = ivec2(sign(dir));
    for (int i = 0; i < relief_shadow_steps; i++) {
        vec2 next = vec2(cell) + step(0.0, dir);
        vec2 leave = abs((next - at) * inv);
        float risen = min(leave.x, leave.y);
        if (risen >= point_depth) {
            return 1.0; // out above the tallest column
        }
        if (leave.x < leave.y) {
            cell.x += step_dir.x;
        } else {
            cell.y += step_dir.y;
        }
        if (column_depth(cell, region, depth) < point_depth - risen - 1e-4) {
            return 0.0;
        }
    }
    return 1.0;
}
#endif

// The maps for a surface drawn at scene_pos (scene space) with texture coordinates uv, facing n (world space), seen
// along view_dir (from the eye, unit). region: the quad's part of the atlas (xy where it starts, zw its size, in uv)
// for the relief. light_dir: towards the sun or the moon.
Maps read_maps(vec3 scene_pos, vec2 uv, vec3 n, vec3 view_dir, vec4 region, vec3 light_dir) {
    vec3 grad_u;
    vec3 grad_v;
    texture_gradients(scene_pos, uv, n, grad_u, grad_v);
    vec2 duv_dx = dFdx(uv) * exp2(cg_mip_bias);
    vec2 duv_dy = dFdy(uv) * exp2(cg_mip_bias);
    Maps m;
    m.uv = uv;
    m.depth = 0.0;
    m.sun = 1.0;
    vec3 t = dot(grad_u, grad_u) > 0.0 ? normalize(grad_u) : vec3(0.0);
    vec3 b = dot(grad_v, grad_v) > 0.0 ? normalize(grad_v) : vec3(0.0);
    vec3 facing = vec3(0.0, 0.0, 1.0);
    bool side = false;
#ifdef LIT_RELIEF
    float dist = length(scene_pos);
    float depth = relief_depth * (1.0 - smoothstep(0.75 * float(PARALLAX_DISTANCE), float(PARALLAX_DISTANCE), dist));
    float facing_eye = -dot(view_dir, n);
    if (depth > 0.001 && facing_eye > 0.01 && region.z > 0.0 && region.w > 0.0) {
        vec2 atlas = vec2(atlasSize);
        ivec4 r = ivec4(ivec2(floor(region.xy * atlas + 0.5)), max(ivec2(floor(region.zw * atlas + 0.5)), ivec2(1)));
        vec2 start = uv * atlas - vec2(r.xy);
        // Pixels across per block of depth along the view.
        vec2 dir = vec2(dot(view_dir, grad_u) * atlas.x, dot(view_dir, grad_v) * atlas.y) / facing_eye;
        vec2 at;
        ivec2 cell;
        float met = relief_trace(start, dir, r, depth, at, facing, cell);
        side = facing.z == 0.0;
        // Read the textures inside the pixel it met (on a column's side, at its edge).
        vec2 inside = clamp(at, vec2(cell) + 0.001, vec2(cell) + 0.999);
        vec2 wrapped = inside - vec2(r.zw) * floor(inside / vec2(r.zw));
        m.uv = (vec2(r.xy) + wrapped) / atlas;
        m.depth = met / facing_eye;
        // The columns towards the light, from the open side of a column's side (which the light may not face at all).
        float facing_light = dot(light_dir, n);
        if (side && dot(facing.xy, vec2(dot(light_dir, t), dot(light_dir, b))) <= 0.0) {
            m.sun = 0.0;
        } else if (facing_light > 0.01 && met > 0.0) {
            vec2 to_light = vec2(dot(light_dir, grad_u) * atlas.x, dot(light_dir, grad_v) * atlas.y) / facing_light;
            vec2 from = side ? at + facing.xy * 0.001 : inside;
            m.sun = relief_shadow(from, ivec2(floor(from)), met, to_light, r, depth);
        }
    }
#endif
    // A texel at a time, as the colour is read: Iris leaves the maps' atlases to OpenGL's own filtering up close, which
    // blends each texel with its neighbours, and at a texture's edge with whatever texture lies next to it in the
    // atlas (a dark line along every block's edge, where that one's crevices and facing bleed in).
    vec2 maps_size = vec2(textureSize(normals, 0));
    vec2 texel_uv = (floor(m.uv * maps_size) + 0.5) / maps_size;
    vec4 nmap = textureGrad(normals, texel_uv, duv_dx, duv_dy);
    m.spec = textureGrad(specular, texel_uv, duv_dx, duv_dy);
    m.ao = side ? 1.0 : nmap.b;
    // A column's side faces across the texture; a top the way its normal map says.
    vec3 f = side ? facing : map_facing(nmap.rg);
    vec3 world = t * f.x + b * f.y + n * f.z;
    m.normal = dot(world, world) > 1e-8 ? normalize(world) : n;
    return m;
}

// How smooth a surface reads (perceptually, 0..1) once the facing's changes across a pixel are folded into its
// roughness, so a normal map's fine bumps don't sparkle from frame to frame (Tokuyoshi and Kaplanyan 2021, the
// isotropic form for a G-buffer: GGX's alpha² grows by the facing's variance over the pixel, up to 0.18).
float filtered_smoothness(float smoothness, vec3 normal) {
    vec3 dx = dFdx(normal);
    vec3 dy = dFdy(normal);
    float variance = (dot(dx, dx) + dot(dy, dy)) / (2.0 * 3.14159265358979);
    float alpha = (1.0 - smoothness) * (1.0 - smoothness);
    float filtered = sqrt(alpha * alpha + min(variance, 0.18));
    return 1.0 - sqrt(filtered);
}

#endif

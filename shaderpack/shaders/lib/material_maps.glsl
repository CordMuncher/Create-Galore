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
    float variance;   // how much the facing varies within the pixel that the mipmap averaged away
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

// How much of the light all around reaches a point down among the columns (0..1), from the columns around it, in
// texture pixels (the height field's own occlusion, as horizon-based ambient occlusion estimates it from the heights
// around). A groove's floor: from how far the columns beside it stand above it; one a pixel wide and a pixel deep sees
// about half of the sky. A column's side faces across the pixel the ray came over, which lies lower: it sees the sky
// above the tallest column across from it within a few pixels (out_dir: the way it faces), and below that the floor
// and the columns, so a step's side sees half of the light all around (what its own facing takes in), a groove's side
// less the deeper down it is. cell: the pixel whose top or side it is.
float relief_occlusion(ivec2 cell, ivec4 region, float depth, float point_depth, ivec2 out_dir, bool side) {
    float texel = 1.0 / float(region.z); // blocks a pixel
    if (side) {
        // How steeply the columns across rise over the point (the tangent of the angle above the surface they hide).
        float rise = 0.0;
        for (int k = 2; k <= 4; k++) {
            float above = (point_depth - column_depth(cell + k * out_dir, region, depth)) / texel;
            rise = max(rise, above / float(k - 1));
        }
        // Over the side's half of the directions, weighted by how squarely they meet it: those above that angle.
        return 0.5 * (1.0 - rise * inversesqrt(1.0 + rise * rise));
    }
    float below = max(point_depth - column_depth(cell + ivec2(1, 0), region, depth), 0.0)
            + max(point_depth - column_depth(cell - ivec2(1, 0), region, depth), 0.0)
            + max(point_depth - column_depth(cell + ivec2(0, 1), region, depth), 0.0)
            + max(point_depth - column_depth(cell - ivec2(0, 1), region, depth), 0.0);
    return 1.0 / (1.0 + 0.25 * below / texel);
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
    m.variance = 0.0;
    float crevice = 1.0;
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
        crevice = relief_occlusion(cell, r, depth, met, ivec2(facing.xy), side);
    }
#endif
    // A texel at a time, as the colour is read: Iris leaves the maps' atlases to OpenGL's own filtering up close, which
    // blends each texel with its neighbours, and at a texture's edge with whatever texture lies next to it in the
    // atlas (a dark line along every block's edge, where that one's crevices and facing bleed in).
    vec2 maps_size = vec2(textureSize(normals, 0));
    vec2 texel_uv = (floor(m.uv * maps_size) + 0.5) / maps_size;
    vec4 nmap = textureGrad(normals, texel_uv, duv_dx, duv_dy);
    m.spec = textureGrad(specular, texel_uv, duv_dx, duv_dy);
    // A groove's floor: the crevices the map holds and the ones the columns make, the latter half counted (a map's
    // own crevices often hold them already); a groove's side: the columns' alone.
    m.ao = side ? crevice : nmap.b * mix(1.0, crevice, 0.5);
    // A column's side faces across the texture, but sees the sky only above the columns across from it: the light all
    // around comes to it from halfway between that angle and straight out of the surface (its bent normal), so the
    // side of a groove in a wall takes the light around the wall, not the deep blue overhead.
    float above = 1.0 - 2.0 * crevice; // the sine of the angle the columns across hide (0 a step's side)
    vec3 f = side ? normalize(facing * sqrt(max(1.0 - above * above, 0.0)) + vec3(0.0, 0.0, 1.0 + above))
            : map_facing(nmap.rg);
    vec3 world = t * f.x + b * f.y + n * f.z;
    m.normal = dot(world, world) > 1e-8 ? normalize(world) : n;
    // Far off, where a pixel spans several texture pixels, the mipmap averages their facings flatter, and the bumps
    // and their sheen would fade: what it averages away (how the facing varies over the pixel's footprint, read two
    // mip levels finer) goes into the roughness instead, as Toksvig's and LEAN mapping's do.
    vec2 footprint = vec2(length(duv_dx * maps_size), length(duv_dy * maps_size));
    if (!side && max(footprint.x, footprint.y) > 1.0) {
        vec2 fine_dx = 0.25 * duv_dx;
        vec2 fine_dy = 0.25 * duv_dy;
        // (Kept inside the texture's own part of the atlas, so its neighbours there don't count.)
        vec2 lo = region.xy + 0.5 / maps_size;
        vec2 hi = max(region.xy + region.zw - 0.5 / maps_size, lo);
        bool bounded = region.z > 0.0 && region.w > 0.0;
        vec2 ua = m.uv + 0.25 * (duv_dx + duv_dy);
        vec2 uc = m.uv + 0.25 * (duv_dx - duv_dy);
        vec2 ud = m.uv - 0.25 * (duv_dx + duv_dy);
        vec2 ue = m.uv - 0.25 * (duv_dx - duv_dy);
        if (bounded) {
            ua = clamp(ua, lo, hi);
            uc = clamp(uc, lo, hi);
            ud = clamp(ud, lo, hi);
            ue = clamp(ue, lo, hi);
        }
        vec2 a = textureGrad(normals, ua, fine_dx, fine_dy).rg * 2.0 - 1.0;
        vec2 c = textureGrad(normals, uc, fine_dx, fine_dy).rg * 2.0 - 1.0;
        vec2 d = textureGrad(normals, ud, fine_dx, fine_dy).rg * 2.0 - 1.0;
        vec2 e = textureGrad(normals, ue, fine_dx, fine_dy).rg * 2.0 - 1.0;
        vec2 mean = 0.25 * (a + c + d + e);
        vec2 coarse = nmap.rg * 2.0 - 1.0;
        m.variance = 0.25 * (dot(a - mean, a - mean) + dot(c - mean, c - mean) + dot(d - mean, d - mean)
                + dot(e - mean, e - mean)) + dot(mean - coarse, mean - coarse);
        m.variance *= smoothstep(1.0, 2.0, max(footprint.x, footprint.y));
    }
    return m;
}

// How smooth a surface reads (perceptually, 0..1) once the facing's changes across a pixel are folded into its
// roughness, so a normal map's fine bumps don't sparkle from frame to frame (Tokuyoshi and Kaplanyan 2021, the
// isotropic form for a G-buffer: GGX's alpha² grows by the facing's variance over the pixel, up to 0.18). within:
// the variance inside the pixel the mipmap averaged away (Maps.variance).
float filtered_smoothness(float smoothness, vec3 normal, float within) {
    vec3 dx = dFdx(normal);
    vec3 dy = dFdy(normal);
    float variance = (dot(dx, dx) + dot(dy, dy)) / (2.0 * 3.14159265358979) + within;
    float alpha = (1.0 - smoothness) * (1.0 - smoothness);
    float filtered = sqrt(alpha * alpha + min(variance, 0.18));
    return 1.0 - sqrt(filtered);
}

#endif

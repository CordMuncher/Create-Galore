// The pack's buffers, and how the G-buffer packs a surface.
//
// colortex0  the scene: light, before exposure and tone mapping (RGBA16F)
// colortex1  the surface's colour (sRGB) and what it is (MAT_)
// colortex2  its normals: the texture's in xy, the face's in zw (octahedral)
// colortex3  Minecraft's block light (x) and sky light (y), the corner shading (z), how much it glows (w)
// colortex4  how smooth (x) and how metallic (y) it is, how much light goes through it (z)
// colortex7  motion vectors for DLSS: rg render pixels towards last frame, b what drew it, a whether it takes over
/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const int colortex2Format = RGBA16;
const int colortex3Format = RGBA8;
const int colortex4Format = RGBA8;
const int colortex7Format = RGBA16F;
const bool colortex0Clear = true;
const bool colortex1Clear = true;
const bool colortex2Clear = true;
const bool colortex3Clear = true;
const bool colortex4Clear = true;
const bool colortex7Clear = true;
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex7ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int shadowcolor0Format = RGBA8;
const bool shadowcolor0Clear = true;
const vec4 shadowcolor0ClearColor = vec4(1.0, 1.0, 1.0, 1.0);
const int shadowcolor1Format = R8;
const bool shadowcolor1Clear = true;
const vec4 shadowcolor1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/

#if !defined LIB_BUFFERS
#define LIB_BUFFERS

#include "/lib/common.glsl"

// What a G-buffer pixel is (colortex1's alpha, times 255).
#define MAT_NONE 0     // the sky
#define MAT_BLOCK 1
#define MAT_PLANT 2    // light goes through softly
#define MAT_LEAVES 3
#define MAT_ENTITY 4
#define MAT_HAND 5
#define MAT_LOD 6      // Distant Horizons
#define MAT_SELF_LIT 7 // particles and the like: lit by their own program, colortex0 holds the result
#define MAT_MACHINE 8  // Create's machines through Colorwheel

struct Surface {
    vec3 albedo;    // linear
    int material;
    vec3 normal;    // the texture's, world space
    vec3 face;      // the face's
    float block;    // Minecraft's block light, 0..1
    float sky;      // Minecraft's sky light, 0..1
    float ao;       // Minecraft's corner shading
    float emission; // 0..1
    float smoothness;
    float metal;
    float subsurface;
};

#endif

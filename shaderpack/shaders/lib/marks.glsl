// Reading the marks the shadow pass leaves for the passes after it (cg_water_marks_img: the water's tops, the sky
// grid, the shore map; cg_voxel_img: the light volume's blocks). A program that defines READ_IMAGES before its
// includes reads them as images, which take none of its texture units (the overworld's deferred pass, which needs
// nearly all of them); the others read them through their samplers.

#ifndef CG_MARKS
#define CG_MARKS

#ifdef READ_IMAGES
layout(r32ui) readonly uniform uimage2D cg_water_marks_img;
layout(r32ui) readonly uniform uimage3D cg_voxel_img;

uint water_marks(ivec2 texel) {
    return imageLoad(cg_water_marks_img, texel).r;
}

uint voxel_marks(ivec3 texel) {
    return imageLoad(cg_voxel_img, texel).r;
}
#else
uniform usampler2D cg_water_marks_tex; // cg_water_marks_img
uniform usampler3D cg_voxel_tex;       // cg_voxel_img

uint water_marks(ivec2 texel) {
    return texelFetch(cg_water_marks_tex, texel, 0).r;
}

uint voxel_marks(ivec3 texel) {
    return texelFetch(cg_voxel_tex, texel, 0).r;
}
#endif

#endif

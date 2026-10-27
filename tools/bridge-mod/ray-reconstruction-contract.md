# DLSS Ray Reconstruction: what a shader pack hands over

The Bridge runs NVIDIA DLSS Ray Reconstruction (RR) in DLSS's place when a pack hands over RR's guides and the player
turns it on (dev builds: Upscaling tab, **Ray Reconstruction**; settings key `dlaaRayReconstruction=true`). The first
pack to do it is the Photon pilot ("Photon v1.3a + DLAA", patch 13, see `PhotonPatcher`), a test bed only. This is the
contract our own shader pack fills later. It adds to the DLSS contract in `shaders/create_galore_dlss.properties` (see
`PackProfile`): everything DLSS needs (colour, motion vectors, depth, exposure, the jitter) stays as it is.

Source: NVIDIA's DLSS-RR Integration Guide in the DLSS SDK (v310.9.16, the tag the Bridge is built with), sections
3.4 (inputs), 3.6 to 3.8 and the appendix (specular albedo).

## The profile

One more key in `shaders/create_galore_dlss.properties`:

```
rrGuideImages=cg_rr_albedo_img,cg_rr_specular_img,cg_rr_normal_img,cg_rr_hitdist_img
```

Four Iris custom images, in that order, declared in `shaders.properties` at the window's size (relative 1.0), not
cleared (the pack writes every pixel it renders):

| Image | Format | Holds |
| --- | --- | --- |
| diffuse albedo | `rgba8` | the surface's diffuse colour, linear, 0 to 1; alpha unused |
| specular albedo | `rgba8` | the specular reflectance after the view angle and roughness (below); alpha unused |
| normal and roughness | `rgba16f` | the normal in **world space**, unit length, in rgb; **linear** roughness (0 to 1, not GGX alpha) in alpha |
| specular hit distance | `r32f` | optional: the world-space distance from the surface to what its reflection ray hit |

```
image.cg_rr_albedo_img = none rgba rgba8 unsigned_byte false true 1.0 1.0
image.cg_rr_specular_img = none rgba rgba8 unsigned_byte false true 1.0 1.0
image.cg_rr_normal_img = none rgba rgba16f half_float false true 1.0 1.0
image.cg_rr_hitdist_img = none red r32f float false true 1.0 1.0
```

At a render scale below 1 the pack renders into the lower left corner, as for DLSS; the guides follow the same
pixels. A pack with fewer than three guide images gets DLSS.

## The uniforms (Iris, per frame)

| Uniform | 1 when |
| --- | --- |
| `cg_rr_active` | the Bridge reads the guides this frame (RR runs, or a guide debug view shows): write them |
| `cg_rr_exposed` | RR runs and wants the colour **times the pack's exposure** (RR takes no exposure; the Bridge divides it back out after) |
| `cg_rr_raw_reflections` | leave the reflections noisy for RR (one ray, white noise, no own denoiser or history) |
| `cg_rr_raw_shadows` | leave soft shadows noisy for RR (white noise, no own filter) |

With all of them 0 the pack must look exactly as it does without RR: write nothing, change nothing.

## Values, as NVIDIA asks

- **Sky** (nothing there): diffuse albedo 0.5 grey, specular albedo 0, normal 0 and roughness 0, hit distance 65504.
- **Diffuse albedo**: metals get their small diffuse share (in Photon, `metal_diffuse_amount`), not their colour.
- **Specular albedo**: NVIDIA's `EnvBRDFApprox2` (appendix; Ray Tracing Gems, chapter 32) of the specular colour (F0:
  dielectrics about 0.04 grey, metals their colour), the GGX alpha and N·V. The GLSL port in the pilot's
  `cg_rr.glsl` (`cg_specular_albedo`) can be copied: HLSL's `float2x2(a, b, c, d)` lists rows and GLSL's `mat2`
  columns, so `mul(M, x)` there is `x * M` here.
- **Normals**: the shading normal (with normal maps), world space, not view space.
- **Roughness**: linear (perceptual). Photon's is GGX alpha, so the pilot writes `sqrt(alpha)`.
- **Hit distance**: per pixel, the distance the reflection ray travelled to its hit; 65504 when it hit the sky or
  nothing, and for surfaces with no reflection.
- **Depth and matrices**: the DLSS depth (1 near, 0 sky, reversed infinite). The Bridge hands RR the world-to-view
  matrix (`gbufferModelView`) and a view-to-clip matrix that matches that depth (near over clip w), flipped in y for
  the textures' bottom-up rows; nothing for the pack to do.

## Rules for the effects RR cleans up

- **White noise only**: per pixel, per frame, uncorrelated (a hash of pixel, frame and a salt per effect; the pilot uses
  `pcg4d`). No blue noise, no interleaved gradient noise, no R1 sequences, no checkerboards or shared patterns: RR
  reads a pattern as detail and keeps it.
- **No own denoising** of what RR gets: no temporal accumulation, no spatial blur, no history. RR replaces them.
- **One sample** is enough; RR is built for one ray a pixel.
- **Depth of field after RR** (or NVIDIA's DOF guide with preset F). Bloom, tone mapping, film grain and the like after
  RR as well: RR runs where DLSS runs, before the pack's tone mapping.
- **Translucent surfaces** (water, glass): depth, motion vectors and the normal, roughness and hit distance follow the
  front surface, as for DLSS; the diffuse albedo may stay that of what lies behind. Particles and other things with no
  depth are a later step (RR's transparency overlay guide; the pilot has none).
- **What RR is for**: reflections (rough ones most), soft shadows, ambient occlusion and global illumination done with
  rays. Effects that are already smooth (Photon's clouds, fog, light shafts) need nothing.

## Cost and cards

RR replaces DLSS's upscaling; NVIDIA's numbers at 1440p Performance: about 1.7 ms on an RTX 4080, about 6 ms on an
RTX 3070 (laptop players stay on DLSS). Any RTX card with NVIDIA driver 580 or newer; `nvngx_dlssd.dll` is the
player's own, like `nvngx_dlss.dll` (**Find on this PC** copies it). Preset F (RR 4.5) is the one we use.

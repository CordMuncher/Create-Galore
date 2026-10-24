AMD FSR 3.1 upscaler shaders for the Create Galore Bridge (OpenGL 4.5 compute), see dev.creategalore.bridge.dlss.Fsr3Upscaler.

From the AMD FidelityFX SDK v1.1.4 (tag v1.1.4, commit c6efa6bf7f2027b3ec94f28578bb5965eabb9e55), MIT licence in
LICENSE-FidelityFX-SDK.txt: sdk/include/FidelityFX/gpu (headers) and sdk/src/backends/vk/shaders/fsr3upscaler (passes).
Only the files the passes below use are here. Changes for OpenGL, everything else is AMD's code as released:

- ffx_fsr3upscaler_callbacks_gl.h (from ffx_fsr3upscaler_callbacks_glsl.h): no descriptor sets or bindings, the Bridge
  binds textures, images and uniform blocks by name; sampler2D in place of texture2D + the two samplers (the Bridge
  gives every texture a linear clamp sampler, the integer one a nearest one); rw_luma_history is rgba16f as in the
  DirectX 12 version; each SPD mip image is declared only if the pass binds it, and the luma pyramid binds only mip 5
  (NVIDIA counts every declared image against its 8 per compute shader; with all six the pass had 9 and didn't link);
  the transparency and composition mask is read from green, as the Bridge hands both masks over in one texture
  (reactive in red).
- ffx_core_glsl.h: GL_EXT_shader_explicit_arithmetic_types only with FFX_HALF (OpenGL drivers may not know it).
- spd/ffx_spd.h: an image memory barrier before a group counts itself done, so the last group sees every mip 5 store.
- Passes: the Vulkan-only extensions removed (include directive, samplerless textures, formatless image loads) and
  the OpenGL callbacks included. The Bridge compiles them with FFX_SPD_NO_WAVE_OPERATIONS (no subgroup extensions
  needed) and FFX_HALF 0.

tools/bridge-mod/fsr3-check.py counts the images each pass declares (at most 8 for NVIDIA) and validates every pass
with glslang, so limits Mesa doesn't enforce show up before a test on an NVIDIA card.

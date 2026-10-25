// The pack's options, as Iris shows them (shaders.properties lays out the screen, lang/ names them).

#if !defined LIB_SETTINGS
#define LIB_SETTINGS

// Upscaling: render the world smaller and let DLSS (or FSR) bring it up to the window's size. Off, they only
// anti-alias at full size (DLAA). The Create Galore Bridge reads both options from the pack
// (create_galore_dlss.properties) and sets the render size the programs see (taau_render_scale).
//#define UPSCALING
#define RENDER_SCALE 0.67 // [0.33 0.5 0.58 0.67 0.77 0.85]

#ifdef UPSCALING
const float upscaling_render_scale = RENDER_SCALE;
#endif

#endif

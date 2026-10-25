// The pack's buffers. Iris reads these directives from the programs that include this file.
//
// colortex0: the scene, RGBA16F because DLSS and FSR take it in that format (in linear light after the composite
//            pass). Cleared to the fog colour every frame, as Minecraft clears its own.
// colortex7: motion vectors for the composite pass (see gbuffers_kind.glsl), cleared to "no motion of its own".
/*
const int colortex0Format = RGBA16F;
const int colortex7Format = RGBA16F;
const bool colortex7Clear = true;
const vec4 colortex7ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/

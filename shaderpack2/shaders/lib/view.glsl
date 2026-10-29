// The camera's matrices and positions that the programs share. "Scene" space is world-aligned and centred on the
// camera (Iris's player space); "view" space is the camera's own.

#if !defined LIB_VIEW
#define LIB_VIEW

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform vec3 cameraPosition;
// The camera's place in whole blocks and the rest, this frame and last (exact far from the world's origin).
uniform ivec3 cameraPositionInt;
uniform vec3 cameraPositionFract;
uniform ivec3 previousCameraPositionInt;
uniform vec3 previousCameraPositionFract;

#endif

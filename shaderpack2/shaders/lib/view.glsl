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

// Whether a box (vox_pos space: the camera's block at the origin, see lib/voxel.glsl; its centre and half size) is in
// view, give or take margin blocks: in front of the camera and inside the four sides of the screen.
bool in_view(vec3 centre, float radius, float margin) {
    vec3 view = (gbufferModelView * vec4(centre - cameraPositionFract, 1.0)).xyz;
    mat4 m = gbufferProjection;
    vec4 row0 = vec4(m[0][0], m[1][0], m[2][0], m[3][0]);
    vec4 row1 = vec4(m[0][1], m[1][1], m[2][1], m[3][1]);
    vec4 row2 = vec4(m[0][2], m[1][2], m[2][2], m[3][2]);
    vec4 row3 = vec4(m[0][3], m[1][3], m[2][3], m[3][3]);
    vec4 planes[5] = vec4[](row3 + row0, row3 - row0, row3 + row1, row3 - row1, row3 + row2);
    for (int i = 0; i < 5; i++) {
        float len = length(planes[i].xyz);
        if (dot(planes[i].xyz, view) + planes[i].w < -(radius + margin) * len) {
            return false;
        }
    }
    return true;
}

#endif

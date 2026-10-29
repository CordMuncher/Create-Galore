// Empties the light hash at the start of each frame, before the shadow pass fills it again (see lib/voxel.glsl).

#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"

layout(local_size_x = 256) in;
const ivec3 workGroups = ivec3(2048, 1, 1); // 65536 slots of 8 words

void main() {
    data_store(data_hash_base + gl_GlobalInvocationID.x, 0u);
}

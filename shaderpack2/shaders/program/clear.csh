// Empties the light hash at the start of each frame, before program/world.csh fills it again (see lib/voxel.glsl).
// And notes when the tables are fresh (the pack was just loaded: the image holds whatever its memory held), so the
// block light works everything out again rather than trusting its frame stamps (program/voxel_light.csh).

#include "/lib/settings.glsl"
#include "/lib/voxel.glsl"

layout(local_size_x = 256) in;
const ivec3 workGroups = ivec3(2048, 1, 1); // 65536 slots of 8 words

uniform int frameCounter;

void main() {
    data_store(data_hash_base + gl_GlobalInvocationID.x, 0u);
    if (gl_GlobalInvocationID.x == 0u && data_load(data_fresh_mark) != data_fresh_magic) {
        data_store(data_fresh_mark, data_fresh_magic);
        data_store(data_fresh_frame, uint(frameCounter));
    }
}

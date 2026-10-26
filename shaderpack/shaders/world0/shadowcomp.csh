#version 430 compatibility
#define WORLD_OVERWORLD
// The overworld's first shadowcomp pass does nothing but let Iris put a memory barrier after it (it puts one after
// every compute pass), so the light flood (shadowcomp1) reads every block the shadow pass marked.

layout(local_size_x = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

void main() {
}

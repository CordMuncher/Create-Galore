// Nothing to do: the first pass after the shadow pass. It once made sure the shadow pass's voxel writes had landed
// before the distance field read them (Iris puts a memory barrier after each compute pass, not after the shadow pass);
// the copy now comes from program/world.csh, a compute pass, so it only keeps the passes' numbers.

layout(local_size_x = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

void main() {
}

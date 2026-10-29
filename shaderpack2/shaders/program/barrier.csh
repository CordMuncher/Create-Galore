// Nothing to do: the first pass after the shadow pass only makes sure the voxel copy's writes have landed before the
// distance field reads them (Iris puts a memory barrier after each compute pass, not after the shadow pass).

layout(local_size_x = 1) in;
const ivec3 workGroups = ivec3(1, 1, 1);

void main() {
}

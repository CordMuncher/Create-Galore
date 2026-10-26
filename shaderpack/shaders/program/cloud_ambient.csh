// The begin pass that works out the light around the clouds (see clouds.glsl), after the frame's light: for each of
// 16 by 16 directions the eye may look along, the sky above and the ground below, each weighted by how much a cloud
// scatters it towards the eye (Alpha Piscium's even mix of a forward and a backward lobe), from 256 directions
// spread evenly over the sphere. The same directions every frame, so the light holds still.

#include "/lib/clouds.glsl"

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(16, 16, 1);
layout(rgba16f) writeonly uniform image2D cg_cloud_amb_img;

shared vec3 shared_sum[64];

void main() {
    uint index = gl_LocalInvocationIndex;
    ivec2 texel = ivec2(gl_WorkGroupID.xy);
    vec3 view = cloud_ambient_dir(texel);
    // The ground's radiance, as the frame's light has it (see frame_globals.csh).
    vec3 ground = global(G_SKY_CUBE + 3).rgb / PI;

    vec3 sum = vec3(0.0);
    for (int k = 0; k < 4; k++) {
        int i = int(index) * 4 + k;
        // A Fibonacci lattice over the sphere.
        float y = 1.0 - (2.0 * float(i) + 1.0) / 256.0;
        float r = sqrt(max(0.0, 1.0 - y * y));
        float phi = float(i) * 2.39996323;
        vec3 dir = vec3(r * cos(phi), y, r * sin(phi));
        vec3 radiance = dir.y > 0.0 ? sky_radiance(dir) + global(G_FLASH).rgb : ground;
        float c = dot(view, dir);
        sum += radiance * mix(phase_cornette_shanks(c, 0.9), phase_cornette_shanks(c, -0.6), 0.5);
    }
    shared_sum[index] = sum * (4.0 * PI / 256.0);
    barrier();
    for (uint stride = 32u; stride > 0u; stride >>= 1u) {
        if (index < stride) {
            shared_sum[index] += shared_sum[index + stride];
        }
        barrier();
    }
    if (index == 0u) {
        imageStore(cg_cloud_amb_img, texel, vec4(shared_sum[0], 1.0));
    }
}

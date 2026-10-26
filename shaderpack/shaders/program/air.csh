// The begin passes that remake the air's look-up tables every frame (see atmosphere.glsl), each one a world0
// begin program that picks its part:
// - AIR_TRANSMITTANCE (begin1): how much light gets through the air, 40 steps a texel;
// - AIR_MULTISCATTER (begin2): the light scattered more than once, 64 directions a texel, 20 steps each;
// - AIR_SKYVIEW (begin3): the sky all around you, lit by the sun and the moon, 32 steps a texel.
// Ported from Alpha Piscium (GPL-3.0), techniques/atmospherics/air/lut, after Hillaire's reference (MIT). Their
// steps are fixed, with no jitter, so the sky is the same every frame for the same sun.

#include "/lib/atmosphere.glsl"

// From origin along dir to the top of the air or the ground; false if the ray misses the air.
bool ray_through_air(vec3 origin, vec3 dir, float ground, out float len) {
    float t_ground = atm_ray_sphere(origin, dir, ground);
    float t_top = atm_ray_sphere(origin, dir, atm_top);
    if (t_top < 0.0) {
        len = 0.0;
        return false;
    }
    len = t_ground >= 0.0 ? min(t_ground, t_top) : t_top;
    return true;
}

#if defined AIR_TRANSMITTANCE

layout(local_size_x = 16, local_size_y = 8) in;
const ivec3 workGroups = ivec3(16, 8, 1);
layout(rgba16f) writeonly uniform image2D cg_trans_img;

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    float height;
    float cos_zenith;
    atm_trans_params((vec2(texel) + 0.5) / vec2(atm_trans_size), height, cos_zenith);

    Air air = atm_air();
    vec3 origin = vec3(0.0, height, 0.0);
    vec3 dir = vec3(sqrt(max(0.0, 1.0 - cos_zenith * cos_zenith)), cos_zenith, 0.0);
    float len;
    ray_through_air(origin, dir, atm_bottom, len);

    const int steps = 40;
    float step_len = len / float(steps);
    vec3 depth = vec3(0.0);
    for (int i = 0; i < steps; i++) {
        vec3 p = origin + dir * ((float(i) + 0.5) * step_len);
        depth += atm_extinction(air, atm_density(length(p) - atm_bottom)) * step_len;
    }
    imageStore(cg_trans_img, texel, vec4(exp(-depth), 1.0));
}

#elif defined AIR_MULTISCATTER

layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(32, 32, 1);
layout(rgba16f) writeonly uniform image2D cg_msctr_img;

shared vec3 shared_light[64];
shared vec3 shared_transfer[64];

// The ground's colour seen from the air (Alpha Piscium's, in linear sRGB).
const vec3 ground_albedo = vec3(0.0262, 0.0999, 0.0844);

void main() {
    ivec2 texel = ivec2(gl_WorkGroupID.xy);
    vec2 uv = (vec2(texel) + 0.5) / vec2(atm_msctr_size);
    uv = (uv - 0.5 / vec2(atm_msctr_size)) * (vec2(atm_msctr_size) / (vec2(atm_msctr_size) - 1.0));
    float cos_light = uv.x * 2.0 - 1.0;
    vec3 light_dir = vec3(sqrt(max(0.0, 1.0 - cos_light * cos_light)), cos_light, 0.0);
    float height = atm_bottom + clamp(uv.y + 0.01, 0.0, 1.0) * (atm_top - atm_bottom - 0.01);
    vec3 origin = vec3(0.0, height, 0.0);

    // One direction per invocation, spread evenly over the sphere (a Fibonacci lattice).
    uint index = gl_LocalInvocationIndex;
    float z = 1.0 - (2.0 * float(index) + 1.0) / 64.0;
    float phi = float(index) * 2.39996323;
    float r = sqrt(max(0.0, 1.0 - z * z));
    vec3 dir = vec3(r * cos(phi), z, r * sin(phi));

    Air air = atm_air();
    const float uniform_phase = 1.0 / (4.0 * PI);
    vec3 light = vec3(0.0);    // light reaching the origin after one more scattering, per unit sun
    vec3 transfer = vec3(0.0); // the share of light that scatters again on the way (f_ms)
    float len;
    if (ray_through_air(origin, dir, atm_bottom, len)) {
        const int steps = 20;
        float step_len = len / float(steps);
        vec3 depth = vec3(0.0);
        for (int i = 0; i < steps; i++) {
            vec3 p = origin + dir * ((float(i) + 0.5) * step_len);
            float h = length(p);
            vec3 density = atm_density(h - atm_bottom);
            vec3 e = atm_extinction(air, density);
            vec3 t_step = exp(-e * step_len);
            vec3 to_origin = exp(-depth);
            vec3 scattering = atm_rayleigh * density.x + air.mie_scattering * density.y;
            float cos_zenith = dot(p / h, light_dir);
            float shadow = atm_ray_sphere(p, light_dir, atm_bottom) < 0.0 ? 1.0 : 0.0;
            vec3 in_scatter = shadow * atm_trans(h, cos_zenith) * scattering * uniform_phase;
            light += to_origin * atm_step_integral(in_scatter, e, t_step);
            transfer += to_origin * atm_step_integral(scattering, e, t_step);
            depth += e * step_len;
        }
        // Where the ray ends on the ground, the light the ground sends back.
        vec3 end = origin + dir * len;
        float end_height = length(end);
        if (abs(end_height - atm_bottom) < 0.01) {
            vec3 up = end / end_height;
            float n_dot_l = max(dot(up, light_dir), 0.0);
            light += atm_trans(atm_bottom + 0.01, dot(up, light_dir)) * exp(-depth) * ground_albedo * n_dot_l / PI;
        }
    }
    const float sphere_solid_angle = 4.0 * PI;
    shared_light[index] = light * sphere_solid_angle / 64.0;
    shared_transfer[index] = transfer * sphere_solid_angle / 64.0;
    barrier();
    for (uint stride = 32u; stride > 0u; stride >>= 1u) {
        if (index < stride) {
            shared_light[index] += shared_light[index + stride];
            shared_transfer[index] += shared_transfer[index + stride];
        }
        barrier();
    }
    if (index == 0u) {
        vec3 l2 = shared_light[0] * uniform_phase;
        vec3 f_ms = shared_transfer[0] * uniform_phase;
        // Every order of scattering after the second, as a geometric series.
        vec3 result = l2 / (1.0 - min(f_ms, vec3(0.999)));
        imageStore(cg_msctr_img, texel, vec4(result * atm_msctr_scale, 1.0));
    }
}

#elif defined AIR_SKYVIEW

#include "/lib/celestial.glsl"

layout(local_size_x = 16, local_size_y = 16) in;
const ivec3 workGroups = ivec3(16, 16, 1);
layout(rgba16f) writeonly uniform image2D cg_skyview_img;

uniform vec3 cameraPosition;

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    vec3 dir = atm_skyview_dir((vec2(texel) + 0.5) / vec2(atm_skyview_size));
    vec3 origin = vec3(0.0, atm_height(cameraPosition.y), 0.0);

    vec3 radiance = vec3(0.0);
    float len;
    if (ray_through_air(origin, dir, atm_bottom - atm_below_ground, len)) {
        vec3 transmittance;
        radiance = atm_segment(origin, dir, len, 32, sun_dir_world(), sun_illuminance, moon_dir_world(), moon_illuminance(),
                transmittance);
    }
    imageStore(cg_skyview_img, texel, vec4(radiance, 1.0));
}

#endif

// The air's tables (see lib/atmosphere.glsl): SKY_TRANSMITTANCE and SKY_MULTIPLE once, when the pack loads (setup,
// setup1: they only depend on the air), and SKY_VIEW at the start of each frame (begin), from where the sun is.

#include "/lib/atmosphere.glsl"

#if defined SKY_TRANSMITTANCE

layout(local_size_x = 8, local_size_y = 8) in;
const ivec3 workGroups = ivec3(32, 8, 1);

layout(rgba16f) writeonly uniform image2D cg_sky_img;

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    float r, mu;
    trans_r_mu((vec2(texel) + 0.5) / vec2(trans_size), r, mu);
    float d = ray_sphere_far(r, mu, atmo_top);
    const int steps = 40;
    vec3 depth = vec3(0.0);
    float dt = d / float(steps);
    for (int i = 0; i < steps; i++) {
        float t = (float(i) + 0.5) * dt;
        float ri = sqrt(r * r + t * t + 2.0 * r * mu * t);
        depth += medium_at(ri).extinction * dt;
    }
    imageStore(cg_sky_img, texel + trans_origin, vec4(exp(-depth), 1.0));
}

#elif defined SKY_MULTIPLE

// Each work group is one texel; its 64 threads each take one direction around the point.
layout(local_size_x = 64) in;
const ivec3 workGroups = ivec3(32, 32, 1);

uniform sampler2D cg_sky_tex;
layout(rgba16f) writeonly uniform image2D cg_sky_img;

shared vec3 s_light[64];
shared vec3 s_fraction[64];

vec3 transmittance(float r, float mu) {
    return texture(cg_sky_tex, sky_atlas_uv(trans_uv(r, mu), trans_size, trans_origin)).rgb;
}

void main() {
    ivec2 texel = ivec2(gl_WorkGroupID.xy);
    vec2 uv = (vec2(texel) + 0.5) / vec2(ms_size);
    uv = (uv * vec2(ms_size) - 0.5) / vec2(ms_size - 1);
    float mu_s = uv.x * 2.0 - 1.0;
    float r = atmo_bottom + clamp(uv.y, 0.0, 1.0) * (atmo_top - atmo_bottom);
    r = clamp(r, atmo_bottom + 0.01, atmo_top - 0.01);
    vec3 sun = vec3(0.0, mu_s, sqrt(max(0.0, 1.0 - mu_s * mu_s)));

    // A direction from an 8 x 8 grid over the sphere.
    uint i = gl_LocalInvocationID.x;
    float a = (float(i % 8u) + 0.5) / 8.0;
    float b = (float(i / 8u) + 0.5) / 8.0;
    float cos_t = 1.0 - 2.0 * b;
    float sin_t = sqrt(max(0.0, 1.0 - cos_t * cos_t));
    vec3 dir = vec3(sin_t * cos(TAU * a), cos_t, sin_t * sin(TAU * a));

    float mu = dir.y;
    float t_ground = ray_sphere_near(r, mu, atmo_bottom);
    float t_end = t_ground >= 0.0 ? t_ground : ray_sphere_far(r, mu, atmo_top);
    const int steps = 20;
    float dt = t_end / float(steps);
    vec3 throughput = vec3(1.0);
    vec3 light = vec3(0.0);
    vec3 fraction = vec3(0.0);
    for (int s = 0; s < steps; s++) {
        float t = (float(s) + 0.5) * dt;
        vec3 p = vec3(0.0, r, 0.0) + dir * t;
        float ri = length(p);
        Medium m = medium_at(ri);
        vec3 scattering = m.scattering_r + m.scattering_m;
        vec3 step_t = exp(-m.extinction * dt);
        vec3 integral = (1.0 - step_t) / max(m.extinction, 1e-7);
        float mu_si = dot(p / ri, sun);
        vec3 sun_t = ray_sphere_near(ri, mu_si, atmo_bottom) >= 0.0 ? vec3(0.0) : transmittance(ri, mu_si);
        // An even phase for every bounce after the first.
        light += throughput * scattering * sun_t * integral / (4.0 * PI);
        fraction += throughput * scattering * integral / (4.0 * PI);
        throughput *= step_t;
    }
    if (t_ground >= 0.0) {
        vec3 p = vec3(0.0, r, 0.0) + dir * t_ground;
        vec3 up = normalize(p);
        float mu_si = dot(up, sun);
        light += throughput * transmittance(atmo_bottom, mu_si) * max(mu_si, 0.0) * ground_albedo / PI;
    }
    // Integrated over the sphere: each direction's share is 4 pi / 64.
    s_light[i] = light * (4.0 * PI / 64.0);
    s_fraction[i] = fraction * (4.0 * PI / 64.0);
    barrier();
    for (uint stride = 32u; stride > 0u; stride >>= 1u) {
        if (i < stride) {
            s_light[i] += s_light[i + stride];
            s_fraction[i] += s_fraction[i + stride];
        }
        barrier();
    }
    if (i == 0u) {
        vec3 psi = s_light[0] / max(1.0 - s_fraction[0], 0.01);
        imageStore(cg_sky_img, texel + ms_origin, vec4(psi, 1.0));
    }
}

#elif defined SKY_VIEW

// Two layers: the sun's sky (rows 0..107) and the moon's (108..215).
layout(local_size_x = 8, local_size_y = 4) in;
const ivec3 workGroups = ivec3(24, 54, 1);

uniform sampler2D cg_sky_tex;
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform mat4 gbufferModelViewInverse;
layout(rgba16f) writeonly uniform image2D cg_sky_img;

vec3 transmittance(float r, float mu) {
    return texture(cg_sky_tex, sky_atlas_uv(trans_uv(r, mu), trans_size, trans_origin)).rgb;
}

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    bool moon = texel.y >= skyview_size.y;
    ivec2 local = ivec2(texel.x, texel.y - (moon ? skyview_size.y : 0));
    vec3 sun = normalize(mat3(gbufferModelViewInverse) * sunPosition);
    vec3 light_dir = moon ? -sun : sun;
    vec3 dir = skyview_dir((vec2(local) + 0.5) / vec2(skyview_size), light_dir);

    float r = camera_radius(cameraPosition.y);
    float mu = dir.y;
    float t_ground = ray_sphere_near(r, mu, atmo_bottom);
    float t_end = t_ground >= 0.0 ? t_ground : ray_sphere_far(r, mu, atmo_top);
    t_end = min(t_end, 400.0);
    float cos_theta = dot(dir, light_dir);
    float phase_r = rayleigh_phase(cos_theta);
    float phase_m = mie_phase(cos_theta, mie_g);

    const int steps = 32;
    vec3 throughput = vec3(1.0);
    vec3 light = vec3(0.0);
    float t_prev = 0.0;
    for (int s = 0; s < steps; s++) {
        // Steps grow away from the camera, where the air thins out.
        float f = (float(s) + 0.5) / float(steps);
        float t = t_end * f * f;
        float t_next = t_end * sqr((float(s) + 1.0) / float(steps));
        float dt = t_next - t_prev;
        t_prev = t_next;
        vec3 p = vec3(0.0, r, 0.0) + dir * t;
        float ri = length(p);
        Medium m = medium_at(ri);
        float mu_si = dot(p / ri, light_dir);
        vec3 sun_t = ray_sphere_near(ri, mu_si, atmo_bottom) >= 0.0 ? vec3(0.0) : transmittance(ri, mu_si);
        vec3 psi = texture(cg_sky_tex, sky_atlas_uv(ms_uv(ri, mu_si), ms_size, ms_origin)).rgb;
        vec3 scattered = sun_t * (m.scattering_r * phase_r + m.scattering_m * phase_m)
                + psi * (m.scattering_r + m.scattering_m);
        vec3 step_t = exp(-m.extinction * dt);
        light += throughput * scattered * (1.0 - step_t) / max(m.extinction, 1e-7);
        throughput *= step_t;
    }
    imageStore(cg_sky_img, texel + skyview_origin, vec4(light, 1.0));
}

#endif

// The sky as the camera sees it: the air's light from the sky view table (see atmosphere.glsl), the sun's disc and
// the stars. The moon is Minecraft's own (gbuffers_skytextured), lit here.

#if !defined LIB_SKY
#define LIB_SKY

#include "/lib/settings.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/globals.glsl"
#include "/lib/view.glsl"

uniform sampler2D cg_sky_tex; // the air's tables (see lib/atmosphere.glsl)

// The sun's light above the air, in the pack's units: a white surface facing it at noon reads about 1.2, with the
// sky's about 0.4 on top, so a sunlit block sits near a lamp-lit one at the same exposure (the fixed exposure that
// suits lamp light by night suits the day too).
const float sun_illuminance = 4.0;
const float sun_radius = 0.06; // radians: about Minecraft's own sun

// The full moon's light above the air (the new moon's is a third of it).
float moon_illuminance() {
    return 0.3 * NIGHT_BRIGHTNESS * (0.35 + 0.65 * g_moon_dir.w);
}

vec3 air_transmittance(float r, float mu) {
    return texture(cg_sky_tex, sky_atlas_uv(trans_uv(r, mu), trans_size, trans_origin)).rgb;
}

// The air's own light from a direction, from the sun's sky and the moon's.
vec3 sky_air(vec3 dir) {
    vec2 uv_sun = skyview_uv(dir, g_sun_dir.xyz);
    vec2 uv_moon = skyview_uv(dir, g_moon_dir.xyz);
    ivec2 both = skyview_size * ivec2(1, 2);
    vec3 sun_sky = texture(cg_sky_tex, sky_atlas_uv(vec2(uv_sun.x, uv_sun.y * 0.5), both, skyview_origin)).rgb;
    vec3 moon_sky = texture(cg_sky_tex, sky_atlas_uv(vec2(uv_moon.x, uv_moon.y * 0.5 + 0.5), both, skyview_origin)).rgb;
    return sun_sky * sun_illuminance + moon_sky * moon_illuminance();
}

// A frame that turns with the sky, for the stars: the sun's direction, the axis it turns around, and the third.
mat3 celestial_frame() {
    vec3 s = g_sun_dir.xyz;
    float tilt = radians(sunPathRotation);
    vec3 axis = vec3(0.0, -sin(tilt), cos(tilt));
    axis = normalize(axis - dot(axis, s) * s);
    return mat3(s, cross(axis, s), axis);
}

float stars(vec3 dir) {
    vec3 c = transpose(celestial_frame()) * dir;
    // A cube's face and the cell on it.
    vec3 a = abs(c);
    float m = max3(a);
    vec2 face_uv;
    float face;
    if (a.x == m) {
        face_uv = c.yz / a.x;
        face = c.x > 0.0 ? 0.0 : 1.0;
    } else if (a.y == m) {
        face_uv = c.xz / a.y;
        face = c.y > 0.0 ? 2.0 : 3.0;
    } else {
        face_uv = c.xy / a.z;
        face = c.z > 0.0 ? 4.0 : 5.0;
    }
    const float cells = 220.0;
    vec2 cell_pos = (face_uv * 0.5 + 0.5) * cells;
    vec2 cell = floor(cell_pos);
    vec3 h = hash33(uvec3(uvec2(cell), uint(face) + 17u));
    if (h.x > 0.045) {
        return 0.0;
    }
    vec2 centre = cell + 0.25 + 0.5 * h.yz;
    float d = length(cell_pos - centre);
    float size = 0.12 + 0.1 * h.y;
    float brightness = pow(h.z, 3.0) * 0.6 + 0.04;
    return brightness * smoothstep(size, 0.0, d);
}

// The sun's disc, with its edge a little darker (limb darkening).
vec3 sun_disc(vec3 dir) {
    float cos_d = dot(dir, g_sun_dir.xyz);
    float angle = acos(clamp(cos_d, -1.0, 1.0));
    if (angle > sun_radius) {
        return vec3(0.0);
    }
    float r = angle / sun_radius;
    float limb = 1.0 - 0.6 * (1.0 - sqrt(max(0.0, 1.0 - r * r)));
    float edge = smoothstep(1.0, 0.92, r);
    float cam_r = camera_radius(cameraPosition.y);
    vec3 through = air_transmittance(cam_r, dir.y);
    // Bright enough to bloom, not the real sun's thousands.
    return through * sun_illuminance * 6.0 * limb * edge;
}

// The whole sky in a direction, without the moon.
vec3 sky(vec3 dir, bool with_sun) {
    vec3 c = sky_air(dir);
    float night = smoothstep(0.05, -0.15, g_sun_dir.w);
    float cam_r = camera_radius(cameraPosition.y);
    if (dir.y > -0.02) {
        c += stars(dir) * night * air_transmittance(cam_r, max(dir.y, 0.0)) * 0.5;
    }
    if (with_sun) {
        c += sun_disc(dir);
    }
    return c;
}

#endif

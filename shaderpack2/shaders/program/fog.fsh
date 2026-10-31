// The air between you and what you see, over the whole lit scene (translucents included):
// - the coloured light in the air: the per-block light (see program/voxel_light.csh; cells in the open take light
//   from every side, so this is the light in the air itself), gathered along the view ray in 8-block steps up to
//   the first surface or half the voxel box; bright spots count much more than dim ones, the fourth root of the
//   average's strength makes even faint light show (in its own colour), and it fades in from nothing at your feet to
//   full 32 blocks out (your own torch doesn't fog your view). It darkens what is behind it a little, then adds
//   itself (in display space): haze, not a glow on top. In the Overworld it is faint by day out in the open and full
//   at night and underground;
// - haze far off, thickest at sea level, in the colour of the sky behind it, starting slowly;
// - in the Nether, its biome's haze (colour and thickness, see lib/sky.glsl), thicker under the ceiling and lit
//   orange from below by the lava sea; in the End, a violet haze low round the islands;
// - water around you (when the camera is under water) and lava.

#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/globals.glsl"
#include "/lib/voxel_sample.glsl"
#include "/lib/lighting.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex6; // the water's surface (see lib/buffers.glsl)
uniform sampler2D colortex8; // its reflection (program/water_reflect.csh)
uniform sampler2D colortex9; // the light shafts under water (program/water_shafts.csh)
uniform sampler2D depthtex1; // without clouds, water, glass and particles
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
uniform float far;

#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
uniform int dhRenderDistance; // in blocks
#endif

in vec2 v_uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

vec3 light_in_air(vec3 dir, float reach, ivec2 texel) {
    float start = fract(ign(vec2(texel)) + float(frameCounter % 64) * GOLDEN);
    vec3 origin = vox_pos(vec3(0.0));
    vec3 sum = vec3(0.0);
    float count = 0.0;
    for (int i = 0; i < 8; i++) {
        float t = (float(i) + start) * 8.0;
        if (t >= reach) {
            break;
        }
        vec3 p = origin + dir * t;
        vec3 r = abs(p - vox_box_middle()) / vec3(vox_half);
        // Fades towards the box's edge, sooner up and down (the box is flatter).
        float edge = (1.0 - smoothstep(0.6, 0.95, max(r.x, r.z))) * (1.0 - smoothstep(0.5, 0.9, r.y));
        float near_fade = smoothstep(0.0, 32.0, t);
        vec4 light = sample_block_table(cg_blk_tex, p);
        vec3 c = light.rgb * light.a;
        // Bright spots count far more than dim ones, so the haze gathers round strong lights.
        float lum = max3(c);
        c *= lum * lum * 4.0;
        sum += c * edge * near_fade;
        count += 1.0;
    }
    if (count == 0.0) {
        return vec3(0.0);
    }
    vec3 avg = sum / count;
    // The fourth root of its strength, so even faint light shows, in the light's own colour (a root per channel
    // would wash a lava sea's orange out to beige).
    float top = max3(avg);
    return top > 0.0 ? avg / top * sqrt(sqrt(top)) * 0.25 : vec3(0.0);
}

// The water's reflection at a water pixel (WATER_QUALITY 1 to 3), traced in program/water_reflect.csh. At High its
// looks were taken one per 2 x 2 pixels: each pixel blends the four nearest, each counting less the further its
// surface lies from this one's in depth or turns from it in normal, so the reflection stays sharp along a shore or a
// boat's edge.
vec3 water_reflection(ivec2 texel, vec4 water, float depth) {
#if WATER_QUALITY == 3
    return texelFetch(colortex8, texel, 0).rgb;
#else
    ivec2 half_size = (ivec2(render_size()) + 1) / 2;
    vec2 at = (vec2(texel) + 0.5) * 0.5 - 0.5;
    ivec2 base = ivec2(floor(at));
    vec2 f = at - vec2(base);
    vec3 n = decode_normal(water.xy);
    vec3 sum = vec3(0.0);
    float total = 0.0;
    vec3 any_sum = vec3(0.0);
    float any_total = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 c = clamp(base + o, ivec2(0), half_size - 1);
        vec4 from = texelFetch(colortex8, c + ivec2(half_size.x, 0), 0);
        if (from.w < 0.5) {
            continue;
        }
        vec3 look = texelFetch(colortex8, c, 0).rgb;
        float w = mix(1.0 - f.x, f.x, float(o.x)) * mix(1.0 - f.y, f.y, float(o.y)) + 1e-3;
        any_sum += look * w;
        any_total += w;
        w *= pow(max(dot(decode_normal(from.yz), n), 0.0), 4.0);
        w *= exp(-abs(from.x - depth) / (0.03 * depth + 0.1));
        sum += look * w;
        total += w;
    }
    if (total > 1e-4) {
        return sum / total;
    }
    return any_total > 0.0 ? any_sum / any_total : vec3(0.0);
#endif
}

// The average of e^((y - base) / scale) along a straight line from height y0 to y1: how much of the way a layer that
// thickens (scale > 0) or thins (scale < 0) upwards takes up.
float layer_average(float y0, float y1, float base, float scale) {
    float a = (y0 - base) / scale;
    float b = (y1 - base) / scale;
    if (abs(b - a) < 1e-3) {
        return exp(a);
    }
    return (exp(b) - exp(a)) / (b - a);
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    if (outside_render_area(gl_FragCoord.xy)) {
        out_color = vec4(0.0);
        return;
    }
    vec3 color = texelFetch(colortex0, texel, 0).rgb;
#if DEBUG_VIEW != 0
    out_color = vec4(color, 1.0); // a debug view shows its values as they are
    return;
#endif
    float depth = texelFetch(depthtex0, texel, 0).r;
    vec3 view = pixel_to_view(texel, min(depth, 0.9999999));
    bool sky_pixel = depth >= 1.0;
    float far_end = far; // where the world drawn ends
#ifdef DISTANT_HORIZONS
    if (sky_pixel) {
        float dh_depth = texelFetch(dhDepthTex0, texel, 0).r;
        if (dh_depth < 1.0) {
            vec2 ndc = (vec2(texel) + 0.5 - vec2(cg_dlss_jitter_x, cg_dlss_jitter_y)) / render_size() * 2.0 - 1.0;
            vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
            view = v.xyz / v.w;
            sky_pixel = false;
        }
    }
    far_end = max(far, float(dhRenderDistance));
#endif
    vec3 scene = view_to_scene_pos(view);
#if WATER_QUALITY >= 1
    // The water's reflection, over the water the forward pass drew (which leaves it out), before the air.
    vec4 water = texelFetch(colortex6, texel, 0);
    if (water.w > 0.5 && depth < 1.0) {
        color += water.z * water_reflection(texel, water, -view.z);
    }
#endif
    float dist = sky_pixel ? 1e4 : length(scene);
    vec3 dir = normalize(scene);
    float solid = sky_pixel ? 0.0 : 1.0;
    if (depth < 1.0 && texelFetch(depthtex1, texel, 0).r >= 1.0) {
        solid = 0.0; // Minecraft's clouds, or glass or a particle with only sky behind it: no haze, like the sky
    }

    // Water or lava around you.
    if (isEyeInWater == 1) {
        color = water_inside(color, min(dist, 96.0), water_light_around(float(eyeBrightnessSmooth.y) / 240.0));
        // The sun's shafts (program/water_shafts.csh), this frame's, from half the size.
        vec2 half_size = vec2((ivec2(render_size()) + 1) / 2);
        vec2 corner = (frameCounter & 1) == 0 ? vec2(0.0, half_size.y) : vec2(half_size.x, 0.0);
        vec2 at = clamp((vec2(texel) + 0.5) * 0.5, vec2(0.5), half_size - 0.5) + corner;
        color += textureLod(colortex9, at / window_size(), 0.0).rgb;
    } else if (isEyeInWater == 2) {
        float f = 1.0 - exp(-dist * 1.2);
        color = mix(color, vec3(1.6, 0.35, 0.05), f);
    }

#if defined SUNLIGHT
    // Haze far off.
    if (isEyeInWater == 0) {
        float height = scene.y + cameraPosition.y;
        // Starts slowly (squared), so the land around you stays clear: about 0.05 at 64 blocks, 0.15 at 128, 0.35
        // at 256.
        float density = 0.005 * exp(-max(height - 63.0, 0.0) / 60.0);
        float haze = sqr(1.0 - exp(-min(dist, far_end) * density));
        float border = smoothstep(far_end * 0.8, far_end, dist);
        haze = max(haze * 0.7, border);
        // In the colour of the sky right behind what it hides (its horizon in that direction, or the sky itself
        // above it), so a far hill against the sunset fades into the sunset and one across from it into the blue.
        vec3 behind = normalize(vec3(dir.x, max(dir.y, 0.0) + 0.05, dir.z));
        vec3 haze_color = sky_look(sky_air_seen(behind), behind);
        // Mixed in display space, like the light on surfaces: in linear light even a little of the bright horizon
        // lifts dark land a lot, so the distance greys out.
        const vec3 to_display = vec3(1.0 / 2.2);
        vec3 shown = mix(pow(max(color, 0.0), to_display), pow(haze_color, to_display), haze * solid);
        color = pow(shown, vec3(2.2));
    }
#elif defined WORLD_NETHER
    // The haze: its biome's colour and thickness (lib/sky.glsl), thicker under the ceiling (from about 30 blocks
    // below it), and lit from below by the lava sea, orange where the view runs low over it. At the edge of what's
    // drawn it is all haze.
    if (isEyeInWater == 0) {
        float d = min(dist, far_end);
        float y0 = cameraPosition.y;
        float y1 = y0 + dir.y * d;
        float under_ceiling = layer_average(clamp(y0, 0.0, 135.0), clamp(y1, 0.0, 135.0), 127.0, 12.0);
        float over_lava = layer_average(max(y0, 31.0), max(y1, 31.0), 31.0, -18.0);
        float haze = 1.0 - exp(-d * (nether_thickness() + 0.04 * under_ceiling));
        haze = max(haze, smoothstep(far_end * 0.75, far_end, dist));
        vec3 haze_color = nether_haze() + vec3(1.0, 0.32, 0.06) * 0.05 * over_lava * NIGHT_BRIGHTNESS;
        const vec3 to_display = vec3(1.0 / 2.2);
        vec3 shown = mix(pow(max(color, 0.0), to_display), pow(haze_color, to_display), haze);
        color = pow(shown, vec3(2.2));
    }
#elif defined WORLD_END
    // The End's haze: violet, a low sea of it round the islands (thick below about 55, thin by 85), a little
    // everywhere far off, and the sky's colour at the edge of what's drawn.
    if (isEyeInWater == 0) {
        float d = min(dist, far_end);
        float y0 = cameraPosition.y;
        float y1 = y0 + dir.y * d;
        float low = layer_average(clamp(y0, 0.0, 256.0), clamp(y1, 0.0, 256.0), 55.0, -12.0);
        float haze = 1.0 - exp(-d * (0.0015 + 0.012 * min(low, 4.0)));
        haze = max(haze, smoothstep(far_end * 0.75, far_end, dist)) * solid;
        const vec3 to_display = vec3(1.0 / 2.2);
        vec3 shown = mix(pow(max(color, 0.0), to_display), pow(g_horizon.rgb, to_display), haze);
        color = pow(shown, vec3(2.2));
    }
#endif

    // The coloured light in the air.
    if (LIGHT_FOG > 0.0 && isEyeInWater != 2) {
        float reach = min(dist, float(vox_half.x));
        vec3 fog = light_in_air(dir, reach, texel);
#if defined SUNLIGHT
        float open = float(eyeBrightnessSmooth.y) / 240.0;
        float strength = mix(1.0, 0.15, open * smoothstep(-0.05, 0.15, g_sun_dir.w));
#elif defined WORLD_NETHER
        float strength = 2.0;
#else
        float strength = 1.0;
#endif
        fog *= strength * LIGHT_FOG;
        if (isEyeInWater == 1) {
            fog *= vec3(0.4, 0.8, 1.0);
        }
        // Mixed in display space, like the light on surfaces (see lib/lighting.glsl): faint haze stays faint.
        vec3 shown = pow(max(color, 0.0), vec3(1.0 / 2.2));
        shown = shown * (1.0 - clamp(max3(fog) * 0.5, 0.0, 0.5)) + fog * 0.65;
        color = pow(shown, vec3(2.2));
    }

    out_color = vec4(color, 1.0);
}

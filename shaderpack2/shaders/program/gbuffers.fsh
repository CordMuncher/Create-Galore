// Every world program's fragment stage (see gbuffers_kind.glsl for what each one draws and how it is lit):
// - LIT_GBUFFER: the surface into the G-buffer for the deferred pass (see lib/buffers.glsl);
// - LIT_FORWARD: lit here, over the lit scene (water, glass, particles, rain, clouds);
// - LIT_GLOW: added light (glowing eyes, the enchantment glint, lightning, beacon beams);
// - LIT_SKY: Minecraft's sky, which the deferred pass draws itself, the moon too (lib/sky.glsl);
// - LIT_CRACKS: the block breaking cracks, laid over the surface's colour in the G-buffer;
// - LIT_LINES: the block outline, leads and debug lines, over the surface's colour and over the lit scene.

#include "/program/gbuffers_kind.glsl"
#include "/lib/motion.glsl"
#include "/lib/buffers.glsl"
#include "/lib/blocks.glsl"

uniform sampler2D gtexture;
uniform vec4 entityColor; // the hurt and creeper flash: rgb its colour, a how much of it
uniform int renderStage;
uniform int isEyeInWater;

in vec2 v_uv;
in vec4 v_color;
in vec4 v_clip_now;
in vec4 v_clip_prev;
in vec3 v_normal;
in vec3 v_scene_pos;
in vec2 v_light;
in float v_ao;
flat in int v_block;
flat in float v_emission;

#ifdef COLORWHEEL
vec2 g_clrwl_light = vec2(0.0); // Flywheel's block and sky light at this fragment, 0..1

// Flywheel's own material code gives the colour (and discards what the material cuts out), with the hurt flash and
// the light at the lightmap's texel centres (level / 16 + 1 / 32).
vec4 base_color() {
    vec4 color;
    vec2 light;
    float ao;
    vec4 overlay;
    clrwl_computeFragment(texture(gtexture, v_uv, cg_mip_bias), color, light, ao, overlay);
    g_clrwl_light = clamp((light - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
#if !defined LIT_GLOW && !defined LIT_CRACKS
    color.rgb = mix(color.rgb, overlay.rgb, overlay.a);
#if defined CLRWL_OLD_LIGHTING && !defined _CLRWL_IS_FALLBACK
    // Flywheel shades the faces as Minecraft does; the sun and the sky do it here.
    color.rgb /= max(_clrwl_diffuseFactor(), 0.05);
#endif
#endif
    return color;
}

vec2 light_levels() {
    return g_clrwl_light;
}
#else
vec2 light_levels() {
    return v_light;
}

vec4 base_color() {
#ifdef TEXTURED
    vec4 c = texture(gtexture, v_uv, cg_mip_bias) * v_color;
#else
    vec4 c = v_color;
#endif
#ifdef ENTITY_FORMAT
    c.rgb = mix(c.rgb, entityColor.rgb, entityColor.a);
#endif
    return c;
}
#endif

vec4 motion_out() {
#if defined NO_MOTION_CLAIM
    return vec4(0.0);
#elif defined OWN_MOTION
#ifdef HAND
    return vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_HAND, 1.0);
#else
    return vec4(motion_vector(v_clip_now, v_clip_prev), MOTION_WORLD, 1.0);
#endif
#elif defined PROGRAM_PARTICLES
    return vec4(0.0, 0.0, MOTION_PARTICLE, 1.0);
#else
    return vec4(0.0, 0.0, MOTION_NONE, 1.0);
#endif
}

#if defined LIT_GBUFFER

/* RENDERTARGETS: 1,2,3,4,7 */
layout(location = 0) out vec4 out_albedo;
layout(location = 1) out vec4 out_normal;
layout(location = 2) out vec4 out_light;
layout(location = 3) out vec4 out_material;
layout(location = 4) out vec4 out_motion;

#if defined PROGRAM_BLOCK
uniform int blockEntityId; // block.properties' number of the block entity being drawn

// The end portal and gateway: a window onto deep space, five layers of stars at different depths drifting slowly
// past each other (as Minecraft's own does), in the portal's pale teal and green. rgb its colour (sRGB, as albedo),
// a how much of it glows. Looked at across the face, so it has depth as you move.
vec4 end_portal_look(vec3 dir, vec3 n) {
    vec3 t = normalize(abs(n.y) > 0.9 ? cross(n, vec3(1.0, 0.0, 0.0)) : cross(n, vec3(0.0, 1.0, 0.0)));
    vec3 b = cross(n, t);
    vec2 across = vec2(dot(dir, t), dot(dir, b)) / max(abs(dot(dir, n)), 0.15);
    vec3 c = vec3(0.03, 0.07, 0.08);
    float glow = 0.2;
    for (int k = 0; k < 5; k++) {
        float depth = 1.0 + float(k) * 0.8;
        float a = float(k) * 1.7;
        vec2 uv = mat2(cos(a), sin(a), -sin(a), cos(a)) * across * (3.0 * depth)
                + vec2(0.0, frameTimeCounter * 0.03 * (1.0 + float(k)));
        ivec2 cell = ivec2(floor(uv));
        vec3 h = hash33(uvec3(uvec2(cell + 32768), uint(k) + 3u));
        if (h.x > 0.82) {
            vec2 f = fract(uv) - (0.2 + 0.6 * h.yz);
            float star = smoothstep(0.16, 0.0, length(f)) / depth;
            vec3 tint = h.y < 0.35 ? vec3(0.45, 1.0, 0.85) : h.y < 0.6 ? vec3(0.6, 1.0, 0.55)
                      : h.y < 0.8 ? vec3(0.4, 0.65, 1.0) : vec3(0.9, 1.0, 1.0);
            c += tint * star;
            glow = max(glow, star);
        }
    }
    return vec4(min(c, 1.0), glow);
}
#endif

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    vec4 color = base_color();
#if defined PROGRAM_BLOCK
    bool end_portal = blockEntityId == BLOCK_LIGHT + KIND_END_PORTAL;
    if (end_portal) {
        color.a = 1.0;
    }
#endif
    if (color.a < 0.1) {
        discard;
    }

    int material = MAT_BLOCK;
    float smoothness = 0.1;
    float metal = 0.0;
    float subsurface = 0.0;
#if defined PROGRAM_TERRAIN
    if (v_block == BLOCK_PLANT) {
        material = MAT_PLANT;
        subsurface = 0.6;
    } else if (v_block == BLOCK_LEAVES) {
        material = MAT_LEAVES;
        subsurface = 0.5;
    } else if (v_block == BLOCK_METAL) {
        smoothness = 0.55;
        metal = 1.0;
    } else if (v_block == BLOCK_POLISHED) {
        smoothness = 0.45;
    }
#elif defined HAND
    material = MAT_HAND;
#elif defined COLORWHEEL
    material = MAT_MACHINE;
    smoothness = 0.25;
#elif defined ENTITY_FORMAT
    material = MAT_ENTITY;
#endif

    vec3 n = normalize(v_normal);
    if (!gl_FrontFacing) {
        n = -n;
    }
    vec3 albedo = color.rgb;
    float glow = glow_of(srgb_to_linear(albedo), v_block, v_emission);
#if defined PROGRAM_TERRAIN
    if (v_block == BLOCK_LIGHT + KIND_LAVA) {
        // Lava keeps its deep orange; its brightest flecks burn towards yellow-white, and all of it glows a little more.
        float heat = smoothstep(0.3, 0.75, luminance(srgb_to_linear(albedo)));
        albedo = mix(albedo, vec3(1.0, 0.9, 0.6), 0.7 * heat);
        glow = min(glow * 1.3 + 0.1, 1.0);
    }
#endif
#if defined PROGRAM_BLOCK
    if (end_portal) {
        vec4 space = end_portal_look(normalize(v_scene_pos), n);
        albedo = space.rgb;
        glow = space.a;
    }
#endif

    out_albedo = vec4(albedo, float(material) / 255.0);
    vec2 e = encode_normal(n);
    out_normal = vec4(e, e);
    out_light = vec4(light_levels(), clamp(v_ao, 0.0, 1.0), glow);
    out_material = vec4(smoothness, metal, subsurface, 1.0);
    out_motion = motion_out();
}

#elif defined LIT_FORWARD

#include "/lib/forward.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"

uniform sampler2D depthtex1;
#if defined DISTANT_HORIZONS && defined PROGRAM_WATER
uniform sampler2D dhDepthTex1; // Distant Horizons' terrain, drawn before Minecraft's chunks
uniform mat4 dhProjectionInverse;
#endif

#if defined PROGRAM_WATER
/* RENDERTARGETS: 0,7,6 */
layout(location = 0) out vec4 out_color; // premultiplied by its alpha (see the blend modes in shaders.properties)
layout(location = 1) out vec4 out_motion;
// The water's surface for its reflection (program/water_reflect.csh, added in program/fog.fsh): its normal
// (octahedral), how much it reflects, and 1 less 0.49 per half a radian the waves too fine to see spread it (so over
// a half: water); all 0 for anything else drawn here (glass in front of water keeps it out).
layout(location = 2) out vec4 out_water;
#else
/* RENDERTARGETS: 0,7 */
layout(location = 0) out vec4 out_color; // premultiplied by its alpha (see the blend modes in shaders.properties)
layout(location = 1) out vec4 out_motion;
#endif

vec3 view_of(vec2 frag, float depth) {
    vec2 ndc = (frag / render_size()) * 2.0 - 1.0;
    vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

// How far behind a point (view space) what lies under the water at a pixel is, from Minecraft's depth there
// (depth) or, past its chunks, from Distant Horizons': near the edge of the chunks a look into a lake reaches its
// floor out where only Distant Horizons has it. fallback where there is nothing (the sky).
float distance_under(vec2 at, float depth, vec3 here, float fallback) {
    if (depth < 1.0) {
        return distance(view_of(at, depth), here);
    }
#if defined DISTANT_HORIZONS && defined PROGRAM_WATER
    float dh_depth = texelFetch(dhDepthTex1, ivec2(at), 0).r;
    if (dh_depth < 1.0) {
        vec2 ndc = (at / render_size()) * 2.0 - 1.0;
        vec4 v = dhProjectionInverse * vec4(ndc, dh_depth * 2.0 - 1.0, 1.0);
        return distance(v.xyz / v.w, here);
    }
#endif
    return fallback;
}

#if defined PROGRAM_WATER || defined PROGRAM_HAND_WATER
#ifdef WATER_REFRACTION
#if defined PROGRAM_WATER
// The water draws what lies under it itself, bent by its waves, from the lit scene's copy (colortex5, written by
// program/deferred_light.fsh): its alpha is 1. (An #ifdef of its own, so Iris sees the option.)
#define WATER_SEES_UNDER
uniform sampler2D colortex5;
#endif
#endif
#define CLOUDS_NO_READ_BACK
#include "/lib/sky_mirror.glsl"
#if defined PROGRAM_WATER
#include "/lib/trace.glsl"

// Whether a block is water (past the copy's edge counts as open water).
float open_water(ivec3 cell) {
    return !vox_in_box(cell, 0) || (vox_word(cell) & VOX_WATER) != 0u ? 1.0 : 0.0;
}

// How much room the swell has to build here: open water 3 to 18 blocks up-wind (the block copy says, blended between
// the blocks around each look so it never steps at a block's edge), so a pond or a river stays calm while a lake or
// the sea rolls.
float swell_room(vec3 scene_pos) {
    vec3 p = vox_pos(scene_pos) - vec3(0.0, 0.3, 0.0);
    vec2 up_wind = -normalize(vec2(wind_way));
    const float reach[4] = float[](3.0, 7.0, 12.0, 18.0);
    float open = 0.0;
    for (int k = 0; k < 4; k++) {
        vec3 q = p + vec3(up_wind.x, 0.0, up_wind.y) * reach[k] - vec3(0.5, 0.0, 0.5);
        ivec3 c = ivec3(floor(q));
        vec2 f = fract(q.xz);
        float a = mix(open_water(c), open_water(c + ivec3(1, 0, 0)), f.x);
        float b = mix(open_water(c + ivec3(0, 0, 1)), open_water(c + ivec3(1, 0, 1)), f.x);
        open += mix(a, b, f.y);
    }
    return open / 4.0;
}
#else
float swell_room(vec3 scene_pos) {
    return 1.0;
}
#endif

// The ring that spreads from where you went into or out of the water (program/frame.csh): out at 2 blocks a second
// to 3.5 blocks, a crest 0.2 blocks across, dying away as it goes.
vec2 splash_ring(vec3 world) {
    vec4 s = g_splash;
    float age = frameTimeCounter - s.w;
    age += age < 0.0 ? 3600.0 : 0.0;
    if (age > 1.75 || abs(world.y - s.y) > 0.6) {
        return vec2(0.0);
    }
    vec2 d = world.xz - s.xz;
    float r = length(d);
    float x = r - 2.0 * age;
    float env = exp(-x * x / (0.15 * 0.15)) * (1.0 - age / 1.75);
    return d / max(r, 1e-3) * cos(TAU * x / 0.2) * env * 0.6;
}

// The water's surface: what lies under it (dimmed and tinted by the water between, bent by the waves), the light the
// water scatters back towards you on the way, and the sun's glint; its reflection comes later, over the whole scene
// (traced through the block copy in program/water_reflect.csh, added in program/fog.fsh). reflected: how much it
// reflects (Fresnel, exact for water); n: its normal on your side; spread: how far the waves finer than a pixel
// scatter a reflection (radians).
vec4 shade_water(vec4 base, out float reflected, out vec3 n, out float spread) {
    vec3 to_eye = normalize(-v_scene_pos);
    float distance_away = length(v_scene_pos);
    vec3 world = v_scene_pos + cameraPosition;
    vec2 levels = light_levels();
    bool inside = isEyeInWater == 1;
    vec3 face = normalize(v_normal);
    if (dot(face, to_eye) < 0.0) {
        face = -face;
    }
    n = face;
    float rough = 0.0;
    if (abs(face.y) > 0.5) {
        vec2 local = water_local(v_scene_pos);
        float grazing = abs(to_eye.y);
        float footprint = distance_away * pixel_angle() * inversesqrt(max(grazing, 0.02));
        float open = smoothstep(0.5, 0.95, levels.y);
        float rain = g_water.x * smoothstep(0.8, 0.9, levels.y);
        // Weaker under a roof and in caves, stronger in the open; calm and choppy stretches; flatter at a grazing
        // look, where the waves would only break the reflection into noise.
        float strength = WATER_WAVES * mix(0.25, 1.0, open) * water_chop(v_scene_pos)
                       * mix(0.35, 1.0, smoothstep(0.0, 0.25, grazing));
        Waves w = water_waves(local, strength, swell_room(v_scene_pos), rain, footprint);
        rough = w.rough;
#ifdef RAIN_RINGS
        w.slope += rain_rings(local, rain * (1.0 - smoothstep(10.0, 35.0, distance_away)), footprint, rough);
#endif
        w.slope += splash_ring(world);
        vec3 wave = wave_normal(w);
        n = face.y > 0.0 ? wave : -wave;
    }
    spread = 2.0 * sqrt(rough);
    float cos_v = max(dot(n, to_eye), 0.0);
    float fresnel = fresnel_dielectric(cos_v, inside ? 1.0 / water_ior : water_ior);
    // The light the water takes in from above (its own surface never shades it, a03).
    vec3 light_here = forward_light(vec3(0.0, 1.0, 0.0), v_scene_pos, levels, 1.0, 1.0, true);

#if WATER_QUALITY >= 1 && defined PROGRAM_WATER
    reflected = fresnel;
    vec3 own_reflection = vec3(0.0);
#else
    // The sky and its clouds where the sky can be seen (Minecraft's sky light says), a dim copy of the water's own
    // light below ground.
    reflected = 0.0;
    vec3 r = reflect(-to_eye, n);
    r.y = inside ? -abs(r.y) : abs(r.y);
    vec3 own_reflection = (inside ? light_here * 0.05 : mix(light_here * 0.05, reflect_sky(r),
                           vanilla_sky_share(levels.y))) * fresnel;
#endif

    // The sun's glint: its disc as the sky shows it, mirrored, spread by the waves too fine to see.
    vec3 sun_glint = vec3(0.0);
#ifdef SUNLIGHT
    float sky_gate = smoothstep(0.05, 0.3, levels.y);
    if (!inside && sky_gate > 0.0 && g_light_dir.w > 0.5) {
        vec3 r_sun = reflect(-to_eye, n);
        float reach = shadow_solid(v_scene_pos + vec3(0.0, 0.05, 0.0), 1.0, sun_beyond_map(levels.y));
        sun_glint = sun_seen(r_sun, spread) * fresnel * reach * sky_gate * cloud_shadow(v_scene_pos);
    }
#endif

    // What lies under it, and how much water is between.
    vec3 here = (gbufferModelView * vec4(v_scene_pos, 1.0)).xyz;
    vec2 at = gl_FragCoord.xy;
    float depth_under = texelFetch(depthtex1, ivec2(at), 0).r;
#ifdef WATER_SEES_UNDER
    if (abs(face.y) > 0.5) {
        // Bent by the waves' tilt: further the deeper it lies (up to 8 blocks), less far off; never onto something
        // in front of the water.
        float behind = distance_under(at, depth_under, here, 8.0);
        vec3 tilt = mat3(gbufferModelView) * (n - face);
        vec2 shift = -tilt.xy * min(behind, 8.0) / max(distance_away, 1.0) * 0.1 * render_size().y;
        vec2 q = clamp(at + shift, vec2(0.5), render_size() - 0.5);
        float d = texelFetch(depthtex1, ivec2(q), 0).r;
        if (d > gl_FragCoord.z) {
            at = q;
            depth_under = d;
        }
    }
#endif
    float thickness = distance_under(at, depth_under, here, 64.0);
    vec3 extinction = water_absorption_for(v_color.rgb) + water_scatter;
    // At least a block or two of it (more in the dark), so even a shallow edge reads as water; none when you are in
    // it (program/fog.fsh has that water).
    float lit = smoothstep(0.02, 0.4, luminance(light_here));
    float depth = inside ? 0.0 : max(thickness, mix(2.0, 1.0, lit));
    vec3 through = exp(-extinction * depth);
    // The light it scatters towards you on the way, from the light that came in at the surface (dimmed on its way
    // down as well as on its way back): it fades in over the first blocks and then holds, the water's own glow.
    float cos_t = sqrt(max(1.0 - (1.0 - cos_v * cos_v) / (water_ior * water_ior), 0.0));
    vec3 along = extinction * (1.0 + 1.15 * cos_t);
    vec3 glow = light_here * (water_scatter * water_scatter_boost) * (1.0 - exp(-along * depth)) / along;

#if DEBUG_VIEW == 15
    // The water's own: red your feet are in water, green eighths of the seconds since you last went in or out (to
    // 8), blue how much room the swell has here.
    float since = frameTimeCounter - g_splash.w;
    return vec4(g_water.y, clamp(since / 8.0, 0.0, 1.0), swell_room(v_scene_pos), 1.0);
#endif
#ifdef WATER_SEES_UNDER
    vec3 under = textureLod(colortex5, at / window_size(), 0.0).rgb;
    return vec4((under * through + glow) * (1.0 - fresnel) + own_reflection + sun_glint, 1.0);
#else
    float kept = dot(through, vec3(0.3, 0.4, 0.3));
    vec3 emitted = glow * (1.0 - fresnel) + own_reflection + sun_glint;
    return vec4(emitted, 1.0 - kept * (1.0 - fresnel));
#endif
}
#endif

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    vec4 color = base_color();
#ifdef PROGRAM_CLOUDS
    color.a *= 0.85;
#endif
    if (color.a < 0.004) {
        discard;
    }
    vec3 albedo = srgb_to_linear(color.rgb);

#if defined PROGRAM_WATER || defined PROGRAM_HAND_WATER
    if (v_block == BLOCK_WATER) {
        float reflected;
        vec3 wave;
        float spread;
        out_color = shade_water(color, reflected, wave, spread);
        out_motion = motion_out();
#if defined PROGRAM_WATER
        out_water = vec4(encode_normal(wave), reflected, 1.0 - 0.49 * clamp(spread / 0.5, 0.0, 1.0));
#endif
        return;
    }
#endif
#if defined PROGRAM_WATER
    out_water = vec4(0.0);
#endif

    vec3 n = normalize(v_normal);
#if defined PROGRAM_PARTICLES || defined PROGRAM_WEATHER || defined PROGRAM_TEXTURED || defined PROGRAM_TEXTURED_LIT
    n = vec3(0.0, 1.0, 0.0); // flat sprites: lit as from above
    float shadow_radius = 0.0;
#elif defined PROGRAM_CLOUDS
    float shadow_radius = 0.0;
#else
    float shadow_radius = 1.0;
#endif
    vec3 light = forward_light(n, v_scene_pos, light_levels(), 1.0, shadow_radius);
#if defined PROGRAM_PARTICLES
    // Flames, lava drips, sparks, portal and glow particles come from Minecraft at full brightness: they glow (about
    // as a hot texel does, so they bloom), instead of taking the dim light around them.
    if (light_levels().x > 0.99) {
        light = max(light, vec3(4.6));
    }
#endif
#if defined PROGRAM_CLOUDS && defined SUNLIGHT
    light = cloud_light(normalize(v_scene_pos));
#elif defined PROGRAM_CLOUDS
    light = g_light_color.rgb * 0.35 + g_sky_up.rgb * 1.2;
#endif
    out_color = vec4(albedo * light * color.a, color.a);
    out_motion = motion_out();
}

#elif defined LIT_LINES

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 out_color;  // over the lit scene, premultiplied (see shaders.properties)
layout(location = 1) out vec4 out_albedo; // over the G-buffer's colour, premultiplied; its alpha (the material) stays

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    vec4 color = base_color();
    if (color.a < 0.004) {
        discard;
    }
    out_color = vec4(srgb_to_linear(color.rgb) * color.a, color.a);
    out_albedo = vec4(color.rgb * color.a, color.a);
}

#elif defined LIT_GLOW

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    vec4 color = base_color();
    vec3 c = srgb_to_linear(color.rgb) * color.a;
#if defined PROGRAM_ARMOR_GLINT || defined PROGRAM_CLRWL_GLINT
    c *= 0.6;
#else
    c *= 3.0;
#endif
    out_color = vec4(c, 0.0);
}

#elif defined LIT_SKY

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 out_color;

void main() {
    discard;
}

#elif defined LIT_CRACKS

/* RENDERTARGETS: 1 */
layout(location = 0) out vec4 out_color;

void main() {
    if (outside_render_area(gl_FragCoord.xy)) {
        discard;
    }
    vec4 color = base_color();
    if (color.a < 0.1) {
        discard;
    }
    // Multiplied into the surface's colour twice over (see shaders.properties), so mid grey leaves it as it is.
    out_color = vec4(color.rgb, 1.0);
}

#endif

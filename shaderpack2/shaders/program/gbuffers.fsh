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

// How much of a light block's texel glows: the bright, hot ones (a torch's flame, not its stick; a furnace's fire,
// not its stone). Lamps that glow all over (glowstone, lava, froglights) glow from their dimmer texels too.
float glow_of(vec3 albedo, int id, float emission) {
    bool listed = id >= BLOCK_LIGHT && id < BLOCK_LIGHT + LIGHT_KINDS;
    if (!listed && emission < 1.0) {
        return 0.0;
    }
    int kind = id - BLOCK_LIGHT;
    bool all_over = listed && (kind == 4 || kind == 5 || kind == 6 || kind == 7 || (kind >= 9 && kind <= 11)
            || kind == KIND_LAVA || kind == KIND_MAGMA || kind == 24 || kind == 25 || kind == 32);
    float peak = max3(albedo);
    float hot = all_over ? smoothstep(0.15, 0.6, luminance(albedo)) : smoothstep(0.55, 0.9, peak);
    float strength = listed ? light_kind(kind).a / 22.0 : emission / 15.0;
    return hot * clamp(strength, 0.3, 1.2);
}

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

/* RENDERTARGETS: 0,7 */
layout(location = 0) out vec4 out_color; // premultiplied by its alpha (see the blend modes in shaders.properties)
layout(location = 1) out vec4 out_motion;

vec3 view_of(vec2 frag, float depth) {
    vec2 ndc = (frag / render_size()) * 2.0 - 1.0;
    vec4 v = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

#if defined PROGRAM_WATER || defined PROGRAM_HAND_WATER
// The water's surface: the sky (or a dim cave) in its reflection, the sun's glint, and the light the water between
// it and what lies under it scatters back; what lies under shows through, less the deeper it is.
vec4 shade_water(vec4 base) {
    vec3 to_eye = normalize(-v_scene_pos);
    float distance_away = length(v_scene_pos);
    vec3 world = v_scene_pos + cameraPosition;
    vec3 n = normalize(v_normal);
    bool top = n.y > 0.5;
    if (top) {
        n = wave_normal(world.xz, frameTimeCounter, 1.0, distance_away);
    }
    if (isEyeInWater == 1) {
        n = -n;
    }
    float cos_v = clamp(dot(n, to_eye), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cos_v, 5.0);

    // The reflection: the sky where the sky can be seen (Minecraft's sky light says), a dim copy of the water's own
    // light below ground.
    vec3 r = reflect(-to_eye, n);
    r.y = abs(r.y);
    vec3 light_here = forward_light(vec3(0.0, 1.0, 0.0), v_scene_pos, light_levels(), 1.0, 1.0);
    vec3 reflected = mix(light_here * 0.05, sky(r, false), vanilla_sky_share(light_levels().y));
    // The sun's glint, sharp.
    vec3 sun_glint = vec3(0.0);
#ifdef SUNLIGHT
    float sun_through = smoothstep(0.05, 0.3, light_levels().y);
    if (sun_through > 0.0 && g_light_dir.w > 0.5) {
        vec3 reach = shadow_light(v_scene_pos + vec3(0.0, 0.05, 0.0), 1.0);
        sun_glint = g_light_color.rgb * reach * ggx_specular(n, to_eye, g_light_dir.xyz, 0.96, 0.02) * sun_through;
    }
#endif

    // How much water lies between the surface and what is under it.
    float depth_under = texelFetch(depthtex1, ivec2(gl_FragCoord.xy), 0).r;
    float thickness = 64.0;
    if (depth_under < 1.0) {
        vec3 under = view_of(gl_FragCoord.xy, depth_under);
        vec3 here = (gbufferModelView * vec4(v_scene_pos, 1.0)).xyz;
        thickness = distance(under, here);
    }
    if (isEyeInWater == 1) {
        thickness = 0.0;
    }
    vec3 through = exp(-water_extinction * thickness);
    float kept = dot(through, vec3(0.3, 0.4, 0.3));
    // The light the water scatters towards you on the way (its own blue-green), from the light above it.
    vec3 tint = clamp(base.rgb * 1.2, 0.0, 1.0);
    vec3 scattered = light_here * water_scattering / water_extinction * (1.0 - through) * tint * 2.2;

    vec3 emitted = scattered * (1.0 - fresnel) + reflected * fresnel + sun_glint;
    float alpha = 1.0 - kept * (1.0 - fresnel);
    return vec4(emitted, alpha);
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
        out_color = shade_water(color);
        out_motion = motion_out();
        return;
    }
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

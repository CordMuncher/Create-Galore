// Depth of field, after DLSS (or FSR) at the window's size, as NVIDIA's DLSS guide places what blurs the frame (the
// upscaler wants it sharp), and the same from frame to frame wherever nothing moves, as nothing after DLSS smooths
// noise over time. A thin lens: a point at a distance blurs over a disc whose diameter, against the frame's height,
// is the aperture's diameter (the focal length over the f-number) times how far its inverse distance is from the
// focus's, over the height the view spans a metre ahead (2 tan(fov/2)). The circle's radius counts negative for what
// lies in front of the focus.
//
// As Call of Duty: Advanced Warfare's (Jimenez, SIGGRAPH 2014), in colortex4 (free after the exposure pass) at half
// the window's size: the frame with each pixel's circle (dof.csh, DOF_PREPARE), the largest circle in front of the
// focus in each 16 by 16 tile (DOF_TILES), then each pixel gathers what spreads over it (DOF_GATHER): the far blur over
// its own circle from the points behind the focus whose circles reach it, and the near blur over the largest near
// circle that reaches it, from the points in front whose circles reach it, with how much of it they cover, so what
// is in front spreads out over what is sharp behind it. The final pass lays the far blur over the sharp frame by each
// pixel's own circle, and the near blur over that. The hand stays sharp.

#if !defined LIB_DOF
#define LIB_DOF

#include "/lib/settings.glsl"
#include "/lib/frame.glsl"
#include "/lib/globals.glsl"

uniform sampler2D colortex4;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjection;
#endif

// The aperture's diameter (m).
const float dof_aperture = float(DOF_FOCAL_LENGTH) / DOF_F_STOP * 0.001;
// The widest blur's radius against the frame's height.
const float dof_max_radius = 0.025;
// How many points each pixel's blur gathers.
#if DOF_QUALITY == 1
const int dof_points = 24;
#elif DOF_QUALITY == 2
const int dof_points = 48;
#else
const int dof_points = 80;
#endif
// The tiles' size (half-size pixels).
const int dof_tile = 16;
// The hand's depth is squeezed into the middle of the range (Iris draws it at MC_HAND_DEPTH of its own).
const float dof_hand_depth = 0.5 + 0.5 * MC_HAND_DEPTH;

// colortex4's quarters: the prepared frame (lower left), the far blur (lower right), the near blur (upper left) and
// the tiles (upper right), each half the window's size.
ivec2 dof_half() {
    return ivec2(window_size()) / 2;
}
ivec2 dof_far_origin() {
    return ivec2(dof_half().x, 0);
}
ivec2 dof_near_origin() {
    return ivec2(0, dof_half().y);
}
ivec2 dof_tiles_origin() {
    return dof_half();
}
ivec2 dof_tiles() {
    return (dof_half() + dof_tile - 1) / dof_tile;
}

// The inverse distance (1/m, along the view) of what a render pixel shows: 0 for the sky, negative for the hand.
float dof_inverse_distance(ivec2 texel) {
    float depth = texelFetch(depthtex0, texel, 0).r;
    if (depth < dof_hand_depth) {
        return -1.0;
    }
    if (depth < 1.0) {
        return (depth * 2.0 - 1.0 + gbufferProjection[2][2]) / gbufferProjection[3][2];
    }
#ifdef DISTANT_HORIZONS
    float dh = texelFetch(dhDepthTex0, texel, 0).r;
    if (dh < 1.0) {
        return (dh * 2.0 - 1.0 + dhProjection[2][2]) / dhProjection[3][2];
    }
#endif
    return 0.0;
}

// Where the lens focuses (its inverse distance, 1/m): what the focus pass found, or the chosen distance.
float dof_focus() {
#if DOF_FOCUS > 0
    return 1.0 / float(DOF_FOCUS);
#else
    return global(G_FOCUS).x;
#endif
}

// The radius (window pixels, negative in front of the focus) of the circle what a render pixel shows blurs over.
float dof_circle_at(ivec2 texel, float focus) {
    float inverse_distance = dof_inverse_distance(texel);
    if (inverse_distance < 0.0) {
        return 0.0;
    }
    float height = window_size().y;
    float circle = 0.25 * dof_aperture * gbufferProjection[1][1] * height * (focus - inverse_distance);
    return clamp(circle, -dof_max_radius * height, dof_max_radius * height);
}

// A quarter of colortex4 at a half-size position (pixel units), bilinear, kept inside the quarter.
vec4 dof_sample(ivec2 origin, vec2 at) {
    at = clamp(at, vec2(0.5), vec2(dof_half()) - 0.5) + vec2(origin);
    return textureLod(colortex4, at / window_size(), 0.0);
}

// The sharp frame's colour at a window pixel (at its centre) with the depth of field.
vec3 depth_of_field(vec3 color, vec2 window_pixel) {
    float circle = dof_circle_at(ivec2(window_pixel * render_scale()), dof_focus());
    vec2 at = window_pixel * 0.5;
    // The far blur where this pixel's own circle is more than a pixel or so across.
    if (circle > 1.0) {
        color = mix(color, dof_sample(dof_far_origin(), at).rgb, smoothstep(1.0, 3.0, circle));
    }
    // The near blur over it (its colour comes premultiplied by how much it covers).
    vec4 near = dof_sample(dof_near_origin(), at);
    return color * (1.0 - near.a) + near.rgb;
}

#endif

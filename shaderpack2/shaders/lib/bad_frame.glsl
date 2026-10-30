// Values that must never be infinite or not a number. One such pixel in the bloom spreads over the whole screen (its
// first copy weighs every pixel by its brightness), which shows as a single white or black frame. The passes that
// hand the frame on replace any such value with nothing and note where they caught it; program/frame.csh keeps the
// note, and while BAD_FRAME_MARK is on the final pass shows it as a small square at the middle of the left edge for
// about two seconds: red caught before DLSS (the lit scene), green after DLSS, blue in the frame's own values (the
// sun, the sky), white in the final pass.
//
// The record (one word of cg_data_img, data_bad_frame): bits 0..7 what the passes caught this frame, 8..15 what the
// mark shows, 16 and up how many frames it still shows.

#if !defined LIB_BAD_FRAME
#define LIB_BAD_FRAME

#include "/lib/data.glsl"

#define BAD_BEFORE_DLSS 1u
#define BAD_AFTER_DLSS 2u
#define BAD_GLOBALS 4u
#define BAD_FINAL 8u

bool bad_value(vec3 c) {
    return any(isnan(c)) || any(isinf(c));
}

bool bad_value(vec4 c) {
    return any(isnan(c)) || any(isinf(c));
}

void note_bad(uint where) {
    data_or(data_bad_frame, where);
}

#endif

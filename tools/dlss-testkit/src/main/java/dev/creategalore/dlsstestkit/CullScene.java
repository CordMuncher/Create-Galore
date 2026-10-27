package dev.creategalore.dlsstestkit;

import java.util.List;

/**
 * Create's machines beside a near overhang, for the "cull" viewpoint: Colorwheel skips drawing a machine that its depth
 * check says nearer things hide. A row of shafts and cogwheels ten blocks ahead and left, low in the view, and a stone
 * lintel just in front of the camera, across the top half of the view; every machine is in plain view under it. With a
 * render scale the pack draws the frame into the lower left corner of its buffers, and a check that reads the whole
 * buffer as the screen looks for each machine further up and right, at the lintel, and hides it. Built in the air over
 * the open sea (x 1056..1072, y 64..71, z 99..109, empty in the DLAA test world) when the camera first gets there and
 * removed when the run ends.
 */
final class CullScene {
    static final String NAME = "cull";
    private static final String CLEAR = "fill 1056 64 99 1072 71 109 air";
    private static final List<String> BUILD = List.of(
            CLEAR,
            // Shafts along x, two cogwheels among them, and a row of cogwheels under them.
            "fill 1058 65 100 1064 65 100 create:shaft[axis=x]",
            "setblock 1059 65 100 create:cogwheel[axis=x]",
            "setblock 1062 65 100 create:cogwheel[axis=x]",
            "fill 1058 64 101 1064 64 101 create:cogwheel[axis=z]",
            // The lintel: its underside a little over eye level, two blocks ahead.
            "fill 1056 68 108 1072 70 108 stone");
    /** The camera: eye height 68.3 (0.3 under the lintel), looking north along the row. */
    static final double X = 1065.5, Y = 66.68, Z = 111.5;

    private static boolean built;

    private CullScene() {}

    static void build() {
        if (!built) {
            built = true;
            BUILD.forEach(Capture::server);
            Capture.log("built the cull scene");
        }
    }

    static void remove() {
        if (built) {
            built = false;
            Capture.server(CLEAR);
        }
    }

    /** Takes the scene away even if this game didn't build it: an earlier game may have left it standing. */
    static void clear() {
        built = true;
        remove();
    }
}

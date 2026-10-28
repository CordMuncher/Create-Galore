package dev.creategalore.dlsstestkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The camera paths, by frame (never by time), so each mode gets the same picture at the same frame whatever its frame
 * rate. Poses are offsets from the viewpoint: turn right, look down, move right, move up (degrees, blocks).
 */
final class Paths {
    record Pose(float yaw, float pitch, double right, double up) {}

    record Path(int length, int[] captures, IntFunction<Pose> pose) {
        Pose at(int frame) {
            return pose.apply(frame);
        }
    }

    static final Pose HOLD = new Pose(0, 0, 0, 0);

    private static final Map<String, Path> PATHS = new LinkedHashMap<>();

    static {
        // Settled image: what each mode looks like once its history has converged. The last 8 frames in a row show
        // shimmer: with the camera still, whatever changes between them (besides the waving leaves, the same in every
        // mode) is the anti-aliasing flickering.
        PATHS.put("still", new Path(120, new int[] {112, 113, 114, 115, 116, 117, 118, 119}, f -> HOLD));
        // A short still look, for the residual views (they keep no history, so nothing needs to settle).
        PATHS.put("brief", new Path(40, new int[] {32, 33, 34, 35, 36, 37, 38, 39}, f -> HOLD));
        // A fast 60 degree flick right and back: smearing during the turn, then how quickly newly seen areas clean up.
        PATHS.put("flick", new Path(160, new int[] {59, 64, 68, 70, 74, 84, 99, 104, 108, 110, 114, 124, 159}, f -> {
            float yaw;
            if (f < 60) {
                yaw = 0;
            } else if (f < 68) {
                yaw = 60 * ease((f - 60) / 8.0f);
            } else if (f < 100) {
                yaw = 60;
            } else if (f < 108) {
                yaw = 60 * (1 - ease((f - 100) / 8.0f));
            } else {
                yaw = 0;
            }
            return new Pose(yaw, 0, 0, 0);
        }));
        // A slow steady turn: shimmer on leaves, fences and glass edges while everything moves a little each frame.
        PATHS.put("pan", new Path(150, new int[] {50, 100, 149}, f -> new Pose(0.3f * f, 0, 0, 0)));
        // A sideways walk: near things pass far things, so edges uncover the background (ghosting trails).
        PATHS.put("strafe", new Path(150, new int[] {50, 100, 149}, f -> new Pose(0, 0, 0.04 * f, 0)));
        // A fast climb, 0.15 blocks a frame (15 blocks a second at 100 fps, like a boat at speed or creative flight),
        // with the view held: the last 32 frames in a row show whatever on screen changes only because the player
        // moved, such as the light on a held item.
        PATHS.put("rise", new Path(140, captureRun(108, 32), f -> new Pose(0, 0, 0, 0.15 * f)));
        // Standing on the ride scene's sailing ship, the view held (the ship carries the player): the last 48 frames
        // in a row show whatever on screen changes only because the player rides along, such as the light on a held
        // item.
        PATHS.put("ride", new Path(160, captureRun(112, 48), f -> HOLD));
        // A short clip for the Photon RR research: 40 frames still, then a slow turn right (0.25 degrees a frame) with
        // its 64 frames saved in a row, to play back as a video (the pack's noise crawling, smears, things that move).
        PATHS.put("clip", new Path(104, captureRun(40, 64), f -> new Pose(0.25f * Math.max(0, f - 40), 0, 0, 0)));
        // Input latency: 600 frames held still, nothing saved; the Bridge's latency numbers are taken over them
        // (-Dcreate_galore_dlss_testkit.latencyFrames sets fewer, for a plumbing check on a slow machine).
        PATHS.put("latency", new Path(Integer.getInteger(TestKit.MOD_ID + ".latencyFrames", 600), new int[0], f -> HOLD));
    }

    static final List<String> NAMES = List.copyOf(PATHS.keySet());

    private Paths() {}

    static Path get(String name) {
        return PATHS.get(name);
    }

    private static int[] captureRun(int first, int count) {
        int[] frames = new int[count];
        for (int i = 0; i < count; i++) {
            frames[i] = first + i;
        }
        return frames;
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }
}

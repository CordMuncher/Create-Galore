package dev.creategalore.bridge.framegen;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;

import java.nio.FloatBuffer;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4fc;

/**
 * Frame generation's camera for one frame: the floats cg_fg_frame takes (FgCameraFloat in
 * tools/bridge-mod/native/cg_framegen.h), in memory the native side reads.
 *
 * The matrices go to the native side as JOML stores them (column-major for column vectors), which is the same sixteen
 * floats as the row-major matrices for row vectors that Streamline and DirectX write. Streamline wants them without
 * jitter, which is how Iris hands them over (Photon jitters in its vertex shaders).
 *
 * View to clip is the game's projection (with view bobbing and nausea in it, as Minecraft puts them there) with its
 * z row made (0, 0, 0, near): clip z is then near and depth = near / w, the reversed, infinite depth the pack writes
 * for DLSS (PhotonPatcher's near over clip w). Clip space is OpenGL's and DirectX's alike for x, y and w (+y up); the
 * inputs' rows are flipped to DirectX's order (top row first), so the jitter's and motion's y turn over, not the
 * matrices.
 *
 * Clip to last frame's clip, in doubles (the camera's position is large): P' V' T(c - c') V^-1 P^-1, where V is the
 * game's view rotation (gbufferModelView), c the camera's position in the world and ' last frame's. Without last
 * frame's camera (the first frame, or frames skipped) it is the identity and the frame resets.
 */
final class FgCamera {
    // cg_framegen.h, FgCameraFloat
    static final int VIEW_TO_CLIP = 0;
    static final int CLIP_TO_VIEW = 16;
    static final int CLIP_TO_PREV_CLIP = 32;
    static final int PREV_CLIP_TO_CLIP = 48;
    static final int JITTER = 64;
    static final int MOTION_SCALE = 66;
    static final int CAMERA_POS = 68;
    static final int CAMERA_UP = 71;
    static final int CAMERA_RIGHT = 74;
    static final int CAMERA_FORWARD = 77;
    static final int NEAR = 80;
    static final int FAR = 81;
    static final int FOV_Y = 82;
    static final int ASPECT = 83;
    static final int FRAME_TIME_MS = 84;
    static final int METERS_PER_UNIT = 85;
    static final int FLOATS = 86;

    private final FloatBuffer floats = memAllocFloat(FLOATS);
    private final Matrix4d viewToClip = new Matrix4d();
    private final Matrix4d clipToView = new Matrix4d();
    private final Matrix4d clipToPrevClip = new Matrix4d();
    private final Matrix4d prevClipToClip = new Matrix4d();
    private final Matrix4d prevViewToClip = new Matrix4d();
    private final Matrix4d prevView = new Matrix4d();
    private final Matrix4d view = new Matrix4d();
    private double prevX;
    private double prevY;
    private double prevZ;
    private long prevFrame = Long.MIN_VALUE;
    private boolean continuous;

    /**
     * This frame's camera: the game's view rotation and projection (no jitter), the camera's world position, the planes,
     * the jitter the pack rendered with (render pixels, OpenGL's +y up), the render size and the time since the frame
     * before. {@code frame} counts frames, so a gap since the last update resets.
     */
    void update(Matrix4fc gameView, Matrix4fc gameProjection, double x, double y, double z, float near, float far,
                float jitterX, float jitterY, int renderWidth, int renderHeight, float frameTimeMs, long frame) {
        view.set(gameView);
        viewToClip.set(gameProjection).m02(0.0).m12(0.0).m22(0.0).m32(near);
        clipToView.set(viewToClip).invert();
        continuous = frame == prevFrame + 1;
        if (continuous) {
            clipToPrevClip.set(prevViewToClip).mul(prevView).translate(x - prevX, y - prevY, z - prevZ)
                    .mul(new Matrix4d(view).invert()).mul(clipToView);
            prevClipToClip.set(clipToPrevClip).invert();
        } else {
            clipToPrevClip.identity();
            prevClipToClip.identity();
        }
        put(VIEW_TO_CLIP, viewToClip);
        put(CLIP_TO_VIEW, clipToView);
        put(CLIP_TO_PREV_CLIP, clipToPrevClip);
        put(PREV_CLIP_TO_CLIP, prevClipToClip);
        floats.put(JITTER, jitterX).put(JITTER + 1, -jitterY);
        floats.put(MOTION_SCALE, 1.0f / renderWidth).put(MOTION_SCALE + 1, 1.0f / renderHeight);
        floats.put(CAMERA_POS, (float) x).put(CAMERA_POS + 1, (float) y).put(CAMERA_POS + 2, (float) z);
        // The rotation's rows are the camera's axes in the world: x right, y up, z backwards.
        floats.put(CAMERA_RIGHT, (float) view.m00()).put(CAMERA_RIGHT + 1, (float) view.m10()).put(CAMERA_RIGHT + 2, (float) view.m20());
        floats.put(CAMERA_UP, (float) view.m01()).put(CAMERA_UP + 1, (float) view.m11()).put(CAMERA_UP + 2, (float) view.m21());
        floats.put(CAMERA_FORWARD, (float) -view.m02()).put(CAMERA_FORWARD + 1, (float) -view.m12())
                .put(CAMERA_FORWARD + 2, (float) -view.m22());
        floats.put(NEAR, near).put(FAR, far);
        floats.put(FOV_Y, (float) (2.0 * Math.atan(1.0 / viewToClip.m11())));
        floats.put(ASPECT, (float) (viewToClip.m11() / viewToClip.m00()));
        floats.put(FRAME_TIME_MS, frameTimeMs).put(METERS_PER_UNIT, 1.0f);

        prevViewToClip.set(viewToClip);
        prevView.set(view);
        prevX = x;
        prevY = y;
        prevZ = z;
        prevFrame = frame;
    }

    private void put(int offset, Matrix4dc m) {
        // JOML's order: m00 m01 m02 m03 m10 ... (column by column)
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                floats.put(offset + column * 4 + row, (float) m.getRowColumn(row, column));
            }
        }
    }

    /** False when this frame's camera has no last frame to go back to (the first, or after a gap): reset. */
    boolean continuous() {
        return continuous;
    }

    /** The floats' address, for cg_fg_frame. */
    long address() {
        return memAddress(floats);
    }

    float get(int index) {
        return floats.get(index);
    }

    /** This frame's clip to last frame's clip (column vectors), for the checks. */
    Matrix4dc clipToPrevClip() {
        return clipToPrevClip;
    }
}

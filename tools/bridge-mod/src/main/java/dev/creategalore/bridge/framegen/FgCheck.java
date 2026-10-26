package dev.creategalore.bridge.framegen;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_PACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_PACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_PACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_RG;
import static org.lwjgl.opengl.GL45C.glGetTextureImage;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import org.joml.Vector4d;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * frameGenCheck (testing): reads one frame's inputs and their sources back (a stall) and checks the capture pass on
 * every pixel, and the camera against the motion vectors.
 *
 * Copy: each input against its source with the rows flipped (hudless and UI alpha exactly; depth and motion exactly
 * inside the render-sized corner, motion's y negated, 0 outside). Camera: for every 7th pixel of the corner with
 * depth, its position one frame back by clip to last frame's clip, against where its motion vector points. Where only
 * the camera moved the two agree, so the median error is a fraction of a pixel when the matrices, the depth and the
 * motion vectors are all in the same convention; the hand and things that move by themselves stray. The medians with
 * motion's x or y turned the other way say which sign would be wrong.
 */
final class FgCheck {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int STEP = 7;
    /** The camera check counts only when the motion vectors move this much (render pixels, median). */
    private static final double MOVING = 0.5;
    private static final double GOOD_ERROR = 0.25;

    private static int count;

    private FgCheck() {}

    static void run(int[] targets, int hudless, int frame, int depth, int motion, int width, int height, int renderWidth,
                    int renderHeight, boolean noHud, FgCamera camera, boolean cameraValid) {
        int pixels = width * height;
        ByteBuffer outHudless = MemoryUtil.memAlloc(pixels * 4);
        ByteBuffer outAlpha = MemoryUtil.memAlloc(pixels);
        ByteBuffer outDepth = MemoryUtil.memAlloc(pixels * 4);
        ByteBuffer outMotion = MemoryUtil.memAlloc(pixels * 8);
        ByteBuffer inHudless = MemoryUtil.memAlloc(pixels * 4);
        ByteBuffer inFrame = MemoryUtil.memAlloc(pixels * 4);
        ByteBuffer inDepth = MemoryUtil.memAlloc(pixels * 4);
        ByteBuffer inMotion = MemoryUtil.memAlloc(pixels * 8);
        try {
            read(targets[0], GL_RGBA, GL_UNSIGNED_BYTE, outHudless);
            read(targets[1], GL_RED, GL_UNSIGNED_BYTE, outAlpha);
            read(targets[2], GL_RED, GL_FLOAT, outDepth);
            read(targets[3], GL_RG, GL_FLOAT, outMotion);
            read(hudless, GL_RGBA, GL_UNSIGNED_BYTE, inHudless);
            read(frame, GL_RGBA, GL_UNSIGNED_BYTE, inFrame);
            read(depth, GL_RED, GL_FLOAT, inDepth);
            read(motion, GL_RG, GL_FLOAT, inMotion);
            count++;

            long badHudless = 0;
            long badAlpha = 0;
            long badDepth = 0;
            long badMotion = 0;
            long badOutside = 0;
            long hudPixels = 0;
            String first = null;
            for (int y = 0; y < height; y++) {
                int source = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    int o = y * width + x;
                    int i = source * width + x;
                    for (int c = 0; c < 3; c++) {
                        if (outHudless.get(o * 4 + c) != inHudless.get(i * 4 + c)) {
                            badHudless++;
                            first = first != null ? first : "hudless at (" + x + ", " + y + ")";
                            break;
                        }
                    }
                    int alpha = noHud ? 0 : inFrame.get(i * 4 + 3) & 0xFF;
                    hudPixels += alpha != 0 ? 1 : 0;
                    if ((outAlpha.get(o) & 0xFF) != alpha) {
                        badAlpha++;
                        first = first != null ? first : "UI alpha at (" + x + ", " + y + ")";
                    }
                    float d = outDepth.getFloat(o * 4);
                    float mx = outMotion.getFloat(o * 8);
                    float my = outMotion.getFloat(o * 8 + 4);
                    if (x < renderWidth && y < renderHeight) {
                        int r = (renderHeight - 1 - y) * width + x;
                        if (d != inDepth.getFloat(r * 4)) {
                            badDepth++;
                            first = first != null ? first : "depth at (" + x + ", " + y + ")";
                        }
                        if (mx != inMotion.getFloat(r * 8) || my != -inMotion.getFloat(r * 8 + 4)) {
                            badMotion++;
                            first = first != null ? first : "motion at (" + x + ", " + y + ")";
                        }
                    } else if (d != 0.0f || mx != 0.0f || my != 0.0f) {
                        badOutside++;
                        first = first != null ? first : "outside the render size at (" + x + ", " + y + ")";
                    }
                }
            }
            long bad = badHudless + badAlpha + badDepth + badMotion + badOutside;
            String copy = bad == 0
                    ? String.format(Locale.ROOT, "copy ok (%dx%d, render %dx%d, %d HUD pixels%s)", width, height, renderWidth,
                            renderHeight, hudPixels, noHud ? ", no HUD" : "")
                    : String.format(Locale.ROOT, "COPY WRONG: hudless %d, UI alpha %d, depth %d, motion %d, outside %d pixels "
                            + "(first: %s)", badHudless, badAlpha, badDepth, badMotion, badOutside, first);
            String cameraText = cameraValid ? cameraCheck(outDepth, outMotion, width, renderWidth, renderHeight, camera)
                    : "no camera this frame";
            if (bad == 0) {
                LOG.info("Frame generation check {}: {}; {}", count, copy, cameraText);
            } else {
                LOG.warn("Frame generation check {}: {}; {}", count, copy, cameraText);
            }
            if (count == 1 || count % 12 == 0) {
                LOG.info("Frame generation check {}: camera fov {} deg, aspect {} (render {}), near {}, far {}, jitter {}, {} px, "
                                + "motion scale {}, {}, position {}, {}, {}, forward {}, {}, {}, up {}, {}, {}",
                        count, round(Math.toDegrees(camera.get(FgCamera.FOV_Y))), round(camera.get(FgCamera.ASPECT)),
                        round((double) renderWidth / renderHeight), camera.get(FgCamera.NEAR), camera.get(FgCamera.FAR),
                        round(camera.get(FgCamera.JITTER)), round(camera.get(FgCamera.JITTER + 1)),
                        camera.get(FgCamera.MOTION_SCALE), camera.get(FgCamera.MOTION_SCALE + 1),
                        round(camera.get(FgCamera.CAMERA_POS)), round(camera.get(FgCamera.CAMERA_POS + 1)), round(camera.get(FgCamera.CAMERA_POS + 2)),
                        round(camera.get(FgCamera.CAMERA_FORWARD)), round(camera.get(FgCamera.CAMERA_FORWARD + 1)),
                        round(camera.get(FgCamera.CAMERA_FORWARD + 2)), round(camera.get(FgCamera.CAMERA_UP)),
                        round(camera.get(FgCamera.CAMERA_UP + 1)), round(camera.get(FgCamera.CAMERA_UP + 2)));
            }
        } finally {
            MemoryUtil.memFree(outHudless);
            MemoryUtil.memFree(outAlpha);
            MemoryUtil.memFree(outDepth);
            MemoryUtil.memFree(outMotion);
            MemoryUtil.memFree(inHudless);
            MemoryUtil.memFree(inFrame);
            MemoryUtil.memFree(inDepth);
            MemoryUtil.memFree(inMotion);
        }
    }

    /** Where clip to last frame's clip puts sampled pixels against where their motion vectors point (DirectX's rows). */
    private static String cameraCheck(ByteBuffer depth, ByteBuffer motion, int width, int renderWidth, int renderHeight,
                                      FgCamera camera) {
        if (!camera.continuous()) {
            return "camera reset this frame";
        }
        int max = (renderWidth / STEP + 1) * (renderHeight / STEP + 1);
        double[] error = new double[max];
        double[] flipX = new double[max];
        double[] flipY = new double[max];
        double[] moved = new double[max];
        double[] expectedMove = new double[max];
        int n = 0;
        Vector4d p = new Vector4d();
        for (int y = STEP / 2; y < renderHeight; y += STEP) {
            for (int x = STEP / 2; x < renderWidth; x += STEP) {
                int o = y * width + x;
                float d = depth.getFloat(o * 4);
                if (d <= 0.0f) {
                    continue; // sky
                }
                double ndcX = (x + 0.5) / renderWidth * 2.0 - 1.0;
                double ndcY = 1.0 - (y + 0.5) / renderHeight * 2.0;
                p.set(ndcX, ndcY, d, 1.0);
                camera.clipToPrevClip().transform(p);
                if (p.w <= 0.0) {
                    continue;
                }
                double ex = ((p.x / p.w + 1.0) * 0.5 * renderWidth - 0.5) - x;
                double ey = ((1.0 - p.y / p.w) * 0.5 * renderHeight - 0.5) - y;
                double mx = motion.getFloat(o * 8);
                double my = motion.getFloat(o * 8 + 4);
                error[n] = Math.hypot(ex - mx, ey - my);
                flipX[n] = Math.hypot(ex + mx, ey - my);
                flipY[n] = Math.hypot(ex - mx, ey + my);
                moved[n] = Math.hypot(mx, my);
                expectedMove[n] = Math.hypot(ex, ey);
                n++;
            }
        }
        if (n < 16) {
            return "camera: too few pixels with depth (" + n + ")";
        }
        double median = median(error, n);
        double p90 = percentile(error, n, 0.9);
        double move = median(moved, n);
        String verdict = move < MOVING ? "too little motion to judge" : median <= GOOD_ERROR ? "camera ok" : "CAMERA DISAGREES";
        return String.format(Locale.ROOT, "%s: motion vectors vs camera off by %.3f px median (90%%: %.3f) over %d pixels; "
                        + "motion %.2f px median (camera says %.2f); with x turned %.3f, with y turned %.3f",
                verdict, median, p90, n, move, median(expectedMove, n), median(flipX, n), median(flipY, n));
    }

    private static double median(double[] values, int n) {
        return percentile(values, n, 0.5);
    }

    private static double percentile(double[] values, int n, double p) {
        double[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        return sorted[Math.max(0, Math.min(n - 1, (int) Math.ceil(p * n) - 1))];
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static void read(int texture, int format, int type, ByteBuffer into) {
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        try {
            glGetTextureImage(texture, 0, format, type, into);
        } finally {
            glPixelStorei(GL_PACK_ALIGNMENT, alignment);
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        }
    }
}

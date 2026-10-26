package dev.creategalore.bridge.present;

import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL32C.glGetInteger64;
import static org.lwjgl.opengl.GL33C.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33C.glQueryCounter;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memGetLong;

import java.util.Arrays;
import java.util.Locale;

import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing (presentTiming=true): where the time goes between OpenGL finishing a frame and Direct3D 12 drawing it, on the
 * GPU. OpenGL takes a timestamp before its hand-off (the wait for Direct3D 12, if any, and the copy) and one after;
 * cg_present.dll takes one where its draw starts (after the queue's wait for OpenGL) and one where it ends. Both GPU
 * clocks are matched to the performance counter together every quarter second, so their drift cancels. Logged when the
 * settings change (the test kit's modes) and every 10 seconds: the hand-off, OpenGL done to Direct3D 12 draw start, and
 * the draw, as medians and 90th percentiles.
 */
final class PresentTiming implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int RING = 8;
    private static final long CALIBRATE_NANOS = 250_000_000L;
    private static final long LOG_NANOS = 10_000_000_000L;
    private static final int MAX_FRAMES = 4096;

    private final long nowNs;
    private final long calibrate;
    private final long timings;
    private final int[] queries = new int[RING * 2];
    private final long[] values = new long[RING];

    /** The OpenGL clock minus the performance counter (ns), and how long the last match took. */
    private long openglOffset;
    private long matchSpread;
    private long maxSpread;
    private long lastCalibration;
    private long windowStart;
    private String label = "";

    private final long[] handOff = new long[MAX_FRAMES];
    private final long[] toDirectx = new long[MAX_FRAMES];
    private final long[] draw = new long[MAX_FRAMES];
    private int count;
    private int frames;
    private int openglWaits;

    PresentTiming(NativeHelper dll, long nowNs) {
        this.nowNs = nowNs;
        calibrate = dll.symbol("cg_present_calibrate");
        timings = dll.symbol("cg_present_timings");
        glGenQueries(queries);
        calibrateClocks(now());
        windowStart = now();
    }

    private long now() {
        return Ffi.call(":P", nowNs);
    }

    /** What the next lines are measuring (the presenter's switches), for the log. Ends the window so far. */
    void label(String text) {
        report();
        label = text;
    }

    /** Before OpenGL's hand-off of frame {@code value} (a wait for Direct3D 12 if {@code waits}, and the copy). */
    void beforeHandOff(long value, boolean waits) {
        int i = (int) (value % RING);
        values[i] = value;
        glQueryCounter(queries[i * 2], GL_TIMESTAMP);
        frames++;
        if (waits) {
            openglWaits++;
        }
    }

    /** After OpenGL's copy of frame {@code value}, just before it signals Direct3D 12. */
    void afterCopy(long value) {
        glQueryCounter(queries[(int) (value % RING) * 2 + 1], GL_TIMESTAMP);
    }

    /** After each draw: matches the finished Direct3D 12 draws with OpenGL's timestamps, and logs now and then. */
    void frameDone() {
        try (MemoryStack stack = stackPush()) {
            long out = stack.nmalloc(8, 16 * 3 * 8);
            int n;
            while ((n = (int) Ffi.call("PI:I", timings, out, 16)) > 0) {
                for (int r = 0; r < n; r++) {
                    long value = memGetLong(out + r * 24L);
                    long start = memGetLong(out + r * 24L + 8);
                    long end = memGetLong(out + r * 24L + 16);
                    match(value, start, end);
                }
                if (n < 16) {
                    break;
                }
            }
        }
        long now = now();
        if (now - lastCalibration >= CALIBRATE_NANOS) {
            calibrateClocks(now);
        }
        if (now - windowStart >= LOG_NANOS) {
            report();
        }
    }

    private void match(long value, long start, long end) {
        int i = (int) (value % RING);
        if (values[i] != value || count >= MAX_FRAMES) {
            return; // OpenGL's timestamps for that frame were overwritten already
        }
        int before = queries[i * 2];
        int after = queries[i * 2 + 1];
        if (glGetQueryObjecti(after, GL_QUERY_RESULT_AVAILABLE) == 0) {
            return;
        }
        long openglBefore = glGetQueryObjecti64(before, GL_QUERY_RESULT) - openglOffset;
        long openglAfter = glGetQueryObjecti64(after, GL_QUERY_RESULT) - openglOffset;
        handOff[count] = openglAfter - openglBefore;
        toDirectx[count] = start - openglAfter;
        draw[count] = end - start;
        count++;
        values[i] = -1L;
    }

    /** Matches the OpenGL clock and the Direct3D 12 queue's clock to the performance counter, one right after the other. */
    private void calibrateClocks(long now) {
        long before = now();
        long opengl = glGetInteger64(GL_TIMESTAMP);
        long after = now();
        Ffi.call(":I", calibrate);
        openglOffset = opengl - (before + after) / 2;
        matchSpread = after - before;
        maxSpread = Math.max(maxSpread, matchSpread);
        lastCalibration = now;
    }

    private void report() {
        long now = now();
        if (count >= 30) {
            LOG.info(String.format(Locale.ROOT, "Perfect Presentation timing (DirectX 12, %s): %d frames, %d timed; OpenGL hand-off %s;"
                    + " OpenGL done to DirectX 12 draw start %s; DirectX 12 draw %s; OpenGL waited for DirectX 12 in %d frames;"
                    + " clocks matched within %d us", label, frames, count, stat(handOff), stat(toDirectx), stat(draw), openglWaits,
                    maxSpread / 1000));
        }
        count = 0;
        frames = 0;
        openglWaits = 0;
        maxSpread = matchSpread;
        windowStart = now;
    }

    private String stat(long[] nanos) {
        long[] sorted = Arrays.copyOf(nanos, count);
        Arrays.sort(sorted);
        return String.format(Locale.ROOT, "%d us (10%%: %d, 90%%: %d)", sorted[count / 2] / 1000, sorted[count / 10] / 1000,
                sorted[Math.min(count - 1, count * 9 / 10)] / 1000);
    }

    @Override
    public void close() {
        report();
        glDeleteQueries(queries);
    }
}

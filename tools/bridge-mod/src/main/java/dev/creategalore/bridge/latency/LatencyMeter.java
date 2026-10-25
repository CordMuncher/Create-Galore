package dev.creategalore.bridge.latency;

import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33C.glQueryCounter;
import static org.lwjgl.opengl.GL32C.glGetInteger64;

import java.util.Arrays;

/**
 * Measures, on any GPU, how old the newest input is when its frame is done: from the last time the game read input
 * (glfwPollEvents) before using the mouse for a frame, to the moment the GPU finished that frame's OpenGL work (a
 * timestamp query after the last copy, before the frame is handed to the window). The GPU's clock is matched to the
 * CPU's once a second (glGetInteger64(GL_TIMESTAMP) against System.nanoTime). Leaves out what follows on every setup
 * alike: the swap, the display's wait for its refresh and the screen itself.
 */
final class LatencyMeter {
    private static final int RING = 16;
    private static final long CALIBRATE_NANOS = 1_000_000_000L;

    private final int[] queries = new int[RING];
    private final long[] inputNanos = new long[RING];
    private final long[] submitNanos = new long[RING];
    private int head;
    private int pending;
    private long gpuMinusCpu;
    private long calibratedAt;

    private double[] latencies = new double[1024];
    private double[] cpuToGpu = new double[1024];
    private int count;
    private long windowStart = System.nanoTime();

    LatencyMeter() {
        glGenQueries(queries);
    }

    /** At the end of the frame's OpenGL work: {@code input} is when input was last read for it. */
    void frameSubmitted(long input) {
        long now = System.nanoTime();
        if (now - calibratedAt > CALIBRATE_NANOS) {
            calibrate();
        }
        collect(false);
        if (pending == RING) {
            collect(true); // the GPU is a whole ring of frames behind: wait for the oldest
        }
        int slot = (head + pending) % RING;
        glQueryCounter(queries[slot], GL_TIMESTAMP);
        inputNanos[slot] = input;
        submitNanos[slot] = now;
        pending++;
    }

    private void calibrate() {
        long before = System.nanoTime();
        long gpu = glGetInteger64(GL_TIMESTAMP);
        long after = System.nanoTime();
        gpuMinusCpu = gpu - (before + after) / 2;
        calibratedAt = after;
    }

    /** Reads the queries the GPU has finished, oldest first; {@code wait}: also the oldest one even if not yet done. */
    private void collect(boolean wait) {
        while (pending > 0) {
            int query = queries[head];
            if (!wait && glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) == 0) {
                return;
            }
            wait = false;
            long done = glGetQueryObjecti64(query, GL_QUERY_RESULT) - gpuMinusCpu;
            if (inputNanos[head] != 0L) {
                add((done - inputNanos[head]) / 1.0e6, (done - submitNanos[head]) / 1.0e6);
            }
            head = (head + 1) % RING;
            pending--;
        }
    }

    private void add(double latencyMs, double queuedMs) {
        if (count == latencies.length) {
            latencies = Arrays.copyOf(latencies, count * 2);
            cpuToGpu = Arrays.copyOf(cpuToGpu, count * 2);
        }
        latencies[count] = latencyMs;
        cpuToGpu[count] = queuedMs;
        count++;
    }

    /**
     * Since the last call: median and 90th percentile of input to GPU done, median of the frame's end on the CPU to
     * GPU done (how far the GPU runs behind), all ms; the frames counted and the frames a second.
     */
    double[] window() {
        long now = System.nanoTime();
        double seconds = (now - windowStart) / 1.0e9;
        double[] values = Arrays.copyOf(latencies, count);
        double[] behind = Arrays.copyOf(cpuToGpu, count);
        double[] result = {percentile(values, 0.5), percentile(values, 0.9), percentile(behind, 0.5), count,
                seconds > 0 ? count / seconds : 0};
        count = 0;
        windowStart = now;
        return result;
    }

    void close() {
        glDeleteQueries(queries);
    }

    /** The p-th percentile (0..1) by nearest rank, NaN for no values; sorts {@code values}. */
    static double percentile(double[] values, double p) {
        if (values.length == 0) {
            return Double.NaN;
        }
        Arrays.sort(values);
        int index = (int) Math.ceil(p * values.length) - 1;
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}

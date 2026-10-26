package dev.creategalore.bridge.latency;

import java.util.Arrays;

/**
 * Keeps the GPU's queue short without a frame cap, on any GPU. When the GPU is the slower side, the CPU gets ahead
 * and each frame's rendering waits behind the frames before it; the input it was made from ages all that time. Waiting
 * just before rendering instead (then reading input, see {@link FrameLatency}) makes the frame reach the GPU about when
 * the GPU gets to it. Reflex does this for Direct3D games, but its driver can't see OpenGL work.
 *
 * {@link LatencyMeter} tells how far ahead of the GPU each frame came: how long its rendering waited behind the frame
 * before (below 0: the GPU sat idle that long). One frame's longer wait moves every later frame back with it, so the
 * queue is the sum of the waits: the wait
 * is a steady part that learns slowly what the frame rate needs (integral) plus a part that follows the difference to
 * the target (proportional). Waiting more each frame instead overshoots and swings (a simulation of the loop showed
 * it), as the GPU's answer comes a frame or two late.
 *
 * The target is how long a frame may still queue, so the GPU never runs dry when a frame takes a little longer on the
 * CPU. Automatic (the player's Lowest, or pacerTargetUs=auto): three times how much the frames' arrival varies from one
 * to the next, at least 250 us; on the RTX 4080 at about 160 fps (Pacer 2) that took 3 ms more off than Reflex but cost
 * 6% of the frame rate, as the render thread feeds the GPU slower than it draws during parts of each frame (the queue's
 * low end under about 3 to 4 ms costs frames). {@link #keepingFrameRate(boolean)} (the player's On): a share of the
 * frame time, at least 4 ms with Perfect Presentation or 5 ms without, which cost nothing there (+3/0% and +1/-1%) and
 * still took 1.5 to 7 ms off. Fixed: pacerTargetUs (testing).
 */
final class GpuPacer implements LatencyMeter.Listener {
    private static final double PROPORTIONAL = 0.3;
    private static final double INTEGRAL = 0.03;
    private static final long MAX_WAIT = 50_000_000L;
    private static final int HISTORY = 64;
    /** The automatic target: this many times the arrival's frame-to-frame variation, at least MIN_TARGET. */
    private static final double JITTER_MARGIN = 3.0;
    private static final long MIN_TARGET = 250_000L;

    /** The fixed target (with a share: the least), or below 0 for the automatic one. */
    private final long fixedTarget;
    /** Above 0: the target is this share of the frame time, at least fixedTarget. */
    private final double share;
    private long target;
    private long steady;
    private long wait;

    /** How far ahead of the GPU the last frames came, oldest first by ring; for the automatic target. */
    private final long[] aheads = new long[HISTORY];
    private int aheadCount;

    /** The time between the GPU's frame ends: no wait is longer than a frame. */
    private final long[] intervals = new long[HISTORY];
    private int intervalCount;
    private long intervalMedian;
    private int sinceSort;

    private double[] waits = new double[1024];
    private double[] lates = new double[1024];
    private int waitCount;

    /** {@code targetNanos}: how long a frame may still queue on the GPU, or below 0 to set that from the jitter. */
    GpuPacer(long targetNanos) {
        this(targetNanos, 0.0);
    }

    private GpuPacer(long targetNanos, double share) {
        fixedTarget = targetNanos;
        this.share = share;
        target = targetNanos >= 0 ? targetNanos : 2_000_000L; // automatic: careful until there is a measure
    }

    /**
     * The player's On: a frame may queue 65% of the frame time, at least 4 ms, with Perfect Presentation
     * ({@code presentation}); 80%, at least 5 ms, without (its frames queue longer before the GPU gets them).
     */
    static GpuPacer keepingFrameRate(boolean presentation) {
        return presentation ? new GpuPacer(4_000_000L, 0.65) : new GpuPacer(5_000_000L, 0.8);
    }

    /** Just before the frame's rendering: the wait the queue asks for. */
    void beforeRender() {
        long start = System.nanoTime();
        if (wait > 0) {
            long deadline = start + wait;
            PreciseWait.until(deadline);
            record(wait / 1.0e6, (System.nanoTime() - deadline) / 1.0e3);
        } else {
            record(0, 0);
        }
    }

    @Override
    public void frameDone(long queue, long interval) {
        intervals[intervalCount++ % HISTORY] = interval;
        if (++sinceSort >= 8) {
            sinceSort = 0;
            intervalMedian = median(intervals, Math.min(intervalCount, HISTORY));
        }
        long ahead = queue; // below 0: the GPU waited for this frame
        aheads[aheadCount++ % HISTORY] = ahead;
        if (aheadCount >= 16 && sinceSort == 0) {
            if (share > 0.0) {
                target = Math.max(fixedTarget, (long) (share * intervalMedian));
            } else if (fixedTarget < 0) {
                target = Math.max(MIN_TARGET, (long) (JITTER_MARGIN * jitter()));
            }
        }
        long error = ahead - target;
        long limit = Math.min(MAX_WAIT, intervalMedian);
        steady = Math.max(0L, Math.min(limit, steady + (long) (INTEGRAL * error)));
        wait = Math.max(0L, Math.min(limit, steady + (long) (PROPORTIONAL * error)));
    }

    /** The standard deviation of the change in how far ahead the frames came, from one frame to the next, over sqrt 2. */
    private double jitter() {
        int n = Math.min(aheadCount, HISTORY);
        int first = aheadCount - n;
        double sum = 0;
        double squares = 0;
        for (int i = first + 1; i < aheadCount; i++) {
            double d = aheads[i % HISTORY] - aheads[(i - 1) % HISTORY];
            sum += d;
            squares += d * d;
        }
        int m = n - 1;
        double mean = sum / m;
        return Math.sqrt(Math.max(0.0, squares / m - mean * mean) / 2.0);
    }

    /** Since the last call: the median wait (ms) and the median and 90th percentile of how late it ended (us). */
    double[] window() {
        double[] w = Arrays.copyOf(waits, waitCount);
        double[] l = Arrays.copyOf(lates, waitCount);
        waitCount = 0;
        return new double[] {LatencyMeter.percentile(w, 0.5), LatencyMeter.percentile(l, 0.5), LatencyMeter.percentile(l, 0.9)};
    }

    /** The target now (ns): the fixed one, or the automatic one as measured. */
    long target() {
        return target;
    }

    private void record(double waitMs, double lateUs) {
        if (waitCount == LatencyMeter.MAX_WINDOW) {
            waitCount = 0;
        }
        if (waitCount == waits.length) {
            waits = Arrays.copyOf(waits, waitCount * 2);
            lates = Arrays.copyOf(lates, waitCount * 2);
        }
        waits[waitCount] = waitMs;
        lates[waitCount] = lateUs;
        waitCount++;
    }

    private static long median(long[] values, int n) {
        if (n == 0) {
            return 0L;
        }
        long[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        return sorted[n / 2];
    }
}

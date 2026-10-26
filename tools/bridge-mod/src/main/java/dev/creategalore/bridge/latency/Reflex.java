package dev.creategalore.bridge.latency;

import java.util.Arrays;

/**
 * NVIDIA Reflex for {@link FrameLatency}: NVAPI's, on the DirectX device Perfect Presentation shows the game with
 * ({@link NvapiReflex}), or Streamline's while DLSS Frame Generation runs ({@link StreamlineReflex}), which then must be
 * the only one (NVIDIA: frame generation paces with Streamline's Reflex; NVAPI's would fight it).
 */
interface Reflex {
    // NV_LATENCY_MARKER_TYPE (Streamline's PCLMarker numbers them the same)
    int SIMULATION_START = 0;
    int SIMULATION_END = 1;
    int RENDERSUBMIT_START = 2;
    int RENDERSUBMIT_END = 3;
    int PRESENT_START = 4;
    int PRESENT_END = 5;
    int INPUT_SAMPLE = 6;

    /** Low latency mode, boost (GPU clocks held up) and the frame rate cap (0: none); the driver keeps them until changed. */
    void setMode(boolean lowLatency, boolean boost, int minimumIntervalUs);

    /**
     * Where the driver waits so frame {@code frameId} starts as late as it can and still be ready in time: before input
     * is read.
     */
    void sleep(long frameId);

    void marker(int type, long frameId);

    /** True if the driver says low latency mode is on (Streamline: that it can run). */
    boolean lowLatency();

    /** What the driver made of the request, in a few words. */
    String status();

    /**
     * The driver's report of its last 64 frames: median and 90th percentile of input sample to GPU render end and of
     * simulation start to GPU render end, the median GPU busy time and GPU frame time (all ms), the frames counted, and
     * the unit check; null when the driver has no full report yet (it wants about 90 frames of markers).
     */
    double[] latency();

    /**
     * {@link #latency()} from the report's frames, oldest first, each {frame id, input sample, simulation start, GPU
     * render end, GPU active us, GPU frame us}. The report's times are in microseconds (NVIDIA's Reflex SDK);
     * gpuFrameTimeUs is checked against the step between frames' render ends (the last number, about 1 when they agree).
     */
    static double[] summarize(long[][] frames) {
        int count = frames.length;
        double[] inputToGpu = new double[count];
        double[] simToGpu = new double[count];
        double[] gpuActive = new double[count];
        double[] gpuFrame = new double[count];
        double[] endStep = new double[count];
        int n = 0;
        int steps = 0;
        long lastEnd = 0;
        for (long[] frame : frames) {
            long input = frame[1];
            long simStart = frame[2];
            long gpuEnd = frame[3];
            if (frame[0] == 0 || gpuEnd == 0 || simStart == 0) {
                lastEnd = 0;
                continue;
            }
            long frameUs = frame[5];
            if (lastEnd != 0 && frameUs > 0) {
                endStep[steps++] = (double) (gpuEnd - lastEnd) / frameUs;
            }
            lastEnd = gpuEnd;
            inputToGpu[n] = input != 0 ? (gpuEnd - input) / 1000.0 : Double.NaN;
            simToGpu[n] = (gpuEnd - simStart) / 1000.0;
            gpuActive[n] = frame[4] / 1000.0;
            gpuFrame[n] = frameUs / 1000.0;
            n++;
        }
        if (n < count / 2) {
            return null;
        }
        double unitCheck = steps > 0 ? LatencyMeter.percentile(Arrays.copyOf(endStep, steps), 0.5) : Double.NaN;
        double[] input = Arrays.stream(inputToGpu, 0, n).filter(Double::isFinite).toArray();
        return new double[] {
                LatencyMeter.percentile(input, 0.5), LatencyMeter.percentile(input, 0.9),
                LatencyMeter.percentile(Arrays.copyOf(simToGpu, n), 0.5), LatencyMeter.percentile(Arrays.copyOf(simToGpu, n), 0.9),
                LatencyMeter.percentile(Arrays.copyOf(gpuActive, n), 0.5), LatencyMeter.percentile(Arrays.copyOf(gpuFrame, n), 0.5),
                n, unitCheck};
    }
}

package dev.creategalore.bridge.latency;

import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.system.MemoryUtil.memSet;
import static org.lwjgl.system.MemoryUtil.nmemCalloc;

import java.util.Arrays;

import dev.creategalore.bridge.present.Com;
import dev.creategalore.bridge.present.Ffi;

/**
 * NVIDIA Reflex through NVAPI, on the Direct3D 11 device Perfect Presentation shows the game with. The game still
 * renders with OpenGL, which Reflex has no API for; whether pacing the D3D11 device's frames lowers the latency of the
 * OpenGL frames behind them is what this tests (LatencyMeter measures either way).
 *
 * nvapi64.dll exports only nvapi_QueryInterface, which hands out each function by its id. Ids, structures and marker
 * types are from NVIDIA's nvapi_interface.h and nvapi.h (MIT, https://github.com/NVIDIA/nvapi); the structure sizes
 * below are those of the x64 headers (checked by compiling their definitions). A structure's version is its size with
 * the version number in the high 16 bits (MAKE_NVAPI_VERSION).
 */
final class Reflex {
    // nvapi_interface.h
    private static final int ID_INITIALIZE = 0x0150e828;
    private static final int ID_D3D_SET_SLEEP_MODE = 0xac1ca9e0;
    private static final int ID_D3D_SLEEP = 0x852cd1d2;
    private static final int ID_D3D_SET_LATENCY_MARKER = 0xd9984c05;
    private static final int ID_D3D_GET_LATENCY = 0x1a587f9c;
    private static final int ID_D3D_GET_SLEEP_STATUS = 0xaef96ca1;

    // NV_LATENCY_MARKER_TYPE
    static final int SIMULATION_START = 0;
    static final int SIMULATION_END = 1;
    static final int RENDERSUBMIT_START = 2;
    static final int RENDERSUBMIT_END = 3;
    static final int PRESENT_START = 4;
    static final int PRESENT_END = 5;
    static final int INPUT_SAMPLE = 6;

    // NV_SET_SLEEP_MODE_PARAMS_V1: version, bLowLatencyMode @4, bLowLatencyBoost @5, minimumIntervalUs @8, ...
    private static final int SLEEP_MODE_SIZE = 44;
    // NV_GET_SLEEP_STATUS_PARAMS_V1: version, bLowLatencyMode @4, bFsVrr @5, bCplVsyncOn @6, sleepIntervalUs @8, bUseGameSleep @12
    private static final int SLEEP_STATUS_SIZE = 136;
    // NV_LATENCY_MARKER_PARAMS_V1: version, frameID @8, markerType @16
    private static final int MARKER_SIZE = 88;
    // NV_LATENCY_RESULT_PARAMS_V1: version, then 64 frame reports of 240 bytes from @8 (oldest first)
    private static final int LATENCY_SIZE = 15400;
    private static final int REPORTS = 64;
    private static final int REPORT_SIZE = 240;
    private static final int REPORT_FRAME_ID = 0;
    private static final int REPORT_INPUT_SAMPLE = 8;
    private static final int REPORT_SIM_START = 16;
    private static final int REPORT_GPU_RENDER_END = 104;
    private static final int REPORT_GPU_ACTIVE_US = 112;
    private static final int REPORT_GPU_FRAME_US = 116;

    private static final int NVAPI_OK = 0;

    private final long device;
    private final long setSleepMode;
    private final long sleep;
    private final long setLatencyMarker;
    private final long getLatency;
    private final long getSleepStatus;
    private final long sleepParams = nmemCalloc(1, SLEEP_MODE_SIZE);
    private final long statusParams = nmemCalloc(1, SLEEP_STATUS_SIZE);
    private final long markerParams = nmemCalloc(1, MARKER_SIZE);
    private final long latencyParams = nmemCalloc(1, LATENCY_SIZE);
    private boolean markerFailed;

    private Reflex(long device, long queryInterface) {
        this.device = device;
        setSleepMode = function(queryInterface, ID_D3D_SET_SLEEP_MODE, "NvAPI_D3D_SetSleepMode");
        sleep = function(queryInterface, ID_D3D_SLEEP, "NvAPI_D3D_Sleep");
        setLatencyMarker = function(queryInterface, ID_D3D_SET_LATENCY_MARKER, "NvAPI_D3D_SetLatencyMarker");
        getLatency = function(queryInterface, ID_D3D_GET_LATENCY, "NvAPI_D3D_GetLatency");
        getSleepStatus = function(queryInterface, ID_D3D_GET_SLEEP_STATUS, "NvAPI_D3D_GetSleepStatus");
    }

    /** Reflex on {@code device} (an ID3D11Device); throws with the reason when NVAPI or the driver can't. */
    static Reflex start(long device) {
        if (device == 0L) {
            throw new IllegalStateException("no Direct3D 11 device (Perfect Presentation is off or did not start)");
        }
        long queryInterface = Com.function("nvapi64.dll", "nvapi_QueryInterface");
        check("NvAPI_Initialize", (int) Ffi.call(":I", function(queryInterface, ID_INITIALIZE, "NvAPI_Initialize")));
        return new Reflex(device, queryInterface);
    }

    private static long function(long queryInterface, int id, String name) {
        long function = Ffi.call("I:P", queryInterface, id);
        if (function == 0L) {
            throw new IllegalStateException(name + " is not in this driver's NVAPI");
        }
        return function;
    }

    private static void check(String what, int status) {
        if (status != NVAPI_OK) {
            throw new IllegalStateException(what + " failed: NVAPI status " + status + statusName(status));
        }
    }

    private static String statusName(int status) {
        return switch (status) {
            case -1 -> " (error)";
            case -3 -> " (not in this driver)";
            case -5 -> " (invalid argument)";
            case -6 -> " (no NVIDIA GPU driving a display)";
            case -9 -> " (structure version not supported)";
            case -104 -> " (not supported on this GPU)";
            default -> "";
        };
    }

    /** Low latency mode, boost (GPU clocks held up) and the frame rate cap (0: none); the driver keeps them until changed. */
    void setMode(boolean lowLatency, boolean boost, int minimumIntervalUs) {
        memSet(sleepParams, 0, SLEEP_MODE_SIZE);
        memPutInt(sleepParams, SLEEP_MODE_SIZE | 1 << 16);
        memPutByte(sleepParams + 4, (byte) (lowLatency ? 1 : 0));
        memPutByte(sleepParams + 5, (byte) (boost ? 1 : 0));
        memPutInt(sleepParams + 8, minimumIntervalUs);
        check("NvAPI_D3D_SetSleepMode", (int) Ffi.call("PP:I", setSleepMode, device, sleepParams));
    }

    /** Where the driver waits so the frame starts as late as it can and still be ready in time: before input is read. */
    void sleep() {
        check("NvAPI_D3D_Sleep", (int) Ffi.call("P:I", sleep, device));
    }

    void marker(int type, long frameId) {
        memSet(markerParams, 0, MARKER_SIZE);
        memPutInt(markerParams, MARKER_SIZE | 1 << 16);
        memPutLong(markerParams + 8, frameId);
        memPutInt(markerParams + 16, type);
        int status = (int) Ffi.call("PP:I", setLatencyMarker, device, markerParams);
        if (status != NVAPI_OK && !markerFailed) {
            markerFailed = true; // say it once; markers come several times a frame
            throw new IllegalStateException("NvAPI_D3D_SetLatencyMarker failed: NVAPI status " + status + statusName(status));
        }
    }

    /** What the driver made of the request: low latency on, its last sleep, and whether the game's Sleep calls are used. */
    String status() {
        memSet(statusParams, 0, SLEEP_STATUS_SIZE);
        memPutInt(statusParams, SLEEP_STATUS_SIZE | 1 << 16);
        int status = (int) Ffi.call("PP:I", getSleepStatus, device, statusParams);
        if (status != NVAPI_OK) {
            return "sleep status unavailable (NVAPI status " + status + statusName(status) + ")";
        }
        return "driver low latency " + (memGetByte(statusParams + 4) != 0 ? "on" : "off")
                + ", last sleep " + Integer.toUnsignedLong(memGetInt(statusParams + 8)) + " us"
                + ", game Sleep " + (memGetByte(statusParams + 12) != 0 ? "used" : "not used")
                + (memGetByte(statusParams + 5) != 0 ? ", G-SYNC fullscreen" : "")
                + (memGetByte(statusParams + 6) != 0 ? ", control panel forces vsync on" : "");
    }

    /**
     * The driver's report of its last 64 frames: median and 90th percentile of input sample to GPU render end and of
     * simulation start to GPU render end, the median GPU busy time and GPU frame time (all ms), and the frames counted;
     * null when the driver has no full report yet (it wants about 90 frames of markers).
     *
     * The report's times are in microseconds (NVIDIA's Reflex SDK); gpuFrameTimeUs is checked against the step between
     * frames' render ends, and the report is given in raw units with a warning if they disagree.
     */
    double[] latency() {
        memSet(latencyParams, 0, LATENCY_SIZE);
        memPutInt(latencyParams, LATENCY_SIZE | 1 << 16);
        int status = (int) Ffi.call("PP:I", getLatency, device, latencyParams);
        if (status != NVAPI_OK) {
            return null;
        }
        double[] inputToGpu = new double[REPORTS];
        double[] simToGpu = new double[REPORTS];
        double[] gpuActive = new double[REPORTS];
        double[] gpuFrame = new double[REPORTS];
        double[] endStep = new double[REPORTS];
        int n = 0;
        int steps = 0;
        long lastEnd = 0;
        for (int i = 0; i < REPORTS; i++) {
            long report = latencyParams + 8 + (long) i * REPORT_SIZE;
            long gpuEnd = memGetLong(report + REPORT_GPU_RENDER_END);
            long input = memGetLong(report + REPORT_INPUT_SAMPLE);
            long simStart = memGetLong(report + REPORT_SIM_START);
            if (memGetLong(report + REPORT_FRAME_ID) == 0 || gpuEnd == 0 || simStart == 0) {
                lastEnd = 0;
                continue;
            }
            int frameUs = memGetInt(report + REPORT_GPU_FRAME_US);
            if (lastEnd != 0 && frameUs > 0) {
                endStep[steps++] = (double) (gpuEnd - lastEnd) / frameUs;
            }
            lastEnd = gpuEnd;
            inputToGpu[n] = input != 0 ? (gpuEnd - input) / 1000.0 : Double.NaN;
            simToGpu[n] = (gpuEnd - simStart) / 1000.0;
            gpuActive[n] = Integer.toUnsignedLong(memGetInt(report + REPORT_GPU_ACTIVE_US)) / 1000.0;
            gpuFrame[n] = frameUs / 1000.0;
            n++;
        }
        if (n < REPORTS / 2) {
            return null;
        }
        double unitCheck = steps > 0 ? LatencyMeter.percentile(Arrays.copyOf(endStep, steps), 0.5) : Double.NaN;
        return new double[] {
                LatencyMeter.percentile(finite(inputToGpu, n), 0.5), LatencyMeter.percentile(finite(inputToGpu, n), 0.9),
                LatencyMeter.percentile(Arrays.copyOf(simToGpu, n), 0.5), LatencyMeter.percentile(Arrays.copyOf(simToGpu, n), 0.9),
                LatencyMeter.percentile(Arrays.copyOf(gpuActive, n), 0.5), LatencyMeter.percentile(Arrays.copyOf(gpuFrame, n), 0.5),
                n, unitCheck};
    }

    private static double[] finite(double[] values, int n) {
        return Arrays.stream(values, 0, n).filter(Double::isFinite).toArray();
    }
}

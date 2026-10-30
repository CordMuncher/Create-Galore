package dev.creategalore.bridge.latency;

import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.system.MemoryUtil.memSet;
import static org.lwjgl.system.MemoryUtil.nmemCalloc;

import dev.creategalore.bridge.present.Com;
import dev.creategalore.bridge.present.Ffi;

/**
 * NVIDIA Reflex through NVAPI, on the DirectX device (Direct3D 11 or 12) Perfect Presentation shows the game with. The
 * game still renders with OpenGL, which Reflex has no API for; whether pacing that device's frames lowers the latency of the
 * OpenGL frames behind them is what this tests (LatencyMeter measures either way).
 *
 * nvapi64.dll exports only nvapi_QueryInterface, which hands out each function by its id. Ids, structures and marker
 * types are from NVIDIA's nvapi_interface.h and nvapi.h (MIT, https://github.com/NVIDIA/nvapi); the structure sizes
 * below are those of the x64 headers (checked by compiling their definitions). A structure's version is its size with
 * the version number in the high 16 bits (MAKE_NVAPI_VERSION).
 */
final class NvapiReflex implements Reflex {
    // nvapi_interface.h
    private static final int ID_INITIALIZE = 0x0150e828;
    private static final int ID_D3D_SET_SLEEP_MODE = 0xac1ca9e0;
    private static final int ID_D3D_SLEEP = 0x852cd1d2;
    private static final int ID_D3D_SET_LATENCY_MARKER = 0xd9984c05;
    private static final int ID_D3D_GET_LATENCY = 0x1a587f9c;
    private static final int ID_D3D_GET_SLEEP_STATUS = 0xaef96ca1;

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

    private NvapiReflex(long device, long queryInterface) {
        this.device = device;
        setSleepMode = function(queryInterface, ID_D3D_SET_SLEEP_MODE, "NvAPI_D3D_SetSleepMode");
        sleep = function(queryInterface, ID_D3D_SLEEP, "NvAPI_D3D_Sleep");
        setLatencyMarker = function(queryInterface, ID_D3D_SET_LATENCY_MARKER, "NvAPI_D3D_SetLatencyMarker");
        getLatency = function(queryInterface, ID_D3D_GET_LATENCY, "NvAPI_D3D_GetLatency");
        getSleepStatus = function(queryInterface, ID_D3D_GET_SLEEP_STATUS, "NvAPI_D3D_GetSleepStatus");
    }

    /** Reflex on {@code device} (an ID3D11Device or ID3D12Device); throws with the reason when NVAPI or the driver can't. */
    static NvapiReflex start(long device) {
        if (device == 0L) {
            throw new IllegalStateException("no DirectX device (Perfect Presentation is off or did not start)");
        }
        long queryInterface = Com.function("nvapi64.dll", "nvapi_QueryInterface");
        check("NvAPI_Initialize", (int) Ffi.call(":I", function(queryInterface, ID_INITIALIZE, "NvAPI_Initialize")));
        return new NvapiReflex(device, queryInterface);
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

    @Override
    public void setMode(boolean lowLatency, boolean boost, int minimumIntervalUs) {
        memSet(sleepParams, 0, SLEEP_MODE_SIZE);
        memPutInt(sleepParams, SLEEP_MODE_SIZE | 1 << 16);
        memPutByte(sleepParams + 4, (byte) (lowLatency ? 1 : 0));
        memPutByte(sleepParams + 5, (byte) (boost ? 1 : 0));
        memPutInt(sleepParams + 8, minimumIntervalUs);
        check("NvAPI_D3D_SetSleepMode", (int) Ffi.call("PP:I", setSleepMode, device, sleepParams));
    }

    @Override
    public void sleep(long frameId) {
        check("NvAPI_D3D_Sleep", (int) Ffi.call("P:I", sleep, device));
    }

    @Override
    public void marker(int type, long frameId) {
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

    /** True if the driver says low latency mode is on. */
    @Override
    public boolean lowLatency() {
        memSet(statusParams, 0, SLEEP_STATUS_SIZE);
        memPutInt(statusParams, SLEEP_STATUS_SIZE | 1 << 16);
        check("NvAPI_D3D_GetSleepStatus", (int) Ffi.call("PP:I", getSleepStatus, device, statusParams));
        return memGetByte(statusParams + 4) != 0;
    }

    /** What the driver made of the request: low latency on, its last sleep, and whether the game's Sleep calls are used. */
    @Override
    public String status() {
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

    @Override
    public double[] latency() {
        memSet(latencyParams, 0, LATENCY_SIZE);
        memPutInt(latencyParams, LATENCY_SIZE | 1 << 16);
        int status = (int) Ffi.call("PP:I", getLatency, device, latencyParams);
        if (status != NVAPI_OK) {
            return null;
        }
        long[][] frames = new long[REPORTS][];
        for (int i = 0; i < REPORTS; i++) {
            long report = latencyParams + 8 + (long) i * REPORT_SIZE;
            frames[i] = new long[] {memGetLong(report + REPORT_FRAME_ID), memGetLong(report + REPORT_INPUT_SAMPLE),
                    memGetLong(report + REPORT_SIM_START), memGetLong(report + REPORT_GPU_RENDER_END),
                    Integer.toUnsignedLong(memGetInt(report + REPORT_GPU_ACTIVE_US)), memGetInt(report + REPORT_GPU_FRAME_US)};
        }
        return Reflex.summarize(frames);
    }
}

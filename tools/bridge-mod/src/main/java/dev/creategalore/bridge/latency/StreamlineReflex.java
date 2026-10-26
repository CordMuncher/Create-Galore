package dev.creategalore.bridge.latency;

import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.system.MemoryUtil.nmemCalloc;

import dev.creategalore.bridge.present.Ffi;
import dev.creategalore.bridge.present.Presentation;

/**
 * NVIDIA Reflex through Streamline (sl.reflex and sl.pcl), while DLSS Frame Generation runs in Perfect Presentation's
 * DirectX 12 presenter: NVIDIA's frame generation needs it, with its sleep and the PC Latency markers every frame, and
 * NVAPI's Reflex can't run beside it. The calls go through cg_present.dll (cg_sl_reflex_options, cg_sl_reflex_sleep,
 * cg_sl_marker, cg_sl_reflex_state in tools/bridge-mod/native/cg_streamline.cpp), which turns each frame id into
 * Streamline's frame token, the same one frame generation's constants and the present markers use.
 */
final class StreamlineReflex implements Reflex {
    private static final int REPORTS = 64;
    private static final int REPORT_NUMBERS = 16;
    // cg_sl_reflex_state's report numbers (Streamline's ReflexReport order)
    private static final int FRAME_ID = 0;
    private static final int INPUT_SAMPLE_TIME = 1;
    private static final int SIM_START = 2;
    private static final int GPU_RENDER_END = 13;
    private static final int GPU_ACTIVE_US = 14;
    private static final int GPU_FRAME_US = 15;
    private static final int FLAG_LOW_LATENCY_AVAILABLE = 1;
    private static final int FLAG_REPORT_AVAILABLE = 2;

    private final long options;
    private final long sleep;
    private final long marker;
    private final long state;
    private final long error;
    private final long stateBuffer = nmemCalloc(8, 1 + REPORTS * REPORT_NUMBERS);
    private boolean markerFailed;

    private StreamlineReflex() {
        options = Presentation.presenterSymbol("cg_sl_reflex_options");
        sleep = Presentation.presenterSymbol("cg_sl_reflex_sleep");
        marker = Presentation.presenterSymbol("cg_sl_marker");
        state = Presentation.presenterSymbol("cg_sl_reflex_state");
        error = Presentation.presenterSymbol("cg_present_error");
    }

    /** Streamline's Reflex; throws unless frame generation runs (with Streamline) in Perfect Presentation's presenter. */
    static StreamlineReflex start() {
        if (!Presentation.frameGeneration()) {
            throw new IllegalStateException("frame generation isn't running, so Streamline isn't either");
        }
        return new StreamlineReflex();
    }

    private String errorText() {
        return memUTF8(Ffi.call(":P", error));
    }

    @Override
    public void setMode(boolean lowLatency, boolean boost, int minimumIntervalUs) {
        int mode = !lowLatency ? 0 : boost ? 2 : 1;
        if (Ffi.call("II:I", options, mode, minimumIntervalUs) == 0) {
            throw new IllegalStateException("slReflexSetOptions: " + errorText());
        }
    }

    @Override
    public void sleep(long frameId) {
        if (Ffi.call("I:I", sleep, (int) frameId) == 0) {
            throw new IllegalStateException("slReflexSleep: " + errorText());
        }
    }

    /** INPUT_SAMPLE has no PC Latency marker any more (Streamline 2.14 deprecated it); the others go through. */
    @Override
    public void marker(int type, long frameId) {
        if (type == INPUT_SAMPLE) {
            return;
        }
        if (Ffi.call("II:I", marker, type, (int) frameId) == 0 && !markerFailed) {
            markerFailed = true; // say it once; markers come several times a frame
            throw new IllegalStateException("slPCLSetMarker: " + errorText());
        }
    }

    private int readState() {
        return (int) Ffi.call("PI:I", state, stateBuffer, 1 + REPORTS * REPORT_NUMBERS);
    }

    @Override
    public boolean lowLatency() {
        if (readState() < 1) {
            throw new IllegalStateException("slReflexGetState: " + errorText());
        }
        return (memGetLong(stateBuffer) & FLAG_LOW_LATENCY_AVAILABLE) != 0;
    }

    @Override
    public String status() {
        if (readState() < 1) {
            return "Streamline's state unavailable (" + errorText() + ")";
        }
        long flags = memGetLong(stateBuffer);
        return "Streamline, low latency " + ((flags & FLAG_LOW_LATENCY_AVAILABLE) != 0 ? "available" : "not available")
                + ", latency report " + ((flags & FLAG_REPORT_AVAILABLE) != 0 ? "available" : "not available");
    }

    @Override
    public double[] latency() {
        int written = readState();
        if (written < 1 + REPORTS * REPORT_NUMBERS || (memGetLong(stateBuffer) & FLAG_REPORT_AVAILABLE) == 0) {
            return null;
        }
        long[][] frames = new long[REPORTS][];
        for (int i = 0; i < REPORTS; i++) {
            long report = stateBuffer + 8L * (1 + (long) i * REPORT_NUMBERS);
            frames[i] = new long[] {memGetLong(report + 8L * FRAME_ID), memGetLong(report + 8L * INPUT_SAMPLE_TIME),
                    memGetLong(report + 8L * SIM_START), memGetLong(report + 8L * GPU_RENDER_END),
                    memGetLong(report + 8L * GPU_ACTIVE_US), memGetLong(report + 8L * GPU_FRAME_US)};
        }
        return Reflex.summarize(frames);
    }
}

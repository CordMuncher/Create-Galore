package dev.creategalore.bridge.latency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.creategalore.bridge.DisplaySettings;
import dev.creategalore.bridge.present.Presentation;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Input latency tests (testing only, off unless the display settings ask). Three things, each switched on its own:
 *
 * reflex (off, markers, on, boost): NVIDIA Reflex on Perfect Presentation's D3D11 device ({@link Reflex}). Each frame
 * starts with Reflex's sleep and then reads input, so the driver can start it as late as still keeps the GPU busy;
 * markers tell the driver where each frame's game work, rendering and present are (markers alone: its latency report
 * without changing anything). On and boost also take over a frame rate cap from the game's own limiter, which waits
 * after the frame instead of before input.
 *
 * latePoll: read input again just before the mouse turns the camera, after any wait for the GPU or the swap chain
 * (Sodium's render-ahead limit at the start of the frame, Perfect Presentation's swap chain, the game's frame cap).
 * Minecraft reads input only while presenting the frame before, so whatever it waits for after that ages the input.
 *
 * latencyLog: logs {@link LatencyMeter}'s numbers (and Reflex's report) every 10 seconds. The test kit measures through
 * {@link #measure(boolean)} and {@link #window()} instead.
 */
public final class FrameLatency {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final long LOG_NANOS = 10_000_000_000L;

    enum ReflexMode { OFF, MARKERS, ON, BOOST }

    private static int generation = -1;
    private static ReflexMode reflexMode = ReflexMode.OFF;
    private static boolean latePoll;
    private static boolean logging;
    private static boolean measuring;

    private static Reflex reflex;
    private static String reflexState = "off";
    private static boolean reflexTried;
    private static boolean reflexSleeping;
    /** What the driver was last told: low latency, boost, frame interval (us); null: nothing yet. */
    private static int[] reflexApplied;

    private static long frameId;
    private static long lastPoll;
    private static long input;
    private static LatencyMeter meter;
    private static long lastLog;

    private FrameLatency() {}

    // --- Minecraft.runTick ---

    /** First thing in a frame: Reflex's sleep, then input. */
    public static void frameStart() {
        if (generation != DisplaySettings.generation()) {
            readSettings();
        }
        frameId++;
        reflexSleeping = false;
        if (reflexMode == ReflexMode.OFF) {
            return;
        }
        if (reflex == null && !reflexTried) {
            startReflex();
        }
        if (reflex == null) {
            return;
        }
        try {
            applyReflexMode();
            reflex.sleep();
            reflexSleeping = true;
            if (reflexMode != ReflexMode.MARKERS) {
                pollEvents(); // input after the sleep, as NVIDIA asks (markers alone change nothing)
            }
            reflex.marker(Reflex.SIMULATION_START, frameId);
        } catch (RuntimeException e) {
            reflexFailed(e);
        }
    }

    /**
     * Just before the frame's rendering starts (after Perfect Presentation's wait for its swap chain), where the mouse
     * is about to turn the camera. Both mixins call this; the one that runs is Perfect Presentation's while it shows
     * the game ({@code fromPresentation}), else the game's own, so this always comes after that wait.
     */
    public static void beforeRender(boolean fromPresentation) {
        if (fromPresentation != Presentation.active()) {
            return;
        }
        if (latePoll) {
            pollEvents();
        }
        input = lastPoll;
        if (reflexSleeping) {
            try {
                reflex.marker(Reflex.INPUT_SAMPLE, frameId);
                reflex.marker(Reflex.SIMULATION_END, frameId);
                reflex.marker(Reflex.RENDERSUBMIT_START, frameId);
            } catch (RuntimeException e) {
                reflexFailed(e);
            }
        }
    }

    /** The frame's OpenGL work is all sent (just before it goes to the window). */
    public static void frameSubmitted() {
        if (reflexSleeping) {
            try {
                reflex.marker(Reflex.RENDERSUBMIT_END, frameId);
            } catch (RuntimeException e) {
                reflexFailed(e);
            }
        }
        if (!measuring && !logging) {
            if (meter != null) {
                meter.close();
                meter = null;
            }
            return;
        }
        if (meter == null) {
            meter = new LatencyMeter();
            lastLog = System.nanoTime();
        }
        meter.frameSubmitted(input);
        if (logging && !measuring && System.nanoTime() - lastLog > LOG_NANOS) {
            lastLog = System.nanoTime();
            LOG.info("Latency: {}", describe(window()));
        }
    }

    /**
     * True when Reflex keeps this frame rate cap ({@code fps}, what the game's limiter was about to wait for), so the
     * game's own limiter (which waits after the frame) stays out. Menus without a world cap at 60 by the game's own.
     */
    public static boolean reflexLimitsFrameRate(int fps) {
        return reflexSleeping && reflexApplied != null && reflexApplied[2] > 0 && fps == frameLimit();
    }

    // --- RenderSystem ---

    /** The game read input (RenderSystem.pollEvents, while presenting). */
    public static void polled() {
        lastPoll = System.nanoTime();
    }

    /** Around Perfect Presentation's Present. */
    public static void presentStart() {
        presentMarker(Reflex.PRESENT_START);
    }

    public static void presentEnd() {
        presentMarker(Reflex.PRESENT_END);
    }

    private static void presentMarker(int type) {
        if (reflexSleeping) {
            try {
                reflex.marker(type, frameId);
            } catch (RuntimeException e) {
                reflexFailed(e);
            }
        }
    }

    // --- the test kit ---

    /** Measure from now on (or stop); the kit reads the numbers with {@link #window()}. */
    public static void measure(boolean on) {
        measuring = on;
        if (on && meter != null) {
            meter.window(); // start a fresh window
        }
    }

    /**
     * The numbers since the last call: inputToGpuMs and inputToGpuP90Ms (input read to the GPU done with the frame),
     * gpuBehindMs (the frame's end on the CPU to the GPU done), frames, fps, reflex (what Reflex did) and, when Reflex
     * sent markers, the driver's report of its last 64 frames (driver...).
     */
    public static Map<String, Object> window() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (meter != null) {
            double[] w = meter.window();
            result.put("inputToGpuMs", round(w[0]));
            result.put("inputToGpuP90Ms", round(w[1]));
            result.put("gpuBehindMs", round(w[2]));
            result.put("frames", w[3]);
            result.put("fps", round(w[4]));
        }
        result.put("reflex", reflexState());
        result.put("latePoll", latePoll);
        if (reflexSleeping && reflex != null) {
            try {
                double[] report = reflex.latency();
                if (report != null) {
                    result.put("driverInputToGpuMs", round(report[0]));
                    result.put("driverInputToGpuP90Ms", round(report[1]));
                    result.put("driverSimToGpuMs", round(report[2]));
                    result.put("driverSimToGpuP90Ms", round(report[3]));
                    result.put("driverGpuActiveMs", round(report[4]));
                    result.put("driverGpuFrameMs", round(report[5]));
                    result.put("driverFrames", report[6]);
                    // About 1 when the report's times are microseconds, as NVIDIA's SDK says.
                    result.put("driverUnitCheck", round(report[7]));
                }
            } catch (RuntimeException e) {
                result.put("driverReport", "failed: " + e.getMessage());
            }
        }
        return result;
    }

    /** Reflex's state in a line: its mode and what the driver made of it, or why it isn't running. */
    public static String reflexState() {
        if (reflexMode == ReflexMode.OFF) {
            return "off";
        }
        if (reflex == null) {
            return reflexState;
        }
        try {
            return reflexMode.name().toLowerCase(Locale.ROOT) + " (" + reflex.status() + ")";
        } catch (RuntimeException e) {
            return reflexMode.name().toLowerCase(Locale.ROOT) + " (status failed: " + e.getMessage() + ")";
        }
    }

    // ---

    private static void readSettings() {
        generation = DisplaySettings.generation();
        String mode = DisplaySettings.getString(DisplaySettings.REFLEX, "off").toLowerCase(Locale.ROOT);
        ReflexMode wanted = switch (mode) {
            case "markers" -> ReflexMode.MARKERS;
            case "on", "true" -> ReflexMode.ON;
            case "boost" -> ReflexMode.BOOST;
            default -> ReflexMode.OFF;
        };
        if (wanted != reflexMode && reflex != null && wanted == ReflexMode.OFF) {
            try {
                reflex.setMode(false, false, 0);
                reflexApplied = null;
            } catch (RuntimeException e) {
                reflexFailed(e);
            }
        }
        if (wanted != reflexMode) {
            LOG.info("Latency test: Reflex {}", wanted.name().toLowerCase(Locale.ROOT));
        }
        reflexMode = wanted;
        boolean poll = DisplaySettings.get(DisplaySettings.LATE_POLL);
        if (poll != latePoll) {
            LOG.info("Latency test: input read again just before rendering {}", poll ? "on" : "off");
        }
        latePoll = poll;
        logging = DisplaySettings.get(DisplaySettings.LATENCY_LOG);
    }

    private static void startReflex() {
        reflexTried = true;
        try {
            reflex = Reflex.start(Presentation.d3dDevice());
            LOG.info("Latency test: NVIDIA Reflex started on Perfect Presentation's Direct3D 11 device");
        } catch (RuntimeException | LinkageError e) {
            reflex = null;
            reflexState = "unavailable: " + e.getMessage();
            LOG.warn("Latency test: Reflex {}", reflexState);
        }
    }

    private static void applyReflexMode() {
        boolean lowLatency = reflexMode.compareTo(ReflexMode.ON) >= 0;
        boolean boost = reflexMode == ReflexMode.BOOST;
        int limit = lowLatency ? frameLimit() : 0;
        int interval = limit > 0 ? 1_000_000 / limit : 0;
        int[] wanted = {lowLatency ? 1 : 0, boost ? 1 : 0, interval};
        if (reflexApplied == null || !java.util.Arrays.equals(reflexApplied, wanted)) {
            reflex.setMode(lowLatency, boost, interval);
            reflexApplied = wanted;
            LOG.info("Latency test: Reflex low latency {}, boost {}, frame cap {}; {}", lowLatency ? "on" : "off", boost ? "on" : "off",
                    limit > 0 ? limit + " fps (Reflex's)" : "none", reflex.status());
        }
    }

    /** The Max Framerate setting (0: Unlimited, the slider's top). */
    private static int frameLimit() {
        int limit = Minecraft.getInstance().getWindow().getFramerateLimit();
        return limit < 260 ? limit : 0;
    }

    private static void reflexFailed(RuntimeException e) {
        LOG.error("Latency test: Reflex stopped after an error, the frames go on without it", e);
        reflexState = "stopped after an error: " + e.getMessage();
        reflex = null;
        reflexSleeping = false;
    }

    private static void pollEvents() {
        GLFW.glfwPollEvents();
        polled();
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) / 100.0 : Double.NaN;
    }

    private static String describe(Map<String, Object> w) {
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                "input to GPU done median %s ms (90%%: %s), GPU %s ms behind the CPU, %s fps; Reflex %s; late input read %s",
                w.get("inputToGpuMs"), w.get("inputToGpuP90Ms"), w.get("gpuBehindMs"), w.get("fps"), w.get("reflex"),
                latePoll ? "on" : "off"));
        if (w.containsKey("driverSimToGpuMs")) {
            line.append(String.format(Locale.ROOT, "; driver: input to GPU end %s ms (90%%: %s), frame start to GPU end %s ms, GPU busy %s of %s ms",
                    w.get("driverInputToGpuMs"), w.get("driverInputToGpuP90Ms"), w.get("driverSimToGpuMs"), w.get("driverGpuActiveMs"),
                    w.get("driverGpuFrameMs")));
        }
        return line.toString();
    }
}

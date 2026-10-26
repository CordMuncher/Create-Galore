package dev.creategalore.bridge.latency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.creategalore.bridge.DisplaySettings;
import dev.creategalore.bridge.dlss.Dlss;
import dev.creategalore.bridge.dlss.StartGuard;
import dev.creategalore.bridge.present.Presentation;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA Reflex (the display settings' NVIDIA Reflex: off, on or boost) and the input latency tests. Three switches:
 *
 * reflex (off, on, boost; markers for testing): NVIDIA Reflex on Perfect Presentation's DirectX device ({@link Reflex}),
 * so it needs Perfect Presentation (the settings screen turns that on with it). Each frame starts with Reflex's sleep
 * and then reads input, so the driver can start it as late as still keeps the GPU busy; markers tell the driver where
 * each frame's game work, rendering and present are (markers alone: its latency report without changing anything). On
 * and boost also take over a frame rate cap from the game's own limiter, which waits after the frame instead of before
 * input. A start that took the game down keeps Reflex off until Try again ({@link StartGuard}). While DLSS Frame
 * Generation runs (testing, see FrameGen) Reflex goes through Streamline ({@link StreamlineReflex}) and is at least on,
 * as NVIDIA's frame generation needs; the GPU pacer stays as set beside it.
 *
 * latePoll: read input again just before the mouse turns the camera, after any wait for the GPU or the swap chain
 * (Sodium's render-ahead limit at the start of the frame, Perfect Presentation's swap chain, the game's frame cap).
 * Minecraft reads input only while presenting the frame before, so whatever it waits for after that ages the input.
 * On with Reflex on or boost, or the GPU pacer, unless the setting says otherwise (testing only, not written to the file).
 *
 * gpuPacing (off, on, lowest): the {@link GpuPacer} waits just before rendering, before the late read, so the frame's
 * rendering reaches the GPU about when the GPU gets to it instead of queueing behind earlier frames. For uncapped play,
 * where Reflex's driver can't see the OpenGL queue. On keeps a queue that costs no frames (a share of the frame time,
 * at least 4 or 5 ms), lowest the shortest the frames' timing allows (about 6% fewer frames on the RTX 4080).
 * gpuPacer and pacerTargetUs (testing) override it: true or false, and how long a frame may still queue (auto: from how
 * much the frames vary).
 *
 * The Max Framerate cap waits at the start of the frame, before input is read, instead of in the game's limiter after
 * the frame (Reflex's own cap does the same while Reflex runs). Any GPU: in the Pacer 1 run it matched Reflex's cap
 * (8.8 ms input to GPU done at 60 fps with Perfect Presentation, the game's limiter 16.7). frameCapAtStart=false
 * (testing) leaves the cap to the game's limiter.
 *
 * latencyLog (testing): logs {@link LatencyMeter}'s numbers (and Reflex's report) every 10 seconds. The test kit
 * measures through {@link #measure(boolean)} and {@link #window()} instead.
 */
public final class FrameLatency {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final long LOG_NANOS = 10_000_000_000L;

    enum ReflexMode { OFF, MARKERS, ON, BOOST }

    /**
     * What Reflex does, for the settings screen: a few words beside the setting, the whole story when the mouse is on
     * them (or null), and whether it is wanted but doesn't run.
     */
    public record Status(String text, String detail, boolean problem) {}

    private static int generation = -1;
    private static ReflexMode reflexMode = ReflexMode.OFF;
    private static boolean latePoll;
    private static boolean logging;
    private static boolean measuring;

    private static Reflex reflex;
    private static StartGuard guard;
    private static String reflexState = "off";
    /** Why Reflex isn't running, in a few words for the settings screen. */
    private static String reflexShort = "";
    private static boolean reflexTried;
    private static boolean reflexSleeping;
    /** What the driver was last told: low latency, boost, frame interval (us); null: nothing yet. */
    private static int[] reflexApplied;

    private static boolean pacing;
    /** How long a frame may queue (ns), below 0 automatic; with pacerKeepsRate the player's On instead. */
    private static long pacerTarget;
    private static boolean pacerKeepsRate;
    private static GpuPacer pacer;
    private static boolean capAtStart;
    /** The frame cap the wait at the frame's start keeps (0: none, the game's limiter or Reflex's has it). */
    private static int startCap;
    private static long nextFrame;

    private static long frameId;
    private static long lastPoll;
    private static long input;
    private static LatencyMeter meter;
    private static long lastLog;

    private FrameLatency() {}

    // --- Minecraft.runTick ---

    /** First thing in a frame: Reflex's sleep or the frame cap's wait, then input. */
    public static void frameStart() {
        if (generation != DisplaySettings.generation()) {
            readSettings();
        }
        frameId++;
        reflexSleeping = false;
        boolean metering = measuring || logging || pacing;
        if (metering && meter == null) {
            pacer = !pacing ? null : pacerKeepsRate ? GpuPacer.keepingFrameRate(Presentation.active(), Presentation.directX12()) : new GpuPacer(pacerTarget);
            meter = new LatencyMeter(pacer);
            lastLog = System.nanoTime();
        } else if (!metering && meter != null) {
            closeMeter();
        }
        if (reflexMode != ReflexMode.OFF) {
            reflexFrameStart();
        }
        if (startCap > 0 && startCap != frameLimit()) {
            startCap = 0; // the cap changed or went (the game's limiter isn't called at all without one)
        }
        if (startCap > 0 && !reflexCaps()) {
            capWait();
        }
    }

    /** Waits for the frame cap's next frame, then reads input (what the game's limiter does after the frame instead). */
    private static void capWait() {
        long interval = 1_000_000_000L / startCap;
        long now = System.nanoTime();
        if (now - nextFrame > interval || nextFrame - now > interval) {
            nextFrame = now; // more than a frame behind, or the cap changed: the cadence starts over
        }
        PreciseWait.until(nextFrame);
        nextFrame += interval;
        pollEvents();
    }

    private static void reflexFrameStart() {
        if (reflex == null && !reflexTried) {
            startReflex();
        }
        if (reflex == null) {
            return;
        }
        try {
            applyReflexMode();
            reflex.sleep(frameId);
            reflexSleeping = true;
            if (reflexMode != ReflexMode.MARKERS) {
                pollEvents(); // input after the sleep, as NVIDIA asks (markers alone change nothing)
            }
            reflex.marker(Reflex.SIMULATION_START, frameId);
            guard().ran();
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
        if (pacer != null) {
            pacer.beforeRender();
        }
        if (latePoll) {
            pollEvents();
        }
        input = lastPoll;
        if (meter != null) {
            meter.renderStart();
        }
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
        if (meter == null) {
            return;
        }
        meter.frameSubmitted(input);
        if (logging && !measuring && System.nanoTime() - lastLog > LOG_NANOS) {
            lastLog = System.nanoTime();
            LOG.info("Latency: {}", describe(window()));
        }
    }

    /**
     * True when Reflex or the wait at the frame's start keeps this frame rate cap ({@code fps}, what the game's limiter
     * was about to wait for), so the game's own limiter (which waits after the frame) stays out. Menus without a world
     * cap at 60 by the game's own.
     */
    public static boolean limitsFrameRate(int fps) {
        boolean ours = fps > 0 && fps == frameLimit();
        if (ours && reflexCaps()) {
            startCap = 0;
            return true;
        }
        startCap = ours && capAtStart ? fps : 0;
        return startCap > 0;
    }

    /** True while Reflex's sleep keeps the frame cap. */
    private static boolean reflexCaps() {
        return reflexSleeping && reflexApplied != null && reflexApplied[2] > 0;
    }

    // --- RenderSystem ---

    /** The frame's id, counted from the start: the one Reflex's markers carry (frame generation's frame index). */
    public static long frameId() {
        return frameId;
    }

    /**
     * Frame generation: a present that came without a frame of Minecraft's of its own gets the next frame number, so
     * Streamline never sees two presents under one (its markers then are only the present's).
     */
    public static long extraFrameId() {
        return ++frameId;
    }

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

    // --- the settings screen ---

    /** What Reflex does now, for the settings screen; null while the setting is off. */
    public static Status status() {
        if (reflexMode == ReflexMode.OFF) {
            return null;
        }
        if (reflex == null) {
            return reflexTried ? new Status(reflexShort, "Reflex can't run: " + reflexState + ".", true) : new Status("Starting.", null, false);
        }
        if (reflexMode == ReflexMode.MARKERS) {
            return new Status("Markers only (testing).", "The driver gets Reflex's markers for its latency report; nothing else changes.", false);
        }
        try {
            String driver = reflex.status();
            return reflex.lowLatency()
                    ? new Status("Running: the driver confirms it.", "The NVIDIA driver runs low latency mode"
                            + (reflexMode == ReflexMode.BOOST ? " with boost" : "") + " (" + driver + ").", false)
                    : new Status("Running, but the driver says off.", "The NVIDIA driver reports low latency mode off (" + driver
                            + "). The NVIDIA Control Panel's Low Latency Mode or a driver update may help.", true);
        } catch (RuntimeException e) {
            return new Status("Running.", "The driver's status is unavailable: " + e.getMessage(), false);
        }
    }

    /** True if Reflex took the game down while starting and stays off until {@link #tryAgain()}. */
    public static boolean crashed() {
        return guard().crashed();
    }

    /** Clears the crash mark: Reflex starts again with the next frame, if it is on. */
    public static void tryAgain() {
        guard().clear();
        if (reflex == null) {
            reflexTried = false;
        }
    }

    /** The game is closing: not a crash, even within Reflex's first frames. */
    public static void shutdown() {
        if (guard != null) {
            guard.stopped();
        }
    }

    // --- the test kit ---

    /** Measure from now on (or stop); the kit reads the numbers with {@link #window()}. */
    public static void measure(boolean on) {
        measuring = on;
        if (on && meter != null) {
            meter.window(); // start a fresh window
            if (pacer != null) {
                pacer.window();
            }
        }
    }

    /**
     * The numbers since the last call: inputToGpuMs and inputToGpuP90Ms (input read to the GPU done with the frame),
     * gpuBehindMs (the frame's end on the CPU to the GPU done), frames, fps, gpuQueueMs and gpuQueueP10Ms (median and
     * 10th percentile of the rendering's start waiting behind the frame before on the GPU; below 0 the GPU waited), reflex
     * (what Reflex did), the switches, with the GPU pacer its wait (pacerWaitMs) and how late that ended (pacerLateUs,
     * pacerLateP90Us), and, when Reflex sent markers, the driver's report of its last 64 frames (driver...).
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
            result.put("gpuQueueMs", round(w[5]));
            result.put("gpuQueueP10Ms", round(w[6]));
        }
        result.put("reflex", reflexState());
        result.put("latePoll", latePoll);
        result.put("gpuPacer", pacing);
        result.put("pacerKeepsRate", pacing && pacerKeepsRate);
        result.put("frameCapAtStart", capAtStart);
        if (pacer != null) {
            double[] p = pacer.window();
            result.put("pacerTargetUs", pacer.target() / 1000);
            result.put("pacerWaitMs", round(p[0]));
            result.put("pacerLateUs", round(p[1]));
            result.put("pacerLateP90Us", round(p[2]));
        }
        if (reflexSleeping && reflex != null) {
            try {
                double[] report = reflex.latency();
                if (report != null) {
                    // Streamline's report has no input time (its input marker is gone), so no number there.
                    if (Double.isFinite(report[0])) {
                        result.put("driverInputToGpuMs", round(report[0]));
                        result.put("driverInputToGpuP90Ms", round(report[1]));
                    }
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
        if (Presentation.frameGeneration() && wanted.compareTo(ReflexMode.ON) < 0) {
            if (reflexMode != ReflexMode.ON) {
                LOG.info("Reflex: on, though set to {}: frame generation needs it", wanted.name().toLowerCase(Locale.ROOT));
            }
            wanted = ReflexMode.ON;
        }
        if (wanted != reflexMode && reflex != null && wanted == ReflexMode.OFF) {
            try {
                reflex.setMode(false, false, 0);
                reflexApplied = null;
                guard().stopped();
            } catch (RuntimeException e) {
                reflexFailed(e);
            }
        }
        if (wanted != reflexMode) {
            LOG.info("Reflex: {}", wanted.name().toLowerCase(Locale.ROOT));
        }
        reflexMode = wanted;
        String pacingSetting = DisplaySettings.getString(DisplaySettings.GPU_PACING, "off").toLowerCase(Locale.ROOT);
        boolean lowest = pacingSetting.equals("lowest");
        boolean on = lowest || pacingSetting.equals("on");
        String paceTest = DisplaySettings.getString(DisplaySettings.GPU_PACER, "");
        boolean pace = paceTest.isEmpty() ? on : Boolean.parseBoolean(paceTest);
        String targetSetting = DisplaySettings.getString(DisplaySettings.PACER_TARGET_US, "");
        long target = -1L; // automatic
        boolean keepsRate = targetSetting.isEmpty() && on && !lowest;
        if (!targetSetting.isEmpty() && !targetSetting.equalsIgnoreCase("auto")) {
            try {
                target = Math.max(0L, Long.parseLong(targetSetting)) * 1000L;
            } catch (NumberFormatException e) {
                LOG.warn("Latency: {}={} is neither auto nor microseconds, using auto", DisplaySettings.PACER_TARGET_US, targetSetting);
            }
        }
        if (pace != pacing || pace && (target != pacerTarget || keepsRate != pacerKeepsRate)) {
            closeMeter(); // made again with (or without) the pacer at the next frame
            LOG.info("Latency: GPU pacer {}", !pace ? "off"
                    : keepsRate ? "on, frames may queue on the GPU a share of the frame time that keeps the frame rate"
                    : target < 0 ? "on, frames may queue on the GPU as much as they vary"
                    : "on, frames may queue " + target / 1000 + " us on the GPU");
        }
        pacing = pace;
        pacerTarget = target;
        pacerKeepsRate = keepsRate;
        boolean cap = !DisplaySettings.getString(DisplaySettings.FRAME_CAP_AT_START, "true").equalsIgnoreCase("false");
        if (cap != capAtStart) {
            LOG.info("Latency: the frame cap waits {}", cap ? "at the start of the frame, before input" : "in the game's limiter");
        }
        capAtStart = cap;
        if (!cap) {
            startCap = 0;
        }
        String pollSetting = DisplaySettings.getString(DisplaySettings.LATE_POLL, "");
        boolean poll = pollSetting.isEmpty() ? wanted.compareTo(ReflexMode.ON) >= 0 || pace : Boolean.parseBoolean(pollSetting);
        if (poll != latePoll) {
            LOG.info("Latency: input read again just before rendering {}", poll ? "on" : "off");
        }
        latePoll = poll;
        logging = DisplaySettings.get(DisplaySettings.LATENCY_LOG);
    }

    private static void startReflex() {
        reflexTried = true;
        if (!Presentation.active()) {
            boolean windows = System.getProperty("os.name", "").startsWith("Windows");
            reflexState = !windows ? "it needs Windows and an NVIDIA graphics card"
                    : Presentation.requested() ? "Perfect Presentation, which it needs, could not start (the log says why)"
                    : "it needs Perfect Presentation, which starts after a restart";
            reflexShort = !windows ? "Needs Windows and an NVIDIA card." : Presentation.requested() ? "Can't run: Perfect Presentation failed."
                    : "Runs after you restart the game.";
            LOG.warn("Reflex: can't run, {}", reflexState);
            return;
        }
        if (guard().crashed()) {
            reflexState = "the game closed while it was starting last time, so it stays off until you pick Try again";
            reflexShort = "Off after a crash: Try again.";
            return;
        }
        guard().starting();
        try {
            if (Presentation.frameGeneration()) {
                reflex = StreamlineReflex.start();
                LOG.info("Reflex: started through Streamline, as frame generation needs ({})", reflex.status());
            } else {
                reflex = NvapiReflex.start(Presentation.d3dDevice());
                LOG.info("Reflex: started on Perfect Presentation's {} device (NVIDIA NVAPI)", Presentation.api());
            }
        } catch (RuntimeException | LinkageError e) {
            guard().stopped();
            reflex = null;
            String reason = String.valueOf(e.getMessage());
            boolean noNvapi = reason.startsWith("Could not load nvapi64.dll");
            reflexState = noNvapi ? "it needs an NVIDIA graphics card and driver (no nvapi64.dll)" : reason;
            reflexShort = noNvapi ? "Can't run: needs an NVIDIA card." : "Can't run (point here for why).";
            LOG.warn("Reflex: can't run, {}", reason);
        }
    }

    /** Made once the game folder is known: the start marks go next to DLSS's. */
    private static StartGuard guard() {
        if (guard == null) {
            guard = new StartGuard(FMLPaths.GAMEDIR.get().resolve(Dlss.FOLDER), "Reflex", "Reflex");
        }
        return guard;
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
            LOG.info("Reflex: low latency {}, boost {}, frame cap {}; {}", lowLatency ? "on" : "off", boost ? "on" : "off",
                    limit > 0 ? limit + " fps (Reflex's)" : "none", reflex.status());
        }
    }

    /** The Max Framerate setting (0: Unlimited, the slider's top). */
    private static int frameLimit() {
        int limit = Minecraft.getInstance().getWindow().getFramerateLimit();
        return limit < 260 ? limit : 0;
    }

    private static void reflexFailed(RuntimeException e) {
        LOG.error("Reflex: stopped after an error, the frames go on without it", e);
        reflexState = "it stopped after an error: " + e.getMessage();
        reflexShort = "Stopped after an error.";
        reflex = null;
        reflexSleeping = false;
        guard().stopped();
    }

    private static void closeMeter() {
        if (meter != null) {
            meter.close();
            meter = null;
        }
        pacer = null;
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
        if (w.containsKey("pacerWaitMs")) {
            line.append(String.format(Locale.ROOT, "; GPU pacer waits %s ms (%s us late), the GPU's queue %s ms",
                    w.get("pacerWaitMs"), w.get("pacerLateUs"), w.get("gpuQueueMs")));
        }
        if (w.containsKey("driverSimToGpuMs")) {
            line.append("; driver: ");
            if (w.containsKey("driverInputToGpuMs")) {
                line.append(String.format(Locale.ROOT, "input to GPU end %s ms (90%%: %s), ", w.get("driverInputToGpuMs"),
                        w.get("driverInputToGpuP90Ms")));
            }
            line.append(String.format(Locale.ROOT, "frame start to GPU end %s ms, GPU busy %s of %s ms", w.get("driverSimToGpuMs"),
                    w.get("driverGpuActiveMs"), w.get("driverGpuFrameMs")));
        }
        return line.toString();
    }
}

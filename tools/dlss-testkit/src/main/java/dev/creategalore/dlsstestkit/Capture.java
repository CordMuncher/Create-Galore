package dev.creategalore.dlsstestkit;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One test run, driven frame by frame from the render loop so every mode sees exactly the same frames: for each mode,
 * set the Bridge's DLAA settings, reload shaders, warm up, then at each viewpoint settle and play each camera path,
 * saving screenshots at the path's capture frames. The pack's clock and frame counter count these frames (see the
 * mixins), so its waving leaves, exposure and jitter repeat too. A manifest.json describes what was saved.
 */
public final class Capture {
    private static final Logger LOG = LoggerFactory.getLogger(TestKit.MOD_ID);
    private static final String CONFIG = "create_galore_dlss_testkit.json";
    private static final String BRIDGE_CONFIG = "create_galore_bridge-display.properties";
    /** The DLAA pack's options (Iris's file beside it): the modes set its upscaling. */
    private static final String PACK_OPTIONS = "Photon v1.3a + DLAA.txt";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Frames between putting the scenes up and the first mode, for the server to place them and the client to get them. */
    private static final int SCENE_BUILD_FRAMES = 40;
    /** Frames between one run of -D...runs and the next. */
    private static final int NEXT_RUN_FRAMES = 40;

    /** config/create_galore_dlss_testkit.json. */
    static final class Settings {
        /** The first mode warms up longer: chunks finish building and Distant Horizons fills in before any capture. */
        int firstWarmupFrames = 900;
        int warmupFrames = 240;
        int settleFrames = 120;
        long time = 3000;
        boolean freezeTicks = true;
        /** Ticks a second while the camera is at the moving scene (20: real time; 1 suits software rendering). */
        float sceneTickRate = 20;
        /**
         * The pack's clock per frame (waving leaves, water). 1/60 looks like a normal game; the residual check wants
         * more, since leaves that move a fraction of a pixel a frame leave too little to measure.
         */
        float packSecondsPerFrame = 1.0f / 60.0f;
        /**
         * Also save the Bridge's exact inputs for each captured frame (MODE/raw/*.npy, for checks), and in the modes that
         * run DLSS or FSR its output too.
         */
        boolean dumpRaw = false;
        /** Save the screenshots at all. Off for measuring frame times (profiling), as saving one holds up its frame. */
        boolean screenshots = true;
        /** The moving scene's ship: degrees it turns and blocks it sways along x per server tick (at most, it eases). */
        float shipTurnDegreesPerTick = 0.75f;
        float shipSwayPerTick = 0.03f;
        /**
         * In the moving scene, show the hand (the HUD shows with it) holding a sword and swing it, to check its motion; in
         * the effects scene, show it holding the sword still, in front of the particles.
         */
        boolean showHand = false;
        List<Mode> modes = new ArrayList<>(List.of(
                new Mode("photon-taa", false, "default", null, null, null, null, null),
                new Mode("dlaa-default", true, "default", null, null, null, null, null),
                // DLAA with only the camera's motion: what the motion vectors of things that move by themselves add.
                new Mode("dlaa-camera", true, "default", null, null, null, false, null),
                new Mode("dlaa-K", true, "K", null, null, null, null, null),
                new Mode("dlaa-L", true, "L", null, null, null, null, null),
                new Mode("dlaa-M", true, "M", null, null, null, null, null),
                // Checks of the jitter's direction: with a wrong sign a still picture shimmers and blurs.
                new Mode("sign-flip-x", true, "default", null, "-1,1", null, null, null),
                new Mode("sign-flip-y", true, "default", null, "1,-1", null, null, null),
                new Mode("sign-flip-xy", true, "default", null, "-1,-1", null, null, null),
                // What DLSS gets, drawn instead of DLSS's result (any GPU).
                new Mode("debug-motion", true, "default", "motion", null, null, null, null),
                new Mode("debug-depth", true, "default", "depth", null, null, null, null),
                new Mode("debug-color", true, "default", "color", null, null, null, null),
                // How far each frame is from the last one moved along the motion vectors (black: the motion is right), with
                // everything's own motion and with the camera's only: what the moving things' motion vectors fix.
                new Mode("debug-residual", true, "default", "residual", null, null, null, null),
                new Mode("debug-residual-camera", true, "default", "residual", null, null, false, null),
                // AMD FSR 3.1 in DLSS's place (OpenGL, any GPU), its jitter checks and its own debug view.
                new Mode("fsr3", true, "default", null, null, "fsr3", null, null),
                new Mode("fsr3-flip-x", true, "default", null, "-1,1", "fsr3", null, null),
                new Mode("fsr3-flip-y", true, "default", null, "1,-1", "fsr3", null, null),
                new Mode("fsr3-flip-xy", true, "default", null, "-1,-1", "fsr3", null, null),
                new Mode("fsr3-debug", true, "default", "fsr3", null, "fsr3", null, null),
                // FSR without the masks that tell it where to trust this frame over its history (particles, rain, the
                // portal, fire and lava), and those masks themselves (red: reactive, green: composition; any GPU).
                new Mode("fsr3-nomask", true, "default", null, null, "fsr3", null, false),
                new Mode("debug-mask", true, "default", "mask", null, null, null, null)));
        {
            // Upscaling: at each standard render scale Photon's own TAAU against DLSS with K (the default), M and L
            // (made for low render resolutions) and FSR 3.1, all at the same render size.
            String[][] scales = {{"q", "0.67"}, {"bal", "0.58"}, {"perf", "0.50"}, {"ultra", "0.33"}};
            for (String[] scale : scales) {
                float s = Float.parseFloat(scale[1]);
                modes.add(new Mode(scale[0] + "-taau", false, "default", null, null, null, null, null, s));
                modes.add(new Mode(scale[0] + "-dlss-K", true, "K", null, null, null, null, null, s));
                modes.add(new Mode(scale[0] + "-dlss-M", true, "M", null, null, null, null, null, s));
                modes.add(new Mode(scale[0] + "-dlss-L", true, "L", null, null, null, null, null, s));
                modes.add(new Mode(scale[0] + "-fsr3", true, "default", null, null, "fsr3", null, null, s));
            }
            // NVIDIA's full mip bias (one more mip level of detail) against the render scale's own.
            modes.add(new Mode("perf-dlss-K-bias", true, "K", null, null, null, null, null, 0.5f, -1.0f));
            modes.add(new Mode("perf-fsr3-bias", true, "default", null, null, "fsr3", null, null, 0.5f, -1.0f));
            // What DLSS gets at a render scale (any GPU): the corner of the pack's buffers, stretched over the window.
            modes.add(new Mode("perf-debug-color", true, "default", "color", null, null, null, null, 0.5f));
            modes.add(new Mode("perf-debug-motion", true, "default", "motion", null, null, null, null, 0.5f));
        }
        // The DLAA test world (seed 20261024, scene built on the lakeshore at x 1023..1040, z 160..176).
        List<Viewpoint> viewpoints = new ArrayList<>(List.of(
                new Viewpoint("shore", 1029.5, 64.0, 160.5, 0.0f, 8.0f, null),
                new Viewpoint("glass", 1029.5, 64.0, 168.5, 0.0f, -14.0f, null),
                new Viewpoint("canopy", 1024.5, 88.0, 205.5, 180.0f, 22.0f, null),
                // Looking out over the lake at things that move by themselves (see MovingScene).
                new Viewpoint("moving", 1029.5, 66.0, 162.5, 180.0f, 14.0f, MovingScene.NAME),
                // Particles, fire, lava, a nether portal and rain (see EffectsScene). It rains only here, but the pack's
                // wet look fades slowly, so this one is best run on its own (viewpoints=effects).
                new Viewpoint("effects", 1006.5, 68.0, 158.5, 180.0f, 14.0f, EffectsScene.NAME)));
        List<String> paths = new ArrayList<>(List.of("still", "flick", "pan", "strafe"));
        /**
         * Named runs for -Dcreate_galore_dlss_testkit.runs=A+B, which plays them one after another in one game. Upscaling 1
         * (PC-RUNBOOK.md): F1 and F2 measure frame rates (no screenshots), U1 to U7 save the pictures.
         */
        List<Run> runs = new ArrayList<>(List.of(
                new Run("F1", "photon-taa+dlaa-default+fsr3+perf-taau+perf-dlss-K+perf-dlss-M+perf-dlss-L+perf-fsr3"
                        + "+ultra-taau+ultra-dlss-K+ultra-dlss-M+ultra-dlss-L+ultra-fsr3", "shore+canopy", null, null, false, null),
                new Run("F2", "photon-taa+dlaa-default+fsr3+q-taau+q-dlss-K+q-fsr3+bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L"
                        + "+bal-fsr3", "shore+canopy", null, null, false, null),
                new Run("U1", "perf-taau+perf-dlss-K+perf-dlss-M+perf-dlss-L+perf-fsr3+dlaa-default", null, null, null, null, null),
                new Run("U2", "ultra-taau+ultra-dlss-K+ultra-dlss-M+ultra-dlss-L+ultra-fsr3", null, null, null, null, null),
                new Run("U3", "bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L+bal-fsr3", null, null, null, null, null),
                new Run("U4", "q-taau+q-dlss-K+q-fsr3", null, null, null, null, null),
                new Run("U5", "perf-taau+perf-dlss-K+perf-dlss-M+perf-fsr3", "moving", "brief", null, null, true),
                new Run("U6", "perf-dlss-K+perf-dlss-K-bias+perf-fsr3+perf-fsr3-bias", null, null, true, null, null),
                new Run("U7", "perf-debug-color+perf-debug-motion", null, null, true, null, null)));
    }

    /**
     * A run: modes, viewpoints and paths as the -D properties write them (a+b; null: all), quick as -D...quick, and
     * screenshots and showHand in place of the config's for this run (null: the config's).
     */
    record Run(String name, String modes, String viewpoints, String paths, Boolean quick, Boolean screenshots, Boolean showHand) {}

    /**
     * debugView (color, motion, depth or residual) shows that instead of DLSS, on any GPU (fsr3: FSR's own view);
     * jitterSign ("x,y", default "1,1") flips the jitter the Bridge hands to DLSS; engine is dlss (null) or fsr3;
     * objectMotion false leaves only the camera's motion in the motion vectors (null: on); reactiveMask false hands FSR
     * no masks (null: on; DLSS never gets them); renderScale (null: full resolution) turns on Photon's TAAU at that
     * render scale, so DLSS or FSR upscale (or with dlaa false Photon's TAAU itself); mipBias (null: 0) is added to the
     * textures' mip bias while DLSS or FSR runs (NVIDIA's guide asks for -1 more than the render scale's own).
     */
    record Mode(String name, boolean dlaa, String preset, String debugView, String jitterSign, String engine, Boolean objectMotion,
                Boolean reactiveMask, Float renderScale, Float mipBias) {
        Mode(String name, boolean dlaa, String preset, String debugView, String jitterSign, String engine, Boolean objectMotion,
             Boolean reactiveMask) {
            this(name, dlaa, preset, debugView, jitterSign, engine, objectMotion, reactiveMask, null, null);
        }

        Mode(String name, boolean dlaa, String preset, String debugView, String jitterSign, String engine, Boolean objectMotion,
             Boolean reactiveMask, Float renderScale) {
            this(name, dlaa, preset, debugView, jitterSign, engine, objectMotion, reactiveMask, renderScale, null);
        }
    }

    /** scene: built there at run time (MovingScene.NAME), else null. */
    record Viewpoint(String name, double x, double y, double z, float yaw, float pitch, String scene) {}

    /** One stretch of frames: the camera at each local frame, and the frames to save. */
    private record Segment(String label, Viewpoint viewpoint, String path, int length, int[] captures, Runnable onStart, Mode mode) {}

    private static boolean running;
    private static boolean autorun;
    private static int frame;
    private static List<Segment> plan = List.of();
    private static int segment;
    private static int local;
    private static String pendingShot;
    private static Path runDir;
    private static String runId;
    private static Settings settings;
    private static JsonArray shots;
    private static JsonArray timeline;
    private static JsonObject openSegment;
    private static Map<String, JsonObject> modeInfo;
    private static Mode currentMode;
    private static long lastNanos;
    private static double frameNanosSum;
    private static int frameNanosCount;
    private static boolean savedHideGui;
    private static boolean savedBob;
    private static boolean savedPauseOnLostFocus;
    private static int savedFov;
    private static double savedFovEffect;
    private static CameraType savedCamera;
    /** The Bridge's display settings before the run (null: no file), put back when it ends. */
    private static String savedBridgeConfig;
    /** The DLAA pack's options before the run (null: no file), put back when it ends. */
    private static String savedPackOptions;
    private static int autorunWait;
    /** The autorun's runs still to come, in this same game. */
    private static final java.util.ArrayDeque<Run> queued = new java.util.ArrayDeque<>();
    private static boolean queuedRuns;
    /** -D...byEye: the mode to switch to once the moving scene is up after the runs (null: none pending). */
    private static String byEyeMode;
    private static int byEyeWait;
    private static String activeScene;

    private Capture() {}

    public static boolean running() {
        return running;
    }

    /** Frames since the current mode started: the pack's frameCounter during a run. */
    public static int frame() {
        return frame;
    }

    /** The pack's clock step per frame during a run. */
    public static float packSecondsPerFrame() {
        Settings current = settings;
        return current != null && current.packSecondsPerFrame > 0 ? current.packSecondsPerFrame : 1.0f / 60.0f;
    }

    static void armAutorun() {
        autorun = true;
        autorunWait = 0;
    }

    static void log(String message) {
        LOG.info("DLAA test: {}", message);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("DLAA test: " + message), false);
        }
    }

    /** Starts a run of the modes in modeFilter (null: all) and the -D properties' viewpoints and paths; null if it started, else why not. */
    static String start(String modeFilter, boolean fromAutorun) {
        return start(propertyRun(modeFilter));
    }

    private static Run propertyRun(String modeFilter) {
        return new Run(null, modeFilter, System.getProperty(TestKit.MOD_ID + ".viewpoints"),
                System.getProperty(TestKit.MOD_ID + ".paths"), Boolean.getBoolean(TestKit.MOD_ID + ".quick"), null, null);
    }

    private static String start(Run run) {
        String modeFilter = run.modes();
        Minecraft mc = Minecraft.getInstance();
        if (running) {
            return "a run is already going";
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            return "open the DLAA test world in singleplayer first";
        }
        try {
            settings = loadSettings();
        } catch (IOException | RuntimeException e) {
            return "could not read config/" + CONFIG + ": " + e;
        }
        if (run.screenshots() != null) {
            settings.screenshots = run.screenshots();
        }
        if (run.showHand() != null) {
            settings.showHand = run.showHand();
        }
        List<Mode> modes = new ArrayList<>(settings.modes);
        if (modeFilter != null && !modeFilter.isBlank()) {
            List<String> wanted = List.of(modeFilter.trim().split("\\s*[,;+ ]\\s*"));
            modes.removeIf(m -> !wanted.contains(m.name()));
            if (modes.isEmpty()) {
                return "no mode called " + wanted + " in config/" + CONFIG;
            }
        }
        List<Viewpoint> viewpoints = settings.viewpoints;
        String viewpointFilter = run.viewpoints();
        if (viewpointFilter != null && !viewpointFilter.isBlank()) {
            List<String> wanted = List.of(viewpointFilter.trim().split("\\s*[,;+ ]\\s*"));
            viewpoints = viewpoints.stream().filter(v -> wanted.contains(v.name())).toList();
        }
        List<String> paths = settings.paths;
        String pathFilter = run.paths();
        if (pathFilter != null && !pathFilter.isBlank()) {
            paths = List.of(pathFilter.trim().split("\\s*[,;+ ]\\s*"));
        }
        if (Boolean.TRUE.equals(run.quick())) {
            viewpoints = viewpoints.subList(0, 1);
            paths = List.of("still", "flick");
        }
        if (viewpoints.isEmpty()) {
            return "no viewpoints (or none of " + viewpointFilter + ") in config/" + CONFIG;
        }

        List<Segment> segments = new ArrayList<>();
        // The scenes go up before the first mode switches the shaders, which rebuilds every chunk's mesh: so every mode
        // sees them whole from its first frame (a scene built just after the switch once lost a section's update in
        // the rebuild, and the first mode showed lake where its platform was).
        List<String> scenes = viewpoints.stream().map(Viewpoint::scene).filter(java.util.Objects::nonNull).distinct().toList();
        if (!scenes.isEmpty()) {
            segments.add(new Segment("build scenes", viewpoints.get(0), null, SCENE_BUILD_FRAMES, new int[0],
                    () -> scenes.forEach(Capture::buildScene), modes.get(0)));
        }
        for (Mode mode : modes) {
            int warmup = mode == modes.get(0) ? Math.max(settings.firstWarmupFrames, settings.warmupFrames) : settings.warmupFrames;
            segments.add(new Segment("mode " + mode.name(), viewpoints.get(0), null, warmup, new int[0], () -> applyMode(mode), mode));
            for (Viewpoint viewpoint : viewpoints) {
                segments.add(new Segment("settle " + viewpoint.name(), viewpoint, null, settings.settleFrames, new int[0], null, mode));
                for (String path : paths) {
                    Paths.Path p = Paths.get(path);
                    if (p == null) {
                        return "unknown path " + path + " (" + Paths.NAMES + ")";
                    }
                    segments.add(new Segment(viewpoint.name() + "-" + path, viewpoint, path, p.length(), p.captures(), null, mode));
                }
            }
        }
        runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + (run.name() != null ? "-" + run.name() : "");
        runDir = mc.gameDirectory.toPath().resolve("screenshots").resolve("dlaa-test").resolve(runId);
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            return "could not make " + runDir + ": " + e;
        }
        shots = new JsonArray();
        timeline = new JsonArray();
        openSegment = null;
        modeInfo = new LinkedHashMap<>();
        plan = segments;
        segment = -1;
        local = 0;
        frame = 0;
        frameNanosSum = 0;
        frameNanosCount = 0;
        lastNanos = 0;
        activeScene = null;

        try {
            Path bridgeFile = FMLPaths.CONFIGDIR.get().resolve(BRIDGE_CONFIG);
            savedBridgeConfig = Files.exists(bridgeFile) ? Files.readString(bridgeFile) : null;
            Path packFile = Iris.getShaderpacksDirectory().resolve(PACK_OPTIONS);
            savedPackOptions = Files.exists(packFile) ? Files.readString(packFile) : null;
        } catch (IOException e) {
            return "could not read config/" + BRIDGE_CONFIG + ": " + e;
        }
        savedHideGui = mc.options.hideGui;
        savedBob = mc.options.bobView().get();
        savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
        savedFov = mc.options.fov().get();
        savedFovEffect = mc.options.fovEffectScale().get();
        savedCamera = mc.options.getCameraType();
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.pauseOnLostFocus = false; // the pause menu would cover the shots if the window lost focus
        mc.options.fov().set(70);
        mc.options.fovEffectScale().set(0.0);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        server("gamemode creative @a");
        server("time set " + settings.time);
        server("weather clear");
        server("gamerule doDaylightCycle false");
        server("gamerule doWeatherCycle false");
        if (settings.freezeTicks) {
            server("tick freeze");
        }
        running = true;
        log("run " + runId + (queued.isEmpty() ? "" : " (" + queued.size() + " more after it)") + ": " + modes.size() + " modes, " + viewpoints.size() + " viewpoints, paths " + paths
                + ", " + segments.stream().mapToInt(Segment::length).sum() + " frames");
        return null;
    }

    /** /dlaatest scene build [moving|effects] | remove: a test scene outside a run (remove takes both away). */
    static String scene(boolean build, String which) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) {
            return "open the DLAA test world in singleplayer first";
        }
        if (running) {
            return "a run is going";
        }
        if (!build) {
            MovingScene.remove();
            EffectsScene.remove();
            return "removed the test scenes";
        }
        if (EffectsScene.NAME.equals(which)) {
            EffectsScene.show();
            return "the effects scene is over the lake west of the moving one, and it rains; /dlaatest scene remove takes it away";
        }
        try {
            MovingScene.show(loadSettings());
        } catch (IOException | RuntimeException e) {
            return "could not read config/" + CONFIG + ": " + e;
        }
        return "the moving scene is over the lake (stand on the ship to ride it); /dlaatest scene remove takes it away";
    }

    /** /dlaatest mode NAME: switches to that mode's anti-aliasing settings until the next switch (for looking by eye). */
    static String useMode(String name) {
        if (running) {
            return "a run is going";
        }
        try {
            settings = loadSettings();
        } catch (IOException | RuntimeException e) {
            return "could not read config/" + CONFIG + ": " + e;
        }
        for (Mode mode : settings.modes) {
            if (mode.name().equals(name)) {
                return writeMode(mode) ? "now " + name + " (the shaders reloaded)" : "could not switch to " + name;
            }
        }
        return "no mode called " + name + " in config/" + CONFIG;
    }

    static void stop(String why) {
        if (!running) {
            return;
        }
        running = false;
        markSegment(null, System.nanoTime());
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = savedHideGui;
        mc.options.bobView().set(savedBob);
        mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        mc.options.fov().set(savedFov);
        mc.options.fovEffectScale().set(savedFovEffect);
        mc.options.setCameraType(savedCamera);
        if (settings != null) {
            leaveScene();
            MovingScene.remove();
            EffectsScene.remove();
        }
        if (settings != null && settings.freezeTicks) {
            server("tick unfreeze");
        }
        restoreBridgeConfig();
        writeManifest(why);
        log(why + "; screenshots in " + runDir);
        if (autorun && "run finished".equals(why) && !queued.isEmpty()) {
            // The next run starts in a moment, in this same game: no restart between runs.
            autorunWait = 200 - NEXT_RUN_FRAMES;
            return;
        }
        String byEye = System.getProperty(TestKit.MOD_ID + ".byEye");
        if (autorun && byEye != null && !byEye.isBlank() && mc.player != null) {
            // Stay in this game for looking by eye: no restart after the runs.
            autorun = false;
            queued.clear();
            startByEye(byEye.trim());
            return;
        }
        if (autorun) {
            autorun = false;
            queued.clear();
            // Give the last screenshots a moment to reach the disk.
            Util.ioPool().execute(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                mc.execute(mc::stop);
            });
        }
    }

    // The modes rewrote the Bridge's display settings; the game goes back to the player's own.
    private static void restoreBridgeConfig() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(BRIDGE_CONFIG);
        try {
            if (savedBridgeConfig != null) {
                Files.writeString(file, savedBridgeConfig);
            } else {
                Files.deleteIfExists(file);
            }
            Path packFile = Iris.getShaderpacksDirectory().resolve(PACK_OPTIONS);
            if (savedPackOptions != null) {
                Files.writeString(packFile, savedPackOptions);
            } else {
                Files.deleteIfExists(packFile);
            }
            if (!autorun) {
                Iris.reload();
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA test: could not put back config/{}: {}", BRIDGE_CONFIG, e.toString());
        }
    }

    static void beforeFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!running) {
            if (byEyeMode != null && mc.player != null && ++byEyeWait == NEXT_RUN_FRAMES) {
                String mode = byEyeMode;
                byEyeMode = null;
                log(useMode(mode) + "; by eye now: /dlaatest mode NAME switches, /dlaatest scene remove takes the scene away");
            }
            // Autorun: start once the world has been showing for a while (chunks built).
            if (autorun && mc.player != null && mc.level != null && mc.screen == null && ++autorunWait == 200) {
                if (!queuedRuns) {
                    queuedRuns = true;
                    long hold = Long.getLong(TestKit.MOD_ID + ".hold", 0);
                    if (hold > 0) {
                        hold(mc, hold);
                        return;
                    }
                    String error = queueRuns();
                    if (error != null) {
                        log("autorun: " + error);
                        mc.stop();
                        return;
                    }
                }
                while (!queued.isEmpty()) {
                    Run run = queued.poll();
                    String error = start(run);
                    if (error == null) {
                        return;
                    }
                    log("autorun: " + (run.name() != null ? run.name() + ": " : "") + error);
                }
                mc.stop();
            }
            return;
        }
        if (mc.player == null || mc.level == null) {
            stop("left the world");
            return;
        }
        if (mc.screen != null) {
            mc.setScreen(null); // a menu would pause the world and hide the frame
        }
        long now = System.nanoTime();
        Segment current = segment >= 0 ? plan.get(segment) : null;
        if (current != null && current.path() != null && "still".equals(current.path()) && lastNanos != 0) {
            frameNanosSum += now - lastNanos;
            frameNanosCount++;
        }
        lastNanos = now;

        if (segment < 0 || ++local >= current.length()) {
            if (current != null && current.path() != null && "still".equals(current.path())) {
                recordFrameTime(current);
            }
            segment++;
            local = 0;
            if (segment >= plan.size()) {
                markSegment(null, now);
                stop("run finished");
                return;
            }
            current = plan.get(segment);
            markSegment(current, now);
            if (current.onStart() != null) {
                current.onStart().run();
            }
            if (!running) {
                return;
            }
            enterScene(current.viewpoint().scene());
        }
        frame++;

        Paths.Pose pose = current.path() != null ? Paths.get(current.path()).at(local) : Paths.HOLD;
        place(mc.player, current.viewpoint(), pose);
        pendingShot = null;
        for (int c : current.captures()) {
            if (c == local && settings.screenshots) {
                pendingShot = current.label() + "-f" + String.format(Locale.ROOT, "%03d", local);
            }
        }
        if (pendingShot != null && settings.dumpRaw && current.mode().dlaa()) {
            requestDump(runDir.resolve(current.mode().name()).resolve("raw"), pendingShot);
        }
    }

    static void afterFrame(RenderFrameEvent.Post event) {
        if (!running || pendingShot == null) {
            return;
        }
        Segment current = plan.get(segment);
        String file = current.mode().name() + "/" + pendingShot + ".png";
        pendingShot = null;
        Path target = runDir.resolve(file);
        NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                image.writeToFile(target);
            } catch (IOException e) {
                LOG.warn("DLAA test: could not save {}", target, e);
            } finally {
                image.close();
            }
        });
        JsonObject shot = new JsonObject();
        shot.addProperty("mode", current.mode().name());
        shot.addProperty("viewpoint", current.viewpoint().name());
        shot.addProperty("path", current.path());
        shot.addProperty("frame", local);
        shot.addProperty("file", file);
        shots.add(shot);
    }

    private static void buildScene(String scene) {
        if (MovingScene.NAME.equals(scene)) {
            MovingScene.build();
        } else if (EffectsScene.NAME.equals(scene)) {
            EffectsScene.build();
        }
    }

    private static void enterScene(String scene) {
        if (java.util.Objects.equals(scene, activeScene)) {
            return;
        }
        leaveScene();
        activeScene = scene;
        if (MovingScene.NAME.equals(scene)) {
            MovingScene.enter(settings);
        } else if (EffectsScene.NAME.equals(scene)) {
            EffectsScene.enter(settings);
        }
    }

    private static void leaveScene() {
        if (MovingScene.NAME.equals(activeScene)) {
            MovingScene.leave(settings);
        } else if (EffectsScene.NAME.equals(activeScene)) {
            EffectsScene.leave(settings);
        }
        activeScene = null;
    }

    /** Pins the camera: same place and turn for the whole frame, no interpolation from the last tick, no fall. */
    private static void place(LocalPlayer player, Viewpoint viewpoint, Paths.Pose pose) {
        double yawRad = Math.toRadians(viewpoint.yaw());
        // Right of the view direction (Minecraft yaw 0 looks towards +z).
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double x = viewpoint.x() + rightX * pose.right();
        double y = viewpoint.y() + pose.up();
        double z = viewpoint.z() + rightZ * pose.right();
        float yaw = viewpoint.yaw() + pose.yaw();
        float pitch = viewpoint.pitch() + pose.pitch();
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(x, y, z);
        player.xo = player.xOld = x;
        player.yo = player.yOld = y;
        player.zo = player.zOld = z;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.yHeadRot = player.yHeadRotO = yaw;
        player.yBodyRot = player.yBodyRotO = yaw;
    }

    private static void applyMode(Mode mode) {
        currentMode = mode;
        frame = 0;
        if (!writeMode(mode)) {
            stop("could not switch to mode " + mode.name());
            return;
        }
        JsonObject info = new JsonObject();
        info.addProperty("name", mode.name());
        info.addProperty("dlaa", mode.dlaa());
        info.addProperty("preset", mode.preset());
        info.addProperty("debugView", mode.debugView());
        info.addProperty("jitterSign", mode.jitterSign());
        info.addProperty("engine", mode.engine() != null ? mode.engine() : "dlss");
        info.addProperty("objectMotion", !Boolean.FALSE.equals(mode.objectMotion()));
        info.addProperty("reactiveMask", !Boolean.FALSE.equals(mode.reactiveMask()));
        info.addProperty("renderScale", mode.renderScale() != null ? mode.renderScale() : 1.0f);
        info.addProperty("mipBias", mode.mipBias() != null ? mode.mipBias() : 0.0f);
        info.add("frameMs", new JsonObject());
        modeInfo.put(mode.name(), info);
        log("mode " + mode.name());
    }

    /** Writes the mode's settings into the Bridge's display settings and reloads the shaders; false if that failed. */
    private static boolean writeMode(Mode mode) {
        Path file = FMLPaths.CONFIGDIR.get().resolve(BRIDGE_CONFIG);
        Properties props = new Properties();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    props.load(r);
                }
            }
            props.setProperty("dlaa", Boolean.toString(mode.dlaa()));
            props.setProperty("dlaaPreset", mode.preset() != null ? mode.preset() : "default");
            if (mode.debugView() != null && !mode.debugView().isBlank()) {
                props.setProperty("dlaaDebugView", mode.debugView());
            } else {
                props.remove("dlaaDebugView");
            }
            if (mode.jitterSign() != null && !mode.jitterSign().isBlank()) {
                props.setProperty("dlaaJitterSign", mode.jitterSign());
            } else {
                props.remove("dlaaJitterSign");
            }
            if (mode.engine() != null && !mode.engine().isBlank()) {
                props.setProperty("dlaaEngine", mode.engine());
            } else {
                props.remove("dlaaEngine");
            }
            if (Boolean.FALSE.equals(mode.objectMotion())) {
                props.setProperty("dlaaObjectMotion", "false");
            } else {
                props.remove("dlaaObjectMotion");
            }
            if (Boolean.FALSE.equals(mode.reactiveMask())) {
                props.setProperty("dlaaReactiveMask", "false");
            } else {
                props.remove("dlaaReactiveMask");
            }
            if (mode.mipBias() != null && mode.mipBias() != 0.0f) {
                props.setProperty("dlaaMipBias", Float.toString(mode.mipBias()));
            } else {
                props.remove("dlaaMipBias");
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                props.store(w, "Create Galore Bridge display settings (last written by the DLAA test kit, mode " + mode.name() + ")");
            }
            // The render scale is the pack's own option: Iris applies queued options on the reload and saves them.
            Float scale = mode.renderScale();
            Iris.getShaderPackOptionQueue().put("TAAU", Boolean.toString(scale != null && scale < 1.0f));
            if (scale != null && scale < 1.0f) {
                Iris.getShaderPackOptionQueue().put("TAAU_RENDER_SCALE", String.format(Locale.ROOT, "%.2f", scale));
            }
            Iris.reload();
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA test: could not switch to mode {}: {}", mode.name(), e.toString());
            return false;
        }
    }

    /** Average frame time over the still path, and whether the Bridge's DLAA really ran in this mode. */
    private static void recordFrameTime(Segment still) {
        JsonObject info = modeInfo.get(still.mode().name());
        if (info == null) {
            return;
        }
        if (frameNanosCount > 0) {
            info.getAsJsonObject("frameMs").addProperty(still.viewpoint().name(), Math.round(frameNanosSum / frameNanosCount / 1.0e4) / 100.0);
        }
        info.addProperty("dlaaActive", bridgeDlaaActive());
        frameNanosSum = 0;
        frameNanosCount = 0;
    }

    private static void requestDump(Path dir, String name) {
        try {
            Class.forName("dev.creategalore.bridge.dlss.Dlss").getMethod("dumpNextFrame", String.class, String.class)
                    .invoke(null, dir.toString(), name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("DLAA test: the Bridge cannot save its inputs: {}", e.toString());
        }
    }

    private static boolean bridgeDlaaActive() {
        try {
            return (boolean) Class.forName("dev.creategalore.bridge.dlss.Dlss").getMethod("shaderActive").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * -Dcreate_galore_dlss_testkit.byEye=MODE: after the runs the game stays open for looking by eye. The moving scene
     * goes up at the game's normal speed, the player stands at its viewpoint (flying, so not in the lake), and a moment
     * later the shaders switch to that mode (after the scene, so the switch's rebuild of the chunks can't lose a part).
     */
    private static void startByEye(String mode) {
        log(scene(true, null));
        Viewpoint at = settings.viewpoints.stream().filter(v -> MovingScene.NAME.equals(v.scene())).findFirst()
                .orElse(settings.viewpoints.get(0));
        server(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", at.x(), at.y(), at.z(), at.yaw(), at.pitch()));
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setDeltaMovement(Vec3.ZERO);
            }));
        }
        byEyeMode = mode;
        byEyeWait = 0;
    }

    /** The autorun's runs: those named in -D...runs=A+B (the config's runs), in that order, else one from the -D properties. */
    private static String queueRuns() {
        String names = System.getProperty(TestKit.MOD_ID + ".runs");
        if (names == null || names.isBlank()) {
            queued.add(propertyRun(System.getProperty(TestKit.MOD_ID + ".modes")));
            return null;
        }
        List<Run> known;
        try {
            known = loadSettings().runs;
        } catch (IOException | RuntimeException e) {
            return "could not read config/" + CONFIG + ": " + e;
        }
        for (String name : names.trim().split("\\s*[,;+ ]\\s*")) {
            Run run = known.stream().filter(r -> name.equals(r.name())).findFirst().orElse(null);
            if (run == null) {
                queued.clear();
                return "no run called " + name + " in config/" + CONFIG;
            }
            queued.add(run);
        }
        return null;
    }

    /**
     * -Dcreate_galore_dlss_testkit.hold=SECONDS: stands at the first viewpoint that long, then closes the game, so chunks
     * and Distant Horizons' far terrain are made once before any run.
     */
    private static void hold(Minecraft mc, long seconds) {
        Viewpoint first;
        try {
            first = loadSettings().viewpoints.get(0);
        } catch (IOException | RuntimeException e) {
            log("autorun: could not read config/" + CONFIG + ": " + e);
            mc.stop();
            return;
        }
        server("gamemode creative @a");
        server(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", first.x(), first.y(), first.z(), first.yaw(), first.pitch()));
        // Creative alone only allows flight: without this the player drops into the lake and the far terrain is made
        // around the lake bed instead.
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setDeltaMovement(Vec3.ZERO);
            }));
        }
        log("holding at " + first.name() + " for " + seconds + " s, then closing");
        autorun = false;
        Util.ioPool().execute(() -> {
            try {
                Thread.sleep(seconds * 1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mc.execute(mc::stop);
        });
    }

    static void server(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> runNow(server, command));
        }
    }

    /** On the server thread. */
    static void runNow(IntegratedServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Settings loadSettings() throws IOException {
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG);
        if (!Files.exists(file)) {
            Files.writeString(file, GSON.toJson(new Settings()));
        }
        try (Reader r = Files.newBufferedReader(file)) {
            Settings loaded = GSON.fromJson(r, Settings.class);
            if (loaded == null) {
                return new Settings();
            }
            // Modes added to the kit after the file was written are offered too.
            List<String> names = loaded.modes.stream().map(Mode::name).toList();
            Settings defaults = new Settings();
            for (Mode mode : defaults.modes) {
                if (!names.contains(mode.name())) {
                    loaded.modes.add(mode);
                }
            }
            // So are runs.
            if (loaded.runs == null) {
                loaded.runs = new ArrayList<>();
            }
            List<String> runNames = loaded.runs.stream().map(Run::name).toList();
            for (Run run : defaults.runs) {
                if (!runNames.contains(run.name())) {
                    loaded.runs.add(run);
                }
            }
            return loaded;
        }
    }

    /**
     * Ends the open segment's entry in the timeline and opens one for the next (null: none). Each entry has the segment's
     * start and end on two clocks: System.nanoTime in ms, which on Windows is the performance counter (the clock of
     * PresentMon's --qpc_time_ms, so its frames line up with the segments), and the wall clock in epoch ms (for Java Flight
     * Recorder's samples).
     */
    private static void markSegment(Segment next, long nanos) {
        long epoch = System.currentTimeMillis();
        if (openSegment != null) {
            openSegment.addProperty("endMs", nanos / 1e6);
            openSegment.addProperty("endEpochMs", epoch);
            openSegment = null;
        }
        if (next != null && timeline != null) {
            openSegment = new JsonObject();
            openSegment.addProperty("segment", next.label());
            openSegment.addProperty("mode", next.mode().name());
            openSegment.addProperty("viewpoint", next.viewpoint().name());
            openSegment.addProperty("path", next.path());
            openSegment.addProperty("frames", next.length());
            openSegment.addProperty("startMs", nanos / 1e6);
            openSegment.addProperty("startEpochMs", epoch);
            timeline.add(openSegment);
        }
    }

    private static void writeManifest(String outcome) {
        if (runDir == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        JsonObject manifest = new JsonObject();
        manifest.addProperty("run", runId);
        manifest.addProperty("outcome", outcome);
        manifest.addProperty("renderer", RenderSystem.isOnRenderThread() ? com.mojang.blaze3d.platform.GlUtil.getRenderer() : "?");
        manifest.addProperty("shaderPack", Iris.getCurrentPackName());
        JsonArray size = new JsonArray();
        size.add(mc.getWindow().getWidth());
        size.add(mc.getWindow().getHeight());
        manifest.add("resolution", size);
        JsonArray modes = new JsonArray();
        modeInfo.values().forEach(modes::add);
        manifest.add("modes", modes);
        JsonObject paths = new JsonObject();
        for (String name : Paths.NAMES) {
            JsonArray captures = new JsonArray();
            for (int c : Paths.get(name).captures()) {
                captures.add(c);
            }
            paths.add(name, captures);
        }
        manifest.add("paths", paths);
        manifest.add("viewpoints", GSON.toJsonTree(settings.viewpoints));
        manifest.add("shots", shots);
        manifest.addProperty("clock", "startMs/endMs: System.nanoTime in ms (on Windows the performance counter, as PresentMon's"
                + " --qpc_time_ms); startEpochMs/endEpochMs: the wall clock");
        manifest.add("timeline", timeline);
        try {
            Files.writeString(runDir.resolve("manifest.json"), GSON.toJson(manifest));
        } catch (IOException e) {
            LOG.warn("DLAA test: could not write the manifest", e);
        }
    }
}

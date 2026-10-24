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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** config/create_galore_dlss_testkit.json. */
    static final class Settings {
        /** The first mode warms up longer: chunks finish building and Distant Horizons fills in before any capture. */
        int firstWarmupFrames = 900;
        int warmupFrames = 240;
        int settleFrames = 120;
        long time = 3000;
        boolean freezeTicks = true;
        List<Mode> modes = new ArrayList<>(List.of(
                new Mode("photon-taa", false, "default", null, null),
                new Mode("dlaa-default", true, "default", null, null),
                new Mode("dlaa-K", true, "K", null, null),
                new Mode("dlaa-L", true, "L", null, null),
                new Mode("dlaa-M", true, "M", null, null),
                // Checks of the jitter's direction: with a wrong sign a still picture shimmers and blurs.
                new Mode("sign-flip-x", true, "default", null, "-1,1"),
                new Mode("sign-flip-y", true, "default", null, "1,-1"),
                new Mode("sign-flip-xy", true, "default", null, "-1,-1"),
                // What DLSS gets, drawn instead of DLSS's result (any GPU).
                new Mode("debug-motion", true, "default", "motion", null),
                new Mode("debug-depth", true, "default", "depth", null),
                new Mode("debug-color", true, "default", "color", null)));
        // The DLAA test world (seed 20261024, scene built on the lakeshore at x 1023..1040, z 160..176).
        List<Viewpoint> viewpoints = new ArrayList<>(List.of(
                new Viewpoint("shore", 1029.5, 64.0, 160.5, 0.0f, 8.0f),
                new Viewpoint("glass", 1029.5, 64.0, 168.5, 0.0f, -14.0f),
                new Viewpoint("canopy", 1024.5, 88.0, 205.5, 180.0f, 22.0f)));
        List<String> paths = new ArrayList<>(List.of("still", "flick", "pan", "strafe"));
    }

    /**
     * debugView (color, motion or depth) shows that DLSS input instead of DLSS, on any GPU; jitterSign ("x,y", default
     * "1,1") flips the jitter the Bridge hands to DLSS.
     */
    record Mode(String name, boolean dlaa, String preset, String debugView, String jitterSign) {}

    record Viewpoint(String name, double x, double y, double z, float yaw, float pitch) {}

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
    private static int autorunWait;

    private Capture() {}

    public static boolean running() {
        return running;
    }

    /** Frames since the current mode started: the pack's frameCounter during a run. */
    public static int frame() {
        return frame;
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

    /** Starts a run; null if it started, else why not. */
    static String start(String modeFilter, boolean fromAutorun) {
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
        List<Mode> modes = new ArrayList<>(settings.modes);
        if (modeFilter != null && !modeFilter.isBlank()) {
            List<String> wanted = List.of(modeFilter.trim().split("\\s*[,;+ ]\\s*"));
            modes.removeIf(m -> !wanted.contains(m.name()));
            if (modes.isEmpty()) {
                return "no mode called " + wanted + " in config/" + CONFIG;
            }
        }
        List<Viewpoint> viewpoints = settings.viewpoints;
        List<String> paths = settings.paths;
        if (Boolean.getBoolean(TestKit.MOD_ID + ".quick")) {
            viewpoints = viewpoints.subList(0, 1);
            paths = List.of("still", "flick");
        }
        if (viewpoints.isEmpty()) {
            return "no viewpoints in config/" + CONFIG;
        }

        List<Segment> segments = new ArrayList<>();
        for (Mode mode : modes) {
            int warmup = segments.isEmpty() ? Math.max(settings.firstWarmupFrames, settings.warmupFrames) : settings.warmupFrames;
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
        runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        runDir = mc.gameDirectory.toPath().resolve("screenshots").resolve("dlaa-test").resolve(runId);
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            return "could not make " + runDir + ": " + e;
        }
        shots = new JsonArray();
        modeInfo = new LinkedHashMap<>();
        plan = segments;
        segment = -1;
        local = 0;
        frame = 0;
        frameNanosSum = 0;
        frameNanosCount = 0;
        lastNanos = 0;

        try {
            Path bridgeFile = FMLPaths.CONFIGDIR.get().resolve(BRIDGE_CONFIG);
            savedBridgeConfig = Files.exists(bridgeFile) ? Files.readString(bridgeFile) : null;
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
        log("run " + runId + ": " + modes.size() + " modes, " + viewpoints.size() + " viewpoints, paths " + paths
                + ", " + segments.stream().mapToInt(Segment::length).sum() + " frames");
        return null;
    }

    static void stop(String why) {
        if (!running) {
            return;
        }
        running = false;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = savedHideGui;
        mc.options.bobView().set(savedBob);
        mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        mc.options.fov().set(savedFov);
        mc.options.fovEffectScale().set(savedFovEffect);
        mc.options.setCameraType(savedCamera);
        if (settings != null && settings.freezeTicks) {
            server("tick unfreeze");
        }
        restoreBridgeConfig();
        writeManifest(why);
        log(why + "; screenshots in " + runDir);
        if (autorun) {
            autorun = false;
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
            // Autorun: start once the world has been showing for a while (chunks built).
            if (autorun && mc.player != null && mc.level != null && mc.screen == null && ++autorunWait == 200) {
                long hold = Long.getLong(TestKit.MOD_ID + ".hold", 0);
                if (hold > 0) {
                    hold(mc, hold);
                    return;
                }
                String error = start(System.getProperty(TestKit.MOD_ID + ".modes"), true);
                if (error != null) {
                    log("autorun: " + error);
                    mc.stop();
                }
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
                stop("run finished");
                return;
            }
            current = plan.get(segment);
            if (current.onStart() != null) {
                current.onStart().run();
            }
        }
        frame++;

        Paths.Pose pose = current.path() != null ? Paths.get(current.path()).at(local) : Paths.HOLD;
        place(mc.player, current.viewpoint(), pose);
        pendingShot = null;
        for (int c : current.captures()) {
            if (c == local) {
                pendingShot = current.label() + "-f" + String.format(Locale.ROOT, "%03d", local);
            }
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
            try (Writer w = Files.newBufferedWriter(file)) {
                props.store(w, "Create Galore Bridge display settings (last written by the DLAA test kit, mode " + mode.name() + ")");
            }
            Iris.reload();
        } catch (IOException | RuntimeException e) {
            stop("could not switch to mode " + mode.name() + ": " + e);
            return;
        }
        JsonObject info = new JsonObject();
        info.addProperty("name", mode.name());
        info.addProperty("dlaa", mode.dlaa());
        info.addProperty("preset", mode.preset());
        info.addProperty("debugView", mode.debugView());
        info.addProperty("jitterSign", mode.jitterSign());
        info.add("frameMs", new JsonObject());
        modeInfo.put(mode.name(), info);
        log("mode " + mode.name());
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

    private static boolean bridgeDlaaActive() {
        try {
            return (boolean) Class.forName("dev.creategalore.bridge.dlss.Dlss").getMethod("shaderActive").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
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

    private static void server(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
        }
    }

    private static Settings loadSettings() throws IOException {
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG);
        if (!Files.exists(file)) {
            Files.writeString(file, GSON.toJson(new Settings()));
        }
        try (Reader r = Files.newBufferedReader(file)) {
            Settings loaded = GSON.fromJson(r, Settings.class);
            return loaded != null ? loaded : new Settings();
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
        try {
            Files.writeString(runDir.resolve("manifest.json"), GSON.toJson(manifest));
        } catch (IOException e) {
            LOG.warn("DLAA test: could not write the manifest", e);
        }
    }
}

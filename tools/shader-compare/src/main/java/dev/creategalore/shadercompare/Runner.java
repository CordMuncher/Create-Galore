package dev.creategalore.shadercompare;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
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
 * One run, driven frame by frame from the render loop: keep every scene's chunks loaded and put the scenes up, then for
 * each pack and pass load the pack with that pass's options, and at each scene set the time and weather, hold the
 * camera there until the picture settles (the packs' exposure, clouds and temporal filters), and save a screenshot.
 */
final class Runner {
    private static final Logger LOG = LoggerFactory.getLogger(ShaderCompare.MOD_ID);
    private static final String CONFIG = "create_galore_shader_compare.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Frames the world shows before an autorun starts (chunks around the player built). */
    private static final int AUTORUN_WAIT_FRAMES = 200;

    /** config/create_galore_shader_compare.json (made by tools/shader-compare/make_config.py). */
    static final class Settings {
        List<String> passes = List.of("defaults", "highest");
        /** Seconds for the scenes' chunks to load (and generate) once they are kept loaded, before they are built. */
        int prepareSeconds = 30;
        /** After loading a pack: its programs compile on the first frames. */
        int reloadSeconds = 10;
        /** After arriving at a scene, before the settling starts: chunks there finish building. */
        int loadSeconds = 3;
        /** Instead, after arriving in another dimension. */
        int dimensionSeconds = 6;
        int settleSeconds = 10;
        int settleFrames = 90;
        List<Pack> packs = List.of();
        List<Scene> scenes = List.of();
    }

    /**
     * file: the pack's name in shaderpacks/ (a zip's file name, or a folder); pbr: the options that make it read the
     * resource pack's material maps (null: it can't); highest: its highest preset's options (null: no presets).
     */
    record Pack(String name, String title, String version, String file, Map<String, String> pbr, String highestProfile,
                Map<String, String> highest, String note) {}

    /**
     * time: the time of day (null: 6000); weather: clear, rain or thunder (null: clear); build: commands that put the
     * scene up; find: a command that puts the player somewhere safe on the first visit, a spot kept for later visits.
     */
    record Scene(String name, String dimension, double x, double y, double z, float yaw, float pitch, Long time, String weather,
                 Integer settleSeconds, List<String> build, String find, String about) {}

    /** What one stretch of the run does: on starting, then held for at least seconds and frames. */
    private record Step(String label, int job, Scene scene, Runnable onStart, double seconds, int frames, boolean shot) {}

    private record Job(Pack pack, String pass, String mode, Map<String, String> options) {}

    private static Settings settings;
    private static boolean running;
    private static boolean autorun;
    private static int autorunWait = -1;
    private static List<Job> jobs = List.of();
    private static List<Step> plan = List.of();
    private static int index;
    private static long stepStartNanos;
    private static int stepFrames;
    private static boolean pendingShot;
    /** Move on at the next frame (the shot is taken, or the pack didn't load). */
    private static boolean advance;
    private static Path runDir;
    private static String runId;
    private static final JsonArray shots = new JsonArray();
    private static final Map<String, JsonObject> modeInfo = new LinkedHashMap<>();
    private static final boolean[] failed = new boolean[256];
    /** Spots found by the scenes' find commands, kept for later visits. */
    private static final Map<String, Vec3> found = new HashMap<>();
    /** Iris's own settings and each pack's options file as they were before the run (null: there was none). */
    private static String savedPackName;
    private static boolean savedShadersEnabled;
    private static final Map<Path, String> savedOptionFiles = new LinkedHashMap<>();
    private static boolean savedHideGui;
    private static boolean savedBob;
    private static boolean savedPauseOnLostFocus;
    private static int savedFov;
    private static double savedFovEffect;
    private static CameraType savedCamera;

    private Runner() {}

    static void log(String message) {
        LOG.info("Shader compare: {}", message);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Shader compare: " + message), false);
        }
    }

    static void armAutorun() {
        autorun = true;
        autorunWait = 0;
    }

    private static List<String> filter(String property) {
        String value = System.getProperty(ShaderCompare.MOD_ID + "." + property);
        return value == null || value.isBlank() ? null : Arrays.asList(value.trim().split("\\+"));
    }

    /** Starts a run of the packs in packFilter (a+b; null: all); null if it started, else why not. */
    static String start(String packFilter) {
        Minecraft mc = Minecraft.getInstance();
        if (running) {
            return "a run is going";
        }
        if (mc.getSingleplayerServer() == null || mc.player == null) {
            return "open the test world in singleplayer first";
        }
        try {
            settings = loadSettings();
        } catch (IOException | RuntimeException e) {
            return "could not read config/" + CONFIG + ": " + e;
        }
        List<String> packNames = packFilter == null || packFilter.isBlank() ? null : Arrays.asList(packFilter.trim().split("\\+"));
        List<String> passNames = filter("passes");
        List<String> sceneNames = filter("scenes");
        List<Scene> scenes = settings.scenes.stream().filter(s -> sceneNames == null || sceneNames.contains(s.name())).toList();
        if (scenes.isEmpty()) {
            return "no scenes to photograph";
        }

        // The jobs: each pass in turn over every pack, so the first pass is complete before the second starts.
        List<Job> list = new ArrayList<>();
        modeInfo.clear();
        for (String pass : settings.passes) {
            if (passNames != null && !passNames.contains(pass)) {
                continue;
            }
            for (Pack pack : settings.packs) {
                if (packNames != null && !packNames.contains(pack.name())) {
                    continue;
                }
                Map<String, String> options = new LinkedHashMap<>();
                if ("highest".equals(pass)) {
                    if (pack.highest() == null) {
                        continue; // no presets: the same as its defaults
                    }
                    options.putAll(pack.highest());
                }
                if (pack.pbr() != null) {
                    options.putAll(pack.pbr());
                }
                Job job = new Job(pack, pass, pack.name() + "-" + pass, options);
                list.add(job);
                JsonObject info = new JsonObject();
                info.addProperty("name", job.mode());
                info.addProperty("pack", pack.name());
                info.addProperty("title", pack.title());
                info.addProperty("version", pack.version());
                info.addProperty("file", pack.file());
                info.addProperty("pass", pass);
                info.addProperty("profile", "highest".equals(pass) ? pack.highestProfile() : null);
                info.add("options", GSON.toJsonTree(options));
                info.addProperty("note", pack.note());
                modeInfo.put(job.mode(), info);
            }
        }
        if (list.isEmpty()) {
            return "no packs to photograph";
        }
        if (list.size() > failed.length) {
            return "too many packs and passes in one run (" + list.size() + ")";
        }
        jobs = list;
        Arrays.fill(failed, false);
        found.clear();

        // The plan.
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("prepare", -1, null, () -> prepare(scenes), settings.prepareSeconds, 20, false));
        steps.add(new Step("build", -1, null, () -> scenes.forEach(Runner::build), 2, 20, false));
        for (int j = 0; j < jobs.size(); j++) {
            int job = j;
            steps.add(new Step(jobs.get(j).mode() + " load", j, null, () -> loadPack(job), settings.reloadSeconds, 30, false));
            String dimension = null;
            for (Scene scene : scenes) {
                boolean travels = dimension == null || !dimension.equals(dimensionOf(scene)) || scene.find() != null;
                dimension = dimensionOf(scene);
                steps.add(new Step(jobs.get(j).mode() + " " + scene.name() + " arrive", j, scene, () -> enter(scene),
                        travels ? settings.dimensionSeconds : settings.loadSeconds, 20, false));
                int settle = scene.settleSeconds() != null ? scene.settleSeconds() : settings.settleSeconds;
                steps.add(new Step(jobs.get(j).mode() + " " + scene.name(), j, scene, null, settle, settings.settleFrames, true));
            }
        }
        plan = steps;
        index = -1;
        stepFrames = 0;
        pendingShot = false;
        advance = false;
        while (!shots.isEmpty()) {
            shots.remove(0);
        }

        runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        runDir = mc.gameDirectory.toPath().resolve("screenshots").resolve("shader-compare").resolve(runId);
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            return "could not make " + runDir + ": " + e;
        }
        savedPackName = Iris.getIrisConfig().getShaderPackName().orElse(null);
        savedShadersEnabled = Iris.getIrisConfig().areShadersEnabled();
        savedOptionFiles.clear();
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
        running = true;
        log("run " + runId + ": " + jobs.size() + " packs and passes, " + scenes.size() + " scenes, about "
                + Math.round(plan.stream().mapToDouble(Step::seconds).sum() / 60) + " minutes");
        return null;
    }

    private static String dimensionOf(Scene scene) {
        return scene.dimension() != null ? scene.dimension() : "minecraft:overworld";
    }

    private static void server(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command));
        }
    }

    private static void inDimension(Scene scene, String command) {
        server("execute in " + dimensionOf(scene) + " run " + command);
    }

    // The world holds still: no day passing, no weather of its own, no mobs spawning, no grass spreading or fire.
    private static void prepare(List<Scene> scenes) {
        server("gamemode creative @a");
        server("gamerule doDaylightCycle false");
        server("gamerule doWeatherCycle false");
        server("gamerule doMobSpawning false");
        server("gamerule randomTickSpeed 0");
        server("gamerule doFireTick false");
        server("weather clear");
        for (Scene scene : scenes) {
            if (scene.find() == null) {
                int x = (int) Math.floor(scene.x());
                int z = (int) Math.floor(scene.z());
                inDimension(scene, String.format(Locale.ROOT, "forceload add %d %d %d %d", x - 24, z - 24, x + 24, z + 24));
            }
        }
    }

    private static void build(Scene scene) {
        if (scene.build() != null) {
            scene.build().forEach(command -> inDimension(scene, command));
        }
    }

    private static void enter(Scene scene) {
        server("time set " + (scene.time() != null ? scene.time() : 6000L));
        server("weather " + (scene.weather() != null ? scene.weather() : "clear"));
        build(scene);
        Vec3 spot = spot(scene);
        if (spot == null) {
            inDimension(scene, scene.find());
        } else {
            inDimension(scene, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", spot.x, spot.y, spot.z,
                    scene.yaw(), scene.pitch()));
        }
    }

    /** Where the camera stands at a scene (null: not found yet). */
    private static Vec3 spot(Scene scene) {
        return scene.find() == null ? new Vec3(scene.x(), scene.y(), scene.z()) : found.get(scene.name());
    }

    private static void loadPack(int job) {
        Job j = jobs.get(job);
        JsonObject info = modeInfo.get(j.mode());
        try {
            Path shaderpacks = Iris.getShaderpacksDirectory();
            if (!Files.exists(shaderpacks.resolve(j.pack().file()))) {
                fail(job, "shaderpacks/" + j.pack().file() + " is missing");
                return;
            }
            // The pack's options file holds exactly this pass's options (the rest stay at the pack's defaults).
            Path optionsFile = shaderpacks.resolve(j.pack().file() + ".txt");
            if (!savedOptionFiles.containsKey(optionsFile)) {
                savedOptionFiles.put(optionsFile, Files.exists(optionsFile) ? Files.readString(optionsFile, StandardCharsets.ISO_8859_1) : null);
            }
            StringBuilder text = new StringBuilder("#Shader compare, ").append(j.mode()).append('\n');
            j.options().forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
            Files.writeString(optionsFile, text.toString(), StandardCharsets.ISO_8859_1);
            Iris.getIrisConfig().setShaderPackName(j.pack().file());
            Iris.getIrisConfig().setShadersEnabled(true);
            Iris.getIrisConfig().save();
            Iris.reload();
        } catch (IOException | RuntimeException e) {
            fail(job, "could not load: " + e);
            return;
        }
        Optional<Exception> error = Iris.getStoredError();
        if (error.isPresent()) {
            fail(job, "Iris could not load it: " + error.get());
        } else if (!j.pack().file().equals(Iris.getCurrentPackName())) {
            fail(job, "Iris loaded " + Iris.getCurrentPackName() + " instead");
        } else {
            log("now " + j.mode());
        }
    }

    // Once a pack has had its first frames: whether its programs compiled (else Iris draws without shaders and says
    // why in the chat and the log).
    private static void checkPack(int job) {
        JsonObject info = modeInfo.get(jobs.get(job).mode());
        if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline) {
            info.addProperty("loaded", true);
        } else {
            fail(job, "its programs did not compile (Iris draws without shaders; the log says why)");
        }
    }

    private static void fail(int job, String why) {
        failed[job] = true;
        advance = true;
        JsonObject info = modeInfo.get(jobs.get(job).mode());
        info.addProperty("loaded", false);
        info.addProperty("error", why);
        log(jobs.get(job).mode() + ": " + why + "; skipped");
    }

    static void beforeFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!running) {
            // Autorun: start once the world has been showing for a while.
            if (autorun && autorunWait >= 0 && mc.player != null && mc.level != null && mc.screen == null
                    && ++autorunWait == AUTORUN_WAIT_FRAMES) {
                String error = start(System.getProperty(ShaderCompare.MOD_ID + ".packs"));
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
        Step step = index >= 0 ? plan.get(index) : null;
        boolean waited = step != null && stepFrames >= step.frames() && (now - stepStartNanos) / 1e9 >= step.seconds();
        if (step == null || advance || (waited && !step.shot())) {
            advance = false;
            if (step != null && step.scene() != null && step.scene().find() != null && !found.containsKey(step.scene().name())) {
                // The find command's spot, kept for the later visits.
                found.put(step.scene().name(), mc.player.position());
            }
            if (step != null && step.label().endsWith(" load") && !failed[step.job()]) {
                checkPack(step.job());
            }
            do {
                index++;
            } while (index < plan.size() && plan.get(index).job() >= 0 && failed[plan.get(index).job()]
                    && !plan.get(index).label().endsWith(" load"));
            if (index >= plan.size()) {
                stop("run finished");
                return;
            }
            step = plan.get(index);
            stepStartNanos = now;
            stepFrames = 0;
            advance = false;
            if (step.onStart() != null) {
                step.onStart().run();
            }
            if (advance) {
                return; // its pack didn't load: the next frame moves on
            }
        }
        stepFrames++;
        if (step.scene() != null) {
            hold(mc.player, step.scene());
        }
        // The shot at the end of a settling step, in this frame's afterFrame.
        pendingShot = step.shot() && stepFrames >= step.frames() && (now - stepStartNanos) / 1e9 >= step.seconds();
    }

    // The camera stands at the scene's spot (once the player is in its dimension), looking its way, flying so it doesn't
    // fall, its view still.
    private static void hold(LocalPlayer player, Scene scene) {
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 spot = spot(scene);
        if (spot != null && player.level().dimension().location().toString().equals(dimensionOf(scene))) {
            player.setPos(spot.x, spot.y, spot.z);
            player.xo = player.xOld = spot.x;
            player.yo = player.yOld = spot.y;
            player.zo = player.zOld = spot.z;
        }
        player.setYRot(scene.yaw());
        player.yRotO = scene.yaw();
        player.setXRot(scene.pitch());
        player.xRotO = scene.pitch();
        player.setYHeadRot(scene.yaw());
        player.yHeadRotO = scene.yaw();
        player.yBodyRot = scene.yaw();
        player.yBodyRotO = scene.yaw();
    }

    static void afterFrame(RenderFrameEvent.Post event) {
        if (!running || !pendingShot) {
            return;
        }
        pendingShot = false;
        Step step = plan.get(index);
        Job job = jobs.get(step.job());
        String file = job.mode() + "/" + step.scene().name() + ".png";
        Path target = runDir.resolve(file);
        NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                image.writeToFile(target);
            } catch (IOException e) {
                LOG.warn("Shader compare: could not save {}", target, e);
            } finally {
                image.close();
            }
        });
        JsonObject shot = new JsonObject();
        shot.addProperty("mode", job.mode());
        shot.addProperty("viewpoint", step.scene().name());
        shot.addProperty("path", "still");
        shot.addProperty("frame", 0);
        shot.addProperty("file", file);
        shots.add(shot);
        advance = true;
        writeManifest("running");
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
        server("weather clear");
        // Iris and the packs' options files go back as they were.
        try {
            for (Map.Entry<Path, String> e : savedOptionFiles.entrySet()) {
                if (e.getValue() != null) {
                    Files.writeString(e.getKey(), e.getValue(), StandardCharsets.ISO_8859_1);
                } else {
                    Files.deleteIfExists(e.getKey());
                }
            }
            Iris.getIrisConfig().setShaderPackName(savedPackName);
            Iris.getIrisConfig().setShadersEnabled(savedShadersEnabled);
            Iris.getIrisConfig().save();
            if (!autorun || Boolean.getBoolean(ShaderCompare.MOD_ID + ".stay")) {
                Iris.reload();
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Shader compare: could not put Iris's settings back: {}", e.toString());
        }
        writeManifest(why);
        log(why + "; screenshots in " + runDir);
        if (autorun && !Boolean.getBoolean(ShaderCompare.MOD_ID + ".stay")) {
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

    private static Settings loadSettings() throws IOException {
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG);
        if (!Files.exists(file)) {
            throw new IOException("it is missing (copy tools/shader-compare/" + CONFIG + " there)");
        }
        try (Reader r = Files.newBufferedReader(file)) {
            Settings loaded = GSON.fromJson(r, Settings.class);
            if (loaded == null || loaded.packs == null || loaded.scenes == null) {
                throw new IOException("it has no packs or no scenes");
            }
            return loaded;
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
        manifest.addProperty("iris", Iris.getVersion());
        JsonArray size = new JsonArray();
        size.add(mc.getWindow().getWidth());
        size.add(mc.getWindow().getHeight());
        manifest.add("resolution", size);
        JsonArray modes = new JsonArray();
        modeInfo.values().forEach(modes::add);
        manifest.add("modes", modes);
        JsonObject paths = new JsonObject();
        JsonArray still = new JsonArray();
        still.add(0);
        paths.add("still", still);
        manifest.add("paths", paths);
        manifest.add("viewpoints", GSON.toJsonTree(settings.scenes));
        JsonObject spots = new JsonObject();
        found.forEach((name, spot) -> spots.add(name, GSON.toJsonTree(new double[] {spot.x, spot.y, spot.z})));
        manifest.add("found", spots);
        manifest.add("shots", shots);
        try {
            Files.writeString(runDir.resolve("manifest.json"), GSON.toJson(manifest));
        } catch (IOException e) {
            LOG.warn("Shader compare: could not write the manifest", e);
        }
    }
}

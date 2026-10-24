package dev.creategalore.bridge.dlss;

import static org.lwjgl.opengl.GL11C.GL_RENDERER;
import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.glGetString;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.ToIntFunction;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.creategalore.bridge.DisplaySettings;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA DLAA (DLSS at full resolution) in place of a shader pack's TAA, for packs patched for it (see
 * {@link PhotonPatcher} and {@link PackProfile}). Each frame the Bridge picks the jitter (a 32-step Halton sequence,
 * handed to the pack as cg_dlss_jitter_x/y), the pack's TAA pass turns into a copy that also writes DLSS's inputs, and
 * right after that pass {@link DlssPass} runs DLSS and writes its result where TAA's would have gone.
 *
 * config/create_galore_bridge-display.properties: dlaa=true turns it on, dlaaPreset picks the DLSS preset; both are
 * read again when shaders reload. It runs on Perfect Presentation's DirectX device when that is on, else on its own
 * (see {@link DlssDevice}). Anything missing (not Windows, no NVIDIA RTX card, no nvngx_dlss.dll, an unpatched pack, a
 * pack option that the patch does not cover) leaves the pack's own anti-aliasing in place and says why in the log.
 *
 * For testing: dlaaDebugView=color|motion|depth in the display settings (or the system property
 * create_galore_bridge.dlss.debugView) draws that DLSS input instead of running DLSS (any GPU, no NGX);
 * dlaaEngine=fsr3 (or create_galore_bridge.dlss.engine) runs AMD FSR 3.1 in DLSS's place with FSR's own jitter
 * period ({@link Fsr3Upscaler}, OpenGL, any GPU), and dlaaDebugView=fsr3 shows FSR's own view of its buffers;
 * create_galore_bridge.dlss.dllDir loads nvngx_dlss.dll from another folder (the
 * SDK's dev build shows DLSS's own debug overlay); dlaaJitterSign=x,y in the display settings (or the system property
 * create_galore_bridge.dlss.jitterSign) flips the jitter handed to DLSS.
 */
public final class Dlss {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** NGX needs a GUID-like id from apps without an NVIDIA application id. */
    private static final String PROJECT_ID = "0f4d213d-b46a-4c91-9f2f-baac70721d6b";
    private static final String ENGINE_VERSION = "1.21.1";
    /** In the game folder: nvngx_dlss.dll (put there by the pack's updater), the helper DLL and NGX's logs. */
    private static final String FOLDER = "create-galore-dlss";
    private static final String DLL_DIR_PROPERTY = "create_galore_bridge.dlss.dllDir";
    private static final String DEBUG_VIEW_PROPERTY = "create_galore_bridge.dlss.debugView";
    private static final String JITTER_SIGN_PROPERTY = "create_galore_bridge.dlss.jitterSign";
    private static final String ENGINE_PROPERTY = "create_galore_bridge.dlss.engine";
    /** DLSS's jitter period; FSR 3 uses its own (8 at full size). */
    private static final int PHASES = 32;
    /** A camera move this far in one frame is a teleport: DLSS starts over instead of blending in the old place. */
    private static final double RESET_DISTANCE = 8.0;
    /** Frames in a row the pack's pass may not run while DLSS is on before DLSS gives up (the jitter would show). */
    private static final int MISSED_FRAMES_LIMIT = 3;
    // NVSDK_NGX_DLSS_Hint_Render_Preset
    private static final int PRESET_DEFAULT = 0;
    private static final int PRESET_J = 10;
    private static final int PRESET_K = 11;
    private static final int PRESET_L = 12;
    private static final int PRESET_M = 13;

    // Read when shaders (re)load.
    private static boolean wanted;
    private static float[] jitterSign = {1.0f, 1.0f};
    private static int preset = PRESET_DEFAULT;
    private static DlssPass.View debugView;
    /** FSR 3 in place of DLSS (testing). */
    private static boolean fsr3;
    /** FSR 3 draws its own debug view. */
    private static boolean fsr3Debug;
    private static PackProfile profile;
    private static String packProblem;
    /** A run failed: the pack's own anti-aliasing is back until shaders reload. */
    private static boolean failed;
    /** The game is quitting: its last frames must not start DLSS again after it was closed. */
    private static boolean shuttingDown;

    // For the session.
    /** Why DLSS cannot run on this computer, once that is known. */
    private static String unavailable;
    private static NgxLibrary ngx;
    private static DlssDevice device;
    private static DlssPass pass;
    /** The debug views' pass: plain OpenGL textures, no DLSS. */
    private static DlssPass debugPass;
    /** Why FSR 3 cannot run here, once that is known. */
    private static String fsr3Unavailable;
    private static Fsr3Upscaler fsr;
    /** FSR 3's pass: plain OpenGL textures like the debug views'. */
    private static DlssPass fsrPass;
    private static int featureFlags = -1;
    private static int featurePreset = -1;

    // For the frame.
    private static boolean active;
    private static boolean ran;
    private static int missedFrames;
    private static float jitterX;
    private static float jitterY;
    private static boolean reset = true;
    private static long lastFrameNanos;
    private static float frameTimeMs = 16.7f;
    private static WeakReference<Object> lastLevel = new WeakReference<>(null);
    private static WeakReference<Object> lastCameraEntity = new WeakReference<>(null);
    private static Vec3 lastCameraPosition;

    private Dlss() {}

    /** At mod construction: frees DLSS when the game closes (the patched pack copy is made earlier, see PhotonPatcher). */
    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(Dlss::shutdown);
        modBus.addListener(Dlss::loadComplete);
        PhotonPatcher.prepare();
    }

    // Colorwheel Patcher makes its Photon zip during setup, after Iris loaded its pack: the copy follows it.
    private static void loadComplete(FMLLoadCompleteEvent event) {
        if (PhotonPatcher.refresh()) {
            Minecraft.getInstance().execute(() -> {
                if (PhotonPatcher.PATCHED_NAME.equals(Iris.getCurrentPackName())) {
                    try {
                        Iris.reload();
                    } catch (IOException | RuntimeException e) {
                        LOG.warn("DLAA: could not reload the shaders after remaking {}: {}", PhotonPatcher.PATCHED_NAME, e.toString());
                    }
                }
            });
        }
    }

    /** A shader pack pipeline was made (shaders loaded or reloaded): reads the settings and the pack's profile again. */
    public static void onPipelineCreated() {
        DisplaySettings.reload();
        wanted = DisplaySettings.get(DisplaySettings.DLAA);
        String presetName = DisplaySettings.getString(DisplaySettings.DLAA_PRESET, "default");
        preset = parsePreset(presetName);
        String debugName = DisplaySettings.getString(DisplaySettings.DLAA_DEBUG_VIEW, System.getProperty(DEBUG_VIEW_PROPERTY, ""));
        fsr3Debug = debugName.trim().equalsIgnoreCase("fsr3");
        debugView = fsr3Debug ? null : parseDebugView(debugName);
        fsr3 = fsr3Debug || parseEngine(DisplaySettings.getString(DisplaySettings.DLAA_ENGINE, System.getProperty(ENGINE_PROPERTY, "dlss")));
        jitterSign = parseJitterSign(DisplaySettings.getString(DisplaySettings.DLAA_JITTER_SIGN, System.getProperty(JITTER_SIGN_PROPERTY, "1,1")));
        failed = false;
        reset = true;
        missedFrames = 0;
        profile = null;
        packProblem = null;
        if (!wanted) {
            return;
        }
        try {
            profile = PackProfile.current();
            packProblem = profile == null ? "it is not patched for DLAA (" + PhotonPatcher.PATCHED_NAME + " is)" : profile.optionProblem();
        } catch (IOException | RuntimeException e) {
            packProblem = "its " + PackProfile.FILE + " could not be read: " + e;
        }
        if (packProblem != null) {
            LOG.info("DLAA: not used with this shader pack, {}", packProblem);
        } else if (fsr3 && debugView == null) {
            LOG.info("DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing){}{}",
                    jitterSign[0] != 1.0f || jitterSign[1] != 1.0f ? ", jitter sign " + (int) jitterSign[0] + "," + (int) jitterSign[1] : "",
                    fsr3Debug ? " (FSR's debug view)" : "");
        } else {
            LOG.info("DLAA: on for this shader pack, preset {}{}{}", presetName,
                    jitterSign[0] != 1.0f || jitterSign[1] != 1.0f ? ", jitter sign " + (int) jitterSign[0] + "," + (int) jitterSign[1] : "",
                    debugView != null ? " (debug view " + debugView.name().toLowerCase(Locale.ROOT) + ", DLSS itself not used)" : "");
        }
    }

    /**
     * Start of a level frame, before the pack's uniforms update: decides whether DLSS runs and picks the jitter.
     * {@code frameCounter} is the pack's frameCounter, so the jitter repeats with the pack's own frame sequence.
     */
    public static void beginFrame(int frameCounter) {
        if (active) {
            missedFrames = ran ? 0 : missedFrames + 1;
            if (missedFrames >= MISSED_FRAMES_LIMIT) {
                LOG.warn("DLAA: the pack's {} pass did not run for {} frames, using the pack's own anti-aliasing until shaders reload",
                        profile.pass(), MISSED_FRAMES_LIMIT);
                failed = true;
            }
        }
        ran = false;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            frameTimeMs = Math.min(Math.max((now - lastFrameNanos) / 1.0e6f, 0.1f), 1000.0f);
        }
        lastFrameNanos = now;

        boolean wasActive = active;
        active = wanted && profile != null && packProblem == null && !failed && !shuttingDown && ensureReady();
        if (!active) {
            return;
        }
        int index = Math.floorMod(frameCounter, fsr3 && debugView == null ? Fsr3Upscaler.phases(1, 1) : PHASES) + 1;
        jitterX = halton(index, 2) - 0.5f;
        jitterY = halton(index, 3) - 0.5f;

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 position = camera.getPosition();
        if (!wasActive || minecraft.level != lastLevel.get() || camera.getEntity() != lastCameraEntity.get()
                || lastCameraPosition == null || position.distanceToSqr(lastCameraPosition) > RESET_DISTANCE * RESET_DISTANCE) {
            reset = true;
        }
        lastLevel = new WeakReference<>(minecraft.level);
        lastCameraEntity = new WeakReference<>(camera.getEntity());
        lastCameraPosition = position;
    }

    /** True while the pack should hand its frame to DLSS (cg_dlss_active). */
    public static boolean shaderActive() {
        return active;
    }

    /** This frame's jitter in pixels, -0.5..0.5 (cg_dlss_jitter_x). */
    public static float jitterX() {
        return active ? jitterX : 0.0f;
    }

    public static float jitterY() {
        return active ? jitterY : 0.0f;
    }

    /**
     * A composite pass just drew, its framebuffer still bound. After the pack's TAA pass this runs DLSS on what it
     * wrote. {@code imageIds} gives the OpenGL texture of one of the pack's custom images by name (0 if none).
     */
    public static void afterPass(String name, ToIntFunction<String> imageIds) {
        if (!active || failed || !profile.pass().equals(name)) {
            return;
        }
        ran = true;
        try {
            DlssPass.Inputs in = DlssPass.inputs(DlssPass.boundColorTexture(profile.colorAttachment()),
                    image(imageIds, profile.motionImage()), image(imageIds, profile.depthImage()),
                    image(imageIds, profile.exposureImage()));
            if (debugView != null) {
                debugPass.ensureSize(in.width(), in.height());
                debugPass.copyAndEvaluate(in, () -> {});
                debugPass.writeBack(profile, debugView);
                return;
            }
            if (fsr3) {
                runFsr3(in);
                return;
            }
            boolean resized = pass.ensureSize(in.width(), in.height());
            // L always meters exposure itself; M is asked to as well, the pack's value is for J and K.
            boolean autoExposure = preset == PRESET_L || preset == PRESET_M;
            int flags = NgxLibrary.FLAG_IS_HDR | NgxLibrary.FLAG_MV_LOW_RES | NgxLibrary.FLAG_DEPTH_INVERTED
                    | (autoExposure ? NgxLibrary.FLAG_AUTO_EXPOSURE : 0);
            if (resized || flags != featureFlags || preset != featurePreset) {
                featureFlags = -1;
                int result = ngx.create(device.context, in.width(), in.height(), in.width(), in.height(),
                        NgxLibrary.PERF_QUALITY_DLAA, flags, preset);
                ngx.drainLog();
                if (NgxLibrary.failed(result)) {
                    throw new IllegalStateException("DLSS could not start at " + in.width() + "x" + in.height() + ": " + NgxLibrary.describe(result));
                }
                featureFlags = flags;
                featurePreset = preset;
                reset = true;
                LOG.info("DLAA: DLSS running at {}x{}, preset {}", in.width(), in.height(), presetName(preset));
            }
            boolean resetNow = reset;
            int[] result = new int[1];
            pass.copyAndEvaluate(in, () -> result[0] = ngx.evaluate(device.context, pass.colorResource(), pass.outputResource(),
                    pass.depthResource(), pass.motionResource(), autoExposure ? 0L : pass.exposureResource(),
                    jitterX * jitterSign[0], jitterY * jitterSign[1], 1.0f, 1.0f, resetNow, in.width(), in.height(), frameTimeMs));
            ngx.drainLog();
            if (NgxLibrary.failed(result[0])) {
                throw new IllegalStateException("DLSS failed: " + NgxLibrary.describe(result[0]));
            }
            reset = false;
            pass.writeBack(profile, DlssPass.View.RESULT);
        } catch (Throwable t) {
            failed = true;
            LOG.error("DLAA: stopped, the pack's own anti-aliasing is back from the next frame (reloading shaders tries DLAA again)", t);
        }
    }

    private static void runFsr3(DlssPass.Inputs in) {
        boolean resized = fsrPass.ensureSize(in.width(), in.height());
        if (fsr.ensureSize(in.width(), in.height(), in.width(), in.height()) || resized) {
            reset = true;
            LOG.info("DLAA: FSR 3.1 running at {}x{}", in.width(), in.height());
        }
        boolean resetNow = reset;
        Matrix4fc projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        fsrPass.copyAndEvaluate(in, () -> fsr.dispatch(fsrPass.colorTexture(), fsrPass.depthTexture(), fsrPass.motionTexture(),
                fsrPass.exposureTexture(), fsrPass.outputTexture(), jitterX * jitterSign[0], jitterY * jitterSign[1], resetNow,
                frameTimeMs, projection, fsr3Debug));
        reset = false;
        fsrPass.writeBack(profile, DlssPass.View.RESULT);
    }

    private static int image(ToIntFunction<String> imageIds, String name) {
        int id = imageIds.applyAsInt(name);
        if (id <= 0) {
            throw new IllegalStateException("the pack has no image " + name);
        }
        return id;
    }

    /** Loads NGX and opens the DirectX device the first time DLSS is needed. False (once logged) if it cannot run. */
    private static boolean ensureReady() {
        if (debugView != null) {
            if (debugPass == null) {
                debugPass = new DlssPass(null);
            }
            return true;
        }
        if (fsr3) {
            return ensureFsr3();
        }
        if (pass != null) {
            return true;
        }
        if (unavailable != null) {
            return false;
        }
        try {
            open();
            return true;
        } catch (Throwable t) {
            unavailable = t.getMessage() != null ? t.getMessage() : t.toString();
            LOG.warn("DLAA: not available, the shader pack keeps its own anti-aliasing: {}", unavailable);
            LOG.debug("DLAA: start-up failure", t);
            closeAll();
            return false;
        }
    }

    /** Compiles FSR 3's passes the first time it is needed. False (once logged) if it cannot run. */
    private static boolean ensureFsr3() {
        if (fsr != null) {
            return true;
        }
        if (fsr3Unavailable != null) {
            return false;
        }
        try {
            if (!GL.getCapabilities().OpenGL45) {
                throw new IllegalStateException("FSR 3 needs OpenGL 4.5");
            }
            fsr = new Fsr3Upscaler();
            fsrPass = new DlssPass(null);
            LOG.info("DLAA: FSR 3.1 ready on {}", glGetString(GL_RENDERER));
            return true;
        } catch (Throwable t) {
            fsr3Unavailable = t.getMessage() != null ? t.getMessage() : t.toString();
            LOG.warn("DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: {}", fsr3Unavailable);
            LOG.debug("DLAA: FSR 3 start-up failure", t);
            closeFsr3();
            return false;
        }
    }

    private static void closeFsr3() {
        if (fsr != null) {
            fsr.close();
        }
        if (fsrPass != null) {
            fsrPass.close();
        }
        fsr = null;
        fsrPass = null;
    }

    private static void open() throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new IllegalStateException("DLSS needs Windows");
        }
        String vendor = glGetString(GL_VENDOR);
        if (vendor == null || !vendor.toLowerCase(Locale.ROOT).contains("nvidia")) {
            throw new IllegalStateException("the game runs on " + vendor + ", DLSS needs an NVIDIA RTX graphics card");
        }
        Path folder = FMLPaths.GAMEDIR.get().resolve(FOLDER);
        String dllDir = System.getProperty(DLL_DIR_PROPERTY);
        Path dllFolder = dllDir != null && !dllDir.isBlank() ? Path.of(dllDir) : folder;
        Path dll = dllFolder.resolve("nvngx_dlss.dll");
        if (!Files.isRegularFile(dll)) {
            throw new IllegalStateException("there is no " + dll + " (the pack's updater puts it there)");
        }
        Files.createDirectories(folder.resolve("ngx"));
        ngx = NgxLibrary.load(folder);
        device = DlssDevice.open();
        int result = ngx.init(PROJECT_ID, ENGINE_VERSION, folder.resolve("ngx"), dllFolder, device.device, NgxLibrary.LOG_ON);
        ngx.drainLog();
        if (NgxLibrary.failed(result)) {
            throw new IllegalStateException("NVIDIA NGX did not start: " + NgxLibrary.describe(result));
        }
        int[] support = ngx.query();
        if (support == null) {
            throw new IllegalStateException("NGX could not say whether DLSS runs here");
        }
        if (support[0] == 0) {
            if (support[1] != 0) {
                throw new IllegalStateException("DLSS needs NVIDIA driver " + support[2] + "." + support[3] + " or newer");
            }
            throw new IllegalStateException("DLSS does not run on this graphics card (" + NgxLibrary.describe(support[4]) + ")");
        }
        pass = new DlssPass(device);
        LOG.info("DLAA: NGX ready on {} ({}), nvngx_dlss.dll from {}", device.adapterName,
                device.shared ? "Perfect Presentation's DirectX device" : "its own DirectX device", dllFolder);
    }

    private static void shutdown(GameShuttingDownEvent event) {
        shuttingDown = true;
        if (RenderSystem.isOnRenderThread()) {
            closeAll();
        }
    }

    private static void closeAll() {
        try {
            if (pass != null) {
                pass.close();
            }
            if (debugPass != null) {
                debugPass.close();
            }
            closeFsr3();
            if (ngx != null) {
                ngx.shutdown();
                ngx.drainLog();
            }
            if (device != null) {
                device.close();
            }
        } catch (Throwable t) {
            LOG.warn("DLAA: could not close cleanly: {}", t.toString());
        } finally {
            pass = null;
            debugPass = null;
            fsr = null;
            fsrPass = null;
            ngx = null;
            device = null;
            featureFlags = -1;
            featurePreset = -1;
            active = false;
        }
    }

    /** The radical inverse of {@code index} in {@code base}: 0..1, evenly spread over any run of indices. */
    private static float halton(int index, int base) {
        float result = 0.0f;
        float fraction = 1.0f;
        while (index > 0) {
            fraction /= base;
            result += fraction * (index % base);
            index /= base;
        }
        return result;
    }

    private static int parsePreset(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "J" -> PRESET_J;
            case "K" -> PRESET_K;
            case "L" -> PRESET_L;
            case "M" -> PRESET_M;
            case "DEFAULT", "" -> PRESET_DEFAULT;
            default -> {
                LOG.warn("DLAA: unknown dlaaPreset {}, using default (choose default, J, K, L or M)", name);
                yield PRESET_DEFAULT;
            }
        };
    }

    private static String presetName(int preset) {
        return switch (preset) {
            case PRESET_J -> "J";
            case PRESET_K -> "K";
            case PRESET_L -> "L";
            case PRESET_M -> "M";
            default -> "default";
        };
    }

    private static DlssPass.View parseDebugView(String name) {
        String value = name.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        try {
            DlssPass.View view = DlssPass.View.valueOf(value);
            return view == DlssPass.View.RESULT ? null : view;
        } catch (IllegalArgumentException e) {
            LOG.warn("DLAA: unknown debug view {}, use color, motion or depth", name);
            return null;
        }
    }

    private static boolean parseEngine(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "fsr3", "fsr" -> true;
            case "dlss", "" -> false;
            default -> {
                LOG.warn("DLAA: unknown dlaaEngine {}, using DLSS (choose dlss or fsr3)", name);
                yield false;
            }
        };
    }

    private static float[] parseJitterSign(String value) {
        String[] parts = value.split(",");
        try {
            float x = Math.signum(Float.parseFloat(parts[0].trim()));
            float y = Math.signum(Float.parseFloat(parts[1].trim()));
            if (x != 0.0f && y != 0.0f) {
                return new float[] {x, y};
            }
        } catch (RuntimeException e) {
            // falls through
        }
        LOG.warn("DLAA: jitter sign {} should be like 1,-1; using 1,1", value);
        return new float[] {1.0f, 1.0f};
    }
}

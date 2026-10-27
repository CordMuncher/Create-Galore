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
import dev.creategalore.bridge.framegen.FrameGen;
import dev.creategalore.bridge.mixin.LevelRendererTicksAccessor;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA DLAA (DLSS at full resolution) in place of a shader pack's TAA, for packs patched for it (see
 * {@link PhotonPatcher} and {@link PackProfile}). Each frame the Bridge picks the jitter (a 32-step Halton sequence,
 * handed to the pack as cg_dlss_jitter_x/y), the pack's TAA pass turns into a copy that also writes DLSS's inputs, and
 * right after that pass {@link DlssPass} runs DLSS and writes its result where TAA's would have gone.
 *
 * Upscaling: with the pack's upscaling on (Photon's TAAU, see {@link PackProfile#renderScale()}), the frame renders
 * at a lower size into the corner of the pack's buffers. The Bridge rounds the pack's render scale to whole pixels per
 * axis (taau_render_scale, which Photon's own TAAU renders at too) and DLSS or FSR 3.1 upscale that size to the window's;
 * the jitter period grows with the scale and the textures' mip bias follows it (cg_mip_bias).
 *
 * config/create_galore_bridge-display.properties (or the settings screen, {@link DlaaScreen}): dlaa picks the engine,
 * auto (DLSS where it can run, else AMD FSR 3.1 at full resolution), dlss, fsr3 or off; dlaaPreset picks the DLSS
 * preset. Both are read again when shaders reload. DLSS runs on a DirectX 11 device of its own (see {@link DlssDevice}),
 * also with Perfect Presentation on; with Ray Reconstruction asked for, on a DirectX 12 device of its own instead
 * ({@link DlssDevice12}), since NVIDIA's Ray Reconstruction crashes on DirectX 11. Anything missing (not Windows, NVIDIA's NGX saying no, no nvngx_dlss.dll, an
 * unpatched pack, a pack option that the patch does not cover) leaves the pack's own anti-aliasing in place, says why
 * in the log and once in a notice, and the settings screen shows it. An engine that took the game down while it
 * started stays off until the player tries it again ({@link StartGuard}).
 *
 * For testing: dlaaDebugView=color|motion|depth|residual|mask in the display settings (or the system property
 * create_galore_bridge.dlss.debugView) draws that DLSS input instead of running DLSS (any GPU, no NGX);
 * FSR 3.1 runs with its own jitter period ({@link Fsr3Upscaler}, OpenGL, any GPU), and dlaaDebugView=fsr3 shows
 * FSR's own view of its buffers;
 * create_galore_bridge.dlss.dllDir loads nvngx_dlss.dll from another folder (the
 * SDK's dev build shows DLSS's own debug overlay); dlaaJitterSign=x,y in the display settings (or the system property
 * create_galore_bridge.dlss.jitterSign) flips the jitter handed to DLSS; dlaaObjectMotion=false and
 * dlaaReactiveMask=false leave out the motion of things that move by themselves and FSR's masks, for comparing;
 * dlaaAutoExposure=true has DLSS meter the exposure itself with presets J and K too (L and M always do);
 * create_galore_bridge.dlss.markers=true names the DLAA work for GPU profilers ({@link Markers}).
 *
 * DLSS Ray Reconstruction (experimental, dlaaRayReconstruction=true): one pass that denoises, anti-aliases and upscales,
 * run in DLSS's place with a pack that hands over guide images (PackProfile's rrGuideImages: what each pixel's surface
 * is made of). While it runs the pack gets cg_rr_active 1: it writes the guides, leaves the effects named in dlaaRrRaw
 * noisy (cg_rr_raw_reflections, cg_rr_raw_shadows) for Ray Reconstruction to clean, and with cg_rr_exposed 1 hands over
 * its colour times its exposure (Ray Reconstruction takes no exposure), which the write-back divides out again. The
 * preset is F (DLSS 4.5's model) for every mode unless dlaaRrPreset says otherwise. It needs nvngx_dlssd.dll beside
 * nvngx_dlss.dll and NVIDIA driver 580 or newer; where it can't run, DLSS runs and the settings screen says why. The
 * debug views albedo, specular, normal, roughness and hit_distance show the guides (with dlaaRayReconstruction=true).
 */
public final class Dlss {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** NGX needs a GUID-like id from apps without an NVIDIA application id. */
    private static final String PROJECT_ID = "0f4d213d-b46a-4c91-9f2f-baac70721d6b";
    private static final String ENGINE_VERSION = "1.21.1";
    /** In the game folder: nvngx_dlss.dll (the player's own), the helper DLL, NGX's logs and the start marks. */
    public static final String FOLDER = "create-galore-dlss";
    /** The nvngx_dlss.dll this was tested with (NVIDIA's DLSS SDK on GitHub). */
    static final String TESTED_DLL_VERSION = "310.9.16";
    /** The nvngx_dlssg.dll (DLSS Frame Generation's model) frame generation was tested with. */
    static final String TESTED_FG_DLL_VERSION = "310.9.16";
    static final String DLL = "nvngx_dlss.dll";
    /** The nvngx_dlssd.dll (DLSS Ray Reconstruction) the pilot was made with (NVIDIA's DLSS SDK on GitHub). */
    static final String TESTED_RR_DLL_VERSION = "310.9.16";
    private static final String DLL_DIR_PROPERTY = "create_galore_bridge.dlss.dllDir";
    private static final String DEBUG_VIEW_PROPERTY = "create_galore_bridge.dlss.debugView";
    private static final String JITTER_SIGN_PROPERTY = "create_galore_bridge.dlss.jitterSign";
    private static final String OBJECT_MOTION_PROPERTY = "create_galore_bridge.dlss.objectMotion";
    private static final String REACTIVE_MASK_PROPERTY = "create_galore_bridge.dlss.reactiveMask";
    /** DLSS's shortest jitter period (NVIDIA: at least 8 times the scale factor squared); FSR 3 uses its own (8 at full size). */
    private static final int PHASES = 32;
    /** A camera move this far in one frame is a teleport: DLSS starts over instead of blending in the old place. */
    private static final double RESET_DISTANCE = 8.0;
    /** Frames in a row the pack's pass may not run while DLSS is on before DLSS gives up (the jitter would show). */
    private static final int MISSED_FRAMES_LIMIT = 3;
    // NVSDK_NGX_DLSS_Hint_Render_Preset: 0 lets DLSS choose; the setting's default is K.
    private static final int PRESET_DEFAULT = 0;
    private static final int PRESET_K = 11;
    private static final int PRESET_L = 12;
    private static final int PRESET_M = 13;

    // Read when shaders (re)load.
    /** auto, dlss, fsr3 or off. */
    private static String engine = "auto";
    private static boolean wanted;
    private static float[] jitterSign = {1.0f, 1.0f};
    private static int preset = PRESET_DEFAULT;
    /** DLSS refused the preset typed in, so it runs default. */
    private static boolean presetFellBack;
    /** DLSS meters the exposure itself with J and K too (testing). */
    private static boolean autoExposureTest;
    /** The pack's render scale: 1 without upscaling. */
    private static float renderScaleSetting = 1.0f;
    /** Added to the textures' mip bias while DLSS or FSR runs (testing). */
    private static float extraMipBias;
    private static DlssPass.View debugView;
    /** Things that move by themselves give their own motion vectors (else only the camera's motion; for comparing). */
    private static boolean objectMotion = true;
    /** FSR 3 gets the pack's reactive and composition masks (else none; for comparing). */
    private static boolean reactiveMask = true;
    private static volatile String[] dumpRequest;
    /** FSR 3 runs, not DLSS: picked, or Auto's choice where DLSS cannot run. */
    private static boolean fsr3;
    /** Ray Reconstruction is asked for (experimental). */
    private static boolean rayReconstructionWanted;
    private static int rrPreset = NgxLibrary.RR_PRESET_F;
    /** The pack hands Ray Reconstruction its colour times its exposure (else as it has it; for comparing). */
    private static boolean rrExposed = true;
    // Testing (dlaaRrTest): no hit distance or matrices; NGX parameters of its own; bands of known hit distances.
    private static boolean rrNoHitDistance;
    private static boolean rrOwnParameters;
    private static boolean rrHitDistanceBands;
    private static boolean rrRawReflections = true;
    private static boolean rrRawShadows = true;
    /** FSR 3 draws its own debug view. */
    private static boolean fsr3Debug;
    private static PackProfile profile;
    private static String packProblem;
    /** {@link #packProblem} for a shader pack that is not made for DLAA. */
    private static final String NO_PROFILE = "it is not made for DLAA (it has no " + PackProfile.FILE + ")";
    /** A run failed: the pack's own anti-aliasing is back until shaders reload. */
    private static boolean failed;
    /** The game is quitting: its last frames must not start DLSS again after it was closed. */
    private static boolean shuttingDown;

    // For the session.
    /** Why DLSS cannot run on this computer, once that is known. */
    private static String unavailable;
    /** It cannot only because nvngx_dlss.dll is missing: looked for again when shaders reload. */
    private static boolean unavailableNoDll;
    private static String adapterName;
    private static String fsrRenderer;
    private static StartGuard dlssGuard;
    private static StartGuard fsrGuard;
    private static final java.util.Set<String> noticesShown = new java.util.HashSet<>();
    private static NgxLibrary ngx;
    private static NgxDevice device;
    /** DLSS starts (or started) on DirectX 12, for Ray Reconstruction; else on DirectX 11. */
    private static boolean onDirectX12;
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
    /** The DLSS feature is Ray Reconstruction. */
    private static boolean featureIsRr;
    /** Why Ray Reconstruction cannot run on this computer, once DLSS started (null: it can, as far as NGX knows). */
    private static String rrUnavailable;
    /** It cannot only because nvngx_dlssd.dll is missing: looked for again when shaders reload. */
    private static boolean rrUnavailableNoDll;
    /**
     * What NVIDIA's own runtime (NGX) answered when asked whether Ray Reconstruction runs here, if it said no: it didn't
     * start on this graphics card, or says DLSS or Ray Reconstruction doesn't run on it or needs a newer driver. Null if
     * it said yes or wasn't asked (DLSS hasn't started: no nvngx_dlss.dll, FSR picked). The pack asks no card names of
     * its own: what NGX says goes, asked again whenever DLSS starts.
     */
    private static String rrRefused;

    // For the frame.
    /** The window's size and the render size in it (the same without upscaling). */
    private static int displayWidth = 1;
    private static int displayHeight = 1;
    private static int renderWidth = 1;
    private static int renderHeight = 1;
    private static boolean active;
    /** The pack writes Ray Reconstruction's guides and leaves its effects raw: it runs, or a debug view shows them. */
    private static boolean rrMode;
    /** Ray Reconstruction runs in DLSS's place this frame. */
    private static boolean rrRunning;
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

    /**
     * At mod construction: frees DLSS when the game closes (the patched pack copy is made earlier, see PhotonPatcher), and
     * puts in place a DLL found on this PC that had to wait for the old one to be let go.
     */
    public static void init(IEventBus modBus) {
        DlssFinder.applyPending();
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
        ObjectMotion.reset();
        FrameGen.pipelineCreated();
        DisplaySettings.reload();
        engine = DisplaySettings.dlaaEngine();
        wanted = !engine.equals("off");
        String presetName = DisplaySettings.getString(DisplaySettings.DLAA_PRESET, "default");
        preset = parsePreset(presetName);
        presetFellBack = false;
        autoExposureTest = DisplaySettings.get(DisplaySettings.DLAA_AUTO_EXPOSURE);
        extraMipBias = parseFloat(DisplaySettings.getString(DisplaySettings.DLAA_MIP_BIAS, "0"), -4.0f, 1.0f);
        String debugName = DisplaySettings.getString(DisplaySettings.DLAA_DEBUG_VIEW, System.getProperty(DEBUG_VIEW_PROPERTY, ""));
        fsr3Debug = debugName.trim().equalsIgnoreCase("fsr3");
        debugView = fsr3Debug ? null : parseDebugView(debugName);
        if (fsr3Debug) {
            engine = "fsr3";
        }
        fsr3 = engine.equals("fsr3");
        if (unavailableNoDll) {
            unavailable = null; // looks for nvngx_dlss.dll again
        }
        rayReconstructionWanted = DisplaySettings.get(DisplaySettings.DLAA_RAY_RECONSTRUCTION);
        if (rayReconstructionWanted != onDirectX12) {
            // Ray Reconstruction runs on a DirectX 12 device of its own, DLSS alone on DirectX 11: the other one now.
            onDirectX12 = rayReconstructionWanted;
            if (pass != null) {
                LOG.info("DLAA: DLSS starts again on DirectX {}", onDirectX12 ? "12, for Ray Reconstruction" : "11");
                closeDlss();
            }
            if (unavailable != null && !unavailable.startsWith("the game closed")) {
                unavailable = null; // tried again on the other device
            }
        }
        rrPreset = parseRrPreset(DisplaySettings.getString(DisplaySettings.DLAA_RR_PRESET, "F"));
        rrExposed = !DisplaySettings.getString(DisplaySettings.DLAA_RR_EXPOSURE, "exposed").equalsIgnoreCase("raw");
        String raw = DisplaySettings.getString(DisplaySettings.DLAA_RR_RAW, "reflections,shadows").toLowerCase(Locale.ROOT);
        rrRawReflections = raw.contains("reflections");
        rrRawShadows = raw.contains("shadows");
        String test = DisplaySettings.getString(DisplaySettings.DLAA_RR_TEST, "").toLowerCase(Locale.ROOT);
        boolean ownParameters = test.contains("own-parameters");
        if (ownParameters != rrOwnParameters) {
            featureIsRr = false; // made again with the other parameters
        }
        rrNoHitDistance = test.contains("no-hit-distance");
        rrOwnParameters = ownParameters;
        rrHitDistanceBands = test.contains("hit-distance-bands");
        HandWatch.configure(DisplaySettings.getString(DisplaySettings.DLAA_HAND_WATCH, ""));
        if (rrUnavailableNoDll && Files.isRegularFile(dllFolder().resolve(DlssFinder.RR_DLL))) {
            rrUnavailable = null; // tried again when it starts
            rrUnavailableNoDll = false;
        }
        jitterSign = parseJitterSign(DisplaySettings.getString(DisplaySettings.DLAA_JITTER_SIGN, System.getProperty(JITTER_SIGN_PROPERTY, "1,1")));
        objectMotion = !DisplaySettings.getString(DisplaySettings.DLAA_OBJECT_MOTION, System.getProperty(OBJECT_MOTION_PROPERTY, "true"))
                .trim().equalsIgnoreCase("false");
        EntityMotion.cubes(!DisplaySettings.getString(DisplaySettings.DLAA_CUBE_MOTION, "true").trim().equalsIgnoreCase("false"));
        reactiveMask = !DisplaySettings.getString(DisplaySettings.DLAA_REACTIVE_MASK, System.getProperty(REACTIVE_MASK_PROPERTY, "true"))
                .trim().equalsIgnoreCase("false");
        failed = false;
        reset = true;
        missedFrames = 0;
        profile = null;
        packProblem = null;
        renderScaleSetting = 1.0f;
        try {
            // (Also with DLAA off: the pack's own upscaling renders at the Bridge's render size.)
            profile = PackProfile.current();
            packProblem = profile == null ? NO_PROFILE : profile.optionProblem();
            renderScaleSetting = profile == null ? 1.0f : profile.renderScale();
        } catch (IOException | RuntimeException e) {
            packProblem = "its " + PackProfile.FILE + " could not be read: " + e;
        }
        if (!wanted) {
            profile = null;
            return;
        }
        String jitter = jitterSign[0] != 1.0f || jitterSign[1] != 1.0f ? ", jitter sign " + (int) jitterSign[0] + "," + (int) jitterSign[1] : "";
        if (profile == null && packProblem != null) {
            LOG.info("DLAA: not used with the shader pack {}, {}", Iris.getCurrentPackName(), packProblem);
        } else if (packProblem != null) {
            LOG.info("DLAA: off with these {} options, {}", Iris.getCurrentPackName(), packProblem);
        } else if (debugView != null) {
            LOG.info("DLAA: on for this shader pack, debug view {} (DLSS itself not used), {}{}", debugView.name().toLowerCase(Locale.ROOT),
                    renderScaleSetting < 1.0f ? "upscaling from " + Math.round(snappedScale() * 100.0f) + "% of the window" : "full resolution", jitter);
        } else {
            if (rayReconstructionWanted) {
                LOG.info("DLAA: DLSS Ray Reconstruction asked for (experimental): preset {}, {} colour, left noisy for it: {}{}",
                        rrPresetName(rrPreset), rrExposed ? "exposed" : "raw", rrRawReflections || rrRawShadows
                                ? (rrRawReflections ? "rough reflections" : "") + (rrRawReflections && rrRawShadows ? ", " : "") + (rrRawShadows ? "soft shadows" : "")
                                : "nothing", profile != null && !profile.hasRayReconstructionGuides() ? " (this pack hands over no guides: DLSS runs)" : "");
            }
            LOG.info("DLAA: on for this shader pack, engine {}, DLSS preset {}{}, {}{}{}{}", engine, presetName(preset),
                    presetName.equalsIgnoreCase(presetName(preset)) || presetName.isEmpty() ? "" : " (set to " + presetName + ")",
                    renderScaleSetting < 1.0f ? "upscaling from " + Math.round(snappedScale() * 100.0f) + "% of the window" : "full resolution",
                    extraMipBias != 0.0f ? ", mip bias " + extraMipBias + " more (testing)" : "",
                    autoExposureTest ? ", DLSS auto exposure (testing)" : "", jitter + (fsr3Debug ? " (FSR's debug view)" : ""));
        }
    }

    /**
     * Start of a level frame, before the pack's uniforms update: decides whether DLSS runs and picks the jitter.
     * {@code frameCounter} is the pack's frameCounter, so the jitter repeats with the pack's own frame sequence.
     */
    public static void beginFrame(int frameCounter) {
        ObjectMotion.beginFrame(((LevelRendererTicksAccessor) Minecraft.getInstance().levelRenderer).create_galore_bridge$ticks());
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
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        displayWidth = Math.max(1, target.width);
        displayHeight = Math.max(1, target.height);
        renderWidth = renderSize(displayWidth);
        renderHeight = renderSize(displayHeight);

        boolean wasActive = active;
        if (wanted && profile != null && packProblem != null) {
            notice("pack:" + packProblem, "DLAA is off with these shader pack options", packProblem);
        }
        active = wanted && profile != null && packProblem == null && !failed && !shuttingDown && ensureReady();
        boolean rrPossible = active && rayReconstructionWanted && profile.hasRayReconstructionGuides();
        rrRunning = rrPossible && debugView == null && !fsr3 && rrUnavailable == null;
        rrMode = rrPossible && (rrRunning || debugView != null);
        EntityMotion.beginFrame(active, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (!active) {
            return;
        }
        int index = Math.floorMod(frameCounter, fsr3 && debugView == null ? Fsr3Upscaler.phases(renderWidth, displayWidth)
                : dlssPhases(renderWidth, displayWidth)) + 1;
        jitterX = halton(index, 2) - 0.5f;
        jitterY = halton(index, 3) - 0.5f;
        if (debugView == DlssPass.View.RESIDUAL) {
            jitterX = jitterY = 0.0f; // so last frame's colour lines up exactly where nothing moved
        }

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

    /** End of a level frame: what draws after it (screens, the inventory's player) is no part of the frame's motion. */
    public static void endFrame() {
        EntityMotion.endFrame();
    }

    /** False while testing without the motion of things that move by themselves (cg_dlss_object_motion). */
    public static boolean objectMotion() {
        return objectMotion;
    }

    /**
     * For the DLAA test kit: saves the next frame's DLSS inputs as numpy arrays in {@code dir} ({@code name}-color.npy,
     * -motion.npy, -depth.npy; see {@link DebugDump}), and outside the debug views DLSS's or FSR's output too
     * (-output.npy).
     */
    public static void dumpNextFrame(String dir, String name) {
        dumpRequest = new String[] {dir, name};
    }

    /** True in the residual test view: the pack hands over surface colours instead of the lit scene (cg_dlss_residual_albedo). */
    public static boolean residualAlbedo() {
        return active && debugView == DlssPass.View.RESIDUAL;
    }

    /** True while the pack should hand its frame to DLSS (cg_dlss_active). */
    public static boolean shaderActive() {
        return active;
    }

    /** True while the pack should write Ray Reconstruction's guides (cg_rr_active). */
    public static boolean rayReconstructionGuides() {
        return active && rrMode;
    }

    /** True while the pack should hand over its colour times its exposure (cg_rr_exposed). */
    public static boolean rayReconstructionExposed() {
        return active && rrRunning && rrExposed;
    }

    /** True while the pack leaves its rough reflections noisy for Ray Reconstruction (cg_rr_raw_reflections). */
    public static boolean rayReconstructionRawReflections() {
        return active && rrMode && rrRawReflections;
    }

    /** True while the pack leaves its soft shadows noisy for Ray Reconstruction (cg_rr_raw_shadows). */
    public static boolean rayReconstructionRawShadows() {
        return active && rrMode && rrRawShadows;
    }

    /** For the test kit: the pack the render resolution goes into, its upscaling switch and its render scale option. */
    public static String[] renderScaleOptions() {
        RenderScaleTarget target = RenderScaleTarget.current();
        return new String[] {target.pack(), target.upscaling(), target.scale()};
    }

    /** This frame's jitter in pixels, -0.5..0.5 (cg_dlss_jitter_x). */
    public static float jitterX() {
        return active ? jitterX : 0.0f;
    }

    public static float jitterY() {
        return active ? jitterY : 0.0f;
    }

    /** The render size over the window's, per axis (taau_render_scale): the pack's render scale in whole pixels. */
    public static org.joml.Vector2f renderScale() {
        return new org.joml.Vector2f((float) renderWidth / displayWidth, (float) renderHeight / displayHeight);
    }

    /** The textures' mip bias (cg_mip_bias): log2 of the render scale as the pack has it, plus dlaaMipBias while DLSS or FSR runs. */
    public static float mipBias() {
        return (float) (Math.log((double) renderWidth / displayWidth) / Math.log(2.0)) + (active && debugView == null ? extraMipBias : 0.0f);
    }

    /** The render size for a window size: the pack's render scale in whole pixels (2/3 for 0.67 and 1/3 for 0.33). */
    private static int renderSize(int display) {
        return renderScaleSetting >= 1.0f ? display : Math.max(1, Math.min(display, Math.round(display * snappedScale())));
    }

    private static float snappedScale() {
        float scale = renderScaleSetting;
        return Math.abs(scale - 2.0f / 3.0f) < 0.005f ? 2.0f / 3.0f : Math.abs(scale - 1.0f / 3.0f) < 0.005f ? 1.0f / 3.0f : scale;
    }

    /** NVIDIA: a jitter period of at least 8 times the scale factor squared; never shorter than {@link #PHASES}. */
    private static int dlssPhases(int render, int display) {
        double factor = (double) display / render;
        return Math.max(PHASES, (int) Math.ceil(8.0 * factor * factor));
    }

    /** DLSS's mode for a render width (it picks the preset per mode): DLAA at full size, else the nearest standard one. */
    private static int perfQuality(int render, int display) {
        float ratio = (float) render / display;
        if (ratio >= 0.99f) {
            return NgxLibrary.PERF_QUALITY_DLAA;
        }
        if (ratio >= 0.62f) {
            return NgxLibrary.PERF_QUALITY_MAX_QUALITY;
        }
        if (ratio >= 0.54f) {
            return NgxLibrary.PERF_QUALITY_BALANCED;
        }
        return ratio >= 0.42f ? NgxLibrary.PERF_QUALITY_MAX_PERF : NgxLibrary.PERF_QUALITY_ULTRA_PERFORMANCE;
    }

    /** Like Performance (1280x720 to 2560x1440, 50%), for the log and the settings screen. */
    static String modeName(int render, int display) {
        if (render >= display) {
            return "full resolution";
        }
        String name = switch (Math.round(100.0f * render / display)) {
            case 67 -> "Quality";
            case 58 -> "Balanced";
            case 50 -> "Performance";
            case 33 -> "Ultra Performance";
            default -> "custom";
        };
        return name + ", " + Math.round(100.0f * render / display) + "%";
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
            DlssPass.Guides guides = null;
            if (rrMode) {
                java.util.List<String> names = profile.rrGuideImages();
                guides = new DlssPass.Guides(image(imageIds, names.get(0)), image(imageIds, names.get(1)), image(imageIds, names.get(2)),
                        names.size() > 3 ? image(imageIds, names.get(3)) : 0);
            }
            DlssPass.Inputs in = DlssPass.inputs(DlssPass.boundColorTexture(profile.colorAttachment()),
                    image(imageIds, profile.motionImage()), image(imageIds, profile.depthImage()),
                    image(imageIds, profile.exposureImage()),
                    profile.maskImage() != null && reactiveMask ? image(imageIds, profile.maskImage()) : 0,
                    guides, Dlss::renderSize);
            DlssPass.hitDistanceBands = rrHitDistanceBands;
            if (debugView != null) {
                debugPass.ensureSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), guides != null);
                debugPass.copyAndEvaluate(in, () -> {});
                dumpIfAsked(debugPass, false);
                debugPass.writeBack(profile, debugView);
                return;
            }
            if (fsr3) {
                runFsr3(in);
                return;
            }
            if (rrRunning && runRayReconstruction(in)) {
                return;
            }
            boolean newSize = pass.needsSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), false);
            int usePreset = presetFellBack ? PRESET_DEFAULT : preset;
            int mode = perfQuality(in.renderWidth(), in.width());
            // NVIDIA: DLSS takes the game's exposure with J and K only, L and M always meter it themselves (asked to
            // here as well). The pack's exposure is the one its tone mapping uses, as DLSS wants.
            boolean autoExposure = usePreset == PRESET_L || usePreset == PRESET_M || autoExposureTest;
            int flags = NgxLibrary.FLAG_IS_HDR | NgxLibrary.FLAG_MV_LOW_RES | NgxLibrary.FLAG_DEPTH_INVERTED
                    | (autoExposure ? NgxLibrary.FLAG_AUTO_EXPOSURE : 0);
            if (newSize || flags != featureFlags || usePreset != featurePreset || featureIsRr) {
                featureFlags = -1;
                featureIsRr = false;
                dlssGuard.starting();
                // DLSS lets go of the textures it ran on before they are freed for a new size.
                ngx.release();
                pass.ensureSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), false);
                int result = ngx.create(device.context(), in.renderWidth(), in.renderHeight(), in.width(), in.height(),
                        mode, flags, usePreset);
                ngx.drainLog();
                if (NgxLibrary.failed(result) && usePreset != PRESET_DEFAULT) {
                    LOG.warn("DLAA: DLSS refused preset {} ({}), running its default preset", presetName(usePreset), NgxLibrary.describe(result));
                    presetFellBack = true;
                    usePreset = PRESET_DEFAULT;
                    autoExposure = autoExposureTest;
                    flags = NgxLibrary.FLAG_IS_HDR | NgxLibrary.FLAG_MV_LOW_RES | NgxLibrary.FLAG_DEPTH_INVERTED
                            | (autoExposure ? NgxLibrary.FLAG_AUTO_EXPOSURE : 0);
                    result = ngx.create(device.context(), in.renderWidth(), in.renderHeight(), in.width(), in.height(),
                            mode, flags, usePreset);
                    ngx.drainLog();
                }
                if (NgxLibrary.failed(result)) {
                    throw new IllegalStateException("DLSS could not start at " + sizes(in) + ": " + NgxLibrary.describe(result));
                }
                featureFlags = flags;
                featurePreset = usePreset;
                reset = true;
                LOG.info("DLAA: DLSS running at {}, preset {}{}", sizes(in), presetName(usePreset),
                        autoExposure ? ", DLSS meters the exposure" : ", the pack's exposure");
            }
            boolean resetNow = reset;
            long exposure = (featureFlags & NgxLibrary.FLAG_AUTO_EXPOSURE) != 0 ? 0L : pass.exposureResource();
            int[] result = new int[1];
            pass.copyAndEvaluate(in, () -> result[0] = ngx.evaluate(device, pass.colorResource(), pass.outputResource(),
                    pass.depthResource(), pass.motionResource(), exposure,
                    jitterX * jitterSign[0], jitterY * jitterSign[1], 1.0f, 1.0f, resetNow, in.renderWidth(), in.renderHeight(), frameTimeMs));
            ngx.drainLog();
            if (NgxLibrary.failed(result[0])) {
                throw new IllegalStateException("DLSS failed: " + NgxLibrary.describe(result[0]));
            }
            reset = false;
            dlssGuard.ran();
            FrameGen.upscaled(in.motionTexture(), in.depthTexture(), in.width(), in.height(), in.renderWidth(), in.renderHeight(),
                    jitterX * jitterSign[0], jitterY * jitterSign[1], resetNow, frameTimeMs);
            dumpIfAsked(pass, true);
            HandWatch.measure(pass, "dlss", false, resetNow, jitterX, jitterY, frameTimeMs);
            // (Exposed only in the frame Ray Reconstruction failed to start in: the pack had already multiplied it.)
            boolean unexpose = rrRunning && rrExposed;
            Markers.around("Create Galore DLAA: write back", () -> pass.writeBack(profile, DlssPass.View.RESULT, unexpose));
        } catch (Throwable t) {
            failed = true;
            guard().stopped();
            LOG.error("DLAA: stopped, the pack's own anti-aliasing is back from the next frame (reloading shaders tries DLAA again)", t);
            notice("failed:" + t, "DLAA stopped after an error", "The shader pack's own anti-aliasing is back. Reloading shaders (F8) tries again; the log says what went wrong.");
        }
    }

    /**
     * Runs DLSS Ray Reconstruction in DLSS's place. False if it could not start: it stays off for the session (the
     * settings screen says why) and DLSS takes this frame.
     */
    private static boolean runRayReconstruction(DlssPass.Inputs in) {
        int flags = NgxLibrary.FLAG_IS_HDR | NgxLibrary.FLAG_MV_LOW_RES | NgxLibrary.FLAG_DEPTH_INVERTED;
        boolean newSize = pass.needsSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), true);
        if (newSize || !featureIsRr || rrPreset != featurePreset) {
            featureFlags = -1;
            featureIsRr = false;
            dlssGuard.starting();
            ngx.release();
            pass.ensureSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), true);
            // Depth: the pack's DLSS depth, reversed and infinite, which is a hardware depth for viewToClip below.
            int result = ngx.createRayReconstruction(device.context(), in.renderWidth(), in.renderHeight(), in.width(), in.height(),
                    perfQuality(in.renderWidth(), in.width()), flags, rrPreset, NgxLibrary.DEPTH_HARDWARE, rrOwnParameters);
            ngx.drainLog();
            if (NgxLibrary.failed(result)) {
                rrUnavailable = "it did not start at " + sizes(in) + ": " + NgxLibrary.describe(result);
                LOG.warn("DLAA: DLSS Ray Reconstruction {}; DLSS runs instead", rrUnavailable);
                notice("rr:" + rrUnavailable, "Ray Reconstruction can't run", rrUnavailable + ". DLSS runs instead.");
                reset = true;
                return false;
            }
            featureFlags = flags;
            featurePreset = rrPreset;
            featureIsRr = true;
            reset = true;
            LOG.info("DLAA: DLSS Ray Reconstruction running at {}, preset {}, {} colour{}{}{}", sizes(in), rrPresetName(rrPreset),
                    rrExposed ? "exposed" : "raw", rrNoHitDistance ? ", test: no hit distance" : "",
                    rrOwnParameters ? ", test: own NGX parameters" : "",
                    rrHitDistanceBands ? ", test: hit distance bands " + DlssPass.HIT_DISTANCE_BANDS_TEXT : "");
        }
        boolean resetNow = reset;
        float[] worldToView = new float[16];
        CapturedRenderingState.INSTANCE.getGbufferModelView().get(worldToView);
        float[] viewToClip = viewToClip(CapturedRenderingState.INSTANCE.getGbufferProjection());
        int[] result = new int[1];
        pass.copyAndEvaluate(in, () -> {
            // After the copy, which tells whether this frame has a hit distance.
            long[] resources = pass.rayReconstructionResources();
            if (rrNoHitDistance) {
                resources[7] = 0L;
            }
            result[0] = ngx.evaluateRayReconstruction(device, resources, jitterX * jitterSign[0], jitterY * jitterSign[1], 1.0f, 1.0f,
                    frameTimeMs, worldToView, viewToClip, resetNow, in.renderWidth(), in.renderHeight());
        });
        ngx.drainLog();
        if (NgxLibrary.failed(result[0])) {
            throw new IllegalStateException("DLSS Ray Reconstruction failed: " + NgxLibrary.describe(result[0]));
        }
        reset = false;
        dlssGuard.ran();
        FrameGen.upscaled(in.motionTexture(), in.depthTexture(), in.width(), in.height(), in.renderWidth(), in.renderHeight(),
                jitterX * jitterSign[0], jitterY * jitterSign[1], resetNow, frameTimeMs);
        dumpIfAsked(pass, true);
        HandWatch.measure(pass, "rr", rrExposed, resetNow, jitterX, jitterY, frameTimeMs);
        Markers.around("Create Galore DLAA: write back", () -> pass.writeBack(profile, DlssPass.View.RESULT, rrExposed));
        return true;
    }

    /** Minecraft's near plane, which the pack's DLSS depth (near over clip w) is made with. */
    private static final float NEAR = 0.05f;

    /**
     * The view-to-clip matrix Ray Reconstruction gets, in OpenGL's column-major layout (NGX reads it as row major for a
     * row vector). x and w as the game projects (view bobbing included), y flipped because the shared textures keep
     * OpenGL's row order (row 0 at the bottom, which DirectX takes as the top), and z the constant near plane, so z over
     * w is the pack's DLSS depth exactly.
     */
    static float[] viewToClip(Matrix4fc projection) {
        float[] m = new float[16];
        for (int column = 0; column < 4; column++) {
            m[column * 4] = projection.get(column, 0);
            m[column * 4 + 1] = -projection.get(column, 1);
            m[column * 4 + 2] = column == 3 ? NEAR : 0.0f;
            m[column * 4 + 3] = projection.get(column, 3);
        }
        return m;
    }

    private static void runFsr3(DlssPass.Inputs in) {
        boolean resized = fsrPass.ensureSize(in.renderWidth(), in.renderHeight(), in.width(), in.height(), false);
        if (resized) {
            fsrGuard.starting();
        }
        if (fsr.ensureSize(in.renderWidth(), in.renderHeight(), in.width(), in.height()) || resized) {
            reset = true;
            LOG.info("DLAA: FSR 3.1 running at {}", sizes(in));
        }
        boolean resetNow = reset;
        Matrix4fc projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        fsrPass.copyAndEvaluate(in, () -> fsr.dispatch(fsrPass.colorTexture(), fsrPass.depthTexture(), fsrPass.motionTexture(),
                fsrPass.exposureTexture(), fsrPass.maskTexture(), fsrPass.outputTexture(), jitterX * jitterSign[0], jitterY * jitterSign[1], resetNow,
                frameTimeMs, projection, fsr3Debug));
        reset = false;
        fsrGuard.ran();
        FrameGen.upscaled(in.motionTexture(), in.depthTexture(), in.width(), in.height(), in.renderWidth(), in.renderHeight(),
                jitterX * jitterSign[0], jitterY * jitterSign[1], resetNow, frameTimeMs);
        dumpIfAsked(fsrPass, true);
        HandWatch.measure(fsrPass, "fsr3", false, resetNow, jitterX, jitterY, frameTimeMs);
        Markers.around("Create Galore DLAA: write back", () -> fsrPass.writeBack(profile, DlssPass.View.RESULT));
    }

    /** Like 2560x1440 (full resolution), or 1280x720 to 2560x1440 (Performance, 50%). */
    private static String sizes(DlssPass.Inputs in) {
        return (in.renderWidth() == in.width() && in.renderHeight() == in.height() ? "" : in.renderWidth() + "x" + in.renderHeight() + " to ")
                + in.width() + "x" + in.height() + " (" + modeName(in.renderWidth(), in.width()) + ")";
    }

    private static void dumpIfAsked(DlssPass from, boolean withOutput) {
        String[] dump = dumpRequest;
        if (dump != null) {
            dumpRequest = null;
            from.dump(java.nio.file.Path.of(dump[0]), dump[1], withOutput);
        }
    }

    private static int image(ToIntFunction<String> imageIds, String name) {
        int id = imageIds.applyAsInt(name);
        if (id <= 0) {
            throw new IllegalStateException("the pack has no image " + name);
        }
        return id;
    }

    /** Readies the engine the setting picks (Auto: DLSS where it can run, else FSR 3). False if none can run. */
    private static boolean ensureReady() {
        if (debugView != null) {
            if (debugPass == null) {
                debugPass = new DlssPass(null);
            }
            return true;
        }
        switch (engine) {
            case "fsr3" -> {
                fsr3 = true;
                return ensureFsr3();
            }
            case "dlss" -> {
                fsr3 = false;
                if (ensureDlss()) {
                    return true;
                }
                notice("dlss:" + unavailable, "DLAA is off: DLSS can't run", noticeText(unavailable));
                return false;
            }
            default -> {
                if (ensureDlss()) {
                    fsr3 = false;
                    return true;
                }
                if (!fsr3) {
                    fsr3 = true;
                    LOG.info("DLAA: Auto uses AMD FSR 3.1, DLSS can't run here: {}", unavailable);
                    // No notice on other graphics cards (not NVIDIA's): FSR 3 is what Auto is for there.
                    if (vendorIsNvidia()) {
                        notice("auto:" + unavailable, "DLAA: AMD FSR 3.1 in place of DLSS", noticeText(unavailable));
                    }
                }
                return ensureFsr3();
            }
        }
    }

    /** Loads NGX and opens the DirectX device the first time DLSS is needed. False (once logged) if it cannot run. */
    private static boolean ensureDlss() {
        if (pass != null) {
            return true;
        }
        if (unavailable != null) {
            return false;
        }
        if (guards() && dlssGuard.crashed()) {
            unavailable = "the game closed while DLSS was starting last time, so it stays off until you pick Try again on the Upscaling & Display screen";
            unavailableNoDll = false;
            LOG.warn("DLAA: DLSS not started: {}", unavailable);
            return false;
        }
        try {
            dlssGuard.starting();
            open();
            return true;
        } catch (Throwable t) {
            dlssGuard.stopped();
            unavailable = t.getMessage() != null ? t.getMessage() : t.toString();
            LOG.warn("DLAA: DLSS not available: {}", unavailable);
            LOG.debug("DLAA: start-up failure", t);
            closeDlss();
            return false;
        }
    }

    /** Compiles FSR 3's passes the first time it is needed. False (once logged) if it cannot run. */
    private static boolean ensureFsr3() {
        if (fsr != null) {
            return true;
        }
        if (fsr3Unavailable == null && guards() && fsrGuard.crashed()) {
            fsr3Unavailable = "the game closed while FSR 3 was starting last time, so it stays off until you pick Try again on the Upscaling & Display screen";
            LOG.warn("DLAA: FSR 3 not started: {}", fsr3Unavailable);
        }
        if (fsr3Unavailable != null) {
            notice("fsr3:" + fsr3Unavailable, "DLAA is off: FSR 3 can't run", fsr3Unavailable);
            return false;
        }
        try {
            fsrGuard.starting();
            String missing = Fsr3Upscaler.missingFunctions();
            if (missing != null) {
                throw new IllegalStateException("FSR 3 needs OpenGL 4.5, this one lacks " + missing);
            }
            fsr = new Fsr3Upscaler();
            fsrPass = new DlssPass(null);
            fsrRenderer = glGetString(GL_RENDERER);
            LOG.info("DLAA: AMD FSR 3.1 ready on {} (OpenGL {})", fsrRenderer, glGetString(org.lwjgl.opengl.GL11C.GL_VERSION));
            return true;
        } catch (Throwable t) {
            fsrGuard.stopped();
            fsr3Unavailable = t.getMessage() != null ? t.getMessage() : t.toString();
            LOG.warn("DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: {}", fsr3Unavailable);
            LOG.debug("DLAA: FSR 3 start-up failure", t);
            closeFsr3();
            return false;
        }
    }

    /** Makes the start guards once the game folder is known. */
    private static boolean guards() {
        if (dlssGuard == null) {
            Path folder = FMLPaths.GAMEDIR.get().resolve(FOLDER);
            dlssGuard = new StartGuard(folder, "DLSS");
            fsrGuard = new StartGuard(folder, "FSR3");
        }
        return true;
    }

    private static StartGuard guard() {
        guards();
        return fsr3 ? fsrGuard : dlssGuard;
    }

    private static boolean vendorIsNvidia() {
        String vendor = glGetString(GL_VENDOR);
        return vendor != null && vendor.toLowerCase(Locale.ROOT).contains("nvidia");
    }

    /** For a reason DLSS can't run, where the game runs on another graphics card than NVIDIA's: which one. */
    private static String onOtherCard() {
        return vendorIsNvidia() ? "" : "; the game runs on " + glGetString(GL_VENDOR) + " " + glGetString(GL_RENDERER)
                + ", and NVIDIA DLSS is made for NVIDIA RTX cards";
    }

    /**
     * Why NVIDIA's NGX said Ray Reconstruction can't run here, null if it said it can or hasn't been asked yet (it is
     * asked whenever DLSS starts). The Upscaling tab greys Ray Reconstruction out on its answer, as frame generation
     * goes by NVIDIA's Streamline.
     */
    public static String rayReconstructionRefused() {
        return rrRefused;
    }

    /** A notice in the corner, once per reason and session: the log has the details. */
    private static void notice(String key, String title, String text) {
        if (!noticesShown.add(key)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(minecraft, NOTICE,
                net.minecraft.network.chat.Component.literal(title), net.minecraft.network.chat.Component.literal(text)));
    }

    private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId NOTICE =
            new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(10000L);

    private static String noticeText(String reason) {
        return unavailableNoDll && vendorIsNvidia()
                ? "nvngx_dlss.dll is missing: put it in the DLSS folder (Mods, Create Galore Bridge, Config, Open DLSS folder) and reload shaders (F8)."
                : reason;
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
        // No card or vendor check of the pack's own: NVIDIA's NGX says whether DLSS and Ray Reconstruction run here.
        Path folder = FMLPaths.GAMEDIR.get().resolve(FOLDER);
        Path dllFolder = dllFolder();
        Path dll = dllFolder.resolve(DLL);
        unavailableNoDll = !Files.isRegularFile(dll);
        if (unavailableNoDll) {
            throw new IllegalStateException("there is no " + dll + " (not part of the pack: bring your own, DLSS "
                    + TESTED_DLL_VERSION + " or newer, see the Upscaling & Display screen)" + onOtherCard());
        }
        Files.createDirectories(folder.resolve("ngx"));
        ngx = NgxLibrary.load(folder);
        device = onDirectX12 ? DlssDevice12.open(ngx) : DlssDevice.open();
        int result = ngx.init(PROJECT_ID, ENGINE_VERSION, folder.resolve("ngx"), dllFolder, device, NgxLibrary.LOG_ON);
        ngx.drainLog();
        if (NgxLibrary.failed(result)) {
            rrRefused = "NVIDIA NGX did not start: " + NgxLibrary.describe(result) + onOtherCard();
            throw new IllegalStateException(rrRefused);
        }
        int[] support = ngx.query();
        if (support == null) {
            throw new IllegalStateException("NGX could not say whether DLSS runs here");
        }
        if (support[0] == 0) {
            rrRefused = support[1] != 0 ? "DLSS needs NVIDIA driver " + support[2] + "." + support[3] + " or newer"
                    : "DLSS does not run on this graphics card (" + NgxLibrary.describe(support[4]) + ")";
            throw new IllegalStateException(rrRefused);
        }
        checkRayReconstruction(dllFolder);
        pass = new DlssPass(device);
        adapterName = device.adapterName();
        LOG.info("DLAA: NGX ready on {} (its own DirectX {} device{}; OpenGL on {}), nvngx_dlss.dll {} from {} (tested with {})",
                adapterName, onDirectX12 ? "12" : "11", onDirectX12 ? ", for Ray Reconstruction" : "", glGetString(GL_RENDERER),
                DllVersion.read(dll), dllFolder, TESTED_DLL_VERSION);
    }

    /** Whether Ray Reconstruction can run here ({@link #rrUnavailable} null), once NGX has started. */
    private static void checkRayReconstruction(Path dllFolder) {
        Path dll = dllFolder.resolve(DlssFinder.RR_DLL);
        rrUnavailableNoDll = !Files.isRegularFile(dll);
        if (rrUnavailableNoDll) {
            rrUnavailable = "there is no " + dll + " (bring your own like nvngx_dlss.dll, or press Find on this PC)";
        } else {
            int[] support = ngx.queryRayReconstruction();
            if (support == null) {
                rrUnavailable = "NGX could not say whether it runs here";
            } else if (support[0] == 0) {
                rrUnavailable = support[1] != 0 ? "it needs NVIDIA driver " + support[2] + "." + support[3] + " or newer"
                        : "NGX says it does not run here (" + NgxLibrary.describe(support[4]) + ")";
                rrRefused = rrUnavailable;
            } else {
                rrUnavailable = null;
                rrRefused = null;
            }
        }
        LOG.info("DLAA: DLSS Ray Reconstruction {}{}", rrUnavailable == null ? "can run, nvngx_dlssd.dll " + DllVersion.read(dll)
                + " (made with " + TESTED_RR_DLL_VERSION + ")" : "can't run: " + rrUnavailable,
                rayReconstructionWanted ? "" : " (not asked for)");
    }

    private static void shutdown(GameShuttingDownEvent event) {
        shuttingDown = true;
        if (dlssGuard != null) {
            dlssGuard.stopped();
            fsrGuard.stopped();
        }
        if (RenderSystem.isOnRenderThread()) {
            closeAll();
        }
    }

    private static void closeAll() {
        try {
            if (debugPass != null) {
                debugPass.close();
            }
            closeFsr3();
        } catch (Throwable t) {
            LOG.warn("DLAA: could not close cleanly: {}", t.toString());
        } finally {
            debugPass = null;
            fsr = null;
            fsrPass = null;
            active = false;
        }
        closeDlss();
    }

    private static void closeDlss() {
        try {
            // NGX first: its DLSS feature ran on the pass's textures.
            if (ngx != null) {
                ngx.shutdown();
                ngx.drainLog();
            }
            if (pass != null) {
                pass.close();
            }
            if (device != null) {
                device.close();
            }
        } catch (Throwable t) {
            LOG.warn("DLAA: could not close cleanly: {}", t.toString());
        } finally {
            pass = null;
            ngx = null;
            device = null;
            featureFlags = -1;
            featurePreset = -1;
            featureIsRr = false;
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

    /**
     * default (K, at every render scale), or a letter: DLSS numbers its presets A = 1 to Z = 26. Any letter goes to
     * DLSS, which runs its own default for one it has no preset for (DLSS 310.9 has E and F, old, and J to M; A to D were removed, G to I, N and O
     * are spare), so a newer nvngx_dlss.dll's presets can be tried; one DLSS refuses runs as default too.
     */
    private static int parsePreset(String name) {
        String value = name.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("DEFAULT")) {
            return PRESET_K;
        }
        if (value.length() == 1 && value.charAt(0) >= 'A' && value.charAt(0) <= 'Z') {
            return value.charAt(0) - 'A' + 1;
        }
        LOG.warn("DLAA: unknown dlaaPreset {}, using default, K (choose default or a letter: J, K, L, M, ...)", name);
        return PRESET_K;
    }

    /** F (the default), E, D, or default for NVIDIA's own pick. */
    private static int parseRrPreset(String name) {
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "D" -> NgxLibrary.RR_PRESET_D;
            case "E" -> NgxLibrary.RR_PRESET_E;
            case "DEFAULT" -> 0;
            case "F", "" -> NgxLibrary.RR_PRESET_F;
            default -> {
                LOG.warn("DLAA: unknown dlaaRrPreset {}, using F (choose F, E, D or default)", name);
                yield NgxLibrary.RR_PRESET_F;
            }
        };
    }

    private static String rrPresetName(int preset) {
        return preset == 0 ? "default" : String.valueOf((char) ('A' + preset - 1));
    }

    private static String presetName(int preset) {
        return preset >= 1 && preset <= 26 ? String.valueOf((char) ('A' + preset - 1)) : "default";
    }

    /** True if the tested DLSS has a preset by this letter (E and F, which are old, and J to M). */
    static boolean knownPreset(char letter) {
        return letter == 'E' || letter == 'F' || (letter >= 'J' && letter <= 'M');
    }

    // For the settings screen (DlaaScreen).

    /**
     * Where DLSS reads nvngx_dlss.dll from (and frame generation nvngx_dlssg.dll): create-galore-dlss in the game folder
     * (or the dllDir test property).
     */
    public static Path dllFolder() {
        String dllDir = System.getProperty(DLL_DIR_PROPERTY);
        return dllDir != null && !dllDir.isBlank() ? Path.of(dllDir) : FMLPaths.GAMEDIR.get().resolve(FOLDER);
    }

    /**
     * DLSS Frame Generation is starting (see FrameGen): puts a nvngx_dlssg.dll that waited for the old one in place.
     * The version of the one in the DLSS folder ({@link #frameGenerationDllVersion()}); NVIDIA's folder is read only
     * by the settings screen's Find on this PC.
     */
    public static String readyFrameGenerationDll() {
        DlssFinder.applyPending();
        return frameGenerationDllVersion();
    }

    /** The version of the nvngx_dlssg.dll in the DLSS folder, "unknown" if it has none, or null if there is none. */
    public static String frameGenerationDllVersion() {
        Path dll = dllFolder().resolve(DlssFinder.FG_DLL);
        if (!Files.isRegularFile(dll)) {
            return null;
        }
        String version = DllVersion.read(dll);
        return version != null ? version : "unknown";
    }

    /** The nvngx_dlssg.dll version frame generation was tested with. */
    public static String testedFrameGenerationDllVersion() {
        return TESTED_FG_DLL_VERSION;
    }

    /** The version of the nvngx_dlssd.dll there (Ray Reconstruction), "unknown" if it has none, or null if there is none. */
    static String rayReconstructionDllVersion() {
        Path dll = dllFolder().resolve(DlssFinder.RR_DLL);
        if (!Files.isRegularFile(dll)) {
            return null;
        }
        String version = DllVersion.read(dll);
        return version != null ? version : "unknown";
    }

    /** The version of the nvngx_dlss.dll there, "unknown" if it has none, or null if there is none. */
    static String foundDllVersion() {
        Path dll = dllFolder().resolve(DLL);
        if (!Files.isRegularFile(dll)) {
            return null;
        }
        String version = DllVersion.read(dll);
        return version != null ? version : "unknown";
    }

    /** True if an engine took the game down while starting and stays off until {@link #tryAgain()}. */
    static boolean crashed() {
        guards();
        return dlssGuard.crashed() || fsrGuard.crashed();
    }

    /** Clears the crash marks so both engines may start again (on the next shader reload). */
    static void tryAgain() {
        guards();
        dlssGuard.clear();
        fsrGuard.clear();
        if (unavailable != null && unavailable.startsWith("the game closed")) {
            unavailable = null;
        }
        if (fsr3Unavailable != null && fsr3Unavailable.startsWith("the game closed")) {
            fsr3Unavailable = null;
        }
    }

    /** The settings were saved: makes the Photon copy if it is now needed, and reloads shaders if a DLAA pack is in use. */
    static void settingsChanged() {
        DisplaySettings.reload();
        PhotonPatcher.refresh();
        if (Iris.getIrisConfig().areShadersEnabled() && (PhotonPatcher.PATCHED_NAME.equals(Iris.getCurrentPackName()) || hasProfile())) {
            try {
                Iris.reload();
            } catch (IOException | RuntimeException e) {
                LOG.warn("DLAA: could not reload the shaders: {}", e.toString());
            }
        }
    }

    private static boolean hasProfile() {
        try {
            return PackProfile.current() != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** What DLAA does right now, in a line for the settings screen. */
    static String status() {
        if (!wanted) {
            return "Off: the shader pack keeps its own anti-aliasing.";
        }
        if (!Iris.getIrisConfig().areShadersEnabled() || Iris.getCurrentPack().isEmpty() || NO_PROFILE.equals(packProblem)) {
            return "Waiting: pick a shader pack made for DLAA, such as \"" + PhotonPatcher.PATCHED_NAME + "\", in Iris's shader menu.";
        }
        if (profile == null && packProblem != null) {
            return "Off: the shader pack's " + PackProfile.FILE + " could not be read (the log says why).";
        }
        if (packProblem != null) {
            return "Off with these shader pack options: " + packProblem + ".";
        }
        if (failed) {
            return "Stopped after an error (the log says which). The pack's own anti-aliasing is back; reloading shaders tries again.";
        }
        if (debugView != null) {
            return "Debug view " + debugView.name().toLowerCase(Locale.ROOT) + " (testing).";
        }
        String size = renderWidth < displayWidth || renderHeight < displayHeight
                ? "upscaling " + renderWidth + "x" + renderHeight + " to " + displayWidth + "x" + displayHeight
                : "at full resolution";
        if (active && !fsr3 && rrRunning && featureIsRr) {
            return "On: NVIDIA DLSS Ray Reconstruction (experimental, DirectX 12) " + size + " on " + adapterName + ", preset " + rrPresetName(rrPreset) + ".";
        }
        String rr = rayReconstructionWanted && rrUnavailable != null ? " Ray Reconstruction can't run: " + rrUnavailable + "."
                : rayReconstructionWanted && profile != null && !profile.hasRayReconstructionGuides() ? " Ray Reconstruction: this pack hands over no guides." : "";
        if (active && !fsr3) {
            return "On: NVIDIA " + (size.startsWith("at") ? "DLAA" : "DLSS " + size) + " on " + adapterName + ", preset "
                    + presetName(presetFellBack ? PRESET_DEFAULT : preset)
                    + (presetFellBack ? " (DLSS refused " + presetName(preset) + ")" : "") + "." + rr;
        }
        if (active) {
            return "On: AMD FSR 3.1 " + size + " on " + fsrRenderer
                    + (engine.equals("auto") && unavailable != null ? ". DLSS can't run: " + unavailable : "") + ".";
        }
        if (engine.equals("dlss") && unavailable != null) {
            return "Off, DLSS can't run: " + unavailable + ".";
        }
        if (fsr3Unavailable != null && (fsr3 || engine.equals("fsr3"))) {
            return "Off, FSR 3 can't run: " + fsr3Unavailable + ".";
        }
        return "Starts in a world.";
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
            LOG.warn("DLAA: unknown debug view {}, use color, motion, depth, residual, mask, albedo, specular, normal, roughness or hit_distance", name);
            return null;
        }
    }

    private static float parseFloat(String value, float min, float max) {
        try {
            float f = Float.parseFloat(value.trim());
            if (Float.isFinite(f)) {
                return Math.max(min, Math.min(max, f));
            }
        } catch (RuntimeException e) {
            // falls through
        }
        LOG.warn("DLAA: {} is not a number, using 0", value);
        return 0.0f;
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

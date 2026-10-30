package dev.creategalore.bridge.dlss;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memUTF8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;

import dev.creategalore.bridge.present.Ffi;
import dev.creategalore.bridge.present.NativeHelper;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * cg_ngx.dll, the Bridge's native DLSS helper (tools/bridge-mod/native/cg_ngx.cpp): NVIDIA's SDK library wrapped as
 * flat C functions, called here through libffi. The DLL ships inside the mod jar and is unpacked next to
 * nvngx_dlss.dll under a name that carries its hash, so a running game never has its copy replaced.
 */
final class NgxLibrary {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int ABI = 4;

    // NVSDK_NGX_Result
    static final int RESULT_SUCCESS = 0x1;
    // NVSDK_NGX_PerfQuality_Value
    static final int PERF_QUALITY_MAX_PERF = 0;
    static final int PERF_QUALITY_BALANCED = 1;
    static final int PERF_QUALITY_MAX_QUALITY = 2;
    static final int PERF_QUALITY_ULTRA_PERFORMANCE = 3;
    static final int PERF_QUALITY_DLAA = 5;
    // NVSDK_NGX_DLSS_Feature_Flags
    static final int FLAG_IS_HDR = 1;
    static final int FLAG_MV_LOW_RES = 1 << 1;
    static final int FLAG_DEPTH_INVERTED = 1 << 3;
    static final int FLAG_AUTO_EXPOSURE = 1 << 6;
    // NVSDK_NGX_Logging_Level
    static final int LOG_ON = 1;
    // NVSDK_NGX_RayReconstruction_Hint_Render_Preset: F is DLSS 4.5's Ray Reconstruction model
    static final int RR_PRESET_D = 4;
    static final int RR_PRESET_E = 5;
    static final int RR_PRESET_F = 6;
    // NVSDK_NGX_DLSS_Depth_Type
    static final int DEPTH_HARDWARE = 1;

    private final long init;
    private final long query;
    private final long create;
    private final long evaluate;
    private final long queryRr;
    private final long createRr;
    private final long evaluateRr;
    private final long release;
    private final long shutdown;
    private final long logNext;
    // Direct3D 12 (DlssDevice12)
    final long init12;
    final long open12;
    final long error12;
    final long adapterName12;
    final long fenceHandle12;
    final long createTexture12;
    final long releaseTexture12;
    final long waitIdle12;
    final long close12;
    private final long evaluate12;
    private final long evaluateRr12;

    private NgxLibrary(NativeHelper dll) {
        dll.checkAbi(ABI);
        init = dll.symbol("cg_ngx_init");
        query = dll.symbol("cg_ngx_query");
        create = dll.symbol("cg_ngx_create");
        evaluate = dll.symbol("cg_ngx_evaluate");
        queryRr = dll.symbol("cg_ngx_query_rr");
        createRr = dll.symbol("cg_ngx_create_rr");
        evaluateRr = dll.symbol("cg_ngx_evaluate_rr");
        release = dll.symbol("cg_ngx_release");
        shutdown = dll.symbol("cg_ngx_shutdown");
        logNext = dll.symbol("cg_ngx_log_next");
        init12 = dll.symbol("cg_ngx_init12");
        open12 = dll.symbol("cg_ngx12_open");
        error12 = dll.symbol("cg_ngx12_error");
        adapterName12 = dll.symbol("cg_ngx12_adapter_name");
        fenceHandle12 = dll.symbol("cg_ngx12_fence_handle");
        createTexture12 = dll.symbol("cg_ngx12_create_texture");
        releaseTexture12 = dll.symbol("cg_ngx12_release_texture");
        waitIdle12 = dll.symbol("cg_ngx12_wait_idle");
        close12 = dll.symbol("cg_ngx12_close");
        evaluate12 = dll.symbol("cg_ngx12_evaluate");
        evaluateRr12 = dll.symbol("cg_ngx12_evaluate_rr");
    }

    /** Unpacks the helper into {@code folder} (if that exact build is not there yet) and loads it. */
    static NgxLibrary load(Path folder) throws IOException {
        return new NgxLibrary(NativeHelper.load("cg_ngx", folder));
    }

    static boolean failed(int result) {
        return (result & 0xFFF00000) == 0xBAD00000;
    }

    /** NGX's result codes by name, for the log. */
    static String describe(int result) {
        if (!failed(result)) {
            return "success";
        }
        String name = switch (result & 0xFFFFF) {
            case 1 -> "feature not supported";
            case 2 -> "platform error";
            case 3 -> "feature already exists";
            case 4 -> "feature not found";
            case 5 -> "invalid parameter";
            case 6 -> "scratch buffer too small";
            case 7 -> "not initialized";
            case 8 -> "unsupported input format";
            case 9 -> "RW flag missing";
            case 10 -> "missing input";
            case 11 -> "unable to initialize feature";
            case 12 -> "out of date (driver or DLSS too old)";
            case 13 -> "out of GPU memory";
            case 14 -> "unsupported format";
            case 15 -> "unable to write to app data path";
            case 16 -> "unsupported parameter";
            case 17 -> "denied";
            case 18 -> "not implemented";
            default -> "failure";
        };
        return String.format("%s (0x%08X)", name, result);
    }

    /** Starts NGX on {@code device}: Direct3D 11's, or Direct3D 12's (cg_ngx12_open made it). */
    int init(String projectId, String engineVersion, Path appData, Path featureFolder, NgxDevice device, int logLevel) {
        try (MemoryStack stack = stackPush()) {
            long project = memAddress(stack.ASCII(projectId));
            long engine = memAddress(stack.ASCII(engineVersion));
            long data = memAddress(stack.UTF16(appData.toString()));
            long features = memAddress(stack.UTF16(featureFolder.toString()));
            return device instanceof DlssDevice12
                    ? (int) Ffi.call("PPPPI:I", init12, project, engine, data, features, logLevel)
                    : (int) Ffi.call("PPPPPI:I", init, project, engine, data, features, device.device(), logLevel);
        }
    }

    /** {available, needs a newer driver, minimum driver major, minor, the feature's init result}, or null. */
    int[] query() {
        return query(query);
    }

    /** {@link #query()} for DLSS Ray Reconstruction (nvngx_dlssd.dll). */
    int[] queryRayReconstruction() {
        return query(queryRr);
    }

    private static int[] query(long function) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer out = stack.callocInt(5);
            int result = (int) Ffi.call("P:I", function, memAddress(out));
            if (failed(result)) {
                return null;
            }
            return new int[] {out.get(0), out.get(1), out.get(2), out.get(3), out.get(4)};
        }
    }

    int create(long context, int renderWidth, int renderHeight, int outWidth, int outHeight, int perfQuality, int flags, int preset) {
        return (int) Ffi.call("PIIIIIII:I", create, context, renderWidth, renderHeight, outWidth, outHeight, perfQuality, flags, preset);
    }

    int evaluate(NgxDevice device, long color, long output, long depth, long motion, long exposure, float jitterX, float jitterY,
                 float mvScaleX, float mvScaleY, boolean reset, int renderWidth, int renderHeight, float frameTimeMs) {
        if (device instanceof DlssDevice12 d3d12) {
            try (MemoryStack stack = stackPush()) {
                java.nio.LongBuffer pointers = stack.longs(color, output, depth, motion, exposure);
                java.nio.FloatBuffer floats = stack.floats(jitterX, jitterY, mvScaleX, mvScaleY, frameTimeMs, 1.0f);
                java.nio.LongBuffer done = stack.callocLong(1);
                int result = (int) Ffi.call("PPIIIPP:I", evaluate12, memAddress(pointers), memAddress(floats), reset ? 1 : 0,
                        renderWidth, renderHeight, d3d12.openglValue(), memAddress(done));
                d3d12.queued(done.get(0));
                return result;
            }
        }
        return (int) Ffi.call("PPPPPPFFFFIIIFF:I", evaluate, device.context(), color, output, depth, motion, exposure,
                Ffi.floatArg(jitterX), Ffi.floatArg(jitterY), Ffi.floatArg(mvScaleX), Ffi.floatArg(mvScaleY),
                reset ? 1 : 0, renderWidth, renderHeight, Ffi.floatArg(frameTimeMs), Ffi.floatArg(1.0f));
    }

    /**
     * Makes the feature DLSS Ray Reconstruction instead (roughness in the normals' alpha). {@code preset} is set for
     * every quality mode; {@code depthType} {@link #DEPTH_HARDWARE} or 0 for view depth. {@code ownParameters} (testing)
     * gives it NGX parameters of its own in place of the ones DLSS shares.
     */
    int createRayReconstruction(long context, int renderWidth, int renderHeight, int outWidth, int outHeight, int perfQuality,
                                int flags, int preset, int depthType, boolean ownParameters) {
        return (int) Ffi.call("PIIIIIIIII:I", createRr, context, renderWidth, renderHeight, outWidth, outHeight, perfQuality,
                flags, preset, depthType, ownParameters ? 1 : 0);
    }

    /**
     * Runs Ray Reconstruction once. {@code resources}: colour, output, depth, motion vectors, diffuse albedo, specular
     * albedo, normals with roughness, specular hit distance (0 if none). The matrices are OpenGL's column-major layout
     * (which NGX reads as row major for a row vector).
     */
    int evaluateRayReconstruction(NgxDevice device, long[] resources, float jitterX, float jitterY, float mvScaleX, float mvScaleY,
                                  float frameTimeMs, float[] worldToView, float[] viewToClip, boolean reset, int renderWidth,
                                  int renderHeight) {
        try (MemoryStack stack = stackPush()) {
            java.nio.LongBuffer pointers = stack.mallocLong(8);
            for (int i = 0; i < 8; i++) {
                pointers.put(i, i < resources.length ? resources[i] : 0L);
            }
            java.nio.FloatBuffer floats = stack.mallocFloat(37);
            floats.put(0, jitterX).put(1, jitterY).put(2, mvScaleX).put(3, mvScaleY).put(4, frameTimeMs);
            floats.put(5, worldToView).put(21, viewToClip);
            if (device instanceof DlssDevice12 d3d12) {
                java.nio.LongBuffer done = stack.callocLong(1);
                int result = (int) Ffi.call("PPIIIPP:I", evaluateRr12, memAddress(pointers), memAddress(floats), reset ? 1 : 0,
                        renderWidth, renderHeight, d3d12.openglValue(), memAddress(done));
                d3d12.queued(done.get(0));
                return result;
            }
            return (int) Ffi.call("PPPIII:I", evaluateRr, device.context(), memAddress(pointers), memAddress(floats), reset ? 1 : 0,
                    renderWidth, renderHeight);
        }
    }

    int release() {
        return (int) Ffi.call(":I", release);
    }

    int shutdown() {
        return (int) Ffi.call(":I", shutdown);
    }

    /** Moves NGX's queued log lines into the game log. */
    void drainLog() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer line = stack.malloc(1024);
            int length;
            while ((length = (int) Ffi.call("PI:I", logNext, memAddress(line), line.capacity())) >= 0) {
                LOG.info("DLAA: NGX: {}", memUTF8(memAddress(line), length));
            }
        }
    }
}

package dev.creategalore.bridge.dlss;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.creategalore.bridge.present.Ffi;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WinBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * cg_ngx.dll, the Bridge's native DLSS helper (tools/bridge-mod/native/cg_ngx.cpp): NVIDIA's SDK library wrapped as
 * flat C functions, called here through libffi. The DLL ships inside the mod jar and is unpacked next to
 * nvngx_dlss.dll under a name that carries its hash, so a running game never has its copy replaced.
 */
final class NgxLibrary {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final String RESOURCE = "/natives/windows-x64/cg_ngx.dll";
    private static final int ABI = 1;

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

    private final long init;
    private final long query;
    private final long create;
    private final long evaluate;
    private final long release;
    private final long shutdown;
    private final long logNext;

    private NgxLibrary(Path dll) {
        long module = WinBase.LoadLibrary(dll.toString());
        if (module == 0L) {
            throw new IllegalStateException("Could not load " + dll + " (error " + WinBase.getLastError() + ")");
        }
        int abi = (int) Ffi.call(":I", symbol(module, "cg_ngx_abi"));
        if (abi != ABI) {
            throw new IllegalStateException(dll + " is helper version " + abi + ", the Bridge needs " + ABI);
        }
        init = symbol(module, "cg_ngx_init");
        query = symbol(module, "cg_ngx_query");
        create = symbol(module, "cg_ngx_create");
        evaluate = symbol(module, "cg_ngx_evaluate");
        release = symbol(module, "cg_ngx_release");
        shutdown = symbol(module, "cg_ngx_shutdown");
        logNext = symbol(module, "cg_ngx_log_next");
    }

    /** Unpacks the helper into {@code folder} (if that exact build is not there yet) and loads it. */
    static NgxLibrary load(Path folder) throws IOException {
        byte[] bytes;
        try (InputStream in = NgxLibrary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("The mod jar has no " + RESOURCE);
            }
            bytes = in.readAllBytes();
        }
        Path dll = folder.resolve("cg_ngx-" + sha256(bytes).substring(0, 12) + ".dll");
        if (!Files.exists(dll) || Files.size(dll) != bytes.length) {
            Files.createDirectories(folder);
            Path temp = Files.createTempFile(folder, "cg_ngx", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, dll, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return new NgxLibrary(dll);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long symbol(long module, String name) {
        long function = WinBase.GetProcAddress(module, name);
        if (function == 0L) {
            throw new IllegalStateException("cg_ngx.dll has no " + name);
        }
        return function;
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

    int init(String projectId, String engineVersion, Path appData, Path featureFolder, long device, int logLevel) {
        try (MemoryStack stack = stackPush()) {
            return (int) Ffi.call("PPPPPI:I", init,
                    memAddress(stack.ASCII(projectId)), memAddress(stack.ASCII(engineVersion)),
                    memAddress(stack.UTF16(appData.toString())), memAddress(stack.UTF16(featureFolder.toString())),
                    device, logLevel);
        }
    }

    /** {available, needs a newer driver, minimum driver major, minor, the feature's init result}, or null. */
    int[] query() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer out = stack.callocInt(5);
            int result = (int) Ffi.call("P:I", query, memAddress(out));
            if (failed(result)) {
                return null;
            }
            return new int[] {out.get(0), out.get(1), out.get(2), out.get(3), out.get(4)};
        }
    }

    int create(long context, int renderWidth, int renderHeight, int outWidth, int outHeight, int perfQuality, int flags, int preset) {
        return (int) Ffi.call("PIIIIIII:I", create, context, renderWidth, renderHeight, outWidth, outHeight, perfQuality, flags, preset);
    }

    int evaluate(long context, long color, long output, long depth, long motion, long exposure, float jitterX, float jitterY,
                 float mvScaleX, float mvScaleY, boolean reset, int renderWidth, int renderHeight, float frameTimeMs) {
        return (int) Ffi.call("PPPPPPFFFFIIIFF:I", evaluate, context, color, output, depth, motion, exposure,
                Ffi.floatArg(jitterX), Ffi.floatArg(jitterY), Ffi.floatArg(mvScaleX), Ffi.floatArg(mvScaleY),
                reset ? 1 : 0, renderWidth, renderHeight, Ffi.floatArg(frameTimeMs), Ffi.floatArg(1.0f));
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

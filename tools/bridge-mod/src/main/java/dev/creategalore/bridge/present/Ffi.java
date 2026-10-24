package dev.creategalore.bridge.present;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.system.libffi.LibFFI.FFI_DEFAULT_ABI;
import static org.lwjgl.system.libffi.LibFFI.FFI_OK;
import static org.lwjgl.system.libffi.LibFFI.ffi_prep_cif;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_float;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_void;
import static org.lwjgl.system.libffi.LibFFI.nffi_call;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;

/**
 * Calls a native function pointer through LWJGL's bundled libffi. Perfect Presentation uses Java 22's foreign function
 * API for this; Minecraft 1.21.1 runs on Java 21, so the D3D11/DXGI calls go through libffi instead.
 *
 * A signature is the argument kinds, a colon, then the return kind: P = pointer or SIZE_T, I = 32-bit int,
 * F = float (passed as its raw bits), V = void return. "PII:I" takes a pointer and two ints and returns an int.
 */
public final class Ffi {
    private static final Map<String, FFICIF> CIFS = new ConcurrentHashMap<>();

    private Ffi() {}

    public static long call(String sig, long function, long... args) {
        if (function == 0L) {
            throw new IllegalStateException("Null function pointer for " + sig);
        }
        int colon = sig.indexOf(':');
        if (colon != args.length) {
            throw new IllegalArgumentException("Signature " + sig + " takes " + colon + " arguments, got " + args.length);
        }
        FFICIF cif = CIFS.computeIfAbsent(sig, Ffi::prepare);
        try (MemoryStack stack = stackPush()) {
            int n = Math.max(1, args.length);
            long values = stack.nmalloc(8, n * 8);
            PointerBuffer pointers = stack.mallocPointer(n);
            for (int i = 0; i < args.length; i++) {
                long slot = values + i * 8L;
                switch (sig.charAt(i)) {
                    case 'P' -> memPutAddress(slot, args[i]);
                    case 'I', 'F' -> memPutInt(slot, (int) args[i]);
                    default -> throw new IllegalArgumentException("Bad signature " + sig);
                }
                pointers.put(i, slot);
            }
            long result = stack.nmalloc(8, 8);
            memPutLong(result, 0L);
            nffi_call(cif.address(), function, result, pointers.address());
            return switch (sig.charAt(colon + 1)) {
                case 'I' -> memGetInt(result);
                case 'P' -> memGetAddress(result);
                default -> 0L;
            };
        }
    }

    public static long floatArg(float value) {
        return Float.floatToRawIntBits(value);
    }

    private static FFICIF prepare(String sig) {
        int colon = sig.indexOf(':');
        // Both live for the rest of the game: a prepared call interface keeps pointing at its argument list.
        PointerBuffer argTypes = memAllocPointer(Math.max(1, colon));
        for (int i = 0; i < colon; i++) {
            argTypes.put(i, type(sig.charAt(i)));
        }
        argTypes.limit(colon);
        FFICIF cif = FFICIF.malloc();
        int status = ffi_prep_cif(cif, FFI_DEFAULT_ABI, type(sig.charAt(colon + 1)), argTypes);
        if (status != FFI_OK) {
            throw new IllegalStateException("ffi_prep_cif failed (" + status + ") for " + sig);
        }
        return cif;
    }

    private static FFIType type(char kind) {
        return switch (kind) {
            case 'P' -> ffi_type_pointer;
            case 'I' -> ffi_type_sint32;
            case 'F' -> ffi_type_float;
            case 'V' -> ffi_type_void;
            default -> throw new IllegalArgumentException("Bad signature kind " + kind);
        };
    }
}

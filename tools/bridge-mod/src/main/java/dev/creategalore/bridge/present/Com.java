package dev.creategalore.bridge.present;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutShort;
import static org.lwjgl.system.MemoryUtil.nmemAlloc;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WinBase;

/**
 * Raw COM and Win32 calls for {@link InteropContext}. A COM object pointer points at its vtable; method N of an
 * interface is the Nth function pointer there, counting the methods of every parent interface first. The slot
 * numbers in InteropContext come from the mingw-w64 d3d11.h, dxgi.h, dxgi1_2.h, dxgi1_3.h and d3dcommon.h headers.
 */
public final class Com {
    public static final int QUERY_INTERFACE = 0;
    public static final int RELEASE = 2;

    private Com() {}

    /** Calls vtable slot {@code slot} of {@code object}; {@code sig} lists the arguments after the object pointer. */
    public static long call(long object, int slot, String sig, long... args) {
        long[] all = new long[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        long function = memGetAddress(memGetAddress(object) + slot * 8L);
        return Ffi.call("P" + sig, function, all);
    }

    /** Calls a method that returns an HRESULT and throws if it failed. */
    public static void check(String what, long object, int slot, String sig, long... args) {
        checkHr(what, (int) call(object, slot, sig + ":I", args));
    }

    public static void checkHr(String what, int hr) {
        if (hr < 0) {
            throw new IllegalStateException(String.format("%s failed: HRESULT 0x%08X", what, hr));
        }
    }

    public static void release(long object) {
        if (object != 0L) {
            call(object, RELEASE, ":I");
        }
    }

    /** A GUID that lives for the rest of the game, from its usual Data1..Data4 parts. */
    public static long guid(int data1, int data2, int data3, int... data4) {
        long g = nmemAlloc(16);
        memPutInt(g, data1);
        memPutShort(g + 4, (short) data2);
        memPutShort(g + 6, (short) data3);
        for (int i = 0; i < 8; i++) {
            memPutByte(g + 8 + i, (byte) data4[i]);
        }
        return g;
    }

    /**
     * Address of an exported function in a system DLL, loading the DLL if needed. A DLL that isn't loaded yet is loaded from
     * System32 only: a plain LoadLibrary would also search the game folder and every PATH folder, where anyone who can
     * write there could put a DLL of the same name (nvapi64.dll isn't in System32 on AMD and Intel PCs, so the search
     * would always go on to those folders).
     */
    public static long function(String dll, String name) {
        long module = WinBase.GetModuleHandle(dll);
        if (module == 0L) {
            module = loadFromSystem32(dll);
        }
        if (module == 0L) {
            throw new IllegalStateException("Could not load " + dll + " from System32");
        }
        long function = WinBase.GetProcAddress(module, name);
        if (function == 0L) {
            throw new IllegalStateException("No " + name + " in " + dll);
        }
        return function;
    }

    private static final int LOAD_LIBRARY_SEARCH_SYSTEM32 = 0x00000800;

    /** LoadLibraryExW(dll, NULL, LOAD_LIBRARY_SEARCH_SYSTEM32); 0 if the DLL isn't in System32. */
    private static long loadFromSystem32(String dll) {
        long kernel32 = WinBase.GetModuleHandle("kernel32.dll");
        long loadLibraryEx = kernel32 == 0L ? 0L : WinBase.GetProcAddress(kernel32, "LoadLibraryExW");
        if (loadLibraryEx == 0L) {
            return 0L;
        }
        try (MemoryStack stack = stackPush()) {
            ByteBuffer wide = stack.UTF16(dll);
            return Ffi.call("PPI:P", loadLibraryEx, memAddress(wide), 0L, LOAD_LIBRARY_SEARCH_SYSTEM32);
        }
    }
}

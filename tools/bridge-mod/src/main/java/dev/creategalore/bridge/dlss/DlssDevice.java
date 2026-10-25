package dev.creategalore.bridge.dlss;

import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXCloseDeviceNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXLockObjectsNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXOpenDeviceNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXRegisterObjectNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXUnlockObjectsNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXUnregisterObjectNV;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memUTF16;

import java.nio.ByteBuffer;
import java.util.List;

import dev.creategalore.bridge.present.Com;
import dev.creategalore.bridge.present.Ffi;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WindowsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Direct3D 11 device DLSS runs on, and the textures OpenGL shares with it through WGL_NV_DX_interop2. DLAA opens a
 * device of its own on the GPU OpenGL renders with, also when Perfect Presentation shows the frames through a swap chain
 * device of its own. On that shared device, freeing DLSS's textures for a new render size crashed NVIDIA's driver
 * inside wglDXUnregisterObjectNV (RTX 4080 Super, driver 621.01), which dozens of restarts on a device of DLAA's own
 * never did.
 */
final class DlssDevice implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    // dxgi.h, d3d11.h, d3dcommon.h
    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;
    private static final int D3D11_SDK_VERSION = 7;
    private static final int DXGI_ERROR_NOT_FOUND = 0x887A0002;
    private static final int DXGI_ADAPTER_FLAG_SOFTWARE = 2;
    private static final int NVIDIA_VENDOR_ID = 0x10DE;
    static final int DXGI_FORMAT_R16G16B16A16_FLOAT = 10;
    static final int DXGI_FORMAT_R16G16_FLOAT = 34;
    static final int DXGI_FORMAT_R32_FLOAT = 41;
    private static final int D3D11_USAGE_DEFAULT = 0;
    static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    static final int D3D11_BIND_RENDER_TARGET = 0x20;
    static final int D3D11_BIND_UNORDERED_ACCESS = 0x80;
    // WGL_NV_DX_interop
    static final int ACCESS_READ_ONLY = 0;
    static final int ACCESS_WRITE_DISCARD = 2;

    // Vtable slots, counting inherited methods (IUnknown 0-2, then IDXGIObject 3-6, or for the context ID3D11DeviceChild 3-6).
    private static final int FACTORY1_ENUM_ADAPTERS1 = 12;
    private static final int ADAPTER1_GET_DESC1 = 10;
    private static final int DEVICE_CREATE_TEXTURE_2D = 5;
    private static final int DEVICE_CREATE_QUERY = 24;
    private static final int CONTEXT_END = 28;
    private static final int CONTEXT_GET_DATA = 29;
    private static final int CONTEXT_FLUSH = 111;
    private static final int S_FALSE = 1;
    /** How long {@link #waitIdle} waits for the GPU at most. */
    private static final long IDLE_TIMEOUT_NANOS = 2_000_000_000L;

    private static final long IID_IDXGI_FACTORY1 = Com.guid(0x770aae78, 0xf26f, 0x4dba, 0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87);

    // DXGI_ADAPTER_DESC1 (x64): Description WCHAR[128] at 0, VendorId 256, DedicatedVideoMemory 272, AdapterLuid 296,
    // Flags 304, size 312. D3D11_TEXTURE2D_DESC is 44 bytes.
    private static final int ADAPTER_DESC1_SIZE = 312;
    private static final int TEXTURE2D_DESC_SIZE = 44;

    final long device;
    final long context;
    final long interopDevice;
    final String adapterName;

    private DlssDevice(long device, long context, long interopDevice, String adapterName) {
        this.device = device;
        this.context = context;
        this.interopDevice = interopDevice;
        this.adapterName = adapterName;
    }

    /** A new device on the GPU OpenGL renders with. */
    static DlssDevice open() {
        if (!GL.getCapabilitiesWGL().WGL_NV_DX_interop2) {
            throw new IllegalStateException("the OpenGL driver has no WGL_NV_DX_interop2");
        }
        long luid = openGlAdapterLuid();
        try (MemoryStack stack = stackPush()) {
            long out = stack.nmalloc(8, 8);
            Com.checkHr("CreateDXGIFactory1", (int) Ffi.call("PP:I", Com.function("dxgi.dll", "CreateDXGIFactory1"), IID_IDXGI_FACTORY1, out));
            long factory = memGetAddress(out);
            long adapter = 0L;
            String name = null;
            try {
                // The GPU OpenGL renders with when the driver says which one that is, else the NVIDIA one with the most memory.
                long desc = memAddress(stack.calloc(8, ADAPTER_DESC1_SIZE));
                boolean matchedLuid = false;
                long bestMemory = -1L;
                for (int i = 0; !matchedLuid; i++) {
                    int hr = (int) Com.call(factory, FACTORY1_ENUM_ADAPTERS1, "IP:I", i, out);
                    if (hr == DXGI_ERROR_NOT_FOUND) {
                        break;
                    }
                    Com.checkHr("EnumAdapters1", hr);
                    long candidate = memGetAddress(out);
                    Com.check("GetDesc1", candidate, ADAPTER1_GET_DESC1, "P", desc);
                    boolean software = (memGetInt(desc + 304) & DXGI_ADAPTER_FLAG_SOFTWARE) != 0;
                    boolean nvidia = memGetInt(desc + 256) == NVIDIA_VENDOR_ID;
                    long memory = memGetLong(desc + 272);
                    matchedLuid = luid != 0L && memGetLong(desc + 296) == luid;
                    if (matchedLuid || nvidia && !software && memory > bestMemory) {
                        Com.release(adapter);
                        adapter = candidate;
                        name = memUTF16(desc);
                        bestMemory = memory;
                    } else {
                        Com.release(candidate);
                    }
                }
            } finally {
                Com.release(factory);
            }
            if (adapter == 0L) {
                throw new IllegalStateException("no NVIDIA adapter found for DirectX");
            }
            long out2 = stack.nmalloc(8, 8);
            int hr = (int) Ffi.call("PIPIPIIPPP:I", Com.function("d3d11.dll", "D3D11CreateDevice"),
                    adapter, D3D_DRIVER_TYPE_UNKNOWN, 0L, 0, 0L, 0, D3D11_SDK_VERSION, out, 0L, out2);
            Com.release(adapter);
            Com.checkHr("D3D11CreateDevice", hr);
            long device = memGetAddress(out);
            long context = memGetAddress(out2);
            long interop = wglDXOpenDeviceNV(device);
            if (interop == 0L) {
                Com.release(context);
                Com.release(device);
                WindowsUtil.windowsThrowException("wglDXOpenDeviceNV failed");
            }
            return new DlssDevice(device, context, interop, name);
        }
    }

    /** The LUID of the GPU behind the current OpenGL context (GL_EXT_memory_object_win32), or 0 if unknown. */
    private static long openGlAdapterLuid() {
        if (!GL.getCapabilities().GL_EXT_memory_object_win32) {
            return 0L;
        }
        try (MemoryStack stack = stackPush()) {
            ByteBuffer luid = stack.calloc(EXTMemoryObjectWin32.GL_LUID_SIZE_EXT);
            EXTMemoryObject.glGetUnsignedBytevEXT(EXTMemoryObjectWin32.GL_DEVICE_LUID_EXT, luid);
            return memGetLong(memAddress(luid));
        }
    }

    /** A texture both APIs see. OpenGL owns it while locked (see {@link #lock}), D3D11 while unlocked. */
    final class SharedTexture implements AutoCloseable {
        final int glTexture;
        final long d3dTexture;
        final int width;
        final int height;
        private final long handle;

        SharedTexture(int width, int height, int format, int bindFlags, int access) {
            this.width = width;
            this.height = height;
            glTexture = glCreateTextures(GL_TEXTURE_2D);
            try (MemoryStack stack = stackPush()) {
                long out = stack.nmalloc(8, 8);
                long desc = memAddress(stack.calloc(8, TEXTURE2D_DESC_SIZE));
                memPutInt(desc, width);
                memPutInt(desc + 4, height);
                memPutInt(desc + 8, 1);              // MipLevels
                memPutInt(desc + 12, 1);             // ArraySize
                memPutInt(desc + 16, format);
                memPutInt(desc + 20, 1);             // SampleDesc.Count
                memPutInt(desc + 28, D3D11_USAGE_DEFAULT);
                memPutInt(desc + 32, bindFlags);
                Com.check("CreateTexture2D", device, DEVICE_CREATE_TEXTURE_2D, "PPP", desc, 0L, out);
                d3dTexture = memGetAddress(out);
            }
            handle = wglDXRegisterObjectNV(interopDevice, d3dTexture, glTexture, GL_TEXTURE_2D, access);
            if (handle == 0L) {
                Com.release(d3dTexture);
                glDeleteTextures(glTexture);
                WindowsUtil.windowsThrowException("wglDXRegisterObjectNV failed");
            }
            // One level, read with texelFetch: without this the texture is mipmap-incomplete and reads as black.
            glTextureParameteri(glTexture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(glTexture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(glTexture, GL_TEXTURE_MAX_LEVEL, 0);
        }

        @Override
        public void close() {
            wglDXUnregisterObjectNV(interopDevice, handle);
            Com.release(d3dTexture);
            glDeleteTextures(glTexture);
        }
    }

    /** Hands the textures to OpenGL, all at once. */
    void lock(List<SharedTexture> textures) {
        try (MemoryStack stack = stackPush()) {
            if (!wglDXLockObjectsNV(interopDevice, handles(stack, textures))) {
                WindowsUtil.windowsThrowException("wglDXLockObjectsNV failed");
            }
        }
    }

    /** Hands the textures to D3D11, all at once; OpenGL's writes to them are finished first. */
    void unlock(List<SharedTexture> textures) {
        try (MemoryStack stack = stackPush()) {
            if (!wglDXUnlockObjectsNV(interopDevice, handles(stack, textures))) {
                WindowsUtil.windowsThrowException("wglDXUnlockObjectsNV failed");
            }
        }
    }

    private static PointerBuffer handles(MemoryStack stack, List<SharedTexture> textures) {
        PointerBuffer handles = stack.mallocPointer(textures.size());
        for (int i = 0; i < textures.size(); i++) {
            handles.put(i, textures.get(i).handle);
        }
        return handles;
    }

    /**
     * Waits (at most {@link #IDLE_TIMEOUT_NANOS}) until the GPU has finished what this device was given so far, so a
     * texture that DLSS read or wrote is no longer in use when it is freed.
     */
    void waitIdle() {
        try (MemoryStack stack = stackPush()) {
            long desc = memAddress(stack.calloc(8, 8)); // D3D11_QUERY_DESC: D3D11_QUERY_EVENT (0), no flags
            long out = stack.nmalloc(8, 8);
            Com.check("CreateQuery", device, DEVICE_CREATE_QUERY, "PP", desc, out);
            long query = memGetAddress(out);
            try {
                Com.call(context, CONTEXT_END, "P:V", query);
                Com.call(context, CONTEXT_FLUSH, ":V");
                long deadline = System.nanoTime() + IDLE_TIMEOUT_NANOS;
                int hr;
                while ((hr = (int) Com.call(context, CONTEXT_GET_DATA, "PPII:I", query, 0L, 0, 0)) == S_FALSE) {
                    if (System.nanoTime() > deadline) {
                        LOG.warn("DLAA: the GPU did not finish DLSS's work within {} s", IDLE_TIMEOUT_NANOS / 1_000_000_000L);
                        return;
                    }
                    Thread.onSpinWait();
                }
                Com.checkHr("GetData", hr);
            } finally {
                Com.release(query);
            }
        }
    }

    @Override
    public void close() {
        wglDXCloseDeviceNV(interopDevice);
        Com.release(context);
        Com.release(device);
        LOG.info("DLAA: closed its DirectX device");
    }
}

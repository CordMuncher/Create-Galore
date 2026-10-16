package dev.creategalore.bridge.present;

import static org.lwjgl.opengl.WGLNVDXInterop.WGL_ACCESS_WRITE_DISCARD_NV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXCloseDeviceNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXLockObjectsNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXOpenDeviceNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXRegisterObjectNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXUnlockObjectsNV;
import static org.lwjgl.opengl.WGLNVDXInterop.wglDXUnregisterObjectNV;
import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.system.MemoryUtil.memPutInt;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WindowsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the game's frames through a Direct3D 11 flip-model swap chain on the game window. The game still renders with
 * OpenGL (on {@link WglContext}); its main colour texture is shared with D3D11 through WGL_NV_DX_interop2 and copied
 * to the swap chain each frame. Windows then treats the window like a DirectX game: borderless fullscreen gets
 * exclusive-fullscreen speed, and Alt-Tab never changes the display mode.
 *
 * Ported from Perfect Presentation's InteropContext (MIT, Copyright (c) sidit77), with its Java 22 foreign-function
 * calls rewritten as libffi calls so it runs on Java 21. Same steps, flags and shader.
 */
final class InteropContext implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    // d3dcommon.h, d3d11.h, dxgi.h, dxgi1_2.h, dxgi1_3.h, dxgi1_5.h
    private static final int D3D_DRIVER_TYPE_HARDWARE = 1;
    private static final int D3D11_CREATE_DEVICE_BGRA_SUPPORT = 0x20;
    private static final int D3D11_SDK_VERSION = 7;
    private static final int DXGI_FORMAT_UNKNOWN = 0;
    private static final int DXGI_FORMAT_R8G8B8A8_UNORM = 28;
    private static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
    private static final int DXGI_USAGE_RENDER_TARGET_OUTPUT = 0x20;
    private static final int DXGI_SCALING_NONE = 1;
    private static final int DXGI_SWAP_EFFECT_FLIP_DISCARD = 4;
    private static final int DXGI_ALPHA_MODE_UNSPECIFIED = 0;
    private static final int DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT = 0x40;
    private static final int DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING = 0x800;
    private static final int DXGI_PRESENT_ALLOW_TEARING = 0x200;
    private static final int D3D11_FILL_SOLID = 3;
    private static final int D3D11_CULL_NONE = 1;
    private static final int D3D11_FILTER_MIN_MAG_MIP_POINT = 0;
    private static final int D3D11_TEXTURE_ADDRESS_CLAMP = 3;
    private static final int D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST = 4;
    private static final int D3D11_USAGE_DEFAULT = 0;
    private static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    private static final int D3D11_BIND_RENDER_TARGET = 0x20;
    private static final int GL_RGBA8 = 0x8058;
    private static final int WAIT_ABANDONED = 0x80;
    private static final int WAIT_TIMEOUT = 0x102;
    private static final int WAIT_FAILED = 0xFFFFFFFF;

    private static final int SWAP_CHAIN_FLAGS = DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING | DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT;

    // Vtable slots, counting inherited methods (IUnknown 0-2 first).
    private static final int DEVICE_CREATE_TEXTURE_2D = 5;
    private static final int DEVICE_CREATE_SHADER_RESOURCE_VIEW = 7;
    private static final int DEVICE_CREATE_RENDER_TARGET_VIEW = 9;
    private static final int DEVICE_CREATE_VERTEX_SHADER = 12;
    private static final int DEVICE_CREATE_PIXEL_SHADER = 15;
    private static final int DEVICE_CREATE_RASTERIZER_STATE = 22;
    private static final int DEVICE_CREATE_SAMPLER_STATE = 23;
    private static final int CONTEXT_PS_SET_SHADER_RESOURCES = 8;
    private static final int CONTEXT_PS_SET_SHADER = 9;
    private static final int CONTEXT_PS_SET_SAMPLERS = 10;
    private static final int CONTEXT_VS_SET_SHADER = 11;
    private static final int CONTEXT_DRAW = 13;
    private static final int CONTEXT_IA_SET_PRIMITIVE_TOPOLOGY = 24;
    private static final int CONTEXT_OM_SET_RENDER_TARGETS = 33;
    private static final int CONTEXT_RS_SET_STATE = 43;
    private static final int CONTEXT_RS_SET_VIEWPORTS = 44;
    private static final int CONTEXT_CLEAR_STATE = 110;
    private static final int FACTORY2_CREATE_SWAP_CHAIN_FOR_HWND = 15;
    private static final int SWAP_CHAIN_PRESENT = 8;
    private static final int SWAP_CHAIN_GET_BUFFER = 9;
    private static final int SWAP_CHAIN_RESIZE_BUFFERS = 13;
    private static final int SWAP_CHAIN2_SET_MAXIMUM_FRAME_LATENCY = 31;
    private static final int SWAP_CHAIN2_GET_FRAME_LATENCY_WAITABLE_OBJECT = 33;
    private static final int TEXTURE_2D_GET_DESC = 10;
    private static final int BLOB_GET_BUFFER_POINTER = 3;
    private static final int BLOB_GET_BUFFER_SIZE = 4;

    private static final long IID_IDXGI_FACTORY2 = Com.guid(0x50c83a1c, 0xe072, 0x4c48, 0x87, 0xb0, 0x36, 0x30, 0xfa, 0x36, 0xa6, 0xd0);
    private static final long IID_IDXGI_SWAP_CHAIN2 = Com.guid(0xa8be2ac4, 0x199f, 0x4946, 0xb3, 0x31, 0x79, 0x59, 0x9f, 0xb9, 0x8d, 0xe7);
    private static final long IID_ID3D11_TEXTURE2D = Com.guid(0x6f15aaf2, 0xd208, 0x4e89, 0x9a, 0xb4, 0x48, 0x95, 0x35, 0xd3, 0x4f, 0x9c);

    // Struct sizes (x64): DXGI_SWAP_CHAIN_DESC1 48, D3D11_RASTERIZER_DESC 40, D3D11_SAMPLER_DESC 52,
    // D3D11_TEXTURE2D_DESC 44, D3D11_VIEWPORT 24.
    private static final int SWAP_CHAIN_DESC1_SIZE = 48;
    private static final int RASTERIZER_DESC_SIZE = 40;
    private static final int SAMPLER_DESC_SIZE = 52;
    private static final int TEXTURE2D_DESC_SIZE = 44;
    private static final int VIEWPORT_SIZE = 24;

    private final WglContext openglContext;
    private final long interopDevice;
    private final long device;
    private final long context;
    private final long swapChain;
    private final long waitHandle;
    private final long waitForSingleObject = Com.function("kernel32.dll", "WaitForSingleObject");
    private final long closeHandle = Com.function("kernel32.dll", "CloseHandle");
    private long renderTargetView;

    private final Map<Integer, SharedTexture> sharedTextures = new HashMap<>();
    private int syncInterval = 1;

    InteropContext(long hwnd, WglContext.ContextFlags flags) {
        openglContext = new WglContext(flags);
        try (MemoryStack stack = stackPush()) {
            long out = stack.nmalloc(8, 8);
            long out2 = stack.nmalloc(8, 8);

            Com.checkHr("D3D11CreateDevice", (int) Ffi.call("PIPIPIIPPP:I", Com.function("d3d11.dll", "D3D11CreateDevice"),
                    0L, D3D_DRIVER_TYPE_HARDWARE, 0L, D3D11_CREATE_DEVICE_BGRA_SUPPORT, 0L, 0, D3D11_SDK_VERSION, out, 0L, out2));
            device = memGetAddress(out);
            context = memGetAddress(out2);

            interopDevice = check(wglDXOpenDeviceNV(device));

            Com.checkHr("CreateDXGIFactory1", (int) Ffi.call("PP:I", Com.function("dxgi.dll", "CreateDXGIFactory1"), IID_IDXGI_FACTORY2, out));
            long factory = memGetAddress(out);

            long desc = calloc(stack, SWAP_CHAIN_DESC1_SIZE);
            memPutInt(desc + 8, DXGI_FORMAT_B8G8R8A8_UNORM);   // Format
            memPutInt(desc + 16, 1);                           // SampleDesc.Count
            memPutInt(desc + 20, 0);                           // SampleDesc.Quality
            memPutInt(desc + 24, DXGI_USAGE_RENDER_TARGET_OUTPUT);
            memPutInt(desc + 28, 2);                           // BufferCount
            memPutInt(desc + 32, DXGI_SCALING_NONE);
            memPutInt(desc + 36, DXGI_SWAP_EFFECT_FLIP_DISCARD);
            memPutInt(desc + 40, DXGI_ALPHA_MODE_UNSPECIFIED);
            memPutInt(desc + 44, SWAP_CHAIN_FLAGS);
            Com.check("CreateSwapChainForHwnd", factory, FACTORY2_CREATE_SWAP_CHAIN_FOR_HWND, "PPPPPP", device, hwnd, desc, 0L, 0L, out);
            long swapChain1 = memGetAddress(out);
            Com.check("QueryInterface(IDXGISwapChain2)", swapChain1, Com.QUERY_INTERFACE, "PP", IID_IDXGI_SWAP_CHAIN2, out);
            swapChain = memGetAddress(out);

            Com.check("SetMaximumFrameLatency", swapChain, SWAP_CHAIN2_SET_MAXIMUM_FRAME_LATENCY, "I", 1);
            waitHandle = Com.call(swapChain, SWAP_CHAIN2_GET_FRAME_LATENCY_WAITABLE_OBJECT, ":P");

            Com.release(swapChain1);
            Com.release(factory);

            ByteBuffer source = stack.UTF8(BLIT_SHADER_SOURCE, false);

            long vertexBlob = compileShader(stack, source, "VsMain", "vs_5_0");
            Com.check("CreateVertexShader", device, DEVICE_CREATE_VERTEX_SHADER, "PPPP", blobPointer(vertexBlob), blobSize(vertexBlob), 0L, out);
            long vertexShader = memGetAddress(out);
            Com.release(vertexBlob);

            long pixelBlob = compileShader(stack, source, "PsMain", "ps_5_0");
            Com.check("CreatePixelShader", device, DEVICE_CREATE_PIXEL_SHADER, "PPPP", blobPointer(pixelBlob), blobSize(pixelBlob), 0L, out);
            long pixelShader = memGetAddress(out);
            Com.release(pixelBlob);

            long rasterizerDesc = calloc(stack, RASTERIZER_DESC_SIZE);
            memPutInt(rasterizerDesc, D3D11_FILL_SOLID);
            memPutInt(rasterizerDesc + 4, D3D11_CULL_NONE);
            Com.check("CreateRasterizerState", device, DEVICE_CREATE_RASTERIZER_STATE, "PP", rasterizerDesc, out);
            long rasterizerState = memGetAddress(out);

            long samplerDesc = calloc(stack, SAMPLER_DESC_SIZE);
            memPutInt(samplerDesc, D3D11_FILTER_MIN_MAG_MIP_POINT);
            memPutInt(samplerDesc + 4, D3D11_TEXTURE_ADDRESS_CLAMP);
            memPutInt(samplerDesc + 8, D3D11_TEXTURE_ADDRESS_CLAMP);
            memPutInt(samplerDesc + 12, D3D11_TEXTURE_ADDRESS_CLAMP);
            Com.check("CreateSamplerState", device, DEVICE_CREATE_SAMPLER_STATE, "PP", samplerDesc, out);
            long samplerState = memGetAddress(out);

            Com.call(context, CONTEXT_IA_SET_PRIMITIVE_TOPOLOGY, "I:V", D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
            Com.call(context, CONTEXT_VS_SET_SHADER, "PPI:V", vertexShader, 0L, 0);
            Com.call(context, CONTEXT_RS_SET_STATE, "P:V", rasterizerState);
            Com.call(context, CONTEXT_PS_SET_SHADER, "PPI:V", pixelShader, 0L, 0);
            memPutAddress(out, samplerState);
            Com.call(context, CONTEXT_PS_SET_SAMPLERS, "IIP:V", 0, 1, out);

            Com.release(vertexShader);
            Com.release(pixelShader);
            Com.release(rasterizerState);
            Com.release(samplerState);
        }
    }

    private static long calloc(MemoryStack stack, int size) {
        return memAddress(stack.calloc(8, size));
    }

    private static long blobPointer(long blob) {
        return Com.call(blob, BLOB_GET_BUFFER_POINTER, ":P");
    }

    private static long blobSize(long blob) {
        return Com.call(blob, BLOB_GET_BUFFER_SIZE, ":P");
    }

    private static long compileShader(MemoryStack stack, ByteBuffer source, String entryPoint, String target) {
        long code = stack.nmalloc(8, 8);
        long errors = stack.nmalloc(8, 8);
        memPutAddress(code, 0L);
        memPutAddress(errors, 0L);
        int hr = (int) Ffi.call("PPPPPPPIIPP:I", Com.function("d3dcompiler_47.dll", "D3DCompile"),
                memAddress(source), source.remaining(), 0L, 0L, 0L,
                memAddress(stack.ASCII(entryPoint)), memAddress(stack.ASCII(target)), 0, 0, code, errors);
        long errorBlob = memGetAddress(errors);
        if (hr < 0) {
            String message = errorBlob == 0L ? "" : org.lwjgl.system.MemoryUtil.memUTF8(blobPointer(errorBlob), (int) blobSize(errorBlob));
            Com.release(errorBlob);
            throw new IllegalStateException(String.format("D3DCompile %s failed: HRESULT 0x%08X %s", entryPoint, hr, message));
        }
        Com.release(errorBlob);
        return memGetAddress(code);
    }

    void makeCurrent() {
        openglContext.makeCurrent();
    }

    void setSyncInterval(int interval) {
        // Sodium Extra's adaptive sync asks for -1; a swap chain has no adaptive mode, so that means no vsync.
        this.syncInterval = Math.max(0, interval);
    }

    void waitForSwapChainSignal() {
        int result = (int) Ffi.call("PI:I", waitForSingleObject, waitHandle, 1000);
        switch (result) {
            case 0 -> { }
            case WAIT_ABANDONED -> LOG.warn("Perfect Presentation: swap chain wait abandoned unexpectedly");
            case WAIT_TIMEOUT -> LOG.warn("Perfect Presentation: swap chain wait timed out");
            case WAIT_FAILED -> LOG.warn("Perfect Presentation: swap chain wait failed");
            default -> { }
        }
    }

    void present() {
        openglContext.swapBuffers();
        Com.check("Present", swapChain, SWAP_CHAIN_PRESENT, "II", syncInterval, syncInterval == 0 ? DXGI_PRESENT_ALLOW_TEARING : 0);
    }

    void resizeSwapChain(int width, int height) {
        Com.release(renderTargetView);
        renderTargetView = 0L;
        Com.check("ResizeBuffers", swapChain, SWAP_CHAIN_RESIZE_BUFFERS, "IIIII", 0, width, height, DXGI_FORMAT_UNKNOWN, SWAP_CHAIN_FLAGS);
    }

    void blitSharedTextureToSwapChain(int glTexture) {
        SharedTexture texture = sharedTextures.get(glTexture);
        if (texture == null) {
            throw new IllegalStateException("No shared texture allocated for GL texture " + glTexture);
        }
        texture.unlock();
        try (MemoryStack stack = stackPush()) {
            long out = stack.nmalloc(8, 8);
            if (renderTargetView == 0L) {
                Com.check("GetBuffer", swapChain, SWAP_CHAIN_GET_BUFFER, "IPP", 0, IID_ID3D11_TEXTURE2D, out);
                long backBuffer = memGetAddress(out);
                Com.check("CreateRenderTargetView", device, DEVICE_CREATE_RENDER_TARGET_VIEW, "PPP", backBuffer, 0L, out);
                renderTargetView = memGetAddress(out);

                long backBufferDesc = calloc(stack, TEXTURE2D_DESC_SIZE);
                Com.call(backBuffer, TEXTURE_2D_GET_DESC, "P:V", backBufferDesc);
                long viewport = calloc(stack, VIEWPORT_SIZE);
                memPutFloat(viewport + 8, (float) Integer.toUnsignedLong(memGetInt(backBufferDesc)));      // Width
                memPutFloat(viewport + 12, (float) Integer.toUnsignedLong(memGetInt(backBufferDesc + 4))); // Height
                memPutFloat(viewport + 20, 1.0f);                                                          // MaxDepth
                Com.call(context, CONTEXT_RS_SET_VIEWPORTS, "IP:V", 1, viewport);
                Com.release(backBuffer);
            }
            memPutAddress(out, texture.textureView);
            Com.call(context, CONTEXT_PS_SET_SHADER_RESOURCES, "IIP:V", 0, 1, out);
            long views = stack.nmalloc(8, 8);
            memPutAddress(views, renderTargetView);
            Com.call(context, CONTEXT_OM_SET_RENDER_TARGETS, "IPP:V", 1, views, 0L);
            Com.call(context, CONTEXT_DRAW, "II:V", 3, 0);
        }
        texture.lock();
    }

    boolean hasSharedTexture(int glTexture) {
        return sharedTextures.containsKey(glTexture);
    }

    void allocateSharedTexture(int glTexture, int glTarget, int glFormat, int width, int height) {
        if (sharedTextures.containsKey(glTexture)) {
            throw new IllegalStateException("Shared texture already allocated for GL texture " + glTexture);
        }
        SharedTexture texture = new SharedTexture(glTexture, glTarget, glFormat, width, height);
        texture.lock();
        sharedTextures.put(glTexture, texture);
    }

    void deallocateSharedTexture(int glTexture) {
        SharedTexture texture = sharedTextures.remove(glTexture);
        if (texture == null) {
            throw new IllegalStateException("No shared texture allocated for GL texture " + glTexture);
        }
        texture.close();
    }

    @Override
    public void close() {
        Com.call(context, CONTEXT_CLEAR_STATE, ":V");
        for (SharedTexture texture : sharedTextures.values()) {
            texture.close();
        }
        sharedTextures.clear();
        Com.release(renderTargetView);
        renderTargetView = 0L;
        Ffi.call("P:I", closeHandle, waitHandle);
        Com.release(swapChain);
        wglDXCloseDeviceNV(interopDevice);
        Com.release(context);
        Com.release(device);
        openglContext.close();
    }

    private final class SharedTexture implements AutoCloseable {
        private final long textureView;
        private final long interopHandle;
        private boolean locked;

        SharedTexture(int glTexture, int glTarget, int glFormat, int width, int height) {
            if (glFormat != GL_RGBA8) {
                throw new IllegalStateException("Unexpected main target format 0x" + Integer.toHexString(glFormat));
            }
            try (MemoryStack stack = stackPush()) {
                long out = stack.nmalloc(8, 8);
                long desc = calloc(stack, TEXTURE2D_DESC_SIZE);
                memPutInt(desc, width);
                memPutInt(desc + 4, height);
                memPutInt(desc + 8, 1);                                  // MipLevels
                memPutInt(desc + 12, 1);                                 // ArraySize
                memPutInt(desc + 16, DXGI_FORMAT_R8G8B8A8_UNORM);
                memPutInt(desc + 20, 1);                                 // SampleDesc.Count
                memPutInt(desc + 24, 0);                                 // SampleDesc.Quality
                memPutInt(desc + 28, D3D11_USAGE_DEFAULT);
                memPutInt(desc + 32, D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE);
                Com.check("CreateTexture2D", device, DEVICE_CREATE_TEXTURE_2D, "PPP", desc, 0L, out);
                long texture = memGetAddress(out);
                Com.check("CreateShaderResourceView", device, DEVICE_CREATE_SHADER_RESOURCE_VIEW, "PPP", texture, 0L, out);
                textureView = memGetAddress(out);
                interopHandle = check(wglDXRegisterObjectNV(interopDevice, texture, glTexture, glTarget, WGL_ACCESS_WRITE_DISCARD_NV));
                Com.release(texture);
            }
        }

        void lock() {
            if (locked) {
                LOG.warn("Perfect Presentation: shared texture is already locked");
                return;
            }
            try (MemoryStack stack = stackPush()) {
                if (!wglDXLockObjectsNV(interopDevice, stack.callocPointer(1).put(0, interopHandle))) {
                    WindowsUtil.windowsThrowException("Failed to lock the shared texture");
                }
            }
            locked = true;
        }

        void unlock() {
            if (!locked) {
                LOG.warn("Perfect Presentation: shared texture is already unlocked");
                return;
            }
            try (MemoryStack stack = stackPush()) {
                if (!wglDXUnlockObjectsNV(interopDevice, stack.callocPointer(1).put(0, interopHandle))) {
                    WindowsUtil.windowsThrowException("Failed to unlock the shared texture");
                }
            }
            locked = false;
        }

        @Override
        public void close() {
            if (locked) {
                unlock();
            }
            if (!wglDXUnregisterObjectNV(interopDevice, interopHandle)) {
                WindowsUtil.windowsThrowException("Failed to unregister the shared texture");
            }
            Com.release(textureView);
        }
    }

    private static final String BLIT_SHADER_SOURCE = """
            struct VSOut
            {
                float4 pos : SV_Position;
                float2 uv  : TEXCOORD0;
            };

            VSOut VsMain(uint id : SV_VertexID)
            {
                VSOut o;
                o.pos = float4(id >> 1, id & 1, 0, 0.5) * 4 - 1;
                o.uv  = float2(id >> 1, id & 1) * 2;

                return o;
            }

            Texture2D srcTex : register(t0);
            SamplerState samp : register(s0);

            float4 PsMain(VSOut i) : SV_Target
            {
                return srcTex.Sample(samp, i.uv);
            }
            """;
}

package dev.creategalore.bridge.present;

import static org.lwjgl.opengl.EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT;
import static org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.EXTMemoryObject.glMemoryObjectParameteriEXT;
import static org.lwjgl.opengl.EXTMemoryObject.glTextureStorageMem2DEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.opengl.EXTSemaphore.glDeleteSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphore.glGetUnsignedBytevEXT;
import static org.lwjgl.opengl.EXTSemaphore.glSemaphoreParameterui64EXT;
import static org.lwjgl.opengl.EXTSemaphore.glSignalSemaphoreEXT;
import static org.lwjgl.opengl.EXTSemaphore.glWaitSemaphoreEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_DEVICE_LUID_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_DEVICE_NODE_MASK_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_LUID_SIZE_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL43C.glCopyImageSubData;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glGetTextureImage;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memUTF8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;

import dev.creategalore.bridge.DisplaySettings;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the game's frames through a Direct3D 12 flip-model swap chain on the game window (presenter=dx12, an
 * experimental choice on the settings screen). The Direct3D side is cg_present.dll (tools/bridge-mod/native/cg_present.cpp). Each
 * frame the game's colour texture is copied into one of three textures that Direct3D 12 made and OpenGL imported
 * (EXT_memory_object_win32), and two Direct3D 12 fences imported as OpenGL semaphores (EXT_semaphore_win32) order the
 * two APIs on the GPU: OpenGL signals one after its copy, the D3D12 queue waits for it before drawing that texture to
 * the back buffer and signals the other, which OpenGL waits for before writing the texture again.
 *
 * Same swap chain, flags, frame latency 1 and blit shader as {@link InteropContext}, which stays the fallback: this
 * needs both extensions, which NVIDIA's OpenGL driver has (Intel's lacks the D3D12 handle types; AMD's is untested).
 * OpenGL waits for Direct3D 12 only when the CPU sees the texture's last draw isn't finished yet (three frames back,
 * it almost always is). The PC tests of Bridge 1.114.0 measured the other choices cg_present.dll still offers (waiting
 * every frame, GL_NVX_progress_fence, frame latency 2, three buffers, a high priority queue) as no better or worse.
 *
 * Testing only: presentTiming=true logs GPU timestamps around the hand-off ({@link PresentTiming}; read again whenever
 * the display settings change); presentCheck=true compares two presented back buffers with the game's frames, pixel by
 * pixel, and logs it.
 */
final class D3D12Presenter implements Presenter {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int ABI = 2;
    private static final int RING_SIZE = 3;
    private static final int WAIT_ABANDONED = 0x80;
    private static final int WAIT_TIMEOUT = 0x102;
    private static final int WAIT_FAILED = 0xFFFFFFFF;
    /** The frames (counted from the start) whose back buffers presentCheck compares: a menu, then likely the world. */
    private static final int CHECK_FRAME = 600;
    private static final int CHECK_FRAME_LATER = 6000;

    private final WglContext openglContext;
    private final long error;
    private final long createTextures;
    private final long releaseTextures;
    private final long waitFrame;
    private final long draw;
    private final long presentFrame;
    private final long resize;
    private final long destroy;
    private final long captureRequest;
    private final long captureRead;
    private final long completed;
    private final long timingSwitch;
    private final long nowNs;
    private final NativeHelper dll;
    private final long device;
    private final String adapter;
    private final int openglDone;
    private final int directxDone;
    private long openglValue;

    private final int[] textures = new int[RING_SIZE];
    private final int[] memories = new int[RING_SIZE];
    private final long[] slotDone = new long[RING_SIZE];
    private int ringIndex;
    private int ringWidth;
    private int ringHeight;
    private int syncInterval = 1;
    private final boolean check;
    private int frames;

    private int settingsGeneration = -1;
    private PresentTiming timing;

    /**
     * Starts Direct3D 12 on {@code hwnd}, on the GPU {@code openglContext} runs on, and leaves that context current.
     * Throws if anything is missing; then nothing is left behind and the caller still owns {@code openglContext}.
     */
    D3D12Presenter(long hwnd, WglContext openglContext, Path folder, boolean check) throws IOException {
        this.openglContext = openglContext;
        this.check = check;
        openglContext.makeCurrent();
        GLCapabilities caps = GL.createCapabilities();
        if (!caps.GL_EXT_memory_object_win32 || !caps.GL_EXT_semaphore_win32 || !caps.OpenGL45) {
            throw new IllegalStateException("the OpenGL driver lacks " + (!caps.OpenGL45 ? "OpenGL 4.5"
                    : !caps.GL_EXT_memory_object_win32 ? "GL_EXT_memory_object_win32" : "GL_EXT_semaphore_win32"));
        }
        dll = NativeHelper.load("cg_present", folder);
        dll.checkAbi(ABI);
        error = dll.symbol("cg_present_error");
        createTextures = dll.symbol("cg_present_create_textures");
        releaseTextures = dll.symbol("cg_present_release_textures");
        waitFrame = dll.symbol("cg_present_wait");
        draw = dll.symbol("cg_present_draw");
        presentFrame = dll.symbol("cg_present_present");
        resize = dll.symbol("cg_present_resize");
        destroy = dll.symbol("cg_present_destroy");
        captureRequest = dll.symbol("cg_present_capture_request");
        captureRead = dll.symbol("cg_present_capture_read");
        completed = dll.symbol("cg_present_completed");
        timingSwitch = dll.symbol("cg_present_timing");
        nowNs = dll.symbol("cg_present_now_ns");

        try (MemoryStack stack = stackPush()) {
            // The LUID of the adapter OpenGL runs on (EXT_external_objects_win32), so Direct3D 12 opens the same one.
            ByteBuffer luid = stack.calloc(GL_LUID_SIZE_EXT);
            glGetUnsignedBytevEXT(GL_DEVICE_LUID_EXT, luid);
            IntBuffer nodeMask = stack.callocInt(1);
            glGetIntegerv(GL_DEVICE_NODE_MASK_EXT, nodeMask);
            boolean known = false;
            for (int i = 0; i < GL_LUID_SIZE_EXT; i++) {
                known |= luid.get(i) != 0;
            }
            if (!known) {
                LOG.warn("Perfect Presentation: OpenGL gave no adapter LUID, DirectX 12 takes the first adapter");
            }
            if ((int) Ffi.call("PPI:I", dll.symbol("cg_present_create"), hwnd, known ? memAddress(luid) : 0L, 0) == 0) {
                throw new IllegalStateException(errorText());
            }
            ByteBuffer name = stack.calloc(256);
            Ffi.call("PI:V", dll.symbol("cg_present_adapter_name"), memAddress(name), name.capacity());
            adapter = memUTF8(memAddress(name));
            device = Ffi.call(":P", dll.symbol("cg_present_device"));
            boolean tearing = Ffi.call(":I", dll.symbol("cg_present_tearing")) != 0;

            drainGlErrors();
            openglDone = importFence(Ffi.call("I:P", dll.symbol("cg_present_fence_handle"), 0));
            directxDone = importFence(Ffi.call("I:P", dll.symbol("cg_present_fence_handle"), 1));
            int glError = glGetError();
            if (glError != GL_NO_ERROR) {
                glDeleteSemaphoresEXT(openglDone);
                glDeleteSemaphoresEXT(directxDone);
                Ffi.call(":V", destroy);
                throw new IllegalStateException(String.format("OpenGL could not import the DirectX 12 fences (GL error 0x%04X)", glError));
            }
            LOG.info("Perfect Presentation: DirectX 12 on {} (OpenGL node mask {}, tearing {})", adapter,
                    nodeMask.get(0), tearing ? "allowed" : "not supported");
        }
    }

    private static int importFence(long handle) {
        int semaphore = glGenSemaphoresEXT();
        glImportSemaphoreWin32HandleEXT(semaphore, GL_HANDLE_TYPE_D3D12_FENCE_EXT, handle);
        return semaphore;
    }

    private static void drainGlErrors() {
        for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) { }
    }

    private String errorText() {
        return "DirectX 12: " + memUTF8(Ffi.call(":P", error));
    }

    @Override
    public long device() {
        return device;
    }

    @Override
    public String api() {
        return "DirectX 12";
    }

    @Override
    public void makeCurrent() {
        openglContext.makeCurrent();
    }

    @Override
    public void setSyncInterval(int interval) {
        // Sodium Extra's adaptive sync asks for -1; a swap chain has no adaptive mode, so that means no vsync.
        syncInterval = Math.max(0, interval);
    }

    @Override
    public void waitForSwapChainSignal() {
        int result = (int) Ffi.call("I:I", waitFrame, 1000);
        switch (result) {
            case 0 -> { }
            case WAIT_ABANDONED -> LOG.warn("Perfect Presentation: swap chain wait abandoned unexpectedly");
            case WAIT_TIMEOUT -> LOG.warn("Perfect Presentation: swap chain wait timed out");
            case WAIT_FAILED -> LOG.warn("Perfect Presentation: swap chain wait failed");
            default -> { }
        }
    }

    /** Testing: presentTiming, read again whenever the display settings change. */
    private void applySettings() {
        int generation = DisplaySettings.generation();
        if (generation == settingsGeneration) {
            return;
        }
        settingsGeneration = generation;
        boolean timed = DisplaySettings.get(DisplaySettings.PRESENT_TIMING);
        if (timed && timing == null) {
            if (Ffi.call("I:I", timingSwitch, 1) != 0) {
                timing = new PresentTiming(dll, nowNs);
            } else {
                LOG.warn("Perfect Presentation timing: {}", errorText());
            }
        } else if (!timed && timing != null) {
            Ffi.call("I:I", timingSwitch, 0);
            timing.close();
            timing = null;
        }
    }

    @Override
    public void blitToSwapChain(int glTexture) {
        applySettings();
        int width = glGetTextureLevelParameteri(glTexture, 0, GL_TEXTURE_WIDTH);
        int height = glGetTextureLevelParameteri(glTexture, 0, GL_TEXTURE_HEIGHT);
        if (width != ringWidth || height != ringHeight) {
            createRing(width, height);
        }
        int slot = ringIndex;
        ringIndex = (ringIndex + 1) % RING_SIZE;
        try (MemoryStack stack = stackPush()) {
            IntBuffer noBuffers = stack.mallocInt(0);
            IntBuffer texture = stack.ints(textures[slot]);
            IntBuffer layout = stack.ints(GL_LAYOUT_GENERAL_EXT);
            // Direct3D 12 drew from this texture a few frames ago; OpenGL may overwrite it once that is done, which the
            // CPU can usually see already (then a GPU wait would only cost a trip through the scheduler).
            long lastDraw = slotDone[slot];
            boolean wait = lastDraw != 0L && Ffi.call(":P", completed) < lastDraw;
            if (timing != null) {
                timing.beforeHandOff(openglValue + 1, wait);
            }
            if (wait) {
                glSemaphoreParameterui64EXT(directxDone, GL_D3D12_FENCE_VALUE_EXT, lastDraw);
                glWaitSemaphoreEXT(directxDone, noBuffers, texture, layout);
            }
            glCopyImageSubData(glTexture, GL_TEXTURE_2D, 0, 0, 0, 0, textures[slot], GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
            openglValue++;
            if (timing != null) {
                timing.afterCopy(openglValue);
            }
            glSemaphoreParameterui64EXT(openglDone, GL_D3D12_FENCE_VALUE_EXT, openglValue);
            glSignalSemaphoreEXT(openglDone, noBuffers, texture, layout); // also flushes, as the spec says
        }
        frames++;
        boolean checking = check && (frames == CHECK_FRAME || frames == CHECK_FRAME_LATER);
        ByteBuffer frame = null;
        if (checking) {
            frame = memAlloc(width * height * 4);
            glGetTextureImage(glTexture, 0, GL_RGBA, GL_UNSIGNED_BYTE, frame);
            if (Ffi.call(":I", captureRequest) == 0) {
                LOG.warn("Perfect Presentation check: {}", errorText());
                memFree(frame);
                frame = null;
            }
        }
        long done = Ffi.call("IP:P", draw, slot, openglValue);
        if (done < 0) {
            throw new IllegalStateException(errorText());
        }
        slotDone[slot] = done;
        if (frame != null) {
            compare(frame, width, height);
            memFree(frame);
        }
        if (timing != null) {
            timing.frameDone();
        }
    }

    /** Testing (presentCheck): the drawn back buffer against the game's frame, rows flipped and BGRA against RGBA. */
    private void compare(ByteBuffer frame, int width, int height) {
        long capacity = 16L * 1024 * 1024 * 4;
        ByteBuffer back = memAlloc((int) capacity);
        try (MemoryStack stack = stackPush()) {
            IntBuffer size = stack.mallocInt(2);
            if (Ffi.call("PPP:I", captureRead, memAddress(back), capacity, memAddress(size)) == 0) {
                LOG.warn("Perfect Presentation check: {}", errorText());
                return;
            }
            int backWidth = size.get(0);
            int backHeight = size.get(1);
            if (backWidth != width || backHeight != height) {
                LOG.info("Perfect Presentation check: back buffer {}x{}, game frame {}x{}, not compared", backWidth, backHeight, width, height);
                return;
            }
            long differ = 0;
            int maxDelta = 0;
            String first = null;
            for (int y = 0; y < height; y++) {
                int gameRow = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    int b = ((y * width) + x) * 4;
                    int g = ((gameRow * width) + x) * 4;
                    int dr = Math.abs((back.get(b + 2) & 0xFF) - (frame.get(g) & 0xFF));
                    int dg = Math.abs((back.get(b + 1) & 0xFF) - (frame.get(g + 1) & 0xFF));
                    int db = Math.abs((back.get(b) & 0xFF) - (frame.get(g + 2) & 0xFF));
                    int delta = Math.max(dr, Math.max(dg, db));
                    if (delta != 0) {
                        if (first == null) {
                            first = String.format("first at (%d, %d) from the top left: delta %d", x, y, delta);
                        }
                        differ++;
                        maxDelta = Math.max(maxDelta, delta);
                    }
                }
            }
            if (differ == 0) {
                LOG.info("Perfect Presentation check: DirectX 12 back buffer {}x{} matches the game's frame exactly", width,
                        height);
            } else {
                LOG.warn("Perfect Presentation check: {} of {} pixels differ from the game's frame (largest delta {}; {})",
                        differ, (long) width * height, maxDelta, first);
            }
        } finally {
            memFree(back);
        }
    }

    private void createRing(int width, int height) {
        closeRing();
        try (MemoryStack stack = stackPush()) {
            long handles = stack.nmalloc(8, RING_SIZE * 8);
            long sizes = stack.nmalloc(8, RING_SIZE * 8);
            if (Ffi.call("IIPP:I", createTextures, width, height, handles, sizes) == 0) {
                throw new IllegalStateException(errorText());
            }
            for (int i = 0; i < RING_SIZE; i++) {
                importTexture(i, memGetAddress(handles + i * 8L), memGetLong(sizes + i * 8L), width, height);
            }
        }
        ringWidth = width;
        ringHeight = height;
        ringIndex = 0;
    }

    /**
     * Imports shared texture {@code i}. The spec ignores the size for D3D12 resources and notes some drivers want 0;
     * others check it against the allocation, so the allocation size goes first and 0 second.
     */
    private void importTexture(int i, long handle, long size, int width, int height) {
        int glError = GL_NO_ERROR;
        for (long tried : new long[] {size, 0L}) {
            drainGlErrors();
            memories[i] = glCreateMemoryObjectsEXT();
            glMemoryObjectParameteriEXT(memories[i], GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
            glImportMemoryWin32HandleEXT(memories[i], tried, GL_HANDLE_TYPE_D3D12_RESOURCE_EXT, handle);
            textures[i] = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorageMem2DEXT(textures[i], 1, GL_RGBA8, width, height, memories[i], 0L);
            glError = glGetError();
            if (glError == GL_NO_ERROR) {
                slotDone[i] = 0L;
                if (tried != size) {
                    LOG.info("Perfect Presentation: OpenGL took the DirectX 12 textures with size 0");
                }
                return;
            }
            glDeleteTextures(textures[i]);
            glDeleteMemoryObjectsEXT(memories[i]);
            textures[i] = 0;
            memories[i] = 0;
        }
        closeRing();
        throw new IllegalStateException(String.format("OpenGL could not import the DirectX 12 texture (GL error 0x%04X)", glError));
    }

    private void closeRing() {
        boolean any = false;
        for (int i = 0; i < RING_SIZE; i++) {
            any |= textures[i] != 0;
            if (textures[i] != 0) {
                glDeleteTextures(textures[i]);
                textures[i] = 0;
            }
            if (memories[i] != 0) {
                glDeleteMemoryObjectsEXT(memories[i]);
                memories[i] = 0;
            }
            slotDone[i] = 0L;
        }
        if (any) {
            glFinish(); // OpenGL is done with the textures before Direct3D 12 frees them
        }
        Ffi.call(":V", releaseTextures);
        ringWidth = 0;
        ringHeight = 0;
    }

    @Override
    public void present() {
        int hr = (int) Ffi.call("I:I", presentFrame, syncInterval);
        if (hr < 0) {
            throw new IllegalStateException(errorText());
        }
    }

    @Override
    public void resizeSwapChain(int width, int height) {
        if ((int) Ffi.call("II:I", resize, width, height) < 0) {
            throw new IllegalStateException(errorText());
        }
    }

    @Override
    public void close() {
        if (timing != null) {
            timing.close();
            timing = null;
        }
        closeRing();
        glDeleteSemaphoresEXT(openglDone);
        glDeleteSemaphoresEXT(directxDone);
        Ffi.call(":V", destroy);
        openglContext.close();
    }
}

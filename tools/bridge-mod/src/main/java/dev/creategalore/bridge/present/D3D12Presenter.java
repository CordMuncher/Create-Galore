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
import dev.creategalore.bridge.framegen.FrameGen;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the game's frames through a Direct3D 12 flip-model swap chain on the game window (presenter=dx12, a choice on
 * the settings screen and a fresh install's). The Direct3D side is cg_present.dll (tools/bridge-mod/native/cg_present.cpp). Each
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
 *
 * Frame generation ({@link FrameGen}): its backend (Streamline, or AMD's FidelityFX) starts with the presenter
 * (cg_fg_prepare) and, while frame generation is on, owns the real swap chain; turning it on or off makes the swap
 * chain again between two frames (cg_fg_enable). A second ring of slots, four textures each (the inputs, in FrameGen.INPUT_FORMATS),
 * goes with the first: each frame FrameGen writes the slot's inputs after the copy, both are in the semaphores' texture
 * lists, cg_fg_frame hands the slot and the camera over before the draw, and OpenGL writes a slot again only after the
 * backend let go of its inputs (cg_fg_inputs_done); the ring goes while frame generation is off. A swap chain resize
 * or a switch while frames are generated waits for a frame to go out without (NVIDIA: frame generation off around
 * window changes).
 */
final class D3D12Presenter implements Presenter {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int ABI = 6;
    /** Present's DXGI_STATUS_OCCLUDED: the window can't be seen (minimized). */
    private static final int DXGI_STATUS_OCCLUDED = 0x087A0001;
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
    private final long fullscreenState;
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

    /** Frame generation's backend runs (on or off). */
    private boolean frameGeneration;
    /** Its backend, FrameGen.BACKEND_DLSSG or BACKEND_FSR. */
    private int fgBackend;
    /** The backend prepared (Streamline's, or AMD's after Streamline made way). */
    private FrameGen.Start fgStart;
    /** Frame generation is on: its backend owns the swap chain behind this presenter's. */
    private boolean fgOn;
    private long fgEnable;
    /** Turning it on or off failed once: not tried again. */
    private boolean fgSwitchFailed;
    private long fgFrame;
    private long fgInputsDone;
    private long fgState;
    private long fgLogNext;
    private long fgCreateInputs;
    private long fgReleaseInputs;
    private final int[][] fgTextures = new int[RING_SIZE][FrameGen.INPUTS];
    private final int[][] fgMemories = new int[RING_SIZE][FrameGen.INPUTS];
    private int fgWidth;
    private int fgHeight;
    private long fgOff;
    /** The frame drawn and not yet presented asked for generated frames. */
    private boolean presentGenerates;
    /** A frame went to frame generation's backend (cg_fg_frame) since the last Present. */
    private boolean frameSent;
    private boolean resizePending;
    private int pendingWidth;
    private int pendingHeight;

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
        fullscreenState = dll.symbol("cg_present_fullscreen_state");

        FrameGen.Start frameGen = FrameGen.start();
        boolean prepared;

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
            prepared = frameGen != null && prepareFrameGeneration(frameGen, known ? memAddress(luid) : 0L);
            if ((int) Ffi.call("PPI:I", dll.symbol("cg_present_create"), hwnd, known ? memAddress(luid) : 0L, 0) == 0) {
                if (prepared) {
                    drainFrameGenLog();
                    FrameGen.unavailable("the DirectX 12 presenter could not start");
                }
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
        if (prepared) {
            frameGeneration = Ffi.call(":I", dll.symbol("cg_fg_active")) != 0;
            drainFrameGenLog();
            if (frameGeneration) {
                fgBackend = fgStart.backend();
                fgOn = fgStart.on();
                fgFrame = dll.symbol("cg_fg_frame");
                fgInputsDone = dll.symbol("cg_fg_inputs_done");
                fgState = dll.symbol("cg_fg_state");
                fgCreateInputs = dll.symbol("cg_fg_create_inputs");
                fgReleaseInputs = dll.symbol("cg_fg_release_inputs");
                fgOff = dll.symbol("cg_fg_off");
                FrameGen.started(adapter, fgOn);
            } else {
                FrameGen.unavailable(memUTF8(Ffi.call(":P", dll.symbol("cg_fg_error"))));
            }
        }
    }

    /**
     * Starts frame generation's backend before the swap chain exists (cg_fg_prepare), on the adapter {@code luid}
     * (OpenGL's, 0: the first). Streamline comes first: it says whether DLSS Frame Generation runs here
     * (cg_fg_supports), then stays or makes way for AMD's (cg_fg_unprepare), as {@link FrameGen#answered} decides. False
     * (logged) if none can start; else {@link #fgStart} is the one that did.
     */
    private boolean prepareFrameGeneration(FrameGen.Start start, long luid) {
        fgLogNext = dll.symbol("cg_fg_log_next");
        if (start.backend() == FrameGen.BACKEND_DLSSG) {
            FrameGen.Start next;
            if (prepare(start)) {
                boolean yes = Ffi.call("P:I", dll.symbol("cg_fg_supports"), luid) != 0;
                next = FrameGen.answered(start, yes, yes ? null : memUTF8(Ffi.call(":P", error)));
                drainFrameGenLog();
                if (next != start) {
                    Ffi.call(":V", dll.symbol("cg_fg_unprepare"));
                    drainFrameGenLog();
                }
            } else {
                next = FrameGen.answered(null, false, memUTF8(Ffi.call(":P", error)));
            }
            if (next == null) {
                return false;
            }
            if (next == start) {
                return enable(start);
            }
            start = next;
        }
        if (!prepare(start)) {
            FrameGen.unavailable(memUTF8(Ffi.call(":P", error)));
            return false;
        }
        return enable(start);
    }

    /** cg_fg_prepare for {@code start}: true when it is ready. */
    private boolean prepare(FrameGen.Start start) {
        try (MemoryStack stack = stackPush()) {
            boolean ready = Ffi.call("IPPPI:I", dll.symbol("cg_fg_prepare"), start.backend(),
                    memAddress(stack.UTF16(start.pluginFolder().toString())), memAddress(stack.UTF16(start.dllFolder().toString())),
                    memAddress(stack.UTF16(start.logFolder().toString())), start.flags()) != 0;
            drainFrameGenLog();
            return ready;
        }
    }

    /** How the prepared backend starts, on or off (before the swap chain, always fine). */
    private boolean enable(FrameGen.Start start) {
        fgStart = start;
        fgEnable = dll.symbol("cg_fg_enable");
        Ffi.call("I:I", fgEnable, start.on() ? 1 : 0);
        return true;
    }

    /** Hands frame generation's native log lines to the game's log. */
    private void drainFrameGenLog() {
        if (fgLogNext == 0L) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            ByteBuffer line = stack.calloc(512);
            for (int i = 0; i < 256 && Ffi.call("PI:I", fgLogNext, memAddress(line), line.capacity()) != 0; i++) {
                FrameGen.nativeLog(memUTF8(memAddress(line)));
            }
        }
    }

    /** True while frame generation's backend runs, on or off. */
    boolean frameGeneration() {
        return frameGeneration;
    }

    /** True while Streamline runs (DLSS Frame Generation's backend, on or off), so Reflex goes through it. */
    boolean streamline() {
        return frameGeneration && fgBackend == FrameGen.BACKEND_DLSSG;
    }

    /**
     * The game window was minimized or brought back: frame generation goes off at once, before the next Present
     * (NVIDIA asks for it off around window changes), and stays off until the window is back.
     */
    void minimizedChanged(boolean minimized) {
        FrameGen.minimized(minimized);
        if (minimized && frameGeneration && fgOn) {
            Ffi.call(":V", fgOff);
            drainFrameGenLog();
        }
    }

    /** For the log: 1 if DXGI holds the swap chain in exclusive full screen, 0 if not, -1 if it can't say. */
    int exclusiveFullscreen() {
        return (int) Ffi.call(":I", fullscreenState);
    }

    /** An export of cg_present.dll. */
    long symbol(String name) {
        return dll.symbol(name);
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
        FrameGen.Frame fg = null;
        if (frameGeneration && fgOn) {
            if (width != fgWidth || height != fgHeight) {
                createFgRing(width, height);
            }
            fg = FrameGen.frame(width, height, resizePending);
        }
        int slot = ringIndex;
        ringIndex = (ringIndex + 1) % RING_SIZE;
        boolean fgInputs = fg != null && fgTextures[slot][0] != 0;
        try (MemoryStack stack = stackPush()) {
            IntBuffer noBuffers = stack.mallocInt(0);
            IntBuffer texture = stack.mallocInt(fgInputs ? 1 + FrameGen.INPUTS : 1);
            texture.put(0, textures[slot]);
            if (fgInputs) {
                texture.put(1, fgTextures[slot]);
            }
            IntBuffer layout = stack.mallocInt(texture.capacity());
            for (int i = 0; i < layout.capacity(); i++) {
                layout.put(i, GL_LAYOUT_GENERAL_EXT);
            }
            // Direct3D 12 drew from this texture a few frames ago; OpenGL may overwrite it once that is done, which the
            // CPU can usually see already (then a GPU wait would only cost a trip through the scheduler). Frame
            // generation's inputs are free a little later, once its backend is done with them.
            long lastDraw = slotDone[slot];
            if (fgInputs) {
                lastDraw = Math.max(lastDraw, Ffi.call("I:P", fgInputsDone, slot));
            }
            boolean wait = lastDraw != 0L && Ffi.call(":P", completed) < lastDraw;
            if (timing != null) {
                timing.beforeHandOff(openglValue + 1, wait);
            }
            if (wait) {
                glSemaphoreParameterui64EXT(directxDone, GL_D3D12_FENCE_VALUE_EXT, lastDraw);
                glWaitSemaphoreEXT(directxDone, noBuffers, texture, layout);
            }
            glCopyImageSubData(glTexture, GL_TEXTURE_2D, 0, 0, 0, 0, textures[slot], GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
            if (fg != null && fg.generate() && (!fgInputs || !FrameGen.capture(fg, fgTextures[slot], glTexture, width, height))) {
                fg.flags &= ~FrameGen.FRAME_GENERATE;
            }
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
        if (fg != null) {
            boolean sent = Ffi.call("IIIIIIP:I", fgFrame, slot, fg.frameIndex, fg.flags, fg.renderWidth, fg.renderHeight,
                    fg.framesToGenerate, fg.camera) != 0;
            FrameGen.frameSent(sent, sent ? null : errorText());
            presentGenerates = sent && fg.generate();
            frameSent = sent;
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
        try {
            int[] imported = importShared(handle, size, width, height, GL_RGBA8);
            memories[i] = imported[0];
            textures[i] = imported[1];
            slotDone[i] = 0L;
        } catch (IllegalStateException e) {
            closeRing();
            throw e;
        }
    }

    /** A shared DirectX 12 texture as an OpenGL one: {memory object, texture}. Throws if OpenGL can't take it. */
    private static int[] importShared(long handle, long size, int width, int height, int format) {
        int glError = GL_NO_ERROR;
        for (long tried : new long[] {size, 0L}) {
            drainGlErrors();
            int memory = glCreateMemoryObjectsEXT();
            glMemoryObjectParameteriEXT(memory, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
            glImportMemoryWin32HandleEXT(memory, tried, GL_HANDLE_TYPE_D3D12_RESOURCE_EXT, handle);
            int texture = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorageMem2DEXT(texture, 1, format, width, height, memory, 0L);
            glError = glGetError();
            if (glError == GL_NO_ERROR) {
                if (tried != size) {
                    LOG.info("Perfect Presentation: OpenGL took a DirectX 12 texture (format 0x{}) with size 0", Integer.toHexString(format));
                }
                return new int[] {memory, texture};
            }
            glDeleteTextures(texture);
            glDeleteMemoryObjectsEXT(memory);
        }
        throw new IllegalStateException(String.format("OpenGL could not import the DirectX 12 texture (format 0x%X, GL error 0x%04X)",
                format, glError));
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

    /** Frame generation's inputs for each slot, the back buffer's size. On failure frame generation stops for the game. */
    private void createFgRing(int width, int height) {
        closeFgRing();
        fgWidth = width;
        fgHeight = height;
        int count = RING_SIZE * FrameGen.INPUTS;
        try (MemoryStack stack = stackPush()) {
            long handles = stack.nmalloc(8, count * 8);
            long sizes = stack.nmalloc(8, count * 8);
            if (Ffi.call("IIPP:I", fgCreateInputs, width, height, handles, sizes) == 0) {
                throw new IllegalStateException(errorText());
            }
            for (int i = 0; i < RING_SIZE; i++) {
                for (int j = 0; j < FrameGen.INPUTS; j++) {
                    int k = i * FrameGen.INPUTS + j;
                    int[] imported = importShared(memGetAddress(handles + k * 8L), memGetLong(sizes + k * 8L), width, height,
                            FrameGen.INPUT_FORMATS[j]);
                    fgMemories[i][j] = imported[0];
                    fgTextures[i][j] = imported[1];
                }
            }
        } catch (IllegalStateException e) {
            closeFgRing();
            fgWidth = width; // not again every frame
            fgHeight = height;
            FrameGen.stop("its inputs could not be made: " + e.getMessage());
        }
    }

    private void closeFgRing() {
        boolean any = false;
        for (int i = 0; i < RING_SIZE; i++) {
            FrameGen.forget(fgTextures[i]);
            for (int j = 0; j < FrameGen.INPUTS; j++) {
                any |= fgTextures[i][j] != 0;
                if (fgTextures[i][j] != 0) {
                    glDeleteTextures(fgTextures[i][j]);
                    fgTextures[i][j] = 0;
                }
                if (fgMemories[i][j] != 0) {
                    glDeleteMemoryObjectsEXT(fgMemories[i][j]);
                    fgMemories[i][j] = 0;
                }
            }
        }
        if (any) {
            glFinish(); // OpenGL is done with the textures before Direct3D 12 frees them
        }
        if (fgReleaseInputs != 0L) {
            Ffi.call(":V", fgReleaseInputs); // frame generation lets go of them first
        }
        fgWidth = 0;
        fgHeight = 0;
    }

    @Override
    public void present() {
        if (frameGeneration && fgOn) {
            if (!frameSent) {
                // A present without a frame drawn for it: Minecraft presents once more while it changes the window
                // mode (Window.updateFullscreen), after the resize it brought. It goes out without generated frames,
                // so the resize can follow it at once (NVIDIA: frame generation off before a resize).
                Ffi.call(":V", fgOff);
                FrameGen.presentedAlone();
            }
            frameSent = false;
        }
        int hr = (int) Ffi.call("I:I", presentFrame, syncInterval);
        if (hr < 0) {
            throw new IllegalStateException(errorText());
        }
        if (frameGeneration) {
            boolean generated = presentGenerates;
            presentGenerates = false;
            FrameGen.hidden(hr == DXGI_STATUS_OCCLUDED);
            try (MemoryStack stack = stackPush()) {
                long state = stack.nmalloc(8, 7 * 8);
                Ffi.call("P:V", fgState, state);
                long[] numbers = new long[7];
                for (int i = 0; i < numbers.length; i++) {
                    numbers[i] = memGetLong(state + i * 8L);
                }
                FrameGen.presented(numbers, syncInterval);
            }
            drainFrameGenLog();
            if (resizePending && !generated) {
                // This frame went out without generated frames: now the swap chain may change.
                resizePending = false;
                resizeNow(pendingWidth, pendingHeight);
            }
            boolean on = FrameGen.on();
            if (on != fgOn && !generated && !fgSwitchFailed) {
                switchFrameGeneration(on); // likewise: off, this frame was its last; on, the next is the first
            }
        }
    }

    /**
     * Turns frame generation on or off between two frames: the swap chain is made again (NVIDIA: frame generation's
     * swap chain costs a copy and a second queue even while it doesn't generate), and its inputs go while it is off.
     */
    private void switchFrameGeneration(boolean on) {
        if (!on) {
            closeFgRing();
        }
        int result = (int) Ffi.call("I:I", fgEnable, on ? 1 : 0);
        drainFrameGenLog();
        if (result < 0) {
            throw new IllegalStateException(errorText());
        }
        if (result == 0) {
            fgSwitchFailed = true;
            FrameGen.stop("it could not be turned " + (on ? "on" : "off") + ": " + errorText());
            return;
        }
        fgOn = on;
        FrameGen.switched(on);
    }

    @Override
    public void resizeSwapChain(int width, int height) {
        // The resize waits while frames are generated, or the frame drawn already asked for them (Minecraft polls
        // window events between drawing a frame and presenting it).
        if (frameGeneration && (FrameGen.generating() || presentGenerates)) {
            resizePending = true;
            pendingWidth = width;
            pendingHeight = height;
            return;
        }
        resizeNow(width, height);
    }

    private void resizeNow(int width, int height) {
        if ((int) Ffi.call("II:I", resize, width, height) < 0) {
            throw new IllegalStateException(errorText());
        }
        if (frameGeneration) {
            FrameGen.resized();
        }
    }

    @Override
    public void close() {
        if (timing != null) {
            timing.close();
            timing = null;
        }
        closeFgRing();
        closeRing();
        glDeleteSemaphoresEXT(openglDone);
        glDeleteSemaphoresEXT(directxDone);
        Ffi.call(":V", destroy);
        openglContext.close();
    }
}

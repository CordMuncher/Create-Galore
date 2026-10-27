package dev.creategalore.bridge.dlss;

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
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_LUID_SIZE_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memUTF8;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

import dev.creategalore.bridge.present.Ffi;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Direct3D 12 device Ray Reconstruction runs on: NVIDIA's Ray Reconstruction crashes on its first frame on Direct3D
 * 11 with any inputs, and runs on Direct3D 12 (the PC's Ray Reconstruction 1b). cg_ngx.dll opens it on the GPU OpenGL
 * renders with and owns its queue; OpenGL imports its textures (EXT_memory_object_win32) and two of its fences as
 * semaphores (EXT_semaphore_win32), as the DX12 presenter does. {@link #unlock} signals "OpenGL done" after OpenGL's
 * copies, cg_ngx's queue waits for it on the GPU, runs NGX and signals "DirectX done" ({@link #queued}), and {@link #lock}
 * has OpenGL wait for that on the GPU before it reads the output or writes the inputs again.
 */
final class DlssDevice12 implements NgxDevice {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** How long {@link #waitIdle} waits for the GPU at most. */
    private static final int IDLE_TIMEOUT_MS = 2000;

    private final NgxLibrary ngx;
    private final long device;
    private final String adapterName;
    private final int openglDone;
    private final int directxDone;
    private long openglValue;
    /** The "DirectX done" value OpenGL waits for at the next {@link #lock}, 0 if nothing was queued since {@link #unlock}. */
    private long directxValue;

    private DlssDevice12(NgxLibrary ngx, long device, String adapterName, int openglDone, int directxDone) {
        this.ngx = ngx;
        this.device = device;
        this.adapterName = adapterName;
        this.openglDone = openglDone;
        this.directxDone = directxDone;
    }

    /** A new device on the GPU OpenGL renders with. */
    static DlssDevice12 open(NgxLibrary ngx) {
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.GL_EXT_memory_object_win32 || !caps.GL_EXT_semaphore_win32) {
            throw new IllegalStateException("the OpenGL driver lacks " + (!caps.GL_EXT_memory_object_win32
                    ? "GL_EXT_memory_object_win32" : "GL_EXT_semaphore_win32") + ", which Ray Reconstruction on DirectX 12 needs");
        }
        long device;
        try (MemoryStack stack = stackPush()) {
            ByteBuffer luid = stack.calloc(GL_LUID_SIZE_EXT);
            glGetUnsignedBytevEXT(GL_DEVICE_LUID_EXT, luid);
            boolean known = memGetLong(memAddress(luid)) != 0L;
            device = Ffi.call("P:P", ngx.open12, known ? memAddress(luid) : 0L);
        }
        if (device == 0L) {
            throw new IllegalStateException("DirectX 12 did not start for Ray Reconstruction: " + memUTF8(Ffi.call(":P", ngx.error12)));
        }
        String name = memUTF8(Ffi.call(":P", ngx.adapterName12));
        drainGlErrors();
        int openglDone = importFence(Ffi.call("I:P", ngx.fenceHandle12, 0));
        int directxDone = importFence(Ffi.call("I:P", ngx.fenceHandle12, 1));
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            glDeleteSemaphoresEXT(openglDone);
            glDeleteSemaphoresEXT(directxDone);
            Ffi.call(":V", ngx.close12);
            throw new IllegalStateException(String.format("OpenGL could not import Ray Reconstruction's DirectX 12 fences (GL error 0x%04X)", glError));
        }
        return new DlssDevice12(ngx, device, name, openglDone, directxDone);
    }

    private static int importFence(long handle) {
        int semaphore = glGenSemaphoresEXT();
        glImportSemaphoreWin32HandleEXT(semaphore, GL_HANDLE_TYPE_D3D12_FENCE_EXT, handle);
        return semaphore;
    }

    private static void drainGlErrors() {
        for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) { }
    }

    @Override
    public Shared texture(int width, int height, int glFormat, int dxgiFormat, boolean output) {
        return new SharedTexture(width, height, glFormat, dxgiFormat, output);
    }

    /** A Direct3D 12 texture OpenGL imported. Between runs it is COMMON on the DirectX side. */
    private final class SharedTexture implements Shared {
        private final long resource;
        private final long handle;
        private final int memory;
        private final int glTexture;

        SharedTexture(int width, int height, int glFormat, int dxgiFormat, boolean output) {
            try (MemoryStack stack = stackPush()) {
                long handleOut = stack.nmalloc(8, 8);
                long sizeOut = stack.nmalloc(8, 8);
                resource = Ffi.call("IIIIPP:P", ngx.createTexture12, width, height, dxgiFormat, output ? 1 : 0, handleOut, sizeOut);
                if (resource == 0L) {
                    throw new IllegalStateException("DirectX 12: " + memUTF8(Ffi.call(":P", ngx.error12)));
                }
                handle = memGetAddress(handleOut);
                int[] imported;
                try {
                    imported = importShared(handle, memGetLong(sizeOut), width, height, glFormat);
                } catch (IllegalStateException e) {
                    Ffi.call("PP:V", ngx.releaseTexture12, resource, handle);
                    throw e;
                }
                memory = imported[0];
                glTexture = imported[1];
            }
            // One level, read with texelFetch: without this the texture is mipmap-incomplete and reads as black.
            glTextureParameteri(glTexture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(glTexture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(glTexture, GL_TEXTURE_MAX_LEVEL, 0);
        }

        @Override
        public int glTexture() {
            return glTexture;
        }

        @Override
        public long resource() {
            return resource;
        }

        @Override
        public void close() {
            glDeleteTextures(glTexture);
            glDeleteMemoryObjectsEXT(memory);
            Ffi.call("PP:V", ngx.releaseTexture12, resource, handle);
        }
    }

    /**
     * A shared DirectX 12 texture as an OpenGL one: {memory object, texture}. The spec ignores the size for D3D12
     * resources and notes some drivers want 0; others check it against the allocation (as for the presenter's
     * textures): the allocation size first, then 0.
     */
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
                return new int[] {memory, texture};
            }
            glDeleteTextures(texture);
            glDeleteMemoryObjectsEXT(memory);
        }
        throw new IllegalStateException(String.format("OpenGL could not import a DirectX 12 texture (format 0x%X, GL error 0x%04X)",
                format, glError));
    }

    /** OpenGL waits on the GPU for DirectX's run queued since {@link #unlock}, if there was one. */
    @Override
    public void lock(List<Shared> textures) {
        if (directxValue == 0L) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            glSemaphoreParameterui64EXT(directxDone, GL_D3D12_FENCE_VALUE_EXT, directxValue);
            glWaitSemaphoreEXT(directxDone, stack.mallocInt(0), textures(stack, textures), layouts(stack, textures.size()));
        }
        directxValue = 0L;
    }

    /** OpenGL signals "OpenGL done" once its writes are finished; the next run's queue waits for {@link #openglValue}. */
    @Override
    public void unlock(List<Shared> textures) {
        openglValue++;
        directxValue = 0L;
        try (MemoryStack stack = stackPush()) {
            glSemaphoreParameterui64EXT(openglDone, GL_D3D12_FENCE_VALUE_EXT, openglValue);
            glSignalSemaphoreEXT(openglDone, stack.mallocInt(0), textures(stack, textures), layouts(stack, textures.size()));
        }
    }

    /** The "OpenGL done" value the next run waits for. */
    long openglValue() {
        return openglValue;
    }

    /** A run was queued: OpenGL waits for {@code value} on "DirectX done" at the next {@link #lock} (0: nothing queued). */
    void queued(long value) {
        if (value != 0L) {
            directxValue = value;
        }
    }

    private static IntBuffer textures(MemoryStack stack, List<Shared> textures) {
        IntBuffer names = stack.mallocInt(textures.size());
        for (int i = 0; i < textures.size(); i++) {
            names.put(i, textures.get(i).glTexture());
        }
        return names;
    }

    private static IntBuffer layouts(MemoryStack stack, int count) {
        IntBuffer layouts = stack.mallocInt(count);
        for (int i = 0; i < count; i++) {
            layouts.put(i, GL_LAYOUT_GENERAL_EXT);
        }
        return layouts;
    }

    /** OpenGL finishes first, then the queue (at most {@link #IDLE_TIMEOUT_MS}). */
    @Override
    public void waitIdle() {
        glFinish();
        if (Ffi.call("I:I", ngx.waitIdle12, IDLE_TIMEOUT_MS) == 0) {
            LOG.warn("DLAA: the GPU did not finish Ray Reconstruction's work within {} s", IDLE_TIMEOUT_MS / 1000);
        }
    }

    @Override
    public String adapterName() {
        return adapterName;
    }

    @Override
    public long device() {
        return device;
    }

    @Override
    public long context() {
        return 0L;
    }

    @Override
    public void close() {
        glFinish();
        glDeleteSemaphoresEXT(openglDone);
        glDeleteSemaphoresEXT(directxDone);
        Ffi.call(":V", ngx.close12);
        LOG.info("DLAA: closed its DirectX 12 device");
    }
}

package dev.creategalore.bridge.dlss;

import java.util.List;

/**
 * The DirectX device NGX runs on, and the textures OpenGL shares with it: {@link DlssDevice} (Direct3D 11, DLSS) or
 * {@link DlssDevice12} (Direct3D 12, Ray Reconstruction, whose Direct3D 11 path crashes on its first frame).
 */
interface NgxDevice extends AutoCloseable {
    /** A texture both APIs see. */
    interface Shared extends AutoCloseable {
        int glTexture();

        /** The DirectX resource NGX gets. */
        long resource();

        @Override
        void close();
    }

    /** {@code output}: DLSS writes it (unordered access); else OpenGL writes it and DLSS reads it. */
    Shared texture(int width, int height, int glFormat, int dxgiFormat, boolean output);

    /** OpenGL takes the textures (back) once DirectX's work on them is done. */
    void lock(List<Shared> textures);

    /** Hands the textures to DirectX, with OpenGL's writes to them finished first. */
    void unlock(List<Shared> textures);

    /** Waits (a while at most) until the GPU has finished what this device was given, before textures are freed. */
    void waitIdle();

    String adapterName();

    /** The device NGX starts on. */
    long device();

    /** The Direct3D 11 context NGX records into, 0 on Direct3D 12 (cg_ngx's own command list). */
    long context();

    @Override
    void close();
}

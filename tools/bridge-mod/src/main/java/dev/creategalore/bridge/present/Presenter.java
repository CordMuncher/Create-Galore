package dev.creategalore.bridge.present;

/**
 * Shows the game's frames through a DXGI swap chain on the game window, while the game renders with OpenGL on a hidden
 * context: {@link InteropContext} (Direct3D 11, Perfect Presentation's way) or {@link D3D12Presenter} (Direct3D 12,
 * the swap chain frame generation needs). Called on the render thread only.
 */
interface Presenter extends AutoCloseable {
    /** The DirectX device showing the game (ID3D11Device* or ID3D12Device*), for NVIDIA Reflex. */
    long device();

    /** "DirectX 11" or "DirectX 12", for the log and the settings screen. */
    String api();

    void makeCurrent();

    void setSyncInterval(int interval);

    /** Waits for the swap chain's frame latency object (frame latency 1) before a frame starts. */
    void waitForSwapChainSignal();

    /** Copies the game's colour texture to the swap chain's back buffer. */
    void blitToSwapChain(int glTexture);

    void present();

    void resizeSwapChain(int width, int height);

    @Override
    void close();
}

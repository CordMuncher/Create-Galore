// cg_framegen.h: what the DirectX 12 presenter (cg_present.cpp) asks of a frame generation backend.
//
// Frame generation sits between the presenter and DXGI: it may wrap the device and queue, owns the real swap chain
// behind the one the presenter presents through, and reads each frame's inputs when the frame is presented. The
// presenter knows nothing of the backend's API; a backend implements this class. NVIDIA DLSS Frame Generation through
// Streamline (cg_streamline.cpp) tags the inputs and recomposes the HUD itself; AMD FidelityFX frame generation
// (cg_ffx.cpp) swaps in its own swap chain (createSwapChain), records its prepare pass into the presenter's command
// list (record), and draws a separate HUD texture over every frame it presents (composesUi), which the presenter cuts
// out of the frame (uiApart).
//
// The inputs are the same for every backend and every upscaler: OpenGL writes them, after DLSS or FSR ran, into a ring
// of textures shared with Direct3D 12 (cg_fg_create_inputs), top row first like the back buffer:
//  - hudless: R8G8B8A8_UNORM, the back buffer's size: the frame before the HUD;
//  - UI alpha: R8_UNORM, the back buffer's size: how much the HUD covers each pixel (frame = UI + (1 - alpha) * hudless);
//  - depth: R32_FLOAT, the back buffer's size, valid in the top left render size: 1 at the near plane, towards 0 far
//    away (reversed and infinite);
//  - motion: R16G16_FLOAT, like depth: render pixels towards where the pixel was the frame before, +y down.
#pragma once

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_6.h>

// The floats of a frame's camera (cg_fg_frame), in this order. Matrices are row-major for row vectors (a point is
// multiplied on the left), as Streamline and DirectX write them, without jitter.
enum FgCameraFloat {
    FG_VIEW_TO_CLIP = 0,          // 16: view space to clip space
    FG_CLIP_TO_VIEW = 16,         // 16: its inverse
    FG_CLIP_TO_PREV_CLIP = 32,    // 16: this frame's clip space to the frame before's
    FG_PREV_CLIP_TO_CLIP = 48,    // 16: its inverse
    FG_JITTER = 64,               // 2: the frame's jitter in render pixels, +y down
    FG_MOTION_SCALE = 66,         // 2: what the motion vectors are multiplied by to be in [-1, 1] of the render size
    FG_CAMERA_POS = 68,           // 3: in the world
    FG_CAMERA_UP = 71,            // 3
    FG_CAMERA_RIGHT = 74,         // 3
    FG_CAMERA_FORWARD = 77,       // 3
    FG_NEAR = 80,
    FG_FAR = 81,
    FG_FOV_Y = 82,                // radians
    FG_ASPECT = 83,               // width / height
    FG_FRAME_TIME_MS = 84,        // since the frame before
    FG_METERS_PER_UNIT = 85,      // world units to meters (1 in Minecraft)
    FG_CAMERA_FLOATS = 86
};

// cg_fg_frame flags
enum FgFrameFlag {
    FG_FRAME_GENERATE = 1,        // generate frames around this one: all inputs and the camera are valid
    FG_FRAME_RESET = 2,           // nothing connects this frame with the one before (a cut, a teleport, FG just on)
    FG_FRAME_CAMERA = 4,          // the camera floats are valid (a world is drawn), even if not generating
    FG_FRAME_UI = 8,              // generate the HUD apart from the world (from hudless and UI alpha), not as one image
};

enum FgInput { FG_HUDLESS, FG_UI_ALPHA, FG_DEPTH, FG_MOTION, FG_INPUTS };

/** One frame for the backend: its slot's textures (null: no inputs this frame) and what cg_fg_frame was told. */
struct FgFrame {
    ID3D12Resource* inputs[FG_INPUTS];
    unsigned frameIndex;
    int flags;
    UINT renderWidth;
    UINT renderHeight;
    UINT backWidth;
    UINT backHeight;
    int framesToGenerate;         // 1: one generated frame per rendered one (2x)
    const float* camera;          // FG_CAMERA_FLOATS, or null without FG_FRAME_CAMERA
    // For a backend that composesUi: the HUD texture, R8G8B8A8_UNORM, the back buffer's size, premultiplied (UI from
    // frame = UI + (1 - alpha) * hudless, alpha in a), between frames in PIXEL_SHADER_RESOURCE | NON_PIXEL_SHADER_RESOURCE.
    // When the backend says uiApart, the presenter draws the HUD into it and the hudless frame into the back buffer.
    ID3D12Resource* ui;
};

/** cg_fg_state: what the backend last reported. */
struct FgState {
    long long generating;         // 1 while it generates (the options were on at the last Present)
    long long status;             // the backend's own status code, 0: fine
    long long framesPresented;    // frames shown for the last rendered one (2 with one generated)
    long long minSize;            // the smallest back buffer width or height it runs at
    long long maxGenerated;       // the most frames it can generate per rendered one
    long long vsyncSupported;     // 1 if it can run with vsync on
    long long errors;             // calls into the backend that failed so far
};

class FgBackend {
public:
    virtual ~FgBackend() {}
    virtual const char* name() const = 0;

    /** Whether it can run on this adapter; false with the reason in cg_present_error. */
    virtual bool supports(const LUID& adapter) = 0;
    /** The device was made (before the queue and the swap chain). */
    virtual bool deviceCreated(ID3D12Device* device) = 0;
    /** Makes the presenting queue; returns the native queue, which the presenter uses for everything. */
    virtual HRESULT createQueue(ID3D12Device* device, const D3D12_COMMAND_QUEUE_DESC& desc, ID3D12CommandQueue** queue) {
        return device->CreateCommandQueue(&desc, IID_PPV_ARGS(queue));
    }
    /**
     * Makes the swap chain the presenter presents through, for the queue createQueue made. {@code desc} has the frame
     * latency waitable flag when waitable() says so.
     */
    virtual HRESULT createSwapChain(IDXGIFactory4* factory, ID3D12CommandQueue* queue, HWND hwnd,
            const DXGI_SWAP_CHAIN_DESC1& desc, IDXGISwapChain1** swapChain) {
        return factory->CreateSwapChainForHwnd(queue, hwnd, &desc, nullptr, nullptr, swapChain);
    }
    /**
     * Whether the swap chain keeps its frame latency waitable object (owned by the backend, closed at shutdown), which
     * the presenter then waits on at frame latency 1 as without frame generation. Otherwise the backend paces (Reflex).
     */
    virtual bool waitable() const { return false; }
    /** Whether the backend draws FgFrame::ui over every frame it presents (cg_fg_create_inputs makes the texture). */
    virtual bool composesUi() const { return false; }
    /** A frame is about to be drawn and presented. False on failure (cg_present_error). */
    virtual bool frame(const FgFrame& frame) = 0;
    /** After frame(): this frame goes into the back buffer without its HUD, and the HUD into FgFrame::ui. */
    virtual bool uiApart() const { return false; }
    /** Records the backend's own work for the frame into the presenter's command list, before the frame is drawn. */
    virtual void record(ID3D12GraphicsCommandList* list) { (void) list; }
    /** Present returned: makes {@code queue} wait until the backend is done with the frame's inputs. */
    virtual void presented(ID3D12CommandQueue* queue) = 0;
    /** The swap chain is about to resize or change its fullscreen state. */
    virtual void beforeResize() = 0;
    /** Off at once, before the next frame (the window was minimized: NVIDIA asks for off around window changes). */
    virtual void off() {}
    virtual void state(FgState& state) = 0;
    /** The presenter is about to release the swap chain and the inputs: let go of them. */
    virtual void stop() = 0;
    /** After the swap chain is released, before the device is: shuts the backend down. */
    virtual void shutdown() = 0;
};

// createStreamlineBackend flags
enum FgStreamlineFlag {
    FG_SL_DEVELOPMENT = 1,        // testing: NVIDIA's development DLLs (no signature check, they draw debug text)
    FG_SL_VERBOSE = 2,            // verbose Streamline log
};

// createFfxBackend flags
enum FgFfxFlag {
    FG_FFX_DEVELOPMENT = 1,       // testing: no signature check of AMD's DLLs
    FG_FFX_VERBOSE = 2,           // AMD's debug checks and every message of theirs in the log
};

/**
 * The Streamline backend (cg_streamline.cpp): sl.interposer.dll and its plugins from {@code pluginFolder}, NGX's
 * nvngx_dlssg.dll from {@code dllFolder} (or the driver's), Streamline's log files in {@code logFolder}. Null with the
 * reason in cg_present_error if it can't start; at most once per process.
 */
FgBackend* createStreamlineBackend(const wchar_t* pluginFolder, const wchar_t* dllFolder, const wchar_t* logFolder,
        int flags);

/**
 * The AMD FidelityFX backend (cg_ffx.cpp): amd_fidelityfx_loader_dx12.dll and amd_fidelityfx_framegeneration_dx12.dll
 * from {@code folder}. Null with the reason in cg_present_error if it can't start.
 */
FgBackend* createFfxBackend(const wchar_t* folder, int flags);

// cg_present.cpp, for backends:
/** Sets cg_present_error (wsprintf format) and returns false. */
bool fgFail(const char* format, ...);
/** Queues a line for cg_fg_log_next, level 'E', 'W' or 'I'. Any thread. */
void fgLog(char level, const char* message);
/** Blocks until the presenting queue has finished everything submitted so far. */
void fgWaitIdle();

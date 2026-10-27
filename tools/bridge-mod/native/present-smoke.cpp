// Smoke test for the DirectX 12 presenter (cg_present.cpp) without OpenGL, for Wine on Linux (present-smoke.sh) or
// Windows. It builds the presenter into itself unshared (CG_PRESENT_UNSHARED: Wine's D3D12 has no shared handles) and
// plays OpenGL's part on a second D3D12 queue: writes a picture into a ring texture bottom row first, as OpenGL stores
// a frame, signals the "OpenGL done" fence, and has the presenter draw and present. Then it checks a captured back
// buffer is that picture the right way up with the colours in place, before and after a resize, over 300 frames each,
// and again with 3 buffers and frame latency 2. The GPU timestamps (cg_present_timing) must come back in order.
//
// Frame generation's plumbing (ABI 5) with a stand-in backend: it must get the queue and swap chain calls, a frame call
// with the four inputs before each draw, a presented call after each Present, and the presenter must signal a later
// "D3D12 done" value for the slot's inputs; no waitable object, frame latency refused, stop and shutdown on destroy.
// A stand-in that composes the HUD itself, as AMD's does, must get the waitable object and a HUD texture, and on its
// frames the back buffer must be the hudless input and the HUD texture frame - hudless * (1 - alpha) with alpha.
// Turned off while the presenter runs (cg_fg_enable), the swap chain must be a plain one with the presenter's waitable
// object and the backend asked nothing, and on again it must generate as before; also when it starts off. A
// backend that can't start must leave a presenter without it (cg_fg_error says why), and Streamline and FidelityFX
// without their DLLs must refuse to prepare. "present-smoke.exe --streamline <folder>" instead tries NVIDIA's real
// Streamline DLLs from that folder (under Wine there is no NVIDIA card: the presenter must go on without them), and
// "present-smoke.exe --ffx <folder> [--unsigned]" AMD's FidelityFX frame generation DLLs, and "present-smoke.exe --probe
// <streamline folder> <fidelityfx folder> [--unsigned]" asks Streamline before anything is made, drops it and runs
// FidelityFX in its place, as the mod does on a card Streamline says no to. The stand-in must also be asked before
// anything is made (cg_fg_supports) and be dropped for another (cg_fg_unprepare).
#define CG_PRESENT_UNSHARED
#include "cg_present.cpp"

#include <stdio.h>

static ID3D12CommandQueue* g_testQueue;
static ID3D12CommandAllocator* g_testAllocator;
static ID3D12GraphicsCommandList* g_testList;
static ID3D12Fence* g_testFence;
static UINT64 g_testValue;
static HANDLE g_testEvent;
static UINT64 g_testWaitDone;  // the "D3D12 done" value the next write waits for (as OpenGL does before a slot)

/** The picture, as RGBA, at (x, y) from its top-left: red over blue, the left half darker green than the right. */
static unsigned picture(int x, int y, int width, int height) {
    unsigned red = y < height / 2 ? 255 : 0;
    unsigned green = x < width / 2 ? 64 : 192;
    unsigned blue = y < height / 2 ? 0 : 255;
    return red | green << 8 | blue << 16 | 255u << 24;
}

typedef unsigned (*Pixel)(int x, int y, int width, int height);

/**
 * Writes {@code pixel}'s picture into {@code texture} ({@code bytes} per pixel, its low bytes), its rows from the top or,
 * with {@code openGlRows}, the way OpenGL holds a frame: memory row 0 is the picture's bottom row.
 */
static bool writeTexture(ID3D12Resource* texture, int width, int height, int bytes, Pixel pixel, bool openGlRows) {
    D3D12_RESOURCE_DESC desc = texture->GetDesc();
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
    UINT64 total = 0;
    g_device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
    D3D12_HEAP_PROPERTIES heap = {};
    heap.Type = D3D12_HEAP_TYPE_UPLOAD;
    D3D12_RESOURCE_DESC buffer = {};
    buffer.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
    buffer.Width = total;
    buffer.Height = 1;
    buffer.DepthOrArraySize = 1;
    buffer.MipLevels = 1;
    buffer.SampleDesc.Count = 1;
    buffer.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
    ID3D12Resource* staging = nullptr;
    if (FAILED(g_device->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_NONE, &buffer, D3D12_RESOURCE_STATE_GENERIC_READ,
            nullptr, IID_PPV_ARGS(&staging)))) {
        return false;
    }
    unsigned char* mapped = nullptr;
    staging->Map(0, nullptr, (void**) &mapped);
    for (int row = 0; row < height; row++) {
        unsigned char* line = mapped + footprint.Offset + (size_t) row * footprint.Footprint.RowPitch;
        for (int x = 0; x < width; x++) {
            unsigned value = pixel(x, openGlRows ? height - 1 - row : row, width, height);
            CopyMemory(line + (size_t) x * bytes, &value, (SIZE_T) bytes);
        }
    }
    staging->Unmap(0, nullptr);

    g_testAllocator->Reset();
    g_testList->Reset(g_testAllocator, nullptr);
    D3D12_TEXTURE_COPY_LOCATION dst = {};
    dst.pResource = texture;
    dst.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
    D3D12_TEXTURE_COPY_LOCATION src = {};
    src.pResource = staging;
    src.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
    src.PlacedFootprint = footprint;
    g_testList->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
    // Back to COMMON, where the presenter expects a texture OpenGL wrote (a write state does not decay).
    D3D12_RESOURCE_BARRIER barrier = {};
    barrier.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier.Transition.pResource = texture;
    barrier.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_DEST;
    barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
    g_testList->ResourceBarrier(1, &barrier);
    g_testList->Close();
    if (g_testWaitDone) {
        g_testQueue->Wait(g_doneFence, g_testWaitDone);
    }
    ID3D12CommandList* lists[] = {g_testList};
    g_testQueue->ExecuteCommandLists(1, lists);
    g_testQueue->Signal(g_testFence, ++g_testValue);
    g_testFence->SetEventOnCompletion(g_testValue, g_testEvent);
    WaitForSingleObject(g_testEvent, INFINITE);
    staging->Release();
    return true;
}

static bool writeAsOpenGl(ID3D12Resource* texture, int width, int height) {
    return writeTexture(texture, width, height, 4, picture, true);
}

/** The hudless input's picture, top row first: a gradient unlike the frame's. */
static unsigned hudlessPicture(int x, int y, int width, int height) {
    (void) width;
    (void) height;
    return (unsigned) (x * 7 & 255) | (unsigned) (y * 5 & 255) << 8 | 128u << 16 | 255u << 24;
}

/** The HUD's coverage, top row first. */
static unsigned alphaPicture(int x, int y, int width, int height) {
    (void) width;
    (void) height;
    return (unsigned) ((x + y) & 255);
}

/** Copies {@code texture} (RGBA8, in {@code state}) into {@code out} as tight rows, top row first. */
static bool readTexture(ID3D12Resource* texture, D3D12_RESOURCE_STATES state, unsigned char* out) {
    D3D12_RESOURCE_DESC desc = texture->GetDesc();
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
    UINT64 total = 0;
    g_device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
    D3D12_HEAP_PROPERTIES heap = {};
    heap.Type = D3D12_HEAP_TYPE_READBACK;
    D3D12_RESOURCE_DESC buffer = {};
    buffer.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
    buffer.Width = total;
    buffer.Height = 1;
    buffer.DepthOrArraySize = 1;
    buffer.MipLevels = 1;
    buffer.SampleDesc.Count = 1;
    buffer.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
    ID3D12Resource* readback = nullptr;
    if (FAILED(g_device->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_NONE, &buffer, D3D12_RESOURCE_STATE_COPY_DEST,
            nullptr, IID_PPV_ARGS(&readback)))) {
        return false;
    }
    g_testAllocator->Reset();
    g_testList->Reset(g_testAllocator, nullptr);
    D3D12_RESOURCE_BARRIER barrier = {};
    barrier.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier.Transition.pResource = texture;
    barrier.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    barrier.Transition.StateBefore = state;
    barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
    g_testList->ResourceBarrier(1, &barrier);
    D3D12_TEXTURE_COPY_LOCATION dst = {};
    dst.pResource = readback;
    dst.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
    dst.PlacedFootprint = footprint;
    D3D12_TEXTURE_COPY_LOCATION src = {};
    src.pResource = texture;
    src.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
    g_testList->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
    barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
    barrier.Transition.StateAfter = state;
    g_testList->ResourceBarrier(1, &barrier);
    g_testList->Close();
    ID3D12CommandList* lists[] = {g_testList};
    g_testQueue->ExecuteCommandLists(1, lists);
    g_testQueue->Signal(g_testFence, ++g_testValue);
    g_testFence->SetEventOnCompletion(g_testValue, g_testEvent);
    WaitForSingleObject(g_testEvent, INFINITE);
    unsigned char* mapped = nullptr;
    readback->Map(0, nullptr, (void**) &mapped);
    for (UINT y = 0; y < desc.Height; y++) {
        CopyMemory(out + (size_t) y * desc.Width * 4, mapped + footprint.Offset + (size_t) y * footprint.Footprint.RowPitch,
                (SIZE_T) desc.Width * 4);
    }
    readback->Unmap(0, nullptr);
    readback->Release();
    return true;
}

/** What the stand-in backend saw (it is deleted on cg_present_destroy). */
static struct {
    int supports, devices, frames, framesWithInputs, badFrames, presented, resizes, stops, shutdowns, splits, switches;
    unsigned lastIndex;
} g_fake;

/**
 * A frame generation backend that checks what the presenter hands it and makes the queue wait on its own fence; with
 * {@code composes}, one that keeps the waitable object (its own while on) and the HUD apart on the frames it
 * generates, as AMD's does. Off, its swap chain is a plain one with the presenter's waitable object, as Streamline's.
 */
class FakeBackend : public FgBackend {
public:
    explicit FakeBackend(bool works, bool composes = false) : m_works(works), m_composes(composes) {}
    const char* name() const override { return "stand-in"; }
    bool supports(const LUID& adapter) override {
        (void) adapter;
        g_fake.supports++;
        return m_works || fgFail("the stand-in can't run on %s", "this card");
    }
    bool deviceCreated(ID3D12Device* device) override {
        g_fake.devices++;
        return SUCCEEDED(device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&m_fence)));
    }
    bool setOn(bool on) override {
        g_fake.switches += on != m_on ? 1 : 0;
        m_on = on;
        return true;
    }
    bool frame(const FgFrame& frame) override {
        g_fake.frames++;
        if (!m_on) {
            g_fake.badFrames++;  // the presenter asks nothing while it's off
        }
        bool inputs = true;
        for (int j = 0; j < FG_INPUTS; j++) {
            if (!frame.inputs[j]) {
                inputs = false;
                continue;
            }
            D3D12_RESOURCE_DESC desc = frame.inputs[j]->GetDesc();
            if (desc.Format != FG_FORMATS[j] || desc.Width != frame.backWidth || desc.Height != frame.backHeight) {
                g_fake.badFrames++;
            }
        }
        g_fake.framesWithInputs += inputs ? 1 : 0;
        if (m_composes) {
            D3D12_RESOURCE_DESC ui = frame.ui ? frame.ui->GetDesc() : D3D12_RESOURCE_DESC{};
            if (!frame.ui || ui.Format != FG_UI_FORMAT || ui.Width != frame.backWidth || ui.Height != frame.backHeight) {
                g_fake.badFrames++;
            }
            m_split = (frame.flags & FG_FRAME_GENERATE) && (frame.flags & FG_FRAME_UI) && frame.ui;
            g_fake.splits += m_split ? 1 : 0;
        } else if (frame.ui) {
            g_fake.badFrames++;
        }
        if (frame.backWidth != g_backWidth || frame.backHeight != g_backHeight || frame.renderWidth > frame.backWidth
                || !frame.camera || frame.camera[FG_NEAR] != 0.05f
                || (g_fake.frames > 1 && frame.frameIndex != g_fake.lastIndex + 1)) {
            g_fake.badFrames++;
        }
        g_fake.lastIndex = frame.frameIndex;
        return true;
    }
    bool waitable() const override { return m_composes || !m_on; }
    bool ownsWaitable() const override { return m_composes && m_on; }
    bool composesUi() const override { return m_composes; }
    bool uiApart() const override { return m_split; }
    void presented(ID3D12CommandQueue* queue) override {
        if (ownsWaitable()) {
            m_waitable = g_waitable;  // closed with the swap chain, as AMD's backend does
        }
        m_split = false;
        g_fake.presented++;
        queue->Signal(m_fence, ++m_value);
        queue->Wait(m_fence, m_value);
    }
    void beforeResize() override { g_fake.resizes++; }
    void state(FgState& state) override {
        state.generating = 1;
        state.maxGenerated = 1;
    }
    void stop() override { g_fake.stops++; }
    void swapChainReleased() override {
        if (m_waitable) {
            CloseHandle(m_waitable);
            m_waitable = nullptr;
        }
    }
    void shutdown() override {
        g_fake.shutdowns++;
        if (m_fence) {
            m_fence->Release();
            m_fence = nullptr;
        }
    }
private:
    bool m_works;
    bool m_composes;
    bool m_split = false;
    bool m_on = true;
    HANDLE m_waitable = nullptr;
    ID3D12Fence* m_fence = nullptr;
    UINT64 m_value = 0;
};

static void makeTestQueue() {
    D3D12_COMMAND_QUEUE_DESC queueDesc = {};
    queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    g_device->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&g_testQueue));
    g_device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&g_testAllocator));
    g_device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, g_testAllocator, nullptr, IID_PPV_ARGS(&g_testList));
    g_testList->Close();
    g_device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&g_testFence));
    g_testEvent = CreateEventA(nullptr, FALSE, FALSE, nullptr);
}

static void releaseTestQueue() {
    g_testList->Release();
    g_testAllocator->Release();
    g_testQueue->Release();
    g_testFence->Release();
    CloseHandle(g_testEvent);
}

static unsigned g_frameIndex;

/**
 * 300 frames of the picture; with {@code fg}, each with a frame generation call and its inputs. Differing pixels. With
 * {@code split} (a backend that keeps the HUD apart), the last frames' hudless and UI alpha inputs are written too,
 * and the back buffer must be the hudless picture and the HUD texture frame - hudless * (1 - alpha), with alpha.
 */
static int runFrames(int width, int height, unsigned long long& glValue, bool fg = false, bool split = false) {
    HANDLE handles[RING];
    UINT64 sizes[RING];
    if (!cg_present_create_textures(width, height, handles, sizes)) {
        printf("textures failed: %s\n", cg_present_error());
        return -1;
    }
    printf("textures %dx%d, %llu bytes each\n", width, height, (unsigned long long) sizes[0]);
    HANDLE fgHandles[RING * FG_INPUTS];
    UINT64 fgSizes[RING * FG_INPUTS];
    if (fg && !cg_fg_create_inputs((int) g_backWidth, (int) g_backHeight, fgHandles, fgSizes)) {
        printf("frame generation inputs failed: %s\n", cg_present_error());
        return -1;
    }
    float camera[FG_CAMERA_FLOATS] = {};
    for (int m = 0; m < 4; m++) {
        for (int i = 0; i < 4; i++) {
            camera[m * 16 + i * 5] = 1.0f;
        }
    }
    camera[FG_MOTION_SCALE] = 2.0f / (float) g_backWidth;
    camera[FG_MOTION_SCALE + 1] = 2.0f / (float) g_backHeight;
    camera[FG_CAMERA_UP + 1] = 1.0f;
    camera[FG_CAMERA_RIGHT] = 1.0f;
    camera[FG_CAMERA_FORWARD + 2] = -1.0f;
    camera[FG_NEAR] = 0.05f;
    camera[FG_FAR] = 1000.0f;
    camera[FG_FOV_Y] = 1.2217305f;
    camera[FG_ASPECT] = (float) g_backWidth / (float) g_backHeight;
    camera[FG_FRAME_TIME_MS] = 16.0f;
    camera[FG_METERS_PER_UNIT] = 1.0f;
    long long drawnSlot[RING] = {};
    int lateInputs = 0;
    LARGE_INTEGER frequency, start, end;
    QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&start);
    const int frames = 300;
    for (int frame = 0; frame < frames; frame++) {
        MSG msg;
        while (PeekMessageA(&msg, nullptr, 0, 0, PM_REMOVE)) {
            DispatchMessageA(&msg);
        }
        int waited = cg_present_wait(1000);
        if (waited != 0) {
            printf("frame %d: swap chain wait returned %d\n", frame, waited);
            return -1;
        }
        int slot = frame % RING;
        unsigned long long inputsDone = fg ? cg_fg_inputs_done(slot) : 0;
        g_testWaitDone = (UINT64) drawnSlot[slot] > inputsDone ? (UINT64) drawnSlot[slot] : inputsDone;
        if ((frame < RING || frame >= frames - RING) && !writeAsOpenGl(g_ring[slot], width, height)) {
            printf("writing the picture failed\n");
            return -1;
        }
        if (split && frame >= frames - RING
                && (!writeTexture(g_fgInputs[slot][FG_HUDLESS], (int) g_backWidth, (int) g_backHeight, 4, hudlessPicture, false)
                || !writeTexture(g_fgInputs[slot][FG_UI_ALPHA], (int) g_backWidth, (int) g_backHeight, 1, alphaPicture, false))) {
            printf("writing the hudless and UI alpha inputs failed\n");
            return -1;
        }
        g_testWaitDone = 0;
        g_testQueue->Signal(g_glFence, ++glValue);
        if (frame == frames - 1) {
            cg_present_capture_request();
        }
        if (fg && !cg_fg_frame(slot, ++g_frameIndex, FG_FRAME_GENERATE | FG_FRAME_CAMERA | FG_FRAME_UI, (int) g_backWidth / 2,
                (int) g_backHeight / 2, 1, camera)) {
            printf("frame %d: frame generation failed: %s\n", frame, cg_present_error());
            return -1;
        }
        long long drawn = cg_present_draw(slot, glValue);
        drawnSlot[slot] = drawn;
        if (drawn < 0 || cg_present_present(0) < 0) {
            printf("frame %d failed: %s\n", frame, cg_present_error());
            return -1;
        }
        if (fg && cg_fg_inputs_done(slot) <= (unsigned long long) drawn) {
            lateInputs++;
        }
    }
    QueryPerformanceCounter(&end);
    printf("%d frames in %.0f ms\n", frames, (end.QuadPart - start.QuadPart) * 1000.0 / frequency.QuadPart);
    if (lateInputs > 0) {
        printf("%d frames' inputs were released no later than their draw\n", lateInputs);
        return -1;
    }

    static unsigned char pixels[4096 * 4096 * 4];
    int size[2];
    if (!cg_present_capture_read(pixels, sizeof pixels, size)) {
        printf("capture failed: %s\n", cg_present_error());
        return -1;
    }
    int differ = 0;
    for (int y = 0; y < size[1]; y++) {
        for (int x = 0; x < size[0]; x++) {
            unsigned want = split ? hudlessPicture(x, y, size[0], size[1])
                    : picture(x * width / size[0], y * height / size[1], width, height);
            const unsigned char* p = pixels + ((size_t) y * size[0] + x) * 4; // BGRA
            unsigned got = p[2] | p[1] << 8 | p[0] << 16 | (unsigned) p[3] << 24;
            if (got != want && differ++ < 5) {
                printf("  (%d, %d): %08X, expected %08X\n", x, y, got, want);
            }
        }
    }
    printf("back buffer %dx%d: %d pixels differ from the %s\n", size[0], size[1], differ, split ? "hudless picture" : "picture");
    if (split) {
        waitIdle();
        if (!g_fgUi || !readTexture(g_fgUi, FG_UI_STATE, pixels)) {
            printf("reading the HUD texture failed\n");
            return -1;
        }
        int uiDiffer = 0;
        for (int y = 0; y < size[1]; y++) {
            for (int x = 0; x < size[0]; x++) {
                const unsigned char* p = pixels + ((size_t) y * size[0] + x) * 4;  // RGBA
                unsigned frame = picture(x, y, width, height);
                unsigned hudless = hudlessPicture(x, y, size[0], size[1]);
                float alpha = (float) alphaPicture(x, y, size[0], size[1]) / 255.0f;
                bool bad = p[3] != alphaPicture(x, y, size[0], size[1]);
                for (int c = 0; c < 3; c++) {
                    float want = (float) (frame >> (c * 8) & 255) - (float) (hudless >> (c * 8) & 255) * (1.0f - alpha);
                    want = want < 0.0f ? 0.0f : want;
                    float got = (float) p[c];
                    bad |= got - want > 1.5f || want - got > 1.5f;
                }
                if (bad && uiDiffer++ < 5) {
                    printf("  HUD (%d, %d): %02X%02X%02X%02X\n", x, y, p[0], p[1], p[2], p[3]);
                }
            }
        }
        printf("HUD texture: %d pixels differ from frame - hudless * (1 - alpha)\n", uiDiffer);
        differ += uiDiffer;
    }
    return differ;
}

/**
 * A real backend from {@code folder} (Streamline, or FidelityFX with {@code ffx}): prepare, create (going on without it
 * if it can't run), frames (and if it runs, a resize and frames again, then off, frames, on and frames), destroy;
 * FidelityFX's frames keep the HUD apart, as the mod's do.
 */
static int realBackend(HWND hwnd, int width, int height, const char* folder, bool ffx, bool unsignedDlls) {
    wchar_t path[MAX_PATH];
    MultiByteToWideChar(CP_UTF8, 0, folder, -1, path, MAX_PATH);
    const char* label = ffx ? "FFX" : "STREAMLINE";
    int prepared = ffx ? cg_fg_prepare(FG_BACKEND_FSR, path, path, path, FG_FFX_VERBOSE | (unsignedDlls ? FG_FFX_DEVELOPMENT : 0))
            : cg_fg_prepare(FG_BACKEND_DLSSG, path, path, path, FG_SL_VERBOSE);
    printf("%s prepare: %d %s\n", label, prepared, prepared ? "" : cg_present_error());
    int result = 0;
    if (!cg_present_create(hwnd, nullptr, 0)) {
        printf("create failed: %s\n", cg_present_error());
        result = 1;
    } else {
        bool active = cg_fg_active() != 0;
        printf("frame generation active %d, error \"%s\"\n", active, cg_fg_error());
        makeTestQueue();
        unsigned long long glValue = 0;
        result = runFrames(width, height, glValue, active, active && ffx) == 0 ? 0 : 1;
        if (result == 0 && active) {  // and after a resize, which makes the backend start over at the new size
            result = cg_present_resize(800, 600) >= 0 && runFrames(800, 600, glValue, true, ffx) == 0 ? 0 : 1;
        }
        if (result == 0 && active) {  // turned off and on again
            int off = cg_fg_enable(0);
            printf("off: %d %s, waitable %s\n", off, off == 1 ? "" : cg_present_error(), g_waitable ? "yes" : "no");
            result = off == 1 && runFrames(800, 600, glValue) == 0 ? 0 : 1;
            int on = result == 0 ? cg_fg_enable(1) : 0;
            printf("on: %d %s, waitable %s\n", on, on == 1 ? "" : cg_present_error(), g_waitable ? "yes" : "no");
            result = on == 1 && runFrames(800, 600, glValue, true, ffx) == 0 ? 0 : 1;
        }
        long long state[7];
        cg_fg_state(state);
        printf("state: generating %lld, status 0x%llx, %lld frames shown per rendered one, %lld failed calls\n", state[0],
                state[1], state[2], state[6]);
        releaseTestQueue();
        cg_present_destroy();
    }
    char line[LOG_LINE];
    int lines = 0;
    while (cg_fg_log_next(line, sizeof line) && lines++ < 60) {
        printf("  log %s\n", line);
    }
    printf("%s %s\n", label, result == 0 ? "PASS" : "FAIL");
    return result;
}

int main(int argc, char** argv) {
    setvbuf(stdout, nullptr, _IONBF, 0);  // every line out before a crash
    WNDCLASSA windowClass = {};
    windowClass.lpfnWndProc = DefWindowProcA;
    windowClass.hInstance = GetModuleHandleA(nullptr);
    windowClass.lpszClassName = "cg_present_smoke";
    RegisterClassA(&windowClass);
    HWND hwnd = CreateWindowA("cg_present_smoke", "cg_present smoke test", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, 656, 519,
            nullptr, nullptr, windowClass.hInstance, nullptr);
    RECT client;
    GetClientRect(hwnd, &client);
    int width = client.right - client.left;
    int height = client.bottom - client.top;
    if (argc == 3 && lstrcmpA(argv[1], "--streamline") == 0) {
        return realBackend(hwnd, width, height, argv[2], false, false);
    }
    if ((argc == 3 || (argc == 4 && lstrcmpA(argv[3], "--unsigned") == 0)) && lstrcmpA(argv[1], "--ffx") == 0) {
        return realBackend(hwnd, width, height, argv[2], true, argc == 4);
    }
    if ((argc == 4 || (argc == 5 && lstrcmpA(argv[4], "--unsigned") == 0)) && lstrcmpA(argv[1], "--probe") == 0) {
        // As the mod starts: Streamline asked first, then dropped for FidelityFX when it says no.
        wchar_t path[MAX_PATH];
        MultiByteToWideChar(CP_UTF8, 0, argv[2], -1, path, MAX_PATH);
        int prepared = cg_fg_prepare(FG_BACKEND_DLSSG, path, path, path, FG_SL_VERBOSE);
        printf("STREAMLINE prepare: %d %s\n", prepared, prepared ? "" : cg_present_error());
        int says = prepared ? cg_fg_supports(nullptr) : 0;
        printf("STREAMLINE says %s %s\n", says ? "yes" : "no", says ? "" : cg_present_error());
        cg_fg_unprepare();
        if (g_fg) {
            printf("PROBE FAIL: Streamline still prepared\n");
            return 1;
        }
        return realBackend(hwnd, width, height, argv[3], true, argc == 5);
    }

    if (!cg_present_create(hwnd, nullptr, 0)) {
        printf("create failed: %s\n", cg_present_error());
        return 1;
    }
    char name[256];
    cg_present_adapter_name(name, sizeof name);
    int fullscreenState = cg_present_fullscreen_state();
    printf("adapter %s, tearing %d, window %dx%d, DXGI full screen state %d\n", name, cg_present_tearing(), width, height,
            fullscreenState);

    makeTestQueue();

    if (!cg_present_timing(1)) {
        printf("timing failed: %s\n", cg_present_error());
        return 1;
    }
    unsigned long long glValue = 0;
    int first = runFrames(width, height, glValue);
    int resized = cg_present_resize(800, 600);
    if (resized < 0) {
        printf("resize failed: %s\n", cg_present_error());
        return 1;
    }
    int second = first < 0 ? -1 : runFrames(800, 600, glValue);
    if (cg_present_set_buffers(3) < 0 || cg_present_set_frame_latency(2) < 0) {
        printf("3 buffers / frame latency 2 failed: %s\n", cg_present_error());
        return 1;
    }
    int third = second < 0 ? -1 : runFrames(800, 600, glValue);

    // Timestamps: the draws' OpenGL values rising, each start before its end and no later than now.
    static long long records[64 * 3];
    int timed = cg_present_timings(records, 64);
    long long now = cg_present_now_ns();
    int badTimes = 0;
    long long longest = 0;
    for (int i = 0; i < timed; i++) {
        long long value = records[i * 3], start = records[i * 3 + 1], end = records[i * 3 + 2];
        if ((i > 0 && value <= records[(i - 1) * 3]) || start > end || end > now + 1000000) {
            badTimes++;
        }
        longest = end - start > longest ? end - start : longest;
    }
    printf("timestamps: %d draws kept, %d out of order, longest draw %lld us, completed value %llu\n", timed, badTimes,
            longest / 1000, cg_present_completed());

    releaseTestQueue();
    cg_present_destroy();
    bool pass = first == 0 && second == 0 && third == 0 && timed > 0 && badTimes == 0 && fullscreenState == 0;

    // Frame generation's plumbing with the stand-in backend.
    g_fg = new FakeBackend(true);
    if (!cg_present_create(hwnd, nullptr, 0) || !cg_fg_active()) {
        printf("create with frame generation failed: %s / %s\n", cg_present_error(), cg_fg_error());
        return 1;
    }
    makeTestQueue();
    bool noWaitable = g_waitable == nullptr && cg_present_wait(1000) == 0;
    bool latencyRefused = cg_present_set_frame_latency(2) < 0 && cg_present_set_buffers(3) < 0;
    glValue = 0;
    int fgFirst = runFrames(width, height, glValue, true);
    int fgResized = cg_present_resize(800, 600);
    int fgSecond = fgFirst < 0 || fgResized < 0 ? -1 : runFrames(800, 600, glValue, true);
    long long state[7];
    cg_fg_state(state);
    releaseTestQueue();
    cg_present_destroy();
    bool fgPass = fgFirst == 0 && fgSecond == 0 && noWaitable && latencyRefused && g_fake.supports == 1
            && g_fake.devices == 1 && g_fake.frames == 600 && g_fake.framesWithInputs == 600 && g_fake.badFrames == 0
            && g_fake.presented == 600 && g_fake.resizes == 1 && g_fake.stops >= 1 && g_fake.shutdowns == 1 && !g_fg
            && state[0] == 1;
    printf("frame generation plumbing: %d frames (%d with inputs, %d bad), %d presented, %d resizes, %d stops, %d shutdowns, "
            "waitable gone %d, latency refused %d: %s\n", g_fake.frames, g_fake.framesWithInputs, g_fake.badFrames,
            g_fake.presented, g_fake.resizes, g_fake.stops, g_fake.shutdowns, noWaitable, latencyRefused,
            fgPass ? "ok" : "FAILED");

    // A backend that can't start: the presenter goes on without it and says why.
    g_fake = {};
    g_fg = new FakeBackend(false);
    bool fallback = cg_present_create(hwnd, nullptr, 0) && !cg_fg_active() && g_fake.shutdowns == 1 && !g_fg
            && g_waitable != nullptr && lstrlenA(cg_fg_error()) > 0;
    printf("frame generation fallback: \"%s\": %s\n", cg_fg_error(), fallback ? "ok" : "FAILED");
    if (fallback) {
        makeTestQueue();
        glValue = 0;
        fallback = runFrames(width, height, glValue) == 0;
        releaseTestQueue();
    }
    cg_present_destroy();

    // Asked before anything is made (cg_fg_supports, here on the first adapter), as the mod asks Streamline: a no keeps
    // it prepared, dropped (cg_fg_unprepare) it is shut down, and another backend runs in its place.
    g_fake = {};
    bool unasked = !cg_fg_supports(nullptr);  // nothing prepared
    g_fg = new FakeBackend(false);
    bool saidNo = cg_fg_supports(nullptr) == 0 && g_fake.supports == 1 && g_fg && lstrlenA(cg_present_error()) > 0;
    printf("asked before anything is made: no, \"%s\"\n", cg_present_error());
    cg_fg_unprepare();
    bool dropped = !g_fg && g_fake.shutdowns == 1;
    g_fake = {};
    g_frameIndex = 0;
    g_fg = new FakeBackend(true);
    bool instead = cg_fg_supports(nullptr) == 1 && cg_present_create(hwnd, nullptr, 0) && cg_fg_active()
            && g_fake.supports == 2;
    if (instead) {
        makeTestQueue();
        glValue = 0;
        instead = runFrames(width, height, glValue, true) == 0;
        releaseTestQueue();
    }
    cg_present_destroy();
    bool asked = unasked && saidNo && dropped && instead && g_fake.shutdowns == 1;
    printf("asked before anything is made, then another in its place: %s\n", asked ? "ok" : "FAILED");

    // A backend that composes the HUD itself: the waitable object stays, and on its frames the HUD goes apart. Turned
    // off and on again while the presenter runs: off, the swap chain is a plain one with the presenter's waitable
    // object and the backend is asked nothing; on again, frames generate as before.
    g_fake = {};
    g_frameIndex = 0;
    g_fg = new FakeBackend(true, true);
    bool composePass = cg_present_create(hwnd, nullptr, 0) && cg_fg_active();
    if (composePass) {
        makeTestQueue();
        bool waitable = g_waitable != nullptr && !g_waitableOwned;  // each frame waits on it (runFrames)
        glValue = 0;
        int composeFirst = runFrames(width, height, glValue, true, true);
        bool off = composeFirst == 0 && cg_fg_enable(0) == 1 && g_waitable != nullptr && g_waitableOwned
                && !cg_fg_frame(0, g_frameIndex + 1, FG_FRAME_GENERATE, 1, 1, 1, nullptr);
        int composeOff = off ? runFrames(width, height, glValue) : -1;
        bool on = composeOff == 0 && cg_fg_enable(1) == 1 && g_waitable != nullptr && !g_waitableOwned;
        int composeResized = on ? cg_present_resize(800, 600) : -1;
        int composeSecond = composeResized < 0 ? -1 : runFrames(800, 600, glValue, true, true);
        releaseTestQueue();
        cg_present_destroy();
        // The presenter leaves the waitable object to the backend that owns the swap chain.
        composePass = waitable && off && on && composeSecond == 0 && g_fake.frames == 600 && g_fake.splits == 600
                && g_fake.badFrames == 0 && g_fake.presented == 600 && g_fake.switches == 2 && g_fake.shutdowns == 1
                && !g_fg && g_waitable == nullptr;
    }
    printf("HUD apart, off and on again: %d frames, %d split, %d bad, %d presented, %d switches, %d shutdowns: %s\n",
            g_fake.frames, g_fake.splits, g_fake.badFrames, g_fake.presented, g_fake.switches, g_fake.shutdowns,
            composePass ? "ok" : "FAILED");

    // A backend that starts off (the setting was off at the start): a plain swap chain, then on and off again.
    g_fake = {};
    g_frameIndex = 0;
    g_fg = new FakeBackend(true);
    bool startOff = cg_fg_enable(0) == 1 && cg_present_create(hwnd, nullptr, 0) && cg_fg_active() && g_waitable != nullptr;
    if (startOff) {
        makeTestQueue();
        glValue = 0;
        startOff = runFrames(width, height, glValue) == 0 && cg_fg_enable(1) == 1 && g_waitable == nullptr
                && runFrames(width, height, glValue, true) == 0 && cg_fg_enable(0) == 1 && g_waitable != nullptr
                && runFrames(width, height, glValue) == 0;
        releaseTestQueue();
    }
    cg_present_destroy();
    startOff = startOff && g_fake.frames == 300 && g_fake.presented == 300 && g_fake.badFrames == 0 && g_fake.switches == 3
            && g_fake.shutdowns == 1 && g_fgOn;
    printf("starting off, then on and off: %d frames, %d bad, %d switches: %s\n", g_fake.frames, g_fake.badFrames,
            g_fake.switches, startOff ? "ok" : "FAILED");

    // Streamline and FidelityFX without their DLLs refuse to prepare.
    bool noStreamline = !cg_fg_prepare(FG_BACKEND_DLSSG, L"C:\\no-such-folder", L"C:\\no-such-folder", L"C:\\no-such-folder", 0)
            && !g_fg;
    printf("Streamline without its DLLs: \"%s\": %s\n", cg_present_error(), noStreamline ? "ok" : "FAILED");
    bool noFfx = !cg_fg_prepare(FG_BACKEND_FSR, L"C:\\no-such-folder", L"C:\\no-such-folder", L"C:\\no-such-folder", 0)
            && !g_fg;
    printf("FidelityFX without its DLLs: \"%s\": %s\n", cg_present_error(), noFfx ? "ok" : "FAILED");

    pass = pass && fgPass && fallback && asked && composePass && startOff && noStreamline && noFfx;
    printf(pass ? "SMOKE PASS\n" : "SMOKE FAIL\n");
    return pass ? 0 : 1;
}

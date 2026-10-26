// Smoke test for the DirectX 12 presenter (cg_present.cpp) without OpenGL, for Wine on Linux (present-smoke.sh) or
// Windows. It builds the presenter into itself unshared (CG_PRESENT_UNSHARED: Wine's D3D12 has no shared handles) and
// plays OpenGL's part on a second D3D12 queue: writes a picture into a ring texture bottom row first, as OpenGL stores
// a frame, signals the "OpenGL done" fence, and has the presenter draw and present. Then it checks a captured back
// buffer is that picture the right way up with the colours in place, before and after a resize, over 300 frames each,
// and again with 3 buffers and frame latency 2. The GPU timestamps (cg_present_timing) must come back in order.
//
// Frame generation's plumbing (ABI 4) with a stand-in backend: it must get the queue and swap chain calls, a frame call
// with the four inputs before each draw, a presented call after each Present, and the presenter must signal a later
// "D3D12 done" value for the slot's inputs; no waitable object, frame latency refused, stop and shutdown on destroy.
// A backend that can't start must leave a presenter without it (cg_fg_error says why), and Streamline without
// sl.interposer.dll must refuse to prepare. "present-smoke.exe --streamline <folder>" instead tries NVIDIA's real
// Streamline DLLs from that folder (under Wine there is no NVIDIA card: the presenter must go on without them).
#define CG_PRESENT_UNSHARED
#include "cg_present.cpp"

#include <stdio.h>

static ID3D12CommandQueue* g_testQueue;
static ID3D12CommandAllocator* g_testAllocator;
static ID3D12GraphicsCommandList* g_testList;
static ID3D12Fence* g_testFence;
static UINT64 g_testValue;
static HANDLE g_testEvent;

/** The picture, as RGBA, at (x, y) from its top-left: red over blue, the left half darker green than the right. */
static unsigned picture(int x, int y, int width, int height) {
    unsigned red = y < height / 2 ? 255 : 0;
    unsigned green = x < width / 2 ? 64 : 192;
    unsigned blue = y < height / 2 ? 0 : 255;
    return red | green << 8 | blue << 16 | 255u << 24;
}

/** Writes the picture into {@code texture} the way OpenGL holds a frame: memory row 0 is the picture's bottom row. */
static bool writeAsOpenGl(ID3D12Resource* texture, int width, int height) {
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
        unsigned* line = (unsigned*) (mapped + footprint.Offset + (size_t) row * footprint.Footprint.RowPitch);
        for (int x = 0; x < width; x++) {
            line[x] = picture(x, height - 1 - row, width, height);
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
    ID3D12CommandList* lists[] = {g_testList};
    g_testQueue->ExecuteCommandLists(1, lists);
    g_testQueue->Signal(g_testFence, ++g_testValue);
    g_testFence->SetEventOnCompletion(g_testValue, g_testEvent);
    WaitForSingleObject(g_testEvent, INFINITE);
    staging->Release();
    return true;
}

/** What the stand-in backend saw (it is deleted on cg_present_destroy). */
static struct {
    int supports, devices, frames, framesWithInputs, badFrames, presented, resizes, stops, shutdowns;
    unsigned lastIndex;
} g_fake;

/** A frame generation backend that checks what the presenter hands it and makes the queue wait on its own fence. */
class FakeBackend : public FgBackend {
public:
    explicit FakeBackend(bool works) : m_works(works) {}
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
    bool frame(const FgFrame& frame) override {
        g_fake.frames++;
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
        if (frame.backWidth != g_backWidth || frame.backHeight != g_backHeight || frame.renderWidth > frame.backWidth
                || !frame.camera || frame.camera[FG_NEAR] != 0.05f
                || (g_fake.frames > 1 && frame.frameIndex != g_fake.lastIndex + 1)) {
            g_fake.badFrames++;
        }
        g_fake.lastIndex = frame.frameIndex;
        return true;
    }
    void presented(ID3D12CommandQueue* queue) override {
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
    void shutdown() override {
        g_fake.shutdowns++;
        if (m_fence) {
            m_fence->Release();
            m_fence = nullptr;
        }
    }
private:
    bool m_works;
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

/** 300 frames of the picture; with {@code fg}, each with a frame generation call and its inputs. Differing pixels. */
static int runFrames(int width, int height, unsigned long long& glValue, bool fg = false) {
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
    camera[FG_NEAR] = 0.05f;
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
        }
        int slot = frame % RING;
        if ((frame < RING || frame >= frames - RING) && !writeAsOpenGl(g_ring[slot], width, height)) {
            printf("writing the picture failed\n");
            return -1;
        }
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
            unsigned want = picture(x * width / size[0], y * height / size[1], width, height);
            const unsigned char* p = pixels + ((size_t) y * size[0] + x) * 4; // BGRA
            unsigned got = p[2] | p[1] << 8 | p[0] << 16 | (unsigned) p[3] << 24;
            if (got != want && differ++ < 5) {
                printf("  (%d, %d): %08X, expected %08X\n", x, y, got, want);
            }
        }
    }
    printf("back buffer %dx%d: %d pixels differ from the picture\n", size[0], size[1], differ);
    return differ;
}

/** The real Streamline from {@code folder}: prepare, create (going on without it if it can't run), frames, destroy. */
static int streamline(HWND hwnd, int width, int height, const char* folder) {
    wchar_t path[MAX_PATH];
    MultiByteToWideChar(CP_UTF8, 0, folder, -1, path, MAX_PATH);
    int prepared = cg_fg_prepare(FG_BACKEND_DLSSG, path, path, path, FG_SL_VERBOSE);
    printf("Streamline prepare: %d %s\n", prepared, prepared ? "" : cg_present_error());
    int result = 0;
    if (!cg_present_create(hwnd, nullptr, 0)) {
        printf("create failed: %s\n", cg_present_error());
        result = 1;
    } else {
        printf("frame generation active %d, error \"%s\"\n", cg_fg_active(), cg_fg_error());
        makeTestQueue();
        unsigned long long glValue = 0;
        result = runFrames(width, height, glValue, cg_fg_active() != 0) == 0 ? 0 : 1;
        releaseTestQueue();
        cg_present_destroy();
    }
    char line[LOG_LINE];
    int lines = 0;
    while (cg_fg_log_next(line, sizeof line) && lines++ < 60) {
        printf("  log %s\n", line);
    }
    printf(result == 0 ? "STREAMLINE PASS\n" : "STREAMLINE FAIL\n");
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
        return streamline(hwnd, width, height, argv[2]);
    }

    if (!cg_present_create(hwnd, nullptr, 0)) {
        printf("create failed: %s\n", cg_present_error());
        return 1;
    }
    char name[256];
    cg_present_adapter_name(name, sizeof name);
    printf("adapter %s, tearing %d, window %dx%d\n", name, cg_present_tearing(), width, height);

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
    bool pass = first == 0 && second == 0 && third == 0 && timed > 0 && badTimes == 0;

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

    // Streamline without its DLLs refuses to prepare.
    bool noStreamline = !cg_fg_prepare(FG_BACKEND_DLSSG, L"C:\\no-such-folder", L"C:\\no-such-folder", L"C:\\no-such-folder", 0)
            && !g_fg;
    printf("Streamline without its DLLs: \"%s\": %s\n", cg_present_error(), noStreamline ? "ok" : "FAILED");

    pass = pass && fgPass && fallback && noStreamline;
    printf(pass ? "SMOKE PASS\n" : "SMOKE FAIL\n");
    return pass ? 0 : 1;
}

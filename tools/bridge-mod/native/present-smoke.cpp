// Smoke test for the DirectX 12 presenter (cg_present.cpp) without OpenGL, for Wine on Linux (present-smoke.sh) or
// Windows. It builds the presenter into itself unshared (CG_PRESENT_UNSHARED: Wine's D3D12 has no shared handles) and
// plays OpenGL's part on a second D3D12 queue: writes a picture into a ring texture bottom row first, as OpenGL stores
// a frame, signals the "OpenGL done" fence, and has the presenter draw and present. Then it checks a captured back
// buffer is that picture the right way up with the colours in place, before and after a resize, over 300 frames each.
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

static int runFrames(int width, int height, unsigned long long& glValue) {
    HANDLE handles[RING];
    UINT64 sizes[RING];
    if (!cg_present_create_textures(width, height, handles, sizes)) {
        printf("textures failed: %s\n", cg_present_error());
        return -1;
    }
    printf("textures %dx%d, %llu bytes each\n", width, height, (unsigned long long) sizes[0]);
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
        if (cg_present_draw(slot, glValue) < 0 || cg_present_present(0) < 0) {
            printf("frame %d failed: %s\n", frame, cg_present_error());
            return -1;
        }
    }
    QueryPerformanceCounter(&end);
    printf("%d frames in %.0f ms\n", frames, (end.QuadPart - start.QuadPart) * 1000.0 / frequency.QuadPart);

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

int main() {
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

    if (!cg_present_create(hwnd, nullptr, 0)) {
        printf("create failed: %s\n", cg_present_error());
        return 1;
    }
    char name[256];
    cg_present_adapter_name(name, sizeof name);
    printf("adapter %s, tearing %d, window %dx%d\n", name, cg_present_tearing(), width, height);

    D3D12_COMMAND_QUEUE_DESC queueDesc = {};
    queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    g_device->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&g_testQueue));
    g_device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&g_testAllocator));
    g_device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, g_testAllocator, nullptr, IID_PPV_ARGS(&g_testList));
    g_testList->Close();
    g_device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&g_testFence));
    g_testEvent = CreateEventA(nullptr, FALSE, FALSE, nullptr);

    unsigned long long glValue = 0;
    int first = runFrames(width, height, glValue);
    int resized = cg_present_resize(800, 600);
    if (resized < 0) {
        printf("resize failed: %s\n", cg_present_error());
        return 1;
    }
    int second = first < 0 ? -1 : runFrames(800, 600, glValue);

    g_testList->Release();
    g_testAllocator->Release();
    g_testQueue->Release();
    g_testFence->Release();
    CloseHandle(g_testEvent);
    cg_present_destroy();
    bool pass = first == 0 && second == 0;
    printf(pass ? "SMOKE PASS\n" : "SMOKE FAIL\n");
    return pass ? 0 : 1;
}

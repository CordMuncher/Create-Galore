// cg_present.dll: the Bridge mod's DirectX 12 presenter.
//
// Shows the game's frames through a DXGI flip-model swap chain on a Direct3D 12 command queue, the swap chain NVIDIA
// Streamline (DLSS Frame Generation) and AMD FidelityFX (frame generation swap chain) wrap or replace in a D3D12 game.
// The game still renders with OpenGL on a hidden context. Each frame its colour texture is copied into one of three
// textures that Direct3D 12 shares with OpenGL (EXT_external_objects_win32: the texture's NT handle is imported as an
// OpenGL memory object), and two shared D3D12 fences, imported as OpenGL semaphores, order the two APIs on the GPU:
//  - OpenGL signals the "OpenGL done" fence after its copy; the D3D12 queue waits for that value, then draws the
//    texture into the back buffer and signals the "D3D12 done" fence;
//  - before OpenGL writes the same texture again, it waits for the "D3D12 done" value of that texture's last draw.
//
// The swap chain, its flags, the frame latency of 1 and the blit shader are those of the Direct3D 11 path
// (InteropContext.java, from Perfect Presentation), which stays the fallback. Resource states follow Microsoft's
// implicit state rules: the shared textures stay in COMMON outside a command list (promoted to
// PIXEL_SHADER_RESOURCE on use, decayed back when the list completes), back buffers are COMMON (= PRESENT) at Present.
//
// All functions must be called on one thread (the render thread). Built by build-present.sh with clang and lld-link
// against the Windows SDK; no third-party SDK. Graphics DLLs are loaded at run time (cg_present_create).

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <d3dcompiler.h>

#define CG_API extern "C" __declspec(dllexport)

// Bumped whenever a function below changes; the Java side refuses a helper with another version.
static const int CG_PRESENT_ABI = 1;

static const UINT BUFFERS = 2;      // swap chain buffers, as on the Direct3D 11 path
static const int RING = 3;          // shared textures OpenGL copies into, as on the Direct3D 11 path
static const int FRAMES = 3;        // command allocators in flight
static const DXGI_FORMAT SWAP_FORMAT = DXGI_FORMAT_B8G8R8A8_UNORM;
static const DXGI_FORMAT RING_FORMAT = DXGI_FORMAT_R8G8B8A8_UNORM;

// cg_present_create flags
static const int FLAG_DEBUG_LAYER = 1;

static char g_error[1024];

typedef HRESULT(WINAPI* CreateDXGIFactory2Fn)(UINT, REFIID, void**);
typedef HRESULT(WINAPI* D3D12CreateDeviceFn)(IUnknown*, D3D_FEATURE_LEVEL, REFIID, void**);
typedef HRESULT(WINAPI* D3D12GetDebugInterfaceFn)(REFIID, void**);
typedef HRESULT(WINAPI* D3D12SerializeRootSignatureFn)(const D3D12_ROOT_SIGNATURE_DESC*, D3D_ROOT_SIGNATURE_VERSION, ID3DBlob**, ID3DBlob**);
typedef HRESULT(WINAPI* D3DCompileFn)(LPCVOID, SIZE_T, LPCSTR, const D3D_SHADER_MACRO*, ID3DInclude*, LPCSTR, LPCSTR, UINT, UINT, ID3DBlob**, ID3DBlob**);

static IDXGIFactory4* g_factory;
static IDXGIAdapter1* g_adapter;
static ID3D12Device* g_device;
static ID3D12CommandQueue* g_queue;
static IDXGISwapChain3* g_swapChain;
static HANDLE g_waitable;
static UINT g_swapFlags;
static bool g_tearing;
static ID3D12Resource* g_backBuffers[BUFFERS];
static UINT g_backWidth;
static UINT g_backHeight;
static ID3D12DescriptorHeap* g_rtvHeap;
static UINT g_rtvSize;
static ID3D12DescriptorHeap* g_srvHeap;
static UINT g_srvSize;
static ID3D12RootSignature* g_rootSignature;
static ID3D12PipelineState* g_pipeline;
static ID3D12CommandAllocator* g_allocators[FRAMES];
static UINT64 g_allocatorValue[FRAMES];
static int g_frame;
static ID3D12GraphicsCommandList* g_list;
static ID3D12Fence* g_glFence;
static HANDLE g_glFenceHandle;
static ID3D12Fence* g_doneFence;
static HANDLE g_doneFenceHandle;
static UINT64 g_doneValue;
static HANDLE g_event;
static ID3D12Resource* g_ring[RING];
static HANDLE g_ringHandles[RING];

// Testing: a copy of one drawn back buffer (cg_present_capture_request / cg_present_capture_read).
static bool g_captureRequested;
static ID3D12Resource* g_readback;
static D3D12_PLACED_SUBRESOURCE_FOOTPRINT g_readbackFootprint;
static UINT64 g_readbackValue;

static const char BLIT_SHADER[] =
    "struct VSOut { float4 pos : SV_Position; float2 uv : TEXCOORD0; };\n"
    "VSOut VsMain(uint id : SV_VertexID) {\n"
    "    VSOut o;\n"
    "    o.pos = float4(id >> 1, id & 1, 0, 0.5) * 4 - 1;\n"
    "    o.uv = float2(id >> 1, id & 1) * 2;\n"
    "    return o;\n"
    "}\n"
    "Texture2D srcTex : register(t0);\n"
    "SamplerState samp : register(s0);\n"
    "float4 PsMain(VSOut i) : SV_Target { return srcTex.Sample(samp, i.uv); }\n";

template <typename T> static void release(T*& object) {
    if (object) {
        object->Release();
        object = nullptr;
    }
}

static void closeHandle(HANDLE& handle) {
    if (handle) {
        CloseHandle(handle);
        handle = nullptr;
    }
}

static bool fail(const char* what, HRESULT hr) {
    wsprintfA(g_error, "%s failed: HRESULT 0x%08X", what, (unsigned) hr);
    return false;
}

static bool failText(const char* text) {
    lstrcpynA(g_error, text, sizeof g_error);
    return false;
}

static FARPROC symbol(const char* dll, const char* name) {
    HMODULE module = GetModuleHandleA(dll);
    if (!module) {
        module = LoadLibraryA(dll);
    }
    return module ? GetProcAddress(module, name) : nullptr;
}

/** Blocks until the queue has finished everything submitted so far. */
static void waitIdle() {
    if (!g_queue || !g_doneFence) {
        return;
    }
    g_queue->Signal(g_doneFence, ++g_doneValue);
    if (g_doneFence->GetCompletedValue() < g_doneValue) {
        g_doneFence->SetEventOnCompletion(g_doneValue, g_event);
        WaitForSingleObject(g_event, INFINITE);
    }
}

static D3D12_CPU_DESCRIPTOR_HANDLE rtv(UINT index) {
    D3D12_CPU_DESCRIPTOR_HANDLE handle = g_rtvHeap->GetCPUDescriptorHandleForHeapStart();
    handle.ptr += (SIZE_T) index * g_rtvSize;
    return handle;
}

static bool getBackBuffers() {
    for (UINT i = 0; i < BUFFERS; i++) {
        HRESULT hr = g_swapChain->GetBuffer(i, IID_PPV_ARGS(&g_backBuffers[i]));
        if (FAILED(hr)) {
            return fail("GetBuffer", hr);
        }
        g_device->CreateRenderTargetView(g_backBuffers[i], nullptr, rtv(i));
    }
    D3D12_RESOURCE_DESC desc = g_backBuffers[0]->GetDesc();
    g_backWidth = (UINT) desc.Width;
    g_backHeight = desc.Height;
    return true;
}

static void releaseBackBuffers() {
    for (UINT i = 0; i < BUFFERS; i++) {
        release(g_backBuffers[i]);
    }
}

static bool createPipeline() {
    D3D12SerializeRootSignatureFn serialize = (D3D12SerializeRootSignatureFn) symbol("d3d12.dll", "D3D12SerializeRootSignature");
    D3DCompileFn compile = (D3DCompileFn) symbol("d3dcompiler_47.dll", "D3DCompile");
    if (!serialize || !compile) {
        return failText(!serialize ? "No D3D12SerializeRootSignature in d3d12.dll" : "Could not load D3DCompile from d3dcompiler_47.dll");
    }

    D3D12_DESCRIPTOR_RANGE range = {};
    range.RangeType = D3D12_DESCRIPTOR_RANGE_TYPE_SRV;
    range.NumDescriptors = 1;
    range.OffsetInDescriptorsFromTableStart = D3D12_DESCRIPTOR_RANGE_OFFSET_APPEND;
    D3D12_ROOT_PARAMETER parameter = {};
    parameter.ParameterType = D3D12_ROOT_PARAMETER_TYPE_DESCRIPTOR_TABLE;
    parameter.DescriptorTable.NumDescriptorRanges = 1;
    parameter.DescriptorTable.pDescriptorRanges = &range;
    parameter.ShaderVisibility = D3D12_SHADER_VISIBILITY_PIXEL;
    D3D12_STATIC_SAMPLER_DESC sampler = {};
    sampler.Filter = D3D12_FILTER_MIN_MAG_MIP_POINT;
    sampler.AddressU = D3D12_TEXTURE_ADDRESS_MODE_CLAMP;
    sampler.AddressV = D3D12_TEXTURE_ADDRESS_MODE_CLAMP;
    sampler.AddressW = D3D12_TEXTURE_ADDRESS_MODE_CLAMP;
    sampler.MaxLOD = D3D12_FLOAT32_MAX;
    sampler.ShaderVisibility = D3D12_SHADER_VISIBILITY_PIXEL;
    D3D12_ROOT_SIGNATURE_DESC rootDesc = {};
    rootDesc.NumParameters = 1;
    rootDesc.pParameters = &parameter;
    rootDesc.NumStaticSamplers = 1;
    rootDesc.pStaticSamplers = &sampler;
    rootDesc.Flags = D3D12_ROOT_SIGNATURE_FLAG_DENY_VERTEX_SHADER_ROOT_ACCESS;

    ID3DBlob* blob = nullptr;
    ID3DBlob* errors = nullptr;
    HRESULT hr = serialize(&rootDesc, D3D_ROOT_SIGNATURE_VERSION_1, &blob, &errors);
    release(errors);
    if (FAILED(hr)) {
        return fail("D3D12SerializeRootSignature", hr);
    }
    hr = g_device->CreateRootSignature(0, blob->GetBufferPointer(), blob->GetBufferSize(), IID_PPV_ARGS(&g_rootSignature));
    release(blob);
    if (FAILED(hr)) {
        return fail("CreateRootSignature", hr);
    }

    ID3DBlob* vertex = nullptr;
    ID3DBlob* pixel = nullptr;
    hr = compile(BLIT_SHADER, sizeof BLIT_SHADER - 1, "blit", nullptr, nullptr, "VsMain", "vs_5_0", 0, 0, &vertex, &errors);
    if (SUCCEEDED(hr)) {
        release(errors);
        hr = compile(BLIT_SHADER, sizeof BLIT_SHADER - 1, "blit", nullptr, nullptr, "PsMain", "ps_5_0", 0, 0, &pixel, &errors);
    }
    if (FAILED(hr)) {
        wsprintfA(g_error, "D3DCompile failed: HRESULT 0x%08X ", (unsigned) hr);
        if (errors) {
            int used = lstrlenA(g_error);
            lstrcpynA(g_error + used, (const char*) errors->GetBufferPointer(), (int) sizeof g_error - used);
        }
        release(errors);
        release(vertex);
        return false;
    }

    D3D12_GRAPHICS_PIPELINE_STATE_DESC pso = {};
    pso.pRootSignature = g_rootSignature;
    pso.VS = {vertex->GetBufferPointer(), vertex->GetBufferSize()};
    pso.PS = {pixel->GetBufferPointer(), pixel->GetBufferSize()};
    D3D12_RENDER_TARGET_BLEND_DESC& blend = pso.BlendState.RenderTarget[0];
    blend.SrcBlend = blend.SrcBlendAlpha = D3D12_BLEND_ONE;
    blend.DestBlend = blend.DestBlendAlpha = D3D12_BLEND_ZERO;
    blend.BlendOp = blend.BlendOpAlpha = D3D12_BLEND_OP_ADD;
    blend.LogicOp = D3D12_LOGIC_OP_NOOP;
    blend.RenderTargetWriteMask = D3D12_COLOR_WRITE_ENABLE_ALL;
    pso.SampleMask = UINT_MAX;
    // As the Direct3D 11 path's zeroed rasterizer state: no depth clip, since the triangle sits at z = -1.
    pso.RasterizerState.FillMode = D3D12_FILL_MODE_SOLID;
    pso.RasterizerState.CullMode = D3D12_CULL_MODE_NONE;
    pso.RasterizerState.DepthClipEnable = FALSE;
    pso.DepthStencilState.DepthFunc = D3D12_COMPARISON_FUNC_ALWAYS;
    pso.DepthStencilState.FrontFace = {D3D12_STENCIL_OP_KEEP, D3D12_STENCIL_OP_KEEP, D3D12_STENCIL_OP_KEEP, D3D12_COMPARISON_FUNC_ALWAYS};
    pso.DepthStencilState.BackFace = pso.DepthStencilState.FrontFace;
    pso.PrimitiveTopologyType = D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE;
    pso.NumRenderTargets = 1;
    pso.RTVFormats[0] = SWAP_FORMAT;
    pso.SampleDesc.Count = 1;
    hr = g_device->CreateGraphicsPipelineState(&pso, IID_PPV_ARGS(&g_pipeline));
    release(vertex);
    release(pixel);
    return SUCCEEDED(hr) || fail("CreateGraphicsPipelineState", hr);
}

// present-smoke.cpp builds this file with CG_PRESENT_UNSHARED for Wine, whose D3D12 has no shared handles: the same
// fences and textures, unshared, which the test reaches directly.
#ifdef CG_PRESENT_UNSHARED
static const bool SHARED = false;
#else
static const bool SHARED = true;
#endif

static bool createSharedFence(ID3D12Fence*& fence, HANDLE& handle, const char* what) {
    HRESULT hr = g_device->CreateFence(0, SHARED ? D3D12_FENCE_FLAG_SHARED : D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&fence));
    if (FAILED(hr)) {
        return fail(what, hr);
    }
    hr = SHARED ? g_device->CreateSharedHandle(fence, nullptr, GENERIC_ALL, nullptr, &handle) : S_OK;
    return SUCCEEDED(hr) || fail("CreateSharedHandle (fence)", hr);
}

CG_API void cg_present_destroy();

CG_API int cg_present_abi() {
    return CG_PRESENT_ABI;
}

/** Why the last call failed. */
CG_API const char* cg_present_error() {
    return g_error;
}

/**
 * Creates the device, queue and swap chain on {@code hwnd}. {@code luid} (8 bytes, or null for the first adapter)
 * picks the adapter: OpenGL's GL_DEVICE_LUID_EXT, so both APIs run on the same GPU. 1 on success, 0 on failure
 * (cg_present_error says why; everything made so far is released).
 */
CG_API int cg_present_create(HWND hwnd, const LUID* luid, int flags) {
    g_error[0] = 0;
    CreateDXGIFactory2Fn createFactory = (CreateDXGIFactory2Fn) symbol("dxgi.dll", "CreateDXGIFactory2");
    D3D12CreateDeviceFn createDevice = (D3D12CreateDeviceFn) symbol("d3d12.dll", "D3D12CreateDevice");
    if (!createFactory || !createDevice) {
        return failText(!createFactory ? "No CreateDXGIFactory2 in dxgi.dll" : "Could not load D3D12CreateDevice from d3d12.dll");
    }
    UINT factoryFlags = 0;
    if (flags & FLAG_DEBUG_LAYER) {
        D3D12GetDebugInterfaceFn getDebug = (D3D12GetDebugInterfaceFn) symbol("d3d12.dll", "D3D12GetDebugInterface");
        ID3D12Debug* debug = nullptr;
        if (getDebug && SUCCEEDED(getDebug(IID_PPV_ARGS(&debug)))) {
            debug->EnableDebugLayer();
            release(debug);
            factoryFlags = DXGI_CREATE_FACTORY_DEBUG;
        }
    }

    bool ok = false;
    do {
        HRESULT hr = createFactory(factoryFlags, IID_PPV_ARGS(&g_factory));
        if (FAILED(hr)) { fail("CreateDXGIFactory2", hr); break; }
        hr = luid ? g_factory->EnumAdapterByLuid(*luid, IID_PPV_ARGS(&g_adapter)) : g_factory->EnumAdapters1(0, &g_adapter);
        if (FAILED(hr)) { fail(luid ? "EnumAdapterByLuid (OpenGL's adapter)" : "EnumAdapters1", hr); break; }
        hr = createDevice(g_adapter, D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&g_device));
        if (FAILED(hr)) { fail("D3D12CreateDevice", hr); break; }

        D3D12_COMMAND_QUEUE_DESC queueDesc = {};
        queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
        hr = g_device->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&g_queue));
        if (FAILED(hr)) { fail("CreateCommandQueue", hr); break; }

        IDXGIFactory5* factory5 = nullptr;
        if (SUCCEEDED(g_factory->QueryInterface(IID_PPV_ARGS(&factory5)))) {
            BOOL allow = FALSE;
            g_tearing = SUCCEEDED(factory5->CheckFeatureSupport(DXGI_FEATURE_PRESENT_ALLOW_TEARING, &allow, sizeof allow)) && allow;
            release(factory5);
        }
        g_swapFlags = DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT | (g_tearing ? DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING : 0);

        DXGI_SWAP_CHAIN_DESC1 desc = {};
        desc.Format = SWAP_FORMAT;
        desc.SampleDesc.Count = 1;
        desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        desc.BufferCount = BUFFERS;
        desc.Scaling = DXGI_SCALING_NONE;
        desc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        desc.AlphaMode = DXGI_ALPHA_MODE_UNSPECIFIED;
        desc.Flags = g_swapFlags;
        IDXGISwapChain1* swapChain1 = nullptr;
        hr = g_factory->CreateSwapChainForHwnd(g_queue, hwnd, &desc, nullptr, nullptr, &swapChain1);
        if (FAILED(hr)) { fail("CreateSwapChainForHwnd", hr); break; }
        hr = swapChain1->QueryInterface(IID_PPV_ARGS(&g_swapChain));
        release(swapChain1);
        if (FAILED(hr)) { fail("QueryInterface(IDXGISwapChain3)", hr); break; }
        hr = g_swapChain->SetMaximumFrameLatency(1);
        if (FAILED(hr)) { fail("SetMaximumFrameLatency", hr); break; }
        g_waitable = g_swapChain->GetFrameLatencyWaitableObject();

        D3D12_DESCRIPTOR_HEAP_DESC rtvDesc = {D3D12_DESCRIPTOR_HEAP_TYPE_RTV, BUFFERS, D3D12_DESCRIPTOR_HEAP_FLAG_NONE, 0};
        hr = g_device->CreateDescriptorHeap(&rtvDesc, IID_PPV_ARGS(&g_rtvHeap));
        if (FAILED(hr)) { fail("CreateDescriptorHeap (RTV)", hr); break; }
        g_rtvSize = g_device->GetDescriptorHandleIncrementSize(D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
        D3D12_DESCRIPTOR_HEAP_DESC srvDesc = {D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV, RING, D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE, 0};
        hr = g_device->CreateDescriptorHeap(&srvDesc, IID_PPV_ARGS(&g_srvHeap));
        if (FAILED(hr)) { fail("CreateDescriptorHeap (SRV)", hr); break; }
        g_srvSize = g_device->GetDescriptorHandleIncrementSize(D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV);
        if (!getBackBuffers() || !createPipeline()) break;

        for (int i = 0; i < FRAMES; i++) {
            hr = g_device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&g_allocators[i]));
            if (FAILED(hr)) break;
            g_allocatorValue[i] = 0;
        }
        if (FAILED(hr)) { fail("CreateCommandAllocator", hr); break; }
        hr = g_device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, g_allocators[0], g_pipeline, IID_PPV_ARGS(&g_list));
        if (FAILED(hr)) { fail("CreateCommandList", hr); break; }
        g_list->Close();

        if (!createSharedFence(g_glFence, g_glFenceHandle, "CreateFence (OpenGL done)")
                || !createSharedFence(g_doneFence, g_doneFenceHandle, "CreateFence (D3D12 done)")) break;
        g_doneValue = 0;
        g_frame = 0;
        g_event = CreateEventA(nullptr, FALSE, FALSE, nullptr);
        if (!g_event) { fail("CreateEvent", HRESULT_FROM_WIN32(GetLastError())); break; }
        ok = true;
    } while (false);

    if (!ok) {
        char reason[sizeof g_error];
        lstrcpynA(reason, g_error, sizeof reason);
        cg_present_destroy();
        lstrcpynA(g_error, reason, sizeof g_error);
    }
    return ok ? 1 : 0;
}

/** The ID3D12Device (for NVIDIA Reflex), null before cg_present_create. */
CG_API void* cg_present_device() {
    return g_device;
}

/** The adapter's name as UTF-8 into {@code out} (at most {@code size} bytes with the 0). */
CG_API void cg_present_adapter_name(char* out, int size) {
    DXGI_ADAPTER_DESC1 desc = {};
    if (size <= 0) {
        return;
    }
    out[0] = 0;
    if (g_adapter && SUCCEEDED(g_adapter->GetDesc1(&desc))) {
        WideCharToMultiByte(CP_UTF8, 0, desc.Description, -1, out, size, nullptr, nullptr);
        out[size - 1] = 0;
    }
}

/** 1 if Present may tear (DXGI_FEATURE_PRESENT_ALLOW_TEARING), for the log. */
CG_API int cg_present_tearing() {
    return g_tearing ? 1 : 0;
}

/** NT handle of a shared fence for OpenGL to import (GL_HANDLE_TYPE_D3D12_FENCE_EXT): 0 OpenGL done, 1 D3D12 done. */
CG_API HANDLE cg_present_fence_handle(int which) {
    return which == 0 ? g_glFenceHandle : g_doneFenceHandle;
}

/** Releases the shared textures (after the GPU has finished with them). */
CG_API void cg_present_release_textures() {
    waitIdle();
    for (int i = 0; i < RING; i++) {
        release(g_ring[i]);
        closeHandle(g_ringHandles[i]);
    }
}

/**
 * Makes the three shared textures, {@code width} x {@code height} RGBA8, and their shader views. Fills
 * {@code handles} with their NT handles (GL_HANDLE_TYPE_D3D12_RESOURCE_EXT; owned here until
 * cg_present_release_textures) and {@code sizes} with their allocation sizes. 1 on success.
 */
CG_API int cg_present_create_textures(int width, int height, HANDLE* handles, UINT64* sizes) {
    cg_present_release_textures();
    D3D12_HEAP_PROPERTIES heap = {};
    heap.Type = D3D12_HEAP_TYPE_DEFAULT;
    D3D12_RESOURCE_DESC desc = {};
    desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
    desc.Width = (UINT64) width;
    desc.Height = (UINT) height;
    desc.DepthOrArraySize = 1;
    desc.MipLevels = 1;
    desc.Format = RING_FORMAT;
    desc.SampleDesc.Count = 1;
    desc.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
    desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET;
    UINT64 size = g_device->GetResourceAllocationInfo(0, 1, &desc).SizeInBytes;
    D3D12_CPU_DESCRIPTOR_HANDLE view = g_srvHeap->GetCPUDescriptorHandleForHeapStart();
    for (int i = 0; i < RING; i++) {
        HRESULT hr = g_device->CreateCommittedResource(&heap, SHARED ? D3D12_HEAP_FLAG_SHARED : D3D12_HEAP_FLAG_NONE, &desc,
                D3D12_RESOURCE_STATE_COMMON, nullptr, IID_PPV_ARGS(&g_ring[i]));
        if (FAILED(hr)) {
            cg_present_release_textures();
            return fail("CreateCommittedResource (shared texture)", hr);
        }
        hr = SHARED ? g_device->CreateSharedHandle(g_ring[i], nullptr, GENERIC_ALL, nullptr, &g_ringHandles[i]) : S_OK;
        if (FAILED(hr)) {
            cg_present_release_textures();
            return fail("CreateSharedHandle (texture)", hr);
        }
        g_device->CreateShaderResourceView(g_ring[i], nullptr, view);
        view.ptr += g_srvSize;
        handles[i] = g_ringHandles[i];
        sizes[i] = size;
    }
    return 1;
}

/** Waits for the swap chain's frame latency object (frame latency 1). Returns WaitForSingleObject's result. */
CG_API int cg_present_wait(int timeoutMs) {
    return (int) WaitForSingleObject(g_waitable, (DWORD) timeoutMs);
}

/**
 * Draws shared texture {@code slot} into the current back buffer once OpenGL has signalled {@code glValue} on the
 * "OpenGL done" fence. Returns the "D3D12 done" value signalled after the draw (OpenGL waits for it before writing
 * {@code slot} again), or -1 on failure.
 */
CG_API long long cg_present_draw(int slot, unsigned long long glValue) {
    if (slot < 0 || slot >= RING || !g_ring[slot]) {
        failText("cg_present_draw: no shared texture in that slot");
        return -1;
    }
    int f = g_frame % FRAMES;
    if (g_doneFence->GetCompletedValue() < g_allocatorValue[f]) {
        g_doneFence->SetEventOnCompletion(g_allocatorValue[f], g_event);
        WaitForSingleObject(g_event, INFINITE);
    }
    HRESULT hr = g_allocators[f]->Reset();
    if (SUCCEEDED(hr)) {
        hr = g_list->Reset(g_allocators[f], g_pipeline);
    }
    if (FAILED(hr)) {
        fail("command list Reset", hr);
        return -1;
    }
    UINT index = g_swapChain->GetCurrentBackBufferIndex();
    ID3D12Resource* back = g_backBuffers[index];

    D3D12_RESOURCE_BARRIER barrier = {};
    barrier.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier.Transition.pResource = back;
    barrier.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
    barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_RENDER_TARGET;
    g_list->ResourceBarrier(1, &barrier);

    g_list->SetGraphicsRootSignature(g_rootSignature);
    g_list->SetDescriptorHeaps(1, &g_srvHeap);
    D3D12_GPU_DESCRIPTOR_HANDLE texture = g_srvHeap->GetGPUDescriptorHandleForHeapStart();
    texture.ptr += (UINT64) slot * g_srvSize;
    g_list->SetGraphicsRootDescriptorTable(0, texture);
    D3D12_VIEWPORT viewport = {0.0f, 0.0f, (float) g_backWidth, (float) g_backHeight, 0.0f, 1.0f};
    D3D12_RECT scissor = {0, 0, (LONG) g_backWidth, (LONG) g_backHeight};
    g_list->RSSetViewports(1, &viewport);
    g_list->RSSetScissorRects(1, &scissor);
    D3D12_CPU_DESCRIPTOR_HANDLE target = rtv(index);
    g_list->OMSetRenderTargets(1, &target, FALSE, nullptr);
    g_list->IASetPrimitiveTopology(D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_list->DrawInstanced(3, 1, 0, 0);

    bool capture = g_captureRequested && g_readback;
    barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_RENDER_TARGET;
    barrier.Transition.StateAfter = capture ? D3D12_RESOURCE_STATE_COPY_SOURCE : D3D12_RESOURCE_STATE_PRESENT;
    g_list->ResourceBarrier(1, &barrier);
    if (capture) {
        D3D12_TEXTURE_COPY_LOCATION dst = {};
        dst.pResource = g_readback;
        dst.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
        dst.PlacedFootprint = g_readbackFootprint;
        D3D12_TEXTURE_COPY_LOCATION src = {};
        src.pResource = back;
        src.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        src.SubresourceIndex = 0;
        g_list->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
        barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
        barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_PRESENT;
        g_list->ResourceBarrier(1, &barrier);
    }
    hr = g_list->Close();
    if (FAILED(hr)) {
        fail("command list Close", hr);
        return -1;
    }

    g_queue->Wait(g_glFence, glValue);
    ID3D12CommandList* lists[] = {g_list};
    g_queue->ExecuteCommandLists(1, lists);
    g_queue->Signal(g_doneFence, ++g_doneValue);
    g_allocatorValue[f] = g_doneValue;
    g_frame++;
    if (capture) {
        g_captureRequested = false;
        g_readbackValue = g_doneValue;
    }
    return (long long) g_doneValue;
}

/** Presents the back buffer: {@code syncInterval} 0 tears where the system allows it. Returns the HRESULT. */
CG_API int cg_present_present(int syncInterval) {
    UINT flags = syncInterval == 0 && g_tearing ? DXGI_PRESENT_ALLOW_TEARING : 0;
    HRESULT hr = g_swapChain->Present((UINT) syncInterval, flags);
    if (FAILED(hr)) {
        HRESULT reason = hr == DXGI_ERROR_DEVICE_REMOVED ? g_device->GetDeviceRemovedReason() : hr;
        wsprintfA(g_error, "Present failed: HRESULT 0x%08X (reason 0x%08X)", (unsigned) hr, (unsigned) reason);
    }
    return (int) hr;
}

/** Resizes the swap chain's buffers (0 x 0: the window's size). Returns the HRESULT. */
CG_API int cg_present_resize(int width, int height) {
    waitIdle();
    releaseBackBuffers();
    release(g_readback);
    HRESULT hr = g_swapChain->ResizeBuffers(0, (UINT) width, (UINT) height, DXGI_FORMAT_UNKNOWN, g_swapFlags);
    if (FAILED(hr)) {
        fail("ResizeBuffers", hr);
        return (int) hr;
    }
    return getBackBuffers() ? 0 : E_FAIL;
}

/** Testing: the next cg_present_draw also copies the back buffer it draws for cg_present_capture_read. */
CG_API int cg_present_capture_request() {
    if (!g_readback) {
        D3D12_RESOURCE_DESC desc = g_backBuffers[0]->GetDesc();
        UINT64 total = 0;
        g_device->GetCopyableFootprints(&desc, 0, 1, 0, &g_readbackFootprint, nullptr, nullptr, &total);
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
        HRESULT hr = g_device->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_NONE, &buffer, D3D12_RESOURCE_STATE_COPY_DEST,
                nullptr, IID_PPV_ARGS(&g_readback));
        if (FAILED(hr)) {
            return fail("CreateCommittedResource (readback)", hr);
        }
    }
    g_readbackValue = 0;
    g_captureRequested = true;
    return 1;
}

/**
 * Testing: waits for the captured back buffer and copies it into {@code out} as tightly packed BGRA rows, top row
 * first ({@code capacity} bytes at most). Writes its size to {@code size} (width, height). 1 on success, 0 if no
 * capture was drawn yet.
 */
CG_API int cg_present_capture_read(unsigned char* out, long long capacity, int* size) {
    if (!g_readback || g_readbackValue == 0) {
        return 0;
    }
    if (g_doneFence->GetCompletedValue() < g_readbackValue) {
        g_doneFence->SetEventOnCompletion(g_readbackValue, g_event);
        WaitForSingleObject(g_event, INFINITE);
    }
    UINT width = g_readbackFootprint.Footprint.Width;
    UINT height = g_readbackFootprint.Footprint.Height;
    UINT pitch = g_readbackFootprint.Footprint.RowPitch;
    if ((long long) width * height * 4 > capacity) {
        return failText("capture buffer too small");
    }
    void* mapped = nullptr;
    D3D12_RANGE read = {0, (SIZE_T) pitch * height};
    HRESULT hr = g_readback->Map(0, &read, &mapped);
    if (FAILED(hr)) {
        return fail("Map (readback)", hr);
    }
    for (UINT y = 0; y < height; y++) {
        CopyMemory(out + (SIZE_T) y * width * 4, (unsigned char*) mapped + g_readbackFootprint.Offset + (SIZE_T) y * pitch, (SIZE_T) width * 4);
    }
    D3D12_RANGE written = {0, 0};
    g_readback->Unmap(0, &written);
    size[0] = (int) width;
    size[1] = (int) height;
    g_readbackValue = 0;
    return 1;
}

/** Releases everything (after the GPU has finished). Safe to call more than once. */
CG_API void cg_present_destroy() {
    waitIdle();
    cg_present_release_textures();
    release(g_readback);
    g_captureRequested = false;
    g_readbackValue = 0;
    release(g_list);
    for (int i = 0; i < FRAMES; i++) {
        release(g_allocators[i]);
    }
    release(g_pipeline);
    release(g_rootSignature);
    releaseBackBuffers();
    release(g_srvHeap);
    release(g_rtvHeap);
    closeHandle(g_waitable);
    release(g_swapChain);
    release(g_glFence);
    closeHandle(g_glFenceHandle);
    release(g_doneFence);
    closeHandle(g_doneFenceHandle);
    closeHandle(g_event);
    release(g_queue);
    release(g_device);
    release(g_adapter);
    release(g_factory);
}

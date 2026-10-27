// cg_ngx.dll: the Bridge mod's small native helper for DLSS and DLSS Ray Reconstruction (NVIDIA NGX), on Direct3D 11
// (DLSS's own device) or on a Direct3D 12 device of its own (Ray Reconstruction, see "Direct3D 12" below).
//
// NGX is only reachable through NVIDIA's SDK library (nvsdk_ngx_s.lib), which finds the driver's NGX core, fills in
// internal data and talks to its C++ parameter objects. The Bridge calls native code through libffi with plain
// pointers, ints and floats, so this file wraps the few SDK calls it needs as flat C functions. All of them must be
// called on one thread (the render thread); NGX itself is not thread safe.
//
// Built by build-ngx.sh against the DLSS SDK (https://github.com/NVIDIA/DLSS, NVIDIA RTX SDKs license, which allows
// shipping applications built with it). The SDK is downloaded at build time and is not in this repository.

#include <windows.h>
#include <d3d11.h>
#include <d3d12.h>
#include <dxgi1_4.h>
#include <stdio.h>

#include "nvsdk_ngx.h"
#include "nvsdk_ngx_helpers.h"
#include "nvsdk_ngx_helpers_dlssd_d3d.h"

#define CG_API extern "C" __declspec(dllexport)

// Bumped whenever a function below changes; the Java side refuses a helper with another version.
static const int CG_NGX_ABI = 4;

// NGX log lines wait here until the Java side reads them. A fixed ring keeps this file free of the C++ library.
#define LOG_LINES 256
#define LOG_LINE_BYTES 512
static SRWLOCK g_logLock = SRWLOCK_INIT;
static char g_log[LOG_LINES][LOG_LINE_BYTES];
static int g_logFirst;
static int g_logCount;

static bool g_initialized;
// The API NGX was started on: 11 (g_device) or 12 (g_device12).
static int g_api;
static ID3D11Device* g_device;
static NVSDK_NGX_Parameter* g_params;
static NVSDK_NGX_Handle* g_feature;
// Testing: Ray Reconstruction's own NGX parameters (cg_ngx_create_rr's ownParameters), else null and g_params serves.
static NVSDK_NGX_Parameter* g_rrParams;
static wchar_t g_featureDir[32768];
static const wchar_t* g_featureDirs[1];

static void NVSDK_CONV logCallback(const char* message, NVSDK_NGX_Logging_Level, NVSDK_NGX_Feature) {
    AcquireSRWLockExclusive(&g_logLock);
    if (g_logCount == LOG_LINES) {
        g_logFirst = (g_logFirst + 1) % LOG_LINES;
        g_logCount--;
    }
    char* line = g_log[(g_logFirst + g_logCount) % LOG_LINES];
    int length = 0;
    for (const char* c = message ? message : ""; *c && length < LOG_LINE_BYTES - 1; c++) {
        line[length++] = *c;
    }
    while (length > 0 && (line[length - 1] == '\n' || line[length - 1] == '\r')) {
        length--;
    }
    line[length] = 0;
    g_logCount++;
    ReleaseSRWLockExclusive(&g_logLock);
}

static const char* presetHintFor(int perfQuality) {
    switch (perfQuality) {
        case NVSDK_NGX_PerfQuality_Value_DLAA: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_DLAA;
        case NVSDK_NGX_PerfQuality_Value_MaxQuality: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Quality;
        case NVSDK_NGX_PerfQuality_Value_Balanced: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Balanced;
        case NVSDK_NGX_PerfQuality_Value_MaxPerf: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Performance;
        case NVSDK_NGX_PerfQuality_Value_UltraPerformance: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_UltraPerformance;
        case NVSDK_NGX_PerfQuality_Value_UltraQuality: return NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_UltraQuality;
        default: return nullptr;
    }
}

static const char* rayReconstructionPresetHintFor(int perfQuality) {
    switch (perfQuality) {
        case NVSDK_NGX_PerfQuality_Value_DLAA: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_DLAA;
        case NVSDK_NGX_PerfQuality_Value_MaxQuality: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Quality;
        case NVSDK_NGX_PerfQuality_Value_Balanced: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Balanced;
        case NVSDK_NGX_PerfQuality_Value_MaxPerf: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Performance;
        case NVSDK_NGX_PerfQuality_Value_UltraPerformance: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_UltraPerformance;
        case NVSDK_NGX_PerfQuality_Value_UltraQuality: return NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_UltraQuality;
        default: return nullptr;
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Direct3D 12: a device of its own for Ray Reconstruction, whose Direct3D 11 path crashes on its first evaluate (a
// null read inside nvngx_dlssd.dll with any inputs; the same inputs run on Direct3D 12, results/rr1b). OpenGL shares
// textures and two fences with it (EXT_memory_object_win32, EXT_semaphore_win32), as with the DX12 presenter: OpenGL
// signals "OpenGL done" after copying the inputs, this queue waits for that, runs NGX and signals "DirectX done", which
// OpenGL waits for before it reads the output or writes the inputs again. Between runs every texture is COMMON.

#define ALLOCATORS 3
static ID3D12Device* g_device12;
static ID3D12CommandQueue* g_queue;
static ID3D12CommandAllocator* g_allocators[ALLOCATORS];
static UINT64 g_allocatorDone[ALLOCATORS];
static int g_allocator;
static ID3D12GraphicsCommandList* g_list;
// 0: "OpenGL done" (OpenGL signals, this queue waits), 1: "DirectX done" (this queue signals).
static ID3D12Fence* g_fences[2];
static HANDLE g_fenceHandles[2];
static UINT64 g_dxValue;
static HANDLE g_event;
static char g_error12[512];
static char g_adapterName[256];

static void* fail12(const char* what, HRESULT hr) {
    snprintf(g_error12, sizeof(g_error12), "%s failed (0x%08lX)", what, (unsigned long) hr);
    return nullptr;
}

template <typename T> static void release(T*& object) {
    if (object) {
        object->Release();
        object = nullptr;
    }
}

// Waits (at most timeoutMs) until this queue has signalled value on "DirectX done". False on time out.
static bool waitFor(UINT64 value, DWORD timeoutMs) {
    if (g_fences[1]->GetCompletedValue() >= value) {
        return true;
    }
    if (FAILED(g_fences[1]->SetEventOnCompletion(value, g_event))) {
        return false;
    }
    return WaitForSingleObject(g_event, timeoutMs) == WAIT_OBJECT_0;
}

// Readies the command list for a run that waits on the GPU for OpenGL's glValue first (0: none). After it, end12 must
// follow, also when this fails, so the run's "DirectX done" value is signalled whatever happened.
static bool begin12(UINT64 glValue) {
    if (glValue) {
        g_queue->Wait(g_fences[0], glValue);
    }
    g_allocator = (g_allocator + 1) % ALLOCATORS;
    if (!waitFor(g_allocatorDone[g_allocator], 2000)) {
        snprintf(g_error12, sizeof(g_error12), "an earlier run did not finish within 2 s");
        return false;
    }
    HRESULT hr = g_allocators[g_allocator]->Reset();
    if (SUCCEEDED(hr)) {
        hr = g_list->Reset(g_allocators[g_allocator], nullptr);
    }
    if (FAILED(hr)) {
        fail12("resetting the command list", hr);
        return false;
    }
    return true;
}

// Closes the command list and runs it (unless begin12 failed), then signals "DirectX done". Returns that value.
static UINT64 end12(bool run) {
    if (SUCCEEDED(g_list->Close()) && run) {
        ID3D12CommandList* lists[] = {g_list};
        g_queue->ExecuteCommandLists(1, lists);
    }
    g_queue->Signal(g_fences[1], ++g_dxValue);
    g_allocatorDone[g_allocator] = g_dxValue;
    return g_dxValue;
}

// The inputs between COMMON and what NGX reads them as; the output between COMMON and unordered access.
static void transition(ID3D12Resource* const* inputs, int count, ID3D12Resource* output, bool toNgx) {
    const D3D12_RESOURCE_STATES read = D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE | D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE;
    D3D12_RESOURCE_BARRIER barriers[16] = {};
    int n = 0;
    for (int i = 0; i <= count && n < 16; i++) {
        ID3D12Resource* resource = i < count ? inputs[i] : output;
        if (!resource) {
            continue;
        }
        D3D12_RESOURCE_STATES ngx = i < count ? read : D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
        barriers[n].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barriers[n].Transition.pResource = resource;
        barriers[n].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barriers[n].Transition.StateBefore = toNgx ? D3D12_RESOURCE_STATE_COMMON : ngx;
        barriers[n].Transition.StateAfter = toNgx ? ngx : D3D12_RESOURCE_STATE_COMMON;
        n++;
    }
    g_list->ResourceBarrier((UINT) n, barriers);
}

CG_API void cg_ngx12_close();

// Opens the Direct3D 12 device on the adapter with this LUID (OpenGL's; null or not found: the NVIDIA one with the most
// memory), with its queue and the two fences. Returns the device, or null (cg_ngx12_error says why).
CG_API ID3D12Device* cg_ngx12_open(const LUID* luid) {
    if (g_device12) {
        return g_device12;
    }
    g_error12[0] = 0;
    IDXGIFactory4* factory = nullptr;
    HRESULT hr = CreateDXGIFactory1(__uuidof(IDXGIFactory4), (void**) &factory);
    if (FAILED(hr)) {
        return (ID3D12Device*) fail12("CreateDXGIFactory1", hr);
    }
    IDXGIAdapter1* adapter = nullptr;
    if (!luid || FAILED(factory->EnumAdapterByLuid(*luid, __uuidof(IDXGIAdapter1), (void**) &adapter))) {
        adapter = nullptr;
        SIZE_T bestMemory = 0;
        IDXGIAdapter1* candidate = nullptr;
        for (UINT i = 0; factory->EnumAdapters1(i, &candidate) != DXGI_ERROR_NOT_FOUND; i++) {
            DXGI_ADAPTER_DESC1 desc;
            candidate->GetDesc1(&desc);
            if (desc.VendorId == 0x10DE && !(desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) && desc.DedicatedVideoMemory > bestMemory) {
                release(adapter);
                adapter = candidate;
                bestMemory = desc.DedicatedVideoMemory;
            } else {
                candidate->Release();
            }
        }
    }
    factory->Release();
    if (!adapter) {
        snprintf(g_error12, sizeof(g_error12), "no NVIDIA adapter found for DirectX 12");
        return nullptr;
    }
    DXGI_ADAPTER_DESC1 desc;
    adapter->GetDesc1(&desc);
    WideCharToMultiByte(CP_UTF8, 0, desc.Description, -1, g_adapterName, sizeof(g_adapterName), nullptr, nullptr);
    hr = D3D12CreateDevice(adapter, D3D_FEATURE_LEVEL_11_0, __uuidof(ID3D12Device), (void**) &g_device12);
    adapter->Release();
    if (FAILED(hr)) {
        g_device12 = nullptr;
        return (ID3D12Device*) fail12("D3D12CreateDevice", hr);
    }
    D3D12_COMMAND_QUEUE_DESC queue = {};
    queue.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    hr = g_device12->CreateCommandQueue(&queue, __uuidof(ID3D12CommandQueue), (void**) &g_queue);
    for (int i = 0; i < ALLOCATORS && SUCCEEDED(hr); i++) {
        hr = g_device12->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, __uuidof(ID3D12CommandAllocator),
                                                (void**) &g_allocators[i]);
    }
    if (SUCCEEDED(hr)) {
        hr = g_device12->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, g_allocators[0], nullptr,
                                           __uuidof(ID3D12GraphicsCommandList), (void**) &g_list);
    }
    if (SUCCEEDED(hr)) {
        hr = g_list->Close();
    }
    for (int i = 0; i < 2 && SUCCEEDED(hr); i++) {
        hr = g_device12->CreateFence(0, D3D12_FENCE_FLAG_SHARED, __uuidof(ID3D12Fence), (void**) &g_fences[i]);
        if (SUCCEEDED(hr)) {
            hr = g_device12->CreateSharedHandle(g_fences[i], nullptr, GENERIC_ALL, nullptr, &g_fenceHandles[i]);
        }
    }
    g_event = SUCCEEDED(hr) ? CreateEventW(nullptr, FALSE, FALSE, nullptr) : nullptr;
    if (FAILED(hr) || !g_event) {
        fail12("making the queue and fences", FAILED(hr) ? hr : HRESULT_FROM_WIN32(GetLastError()));
        cg_ngx12_close();
        return nullptr;
    }
    return g_device12;
}

// Why the last Direct3D 12 call failed.
CG_API const char* cg_ngx12_error() {
    return g_error12;
}

CG_API const char* cg_ngx12_adapter_name() {
    return g_adapterName;
}

// The NT handle of fence 0 ("OpenGL done") or 1 ("DirectX done") for GL_HANDLE_TYPE_D3D12_FENCE_EXT; owned here.
CG_API HANDLE cg_ngx12_fence_handle(int which) {
    return which == 0 || which == 1 ? g_fenceHandles[which] : nullptr;
}

// Testing (rr-probe stands in for OpenGL): the fence itself.
CG_API ID3D12Fence* cg_ngx12_fence(int which) {
    return which == 0 || which == 1 ? g_fences[which] : nullptr;
}

// A texture OpenGL imports (GL_HANDLE_TYPE_D3D12_RESOURCE_EXT): a committed, shared resource in the COMMON state,
// render target capable as the presenter's, the output also unordered access. handle gets its NT handle (closed by
// cg_ngx12_release_texture), size its allocation size. Returns the resource, or null (cg_ngx12_error says why).
CG_API ID3D12Resource* cg_ngx12_create_texture(int width, int height, int format, int output, HANDLE* handle, UINT64* size) {
    if (!g_device12) {
        snprintf(g_error12, sizeof(g_error12), "no DirectX 12 device");
        return nullptr;
    }
    D3D12_HEAP_PROPERTIES heap = {};
    heap.Type = D3D12_HEAP_TYPE_DEFAULT;
    D3D12_RESOURCE_DESC desc = {};
    desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
    desc.Width = (UINT64) width;
    desc.Height = (UINT) height;
    desc.DepthOrArraySize = 1;
    desc.MipLevels = 1;
    desc.Format = (DXGI_FORMAT) format;
    desc.SampleDesc.Count = 1;
    desc.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
    desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET | (output ? D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS : D3D12_RESOURCE_FLAG_NONE);
    ID3D12Resource* texture = nullptr;
    HRESULT hr = g_device12->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_SHARED, &desc, D3D12_RESOURCE_STATE_COMMON,
                                                     nullptr, __uuidof(ID3D12Resource), (void**) &texture);
    if (FAILED(hr)) {
        return (ID3D12Resource*) fail12("CreateCommittedResource (shared texture)", hr);
    }
    hr = g_device12->CreateSharedHandle(texture, nullptr, GENERIC_ALL, nullptr, handle);
    if (FAILED(hr)) {
        texture->Release();
        return (ID3D12Resource*) fail12("CreateSharedHandle (texture)", hr);
    }
    *size = g_device12->GetResourceAllocationInfo(0, 1, &desc).SizeInBytes;
    return texture;
}

CG_API void cg_ngx12_release_texture(ID3D12Resource* texture, HANDLE handle) {
    if (handle) {
        CloseHandle(handle);
    }
    if (texture) {
        texture->Release();
    }
}

// Waits (at most timeoutMs) until the queue has finished everything it was given. 1 if it did.
CG_API int cg_ngx12_wait_idle(int timeoutMs) {
    if (!g_queue) {
        return 1;
    }
    g_queue->Signal(g_fences[1], ++g_dxValue);
    return waitFor(g_dxValue, (DWORD) timeoutMs) ? 1 : 0;
}

// Closes the device (after cg_ngx_shutdown and every texture's release).
CG_API void cg_ngx12_close() {
    if (g_queue && g_fences[1] && g_event) {
        cg_ngx12_wait_idle(2000);
    }
    release(g_list);
    for (int i = 0; i < ALLOCATORS; i++) {
        release(g_allocators[i]);
        g_allocatorDone[i] = 0;
    }
    release(g_queue);
    for (int i = 0; i < 2; i++) {
        release(g_fences[i]);
        if (g_fenceHandles[i]) {
            CloseHandle(g_fenceHandles[i]);
            g_fenceHandles[i] = nullptr;
        }
    }
    if (g_event) {
        CloseHandle(g_event);
        g_event = nullptr;
    }
    g_dxValue = 0;
    release(g_device12);
}

// ---------------------------------------------------------------------------------------------------------------
// NGX, on whichever device it was started on.

static NVSDK_NGX_Result releaseFeature(NVSDK_NGX_Handle* feature) {
    return g_api == 12 ? NVSDK_NGX_D3D12_ReleaseFeature(feature) : NVSDK_NGX_D3D11_ReleaseFeature(feature);
}

static NVSDK_NGX_Result allocateParameters(NVSDK_NGX_Parameter** params) {
    return g_api == 12 ? NVSDK_NGX_D3D12_AllocateParameters(params) : NVSDK_NGX_D3D11_AllocateParameters(params);
}

static NVSDK_NGX_Result destroyParameters(NVSDK_NGX_Parameter* params) {
    return g_api == 12 ? NVSDK_NGX_D3D12_DestroyParameters(params) : NVSDK_NGX_D3D11_DestroyParameters(params);
}

static void commonInfo(NVSDK_NGX_FeatureCommonInfo& info, const wchar_t* featureDir, int logLevel) {
    g_featureDir[0] = 0;
    if (featureDir && wcslen(featureDir) < sizeof(g_featureDir) / sizeof(g_featureDir[0])) {
        wcscpy_s(g_featureDir, sizeof(g_featureDir) / sizeof(g_featureDir[0]), featureDir);
    }
    g_featureDirs[0] = g_featureDir;
    info = {};
    info.PathListInfo.Path = g_featureDir[0] ? g_featureDirs : nullptr;
    info.PathListInfo.Length = g_featureDir[0] ? 1 : 0;
    info.LoggingInfo.LoggingCallback = logCallback;
    info.LoggingInfo.MinimumLoggingLevel = (NVSDK_NGX_Logging_Level) logLevel;
    info.LoggingInfo.DisableOtherLoggingSinks = false;
}

CG_API int cg_ngx_abi() {
    return CG_NGX_ABI;
}

// Starts NGX on the given Direct3D 11 device. appDataPath is a writable folder (NGX keeps its logs there); featureDir
// is the folder holding nvngx_dlss.dll, searched before the driver's own copy. logLevel: 0 off, 1 on, 2 verbose.
CG_API int cg_ngx_init(const char* projectId, const char* engineVersion, const wchar_t* appDataPath,
                       const wchar_t* featureDir, ID3D11Device* device, int logLevel) {
    if (g_initialized) {
        return NVSDK_NGX_Result_FAIL_FeatureAlreadyExists;
    }
    NVSDK_NGX_FeatureCommonInfo info;
    commonInfo(info, featureDir, logLevel);
    NVSDK_NGX_Result result = NVSDK_NGX_D3D11_Init_with_ProjectID(projectId, NVSDK_NGX_ENGINE_TYPE_CUSTOM, engineVersion,
                                                                  appDataPath, device, &info, NVSDK_NGX_Version_API);
    if (NVSDK_NGX_FAILED(result)) {
        return result;
    }
    g_initialized = true;
    g_api = 11;
    g_device = device;

    result = NVSDK_NGX_D3D11_GetCapabilityParameters(&g_params);
    if (NVSDK_NGX_FAILED(result)) {
        NVSDK_NGX_D3D11_Shutdown1(device);
        g_initialized = false;
        g_api = 0;
        g_device = nullptr;
        g_params = nullptr;
    }
    return result;
}

// cg_ngx_init on the Direct3D 12 device cg_ngx12_open made.
CG_API int cg_ngx_init12(const char* projectId, const char* engineVersion, const wchar_t* appDataPath,
                         const wchar_t* featureDir, int logLevel) {
    if (g_initialized) {
        return NVSDK_NGX_Result_FAIL_FeatureAlreadyExists;
    }
    if (!g_device12) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    NVSDK_NGX_FeatureCommonInfo info;
    commonInfo(info, featureDir, logLevel);
    NVSDK_NGX_Result result = NVSDK_NGX_D3D12_Init_with_ProjectID(projectId, NVSDK_NGX_ENGINE_TYPE_CUSTOM, engineVersion,
                                                                  appDataPath, g_device12, &info, NVSDK_NGX_Version_API);
    if (NVSDK_NGX_FAILED(result)) {
        return result;
    }
    g_initialized = true;
    g_api = 12;

    result = NVSDK_NGX_D3D12_GetCapabilityParameters(&g_params);
    if (NVSDK_NGX_FAILED(result)) {
        NVSDK_NGX_D3D12_Shutdown1(g_device12);
        g_initialized = false;
        g_api = 0;
        g_params = nullptr;
    }
    return result;
}

// Whether DLSS super sampling can run here. out[0] available (0/1), out[1] needs a newer driver (0/1),
// out[2], out[3] the minimum driver version, out[4] the feature's own init result.
CG_API int cg_ngx_query(int* out) {
    if (!g_params) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    int available = 0;
    unsigned int needsDriver = 0;
    unsigned int major = 0;
    unsigned int minor = 0;
    int initResult = 0;
    NVSDK_NGX_Parameter_GetI(g_params, NVSDK_NGX_Parameter_SuperSampling_Available, &available);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSampling_NeedsUpdatedDriver, &needsDriver);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMajor, &major);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMinor, &minor);
    NVSDK_NGX_Parameter_GetI(g_params, NVSDK_NGX_Parameter_SuperSampling_FeatureInitResult, &initResult);
    out[0] = available;
    out[1] = (int) needsDriver;
    out[2] = (int) major;
    out[3] = (int) minor;
    out[4] = initResult;
    return NVSDK_NGX_Result_Success;
}

// Whether DLSS Ray Reconstruction (nvngx_dlssd.dll) can run here, in the same layout as cg_ngx_query.
CG_API int cg_ngx_query_rr(int* out) {
    if (!g_params) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    int available = 0;
    unsigned int needsDriver = 0;
    unsigned int major = 0;
    unsigned int minor = 0;
    int initResult = 0;
    NVSDK_NGX_Parameter_GetI(g_params, NVSDK_NGX_Parameter_SuperSamplingDenoising_Available, &available);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSamplingDenoising_NeedsUpdatedDriver, &needsDriver);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSamplingDenoising_MinDriverVersionMajor, &major);
    NVSDK_NGX_Parameter_GetUI(g_params, NVSDK_NGX_Parameter_SuperSamplingDenoising_MinDriverVersionMinor, &minor);
    NVSDK_NGX_Parameter_GetI(g_params, NVSDK_NGX_Parameter_SuperSamplingDenoising_FeatureInitResult, &initResult);
    out[0] = available;
    out[1] = (int) needsDriver;
    out[2] = (int) major;
    out[3] = (int) minor;
    out[4] = initResult;
    return NVSDK_NGX_Result_Success;
}

// The render sizes DLSS allows for an output size and quality mode. out: optimal width, height, then the
// dynamic maximum width, height, then the minimum width, height.
CG_API int cg_ngx_optimal(int outWidth, int outHeight, int perfQuality, int* out) {
    if (!g_params) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    unsigned int values[6] = {};
    float sharpness = 0.0f;
    NVSDK_NGX_Result result = NGX_DLSS_GET_OPTIMAL_SETTINGS(g_params, (unsigned int) outWidth, (unsigned int) outHeight,
                                                            (NVSDK_NGX_PerfQuality_Value) perfQuality,
                                                            &values[0], &values[1], &values[2], &values[3],
                                                            &values[4], &values[5], &sharpness);
    for (int i = 0; i < 6; i++) {
        out[i] = (int) values[i];
    }
    return result;
}

CG_API int cg_ngx_release() {
    NVSDK_NGX_Result result = NVSDK_NGX_Result_Success;
    if (g_api == 12 && (g_feature || g_rrParams)) {
        cg_ngx12_wait_idle(2000); // the GPU is done with the feature first
    }
    if (g_feature) {
        result = releaseFeature(g_feature);
        g_feature = nullptr;
    }
    if (g_rrParams) {
        destroyParameters(g_rrParams);
        g_rrParams = nullptr;
    }
    return result;
}

// On Direct3D 12 a feature is made on the command list, which runs before it is used.
static NVSDK_NGX_Result created12(NVSDK_NGX_Result result, bool began) {
    end12(began);
    if (!began) {
        return NVSDK_NGX_Result_Fail;
    }
    if (!waitFor(g_dxValue, 5000)) {
        snprintf(g_error12, sizeof(g_error12), "making the feature did not finish within 5 s");
    }
    return result;
}

// (Re)creates the DLSS feature. preset is a render preset (0 = DLSS's default for the mode, 11 = K, ...);
// flags are NVSDK_NGX_DLSS_Feature_Flags. context: Direct3D 11's (ignored on Direct3D 12).
CG_API int cg_ngx_create(ID3D11DeviceContext* context, int renderWidth, int renderHeight, int outWidth, int outHeight,
                         int perfQuality, int flags, int preset) {
    if (!g_params) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    cg_ngx_release();
    for (int mode = NVSDK_NGX_PerfQuality_Value_MaxPerf; mode <= NVSDK_NGX_PerfQuality_Value_DLAA; mode++) {
        NVSDK_NGX_Parameter_SetUI(g_params, presetHintFor(mode), mode == perfQuality ? (unsigned int) preset : 0u);
    }
    NVSDK_NGX_DLSS_Create_Params create = {};
    create.Feature.InWidth = (unsigned int) renderWidth;
    create.Feature.InHeight = (unsigned int) renderHeight;
    create.Feature.InTargetWidth = (unsigned int) outWidth;
    create.Feature.InTargetHeight = (unsigned int) outHeight;
    create.Feature.InPerfQualityValue = (NVSDK_NGX_PerfQuality_Value) perfQuality;
    create.InFeatureCreateFlags = flags;
    create.InEnableOutputSubrects = false;
    if (g_api == 12) {
        bool began = begin12(0);
        return created12(began ? NGX_D3D12_CREATE_DLSS_EXT(g_list, 1, 1, &g_feature, g_params, &create)
                               : NVSDK_NGX_Result_Fail, began);
    }
    return NGX_D3D11_CREATE_DLSS_EXT(context, &g_feature, g_params, &create);
}

// (Re)creates the feature as DLSS Ray Reconstruction (one pass that denoises, anti-aliases and upscales) in place of
// DLSS. preset is a Ray Reconstruction render preset (0 = its default, 4 = D, 5 = E, 6 = F), set for every quality
// mode so a newer model NVIDIA sends never swaps it. Roughness comes in the normals' alpha; depthType 1 is a
// hardware depth buffer (with NVSDK_NGX_DLSS_Feature_Flags_DepthInverted in flags when it is reversed), 0 view depth.
// ownParameters (testing): NGX parameters of its own for this feature instead of the capability parameters DLSS uses.
CG_API int cg_ngx_create_rr(ID3D11DeviceContext* context, int renderWidth, int renderHeight, int outWidth, int outHeight,
                            int perfQuality, int flags, int preset, int depthType, int ownParameters) {
    if (!g_params) {
        return NVSDK_NGX_Result_FAIL_NotInitialized;
    }
    cg_ngx_release();
    if (ownParameters) {
        NVSDK_NGX_Result allocated = allocateParameters(&g_rrParams);
        if (NVSDK_NGX_FAILED(allocated)) {
            g_rrParams = nullptr;
            return allocated;
        }
    }
    NVSDK_NGX_Parameter* params = g_rrParams ? g_rrParams : g_params;
    for (int mode = NVSDK_NGX_PerfQuality_Value_MaxPerf; mode <= NVSDK_NGX_PerfQuality_Value_DLAA; mode++) {
        NVSDK_NGX_Parameter_SetUI(params, rayReconstructionPresetHintFor(mode), (unsigned int) preset);
    }
    NVSDK_NGX_DLSSD_Create_Params create = {};
    create.InDenoiseMode = NVSDK_NGX_DLSS_Denoise_Mode_DLUnified;
    create.InRoughnessMode = NVSDK_NGX_DLSS_Roughness_Mode_Packed;
    create.InUseHWDepth = depthType ? NVSDK_NGX_DLSS_Depth_Type_HW : NVSDK_NGX_DLSS_Depth_Type_Linear;
    create.InWidth = (unsigned int) renderWidth;
    create.InHeight = (unsigned int) renderHeight;
    create.InTargetWidth = (unsigned int) outWidth;
    create.InTargetHeight = (unsigned int) outHeight;
    create.InPerfQualityValue = (NVSDK_NGX_PerfQuality_Value) perfQuality;
    create.InFeatureCreateFlags = flags;
    create.InEnableOutputSubrects = false;
    if (g_api == 12) {
        bool began = begin12(0);
        return created12(began ? NGX_D3D12_CREATE_DLSSD_EXT(g_list, 1, 1, &g_feature, params, &create)
                               : NVSDK_NGX_Result_Fail, began);
    }
    return NGX_D3D11_CREATE_DLSSD_EXT(context, &g_feature, params, &create);
}

// Runs Ray Reconstruction once. resources: colour, output, depth, motion vectors, diffuse albedo, specular albedo,
// normals with roughness in alpha, specular hit distance (may be null). floats: jitter x, y (render pixels), motion
// vector scale x, y, the frame time in ms, then the world-to-view and view-to-clip matrices, 16 floats each, row
// major for a row vector on the left (which is OpenGL's column-major layout of a matrix for a column vector); they go
// with the hit distance (NVIDIA's guide), so without one they are left out too.
CG_API int cg_ngx_evaluate_rr(ID3D11DeviceContext* context, ID3D11Resource** resources, const float* floats, int reset,
                              int renderWidth, int renderHeight) {
    if (!g_feature) {
        return NVSDK_NGX_Result_FAIL_FeatureNotFound;
    }
    static float worldToView[16];
    static float viewToClip[16];
    for (int i = 0; i < 16; i++) {
        worldToView[i] = floats[5 + i];
        viewToClip[i] = floats[21 + i];
    }
    NVSDK_NGX_D3D11_DLSSD_Eval_Params eval = {};
    eval.pInColor = resources[0];
    eval.pInOutput = resources[1];
    eval.pInDepth = resources[2];
    eval.pInMotionVectors = resources[3];
    eval.pInDiffuseAlbedo = resources[4];
    eval.pInSpecularAlbedo = resources[5];
    eval.pInNormals = resources[6];
    eval.pInSpecularHitDistance = resources[7];
    eval.InJitterOffsetX = floats[0];
    eval.InJitterOffsetY = floats[1];
    eval.InMVScaleX = floats[2];
    eval.InMVScaleY = floats[3];
    eval.InFrameTimeDeltaInMsec = floats[4];
    eval.pInWorldToViewMatrix = resources[7] ? worldToView : nullptr;
    eval.pInViewToClipMatrix = resources[7] ? viewToClip : nullptr;
    eval.InReset = reset;
    eval.InRenderSubrectDimensions.Width = (unsigned int) renderWidth;
    eval.InRenderSubrectDimensions.Height = (unsigned int) renderHeight;
    return NGX_D3D11_EVALUATE_DLSSD_EXT(context, g_feature, g_rrParams ? g_rrParams : g_params, &eval);
}

// Runs DLSS once. Jitter is in render pixels; motion vectors are multiplied by mvScale to get render pixels.
// exposure may be null when the feature was created with auto exposure.
CG_API int cg_ngx_evaluate(ID3D11DeviceContext* context, ID3D11Resource* color, ID3D11Resource* output,
                           ID3D11Resource* depth, ID3D11Resource* motion, ID3D11Resource* exposure,
                           float jitterX, float jitterY, float mvScaleX, float mvScaleY, int reset,
                           int renderWidth, int renderHeight, float frameTimeMs, float preExposure) {
    if (!g_feature) {
        return NVSDK_NGX_Result_FAIL_FeatureNotFound;
    }
    NVSDK_NGX_D3D11_DLSS_Eval_Params eval = {};
    eval.Feature.pInColor = color;
    eval.Feature.pInOutput = output;
    eval.pInDepth = depth;
    eval.pInMotionVectors = motion;
    eval.pInExposureTexture = exposure;
    eval.InJitterOffsetX = jitterX;
    eval.InJitterOffsetY = jitterY;
    eval.InMVScaleX = mvScaleX;
    eval.InMVScaleY = mvScaleY;
    eval.InReset = reset;
    eval.InRenderSubrectDimensions.Width = (unsigned int) renderWidth;
    eval.InRenderSubrectDimensions.Height = (unsigned int) renderHeight;
    eval.InFrameTimeDeltaInMsec = frameTimeMs;
    eval.InPreExposure = preExposure;
    eval.InExposureScale = 1.0f;
    return NGX_D3D11_EVALUATE_DLSS_EXT(context, g_feature, g_params, &eval);
}

// cg_ngx_evaluate_rr on Direct3D 12: the same resources (ID3D12Resource*) and floats. The queue waits for OpenGL's
// glValue on "OpenGL done" first; dxValue gets the "DirectX done" value OpenGL waits for (0 if nothing was queued).
CG_API int cg_ngx12_evaluate_rr(ID3D12Resource** resources, const float* floats, int reset, int renderWidth,
                                int renderHeight, UINT64 glValue, UINT64* dxValue) {
    *dxValue = 0;
    if (!g_feature || g_api != 12) {
        return NVSDK_NGX_Result_FAIL_FeatureNotFound;
    }
    static float worldToView[16];
    static float viewToClip[16];
    for (int i = 0; i < 16; i++) {
        worldToView[i] = floats[5 + i];
        viewToClip[i] = floats[21 + i];
    }
    ID3D12Resource* inputs[] = {resources[0], resources[2], resources[3], resources[4], resources[5], resources[6], resources[7]};
    if (!begin12(glValue)) {
        *dxValue = end12(false);
        return NVSDK_NGX_Result_Fail;
    }
    transition(inputs, 7, resources[1], true);
    NVSDK_NGX_D3D12_DLSSD_Eval_Params eval = {};
    eval.pInColor = resources[0];
    eval.pInOutput = resources[1];
    eval.pInDepth = resources[2];
    eval.pInMotionVectors = resources[3];
    eval.pInDiffuseAlbedo = resources[4];
    eval.pInSpecularAlbedo = resources[5];
    eval.pInNormals = resources[6];
    eval.pInSpecularHitDistance = resources[7];
    eval.InJitterOffsetX = floats[0];
    eval.InJitterOffsetY = floats[1];
    eval.InMVScaleX = floats[2];
    eval.InMVScaleY = floats[3];
    eval.InFrameTimeDeltaInMsec = floats[4];
    eval.pInWorldToViewMatrix = resources[7] ? worldToView : nullptr;
    eval.pInViewToClipMatrix = resources[7] ? viewToClip : nullptr;
    eval.InReset = reset;
    eval.InRenderSubrectDimensions.Width = (unsigned int) renderWidth;
    eval.InRenderSubrectDimensions.Height = (unsigned int) renderHeight;
    NVSDK_NGX_Result result = NGX_D3D12_EVALUATE_DLSSD_EXT(g_list, g_feature, g_rrParams ? g_rrParams : g_params, &eval);
    transition(inputs, 7, resources[1], false);
    *dxValue = end12(true);
    return result;
}

// cg_ngx_evaluate on Direct3D 12 (DLSS where Ray Reconstruction could not start). resources: colour, output, depth,
// motion vectors, exposure (may be null). floats: jitter x, y, motion vector scale x, y, the frame time in ms, the
// pre-exposure. glValue and dxValue as for cg_ngx12_evaluate_rr.
CG_API int cg_ngx12_evaluate(ID3D12Resource** resources, const float* floats, int reset, int renderWidth, int renderHeight,
                             UINT64 glValue, UINT64* dxValue) {
    *dxValue = 0;
    if (!g_feature || g_api != 12) {
        return NVSDK_NGX_Result_FAIL_FeatureNotFound;
    }
    ID3D12Resource* inputs[] = {resources[0], resources[2], resources[3], resources[4]};
    if (!begin12(glValue)) {
        *dxValue = end12(false);
        return NVSDK_NGX_Result_Fail;
    }
    transition(inputs, 4, resources[1], true);
    NVSDK_NGX_D3D12_DLSS_Eval_Params eval = {};
    eval.Feature.pInColor = resources[0];
    eval.Feature.pInOutput = resources[1];
    eval.pInDepth = resources[2];
    eval.pInMotionVectors = resources[3];
    eval.pInExposureTexture = resources[4];
    eval.InJitterOffsetX = floats[0];
    eval.InJitterOffsetY = floats[1];
    eval.InMVScaleX = floats[2];
    eval.InMVScaleY = floats[3];
    eval.InReset = reset;
    eval.InRenderSubrectDimensions.Width = (unsigned int) renderWidth;
    eval.InRenderSubrectDimensions.Height = (unsigned int) renderHeight;
    eval.InFrameTimeDeltaInMsec = floats[4];
    eval.InPreExposure = floats[5];
    eval.InExposureScale = 1.0f;
    NVSDK_NGX_Result result = NGX_D3D12_EVALUATE_DLSS_EXT(g_list, g_feature, g_params, &eval);
    transition(inputs, 4, resources[1], false);
    *dxValue = end12(true);
    return result;
}

CG_API int cg_ngx_shutdown() {
    if (!g_initialized) {
        return NVSDK_NGX_Result_Success;
    }
    cg_ngx_release();
    if (g_params) {
        destroyParameters(g_params);
        g_params = nullptr;
    }
    NVSDK_NGX_Result result = g_api == 12 ? NVSDK_NGX_D3D12_Shutdown1(g_device12) : NVSDK_NGX_D3D11_Shutdown1(g_device);
    g_initialized = false;
    g_api = 0;
    g_device = nullptr;
    return result;
}

// Takes the oldest NGX log line into buf (UTF-8, NUL-terminated, cut to fit). Returns its length, or -1 if none.
CG_API int cg_ngx_log_next(char* buf, int capacity) {
    if (capacity <= 0) {
        return -1;
    }
    AcquireSRWLockExclusive(&g_logLock);
    int length = -1;
    if (g_logCount > 0) {
        const char* line = g_log[g_logFirst];
        length = 0;
        while (line[length] && length < capacity - 1) {
            buf[length] = line[length];
            length++;
        }
        buf[length] = 0;
        g_logFirst = (g_logFirst + 1) % LOG_LINES;
        g_logCount--;
    }
    ReleaseSRWLockExclusive(&g_logLock);
    return length;
}

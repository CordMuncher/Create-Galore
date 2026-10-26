// cg_ffx.cpp: AMD FidelityFX Super Resolution frame generation (FSR 3.1.5 frame generation and its swap chain, from the
// FidelityFX SDK 2.0.0), the presenter's second frame generation backend (cg_framegen.h). It runs on any DirectX 12
// card AMD's requirements allow, beside DLSS or FSR upscaling, from the same inputs as DLSS Frame Generation.
//
// As AMD's guides (docs/techniques/frame-interpolation.md, frame-interpolation-swap-chain.md) and its FSR sample
// (Samples/Upscalers/FidelityFX_FSR/dx12/fsrapirendermodule.cpp) lay it out:
//  - amd_fidelityfx_framegeneration_dx12.dll and amd_fidelityfx_loader_dx12.dll are loaded by their full paths once
//    their AMD signatures check out, frame generation first, so the loader (which loads it by name) finds it loaded;
//    neither is unloaded;
//  - AMD's swap chain (ForHwnd, on the presenter's queue) stands in for DXGI's: the presenter draws into its back
//    buffers and presents through it, and waits on its frame latency waitable object as without frame generation;
//  - the frame generation context (depth reversed and infinite, the back buffer's size) is made with the swap chain
//    and again for another size; each frame it is configured with the same frame ID as that frame's prepare pass,
//    which goes into the presenter's command list with the frame's depth, motion and camera; during Present the swap
//    chain calls back to record the generation on the presenting queue;
//  - the HUD stays out of generation (AMD's "UI texture" way): the presenter draws the hudless frame into the back
//    buffer and the HUD, premultiplied, into a texture registered every frame, which the swap chain copies at Present
//    and draws over the rendered and the generated frame alike;
//  - FSR has no quality modes or presets: pacing and everything else stay at AMD's defaults, without async compute;
//  - frame generation follows the game's choice frame by frame (AMD's swap chain waits for its presents when it turns
//    on or off); a reset (a cut, or the first frame) makes the generated frame a copy of the rendered one.

#include "cg_framegen.h"

#include <float.h>
#include <wintrust.h>
#include <softpub.h>

#include <ffx_api.h>
#include <dx12/ffx_api_dx12.h>
#include <ffx_framegeneration.h>
#include <dx12/ffx_api_framegeneration_dx12.h>

namespace {

const wchar_t FRAMEGENERATION_DLL[] = L"amd_fidelityfx_framegeneration_dx12.dll";
const wchar_t LOADER_DLL[] = L"amd_fidelityfx_loader_dx12.dll";
/** The signer of AMD's DLLs (their certificate's common name), under a certificate authority Windows trusts. */
const wchar_t SIGNER[] = L"Advanced Micro Devices";

// FgState::status bits
enum FfxStatus {
    STATUS_CONTEXT = 1,           // the frame generation context could not be made for the window's size
    STATUS_CALL = 2,              // a FidelityFX call failed on the last frame
};

const char* returnName(ffxReturnCode_t code) {
    switch (code) {
    case FFX_API_RETURN_OK: return "OK";
    case FFX_API_RETURN_ERROR: return "ERROR";
    case FFX_API_RETURN_ERROR_UNKNOWN_DESCTYPE: return "ERROR_UNKNOWN_DESCTYPE";
    case FFX_API_RETURN_ERROR_RUNTIME_ERROR: return "ERROR_RUNTIME_ERROR";
    case FFX_API_RETURN_NO_PROVIDER: return "NO_PROVIDER";
    case FFX_API_RETURN_ERROR_MEMORY: return "ERROR_MEMORY";
    case FFX_API_RETURN_ERROR_PARAMETER: return "ERROR_PARAMETER";
    case FFX_API_RETURN_PROVIDER_NO_SUPPORT_NEW_DESCTYPE: return "PROVIDER_NO_SUPPORT_NEW_DESCTYPE";
    default: return "unknown result";
    }
}

/** {@code text} as UTF-8 into {@code out}, cut to fit. */
void utf8(const wchar_t* text, char* out, int size) {
    int length = lstrlenW(text);
    int written = 0;
    // Cut by characters until it fits (a message is a line or two).
    for (int cut = length; cut > 0 && written == 0; cut = cut * 3 / 4) {
        written = WideCharToMultiByte(CP_UTF8, 0, text, cut, out, size - 1, nullptr, nullptr);
    }
    out[written > 0 ? written : 0] = 0;
}

/** AMD's last error message, for failure messages (its message callback runs on any thread). */
SRWLOCK g_lastErrorLock = SRWLOCK_INIT;
char g_lastError[300];

void onMessage(uint32_t type, const wchar_t* message) {
    char line[300];
    utf8(message, line, sizeof line);
    if (type == FFX_API_MESSAGE_TYPE_ERROR) {
        AcquireSRWLockExclusive(&g_lastErrorLock);
        lstrcpynA(g_lastError, line, sizeof g_lastError);
        ReleaseSRWLockExclusive(&g_lastErrorLock);
    }
    fgLog(type == FFX_API_MESSAGE_TYPE_ERROR ? 'E' : 'W', line);
}

/** " (FidelityFX: <its last error>)", or "" without one. */
const char* lastErrorSuffix() {
    static char suffix[sizeof g_lastError + 16];
    AcquireSRWLockExclusive(&g_lastErrorLock);
    if (g_lastError[0]) {
        wsprintfA(suffix, " (FidelityFX: %s)", g_lastError);
    } else {
        suffix[0] = 0;
    }
    ReleaseSRWLockExclusive(&g_lastErrorLock);
    return suffix;
}

struct Api {
    PfnFfxCreateContext createContext;
    PfnFfxDestroyContext destroyContext;
    PfnFfxConfigure configure;
    PfnFfxQuery query;
    PfnFfxDispatch dispatch;
};

template <typename T> bool proc(HMODULE module, const char* name, T& function) {
    function = (T) GetProcAddress(module, name);
    return function != nullptr || fgFail("amd_fidelityfx_loader_dx12.dll has no %s", name);
}

typedef LONG(WINAPI* WinVerifyTrustFn)(HWND, GUID*, LPVOID);
typedef CRYPT_PROVIDER_DATA*(WINAPI* ProvDataFn)(HANDLE);
typedef CRYPT_PROVIDER_SGNR*(WINAPI* ProvSignerFn)(CRYPT_PROVIDER_DATA*, DWORD, BOOL, DWORD);
typedef CRYPT_PROVIDER_CERT*(WINAPI* ProvCertFn)(CRYPT_PROVIDER_SGNR*, DWORD);
typedef DWORD(WINAPI* CertNameFn)(PCCERT_CONTEXT, DWORD, DWORD, void*, LPWSTR, DWORD);

/**
 * Whether {@code path} carries a valid Authenticode signature that chains to a root Windows trusts, made by AMD's
 * certificate. False with the reason in cg_present_error. Nothing is fetched from the network.
 */
bool signedByAmd(const wchar_t* path, const char* name) {
    HMODULE wintrust = LoadLibraryExW(L"wintrust.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
    HMODULE crypt32 = LoadLibraryExW(L"crypt32.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
    WinVerifyTrustFn verify = wintrust ? (WinVerifyTrustFn) GetProcAddress(wintrust, "WinVerifyTrust") : nullptr;
    ProvDataFn provData = wintrust ? (ProvDataFn) GetProcAddress(wintrust, "WTHelperProvDataFromStateData") : nullptr;
    ProvSignerFn provSigner = wintrust ? (ProvSignerFn) GetProcAddress(wintrust, "WTHelperGetProvSignerFromChain") : nullptr;
    ProvCertFn provCert = wintrust ? (ProvCertFn) GetProcAddress(wintrust, "WTHelperGetProvCertFromChain") : nullptr;
    CertNameFn certName = crypt32 ? (CertNameFn) GetProcAddress(crypt32, "CertGetNameStringW") : nullptr;
    if (!verify || !provData || !provSigner || !provCert || !certName) {
        return fgFail("Windows' signature check (wintrust.dll, crypt32.dll) could not be loaded; %s was not loaded", name);
    }
    WINTRUST_FILE_INFO file = {};
    file.cbStruct = sizeof file;
    file.pcwszFilePath = path;
    GUID action = WINTRUST_ACTION_GENERIC_VERIFY_V2;
    WINTRUST_DATA data = {};
    data.cbStruct = sizeof data;
    data.dwUIChoice = WTD_UI_NONE;
    data.fdwRevocationChecks = WTD_REVOKE_NONE;
    data.dwUnionChoice = WTD_CHOICE_FILE;
    data.pFile = &file;
    data.dwStateAction = WTD_STATEACTION_VERIFY;
    data.dwProvFlags = WTD_CACHE_ONLY_URL_RETRIEVAL;
    LONG status = verify(nullptr, &action, &data);
    wchar_t signer[128] = {};
    if (status == ERROR_SUCCESS) {
        CRYPT_PROVIDER_DATA* provider = provData(data.hWVTStateData);
        CRYPT_PROVIDER_SGNR* signature = provider ? provSigner(provider, 0, FALSE, 0) : nullptr;
        CRYPT_PROVIDER_CERT* certificate = signature ? provCert(signature, 0) : nullptr;
        if (certificate && certificate->pCert) {
            certName(certificate->pCert, CERT_NAME_ATTR_TYPE, 0, (void*) szOID_COMMON_NAME, signer, 128);
        }
    }
    data.dwStateAction = WTD_STATEACTION_CLOSE;
    verify(nullptr, &action, &data);
    if (status != ERROR_SUCCESS) {
        return fgFail("%s has no valid signature (WinVerifyTrust 0x%08X); it was not loaded", name, (unsigned) status);
    }
    if (lstrcmpW(signer, SIGNER) != 0) {
        char who[128];
        utf8(signer, who, sizeof who);
        return fgFail("%s is signed by \"%s\", not AMD; it was not loaded", name, who);
    }
    return true;
}

class FfxBackend : public FgBackend {
public:
    FfxBackend(const Api& api, int flags) : m_api(api), m_flags(flags) {}

    const char* name() const override {
        return "AMD FSR 3.1.5 frame generation (FidelityFX SDK 2.0.0)";
    }

    bool supports(const LUID& adapter) override {
        (void) adapter;  // any vendor: what AMD needs is checked on the device
        return true;
    }

    bool deviceCreated(ID3D12Device* device) override {
        // AMD: "requires a GPU with typed UAV load and R16G16B16A16_UNORM support".
        D3D12_FEATURE_DATA_D3D12_OPTIONS options = {};
        if (FAILED(device->CheckFeatureSupport(D3D12_FEATURE_D3D12_OPTIONS, &options, sizeof options))
                || !options.TypedUAVLoadAdditionalFormats) {
            return fgFail("FSR frame generation isn't available: this graphics card can't load typed UAVs");
        }
        D3D12_FEATURE_DATA_FORMAT_SUPPORT format = {DXGI_FORMAT_R16G16B16A16_UNORM};
        if (FAILED(device->CheckFeatureSupport(D3D12_FEATURE_FORMAT_SUPPORT, &format, sizeof format))
                || !(format.Support2 & D3D12_FORMAT_SUPPORT2_UAV_TYPED_LOAD)) {
            return fgFail("FSR frame generation isn't available: this graphics card can't load R16G16B16A16_UNORM UAVs");
        }
        m_device = device;
        return true;
    }

    HRESULT createSwapChain(IDXGIFactory4* factory, ID3D12CommandQueue* queue, HWND hwnd, const DXGI_SWAP_CHAIN_DESC1& desc,
            IDXGISwapChain1** swapChain) override {
        *swapChain = nullptr;
        DXGI_SWAP_CHAIN_DESC1 chainDesc = desc;
        IDXGISwapChain4* proxy = nullptr;
        ffxCreateContextDescFrameGenerationSwapChainForHwndDX12 create = {};
        create.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATIONSWAPCHAIN_FOR_HWND_DX12;
        create.swapchain = &proxy;
        create.hwnd = hwnd;
        create.desc = &chainDesc;
        create.fullscreenDesc = nullptr;  // windowed, as the presenter's own swap chain
        create.dxgiFactory = factory;
        create.gameQueue = queue;
        ffxContext context = nullptr;
        ffxReturnCode_t result = m_api.createContext(&context, &create.header, nullptr);
        if (result != FFX_API_RETURN_OK || !proxy) {
            fgFail("AMD's frame generation swap chain: ffxCreateContext %s%s", returnName(result), lastErrorSuffix());
            if (context) {
                m_api.destroyContext(&context, nullptr);
            }
            return E_FAIL;
        }
        m_swapChainContext = context;
        m_swapChain = proxy;  // its context holds a reference until shutdown; the presenter gets this one
        m_format = desc.Format;
        m_waitable = proxy->GetFrameLatencyWaitableObject();
        // As AMD's sample, after a swap chain made another way: DXGI leaves the window alone (no Alt+Enter).
        factory->MakeWindowAssociation(hwnd, DXGI_MWA_NO_WINDOW_CHANGES);
        // The frame generation context now, at the window's size, so a card or driver it can't run on falls back at
        // once; it is made again when the back buffer's size changes.
        RECT client = {};
        if (GetClientRect(hwnd, &client) && client.right > 0 && client.bottom > 0
                && !createContext((UINT) client.right, (UINT) client.bottom)) {
            proxy->Release();  // shutdown destroys the swap chain's context
            return E_FAIL;
        }
        *swapChain = proxy;
        return S_OK;
    }

    bool waitable() const override {
        return true;
    }

    bool composesUi() const override {
        return true;
    }

    bool frame(const FgFrame& frame) override {
        m_frameId++;
        m_prepare = false;
        m_split = false;
        m_status &= ~STATUS_CALL;
        bool generate = (frame.flags & FG_FRAME_GENERATE) && frame.camera && frame.inputs[FG_DEPTH]
                && frame.inputs[FG_MOTION] && frame.renderWidth > 0 && frame.renderHeight > 0;
        if (generate && (!m_context || m_width != frame.backWidth || m_height != frame.backHeight)) {
            destroyContext();
            generate = createContext(frame.backWidth, frame.backHeight);  // logged; STATUS_CONTEXT if it can't
        }
        bool split = generate && (frame.flags & FG_FRAME_UI) && frame.inputs[FG_HUDLESS] && frame.inputs[FG_UI_ALPHA]
                && frame.ui;
        bool configured = !m_context || configure(generate);
        // Every frame: the swap chain lets go of the HUD texture at Present (null: the frame has its HUD).
        bool registered = registerUi(split ? frame.ui : nullptr);
        m_generating = generate && configured;
        m_split = split && registered;
        if (m_generating) {
            m_depth = frame.inputs[FG_DEPTH];
            m_motion = frame.inputs[FG_MOTION];
            m_renderWidth = frame.renderWidth;
            m_renderHeight = frame.renderHeight;
            CopyMemory(m_camera, frame.camera, sizeof m_camera);
            m_prepare = true;
        }
        m_resetNext = (frame.flags & FG_FRAME_RESET) != 0;
        return configured && registered;
    }

    bool uiApart() const override {
        return m_split;
    }

    void record(ID3D12GraphicsCommandList* list) override {
        if (!m_prepare) {
            return;
        }
        m_prepare = false;
        const float* c = m_camera;
        ffxDispatchDescFrameGenerationPrepareCameraInfo cameraInfo = {};
        cameraInfo.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION_PREPARE_CAMERAINFO;
        for (int i = 0; i < 3; i++) {
            cameraInfo.cameraPosition[i] = c[FG_CAMERA_POS + i];
            cameraInfo.cameraUp[i] = c[FG_CAMERA_UP + i];
            cameraInfo.cameraRight[i] = c[FG_CAMERA_RIGHT + i];
            cameraInfo.cameraForward[i] = c[FG_CAMERA_FORWARD + i];
        }
        ffxDispatchDescFrameGenerationPrepare prepare = {};
        prepare.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION_PREPARE;
        prepare.header.pNext = &cameraInfo.header;
        prepare.frameID = m_frameId;
        prepare.flags = 0;
        prepare.commandList = list;
        prepare.renderSize = {m_renderWidth, m_renderHeight};
        prepare.jitterOffset = {c[FG_JITTER], c[FG_JITTER + 1]};
        // FSR wants render pixels; the motion scale makes the vectors fractions of the render size.
        prepare.motionVectorScale = {c[FG_MOTION_SCALE] * (float) m_renderWidth, c[FG_MOTION_SCALE + 1] * (float) m_renderHeight};
        prepare.frameTimeDelta = c[FG_FRAME_TIME_MS];
        // Reversed infinite depth, as AMD's sample passes it: the near plane as the far one, the near one unbounded.
        prepare.cameraNear = FLT_MAX;
        prepare.cameraFar = c[FG_NEAR];
        prepare.cameraFovAngleVertical = c[FG_FOV_Y];
        prepare.viewSpaceToMetersFactor = c[FG_METERS_PER_UNIT];
        // The inputs are in COMMON outside a command list (written by OpenGL); FidelityFX moves them and back.
        prepare.depth = ffxApiGetResourceDX12(m_depth, FFX_API_RESOURCE_STATE_COMMON);
        prepare.motionVectors = ffxApiGetResourceDX12(m_motion, FFX_API_RESOURCE_STATE_COMMON);
        if (!call("ffxDispatch (prepare)", m_api.dispatch(&m_context, &prepare.header))) {
            m_resetNext = true;  // a copy of this frame rather than one from an older prepare
            fgLog('E', lastFailure());
        }
    }

    void presented(ID3D12CommandQueue* queue) override {
        (void) queue;  // the inputs were read by the presenter's own command list, before Present
        m_split = false;
        m_prepare = false;
    }

    void beforeResize() override {
        if (m_generating) {
            fgLog('W', "Frame generation was on when the swap chain resized: it is turned off one frame before a resize");
        }
        destroyContext();  // made again at the new size
    }

    void off() override {
        if (m_context && m_generating && configure(false)) {
            m_generating = false;
        }
    }

    void state(FgState& state) override {
        state.generating = m_generating ? 1 : 0;
        state.status = m_status;
        state.framesPresented = m_generating ? 2 : 1;
        state.minSize = 0;
        state.maxGenerated = 1;
        state.vsyncSupported = 1;
        state.errors = m_errors;
    }

    void stop() override {
        if (m_swapChainContext) {
            registerUi(nullptr);
        }
        destroyContext();
        m_split = false;
        m_prepare = false;
    }

    void shutdown() override {
        stop();
        if (m_swapChainContext) {
            // The last reference: AMD's swap chain waits for its presents and goes, with the real one.
            m_api.destroyContext(&m_swapChainContext, nullptr);
            m_swapChainContext = nullptr;
        }
        m_swapChain = nullptr;
        if (m_waitable) {
            CloseHandle(m_waitable);  // AMD's swap chain made it and leaves it to the game
            m_waitable = nullptr;
        }
    }

private:
    Api m_api;
    int m_flags;
    ID3D12Device* m_device = nullptr;
    ffxContext m_swapChainContext = nullptr;
    IDXGISwapChain4* m_swapChain = nullptr;
    HANDLE m_waitable = nullptr;
    DXGI_FORMAT m_format = DXGI_FORMAT_B8G8R8A8_UNORM;
    ffxContext m_context = nullptr;
    UINT m_width = 0;
    UINT m_height = 0;
    UINT m_failedWidth = 0;
    UINT m_failedHeight = 0;
    uint64_t m_frameId = 0;
    bool m_generating = false;
    bool m_split = false;
    bool m_prepare = false;
    bool m_resetNext = false;
    ID3D12Resource* m_depth = nullptr;
    ID3D12Resource* m_motion = nullptr;
    UINT m_renderWidth = 0;
    UINT m_renderHeight = 0;
    float m_camera[FG_CAMERA_FLOATS] = {};
    long long m_status = 0;
    long long m_errors = 0;
    char m_failure[400] = {};

    bool call(const char* what, ffxReturnCode_t result) {
        if (result == FFX_API_RETURN_OK) {
            return true;
        }
        m_errors++;
        m_status |= STATUS_CALL;
        wsprintfA(m_failure, "%s: %s%s", what, returnName(result), lastErrorSuffix());
        return fgFail("%s", m_failure);
    }

    const char* lastFailure() const {
        return m_failure;
    }

    /** The swap chain calls this during Present to record the generated frame, on the presenting queue. */
    static ffxReturnCode_t generateFrame(ffxDispatchDescFrameGeneration* params, void* self) {
        FfxBackend* backend = (FfxBackend*) self;
        if (backend->m_resetNext) {
            params->reset = true;
        }
        ffxReturnCode_t result = backend->m_api.dispatch(&backend->m_context, &params->header);
        if (result != FFX_API_RETURN_OK) {
            backend->call("ffxDispatch (frame generation)", result);
            fgLog('E', backend->lastFailure());
        }
        return result;
    }

    bool configure(bool enabled) {
        ffxConfigureDescFrameGeneration config = {};
        config.header.type = FFX_API_CONFIGURE_DESC_TYPE_FRAMEGENERATION;
        config.swapChain = m_swapChain;
        config.presentCallback = nullptr;  // AMD's own HUD composition
        config.frameGenerationCallback = generateFrame;
        config.frameGenerationCallbackUserContext = this;
        config.frameGenerationEnabled = enabled;
        config.allowAsyncWorkloads = false;
        config.flags = 0;
        config.onlyPresentGenerated = false;
        config.frameID = m_frameId;
        return call(enabled ? "ffxConfigure (on)" : "ffxConfigure (off)", m_api.configure(&m_context, &config.header));
    }

    bool registerUi(ID3D12Resource* ui) {
        ffxConfigureDescFrameGenerationSwapChainRegisterUiResourceDX12 desc = {};
        desc.header.type = FFX_API_CONFIGURE_DESC_TYPE_FRAMEGENERATIONSWAPCHAIN_REGISTERUIRESOURCE_DX12;
        desc.uiResource = ffxApiGetResourceDX12(ui, FFX_API_RESOURCE_STATE_PIXEL_COMPUTE_READ);
        desc.flags = ui ? FFX_FRAMEGENERATION_UI_COMPOSITION_FLAG_USE_PREMUL_ALPHA
                | FFX_FRAMEGENERATION_UI_COMPOSITION_FLAG_ENABLE_INTERNAL_UI_DOUBLE_BUFFERING : 0;
        return call("ffxConfigure (HUD texture)", m_api.configure(&m_swapChainContext, &desc.header));
    }

    /** The frame generation context for a {@code width} x {@code height} back buffer. False (logged) if it can't. */
    bool createContext(UINT width, UINT height) {
        if (width == m_failedWidth && height == m_failedHeight) {
            return false;  // failed at this size already: tried again at another
        }
        ffxCreateBackendDX12Desc backend = {};
        backend.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;
        backend.device = m_device;
        ffxCreateContextDescFrameGeneration create = {};
        create.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION;
        create.header.pNext = &backend.header;
        create.flags = FFX_FRAMEGENERATION_ENABLE_DEPTH_INVERTED | FFX_FRAMEGENERATION_ENABLE_DEPTH_INFINITE
                | ((m_flags & FG_FFX_VERBOSE) ? FFX_FRAMEGENERATION_ENABLE_DEBUG_CHECKING : 0);
        create.displaySize = {width, height};
        create.maxRenderSize = {width, height};
        create.backBufferFormat = ffxApiGetSurfaceFormatDX12(m_format);
        ffxContext context = nullptr;
        ffxReturnCode_t result = m_api.createContext(&context, &create.header, nullptr);
        if (result != FFX_API_RETURN_OK || !context) {
            m_failedWidth = width;
            m_failedHeight = height;
            m_status |= STATUS_CONTEXT;
            m_errors++;
            wsprintfA(m_failure, "FSR frame generation could not start at %ux%u: ffxCreateContext %s%s", width, height,
                    returnName(result), lastErrorSuffix());
            fgLog('E', m_failure);
            return fgFail("%s", m_failure);
        }
        m_context = context;
        m_width = width;
        m_height = height;
        m_failedWidth = 0;
        m_failedHeight = 0;
        m_status &= ~STATUS_CONTEXT;
        ffxConfigureDescGlobalDebug1 debug = {};
        debug.header.type = FFX_API_CONFIGURE_DESC_TYPE_GLOBALDEBUG1;
        debug.fpMessage = onMessage;
        debug.debugLevel = (m_flags & FG_FFX_VERBOSE) ? FFX_API_CONFIGURE_GLOBALDEBUG_LEVEL_VERBOSE
                : FFX_API_CONFIGURE_GLOBALDEBUG_LEVEL_WARNINGS;
        m_api.configure(&m_context, &debug.header);
        ffxQueryGetProviderVersion version = {};
        version.header.type = FFX_API_QUERY_DESC_TYPE_GET_PROVIDER_VERSION;
        char line[160];
        if (m_api.query(&m_context, &version.header) == FFX_API_RETURN_OK && version.versionName) {
            wsprintfA(line, "FSR frame generation %s at %ux%u", version.versionName, width, height);
        } else {
            wsprintfA(line, "FSR frame generation at %ux%u", width, height);
        }
        fgLog('I', line);
        return true;
    }

    /** Off without callbacks, as AMD's sample does before the context goes; then the GPU's work with it, then it. */
    void destroyContext() {
        if (!m_context) {
            return;
        }
        ffxConfigureDescFrameGeneration config = {};
        config.header.type = FFX_API_CONFIGURE_DESC_TYPE_FRAMEGENERATION;
        config.swapChain = m_swapChain;
        config.frameGenerationEnabled = false;
        config.frameID = m_frameId;
        m_api.configure(&m_context, &config.header);
        fgWaitIdle();
        m_api.destroyContext(&m_context, nullptr);
        m_context = nullptr;
        m_width = 0;
        m_height = 0;
        m_generating = false;
        m_prepare = false;
    }
};

}  // namespace

FgBackend* createFfxBackend(const wchar_t* folder, int flags) {
    wchar_t generation[MAX_PATH];
    wchar_t loader[MAX_PATH];
    if (!folder || lstrlenW(folder) + 45 >= MAX_PATH) {
        fgFail("The FidelityFX folder's path is too long");
        return nullptr;
    }
    wsprintfW(generation, L"%s\\%s", folder, FRAMEGENERATION_DLL);
    wsprintfW(loader, L"%s\\%s", folder, LOADER_DLL);
    if (GetFileAttributesW(generation) == INVALID_FILE_ATTRIBUTES || GetFileAttributesW(loader) == INVALID_FILE_ATTRIBUTES) {
        fgFail("No amd_fidelityfx_loader_dx12.dll and amd_fidelityfx_framegeneration_dx12.dll in the FidelityFX folder");
        return nullptr;
    }
    if (!(flags & FG_FFX_DEVELOPMENT) && (!signedByAmd(generation, "amd_fidelityfx_framegeneration_dx12.dll")
            || !signedByAmd(loader, "amd_fidelityfx_loader_dx12.dll"))) {
        return nullptr;
    }
    // Frame generation's DLL first: the loader loads it by name, and finds this one loaded.
    if (!LoadLibraryExW(generation, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH)) {
        fgFail("LoadLibrary(amd_fidelityfx_framegeneration_dx12.dll) failed: error %u", (unsigned) GetLastError());
        return nullptr;
    }
    HMODULE module = LoadLibraryExW(loader, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH);
    if (!module) {
        fgFail("LoadLibrary(amd_fidelityfx_loader_dx12.dll) failed: error %u", (unsigned) GetLastError());
        return nullptr;
    }
    Api api{};
    if (!proc(module, "ffxCreateContext", api.createContext) || !proc(module, "ffxDestroyContext", api.destroyContext)
            || !proc(module, "ffxConfigure", api.configure) || !proc(module, "ffxQuery", api.query)
            || !proc(module, "ffxDispatch", api.dispatch)) {
        return nullptr;
    }
    return new FfxBackend(api, flags);
}

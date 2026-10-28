// cg_streamline.cpp: NVIDIA DLSS Frame Generation through Streamline, the presenter's first frame generation backend
// (cg_framegen.h), and Streamline's Reflex and PC Latency markers, which stand in for NVAPI's Reflex while Streamline is
// loaded (frame generation needs Reflex, and NVAPI's Reflex can't run beside Streamline's).
//
// Streamline 2.14.16 with manual hooking, as NVIDIA's guides lay it out (ProgrammingGuideManualHooking.md,
// ProgrammingGuideDLSS_G.md, ProgrammingGuideReflex.md, ProgrammingGuidePCL.md):
//  - sl.interposer.dll is loaded by its full path once its NVIDIA signature checks out (sl_security.h), and never
//    unloaded; slInit runs before anything Streamline hooks is made, with manual hooking and frame-based tagging, without
//    over-the-air updates;
//  - the presenter's native device goes to slSetD3DDevice, and the presenter keeps using its native device, queue and
//    factory for everything Streamline doesn't hook: the queue comes from the upgraded (proxy) device's
//    CreateCommandQueue, the swap chain from the upgraded factory with that proxy queue, and every call on the swap
//    chain goes through its proxy (Present, GetBuffer, ResizeBuffers, GetCurrentBackBufferIndex);
//  - each frame: a frame token for the game's frame index, the common constants, the four inputs tagged until Present
//    (or null tags), DLSS-G's options when they change; after Present, DLSS-G's state, whose fence the presenting
//    queue waits on before OpenGL may write those inputs again (they are written outside the presenting queue);
//  - no frame latency waitable object: Reflex's sleep paces the frames, and it and the PC Latency markers run every
//    frame, Reflex on or off;
//  - slShutdown after the proxies are released, before the native device is.
//
// The feature functions (slDLSSGSetOptions, slReflexSleep...) are fetched with slGetFeatureFunction once the device is
// set, instead of through the inline helpers in sl_*.h, which need sl.interposer.lib linked.
//
// The proxies' reference counts follow Streamline 2.14.16's interposer: slUpgradeInterface on a device takes over the
// reference it is given, on a factory it adds its own; a proxy's Release also releases what it wraps.

#include "cg_framegen.h"

#include <sl.h>
#include <sl_dlss_g.h>
#include <sl_reflex.h>
#include <sl_pcl.h>
#include <sl_security.h>

#define CG_API extern "C" __declspec(dllexport)

namespace {

const char* const RESULT_NAMES[] = {"eOk", "eErrorIO", "eErrorDriverOutOfDate", "eErrorOSOutOfDate",
    "eErrorOSDisabledHWS", "eErrorDeviceNotCreated", "eErrorNoSupportedAdapterFound", "eErrorAdapterNotSupported",
    "eErrorNoPlugins", "eErrorVulkanAPI", "eErrorDXGIAPI", "eErrorD3DAPI", "eErrorNRDAPI", "eErrorNVAPI",
    "eErrorReflexAPI", "eErrorNGXFailed", "eErrorJSONParsing", "eErrorMissingProxy", "eErrorMissingResourceState",
    "eErrorInvalidIntegration", "eErrorMissingInputParameter", "eErrorNotInitialized", "eErrorComputeFailed",
    "eErrorInitNotCalled", "eErrorExceptionHandler", "eErrorInvalidParameter", "eErrorMissingConstants",
    "eErrorDuplicatedConstants", "eErrorMissingOrInvalidAPI", "eErrorCommonConstantsMissing",
    "eErrorUnsupportedInterface", "eErrorFeatureMissing", "eErrorFeatureNotSupported", "eErrorFeatureMissingHooks",
    "eErrorFeatureFailedToLoad", "eErrorFeatureWrongPriority", "eErrorFeatureMissingDependency",
    "eErrorFeatureManagerInvalidState", "eErrorInvalidState", "eWarnOutOfVRAM"};

const char* resultName(sl::Result result) {
    unsigned index = (unsigned) result;
    return index < sizeof RESULT_NAMES / sizeof RESULT_NAMES[0] ? RESULT_NAMES[index] : "unknown result";
}

/** Streamline's last error line, for failure messages (its log callback runs on any thread). */
SRWLOCK g_lastErrorLock = SRWLOCK_INIT;
char g_lastError[300];

void onLog(sl::LogType type, const char* message) {
    char level = type == sl::LogType::eError ? 'E' : type == sl::LogType::eWarn ? 'W' : 'I';
    if (level == 'E') {
        AcquireSRWLockExclusive(&g_lastErrorLock);
        lstrcpynA(g_lastError, message, sizeof g_lastError);
        int end = lstrlenA(g_lastError);
        while (end > 0 && (g_lastError[end - 1] == '\n' || g_lastError[end - 1] == '\r')) {
            g_lastError[--end] = 0;
        }
        ReleaseSRWLockExclusive(&g_lastErrorLock);
    }
    fgLog(level, message);
}

/** " (Streamline: <its last error>)", or "" without one. */
const char* lastErrorSuffix() {
    static char suffix[sizeof g_lastError + 16];
    AcquireSRWLockExclusive(&g_lastErrorLock);
    if (g_lastError[0]) {
        wsprintfA(suffix, " (Streamline: %s)", g_lastError);
    } else {
        suffix[0] = 0;
    }
    ReleaseSRWLockExclusive(&g_lastErrorLock);
    return suffix;
}

struct Api {
    PFun_slInit* init;
    PFun_slShutdown* shutdown;
    PFun_slIsFeatureSupported* isFeatureSupported;
    PFun_slIsFeatureLoaded* isFeatureLoaded;
    PFun_slSetFeatureLoaded* setFeatureLoaded;
    PFun_slFreeResources* freeResources;
    PFun_slSetTagForFrame* setTagForFrame;
    PFun_slSetConstants* setConstants;
    PFun_slGetFeatureRequirements* getFeatureRequirements;
    PFun_slGetFeatureVersion* getFeatureVersion;
    PFun_slUpgradeInterface* upgradeInterface;
    PFun_slGetNativeInterface* getNativeInterface;
    PFun_slGetFeatureFunction* getFeatureFunction;
    PFun_slGetNewFrameToken* getNewFrameToken;
    PFun_slSetD3DDevice* setD3DDevice;
    // after slSetD3DDevice
    PFun_slDLSSGGetState* dlssgGetState;
    PFun_slDLSSGSetOptions* dlssgSetOptions;
    PFun_slReflexGetState* reflexGetState;
    PFun_slReflexSleep* reflexSleep;
    PFun_slReflexSetOptions* reflexSetOptions;
    PFun_slPCLSetMarker* pclSetMarker;
};

template <typename T> bool proc(HMODULE module, const char* name, T*& function) {
    function = (T*) GetProcAddress(module, name);
    return function != nullptr || fgFail("sl.interposer.dll has no %s", name);
}

template <typename T> bool featureFunction(const Api& api, sl::Feature feature, const char* name, T*& function) {
    void* address = nullptr;
    sl::Result result = api.getFeatureFunction(feature, name, address);
    function = (T*) address;
    return (result == sl::Result::eOk && function) || fgFail("slGetFeatureFunction(%s): %s%s", name, resultName(result),
            lastErrorSuffix());
}

template <typename T> void release(T*& object) {
    if (object) {
        object->Release();
        object = nullptr;
    }
}

void setRows(sl::float4x4& matrix, const float* values) {
    for (int row = 0; row < 4; row++) {
        matrix.setRow(row, sl::float4(values[row * 4], values[row * 4 + 1], values[row * 4 + 2], values[row * 4 + 3]));
    }
}

/** A camera that is valid but means nothing (identity matrices), for frames without a world: constants every frame. */
void restingCamera(float* camera) {
    ZeroMemory(camera, sizeof(float) * FG_CAMERA_FLOATS);
    for (int m = 0; m < 4; m++) {
        for (int i = 0; i < 4; i++) {
            camera[m * 16 + i * 5] = 1.0f;
        }
    }
    camera[FG_MOTION_SCALE] = camera[FG_MOTION_SCALE + 1] = 1.0f;
    camera[FG_CAMERA_UP + 1] = 1.0f;
    camera[FG_CAMERA_RIGHT] = 1.0f;
    camera[FG_CAMERA_FORWARD + 2] = -1.0f;
    camera[FG_NEAR] = 0.05f;
    camera[FG_FAR] = 1000.0f;
    camera[FG_FOV_Y] = 1.2217305f;  // 70 degrees
    camera[FG_ASPECT] = 16.0f / 9.0f;
    camera[FG_FRAME_TIME_MS] = 16.0f;
    camera[FG_METERS_PER_UNIT] = 1.0f;
}

class StreamlineBackend;
StreamlineBackend* g_streamline;
bool g_started;

class StreamlineBackend : public FgBackend {
public:
    explicit StreamlineBackend(const Api& api) : m_api(api) {
        restingCamera(m_camera);
    }

    const char* name() const override {
        return "DLSS Frame Generation (Streamline 2.14.16)";
    }

    bool supports(const LUID& adapter) override {
        LUID luid = adapter;
        sl::AdapterInfo info{};
        info.deviceLUID = (uint8_t*) &luid;
        info.deviceLUIDSizeInBytes = sizeof luid;
        sl::Result result = m_api.isFeatureSupported(sl::kFeatureDLSS_G, info);
        if (result != sl::Result::eOk) {
            sl::FeatureRequirements requirements{};
            m_api.getFeatureRequirements(sl::kFeatureDLSS_G, requirements);
            const char* why = result == sl::Result::eErrorOSDisabledHWS ? "hardware-accelerated GPU scheduling is off in Windows"
                    : result == sl::Result::eErrorDriverOutOfDate ? "the graphics driver is too old"
                    : result == sl::Result::eErrorOSOutOfDate ? "Windows is too old"
                    : result == sl::Result::eErrorAdapterNotSupported || result == sl::Result::eErrorNoSupportedAdapterFound
                        ? "this graphics card can't run it"
                    : "Streamline says no";
            return fgFail("DLSS Frame Generation isn't available: %s (%s; driver %u.%u, needs %u.%u; Windows build %u, needs %u)%s",
                    why, resultName(result), requirements.driverVersionDetected.major, requirements.driverVersionDetected.minor,
                    requirements.driverVersionRequired.major, requirements.driverVersionRequired.minor,
                    requirements.osVersionDetected.build, requirements.osVersionRequired.build, lastErrorSuffix());
        }
        result = m_api.isFeatureSupported(sl::kFeatureReflex, info);
        if (result != sl::Result::eOk) {
            return fgFail("Streamline's Reflex isn't available (%s), and frame generation needs it%s", resultName(result),
                    lastErrorSuffix());
        }
        return true;
    }

    bool deviceCreated(ID3D12Device* device) override {
        sl::Result result = m_api.setD3DDevice(device);
        if (result != sl::Result::eOk) {
            return fgFail("slSetD3DDevice: %s%s", resultName(result), lastErrorSuffix());
        }
        m_deviceSet = true;
        bool loaded = false;
        if (m_api.isFeatureLoaded(sl::kFeatureDLSS_G, loaded) != sl::Result::eOk || !loaded) {
            return fgFail("DLSS Frame Generation didn't load (is nvngx_dlssg.dll there?)%s", lastErrorSuffix());
        }
        if (!featureFunction(m_api, sl::kFeatureDLSS_G, "slDLSSGGetState", m_api.dlssgGetState)
                || !featureFunction(m_api, sl::kFeatureDLSS_G, "slDLSSGSetOptions", m_api.dlssgSetOptions)
                || !featureFunction(m_api, sl::kFeatureReflex, "slReflexGetState", m_api.reflexGetState)
                || !featureFunction(m_api, sl::kFeatureReflex, "slReflexSleep", m_api.reflexSleep)
                || !featureFunction(m_api, sl::kFeatureReflex, "slReflexSetOptions", m_api.reflexSetOptions)
                || !featureFunction(m_api, sl::kFeaturePCL, "slPCLSetMarker", m_api.pclSetMarker)) {
            return false;
        }
        // Reflex On until the game sets its own: frame generation needs it (DLSSGStatus::eFailReflexNotDetectedAtRuntime).
        sl::ReflexOptions reflex{};
        reflex.mode = sl::ReflexMode::eLowLatency;
        reflexOptions(reflex);
        sl::FeatureVersion version{};
        if (m_api.getFeatureVersion(sl::kFeatureDLSS_G, version) == sl::Result::eOk) {
            char line[160];
            wsprintfA(line, "DLSS Frame Generation: Streamline %u.%u.%u, NGX %u.%u.%u", version.versionSL.major,
                    version.versionSL.minor, version.versionSL.build, version.versionNGX.major, version.versionNGX.minor,
                    version.versionNGX.build);
            fgLog('I', line);
        }
        return true;
    }

    bool setOn(bool on) override {
        if (on == m_on) {
            return true;
        }
        if (!on) {
            // Off for a while (the player turned it off): its memory goes too, as NVIDIA asks with
            // eRetainResourcesWhenOff (stop set the mode to off). Then the plugin stops hooking, so the swap chain
            // made next is a plain one.
            sl::Result result = m_api.freeResources(sl::kFeatureDLSS_G, sl::ViewportHandle(0));
            if (result != sl::Result::eOk) {
                char line[120];
                wsprintfA(line, "slFreeResources (DLSS-G): %s; it goes off all the same", resultName(result));
                fgLog('W', line);
            }
        }
        sl::Result result = m_api.setFeatureLoaded(sl::kFeatureDLSS_G, on);
        if (result != sl::Result::eOk) {
            return failed(on ? "slSetFeatureLoaded (on)" : "slSetFeatureLoaded (off)", result);
        }
        m_on = on;
        m_optionsSet = false;
        m_tagged = false;
        m_generating = false;
        return true;
    }

    bool waitable() const override {
        // Streamline's pacer owns the waitable object while DLSS-G is loaded; without it the swap chain is a plain
        // one (NVIDIA's guide, 12.1).
        return !m_on;
    }

    HRESULT createQueue(ID3D12Device* device, const D3D12_COMMAND_QUEUE_DESC& desc, ID3D12CommandQueue** queue) override {
        *queue = nullptr;
        if (!m_proxyDevice) {
            ID3D12Device* proxy = device;
            device->AddRef();  // the proxy takes this reference over; the presenter keeps its own
            sl::Result result = m_api.upgradeInterface((void**) &proxy);
            if (result != sl::Result::eOk) {
                device->Release();
                fgFail("slUpgradeInterface (device): %s%s", resultName(result), lastErrorSuffix());
                return E_FAIL;
            }
            m_proxyDevice = proxy;
        }
        release(m_proxyQueue);
        HRESULT hr = m_proxyDevice->CreateCommandQueue(&desc, IID_PPV_ARGS(&m_proxyQueue));
        if (FAILED(hr)) {
            fgFail("CreateCommandQueue (Streamline): HRESULT 0x%08X", (unsigned) hr);
            return hr;
        }
        sl::Result result = m_api.getNativeInterface(m_proxyQueue, (void**) queue);
        if (result != sl::Result::eOk || !*queue) {
            *queue = nullptr;
            fgFail("slGetNativeInterface (queue): %s%s", resultName(result), lastErrorSuffix());
            return E_FAIL;
        }
        return S_OK;
    }

    HRESULT createSwapChain(IDXGIFactory4* factory, ID3D12CommandQueue* queue, HWND hwnd, const DXGI_SWAP_CHAIN_DESC1& desc,
            IDXGISwapChain1** swapChain) override {
        (void) queue;  // the swap chain is made for the proxy queue, which Streamline resolves to it
        *swapChain = nullptr;
        if (!m_proxyFactory) {
            IDXGIFactory4* proxy = factory;  // the proxy adds its own reference to the factory
            sl::Result result = m_api.upgradeInterface((void**) &proxy);
            if (result != sl::Result::eOk) {
                fgFail("slUpgradeInterface (factory): %s%s", resultName(result), lastErrorSuffix());
                return E_FAIL;
            }
            m_proxyFactory = proxy;
        }
        HRESULT hr = m_proxyFactory->CreateSwapChainForHwnd(m_proxyQueue, hwnd, &desc, nullptr, nullptr, swapChain);
        if (FAILED(hr)) {
            fgFail("CreateSwapChainForHwnd (Streamline): HRESULT 0x%08X%s", (unsigned) hr, lastErrorSuffix());
        }
        return hr;
    }

    bool frame(const FgFrame& frame) override {
        sl::FrameToken* token = nullptr;
        uint32_t index = frame.frameIndex;
        sl::Result result = m_api.getNewFrameToken(token, &index);
        if (result != sl::Result::eOk || !token) {
            return failed("slGetNewFrameToken", result);
        }
        m_token = token;

        bool generate = (frame.flags & FG_FRAME_GENERATE) && frame.camera && frame.inputs[FG_HUDLESS]
                && frame.inputs[FG_UI_ALPHA] && frame.inputs[FG_DEPTH] && frame.inputs[FG_MOTION]
                && frame.backWidth >= m_minSize && frame.backHeight >= m_minSize;
        bool reset = (frame.flags & FG_FRAME_RESET) || !generate;
        if (frame.camera && (frame.flags & FG_FRAME_CAMERA)) {
            CopyMemory(m_camera, frame.camera, sizeof m_camera);
        } else {
            reset = true;  // the last camera again: the constants are needed every frame
        }
        result = m_api.setConstants(constants(m_camera, reset), *token, sl::ViewportHandle(0));
        if (result != sl::Result::eOk) {
            return failed("slSetConstants", result);
        }

        if (generate) {
            sl::Resource resources[FG_INPUTS] = {
                sl::Resource(sl::ResourceType::eTex2d, frame.inputs[FG_HUDLESS], D3D12_RESOURCE_STATE_COMMON),
                sl::Resource(sl::ResourceType::eTex2d, frame.inputs[FG_UI_ALPHA], D3D12_RESOURCE_STATE_COMMON),
                sl::Resource(sl::ResourceType::eTex2d, frame.inputs[FG_DEPTH], D3D12_RESOURCE_STATE_COMMON),
                sl::Resource(sl::ResourceType::eTex2d, frame.inputs[FG_MOTION], D3D12_RESOURCE_STATE_COMMON),
            };
            sl::Extent full{0, 0, frame.backWidth, frame.backHeight};
            sl::Extent render{0, 0, frame.renderWidth, frame.renderHeight};
            sl::ResourceTag tags[FG_INPUTS] = {
                sl::ResourceTag(&resources[FG_HUDLESS], sl::kBufferTypeHUDLessColor, sl::eValidUntilPresent, &full),
                sl::ResourceTag(&resources[FG_UI_ALPHA], sl::kBufferTypeUIAlpha, sl::eValidUntilPresent, &full),
                sl::ResourceTag(&resources[FG_DEPTH], sl::kBufferTypeDepth, sl::eValidUntilPresent, &render),
                sl::ResourceTag(&resources[FG_MOTION], sl::kBufferTypeMotionVectors, sl::eValidUntilPresent, &render),
            };
            result = m_api.setTagForFrame(*token, sl::ViewportHandle(0), tags, FG_INPUTS, nullptr);
            if (result != sl::Result::eOk) {
                return failed("slSetTagForFrame", result);
            }
            m_tagged = true;
        } else if (m_tagged && !nullTags(*token)) {
            return false;
        }

        // Dynamic Multi Frame Generation only where Streamline says it runs: asking for it elsewhere is an error (NVIDIA's
        // DLSS-G guide, 14.2). It targets the display's refresh rate (dynamicTargetFrameRate 0), or with vsync the rate
        // that presents without tearing, and ignores numFramesToGenerate.
        bool dynamic = generate && (frame.flags & FG_FRAME_DYNAMIC) && dynamicSupported();
        sl::DLSSGOptions options{};
        options.mode = !generate ? sl::DLSSGMode::eOff : dynamic ? sl::DLSSGMode::eDynamic : sl::DLSSGMode::eOn;
        options.dynamicTargetFrameRate = 0.0f;
        UINT frames = frame.framesToGenerate < 1 ? 1 : (UINT) frame.framesToGenerate;
        options.numFramesToGenerate = m_maxGenerated > 0 && frames > m_maxGenerated ? m_maxGenerated : frames;
        options.flags = sl::DLSSGFlags::eRetainResourcesWhenOff;
        options.enableUserInterfaceRecomposition = (frame.flags & FG_FRAME_UI) ? sl::Boolean::eTrue : sl::Boolean::eFalse;
        if (!m_optionsSet || options.mode != m_options.mode || options.numFramesToGenerate != m_options.numFramesToGenerate
                || options.enableUserInterfaceRecomposition != m_options.enableUserInterfaceRecomposition) {
            result = m_api.dlssgSetOptions(sl::ViewportHandle(0), options);
            if (result != sl::Result::eOk) {
                m_optionsSet = false;
                return failed("slDLSSGSetOptions", result);
            }
            m_options = options;
            m_optionsSet = true;
        }
        return true;
    }

    void presented(ID3D12CommandQueue* queue) override {
        sl::DLSSGState state{};
        sl::Result result = m_api.dlssgGetState(sl::ViewportHandle(0), state, nullptr);
        if (result != sl::Result::eOk) {
            m_errors++;
            return;
        }
        m_generating = m_optionsSet && m_options.mode != sl::DLSSGMode::eOff;
        m_status = (long long) state.status;
        m_framesPresented = state.numFramesActuallyPresented;
        took(state);
        ID3D12Fence* fence = (ID3D12Fence*) state.inputsProcessingCompletionFence;
        if (fence && state.lastPresentInputsProcessingCompletionFenceValue > 0) {
            queue->Wait(fence, state.lastPresentInputsProcessingCompletionFenceValue);
        }
    }

    void off() override {
        if (!m_optionsSet || m_options.mode == sl::DLSSGMode::eOff) {
            return;
        }
        sl::DLSSGOptions options = m_options;
        options.mode = sl::DLSSGMode::eOff;
        sl::Result result = m_api.dlssgSetOptions(sl::ViewportHandle(0), options);
        if (result != sl::Result::eOk) {
            m_optionsSet = false;
            failed("slDLSSGSetOptions (off)", result);
            return;
        }
        m_options = options;
    }

    void beforeResize() override {
        if (m_optionsSet && m_options.mode != sl::DLSSGMode::eOff) {
            fgLog('W', "Frame generation was on when the swap chain resized: it is turned off one frame before a resize");
        }
    }

    void state(FgState& state) override {
        state.generating = m_generating ? 1 : 0;
        state.status = m_status;
        state.framesPresented = m_framesPresented;
        state.minSize = m_minSize;
        state.maxGenerated = m_maxGenerated;
        state.vsyncSupported = m_vsyncSupported ? 1 : 0;
        state.errors = m_errors;
        state.dynamicSupported = m_dynamicSupported ? 1 : 0;
    }

    void stop() override {
        if (m_on && m_deviceSet && m_api.dlssgSetOptions) {
            sl::DLSSGOptions options{};
            options.mode = sl::DLSSGMode::eOff;
            m_api.dlssgSetOptions(sl::ViewportHandle(0), options);
            m_options = options;
            m_optionsSet = true;
        }
        if (m_tagged && m_token) {
            nullTags(*m_token);
        }
    }

    void shutdown() override {
        stop();
        release(m_proxyQueue);
        release(m_proxyDevice);
        release(m_proxyFactory);
        sl::Result result = m_api.shutdown();
        if (result != sl::Result::eOk) {
            char line[120];
            wsprintfA(line, "slShutdown: %s", resultName(result));
            fgLog('W', line);
        }
        if (g_streamline == this) {
            g_streamline = nullptr;
        }
        // sl.interposer.dll stays loaded: Streamline's guides never unload it within a process.
    }

    // --- Reflex and PC Latency (cg_sl_*) ---

    bool reflexOptions(const sl::ReflexOptions& options) {
        if (!m_api.reflexSetOptions) {
            return fgFail("Streamline's Reflex isn't loaded");
        }
        sl::Result result = m_api.reflexSetOptions(options);
        return result == sl::Result::eOk || failed("slReflexSetOptions", result);
    }

    bool reflexSleep(uint32_t frameIndex) {
        sl::FrameToken* token = tokenFor(frameIndex);
        if (!token) {
            return false;
        }
        sl::Result result = m_api.reflexSleep(*token);
        return result == sl::Result::eOk || failed("slReflexSleep", result);
    }

    bool marker(uint32_t marker, uint32_t frameIndex) {
        sl::FrameToken* token = tokenFor(frameIndex);
        if (!token) {
            return false;
        }
        sl::Result result = m_api.pclSetMarker((sl::PCLMarker) marker, *token);
        return result == sl::Result::eOk || failed("slPCLSetMarker", result);
    }

    /** {@code out}: flags (1 low latency available, 2 report available, 4 flash indicator driver controlled), then the
     *  64 reports' 16 numbers each (ReflexReport's order). Returns how many numbers were written, 0 on failure. */
    int reflexState(long long* out, int max) {
        if (!m_api.reflexGetState) {
            return fgFail("Streamline's Reflex isn't loaded");
        }
        static sl::ReflexState state;  // large: off the stack
        state = sl::ReflexState();
        sl::Result result = m_api.reflexGetState(state);
        if (result != sl::Result::eOk) {
            return failed("slReflexGetState", result);
        }
        if (max < 1) {
            return 0;
        }
        out[0] = (state.lowLatencyAvailable ? 1 : 0) | (state.latencyReportAvailable ? 2 : 0)
                | (state.flashIndicatorDriverControlled ? 4 : 0);
        int written = 1;
        for (int i = 0; i < sl::kReflexFrameReportCount && written + 16 <= max; i++) {
            const sl::ReflexReport& r = state.frameReport[i];
            const uint64_t values[16] = {r.frameID, r.inputSampleTime, r.simStartTime, r.simEndTime, r.renderSubmitStartTime,
                r.renderSubmitEndTime, r.presentStartTime, r.presentEndTime, r.driverStartTime, r.driverEndTime,
                r.osRenderQueueStartTime, r.osRenderQueueEndTime, r.gpuRenderStartTime, r.gpuRenderEndTime,
                r.gpuActiveRenderTimeUs, r.gpuFrameTimeUs};
            for (int v = 0; v < 16; v++) {
                out[written++] = (long long) values[v];
            }
        }
        return written;
    }

private:
    Api m_api;
    ID3D12Device* m_proxyDevice = nullptr;
    ID3D12CommandQueue* m_proxyQueue = nullptr;
    IDXGIFactory4* m_proxyFactory = nullptr;
    bool m_deviceSet = false;
    bool m_on = true;
    sl::FrameToken* m_token = nullptr;
    bool m_tagged = false;
    float m_camera[FG_CAMERA_FLOATS];
    sl::DLSSGOptions m_options;
    bool m_optionsSet = false;
    bool m_generating = false;
    long long m_status = 0;
    long long m_framesPresented = 0;
    UINT m_minSize = 0;
    UINT m_maxGenerated = 0;
    bool m_vsyncSupported = false;
    bool m_dynamicSupported = false;
    bool m_stateRead = false;
    bool m_dynamicRefusedLogged = false;
    long long m_errors = 0;

    /** What the card can do, from a DLSSGState: the most frames it generates, vsync, Dynamic Multi Frame Generation. */
    void took(const sl::DLSSGState& state) {
        m_minSize = state.minWidthOrHeight;
        m_maxGenerated = state.numFramesToGenerateMax;
        m_vsyncSupported = state.bIsVsyncSupportAvailable == sl::Boolean::eTrue;
        m_dynamicSupported = state.bIsDynamicMFGSupported == sl::Boolean::eTrue;
        m_stateRead = true;
    }

    /** Whether Streamline runs Dynamic Multi Frame Generation here, asked before the first Present if need be. */
    bool dynamicSupported() {
        if (!m_stateRead) {
            sl::DLSSGState state{};
            if (m_api.dlssgGetState(sl::ViewportHandle(0), state, nullptr) != sl::Result::eOk) {
                return false;
            }
            took(state);
        }
        if (!m_dynamicSupported && !m_dynamicRefusedLogged) {
            m_dynamicRefusedLogged = true;
            fgLog('W', "Dynamic Multi Frame Generation was asked for, but Streamline says it doesn't run here: a fixed"
                    " number of generated frames instead");
        }
        return m_dynamicSupported;
    }

    bool failed(const char* what, sl::Result result) {
        m_errors++;
        return fgFail("%s: %s%s", what, resultName(result), lastErrorSuffix());
    }

    sl::FrameToken* tokenFor(uint32_t frameIndex) {
        sl::FrameToken* token = nullptr;
        sl::Result result = m_api.getNewFrameToken(token, &frameIndex);
        if (result != sl::Result::eOk || !token) {
            failed("slGetNewFrameToken", result);
            return nullptr;
        }
        return token;
    }

    /** Lets go of the tagged inputs (Streamline holds a reference until a null tag). */
    bool nullTags(const sl::FrameToken& token) {
        sl::ResourceTag tags[FG_INPUTS] = {
            sl::ResourceTag(nullptr, sl::kBufferTypeHUDLessColor, sl::eValidUntilPresent),
            sl::ResourceTag(nullptr, sl::kBufferTypeUIAlpha, sl::eValidUntilPresent),
            sl::ResourceTag(nullptr, sl::kBufferTypeDepth, sl::eValidUntilPresent),
            sl::ResourceTag(nullptr, sl::kBufferTypeMotionVectors, sl::eValidUntilPresent),
        };
        sl::Result result = m_api.setTagForFrame(token, sl::ViewportHandle(0), tags, FG_INPUTS, nullptr);
        if (result != sl::Result::eOk) {
            return failed("slSetTagForFrame (null)", result);
        }
        m_tagged = false;
        return true;
    }

    static sl::Constants constants(const float* camera, bool reset) {
        sl::Constants c{};
        setRows(c.cameraViewToClip, camera + FG_VIEW_TO_CLIP);
        setRows(c.clipToCameraView, camera + FG_CLIP_TO_VIEW);
        setRows(c.clipToPrevClip, camera + FG_CLIP_TO_PREV_CLIP);
        setRows(c.prevClipToClip, camera + FG_PREV_CLIP_TO_CLIP);
        c.jitterOffset = sl::float2(camera[FG_JITTER], camera[FG_JITTER + 1]);
        c.mvecScale = sl::float2(camera[FG_MOTION_SCALE], camera[FG_MOTION_SCALE + 1]);
        c.cameraPinholeOffset = sl::float2(0.0f, 0.0f);
        c.cameraPos = sl::float3(camera[FG_CAMERA_POS], camera[FG_CAMERA_POS + 1], camera[FG_CAMERA_POS + 2]);
        c.cameraUp = sl::float3(camera[FG_CAMERA_UP], camera[FG_CAMERA_UP + 1], camera[FG_CAMERA_UP + 2]);
        c.cameraRight = sl::float3(camera[FG_CAMERA_RIGHT], camera[FG_CAMERA_RIGHT + 1], camera[FG_CAMERA_RIGHT + 2]);
        c.cameraFwd = sl::float3(camera[FG_CAMERA_FORWARD], camera[FG_CAMERA_FORWARD + 1], camera[FG_CAMERA_FORWARD + 2]);
        c.cameraNear = camera[FG_NEAR];
        c.cameraFar = camera[FG_FAR];
        c.cameraFOV = camera[FG_FOV_Y];
        c.cameraAspectRatio = camera[FG_ASPECT];
        c.depthInverted = sl::Boolean::eTrue;
        c.cameraMotionIncluded = sl::Boolean::eTrue;
        c.motionVectors3D = sl::Boolean::eFalse;
        c.reset = reset ? sl::Boolean::eTrue : sl::Boolean::eFalse;
        c.orthographicProjection = sl::Boolean::eFalse;
        c.motionVectorsDilated = sl::Boolean::eFalse;
        c.motionVectorsJittered = sl::Boolean::eFalse;
        return c;
    }
};

}  // namespace

FgBackend* createStreamlineBackend(const wchar_t* pluginFolder, const wchar_t* dllFolder, const wchar_t* logFolder,
        int flags) {
    if (g_started) {
        fgFail("Streamline starts once per game run");
        return nullptr;
    }
    wchar_t path[MAX_PATH];
    if (lstrlenW(pluginFolder) + 20 >= MAX_PATH) {
        fgFail("The Streamline folder's path is too long");
        return nullptr;
    }
    lstrcpyW(path, pluginFolder);
    lstrcatW(path, L"\\sl.interposer.dll");
    if (GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES) {
        fgFail("No sl.interposer.dll in the Streamline folder");
        return nullptr;
    }
    if (!(flags & FG_SL_DEVELOPMENT) && !sl::security::verifyEmbeddedSignature(path)) {
        fgFail("sl.interposer.dll isn't signed by NVIDIA; Streamline was not loaded");
        return nullptr;
    }
    HMODULE module = LoadLibraryExW(path, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH);
    if (!module) {
        fgFail("LoadLibrary(sl.interposer.dll) failed: error %u", (unsigned) GetLastError());
        return nullptr;
    }
    g_started = true;

    Api api{};
    if (!proc(module, "slInit", api.init) || !proc(module, "slShutdown", api.shutdown)
            || !proc(module, "slIsFeatureSupported", api.isFeatureSupported)
            || !proc(module, "slIsFeatureLoaded", api.isFeatureLoaded)
            || !proc(module, "slSetFeatureLoaded", api.setFeatureLoaded)
            || !proc(module, "slFreeResources", api.freeResources)
            || !proc(module, "slSetTagForFrame", api.setTagForFrame) || !proc(module, "slSetConstants", api.setConstants)
            || !proc(module, "slGetFeatureRequirements", api.getFeatureRequirements)
            || !proc(module, "slGetFeatureVersion", api.getFeatureVersion)
            || !proc(module, "slUpgradeInterface", api.upgradeInterface)
            || !proc(module, "slGetNativeInterface", api.getNativeInterface)
            || !proc(module, "slGetFeatureFunction", api.getFeatureFunction)
            || !proc(module, "slGetNewFrameToken", api.getNewFrameToken)
            || !proc(module, "slSetD3DDevice", api.setD3DDevice)) {
        return nullptr;
    }

    const sl::Feature features[] = {sl::kFeatureDLSS_G, sl::kFeatureReflex, sl::kFeaturePCL};
    const wchar_t* pluginPaths[] = {pluginFolder, dllFolder};
    sl::Preferences preferences{};
    preferences.showConsole = false;
    preferences.logLevel = (flags & (FG_SL_DEVELOPMENT | FG_SL_VERBOSE)) ? sl::LogLevel::eVerbose : sl::LogLevel::eDefault;
    preferences.pathsToPlugins = pluginPaths;
    preferences.numPathsToPlugins = dllFolder && dllFolder[0] ? 2 : 1;
    preferences.pathToLogsAndData = logFolder;
    preferences.logMessageCallback = onLog;
    preferences.flags = sl::PreferenceFlags::eDisableCLStateTracking | sl::PreferenceFlags::eUseManualHooking
            | sl::PreferenceFlags::eUseFrameBasedResourceTagging;
    preferences.featuresToLoad = features;
    preferences.numFeaturesToLoad = sizeof features / sizeof features[0];
    preferences.engine = sl::EngineType::eCustom;
    preferences.engineVersion = "Minecraft 1.21.1";
    preferences.projectId = "0f4d213d-b46a-4c91-9f2f-baac70721d6b";
    preferences.renderAPI = sl::RenderAPI::eD3D12;
    sl::Result result = api.init(preferences, sl::kSDKVersion);
    if (result != sl::Result::eOk) {
        fgFail("slInit: %s%s", resultName(result), lastErrorSuffix());
        return nullptr;
    }
    g_streamline = new StreamlineBackend(api);
    return g_streamline;
}

// --- Streamline's Reflex, for the game (FrameLatency) while Streamline is loaded ---

/** Reflex's mode (0 off, 1 on, 2 on + boost) and frame cap (microseconds a frame, 0: none). 1 on success. */
CG_API int cg_sl_reflex_options(int mode, int frameLimitUs) {
    if (!g_streamline) {
        return fgFail("Streamline isn't running");
    }
    sl::ReflexOptions options{};
    options.mode = mode == 2 ? sl::ReflexMode::eLowLatencyWithBoost : mode == 1 ? sl::ReflexMode::eLowLatency
            : sl::ReflexMode::eOff;
    options.frameLimitUs = frameLimitUs > 0 ? (uint32_t) frameLimitUs : 0;
    return g_streamline->reflexOptions(options) ? 1 : 0;
}

/** Reflex's sleep at the start of frame {@code frameIndex} (every frame, Reflex on or off). 1 on success. */
CG_API int cg_sl_reflex_sleep(unsigned frameIndex) {
    return g_streamline && g_streamline->reflexSleep(frameIndex) ? 1 : 0;
}

/** A PC Latency marker (sl::PCLMarker: 0 simulation start ... 5 present end, 7 flash) for frame {@code frameIndex}. */
CG_API int cg_sl_marker(int marker, unsigned frameIndex) {
    return g_streamline && marker >= 0 && g_streamline->marker((uint32_t) marker, frameIndex) ? 1 : 0;
}

/** Reflex's state and latency reports into {@code out} (see StreamlineBackend::reflexState). */
CG_API int cg_sl_reflex_state(long long* out, int max) {
    return g_streamline ? g_streamline->reflexState(out, max) : fgFail("Streamline isn't running");
}

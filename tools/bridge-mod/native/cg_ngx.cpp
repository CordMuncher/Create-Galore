// cg_ngx.dll: the Bridge mod's small native helper for DLSS (NVIDIA NGX) on Direct3D 11.
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

#include "nvsdk_ngx.h"
#include "nvsdk_ngx_helpers.h"

#define CG_API extern "C" __declspec(dllexport)

// Bumped whenever a function below changes; the Java side refuses a helper with another version.
static const int CG_NGX_ABI = 1;

// NGX log lines wait here until the Java side reads them. A fixed ring keeps this file free of the C++ library.
#define LOG_LINES 256
#define LOG_LINE_BYTES 512
static SRWLOCK g_logLock = SRWLOCK_INIT;
static char g_log[LOG_LINES][LOG_LINE_BYTES];
static int g_logFirst;
static int g_logCount;

static bool g_initialized;
static ID3D11Device* g_device;
static NVSDK_NGX_Parameter* g_params;
static NVSDK_NGX_Handle* g_feature;
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

CG_API int cg_ngx_abi() {
    return CG_NGX_ABI;
}

// Starts NGX on the given device. appDataPath is a writable folder (NGX keeps its logs there); featureDir is the
// folder holding nvngx_dlss.dll, searched before the driver's own copy. logLevel: 0 off, 1 on, 2 verbose.
CG_API int cg_ngx_init(const char* projectId, const char* engineVersion, const wchar_t* appDataPath,
                       const wchar_t* featureDir, ID3D11Device* device, int logLevel) {
    if (g_initialized) {
        return NVSDK_NGX_Result_FAIL_FeatureAlreadyExists;
    }
    g_featureDir[0] = 0;
    if (featureDir && wcslen(featureDir) < sizeof(g_featureDir) / sizeof(g_featureDir[0])) {
        wcscpy_s(g_featureDir, sizeof(g_featureDir) / sizeof(g_featureDir[0]), featureDir);
    }
    g_featureDirs[0] = g_featureDir;

    NVSDK_NGX_FeatureCommonInfo info = {};
    info.PathListInfo.Path = g_featureDir[0] ? g_featureDirs : nullptr;
    info.PathListInfo.Length = g_featureDir[0] ? 1 : 0;
    info.LoggingInfo.LoggingCallback = logCallback;
    info.LoggingInfo.MinimumLoggingLevel = (NVSDK_NGX_Logging_Level) logLevel;
    info.LoggingInfo.DisableOtherLoggingSinks = false;

    NVSDK_NGX_Result result = NVSDK_NGX_D3D11_Init_with_ProjectID(projectId, NVSDK_NGX_ENGINE_TYPE_CUSTOM, engineVersion,
                                                                  appDataPath, device, &info, NVSDK_NGX_Version_API);
    if (NVSDK_NGX_FAILED(result)) {
        return result;
    }
    g_initialized = true;
    g_device = device;

    result = NVSDK_NGX_D3D11_GetCapabilityParameters(&g_params);
    if (NVSDK_NGX_FAILED(result)) {
        NVSDK_NGX_D3D11_Shutdown1(device);
        g_initialized = false;
        g_device = nullptr;
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
    if (!g_feature) {
        return NVSDK_NGX_Result_Success;
    }
    NVSDK_NGX_Result result = NVSDK_NGX_D3D11_ReleaseFeature(g_feature);
    g_feature = nullptr;
    return result;
}

// (Re)creates the DLSS feature. preset is a render preset (0 = DLSS's default for the mode, 11 = K, ...);
// flags are NVSDK_NGX_DLSS_Feature_Flags.
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
    return NGX_D3D11_CREATE_DLSS_EXT(context, &g_feature, g_params, &create);
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

CG_API int cg_ngx_shutdown() {
    if (!g_initialized) {
        return NVSDK_NGX_Result_Success;
    }
    cg_ngx_release();
    if (g_params) {
        NVSDK_NGX_D3D11_DestroyParameters(g_params);
        g_params = nullptr;
    }
    NVSDK_NGX_Result result = NVSDK_NGX_D3D11_Shutdown1(g_device);
    g_initialized = false;
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

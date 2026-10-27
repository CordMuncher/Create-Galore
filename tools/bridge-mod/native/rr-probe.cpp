// rr-probe.exe: runs NVIDIA DLSS Ray Reconstruction on its own, outside the game, to find what makes it crash in the
// Bridge's Ray Reconstruction pilot (Ray Reconstruction 1: a null read inside NVIDIA's code on the first evaluate,
// with the dev and the release nvngx_dlssd.dll alike, nothing in NGX's log before it).
//
// Usage: rr-probe.exe <folder with nvngx_dlssd.dll and nvngx_dlss.dll (and cg_ngx.dll)> <output folder>
// It runs every variant below in a process of its own (a crash ends only that one), each the same way: a device on
// the NVIDIA card with the most memory, NGX started with the game's project id, the inputs the game hands over (in its
// formats and sizes: 1272x701 to 2544x1401, Performance), the feature made with preset F, three frames evaluated, the
// output read back. Each variant's own lines (and NGX's log) go to <output>\<variant>.txt; the summary is printed and
// written to <output>\summary.txt. One variant changes one thing against d3d11-game, so the first one that stops
// crashing names the cause; d3d12-game tells whether Ray Reconstruction runs on DirectX 12, where the game's own
// presenter already has a device. The dll- variants run the Bridge's own cg_ngx.dll (from the same folder) on Direct3D
// 12 the way the game does, with this program in OpenGL's place: the textures and fences the game shares, the queue
// waiting for "OpenGL done" and signalling "DirectX done".
//
// Built by build-rr-probe.sh against the DLSS SDK, like cg_ngx.dll. Only for the PC test; never part of the pack.

#include <windows.h>
#include <d3d11.h>
#include <d3d12.h>
#include <dxgi1_4.h>
#include <stdio.h>
#include <share.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdarg.h>
#include <wchar.h>

#include "nvsdk_ngx.h"
#include "nvsdk_ngx_helpers.h"
#include "nvsdk_ngx_helpers_dlssd_d3d.h"

static const char* PROJECT_ID = "0f4d213d-b46a-4c91-9f2f-baac70721d6b"; // the Bridge's (Dlss.java)
static const char* ENGINE_VERSION = "1.21.1";
static const unsigned int PRESET_F = 6;

struct Variant {
    const char* name;
    const char* what;
    bool d3d12;
    bool superResolution; // plain DLSS, for comparison
    bool hitDistance;     // specular hit distance + the two matrices
    bool roughnessApart;  // roughness in an image of its own (unpacked) in place of the normals' alpha
    bool ownParameters;   // NGX parameters allocated for the feature, not the capability parameters DLSS shares
    bool evenSize;        // 1280x720 to 2560x1440 in place of the game's odd 1272x701 to 2544x1401
    bool level111;        // a DirectX 11 device at feature level 11_1 (the game's is 11_0)
    bool viaDll;          // through cg_ngx.dll's Direct3D 12 path, as the game runs it
    bool beside;          // with another NGX user on the same device (frame generation's Streamline in the game)
};

static const Variant VARIANTS[] = {
    {"d3d11-dlss", "DLSS without Ray Reconstruction on DirectX 11, as the game runs it", false, true, false, false, false, false, false},
    {"d3d11-game", "Ray Reconstruction on DirectX 11 with the game's inputs", false, false, true, false, false, false, false},
    {"d3d11-no-hit-distance", "the same without hit distance and matrices", false, false, false, false, false, false, false},
    {"d3d11-roughness-apart", "the same with roughness in an image of its own", false, false, true, true, false, false, false},
    {"d3d11-own-parameters", "the same with NGX parameters of its own", false, false, true, false, true, false, false},
    {"d3d11-even-size", "the same at 1280x720 to 2560x1440", false, false, true, false, false, true, false},
    {"d3d11-level-11-1", "the same on a feature level 11_1 device", false, false, true, false, false, false, true},
    {"d3d12-dlss", "DLSS without Ray Reconstruction on DirectX 12", true, true, false, false, false, false, false},
    {"d3d12-game", "Ray Reconstruction on DirectX 12 with the game's inputs", true, false, true, false, false, false, false},
    {"dll-d3d12-dlss", "DLSS through cg_ngx.dll on DirectX 12, as the game runs it", true, true, false, false, false, false, false, true},
    {"dll-d3d12-game", "Ray Reconstruction through cg_ngx.dll on DirectX 12, as the game runs it", true, false, true, false, false, false, false, true},
    {"dll-d3d12-beside", "the same, closed and opened again 3 times beside another NGX user's DLSS on the same device", true, false, true, false, false, false, false, true, true},
};

// ---------------------------------------------------------------------------------------------------------------
// One variant (the child process).

static void step(const char* format, ...) {
    va_list args;
    va_start(args, format);
    printf("step: ");
    vprintf(format, args);
    printf("\n");
    va_end(args);
    fflush(stdout);
}

static void NVSDK_CONV ngxLog(const char* message, NVSDK_NGX_Logging_Level, NVSDK_NGX_Feature) {
    size_t length = message ? strlen(message) : 0;
    while (length > 0 && (message[length - 1] == '\n' || message[length - 1] == '\r')) {
        length--;
    }
    printf("ngx: %.*s\n", (int) length, message ? message : "");
    fflush(stdout);
}

static LONG WINAPI crashed(EXCEPTION_POINTERS* info) {
    EXCEPTION_RECORD* record = info->ExceptionRecord;
    printf("crash: code 0x%08lX at %p", record->ExceptionCode, record->ExceptionAddress);
    if (record->ExceptionCode == EXCEPTION_ACCESS_VIOLATION && record->NumberParameters >= 2) {
        printf(", %s address %p", record->ExceptionInformation[0] == 0 ? "reading" : "writing",
               (void*) record->ExceptionInformation[1]);
    }
    HMODULE module = nullptr;
    char name[MAX_PATH] = "not in any loaded module";
    if (GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                           (LPCSTR) record->ExceptionAddress, &module)) {
        GetModuleFileNameA(module, name, sizeof(name));
    }
    printf(" (%s)\n", name);
    fflush(stdout);
    return EXCEPTION_EXECUTE_HANDLER;
}

static unsigned short half(float value) {
    unsigned int bits;
    memcpy(&bits, &value, 4);
    unsigned int sign = (bits >> 16) & 0x8000;
    int exponent = (int) ((bits >> 23) & 0xff) - 127 + 15;
    unsigned int mantissa = bits & 0x7fffff;
    if (exponent <= 0) return (unsigned short) sign;
    if (exponent >= 31) return (unsigned short) (sign | 0x7c00);
    return (unsigned short) (sign | (exponent << 10) | (mantissa >> 13));
}

static float fromHalf(unsigned short h) {
    unsigned int sign = (h & 0x8000u) << 16;
    unsigned int exponent = (h >> 10) & 0x1f;
    unsigned int mantissa = h & 0x3ff;
    unsigned int bits;
    if (exponent == 0) bits = sign; // (denormals as zero)
    else if (exponent == 31) bits = sign | 0x7f800000 | (mantissa << 13);
    else bits = sign | ((exponent - 15 + 127) << 23) | (mantissa << 13);
    float value;
    memcpy(&value, &bits, 4);
    return value;
}

// An input image, filled on the CPU with what the game's would roughly hold.
struct Image {
    const char* name;
    DXGI_FORMAT format;
    int width, height, pixelBytes;
    unsigned char* data;
};

static Image image(const char* name, DXGI_FORMAT format, int width, int height, int pixelBytes) {
    Image result{name, format, width, height, pixelBytes, (unsigned char*) calloc((size_t) width * height, (size_t) pixelBytes)};
    return result;
}

static void fillHalf4(Image& im, float r, float g, float b, float a, float noise) {
    unsigned short* p = (unsigned short*) im.data;
    for (int i = 0; i < im.width * im.height; i++) {
        unsigned int hash = (unsigned int) i * 2654435761u;
        float n = noise * ((float) (hash >> 8) / 16777216.0f - 0.5f);
        p[i * 4] = half(r + n);
        p[i * 4 + 1] = half(g + n);
        p[i * 4 + 2] = half(b + n);
        p[i * 4 + 3] = half(a);
    }
}

static void fillHalf2(Image& im, float x, float y) {
    unsigned short* p = (unsigned short*) im.data;
    for (int i = 0; i < im.width * im.height; i++) {
        p[i * 2] = half(x);
        p[i * 2 + 1] = half(y);
    }
}

static void fillHalf1(Image& im, float x) {
    unsigned short* p = (unsigned short*) im.data;
    for (int i = 0; i < im.width * im.height; i++) p[i] = half(x);
}

static void fillFloat(Image& im, float x) {
    float* p = (float*) im.data;
    for (int i = 0; i < im.width * im.height; i++) p[i] = x;
}

static void fillBytes4(Image& im, unsigned char r, unsigned char g, unsigned char b, unsigned char a) {
    unsigned char* p = im.data;
    for (int i = 0; i < im.width * im.height; i++) {
        p[i * 4] = r;
        p[i * 4 + 1] = g;
        p[i * 4 + 2] = b;
        p[i * 4 + 3] = a;
    }
}

struct Inputs {
    int renderWidth, renderHeight, outWidth, outHeight;
    Image color, depth, motion, diffuse, specular, normals, roughness, hitDistance;
    float worldToView[16];
    float viewToClip[16];
};

static Inputs makeInputs(const Variant& v) {
    int rw = v.evenSize ? 1280 : 1272, rh = v.evenSize ? 720 : 701;
    int ow = v.evenSize ? 2560 : 2544, oh = v.evenSize ? 1440 : 1401;
    Inputs in{rw, rh, ow, oh,
              image("colour", DXGI_FORMAT_R16G16B16A16_FLOAT, rw, rh, 8),
              image("depth", DXGI_FORMAT_R32_FLOAT, rw, rh, 4),
              image("motion vectors", DXGI_FORMAT_R16G16_FLOAT, rw, rh, 4),
              image("diffuse albedo", DXGI_FORMAT_R8G8B8A8_UNORM, rw, rh, 4),
              image("specular albedo", DXGI_FORMAT_R8G8B8A8_UNORM, rw, rh, 4),
              image("normals", DXGI_FORMAT_R16G16B16A16_FLOAT, rw, rh, 8),
              image("roughness", DXGI_FORMAT_R16_FLOAT, rw, rh, 2),
              image("hit distance", DXGI_FORMAT_R32_FLOAT, rw, rh, 4),
              {}, {}};
    fillHalf4(in.color, 0.5f, 0.45f, 0.4f, 1.0f, 0.2f);
    fillFloat(in.depth, 0.01f); // reversed depth: 5 blocks away at the game's 0.05 near plane
    fillHalf2(in.motion, 0.0f, 0.0f);
    fillBytes4(in.diffuse, 128, 128, 128, 255);
    fillBytes4(in.specular, 10, 10, 10, 255);
    fillHalf4(in.normals, 0.0f, 1.0f, 0.0f, 0.5f, 0.0f);
    fillHalf1(in.roughness, 0.5f);
    fillFloat(in.hitDistance, 3.0f);
    // Row major for a row vector on the left, as NGX reads them: identity view, an infinite reversed-depth projection
    // (70 degrees, the game's near plane) for a camera looking down -z, as OpenGL's.
    memset(in.worldToView, 0, sizeof(in.worldToView));
    for (int i = 0; i < 4; i++) in.worldToView[i * 5] = 1.0f;
    memset(in.viewToClip, 0, sizeof(in.viewToClip));
    float f = 1.0f / tanf(35.0f * 3.14159265f / 180.0f);
    in.viewToClip[0] = f * rh / rw;
    in.viewToClip[5] = f;
    in.viewToClip[11] = -1.0f;  // w = -z
    in.viewToClip[14] = 0.05f;  // z = near
    return in;
}

static void describeOutput(const unsigned char* rows, int width, int height, size_t rowPitch) {
    double sum = 0.0;
    long long bad = 0;
    for (int y = 0; y < height; y++) {
        const unsigned short* p = (const unsigned short*) (rows + rowPitch * y);
        for (int x = 0; x < width; x++) {
            float r = fromHalf(p[x * 4]);
            if (!isfinite(r)) bad++;
            else sum += r;
        }
    }
    printf("result: ran 3 frames; output red mean %.4f over %dx%d, %lld pixels not finite\n",
           sum / ((double) width * height), width, height, bad);
}

static void ngxInfo(NVSDK_NGX_FeatureCommonInfo& info, const wchar_t** paths) {
    memset(&info, 0, sizeof(info));
    info.PathListInfo.Path = paths;
    info.PathListInfo.Length = 1;
    info.LoggingInfo.LoggingCallback = ngxLog;
    info.LoggingInfo.MinimumLoggingLevel = NVSDK_NGX_LOGGING_LEVEL_ON;
    info.LoggingInfo.DisableOtherLoggingSinks = false;
}

static bool available(NVSDK_NGX_Parameter* caps, bool superResolution) {
    int ok = 0, initResult = 0;
    unsigned int needsDriver = 0, major = 0, minor = 0;
    NVSDK_NGX_Parameter_GetI(caps, superResolution ? NVSDK_NGX_Parameter_SuperSampling_Available
                                                   : NVSDK_NGX_Parameter_SuperSamplingDenoising_Available, &ok);
    NVSDK_NGX_Parameter_GetI(caps, superResolution ? NVSDK_NGX_Parameter_SuperSampling_FeatureInitResult
                                                   : NVSDK_NGX_Parameter_SuperSamplingDenoising_FeatureInitResult, &initResult);
    NVSDK_NGX_Parameter_GetUI(caps, superResolution ? NVSDK_NGX_Parameter_SuperSampling_NeedsUpdatedDriver
                                                    : NVSDK_NGX_Parameter_SuperSamplingDenoising_NeedsUpdatedDriver, &needsDriver);
    NVSDK_NGX_Parameter_GetUI(caps, superResolution ? NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMajor
                                                    : NVSDK_NGX_Parameter_SuperSamplingDenoising_MinDriverVersionMajor, &major);
    NVSDK_NGX_Parameter_GetUI(caps, superResolution ? NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMinor
                                                    : NVSDK_NGX_Parameter_SuperSamplingDenoising_MinDriverVersionMinor, &minor);
    printf("info: %s available %d, init result 0x%08X, needs a newer driver %u (minimum %u.%u)\n",
           superResolution ? "DLSS" : "Ray Reconstruction", ok, (unsigned) initResult, needsDriver, major, minor);
    return ok != 0;
}

static void presetHints(NVSDK_NGX_Parameter* params) {
    const char* hints[] = {NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_DLAA,
                           NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Quality,
                           NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Balanced,
                           NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_Performance,
                           NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_UltraPerformance,
                           NVSDK_NGX_Parameter_RayReconstruction_Hint_Render_Preset_UltraQuality};
    for (const char* hint : hints) NVSDK_NGX_Parameter_SetUI(params, hint, PRESET_F);
}

static NVSDK_NGX_DLSSD_Create_Params rrCreateParams(const Variant& v, const Inputs& in) {
    NVSDK_NGX_DLSSD_Create_Params create = {};
    create.InDenoiseMode = NVSDK_NGX_DLSS_Denoise_Mode_DLUnified;
    create.InRoughnessMode = v.roughnessApart ? NVSDK_NGX_DLSS_Roughness_Mode_Unpacked : NVSDK_NGX_DLSS_Roughness_Mode_Packed;
    create.InUseHWDepth = NVSDK_NGX_DLSS_Depth_Type_HW;
    create.InWidth = (unsigned int) in.renderWidth;
    create.InHeight = (unsigned int) in.renderHeight;
    create.InTargetWidth = (unsigned int) in.outWidth;
    create.InTargetHeight = (unsigned int) in.outHeight;
    create.InPerfQualityValue = NVSDK_NGX_PerfQuality_Value_MaxPerf;
    create.InFeatureCreateFlags = NVSDK_NGX_DLSS_Feature_Flags_IsHDR | NVSDK_NGX_DLSS_Feature_Flags_MVLowRes
                                  | NVSDK_NGX_DLSS_Feature_Flags_DepthInverted;
    create.InEnableOutputSubrects = false;
    return create;
}

static NVSDK_NGX_DLSS_Create_Params srCreateParams(const Inputs& in) {
    NVSDK_NGX_DLSS_Create_Params create = {};
    create.Feature.InWidth = (unsigned int) in.renderWidth;
    create.Feature.InHeight = (unsigned int) in.renderHeight;
    create.Feature.InTargetWidth = (unsigned int) in.outWidth;
    create.Feature.InTargetHeight = (unsigned int) in.outHeight;
    create.Feature.InPerfQualityValue = NVSDK_NGX_PerfQuality_Value_MaxPerf;
    create.InFeatureCreateFlags = NVSDK_NGX_DLSS_Feature_Flags_IsHDR | NVSDK_NGX_DLSS_Feature_Flags_MVLowRes
                                  | NVSDK_NGX_DLSS_Feature_Flags_DepthInverted | NVSDK_NGX_DLSS_Feature_Flags_AutoExposure;
    return create;
}

static IDXGIAdapter1* nvidiaAdapter() {
    IDXGIFactory1* factory = nullptr;
    if (FAILED(CreateDXGIFactory1(__uuidof(IDXGIFactory1), (void**) &factory))) return nullptr;
    IDXGIAdapter1* best = nullptr;
    SIZE_T bestMemory = 0;
    IDXGIAdapter1* adapter = nullptr;
    for (UINT i = 0; factory->EnumAdapters1(i, &adapter) != DXGI_ERROR_NOT_FOUND; i++) {
        DXGI_ADAPTER_DESC1 desc;
        adapter->GetDesc1(&desc);
        if (desc.VendorId == 0x10DE && !(desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) && desc.DedicatedVideoMemory > bestMemory) {
            if (best) best->Release();
            best = adapter;
            bestMemory = desc.DedicatedVideoMemory;
            printf("info: adapter %ls, %llu MB\n", desc.Description, (unsigned long long) (desc.DedicatedVideoMemory >> 20));
        } else {
            adapter->Release();
        }
    }
    factory->Release();
    return best;
}

static int fail(const char* what, long long code) {
    printf("result: failed at %s (0x%08X)\n", what, (unsigned int) code);
    return 1;
}

// DirectX 11: the game's own device and textures (shader resources; the output also unordered access).
static int runD3D11(const Variant& v, const wchar_t* dllFolder, const wchar_t* outFolder) {
    Inputs in = makeInputs(v);
    step("device");
    IDXGIAdapter1* adapter = nvidiaAdapter();
    if (!adapter) return fail("finding an NVIDIA adapter", 0);
    D3D_FEATURE_LEVEL levels[] = {D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0};
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    D3D_FEATURE_LEVEL level;
    HRESULT hr = D3D11CreateDevice(adapter, D3D_DRIVER_TYPE_UNKNOWN, nullptr, 0, v.level111 ? levels : nullptr,
                                   v.level111 ? 2 : 0, D3D11_SDK_VERSION, &device, &level, &context);
    adapter->Release();
    if (FAILED(hr)) return fail("D3D11CreateDevice", hr);
    printf("info: DirectX 11 feature level %x\n", (unsigned) level);

    step("NGX init");
    const wchar_t* paths[] = {dllFolder};
    NVSDK_NGX_FeatureCommonInfo info;
    ngxInfo(info, paths);
    NVSDK_NGX_Result result = NVSDK_NGX_D3D11_Init_with_ProjectID(PROJECT_ID, NVSDK_NGX_ENGINE_TYPE_CUSTOM, ENGINE_VERSION,
                                                                  outFolder, device, &info, NVSDK_NGX_Version_API);
    if (NVSDK_NGX_FAILED(result)) return fail("NVSDK_NGX_D3D11_Init_with_ProjectID", result);
    NVSDK_NGX_Parameter* caps = nullptr;
    result = NVSDK_NGX_D3D11_GetCapabilityParameters(&caps);
    if (NVSDK_NGX_FAILED(result)) return fail("NVSDK_NGX_D3D11_GetCapabilityParameters", result);
    if (!available(caps, v.superResolution)) return fail("the feature isn't available", 0);
    NVSDK_NGX_Parameter* params = caps;
    if (v.ownParameters) {
        result = NVSDK_NGX_D3D11_AllocateParameters(&params);
        if (NVSDK_NGX_FAILED(result)) return fail("NVSDK_NGX_D3D11_AllocateParameters", result);
    }

    step("textures");
    auto texture = [&](const Image& im, bool output) -> ID3D11Texture2D* {
        D3D11_TEXTURE2D_DESC desc = {};
        desc.Width = (UINT) im.width;
        desc.Height = (UINT) im.height;
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        desc.Format = im.format;
        desc.SampleDesc.Count = 1;
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE | (output ? D3D11_BIND_UNORDERED_ACCESS : 0);
        D3D11_SUBRESOURCE_DATA data = {im.data, (UINT) (im.width * im.pixelBytes), 0};
        ID3D11Texture2D* t = nullptr;
        HRESULT h = device->CreateTexture2D(&desc, output ? nullptr : &data, &t);
        if (FAILED(h)) printf("info: CreateTexture2D %s failed 0x%08lX\n", im.name, h);
        return t;
    };
    Image outImage = image("output", DXGI_FORMAT_R16G16B16A16_FLOAT, in.outWidth, in.outHeight, 8);
    ID3D11Texture2D* color = texture(in.color, false);
    ID3D11Texture2D* depth = texture(in.depth, false);
    ID3D11Texture2D* motion = texture(in.motion, false);
    ID3D11Texture2D* diffuse = texture(in.diffuse, false);
    ID3D11Texture2D* specular = texture(in.specular, false);
    ID3D11Texture2D* normals = texture(in.normals, false);
    ID3D11Texture2D* roughness = texture(in.roughness, false);
    ID3D11Texture2D* hitDistance = texture(in.hitDistance, false);
    ID3D11Texture2D* output = texture(outImage, true);
    if (!color || !depth || !motion || !diffuse || !specular || !normals || !roughness || !hitDistance || !output) {
        return fail("making the textures", 0);
    }

    NVSDK_NGX_Handle* feature = nullptr;
    if (v.superResolution) {
        step("create DLSS");
        NVSDK_NGX_DLSS_Create_Params create = srCreateParams(in);
        result = NGX_D3D11_CREATE_DLSS_EXT(context, &feature, params, &create);
    } else {
        step("create Ray Reconstruction, preset F");
        presetHints(params);
        NVSDK_NGX_DLSSD_Create_Params create = rrCreateParams(v, in);
        result = NGX_D3D11_CREATE_DLSSD_EXT(context, &feature, params, &create);
    }
    if (NVSDK_NGX_FAILED(result)) return fail("creating the feature", result);

    for (int frame = 0; frame < 3; frame++) {
        step("evaluate frame %d", frame + 1);
        float jitterX = 0.25f * (frame % 2 ? 1 : -1), jitterY = 0.25f * (frame / 2 ? 1 : -1);
        if (v.superResolution) {
            NVSDK_NGX_D3D11_DLSS_Eval_Params eval = {};
            eval.Feature.pInColor = color;
            eval.Feature.pInOutput = output;
            eval.pInDepth = depth;
            eval.pInMotionVectors = motion;
            eval.InJitterOffsetX = jitterX;
            eval.InJitterOffsetY = jitterY;
            eval.InMVScaleX = 1.0f;
            eval.InMVScaleY = 1.0f;
            eval.InReset = frame == 0;
            eval.InRenderSubrectDimensions.Width = (unsigned int) in.renderWidth;
            eval.InRenderSubrectDimensions.Height = (unsigned int) in.renderHeight;
            eval.InFrameTimeDeltaInMsec = 16.6f;
            result = NGX_D3D11_EVALUATE_DLSS_EXT(context, feature, params, &eval);
        } else {
            NVSDK_NGX_D3D11_DLSSD_Eval_Params eval = {};
            eval.pInColor = color;
            eval.pInOutput = output;
            eval.pInDepth = depth;
            eval.pInMotionVectors = motion;
            eval.pInDiffuseAlbedo = diffuse;
            eval.pInSpecularAlbedo = specular;
            eval.pInNormals = normals;
            eval.pInRoughness = v.roughnessApart ? roughness : nullptr;
            eval.pInSpecularHitDistance = v.hitDistance ? hitDistance : nullptr;
            eval.pInWorldToViewMatrix = v.hitDistance ? in.worldToView : nullptr;
            eval.pInViewToClipMatrix = v.hitDistance ? in.viewToClip : nullptr;
            eval.InJitterOffsetX = jitterX;
            eval.InJitterOffsetY = jitterY;
            eval.InMVScaleX = 1.0f;
            eval.InMVScaleY = 1.0f;
            eval.InFrameTimeDeltaInMsec = 16.6f;
            eval.InReset = frame == 0;
            eval.InRenderSubrectDimensions.Width = (unsigned int) in.renderWidth;
            eval.InRenderSubrectDimensions.Height = (unsigned int) in.renderHeight;
            result = NGX_D3D11_EVALUATE_DLSSD_EXT(context, feature, params, &eval);
        }
        if (NVSDK_NGX_FAILED(result)) return fail("evaluating", result);
        context->Flush();
    }

    step("read back");
    D3D11_TEXTURE2D_DESC desc;
    output->GetDesc(&desc);
    desc.BindFlags = 0;
    desc.Usage = D3D11_USAGE_STAGING;
    desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
    ID3D11Texture2D* staging = nullptr;
    if (FAILED(device->CreateTexture2D(&desc, nullptr, &staging))) return fail("making the read-back texture", 0);
    context->CopyResource(staging, output);
    D3D11_MAPPED_SUBRESOURCE mapped;
    hr = context->Map(staging, 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) return fail("reading the output back", hr);
    describeOutput((const unsigned char*) mapped.pData, in.outWidth, in.outHeight, mapped.RowPitch);
    context->Unmap(staging, 0);

    step("release");
    NVSDK_NGX_D3D11_ReleaseFeature(feature);
    if (v.ownParameters) NVSDK_NGX_D3D11_DestroyParameters(params);
    NVSDK_NGX_D3D11_Shutdown1(device);
    return 0;
}

// DirectX 12: the same inputs in committed textures, uploaded once, then left in the states NGX wants.
struct D3D12Run {
    ID3D12Device* device = nullptr;
    ID3D12CommandQueue* queue = nullptr;
    ID3D12CommandAllocator* allocator = nullptr;
    ID3D12GraphicsCommandList* list = nullptr;
    ID3D12Fence* fence = nullptr;
    HANDLE event = nullptr;
    UINT64 fenceValue = 0;

    bool submit() {
        if (FAILED(list->Close())) return false;
        ID3D12CommandList* lists[] = {list};
        queue->ExecuteCommandLists(1, lists);
        queue->Signal(fence, ++fenceValue);
        if (fence->GetCompletedValue() < fenceValue) {
            fence->SetEventOnCompletion(fenceValue, event);
            if (WaitForSingleObject(event, 20000) != WAIT_OBJECT_0) return false;
        }
        HRESULT removed = device->GetDeviceRemovedReason();
        if (FAILED(removed)) {
            printf("info: the device was removed (0x%08lX)\n", removed);
            return false;
        }
        return SUCCEEDED(allocator->Reset()) && SUCCEEDED(list->Reset(allocator, nullptr));
    }
};

static void barrier(ID3D12GraphicsCommandList* list, ID3D12Resource* resource, D3D12_RESOURCE_STATES from,
                    D3D12_RESOURCE_STATES to) {
    D3D12_RESOURCE_BARRIER b = {};
    b.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    b.Transition.pResource = resource;
    b.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    b.Transition.StateBefore = from;
    b.Transition.StateAfter = to;
    list->ResourceBarrier(1, &b);
}

static ID3D12Resource* buffer(ID3D12Device* device, UINT64 bytes, D3D12_HEAP_TYPE heap) {
    D3D12_HEAP_PROPERTIES props = {heap};
    D3D12_RESOURCE_DESC desc = {};
    desc.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
    desc.Width = bytes;
    desc.Height = 1;
    desc.DepthOrArraySize = 1;
    desc.MipLevels = 1;
    desc.SampleDesc.Count = 1;
    desc.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
    ID3D12Resource* r = nullptr;
    device->CreateCommittedResource(&props, D3D12_HEAP_FLAG_NONE, &desc,
                                    heap == D3D12_HEAP_TYPE_UPLOAD ? D3D12_RESOURCE_STATE_GENERIC_READ : D3D12_RESOURCE_STATE_COPY_DEST,
                                    nullptr, __uuidof(ID3D12Resource), (void**) &r);
    return r;
}

static int runD3D12(const Variant& v, const wchar_t* dllFolder, const wchar_t* outFolder) {
    Inputs in = makeInputs(v);
    D3D12Run run;
    step("device");
    IDXGIAdapter1* adapter = nvidiaAdapter();
    if (!adapter) return fail("finding an NVIDIA adapter", 0);
    HRESULT hr = D3D12CreateDevice(adapter, D3D_FEATURE_LEVEL_11_0, __uuidof(ID3D12Device), (void**) &run.device);
    adapter->Release();
    if (FAILED(hr)) return fail("D3D12CreateDevice", hr);
    D3D12_COMMAND_QUEUE_DESC queueDesc = {D3D12_COMMAND_LIST_TYPE_DIRECT};
    if (FAILED(run.device->CreateCommandQueue(&queueDesc, __uuidof(ID3D12CommandQueue), (void**) &run.queue))
        || FAILED(run.device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, __uuidof(ID3D12CommandAllocator),
                                                     (void**) &run.allocator))
        || FAILED(run.device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, run.allocator, nullptr,
                                                __uuidof(ID3D12GraphicsCommandList), (void**) &run.list))
        || FAILED(run.device->CreateFence(0, D3D12_FENCE_FLAG_NONE, __uuidof(ID3D12Fence), (void**) &run.fence))) {
        return fail("making the queue", 0);
    }
    run.event = CreateEventW(nullptr, FALSE, FALSE, nullptr);

    step("NGX init");
    const wchar_t* paths[] = {dllFolder};
    NVSDK_NGX_FeatureCommonInfo info;
    ngxInfo(info, paths);
    NVSDK_NGX_Result result = NVSDK_NGX_D3D12_Init_with_ProjectID(PROJECT_ID, NVSDK_NGX_ENGINE_TYPE_CUSTOM, ENGINE_VERSION,
                                                                  outFolder, run.device, &info, NVSDK_NGX_Version_API);
    if (NVSDK_NGX_FAILED(result)) return fail("NVSDK_NGX_D3D12_Init_with_ProjectID", result);
    NVSDK_NGX_Parameter* params = nullptr;
    result = NVSDK_NGX_D3D12_GetCapabilityParameters(&params);
    if (NVSDK_NGX_FAILED(result)) return fail("NVSDK_NGX_D3D12_GetCapabilityParameters", result);
    if (!available(params, v.superResolution)) return fail("the feature isn't available", 0);

    step("textures");
    auto texture = [&](const Image& im, bool output) -> ID3D12Resource* {
        D3D12_HEAP_PROPERTIES props = {D3D12_HEAP_TYPE_DEFAULT};
        D3D12_RESOURCE_DESC desc = {};
        desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
        desc.Width = (UINT64) im.width;
        desc.Height = (UINT) im.height;
        desc.DepthOrArraySize = 1;
        desc.MipLevels = 1;
        desc.Format = im.format;
        desc.SampleDesc.Count = 1;
        desc.Flags = output ? D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS : D3D12_RESOURCE_FLAG_NONE;
        ID3D12Resource* t = nullptr;
        HRESULT h = run.device->CreateCommittedResource(&props, D3D12_HEAP_FLAG_NONE, &desc,
                                                        output ? D3D12_RESOURCE_STATE_UNORDERED_ACCESS : D3D12_RESOURCE_STATE_COPY_DEST,
                                                        nullptr, __uuidof(ID3D12Resource), (void**) &t);
        if (FAILED(h)) {
            printf("info: CreateCommittedResource %s failed 0x%08lX\n", im.name, h);
            return nullptr;
        }
        if (output) return t;
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
        UINT64 total = 0;
        run.device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
        ID3D12Resource* upload = buffer(run.device, total, D3D12_HEAP_TYPE_UPLOAD);
        if (!upload) return nullptr;
        // (The upload buffers live until the process ends.)
        unsigned char* mapped = nullptr;
        upload->Map(0, nullptr, (void**) &mapped);
        for (int y = 0; y < im.height; y++) {
            memcpy(mapped + footprint.Offset + (size_t) footprint.Footprint.RowPitch * y,
                   im.data + (size_t) im.width * im.pixelBytes * y, (size_t) im.width * im.pixelBytes);
        }
        upload->Unmap(0, nullptr);
        D3D12_TEXTURE_COPY_LOCATION dst = {t, D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX};
        dst.SubresourceIndex = 0;
        D3D12_TEXTURE_COPY_LOCATION src = {upload, D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT};
        src.PlacedFootprint = footprint;
        run.list->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
        barrier(run.list, t, D3D12_RESOURCE_STATE_COPY_DEST,
                D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE | D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
        return t;
    };
    Image outImage = image("output", DXGI_FORMAT_R16G16B16A16_FLOAT, in.outWidth, in.outHeight, 8);
    ID3D12Resource* color = texture(in.color, false);
    ID3D12Resource* depth = texture(in.depth, false);
    ID3D12Resource* motion = texture(in.motion, false);
    ID3D12Resource* diffuse = texture(in.diffuse, false);
    ID3D12Resource* specular = texture(in.specular, false);
    ID3D12Resource* normals = texture(in.normals, false);
    ID3D12Resource* hitDistance = texture(in.hitDistance, false);
    ID3D12Resource* output = texture(outImage, true);
    if (!color || !depth || !motion || !diffuse || !specular || !normals || !hitDistance || !output) {
        return fail("making the textures", 0);
    }
    if (!run.submit()) return fail("uploading the textures", 0);

    NVSDK_NGX_Handle* feature = nullptr;
    if (v.superResolution) {
        step("create DLSS");
        NVSDK_NGX_DLSS_Create_Params create = srCreateParams(in);
        result = NGX_D3D12_CREATE_DLSS_EXT(run.list, 1, 1, &feature, params, &create);
    } else {
        step("create Ray Reconstruction, preset F");
        presetHints(params);
        NVSDK_NGX_DLSSD_Create_Params create = rrCreateParams(v, in);
        result = NGX_D3D12_CREATE_DLSSD_EXT(run.list, 1, 1, &feature, params, &create);
    }
    if (NVSDK_NGX_FAILED(result)) return fail("creating the feature", result);
    if (!run.submit()) return fail("running the feature's creation", 0);

    for (int frame = 0; frame < 3; frame++) {
        step("evaluate frame %d", frame + 1);
        float jitterX = 0.25f * (frame % 2 ? 1 : -1), jitterY = 0.25f * (frame / 2 ? 1 : -1);
        if (v.superResolution) {
            NVSDK_NGX_D3D12_DLSS_Eval_Params eval = {};
            eval.Feature.pInColor = color;
            eval.Feature.pInOutput = output;
            eval.pInDepth = depth;
            eval.pInMotionVectors = motion;
            eval.InJitterOffsetX = jitterX;
            eval.InJitterOffsetY = jitterY;
            eval.InMVScaleX = 1.0f;
            eval.InMVScaleY = 1.0f;
            eval.InReset = frame == 0;
            eval.InRenderSubrectDimensions.Width = (unsigned int) in.renderWidth;
            eval.InRenderSubrectDimensions.Height = (unsigned int) in.renderHeight;
            eval.InFrameTimeDeltaInMsec = 16.6f;
            result = NGX_D3D12_EVALUATE_DLSS_EXT(run.list, feature, params, &eval);
        } else {
            NVSDK_NGX_D3D12_DLSSD_Eval_Params eval = {};
            eval.pInColor = color;
            eval.pInOutput = output;
            eval.pInDepth = depth;
            eval.pInMotionVectors = motion;
            eval.pInDiffuseAlbedo = diffuse;
            eval.pInSpecularAlbedo = specular;
            eval.pInNormals = normals;
            eval.pInSpecularHitDistance = v.hitDistance ? hitDistance : nullptr;
            eval.pInWorldToViewMatrix = v.hitDistance ? in.worldToView : nullptr;
            eval.pInViewToClipMatrix = v.hitDistance ? in.viewToClip : nullptr;
            eval.InJitterOffsetX = jitterX;
            eval.InJitterOffsetY = jitterY;
            eval.InMVScaleX = 1.0f;
            eval.InMVScaleY = 1.0f;
            eval.InFrameTimeDeltaInMsec = 16.6f;
            eval.InReset = frame == 0;
            eval.InRenderSubrectDimensions.Width = (unsigned int) in.renderWidth;
            eval.InRenderSubrectDimensions.Height = (unsigned int) in.renderHeight;
            result = NGX_D3D12_EVALUATE_DLSSD_EXT(run.list, feature, params, &eval);
        }
        if (NVSDK_NGX_FAILED(result)) return fail("evaluating", result);
        if (!run.submit()) return fail("running the evaluate", 0);
    }

    step("read back");
    D3D12_RESOURCE_DESC desc = output->GetDesc();
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
    UINT64 total = 0;
    run.device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
    ID3D12Resource* readback = buffer(run.device, total, D3D12_HEAP_TYPE_READBACK);
    if (!readback) return fail("making the read-back buffer", 0);
    barrier(run.list, output, D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_STATE_COPY_SOURCE);
    D3D12_TEXTURE_COPY_LOCATION dst = {readback, D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT};
    dst.PlacedFootprint = footprint;
    D3D12_TEXTURE_COPY_LOCATION src = {output, D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX};
    src.SubresourceIndex = 0;
    run.list->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
    if (!run.submit()) return fail("copying the output back", 0);
    unsigned char* mapped = nullptr;
    D3D12_RANGE range = {0, (SIZE_T) total};
    if (FAILED(readback->Map(0, &range, (void**) &mapped))) return fail("reading the output back", 0);
    describeOutput(mapped + footprint.Offset, in.outWidth, in.outHeight, footprint.Footprint.RowPitch);
    readback->Unmap(0, nullptr);

    step("release");
    NVSDK_NGX_D3D12_ReleaseFeature(feature);
    NVSDK_NGX_D3D12_Shutdown1(run.device);
    return 0;
}

// cg_ngx.dll's Direct3D 12 path, called as the Bridge calls it, with this program standing in for OpenGL: it fills the
// shared textures on a queue of its own and signals "OpenGL done" (the game's semaphore), then reads the output back
// once the DLL's queue has signalled "DirectX done".
typedef ID3D12Device* (*Open12)(const LUID*);
typedef const char* (*Text12)();
typedef ID3D12Fence* (*Fence12)(int);
typedef ID3D12Resource* (*CreateTexture12)(int, int, int, int, HANDLE*, UINT64*);
typedef void (*ReleaseTexture12)(ID3D12Resource*, HANDLE);
typedef int (*WaitIdle12)(int);
typedef void (*Close12)();
typedef int (*Init12)(const char*, const char*, const wchar_t*, const wchar_t*, int);
typedef int (*Query)(int*);
typedef int (*Create)(void*, int, int, int, int, int, int, int);
typedef int (*CreateRr)(void*, int, int, int, int, int, int, int, int, int);
typedef int (*Evaluate12)(ID3D12Resource**, const float*, int, int, int, UINT64, UINT64*);
typedef int (*Plain)();
typedef int (*LogNext)(char*, int);
typedef int (*Abi)();

static LogNext g_dllLog;

static void dllLog() {
    char line[1024];
    while (g_dllLog && g_dllLog(line, sizeof(line)) >= 0) {
        printf("ngx: %s\n", line);
    }
    fflush(stdout);
}

static int runViaDll(const Variant& v, const wchar_t* dllFolder, const wchar_t* outFolder) {
    Inputs in = makeInputs(v);
    step("load cg_ngx.dll");
    wchar_t path[MAX_PATH];
    swprintf(path, MAX_PATH, L"%ls\\cg_ngx.dll", dllFolder);
    HMODULE dll = LoadLibraryW(path);
    if (!dll) return fail("loading cg_ngx.dll from the DLL folder", GetLastError());
#define SYMBOL(type, name) type name = (type) (void*) GetProcAddress(dll, "cg_" #name); if (!name) return fail("finding cg_" #name, 0)
    SYMBOL(Abi, ngx_abi);
    SYMBOL(Open12, ngx12_open);
    SYMBOL(Text12, ngx12_error);
    SYMBOL(Text12, ngx12_adapter_name);
    SYMBOL(Fence12, ngx12_fence);
    SYMBOL(CreateTexture12, ngx12_create_texture);
    SYMBOL(ReleaseTexture12, ngx12_release_texture);
    SYMBOL(WaitIdle12, ngx12_wait_idle);
    SYMBOL(Close12, ngx12_close);
    SYMBOL(Init12, ngx_init12);
    SYMBOL(Query, ngx_query);
    SYMBOL(Query, ngx_query_rr);
    SYMBOL(Create, ngx_create);
    SYMBOL(CreateRr, ngx_create_rr);
    SYMBOL(Evaluate12, ngx12_evaluate);
    SYMBOL(Evaluate12, ngx12_evaluate_rr);
    SYMBOL(Plain, ngx_release);
    SYMBOL(Plain, ngx_shutdown);
    SYMBOL(LogNext, ngx_log_next);
#undef SYMBOL
    g_dllLog = ngx_log_next;
    printf("info: cg_ngx.dll ABI %d\n", ngx_abi());

    step("device");
    ID3D12Device* device = ngx12_open(nullptr);
    if (!device) {
        printf("info: %s\n", ngx12_error());
        return fail("cg_ngx12_open", 0);
    }
    printf("info: adapter %s\n", ngx12_adapter_name());
    // "OpenGL's" own queue on the same device (Direct3D 12 gives one device per adapter and process).
    D3D12Run run;
    run.device = device;
    D3D12_COMMAND_QUEUE_DESC queueDesc = {D3D12_COMMAND_LIST_TYPE_DIRECT};
    if (FAILED(device->CreateCommandQueue(&queueDesc, __uuidof(ID3D12CommandQueue), (void**) &run.queue))
        || FAILED(device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, __uuidof(ID3D12CommandAllocator),
                                                 (void**) &run.allocator))
        || FAILED(device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, run.allocator, nullptr,
                                            __uuidof(ID3D12GraphicsCommandList), (void**) &run.list))
        || FAILED(device->CreateFence(0, D3D12_FENCE_FLAG_NONE, __uuidof(ID3D12Fence), (void**) &run.fence))) {
        return fail("making the stand-in queue", 0);
    }
    run.event = CreateEventW(nullptr, FALSE, FALSE, nullptr);

    // beside: this program also stands in for Streamline, NGX's other user in the game. It starts NGX on the same device
    // through its own copy of NVIDIA's SDK library, which finds the same NGX core in the driver, as Streamline's does,
    // and runs plain DLSS there (DLSS Frame Generation in the game). That feature must keep running while cg_ngx.dll's
    // closes and opens again.
    NVSDK_NGX_Parameter* otherParams = nullptr;
    if (v.beside) {
        device->AddRef(); // the other user's own reference, as the presenter holds one
        step("the other NGX user: init");
        const wchar_t* paths[] = {dllFolder};
        NVSDK_NGX_FeatureCommonInfo info;
        ngxInfo(info, paths);
        NVSDK_NGX_Result other = NVSDK_NGX_D3D12_Init_with_ProjectID(PROJECT_ID, NVSDK_NGX_ENGINE_TYPE_CUSTOM, ENGINE_VERSION,
                                                                     outFolder, device, &info, NVSDK_NGX_Version_API);
        if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's NVSDK_NGX_D3D12_Init_with_ProjectID", other);
        other = NVSDK_NGX_D3D12_GetCapabilityParameters(&otherParams);
        if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's NVSDK_NGX_D3D12_GetCapabilityParameters", other);
        if (!available(otherParams, true)) return fail("DLSS isn't available to the other NGX user", 0);
    }

    step("NGX init");
    int result = ngx_init12(PROJECT_ID, ENGINE_VERSION, outFolder, dllFolder, 1);
    dllLog();
    if (NVSDK_NGX_FAILED(result)) return fail("cg_ngx_init12", result);
    int support[5] = {};
    (v.superResolution ? ngx_query : ngx_query_rr)(support);
    printf("info: %s available %d, init result 0x%08X, needs a newer driver %d (minimum %d.%d)\n",
           v.superResolution ? "DLSS" : "Ray Reconstruction", support[0], (unsigned) support[4], support[1], support[2], support[3]);
    if (!support[0]) return fail("the feature isn't available", 0);

    step("shared textures");
    Image outImage = image("output", DXGI_FORMAT_R16G16B16A16_FLOAT, in.outWidth, in.outHeight, 8);
    Image exposure = image("exposure", DXGI_FORMAT_R32_FLOAT, 1, 1, 4);
    fillFloat(exposure, 1.0f);
    // In the order cg_ngx12_evaluate_rr takes them; the exposure only for DLSS (cg_ngx12_evaluate's fifth).
    const Image* images[] = {&in.color, &outImage, &in.depth, &in.motion, &in.diffuse, &in.specular, &in.normals,
                             &in.hitDistance, &exposure};
    ID3D12Resource* textures[9] = {};
    HANDLE handles[9] = {};
    for (int i = 0; i < 9; i++) {
        UINT64 size = 0;
        textures[i] = ngx12_create_texture(images[i]->width, images[i]->height, images[i]->format, i == 1, &handles[i], &size);
        if (!textures[i]) {
            printf("info: %s: %s\n", images[i]->name, ngx12_error());
            return fail("cg_ngx12_create_texture", 0);
        }
        if (i == 1) continue;
        D3D12_RESOURCE_DESC desc = textures[i]->GetDesc();
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
        UINT64 total = 0;
        device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
        ID3D12Resource* upload = buffer(device, total, D3D12_HEAP_TYPE_UPLOAD);
        if (!upload) return fail("making an upload buffer", 0);
        unsigned char* mapped = nullptr;
        upload->Map(0, nullptr, (void**) &mapped);
        for (int y = 0; y < images[i]->height; y++) {
            memcpy(mapped + footprint.Offset + (size_t) footprint.Footprint.RowPitch * y,
                   images[i]->data + (size_t) images[i]->width * images[i]->pixelBytes * y,
                   (size_t) images[i]->width * images[i]->pixelBytes);
        }
        upload->Unmap(0, nullptr);
        barrier(run.list, textures[i], D3D12_RESOURCE_STATE_COMMON, D3D12_RESOURCE_STATE_COPY_DEST);
        D3D12_TEXTURE_COPY_LOCATION dst = {textures[i], D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX};
        dst.SubresourceIndex = 0;
        D3D12_TEXTURE_COPY_LOCATION src = {upload, D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT};
        src.PlacedFootprint = footprint;
        run.list->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
        barrier(run.list, textures[i], D3D12_RESOURCE_STATE_COPY_DEST, D3D12_RESOURCE_STATE_COMMON);
    }
    if (!run.submit()) return fail("filling the textures", 0);

    // The other user's DLSS, on this program's queue, from the same textures (COMMON between runs, as everywhere here).
    NVSDK_NGX_Handle* otherFeature = nullptr;
    auto otherCreate = [&]() -> NVSDK_NGX_Result {
        NVSDK_NGX_DLSS_Create_Params create = srCreateParams(in);
        NVSDK_NGX_Result r = NGX_D3D12_CREATE_DLSS_EXT(run.list, 1, 1, &otherFeature, otherParams, &create);
        return run.submit() || NVSDK_NGX_FAILED(r) ? r : NVSDK_NGX_Result_Fail;
    };
    auto otherEvaluate = [&](bool reset) -> NVSDK_NGX_Result {
        const D3D12_RESOURCE_STATES read = D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE | D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE;
        ID3D12Resource* inputs[] = {textures[0], textures[2], textures[3]};
        for (ID3D12Resource* t : inputs) barrier(run.list, t, D3D12_RESOURCE_STATE_COMMON, read);
        barrier(run.list, textures[1], D3D12_RESOURCE_STATE_COMMON, D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        NVSDK_NGX_D3D12_DLSS_Eval_Params eval = {};
        eval.Feature.pInColor = textures[0];
        eval.Feature.pInOutput = textures[1];
        eval.pInDepth = textures[2];
        eval.pInMotionVectors = textures[3];
        eval.InMVScaleX = 1.0f;
        eval.InMVScaleY = 1.0f;
        eval.InReset = reset;
        eval.InRenderSubrectDimensions.Width = (unsigned int) in.renderWidth;
        eval.InRenderSubrectDimensions.Height = (unsigned int) in.renderHeight;
        eval.InFrameTimeDeltaInMsec = 16.6f;
        NVSDK_NGX_Result r = NGX_D3D12_EVALUATE_DLSS_EXT(run.list, otherFeature, otherParams, &eval);
        for (ID3D12Resource* t : inputs) barrier(run.list, t, read, D3D12_RESOURCE_STATE_COMMON);
        barrier(run.list, textures[1], D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_STATE_COMMON);
        return run.submit() || NVSDK_NGX_FAILED(r) ? r : NVSDK_NGX_Result_Fail;
    };
    if (v.beside) {
        step("the other NGX user: create DLSS and evaluate");
        NVSDK_NGX_Result other = otherCreate();
        if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's create", other);
        other = otherEvaluate(true);
        if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's evaluate", other);
    }

    const int flags = NVSDK_NGX_DLSS_Feature_Flags_IsHDR | NVSDK_NGX_DLSS_Feature_Flags_MVLowRes
                      | NVSDK_NGX_DLSS_Feature_Flags_DepthInverted;
    const int rounds = v.beside ? 3 : 1;
    for (int round = 1; round <= rounds; round++) {
        if (round > 1) {
            step("round %d: cg_ngx.dll opens again", round);
            if (ngx12_open(nullptr) != device) return fail("cg_ngx12_open gave another device", 0);
            result = ngx_init12(PROJECT_ID, ENGINE_VERSION, outFolder, dllFolder, 1);
            dllLog();
            if (NVSDK_NGX_FAILED(result)) return fail("cg_ngx_init12 again", result);
        }
        ID3D12Fence* openglDone = ngx12_fence(0);
        if (v.superResolution) {
            step("create DLSS, preset K");
            result = ngx_create(nullptr, in.renderWidth, in.renderHeight, in.outWidth, in.outHeight,
                                NVSDK_NGX_PerfQuality_Value_MaxPerf, flags, 11);
        } else {
            step("create Ray Reconstruction, preset F");
            result = ngx_create_rr(nullptr, in.renderWidth, in.renderHeight, in.outWidth, in.outHeight,
                                   NVSDK_NGX_PerfQuality_Value_MaxPerf, flags, (int) PRESET_F, 1, 0);
        }
        dllLog();
        if (NVSDK_NGX_FAILED(result)) {
            printf("info: %s\n", ngx12_error());
            return fail("creating the feature", result);
        }

        for (int frame = 0; frame < 3; frame++) {
            step("evaluate frame %d", frame + 1);
            // OpenGL's copies are done: "OpenGL done" reaches this frame's value (on the GPU, after the fill).
            UINT64 glValue = (UINT64) frame + 1;
            run.queue->Signal(openglDone, glValue);
            float floats[37] = {};
            floats[0] = 0.25f * (frame % 2 ? 1 : -1);
            floats[1] = 0.25f * (frame / 2 ? 1 : -1);
            floats[2] = 1.0f;
            floats[3] = 1.0f;
            floats[4] = 16.6f;
            UINT64 dxValue = 0;
            if (v.superResolution) {
                floats[5] = 1.0f; // pre-exposure
                ID3D12Resource* srResources[] = {textures[0], textures[1], textures[2], textures[3], textures[8]};
                result = ngx12_evaluate(srResources, floats, frame == 0, in.renderWidth, in.renderHeight, glValue, &dxValue);
            } else {
                memcpy(floats + 5, in.worldToView, sizeof(in.worldToView));
                memcpy(floats + 21, in.viewToClip, sizeof(in.viewToClip));
                result = ngx12_evaluate_rr(textures, floats, frame == 0, in.renderWidth, in.renderHeight, glValue, &dxValue);
            }
            dllLog();
            if (NVSDK_NGX_FAILED(result)) {
                printf("info: %s\n", ngx12_error());
                return fail("evaluating", result);
            }
            if (!dxValue) return fail("the evaluate queued nothing", 0);
            // OpenGL waits for "DirectX done" before it reads the output.
            run.queue->Wait(ngx12_fence(1), dxValue);
        }
        if (!ngx12_wait_idle(20000)) return fail("waiting for the DLL's queue", 0);

        step("read back");
        D3D12_RESOURCE_DESC desc = textures[1]->GetDesc();
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint;
        UINT64 total = 0;
        device->GetCopyableFootprints(&desc, 0, 1, 0, &footprint, nullptr, nullptr, &total);
        ID3D12Resource* readback = buffer(device, total, D3D12_HEAP_TYPE_READBACK);
        if (!readback) return fail("making the read-back buffer", 0);
        barrier(run.list, textures[1], D3D12_RESOURCE_STATE_COMMON, D3D12_RESOURCE_STATE_COPY_SOURCE);
        D3D12_TEXTURE_COPY_LOCATION dst = {readback, D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT};
        dst.PlacedFootprint = footprint;
        D3D12_TEXTURE_COPY_LOCATION src = {textures[1], D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX};
        src.SubresourceIndex = 0;
        run.list->CopyTextureRegion(&dst, 0, 0, 0, &src, nullptr);
        barrier(run.list, textures[1], D3D12_RESOURCE_STATE_COPY_SOURCE, D3D12_RESOURCE_STATE_COMMON);
        if (!run.submit()) return fail("copying the output back", 0);
        unsigned char* mapped = nullptr;
        D3D12_RANGE range = {0, (SIZE_T) total};
        if (FAILED(readback->Map(0, &range, (void**) &mapped))) return fail("reading the output back", 0);
        describeOutput(mapped + footprint.Offset, in.outWidth, in.outHeight, footprint.Footprint.RowPitch);
        readback->Unmap(0, nullptr);
        readback->Release();

        if (v.beside) {
            // Ray Reconstruction off in the game: the Bridge releases, shuts down and closes; the other user goes on.
            step("round %d: cg_ngx.dll closes", round);
            ngx_release();
            ngx_shutdown();
            dllLog();
            ngx12_close();
            step("round %d: the other NGX user evaluates its DLSS", round);
            NVSDK_NGX_Result other = otherEvaluate(false);
            if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's evaluate after cg_ngx.dll closed", other);
            step("round %d: the other NGX user makes its DLSS again", round);
            NVSDK_NGX_D3D12_ReleaseFeature(otherFeature);
            otherFeature = nullptr;
            other = otherCreate();
            if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's create after cg_ngx.dll closed", other);
            other = otherEvaluate(true);
            if (NVSDK_NGX_FAILED(other)) return fail("the other NGX user's evaluate of its new DLSS", other);
            printf("info: round %d: the other NGX user's DLSS ran on after cg_ngx.dll closed, and was made again\n", round);
        }
    }

    step("release");
    if (v.beside) {
        // The other user ends last, as Streamline does when the game closes.
        NVSDK_NGX_D3D12_ReleaseFeature(otherFeature);
        NVSDK_NGX_D3D12_Shutdown1(device);
    } else {
        ngx_release();
        ngx_shutdown();
        dllLog();
    }
    for (int i = 0; i < 9; i++) ngx12_release_texture(textures[i], handles[i]);
    run.list->Release();
    run.allocator->Release();
    run.queue->Release();
    run.fence->Release();
    if (v.beside) {
        device->Release();
    } else {
        ngx12_close();
    }
    return 0;
}

static int runVariant(const Variant& v, const wchar_t* dllFolder, const wchar_t* outFolder) {
    SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
    SetUnhandledExceptionFilter(crashed);
    printf("variant: %s (%s)\n", v.name, v.what);
    fflush(stdout);
    return v.viaDll ? runViaDll(v, dllFolder, outFolder) : v.d3d12 ? runD3D12(v, dllFolder, outFolder) : runD3D11(v, dllFolder, outFolder);
}

// ---------------------------------------------------------------------------------------------------------------
// Every variant, each in its own process (the parent).

static void summarize(FILE* summary, const char* format, ...) {
    va_list args;
    va_start(args, format);
    vprintf(format, args);
    va_end(args);
    va_start(args, format);
    vfprintf(summary, format, args);
    va_end(args);
}

static int runAll(const wchar_t* exe, const wchar_t* dllFolder, const wchar_t* outFolder) {
    CreateDirectoryW(outFolder, nullptr);
    wchar_t summaryPath[MAX_PATH];
    swprintf(summaryPath, MAX_PATH, L"%ls\\summary.txt", outFolder);
    FILE* summary = _wfsopen(summaryPath, L"w", _SH_DENYNO);
    if (!summary) {
        printf("Can't write %ls\n", summaryPath);
        return 2;
    }
    summarize(summary, "rr-probe: DLSS Ray Reconstruction alone, nvngx DLLs from %ls\n", dllFolder);
    int crashes = 0;
    for (const Variant& v : VARIANTS) {
        wchar_t logPath[MAX_PATH];
        swprintf(logPath, MAX_PATH, L"%ls\\%hs.txt", outFolder, v.name);
        SECURITY_ATTRIBUTES inherit = {sizeof(inherit), nullptr, TRUE};
        HANDLE log = CreateFileW(logPath, GENERIC_WRITE, FILE_SHARE_READ, &inherit, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (log == INVALID_HANDLE_VALUE) {
            summarize(summary, "%-24s can't write its log\n", v.name);
            continue;
        }
        wchar_t command[4 * MAX_PATH];
        swprintf(command, 4 * MAX_PATH, L"\"%ls\" \"%ls\" \"%ls\" --variant %hs", exe, dllFolder, outFolder, v.name);
        STARTUPINFOW startup = {sizeof(startup)};
        startup.dwFlags = STARTF_USESTDHANDLES;
        startup.hStdOutput = log;
        startup.hStdError = log;
        startup.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
        PROCESS_INFORMATION process = {};
        if (!CreateProcessW(exe, command, nullptr, nullptr, TRUE, 0, nullptr, nullptr, &startup, &process)) {
            CloseHandle(log);
            summarize(summary, "%-24s couldn't start (error %lu)\n", v.name, GetLastError());
            continue;
        }
        DWORD waited = WaitForSingleObject(process.hProcess, 120000);
        DWORD code = 0;
        if (waited == WAIT_TIMEOUT) {
            TerminateProcess(process.hProcess, 0xDEAD);
            WaitForSingleObject(process.hProcess, 5000);
            code = 0xDEAD;
        } else {
            GetExitCodeProcess(process.hProcess, &code);
        }
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
        CloseHandle(log);

        // The child's last step, its result and its crash line, if any.
        char lastStep[512] = "", result[512] = "", crash[512] = "";
        FILE* lines = _wfsopen(logPath, L"r", _SH_DENYNO);
        if (lines) {
            char line[2048];
            while (fgets(line, sizeof(line), lines)) {
                line[strcspn(line, "\r\n")] = 0;
                if (!strncmp(line, "step: ", 6)) snprintf(lastStep, sizeof(lastStep), "%s", line + 6);
                else if (!strncmp(line, "result: ", 8)) snprintf(result, sizeof(result), "%s", line + 8);
                else if (!strncmp(line, "crash: ", 7)) snprintf(crash, sizeof(crash), "%s", line + 7);
            }
            fclose(lines);
        }
        if (code == 0xDEAD) {
            summarize(summary, "%-24s HUNG (stopped after 2 minutes) at: %s\n", v.name, lastStep);
        } else if (crash[0] || code >= 0xC0000000) {
            crashes++;
            summarize(summary, "%-24s CRASHED at: %s; %s\n", v.name, lastStep, crash[0] ? crash : "no crash line");
        } else if (code == 0) {
            summarize(summary, "%-24s OK: %s\n", v.name, result);
        } else {
            summarize(summary, "%-24s FAILED: %s\n", v.name, result[0] ? result : "no result line");
        }
        fflush(summary);
    }
    fclose(summary);
    printf("Each variant's own lines: %ls\\<variant>.txt\n", outFolder);
    return crashes ? 1 : 0;
}

int wmain(int argc, wchar_t** argv) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    if (argc == 5 && !wcscmp(argv[3], L"--variant")) {
        if (!wcscmp(argv[4], L"self-test-crash")) { // what a crash looks like in a variant's lines
            SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
            SetUnhandledExceptionFilter(crashed);
            step("reading through a null pointer");
            volatile int* nothing = (volatile int*) (UINT_PTR) 0x34;
            return *nothing;
        }
        for (const Variant& v : VARIANTS) {
            wchar_t name[64];
            swprintf(name, 64, L"%hs", v.name);
            if (!wcscmp(name, argv[4])) return runVariant(v, argv[1], argv[2]);
        }
        printf("No variant %ls\n", argv[4]);
        return 2;
    }
    if (argc != 3) {
        printf("Usage: rr-probe.exe <folder with nvngx_dlssd.dll and nvngx_dlss.dll (and cg_ngx.dll)> <output folder>\n");
        return 2;
    }
    wchar_t exe[MAX_PATH];
    GetModuleFileNameW(nullptr, exe, MAX_PATH);
    wchar_t dllFolder[MAX_PATH], outFolder[MAX_PATH];
    GetFullPathNameW(argv[1], MAX_PATH, dllFolder, nullptr);
    GetFullPathNameW(argv[2], MAX_PATH, outFolder, nullptr);
    return runAll(exe, dllFolder, outFolder);
}

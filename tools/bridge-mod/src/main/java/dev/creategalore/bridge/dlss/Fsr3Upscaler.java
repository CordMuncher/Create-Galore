package dev.creategalore.bridge.dlss;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_ACTIVE_UNIFORMS;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.GL_SAMPLER_2D;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetActiveUniform;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_R16F;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_R8;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.GL_RG16F;
import static org.lwjgl.opengl.GL30C.GL_RGBA16F;
import static org.lwjgl.opengl.GL30C.GL_RGBA32F;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_SAMPLER_2D;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31C.GL_MAX_UNIFORM_BUFFER_BINDINGS;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_BINDING;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_SIZE;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_START;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_OFFSET;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BLOCK_DATA_SIZE;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformBlocki;
import static org.lwjgl.opengl.GL31C.glGetActiveUniformsi;
import static org.lwjgl.opengl.GL31C.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31C.glGetUniformIndices;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL32C.glGetInteger64i;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_2D;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_ACCESS;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_FORMAT;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYER;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYERED;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LEVEL;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_NAME;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_UNSIGNED_INT_IMAGE_2D;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_MAX_COMPUTE_IMAGE_UNIFORMS;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL44C.glClearTexImage;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glCreateSamplers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

/**
 * AMD FSR 3.1's upscaler (FidelityFX SDK 1.1.4, MIT licence) on OpenGL compute shaders, so the pack's TAA can be
 * swapped for FSR on any graphics card the way it is for DLAA. The shaders are AMD's own, ported in
 * resources/create_galore_bridge/fsr3 (its README lists the changes); this class does each frame what the SDK's
 * ffx_fsr3upscaler.cpp does: its internal textures, its constants, its clears and its seven passes in order.
 *
 * Inputs as DLSS gets them: HDR colour before the pack's exposure, motion vectors in render pixels towards the
 * previous frame without jitter, depth reversed and infinite (near / view depth) and the pack's exposure; plus the
 * pack's reactive mask (red) and transparency and composition mask (green) in one texture, when it has them. No
 * sharpening. Render size and output size are kept apart like the SDK does; the Bridge uses
 * the same size for both (native anti-aliasing).
 *
 * Everything it changes in OpenGL's state is put back, so Iris and Minecraft's cached state stay right.
 */
final class Fsr3Upscaler implements AutoCloseable {
    private static final String ROOT = "/create_galore_bridge/fsr3/";
    /** Iris's near plane (its near uniform): the pack's depth is this over the view depth. */
    private static final float NEAR = 0.05f;
    private static final float FLT_EPSILON = Math.ulp(1.0f);
    /** The SDK's shader options for these inputs; the reference Lanczos (no table) as the SDK picks on NVIDIA cards. */
    private static final String[] DEFINES = {
            "FFX_GPU 1", "FFX_GLSL 1", "FFX_HALF 0", "FFX_SPD_NO_WAVE_OPERATIONS 1",
            "FFX_FSR3UPSCALER_OPTION_HDR_COLOR_INPUT 1",
            "FFX_FSR3UPSCALER_OPTION_LOW_RESOLUTION_MOTION_VECTORS 1",
            "FFX_FSR3UPSCALER_OPTION_JITTERED_MOTION_VECTORS 0",
            "FFX_FSR3UPSCALER_OPTION_INVERTED_DEPTH 1",
            "FFX_FSR3UPSCALER_OPTION_APPLY_SHARPENING 0",
            "FFX_FSR3UPSCALER_OPTION_REPROJECT_USE_LANCZOS_TYPE 0"};
    private static final Pattern INCLUDE = Pattern.compile("\\s*#\\s*include\\s+\"([^\"]+)\".*");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version.*$");
    /** Texture units for the passes, just under the write-back's top 2 and far above Minecraft's 12. */
    private static final int TEXTURE_UNITS = 16;

    // cbFSR3Upscaler (std140, the same layout as the SDK's Fsr3UpscalerConstants); checked against the driver's.
    private static final String MAIN_BLOCK = "cbFSR3UPSCALER_t";
    private static final int MAIN_SIZE = 160;
    private static final String[] MAIN_MEMBERS = {"iRenderSize", "iPreviousFrameRenderSize", "iUpscaleSize",
            "iPreviousFrameUpscaleSize", "iMaxRenderSize", "iMaxUpscaleSize", "fDeviceToViewDepth", "fJitter",
            "fPreviousFrameJitter", "fMotionVectorScale", "fDownscaleFactor", "fMotionVectorJitterCancellation",
            "fTanHalfFOV", "fJitterSequenceLength", "fDeltaTime", "fDeltaPreExposure", "fViewSpaceToMetersFactor",
            "fFrameIndex", "fVelocityFactor", "fReactivenessScale", "fShadingChangeScale", "fAccumulationAddedPerFrame",
            "fMinDisocclusionAccumulation"};
    private static final int[] MAIN_OFFSETS = {0, 8, 16, 24, 32, 40, 48, 64, 72, 80, 88, 96, 104, 108, 112, 116, 120,
            124, 128, 132, 136, 140, 144};
    private static final String SPD_BLOCK = "cbSPD_t";
    private static final int SPD_SIZE = 32;

    /** The passes in the order they run, as the SDK names them. */
    private static final String[] PASSES = {"prepare_inputs", "luma_pyramid", "shading_change_pyramid",
            "shading_change", "prepare_reactivity", "luma_instability", "accumulate", "debug_view"};
    private static final int PREPARE_INPUTS = 0;
    private static final int LUMA_PYRAMID = 1;
    private static final int SHADING_CHANGE_PYRAMID = 2;
    private static final int SHADING_CHANGE = 3;
    private static final int PREPARE_REACTIVITY = 4;
    private static final int LUMA_INSTABILITY = 5;
    private static final int ACCUMULATE = 6;
    private static final int DEBUG_VIEW = 7;

    private final Pass[] passes = new Pass[PASSES.length];
    private final int textureBase;
    private final int mainBinding;
    private final int spdBinding;
    private final int mainBuffer;
    private final int spdBuffer;
    private final int linearSampler;
    private final int pointSampler;
    private final ByteBuffer main = ByteBuffer.allocateDirect(MAIN_SIZE).order(ByteOrder.nativeOrder());
    private final ByteBuffer spd = ByteBuffer.allocateDirect(SPD_SIZE).order(ByteOrder.nativeOrder());
    private int imageUnits;
    private int samplerUnits;

    // Sized textures (see ensureSize), as in the SDK's internalSurfaceDesc.
    private int renderWidth;
    private int renderHeight;
    private int upscaleWidth;
    private int upscaleHeight;
    private final List<Tex> owned = new ArrayList<>();
    private Tex[] accumulation;
    private Tex[] luma;
    private Tex[] lumaHistory;
    private Tex[] upscaled;
    private Tex intermediate;
    private Tex shadingChange;
    private Tex newLocks;
    private Tex spdMips;
    private Tex farthestDepthMip1;
    private Tex spdAtomic;
    private Tex dilatedReactiveMasks;
    private Tex defaultReactivity;
    private Tex frameInfo;
    private Tex dilatedDepth;
    private Tex dilatedMotionVectors;
    private Tex reconstructedPreviousNearestDepth;

    // The context's running state.
    private boolean firstExecution = true;
    private boolean oddFrame;
    private final float[] jitter = new float[2];
    private final float[] previousJitter = new float[2];
    private final int[] previousRenderSize = new int[2];
    private final int[] previousUpscaleSize = new int[2];
    private float jitterPhaseCount;
    private float frameIndex;

    // This frame's resources, picked by name as the SDK's binding tables do.
    private Tex inColor;
    private Tex inDepth;
    private Tex inMotion;
    private Tex inExposure;
    private Tex inMask;
    private Tex outColor;

    /** Compiles the passes (the first time takes a moment). Throws if this OpenGL cannot run them. */
    Fsr3Upscaler() throws IOException {
        textureBase = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 2 - TEXTURE_UNITS;
        int bindings = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        mainBinding = bindings - 2;
        spdBinding = bindings - 1;
        try {
            for (int i = 0; i < PASSES.length; i++) {
                passes[i] = new Pass(PASSES[i]);
            }
            for (Pass pass : passes) {
                checkLayout(pass.program);
            }
        } catch (RuntimeException | IOException e) {
            for (Pass pass : passes) {
                if (pass != null) {
                    glDeleteProgram(pass.program);
                }
            }
            throw e;
        }
        int maxImages = glGetInteger(GL_MAX_COMPUTE_IMAGE_UNIFORMS);
        if (imageUnits > maxImages) {
            close();
            throw new IllegalStateException("FSR 3 needs " + imageUnits + " compute images, this OpenGL has " + maxImages);
        }
        mainBuffer = glCreateBuffers();
        glNamedBufferStorage(mainBuffer, MAIN_SIZE, GL_DYNAMIC_STORAGE_BIT);
        spdBuffer = glCreateBuffers();
        glNamedBufferStorage(spdBuffer, SPD_SIZE, GL_DYNAMIC_STORAGE_BIT);
        linearSampler = sampler(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
        pointSampler = sampler(GL_NEAREST, GL_NEAREST);
    }

    /**
     * The OpenGL 4.1 to 4.5 functions the passes call that this context lacks, comma-separated, or null if it has them
     * all. The functions are checked rather than the version: NVIDIA reports the version the game asked for (3.3 on
     * the 4080) while having every 4.5 function (through the ARB extensions); Mesa reports its newest version.
     */
    static String missingFunctions() {
        GLCapabilities caps = GL.getCapabilities();
        String[] names = {"glProgramUniform1i", "glBindImageTexture", "glMemoryBarrier", "glDispatchCompute",
                "glClearTexImage", "glBindTextureUnit", "glCreateBuffers", "glCreateSamplers", "glCreateTextures",
                "glNamedBufferStorage", "glNamedBufferSubData", "glTextureParameteri", "glTextureStorage2D"};
        long[] addresses = {caps.glProgramUniform1i, caps.glBindImageTexture, caps.glMemoryBarrier,
                caps.glDispatchCompute, caps.glClearTexImage, caps.glBindTextureUnit, caps.glCreateBuffers,
                caps.glCreateSamplers, caps.glCreateTextures, caps.glNamedBufferStorage, caps.glNamedBufferSubData,
                caps.glTextureParameteri, caps.glTextureStorage2D};
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (addresses[i] == 0L) {
                missing.add(names[i]);
            }
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    /** FSR's own jitter period for this scale (8 at native size): its Halton sequence repeats after this many frames. */
    static int phases(int renderWidth, int upscaleWidth) {
        float ratio = (float) upscaleWidth / renderWidth;
        return (int) (8.0f * ratio * ratio);
    }

    /** True if the textures were (re)made for new sizes; FSR starts over then. */
    boolean ensureSize(int renderWidth, int renderHeight, int upscaleWidth, int upscaleHeight) {
        if (accumulation != null && this.renderWidth == renderWidth && this.renderHeight == renderHeight
                && this.upscaleWidth == upscaleWidth && this.upscaleHeight == upscaleHeight) {
            return false;
        }
        closeTextures();
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.upscaleWidth = upscaleWidth;
        this.upscaleHeight = upscaleHeight;
        int halfWidth = renderWidth / 2;
        int halfHeight = renderHeight / 2;
        accumulation = new Tex[] {tex(GL_R8, renderWidth, renderHeight, 1), tex(GL_R8, renderWidth, renderHeight, 1)};
        luma = new Tex[] {tex(GL_R16F, renderWidth, renderHeight, 1), tex(GL_R16F, renderWidth, renderHeight, 1)};
        intermediate = tex(GL_R16F, renderWidth, renderHeight, 1);
        shadingChange = tex(GL_R8, halfWidth, halfHeight, 1);
        newLocks = tex(GL_R8, upscaleWidth, upscaleHeight, 1);
        upscaled = new Tex[] {tex(GL_RGBA16F, upscaleWidth, upscaleHeight, 1), tex(GL_RGBA16F, upscaleWidth, upscaleHeight, 1)};
        int levels = 32 - Integer.numberOfLeadingZeros(Math.max(Math.max(halfWidth, halfHeight), 1));
        spdMips = tex(GL_RG16F, halfWidth, halfHeight, levels);
        farthestDepthMip1 = tex(GL_R16F, halfWidth, halfHeight, 1);
        lumaHistory = new Tex[] {tex(GL_RGBA16F, renderWidth, renderHeight, 1), tex(GL_RGBA16F, renderWidth, renderHeight, 1)};
        spdAtomic = tex(GL_R32UI, 1, 1, 1);
        dilatedReactiveMasks = tex(GL_RGBA8, renderWidth, renderHeight, 1);
        defaultReactivity = tex(GL_R8, 1, 1, 1);
        frameInfo = tex(GL_RGBA32F, 1, 1, 1);
        dilatedDepth = tex(GL_R32F, renderWidth, renderHeight, 1);
        dilatedMotionVectors = tex(GL_RG16F, renderWidth, renderHeight, 1);
        reconstructedPreviousNearestDepth = tex(GL_R32UI, renderWidth, renderHeight, 1);
        clear(spdAtomic, 0);
        clear(defaultReactivity, 0.0f, 0.0f, 0.0f, 0.0f);
        firstExecution = true;
        jitterPhaseCount = 0.0f;
        return true;
    }

    /**
     * One frame: reads the inputs (OpenGL textures at render size: RGBA16F colour, R32F depth, RG16F motion, 1x1 R32F
     * exposure, RG16F masks or 0 for none) and writes {@code output} (RGBA16F, output size). {@code jitterX/Y} is this frame's jitter in pixels
     * as the pack applied it, {@code projection} the frame's unjittered projection. {@code debugView} draws FSR's own
     * view of its buffers instead.
     */
    void dispatch(int color, int depth, int motion, int exposure, int mask, int output, float jitterX, float jitterY,
            boolean reset, float frameTimeMs, Matrix4fc projection, boolean debugView) {
        inColor = new Tex(color, GL_RGBA16F, renderWidth, renderHeight, 1);
        inDepth = new Tex(depth, GL_R32F, renderWidth, renderHeight, 1);
        inMotion = new Tex(motion, GL_RG16F, renderWidth, renderHeight, 1);
        inExposure = new Tex(exposure, GL_R32F, 1, 1, 1);
        inMask = mask != 0 ? new Tex(mask, GL_RG16F, renderWidth, renderHeight, 1) : defaultReactivity;
        outColor = new Tex(output, GL_RGBA16F, upscaleWidth, upscaleHeight, 1);

        boolean resetAccumulation = reset || firstExecution;
        if (firstExecution) {
            clear(accumulation[0], 0.0f, 0.0f, 0.0f, 0.0f);
            clear(accumulation[1], 0.0f, 0.0f, 0.0f, 0.0f);
            clear(luma[0], 0.0f, 0.0f, 0.0f, 0.0f);
            clear(luma[1], 0.0f, 0.0f, 0.0f, 0.0f);
            firstExecution = false;
        }
        writeConstants(jitterX, jitterY, resetAccumulation, frameTimeMs, projection);
        if (resetAccumulation) {
            clear(accumulationRead(), 0.0f, 0.0f, 0.0f, 0.0f);
            clear(spdMips, 0.0f, 0.0f, 0.0f, 0.0f);
            // Auto exposure always tracks luma changes for the locks.
            clear(frameInfo, -1.0f, 1.0f, 0.0f, 0.0f);
        }
        // Reversed depth: the farthest is 0.
        clear(reconstructedPreviousNearestDepth, 0);
        clear(spdAtomic, 0);
        clear(spdMips, 0.0f, 0.0f, 0.0f, 0.0f);

        int srcX = groups(renderWidth, 8);
        int srcY = groups(renderHeight, 8);
        int dstX = groups(upscaleWidth, 8);
        int dstY = groups(upscaleHeight, 8);
        int spdX = (renderWidth - 1) / 64 + 1;
        int spdY = (renderHeight - 1) / 64 + 1;
        writeSpdConstants(spdX * spdY);

        State state = State.save(textureBase, samplerUnits, imageUnits, mainBinding, spdBinding);
        try {
            glBindBufferRange(GL_UNIFORM_BUFFER, mainBinding, mainBuffer, 0, MAIN_SIZE);
            glBindBufferRange(GL_UNIFORM_BUFFER, spdBinding, spdBuffer, 0, SPD_SIZE);
            run(passes[PREPARE_INPUTS], srcX, srcY);
            run(passes[LUMA_PYRAMID], spdX, spdY);
            run(passes[SHADING_CHANGE_PYRAMID], spdX, spdY);
            run(passes[SHADING_CHANGE], groups(renderWidth / 2, 8), groups(renderHeight / 2, 8));
            run(passes[PREPARE_REACTIVITY], srcX, srcY);
            run(passes[LUMA_INSTABILITY], srcX, srcY);
            run(passes[ACCUMULATE], dstX, dstY);
            if (debugView) {
                run(passes[DEBUG_VIEW], dstX, dstY);
            }
        } finally {
            state.restore();
        }
        oddFrame = !oddFrame;
    }

    private void writeConstants(float jitterX, float jitterY, boolean resetAccumulation, float frameTimeMs, Matrix4fc projection) {
        previousJitter[0] = jitter[0];
        previousJitter[1] = jitter[1];
        jitter[0] = jitterX;
        jitter[1] = jitterY;
        int phases = phases(renderWidth, upscaleWidth);
        if (resetAccumulation || jitterPhaseCount == 0.0f) {
            jitterPhaseCount = phases;
        } else if (phases > jitterPhaseCount) {
            jitterPhaseCount++;
        } else if (phases < jitterPhaseCount) {
            jitterPhaseCount--;
        }
        frameIndex = resetAccumulation ? 0.0f : frameIndex + 1.0f;

        main.clear();
        main.putInt(0, renderWidth).putInt(4, renderHeight);
        main.putInt(8, previousRenderSize[0]).putInt(12, previousRenderSize[1]);
        main.putInt(16, upscaleWidth).putInt(20, upscaleHeight);
        main.putInt(24, previousUpscaleSize[0]).putInt(28, previousUpscaleSize[1]);
        main.putInt(32, renderWidth).putInt(36, renderHeight);
        main.putInt(40, upscaleWidth).putInt(44, upscaleHeight);
        // Device depth to view depth, reversed and infinite: view = near / (depth + epsilon); x and y from the
        // projection (the SDK's 1 / a and 1 / b).
        main.putFloat(48, -FLT_EPSILON).putFloat(52, NEAR).putFloat(56, 1.0f / projection.m00()).putFloat(60, 1.0f / projection.m11());
        main.putFloat(64, jitter[0]).putFloat(68, jitter[1]);
        main.putFloat(72, previousJitter[0]).putFloat(76, previousJitter[1]);
        // The motion vectors are in render pixels.
        main.putFloat(80, 1.0f / renderWidth).putFloat(84, 1.0f / renderHeight);
        main.putFloat(88, (float) renderWidth / upscaleWidth).putFloat(92, (float) renderHeight / upscaleHeight);
        main.putFloat(96, 0.0f).putFloat(100, 0.0f);
        // tan of half the horizontal field of view.
        main.putFloat(104, 1.0f / projection.m00());
        main.putFloat(108, jitterPhaseCount);
        main.putFloat(112, Math.max(0.0f, Math.min(1.0f, frameTimeMs / 1000.0f)));
        main.putFloat(116, 1.0f); // pre-exposure never changes
        main.putFloat(120, 1.0f); // one block is a metre
        main.putFloat(124, frameIndex);
        main.putFloat(128, 1.0f).putFloat(132, 1.0f).putFloat(136, 1.0f);
        main.putFloat(140, 1.0f / 3.0f).putFloat(144, -1.0f / 3.0f);
        glNamedBufferSubData(mainBuffer, 0, main);

        previousRenderSize[0] = renderWidth;
        previousRenderSize[1] = renderHeight;
        previousUpscaleSize[0] = upscaleWidth;
        previousUpscaleSize[1] = upscaleHeight;
    }

    // ffxSpdSetup for the whole render size.
    private void writeSpdConstants(int workGroups) {
        int mips = (int) Math.min(Math.floor(Math.log(Math.max(renderWidth, renderHeight)) / Math.log(2.0)), 12.0);
        spd.clear();
        spd.putInt(0, mips).putInt(4, workGroups).putInt(8, 0).putInt(12, 0).putInt(16, renderWidth).putInt(20, renderHeight);
        glNamedBufferSubData(spdBuffer, 0, spd);
    }

    private void run(Pass pass, int groupsX, int groupsY) {
        glUseProgram(pass.program);
        for (int i = 0; i < pass.samplers.length; i++) {
            Tex texture = read(pass.samplers[i]);
            int unit = textureBase + i;
            glBindTextureUnit(unit, texture.id);
            glBindSampler(unit, texture.format == GL_R32UI ? pointSampler : linearSampler);
        }
        for (int i = 0; i < pass.images.length; i++) {
            String name = pass.images[i];
            Tex texture = write(name);
            int level = name.startsWith("rw_spd_mip") ? Math.min(name.charAt(10) - '0', texture.levels - 1) : 0;
            glBindImageTexture(i, texture.id, level, false, 0, GL_READ_WRITE, texture.format);
        }
        glDispatchCompute(groupsX, groupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    }

    // The SDK's srvTextureBindingTable with this frame's picks.
    private Tex read(String name) {
        return switch (name) {
            case "r_input_color_jittered" -> inColor;
            case "r_input_motion_vectors" -> inMotion;
            case "r_input_depth" -> inDepth;
            case "r_input_exposure" -> inExposure;
            case "r_frame_info" -> frameInfo;
            case "r_reactive_mask", "r_transparency_and_composition_mask" -> inMask;
            case "r_reconstructed_previous_nearest_depth" -> reconstructedPreviousNearestDepth;
            case "r_dilated_motion_vectors" -> dilatedMotionVectors;
            case "r_dilated_depth" -> dilatedDepth;
            case "r_internal_upscaled_color" -> upscaled[oddFrame ? 1 : 0];
            case "r_accumulation" -> accumulationRead();
            case "r_luma_history" -> lumaHistory[oddFrame ? 1 : 0];
            case "r_rcas_input" -> upscaled[oddFrame ? 0 : 1];
            case "r_spd_mips" -> spdMips;
            case "r_dilated_reactive_masks" -> dilatedReactiveMasks;
            case "r_new_locks" -> newLocks;
            case "r_farthest_depth", "r_luma_instability" -> intermediate;
            case "r_farthest_depth_mip1" -> farthestDepthMip1;
            case "r_shading_change" -> shadingChange;
            case "r_current_luma" -> luma[oddFrame ? 1 : 0];
            case "r_previous_luma" -> luma[oddFrame ? 0 : 1];
            default -> throw new IllegalStateException("FSR 3 reads " + name + ", which the Bridge does not provide");
        };
    }

    // The SDK's uavTextureBindingTable with this frame's picks.
    private Tex write(String name) {
        if (name.startsWith("rw_spd_mip")) {
            return spdMips;
        }
        return switch (name) {
            case "rw_reconstructed_previous_nearest_depth" -> reconstructedPreviousNearestDepth;
            case "rw_dilated_motion_vectors" -> dilatedMotionVectors;
            case "rw_dilated_depth" -> dilatedDepth;
            case "rw_internal_upscaled_color" -> upscaled[oddFrame ? 0 : 1];
            case "rw_accumulation" -> accumulation[oddFrame ? 0 : 1];
            case "rw_luma_history" -> lumaHistory[oddFrame ? 0 : 1];
            case "rw_upscaled_output" -> outColor;
            case "rw_dilated_reactive_masks" -> dilatedReactiveMasks;
            case "rw_frame_info" -> frameInfo;
            case "rw_spd_global_atomic" -> spdAtomic;
            case "rw_new_locks" -> newLocks;
            case "rw_shading_change" -> shadingChange;
            case "rw_farthest_depth", "rw_luma_instability" -> intermediate;
            case "rw_farthest_depth_mip1" -> farthestDepthMip1;
            case "rw_current_luma" -> luma[oddFrame ? 1 : 0];
            default -> throw new IllegalStateException("FSR 3 writes " + name + ", which the Bridge does not provide");
        };
    }

    private Tex accumulationRead() {
        return accumulation[oddFrame ? 1 : 0];
    }

    private static int groups(int size, int groupSize) {
        return (size + groupSize - 1) / groupSize;
    }

    private Tex tex(int format, int width, int height, int levels) {
        int id = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(id, levels, format, Math.max(width, 1), Math.max(height, 1));
        Tex texture = new Tex(id, format, width, height, levels);
        owned.add(texture);
        return texture;
    }

    private static void clear(Tex texture, float r, float g, float b, float a) {
        for (int level = 0; level < texture.levels; level++) {
            glClearTexImage(texture.id, level, GL_RGBA, GL_FLOAT, new float[] {r, g, b, a});
        }
    }

    private static void clear(Tex texture, int value) {
        glClearTexImage(texture.id, 0, GL_RED_INTEGER, GL_UNSIGNED_INT, new int[] {value});
    }

    private static int sampler(int minFilter, int magFilter) {
        int sampler = glCreateSamplers();
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, minFilter);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, magFilter);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return sampler;
    }

    // The constants are written at fixed std140 offsets: make sure this driver agrees.
    private static void checkLayout(int program) {
        int block = glGetUniformBlockIndex(program, MAIN_BLOCK);
        if (block != GL_INVALID_INDEX && glGetActiveUniformBlocki(program, block, GL_UNIFORM_BLOCK_DATA_SIZE) > MAIN_SIZE) {
            throw new IllegalStateException("FSR 3 constants take " + glGetActiveUniformBlocki(program, block, GL_UNIFORM_BLOCK_DATA_SIZE)
                    + " bytes here, expected " + MAIN_SIZE);
        }
        for (int i = 0; i < MAIN_MEMBERS.length; i++) {
            int index = glGetUniformIndices(program, MAIN_BLOCK + "." + MAIN_MEMBERS[i]);
            if (index == GL_INVALID_INDEX) {
                continue; // not used by this pass
            }
            int offset = glGetActiveUniformsi(program, index, GL_UNIFORM_OFFSET);
            if (offset != MAIN_OFFSETS[i]) {
                throw new IllegalStateException("FSR 3 constant " + MAIN_MEMBERS[i] + " is at " + offset + ", expected " + MAIN_OFFSETS[i]);
            }
        }
    }

    private void closeTextures() {
        for (Tex texture : owned) {
            glDeleteTextures(texture.id);
        }
        owned.clear();
        accumulation = null;
    }

    @Override
    public void close() {
        closeTextures();
        for (Pass pass : passes) {
            if (pass != null) {
                glDeleteProgram(pass.program);
            }
        }
        if (mainBuffer != 0) {
            glDeleteBuffers(mainBuffer);
            glDeleteBuffers(spdBuffer);
            glDeleteSamplers(linearSampler);
            glDeleteSamplers(pointSampler);
        }
    }

    private record Tex(int id, int format, int width, int height, int levels) {}

    /** One compiled pass and the textures and images it uses, by the SDK's names, in unit order. */
    private final class Pass {
        final int program;
        final String[] samplers;
        final String[] images;

        Pass(String name) throws IOException {
            program = link(name, source(name));
            List<String> samplerNames = new ArrayList<>();
            List<String> imageNames = new ArrayList<>();
            try (MemoryStack stack = stackPush()) {
                IntBuffer size = stack.mallocInt(1);
                IntBuffer type = stack.mallocInt(1);
                int count = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
                for (int i = 0; i < count; i++) {
                    String uniform = glGetActiveUniform(program, i, size, type);
                    int t = type.get(0);
                    if (t == GL_SAMPLER_2D || t == GL_UNSIGNED_INT_SAMPLER_2D) {
                        glProgramUniform1i(program, glGetUniformLocation(program, uniform), textureBase + samplerNames.size());
                        samplerNames.add(uniform);
                    } else if (t == GL_IMAGE_2D || t == GL_UNSIGNED_INT_IMAGE_2D) {
                        glProgramUniform1i(program, glGetUniformLocation(program, uniform), imageNames.size());
                        imageNames.add(uniform);
                    }
                }
            }
            if (samplerNames.size() > TEXTURE_UNITS) {
                throw new IllegalStateException("FSR 3 pass " + name + " reads " + samplerNames.size() + " textures");
            }
            samplers = samplerNames.toArray(String[]::new);
            images = imageNames.toArray(String[]::new);
            samplerUnits = Math.max(samplerUnits, samplers.length);
            imageUnits = Math.max(imageUnits, images.length);
            bindBlock(MAIN_BLOCK, mainBinding);
            bindBlock(SPD_BLOCK, spdBinding);
        }

        private void bindBlock(String block, int binding) {
            int index = glGetUniformBlockIndex(program, block);
            if (index != GL_INVALID_INDEX) {
                glUniformBlockBinding(program, index, binding);
            }
        }
    }

    /** Pass {@code name}'s source with its includes put in and the Bridge's options defined. */
    static String source(String name) throws IOException {
        StringBuilder out = new StringBuilder("#version 450 core\n");
        for (String define : DEFINES) {
            out.append("#define ").append(define).append('\n');
        }
        String pass = "passes/ffx_fsr3upscaler_" + name + "_pass.glsl";
        expand(VERSION.matcher(readText(pass)).replaceFirst(""), "passes/", out, 0);
        return out.toString();
    }

    // AMD's headers name includes from the shader root or their own folder; a header the Bridge does not ship (the
    // HLSL and CPU ones, behind #if) becomes an #error that only fires if it is really used.
    private static void expand(String text, String folder, StringBuilder out, int depth) throws IOException {
        if (depth > 16) {
            throw new IOException("FSR 3 includes nest too deep");
        }
        for (String line : text.split("\n", -1)) {
            Matcher m = INCLUDE.matcher(line);
            if (!m.matches()) {
                out.append(line).append('\n');
                continue;
            }
            String include = m.group(1);
            String path = exists(include) ? include : exists(folder + include) ? folder + include : null;
            if (path == null) {
                out.append("#error the Bridge has no ").append(include).append('\n');
                continue;
            }
            int slash = path.lastIndexOf('/');
            expand(readText(path), slash < 0 ? "" : path.substring(0, slash + 1), out, depth + 1);
        }
    }

    private static boolean exists(String path) {
        return Fsr3Upscaler.class.getResource(ROOT + path) != null;
    }

    private static String readText(String path) throws IOException {
        try (InputStream in = Fsr3Upscaler.class.getResourceAsStream(ROOT + path)) {
            if (in == null) {
                throw new IOException("The mod jar has no " + ROOT + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static int link(String name, String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("FSR 3 pass " + name + " did not compile: " + shorten(log));
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("FSR 3 pass " + name + " did not link: " + shorten(log));
        }
        return program;
    }

    private static String shorten(String log) {
        return log.length() > 2000 ? log.substring(0, 2000) + "..." : log;
    }

    /** The OpenGL state the passes change: texture and image units, the two uniform buffer bindings, the program. */
    private record State(int program, int activeTexture, int textureBase, int[] textures, int[] samplers, int[][] images,
            int[] uniformBindings, int[] buffers, long[] starts, long[] sizes, int genericBuffer) {
        static State save(int textureBase, int textureCount, int imageCount, int mainBinding, int spdBinding) {
            int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            int[] textures = new int[textureCount];
            int[] samplers = new int[textureCount];
            for (int i = 0; i < textureCount; i++) {
                glActiveTexture(GL_TEXTURE0 + textureBase + i);
                textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
                samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
            }
            glActiveTexture(activeTexture);
            int[][] images = new int[imageCount][];
            for (int i = 0; i < imageCount; i++) {
                images[i] = new int[] {glGetIntegeri(GL_IMAGE_BINDING_NAME, i), glGetIntegeri(GL_IMAGE_BINDING_LEVEL, i),
                        glGetIntegeri(GL_IMAGE_BINDING_LAYERED, i), glGetIntegeri(GL_IMAGE_BINDING_LAYER, i),
                        glGetIntegeri(GL_IMAGE_BINDING_ACCESS, i), glGetIntegeri(GL_IMAGE_BINDING_FORMAT, i)};
            }
            int[] bindings = {mainBinding, spdBinding};
            int[] buffers = new int[2];
            long[] starts = new long[2];
            long[] sizes = new long[2];
            for (int i = 0; i < 2; i++) {
                buffers[i] = glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, bindings[i]);
                starts[i] = glGetInteger64i(GL_UNIFORM_BUFFER_START, bindings[i]);
                sizes[i] = glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, bindings[i]);
            }
            return new State(glGetInteger(GL_CURRENT_PROGRAM), activeTexture, textureBase, textures, samplers, images,
                    bindings, buffers, starts, sizes, glGetInteger(GL_UNIFORM_BUFFER_BINDING));
        }

        void restore() {
            glUseProgram(program);
            for (int i = 0; i < textures.length; i++) {
                glActiveTexture(GL_TEXTURE0 + textureBase + i);
                glBindTexture(GL_TEXTURE_2D, textures[i]);
                glBindSampler(textureBase + i, samplers[i]);
            }
            glActiveTexture(activeTexture);
            for (int i = 0; i < images.length; i++) {
                int[] image = images[i];
                glBindImageTexture(i, image[0], image[1], image[2] != 0, image[3], image[4], image[5]);
            }
            for (int i = 0; i < 2; i++) {
                if (buffers[i] != 0 && sizes[i] > 0) {
                    glBindBufferRange(GL_UNIFORM_BUFFER, uniformBindings[i], buffers[i], starts[i], sizes[i]);
                } else {
                    glBindBufferBase(GL_UNIFORM_BUFFER, uniformBindings[i], buffers[i]);
                }
            }
            glBindBuffer(GL_UNIFORM_BUFFER, genericBuffer);
        }
    }
}

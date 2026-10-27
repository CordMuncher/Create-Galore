package dev.creategalore.bridge.dlss;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_INTERNAL_FORMAT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.GL_RG16F;
import static org.lwjgl.opengl.GL30C.GL_RGBA16F;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glColorMaski;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glDisablei;
import static org.lwjgl.opengl.GL30C.glEnablei;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glGetBooleani_v;
import static org.lwjgl.opengl.GL30C.glGetFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL30C.glIsEnabledi;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL41C.glProgramUniform1f;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL41C.glProgramUniform2f;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.glCopyImageSubData;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.system.MemoryStack;

/**
 * The OpenGL side of one DLSS run, right after the pack's TAA pass while its framebuffer is still bound: copy the
 * pass's colour, motion vectors, depth and exposure into the textures shared with D3D11, let DLSS run, then draw its
 * result into the pass's outputs. When upscaling, the inputs are the render-sized lower left corner of the pack's
 * window-sized buffers, and the result is window-sized. Everything it changes in OpenGL's state is put back as it was, so Iris and
 * Minecraft's cached state stay right.
 *
 * The copies keep OpenGL's row order (bottom row first), so DLSS sees the frame upside down; its developer DLL's
 * watermark shows that. The motion vectors (+y up) and the jitter are in the same order, so to DLSS it is simply a
 * consistent frame: round 1 on the PC found the unflipped jitter sign steadiest and the motion vectors pointing right.
 *
 * Without a device the textures are plain OpenGL ones: for FSR 3 ({@link Fsr3Upscaler}, which runs in OpenGL), and
 * for the debug views (see {@link Dlss}), where the pass draws one of the inputs instead of a result, so the copies,
 * the pack's inputs and the write-back can be checked on any GPU.
 */
final class DlssPass implements AutoCloseable {
    private static final int GL_RENDERBUFFER_TYPE = 0x8D41;

    /** What the pass draws into the pack's outputs. */
    enum View {
        /** The upscaler's result (DLSS, or FSR 3 in its textures without a device). */
        RESULT,
        /** The pack's colour as DLSS gets it: jittered, not anti-aliased. */
        COLOR,
        /** Motion vectors: red +x, green +y, blue -x/-y; 4 pixels or more is full brightness, still pixels black. */
        MOTION,
        /** DLSS depth: bright near, dark far, black sky. */
        DEPTH,
        /**
         * How well the motion vectors line up last frame's colour with this frame's (the Bridge leaves the jitter
         * out for it): black where they do, bright where something moved another way than its motion vector says.
         * Packs that know cg_dlss_residual_albedo hand over surface colours for it, free of the lighting's noise.
         */
        RESIDUAL,
        /** FSR 3's masks from the pack: reactive red, transparency and composition green (black without them). */
        MASK
    }

    private final DlssDevice device;
    private Texture color;
    private Texture motion;
    private Texture depth;
    private Texture exposure;
    private Texture output;
    /** The pack's masks for FSR 3 (only without a device: DLSS's current models take none). */
    private Texture mask;
    private boolean hasMask;
    private List<Texture> all = List.of();
    private List<DlssDevice.SharedTexture> shared = List.of();
    private int program;
    private int programOutputs = -1;
    private View programView;
    private int vao;

    /** {@code device} is null for FSR 3 and the debug views. */
    DlssPass(DlssDevice device) {
        this.device = device;
    }

    /**
     * What the bound framebuffer's pass produced, for one run: window-sized textures (width x height) holding the
     * frame in their lower left renderWidth x renderHeight. maskTexture 0 if the pack hands over no masks.
     */
    record Inputs(int colorTexture, int width, int height, int renderWidth, int renderHeight, int motionTexture, int depthTexture,
                  int exposureTexture, int maskTexture) {}

    /** Reads the colour texture of draw buffer {@code attachment} of the bound framebuffer; throws if it is unusable. */
    static int boundColorTexture(int attachment) {
        int type = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type != GL_TEXTURE) {
            throw new IllegalStateException("colour output " + attachment + " of the pass is not a texture"
                    + (type == GL_RENDERBUFFER_TYPE ? " (renderbuffer)" : ""));
        }
        return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    /** {@code renderSize} gives the render width or height for the window's. */
    static Inputs inputs(int colorTexture, int motionTexture, int depthTexture, int exposureTexture, int maskTexture,
                         java.util.function.IntUnaryOperator renderSize) {
        int width = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_WIDTH);
        int height = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_HEIGHT);
        requireFormat("colour", colorTexture, GL_RGBA16F, width, height);
        requireFormat("motion vector image", motionTexture, GL_RG16F, width, height);
        requireFormat("depth image", depthTexture, GL_R32F, width, height);
        requireFormat("exposure image", exposureTexture, GL_R32F, 1, 1);
        if (maskTexture != 0) {
            requireFormat("mask image", maskTexture, GL_RG16F, width, height);
        }
        return new Inputs(colorTexture, width, height, renderSize.applyAsInt(width), renderSize.applyAsInt(height),
                motionTexture, depthTexture, exposureTexture, maskTexture);
    }

    private static void requireFormat(String what, int texture, int format, int width, int height) {
        int actual = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_INTERNAL_FORMAT);
        int w = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_WIDTH);
        int h = glGetTextureLevelParameteri(texture, 0, GL_TEXTURE_HEIGHT);
        if (actual != format || w != width || h != height) {
            throw new IllegalStateException(String.format("the pack's %s is 0x%X %dx%d, DLSS needs 0x%X %dx%d",
                    what, actual, w, h, format, width, height));
        }
    }

    /** True if the textures don't have these sizes (yet): {@link #ensureSize} will make new ones. */
    boolean needsSize(int width, int height, int outputWidth, int outputHeight) {
        return color == null || color.width != width || color.height != height || output.width != outputWidth || output.height != outputHeight;
    }

    /**
     * True if the textures were (re)made for new sizes, which needs a new DLSS feature too. The old DLSS feature must
     * be released before (see {@link Dlss}): it ran on the textures this frees.
     */
    boolean ensureSize(int width, int height, int outputWidth, int outputHeight) {
        if (!needsSize(width, height, outputWidth, outputHeight)) {
            return false;
        }
        closeTextures();
        color = new Texture(width, height, GL_RGBA16F, DlssDevice.DXGI_FORMAT_R16G16B16A16_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE, DlssDevice.ACCESS_WRITE_DISCARD);
        motion = new Texture(width, height, GL_RG16F, DlssDevice.DXGI_FORMAT_R16G16_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE, DlssDevice.ACCESS_WRITE_DISCARD);
        depth = new Texture(width, height, GL_R32F, DlssDevice.DXGI_FORMAT_R32_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE, DlssDevice.ACCESS_WRITE_DISCARD);
        exposure = new Texture(1, 1, GL_R32F, DlssDevice.DXGI_FORMAT_R32_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE, DlssDevice.ACCESS_WRITE_DISCARD);
        output = new Texture(outputWidth, outputHeight, GL_RGBA16F, DlssDevice.DXGI_FORMAT_R16G16B16A16_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE | DlssDevice.D3D11_BIND_UNORDERED_ACCESS, DlssDevice.ACCESS_READ_ONLY);
        mask = device == null ? new Texture(width, height, GL_RG16F, DlssDevice.DXGI_FORMAT_R16G16_FLOAT,
                DlssDevice.D3D11_BIND_SHADER_RESOURCE, DlssDevice.ACCESS_WRITE_DISCARD) : null;
        all = mask != null ? List.of(color, motion, depth, exposure, output, mask) : List.of(color, motion, depth, exposure, output);
        List<DlssDevice.SharedTexture> list = new ArrayList<>();
        for (Texture texture : all) {
            if (texture.shared != null) {
                list.add(texture.shared);
            }
        }
        shared = List.copyOf(list);
        if (!shared.isEmpty()) {
            device.lock(shared); // OpenGL holds them between runs
        }
        return true;
    }

    /** Copies the inputs, hands the textures to D3D11 for {@code evaluate} (if there is a device), then takes them back. */
    void copyAndEvaluate(Inputs in, Runnable evaluate) {
        Markers.around("Create Galore DLAA: copy inputs", () -> {
            // The pack wrote the motion, depth and exposure images with image stores in the pass that just ran.
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
            int w = in.renderWidth();
            int h = in.renderHeight();
            glCopyImageSubData(in.colorTexture(), GL_TEXTURE_2D, 0, 0, 0, 0, color.gl, GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            glCopyImageSubData(in.motionTexture(), GL_TEXTURE_2D, 0, 0, 0, 0, motion.gl, GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            glCopyImageSubData(in.depthTexture(), GL_TEXTURE_2D, 0, 0, 0, 0, depth.gl, GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            glCopyImageSubData(in.exposureTexture(), GL_TEXTURE_2D, 0, 0, 0, 0, exposure.gl, GL_TEXTURE_2D, 0, 0, 0, 0, 1, 1, 1);
            hasMask = mask != null && in.maskTexture() != 0;
            if (hasMask) {
                glCopyImageSubData(in.maskTexture(), GL_TEXTURE_2D, 0, 0, 0, 0, mask.gl, GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            }
        });
        if (shared.isEmpty()) {
            Markers.around("Create Galore DLAA: evaluate", evaluate);
            return;
        }
        // The DirectX side (DLSS) runs between handing the shared textures over and taking them back.
        Markers.around("Create Galore DLAA: evaluate (DirectX)", () -> {
            device.unlock(shared);
            try {
                evaluate.run();
            } finally {
                device.lock(shared);
            }
        });
    }

    // The OpenGL side of the textures, for FSR 3.
    int colorTexture() {
        return color.gl;
    }

    int motionTexture() {
        return motion.gl;
    }

    int depthTexture() {
        return depth.gl;
    }

    int exposureTexture() {
        return exposure.gl;
    }

    int outputTexture() {
        return output.gl;
    }

    /** The pack's masks for FSR 3 this frame, 0 if it handed over none. */
    int maskTexture() {
        return hasMask ? mask.gl : 0;
    }

    long colorResource() {
        return color.d3d;
    }

    long outputResource() {
        return output.d3d;
    }

    long depthResource() {
        return depth.d3d;
    }

    long motionResource() {
        return motion.d3d;
    }

    long exposureResource() {
        return exposure.d3d;
    }

    /**
     * For testing: saves this frame's inputs (colour, motion, depth) as dir/name-color.npy and so on, and with
     * {@code withOutput} what DLSS or FSR made of them (name-output.npy) and the pack's exposure (name-exposure.npy).
     */
    void dump(java.nio.file.Path dir, String name, boolean withOutput) {
        DebugDump.write(dir, name + "-color", color.gl, color.width, color.height, 4);
        DebugDump.write(dir, name + "-motion", motion.gl, motion.width, motion.height, 2);
        DebugDump.write(dir, name + "-depth", depth.gl, depth.width, depth.height, 1);
        if (hasMask) {
            DebugDump.write(dir, name + "-mask", mask.gl, mask.width, mask.height, 2);
        }
        if (withOutput) {
            DebugDump.write(dir, name + "-exposure", exposure.gl, exposure.width, exposure.height, 1);
            DebugDump.write(dir, name + "-output", output.gl, output.width, output.height, 4);
        }
    }

    /**
     * Draws {@code view} into draw buffers 0..max of the bound framebuffer: into those {@code profile} lists, keeping
     * alpha where the profile says so.
     */
    void writeBack(PackProfile profile, View view) {
        int outputs = 0;
        for (int a : profile.outputAttachments()) {
            outputs = Math.max(outputs, a + 1);
        }
        ensureProgram(outputs, view);
        Texture source = switch (view) {
            case RESULT -> output;
            case COLOR, RESIDUAL -> color;
            case MOTION -> motion;
            case DEPTH -> depth;
            case MASK -> hasMask ? mask : depth; // (all black without masks, see the view's shader)
        };
        // The source, the exposure, and for the residual view last frame's colour (kept in the unused output) and the motion.
        String[] samplers = {"cg_dlss_source", "cg_dlss_exposure", "cg_dlss_previous", "cg_dlss_motion"};
        Texture[] textures = {source, exposure, output, motion};
        int topUnit = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;

        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] oldTextures = new int[samplers.length];
        int[] oldSamplers = new int[samplers.length];
        for (int i = 0; i < samplers.length; i++) {
            glActiveTexture(GL_TEXTURE0 + topUnit - i);
            oldTextures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            oldSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean stencilTest = glIsEnabled(GL_STENCIL_TEST);
        boolean scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        boolean cullFace = glIsEnabled(GL_CULL_FACE);
        boolean[] blend = new boolean[outputs];
        byte[][] masks = new byte[outputs][4];
        try (MemoryStack stack = stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            for (int i = 0; i < outputs; i++) {
                blend[i] = glIsEnabledi(GL_BLEND, i);
                glGetBooleani_v(GL_COLOR_WRITEMASK, i, mask);
                mask.get(0, masks[i]);
            }
        }
        try {
            for (int i = 0; i < samplers.length; i++) {
                int location = glGetUniformLocation(program, samplers[i]);
                if (location >= 0) {
                    glActiveTexture(GL_TEXTURE0 + topUnit - i);
                    glBindTexture(GL_TEXTURE_2D, textures[i].gl);
                    glBindSampler(topUnit - i, 0);
                    glProgramUniform1i(program, location, topUnit - i);
                }
            }
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_CULL_FACE);
            for (int i = 0; i < outputs; i++) {
                glDisablei(GL_BLEND, i);
                boolean written = contains(profile.outputAttachments(), i);
                glColorMaski(i, written, written, written, written && !profile.keepsAlpha(i));
            }
            if (view == View.MASK) {
                glProgramUniform1f(program, glGetUniformLocation(program, "cg_dlss_has_mask"), hasMask ? 1.0f : 0.0f);
            }
            // The input views stretch the render-sized inputs over the window.
            glProgramUniform2f(program, glGetUniformLocation(program, "cg_dlss_view_scale"),
                    (float) source.width / output.width, (float) source.height / output.height);
            glUseProgram(program);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            glBindVertexArray(oldVao);
            glUseProgram(oldProgram);
            for (int i = 0; i < outputs; i++) {
                glColorMaski(i, masks[i][0] == GL_TRUE, masks[i][1] == GL_TRUE, masks[i][2] == GL_TRUE, masks[i][3] == GL_TRUE);
                if (blend[i]) {
                    glEnablei(GL_BLEND, i);
                }
            }
            setEnabled(GL_DEPTH_TEST, depthTest);
            setEnabled(GL_STENCIL_TEST, stencilTest);
            setEnabled(GL_SCISSOR_TEST, scissorTest);
            setEnabled(GL_CULL_FACE, cullFace);
            for (int i = samplers.length - 1; i >= 0; i--) {
                glActiveTexture(GL_TEXTURE0 + topUnit - i);
                glBindSampler(topUnit - i, oldSamplers[i]);
                glBindTexture(GL_TEXTURE_2D, oldTextures[i]);
            }
            glActiveTexture(oldActive);
        }
        if (view == View.RESIDUAL) {
            // This frame's colour is next frame's last one.
            glCopyImageSubData(color.gl, GL_TEXTURE_2D, 0, 0, 0, 0, output.gl, GL_TEXTURE_2D, 0, 0, 0, 0, color.width, color.height, 1);
        }
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }

    private void ensureProgram(int outputs, View view) {
        if (program != 0 && programOutputs == outputs && programView == view) {
            return;
        }
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        StringBuilder fragment = new StringBuilder("#version 330 core\nuniform sampler2D cg_dlss_source;\nuniform vec2 cg_dlss_view_scale;\n");
        if (view == View.MOTION || view == View.DEPTH || view == View.RESIDUAL || view == View.MASK) {
            fragment.append("uniform sampler2D cg_dlss_exposure;\n");
        }
        if (view == View.RESIDUAL) {
            fragment.append("uniform sampler2D cg_dlss_previous;\nuniform sampler2D cg_dlss_motion;\n");
        }
        if (view == View.MASK) {
            fragment.append("uniform float cg_dlss_has_mask;\n");
        }
        for (int i = 0; i < outputs; i++) {
            fragment.append("layout(location = ").append(i).append(") out vec4 cg_out").append(i).append(";\n");
        }
        fragment.append("void main() {\n    ivec2 texel = ivec2(gl_FragCoord.xy * cg_dlss_view_scale);\n");
        fragment.append(switch (view) {
            case RESULT, COLOR -> "    vec3 c = texelFetch(cg_dlss_source, texel, 0).rgb;\n";
            // The pack multiplies the scene by its exposure later; dividing here shows these at their own brightness.
            case MOTION -> """
                        vec2 mv = texelFetch(cg_dlss_source, texel, 0).rg;
                        vec3 c = clamp(vec3(max(mv.x, 0.0), max(mv.y, 0.0), max(-mv.x, 0.0) + max(-mv.y, 0.0)) / 4.0, 0.0, 1.0);
                        c /= max(texelFetch(cg_dlss_exposure, ivec2(0), 0).r, 1e-4);
                    """;
            case DEPTH -> """
                        vec3 c = vec3(pow(texelFetch(cg_dlss_source, texel, 0).r, 0.25));
                        c /= max(texelFetch(cg_dlss_exposure, ivec2(0), 0).r, 1e-4);
                    """;
            case MASK -> """
                        vec3 c = vec3(texelFetch(cg_dlss_source, texel, 0).rg * cg_dlss_has_mask, 0.0);
                        c /= max(texelFetch(cg_dlss_exposure, ivec2(0), 0).r, 1e-4);
                    """;
            // Last frame's colour where the motion vector says this pixel was (bilinear), against this frame's, both
            // exposed and tone mapped like a picture; off the screen last frame counts as matching.
            case RESIDUAL -> """
                        float e = max(texelFetch(cg_dlss_exposure, ivec2(0), 0).r, 1e-4);
                        ivec2 size = textureSize(cg_dlss_source, 0);
                        vec2 p = vec2(texel) + texelFetch(cg_dlss_motion, texel, 0).rg;
                        vec3 c = vec3(0.0);
                        if (all(greaterThanEqual(p, vec2(0.0))) && all(lessThanEqual(p, vec2(size - 1)))) {
                            ivec2 i = ivec2(floor(p));
                            ivec2 j = min(i + 1, size - 1);
                            vec2 f = p - vec2(i);
                            vec3 then = mix(mix(texelFetch(cg_dlss_previous, i, 0).rgb, texelFetch(cg_dlss_previous, ivec2(j.x, i.y), 0).rgb, f.x),
                                            mix(texelFetch(cg_dlss_previous, ivec2(i.x, j.y), 0).rgb, texelFetch(cg_dlss_previous, j, 0).rgb, f.x), f.y);
                            vec3 now = texelFetch(cg_dlss_source, texel, 0).rgb;
                            then *= e;
                            now *= e;
                            c = clamp(abs(now / (1.0 + now) - then / (1.0 + then)) * 4.0, 0.0, 1.0) / e;
                        }
                    """;
        });
        for (int i = 0; i < outputs; i++) {
            fragment.append("    cg_out").append(i).append(" = vec4(c, 0.0);\n");
        }
        fragment.append("}\n");
        program = link(VERTEX, fragment.toString());
        programOutputs = outputs;
        programView = view;
        if (vao == 0) {
            vao = glGenVertexArrays();
        }
    }

    // One triangle that covers the screen.
    private static final String VERTEX = """
            #version 330 core
            void main() {
                vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("DLAA write-back program did not link: " + log);
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("DLAA write-back shader did not compile: " + log);
        }
        return shader;
    }

    private void closeTextures() {
        if (!shared.isEmpty()) {
            // OpenGL hands them over with its work on them done; then the GPU finishes DLSS's last run on them.
            device.unlock(shared);
            device.waitIdle();
        }
        for (Texture texture : all) {
            texture.close();
        }
        all = List.of();
        shared = List.of();
        color = motion = depth = exposure = output = mask = null;
        hasMask = false;
    }

    @Override
    public void close() {
        closeTextures();
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
    }

    /** A texture shared with D3D11, or without a device a plain OpenGL one of the same format. */
    private final class Texture implements AutoCloseable {
        final int gl;
        final long d3d;
        final int width;
        final int height;
        final DlssDevice.SharedTexture shared;

        Texture(int width, int height, int glFormat, int dxgiFormat, int bindFlags, int access) {
            this.width = width;
            this.height = height;
            if (device != null) {
                shared = device.new SharedTexture(width, height, dxgiFormat, bindFlags, access);
                gl = shared.glTexture;
                d3d = shared.d3dTexture;
            } else {
                shared = null;
                d3d = 0L;
                gl = glCreateTextures(GL_TEXTURE_2D);
                glTextureStorage2D(gl, 1, glFormat, width, height);
                glTextureParameteri(gl, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTextureParameteri(gl, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTextureParameteri(gl, GL_TEXTURE_MAX_LEVEL, 0);
            }
        }

        @Override
        public void close() {
            if (shared != null) {
                shared.close();
            } else {
                glDeleteTextures(gl);
            }
        }
    }
}

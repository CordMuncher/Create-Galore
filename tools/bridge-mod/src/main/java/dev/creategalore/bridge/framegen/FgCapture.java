package dev.creategalore.bridge.framegen;

import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
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
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL41C.glProgramUniform2i;
import static org.lwjgl.opengl.GL45C.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.system.MemoryStack;

/**
 * The OpenGL pass that writes frame generation's inputs (FgInput in tools/bridge-mod/native/cg_framegen.h) at the end of
 * a frame, after DLSS or FSR ran and the HUD was drawn: into the textures shared with the DirectX 12 presenter, or for
 * the test views and checks into plain OpenGL ones of the same formats. One draw over the back buffer's size writes all
 * four, rows flipped to DirectX's order (top row first, as the presenter's back buffer):
 *  - hudless (RGBA8): the HUD split's world copy ({@code hudless}), or the frame itself when no HUD was drawn;
 *  - UI alpha (R8): the frame's alpha, where the HUD split recorded the HUD's coverage (0 when no HUD was drawn);
 *  - depth (R32F) and motion (RG16F): the pack's DLSS inputs, which fill the lower left render-sized corner in OpenGL
 *    and go to the top left in DirectX; motion's y turns over with the rows (render pixels, +y down). The rest is 0.
 * Everything it changes in OpenGL's state is put back, as Minecraft's and Iris's cached state expects.
 */
final class FgCapture {
    /** Draw buffers: the four inputs in FgInput order. */
    private static final int OUTPUTS = 4;

    private int program;
    private int vao;
    /** Nearest, no mipmaps: the sources read whole, whatever filtering their own textures ask for. */
    private int sampler;
    /** A framebuffer per set of targets, by the first target's texture. */
    private final Map<Integer, Integer> framebuffers = new HashMap<>();

    /**
     * Writes the inputs into {@code targets} (FgInput order, {@code width} x {@code height}). {@code depth} and
     * {@code motion} are the pack's images, as large, with the frame in their lower left {@code renderWidth} x
     * {@code renderHeight}; {@code noHud}: no HUD was drawn, so {@code hudless} is the frame and nothing is UI.
     */
    void run(int[] targets, int hudless, int frame, int depth, int motion, int width, int height, int renderWidth,
             int renderHeight, boolean noHud) {
        if (program == 0) {
            program = link(VERTEX, FRAGMENT);
            vao = glGenVertexArrays();
            sampler = glGenSamplers();
            glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        int framebuffer = framebuffer(targets);
        int[] textures = {hudless, frame, depth, motion};
        String[] samplers = {"cg_hudless", "cg_frame", "cg_depth", "cg_motion"};
        GlSnapshot state = new GlSnapshot(textures.length, OUTPUTS);
        try {
            for (int i = 0; i < textures.length; i++) {
                state.bind(i, textures[i], sampler);
                glProgramUniform1i(program, glGetUniformLocation(program, samplers[i]), state.units[i]);
            }
            glProgramUniform2i(program, glGetUniformLocation(program, "cg_size"), width, height);
            glProgramUniform2i(program, glGetUniformLocation(program, "cg_render"), renderWidth, renderHeight);
            glProgramUniform1i(program, glGetUniformLocation(program, "cg_no_hud"), noHud ? 1 : 0);
            state.plainDraw(framebuffer, width, height, true);
            glUseProgram(program);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            state.restore();
        }
    }

    private int framebuffer(int[] targets) {
        Integer known = framebuffers.get(targets[0]);
        if (known != null) {
            return known;
        }
        int framebuffer = glCreateFramebuffers();
        for (int i = 0; i < OUTPUTS; i++) {
            glNamedFramebufferTexture(framebuffer, GL_COLOR_ATTACHMENT0 + i, targets[i], 0);
        }
        try (MemoryStack stack = stackPush()) {
            glNamedFramebufferDrawBuffers(framebuffer, stack.ints(GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0 + 1,
                    GL_COLOR_ATTACHMENT0 + 2, GL_COLOR_ATTACHMENT0 + 3));
        }
        int status = glCheckNamedFramebufferStatus(framebuffer, GL_DRAW_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            glDeleteFramebuffers(framebuffer);
            throw new IllegalStateException(String.format("frame generation's inputs can't be drawn to (framebuffer status 0x%X)", status));
        }
        framebuffers.put(targets[0], framebuffer);
        return framebuffer;
    }

    /** The targets are about to be deleted: so is their framebuffer. */
    void forget(int[] targets) {
        Integer framebuffer = framebuffers.remove(targets[0]);
        if (framebuffer != null) {
            glDeleteFramebuffers(framebuffer);
        }
    }

    void close() {
        for (int framebuffer : framebuffers.values()) {
            glDeleteFramebuffers(framebuffer);
        }
        framebuffers.clear();
        if (program != 0) {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteSamplers(sampler);
            program = 0;
            vao = 0;
            sampler = 0;
        }
    }


    // One triangle that covers the screen.
    static final String VERTEX = """
            #version 330 core
            void main() {
                vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    // p is a target texel, its row counted from the top (DirectX's order); OpenGL's rows count from the bottom.
    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D cg_hudless;
            uniform sampler2D cg_frame;
            uniform sampler2D cg_depth;
            uniform sampler2D cg_motion;
            uniform ivec2 cg_size;
            uniform ivec2 cg_render;
            uniform int cg_no_hud;
            layout(location = 0) out vec4 cg_out_hudless;
            layout(location = 1) out vec4 cg_out_alpha;
            layout(location = 2) out vec4 cg_out_depth;
            layout(location = 3) out vec4 cg_out_motion;
            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy);
                ivec2 s = ivec2(p.x, cg_size.y - 1 - p.y);
                vec4 frame = texelFetch(cg_frame, s, 0);
                cg_out_hudless = vec4(cg_no_hud != 0 ? frame.rgb : texelFetch(cg_hudless, s, 0).rgb, 1.0);
                cg_out_alpha = vec4(cg_no_hud != 0 ? 0.0 : frame.a, 0.0, 0.0, 0.0);
                if (p.x < cg_render.x && p.y < cg_render.y) {
                    ivec2 r = ivec2(p.x, cg_render.y - 1 - p.y);
                    vec2 m = texelFetch(cg_motion, r, 0).rg;
                    cg_out_depth = vec4(texelFetch(cg_depth, r, 0).r, 0.0, 0.0, 0.0);
                    cg_out_motion = vec4(m.x, -m.y, 0.0, 0.0);
                } else {
                    cg_out_depth = vec4(0.0);
                    cg_out_motion = vec4(0.0);
                }
            }
            """;

    static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int linked = glCreateProgram();
        glAttachShader(linked, vertex);
        glAttachShader(linked, fragment);
        glLinkProgram(linked);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(linked, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(linked);
            glDeleteProgram(linked);
            throw new IllegalStateException("frame generation's program did not link: " + log);
        }
        return linked;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("frame generation's shader did not compile: " + log);
        }
        return shader;
    }
}

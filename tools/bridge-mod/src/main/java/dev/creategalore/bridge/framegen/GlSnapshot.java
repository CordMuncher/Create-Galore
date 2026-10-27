package dev.creategalore.bridge.framegen;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glColorMaski;
import static org.lwjgl.opengl.GL30C.glDisablei;
import static org.lwjgl.opengl.GL30C.glEnablei;
import static org.lwjgl.opengl.GL30C.glGetBooleani_v;
import static org.lwjgl.opengl.GL30C.glIsEnabledi;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.system.MemoryStack;

/**
 * The OpenGL state a full-screen pass of frame generation's changes, taken before and put back after, so Minecraft's
 * and Iris's cached copies of it stay right (as HudSplit and DlssPass do): the program, vertex array, draw framebuffer,
 * viewport, the active texture and the top texture units' textures and samplers, the depth, stencil, scissor and cull
 * switches, and blending and the colour mask of the first draw buffers.
 */
final class GlSnapshot {
    /** The texture units passes use: the highest ones, which Minecraft and Iris leave alone. */
    final int[] units;
    private final int drawBuffers;
    private final int program;
    private final int vao;
    private final int framebuffer;
    private final int active;
    private final int[] textures;
    private final int[] samplers;
    private final boolean depthTest;
    private final boolean stencilTest;
    private final boolean scissorTest;
    private final boolean cullFace;
    private final boolean[] blend;
    private final boolean[][] masks;
    private final int[] viewport = new int[4];

    /** Takes the state of {@code unitCount} top texture units and {@code drawBuffers} draw buffers. */
    GlSnapshot(int unitCount, int drawBuffers) {
        this.drawBuffers = drawBuffers;
        int top = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;
        units = new int[unitCount];
        for (int i = 0; i < unitCount; i++) {
            units[i] = top - i;
        }
        program = glGetInteger(GL_CURRENT_PROGRAM);
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        active = glGetInteger(GL_ACTIVE_TEXTURE);
        textures = new int[unitCount];
        samplers = new int[unitCount];
        for (int i = 0; i < unitCount; i++) {
            glActiveTexture(GL_TEXTURE0 + units[i]);
            textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        glActiveTexture(active);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        stencilTest = glIsEnabled(GL_STENCIL_TEST);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        cullFace = glIsEnabled(GL_CULL_FACE);
        blend = new boolean[drawBuffers];
        masks = new boolean[drawBuffers][4];
        try (MemoryStack stack = stackPush()) {
            IntBuffer view = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, view);
            view.get(0, viewport);
            ByteBuffer mask = stack.malloc(4);
            for (int i = 0; i < drawBuffers; i++) {
                blend[i] = glIsEnabledi(GL_BLEND, i);
                glGetBooleani_v(GL_COLOR_WRITEMASK, i, mask);
                for (int c = 0; c < 4; c++) {
                    masks[i][c] = mask.get(c) == GL_TRUE;
                }
            }
        }
    }

    /** Binds {@code texture} with {@code sampler} on texture unit {@code i} of {@link #units}. */
    void bind(int i, int texture, int sampler) {
        glActiveTexture(GL_TEXTURE0 + units[i]);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindSampler(units[i], sampler);
        glActiveTexture(active);
    }

    /** For a full-screen draw: no depth, stencil, scissor, culling or blending, the draw buffers' colour masks as given. */
    void plainDraw(int framebuffer, int width, int height, boolean alpha) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        for (int i = 0; i < drawBuffers; i++) {
            glDisablei(GL_BLEND, i);
            glColorMaski(i, true, true, true, alpha);
        }
    }

    void restore() {
        glBindVertexArray(vao);
        glUseProgram(program);
        for (int i = 0; i < drawBuffers; i++) {
            glColorMaski(i, masks[i][0], masks[i][1], masks[i][2], masks[i][3]);
            if (blend[i]) {
                glEnablei(GL_BLEND, i);
            } else {
                glDisablei(GL_BLEND, i);
            }
        }
        setEnabled(GL_DEPTH_TEST, depthTest);
        setEnabled(GL_STENCIL_TEST, stencilTest);
        setEnabled(GL_SCISSOR_TEST, scissorTest);
        setEnabled(GL_CULL_FACE, cullFace);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        for (int i = units.length - 1; i >= 0; i--) {
            glActiveTexture(GL_TEXTURE0 + units[i]);
            glBindSampler(units[i], samplers[i]);
            glBindTexture(GL_TEXTURE_2D, textures[i]);
        }
        glActiveTexture(active);
    }

    private static void setEnabled(int cap, boolean on) {
        if (on) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}

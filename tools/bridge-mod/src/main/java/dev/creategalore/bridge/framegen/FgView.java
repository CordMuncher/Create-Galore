package dev.creategalore.bridge.framegen;

import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL41C.glProgramUniform2i;

/**
 * frameGenView (testing): draws one of frame generation's inputs over the frame, rows turned back so it stands upright:
 * hudless as it is, UI alpha and depth (to the power 1/4) in grey, motion as the DLAA motion view draws it (red +x,
 * green +y, which is down here, blue -x and -y; 4 render pixels full brightness). Depth and motion fill the top left
 * render-sized corner, as frame generation reads them.
 */
final class FgView {
    static final String[] NAMES = {"off", "hudless", "alpha", "depth", "motion"};

    private int program;
    private int vao;
    private int sampler;

    /** Draws input {@code view} (1 to 4, {@link #NAMES}), {@code texture}, into {@code framebuffer}'s colour, alpha kept. */
    void draw(int view, int texture, int framebuffer, int width, int height) {
        if (program == 0) {
            program = FgCapture.link(FgCapture.VERTEX, FRAGMENT);
            vao = glGenVertexArrays();
            sampler = glGenSamplers();
            glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        GlSnapshot state = new GlSnapshot(1, 1);
        try {
            state.bind(0, texture, sampler);
            glProgramUniform1i(program, glGetUniformLocation(program, "cg_source"), state.units[0]);
            glProgramUniform1i(program, glGetUniformLocation(program, "cg_view"), view);
            glProgramUniform2i(program, glGetUniformLocation(program, "cg_size"), width, height);
            state.plainDraw(framebuffer, width, height, false);
            glUseProgram(program);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            state.restore();
        }
    }

    void close() {
        if (program != 0) {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteSamplers(sampler);
            program = 0;
        }
    }

    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D cg_source;
            uniform int cg_view;
            uniform ivec2 cg_size;
            out vec4 cg_out;
            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy);
                vec4 v = texelFetch(cg_source, ivec2(p.x, cg_size.y - 1 - p.y), 0);
                vec3 c;
                if (cg_view == 1) {
                    c = v.rgb;
                } else if (cg_view == 2) {
                    c = vec3(v.r);
                } else if (cg_view == 3) {
                    c = vec3(pow(max(v.r, 0.0), 0.25));
                } else {
                    vec2 m = v.rg;
                    c = clamp(vec3(max(m.x, 0.0), max(m.y, 0.0), max(-m.x, 0.0) + max(-m.y, 0.0)) / 4.0, 0.0, 1.0);
                }
                cg_out = vec4(c, 1.0);
            }
            """;
}

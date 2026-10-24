package dev.creategalore.bridge.dlss;

import java.nio.ByteBuffer;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DLAA motion vectors for Create's machines, contraptions, trains and Sable's ships drawn through Colorwheel (Flywheel
 * under Iris). The patched Photon's clrwl_solid program declares cg_flw_prev_pos, where Flywheel had the vertex last
 * frame; this fills it in, by editing the program Colorwheel puts together just before it compiles:
 * - Flywheel's clock (flw_renderTicks, flw_renderSeconds) can be moved back, so the instance code works out a
 *   spinning shaft's or fan's vertex at last frame's time ({@link ObjectMotion#flywheelTickDelta()});
 * - a contraption's, train's or ship's model matrix is last frame's for that second pass ({@link Pose}, uploaded per
 *   draw on the instancing backend and as a buffer beside Flywheel's own matrices on the indirect one).
 * Parts that Flywheel's instances move on the CPU each frame (train wheels, chest lids) keep the camera's motion only.
 * A Colorwheel this does not recognise compiles as it is: its programs then have no motion of their own.
 */
public final class ColorwheelMotion {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    static final String PREV_POS = "cg_flw_prev_pos";
    public static final String TICK_DELTA_UNIFORM = "_cg_flw_tick_delta";
    public static final String PREV_MODEL_UNIFORM = "_cg_flw_prevModelMatrixUniform";

    private static boolean warned;
    private static int binding = -1;
    private static ByteBuffer previous;
    private static int previousBytes;
    private static int buffer;
    private static int bufferBytes;

    private ColorwheelMotion() {}

    /** An embedded environment (contraption, train, ship) with its model matrix as drawn last frame. */
    public interface Pose {
        Matrix4f create_galore_bridge$previousPose();

        int create_galore_bridge$matrixIndex();
    }

    // Flywheel's clock, in the pass uniforms. The block stays as it is (the fragment shader declares the same one and
    // they must match); after it, the names lead to functions that can move the clock back.
    private static final String PASS_CLOCK = "    float flw_renderTicks;\n    float flw_renderSeconds;\n";
    private static final String PASS_END = "    uint flw_systemMillis;\n};\n";
    private static final String CLOCK = """

            // Create Galore DLAA: Flywheel's clock, moved back to last frame while last frame's position is worked out.
            uniform float _cg_flw_tick_delta;
            float _cg_flw_time_shift = 0.0;
            float _cg_flw_render_ticks() { return flw_renderTicks - _cg_flw_time_shift; }
            float _cg_flw_render_seconds() { return flw_renderSeconds - _cg_flw_time_shift / 20.0; }
            #define flw_renderTicks _cg_flw_render_ticks()
            #define flw_renderSeconds _cg_flw_render_seconds()
            """;
    private static final String COMMON_EMBEDDED = "#ifdef FLW_EMBEDDED\n    mat4 _flw_modelMatrix;\n    mat3 _flw_normalMatrix;\n";
    private static final String COMMON_SECOND_PASS = "    // ---\n\n    flw_instanceVertex(instance);\n    flw_materialVertex();\n";
    private static final String PREVIOUS_POSITION = """
                // Create Galore DLAA: where Flywheel had this vertex last frame, for the pack's program.
                _cg_flw_time_shift = _cg_flw_tick_delta;
                flw_instanceVertex(instance);
                flw_materialVertex();
                #ifdef FLW_EMBEDDED
                    cg_flw_prev_pos = vec4(((_cg_flw_prevModelMatrix[3][3] != 0.0 ? _cg_flw_prevModelMatrix : _flw_modelMatrix)
                        * flw_vertexPos).xyz, 1.0);
                #else
                    cg_flw_prev_pos = vec4(flw_vertexPos.xyz, 1.0);
                #endif
                _cg_flw_time_shift = 0.0;
                flw_vertexPos = flw_vertexPos_bkp;
                flw_vertexColor = flw_vertexColor_bkp;
                flw_vertexTexCoord = flw_vertexTexCoord_bkp;
                flw_vertexOverlay = flw_vertexOverlay_bkp;
                flw_vertexLight = flw_vertexLight_bkp;
                flw_vertexNormal = flw_vertexNormal_bkp;

            """;
    private static final String INSTANCING_UNIFORM = "    uniform mat4 _flw_modelMatrixUniform;\n";
    private static final String INSTANCING_SET = "        _flw_modelMatrix = _flw_modelMatrixUniform;\n";
    private static final String INDIRECT_BUFFER = "    FlwMatrices _flw_matrices[];\n};\n#endif\n";
    private static final String INDIRECT_SET = "            _flw_unpackMatrices(_flw_matrices[draw.matrixIndex], _flw_modelMatrix, _flw_normalMatrix);\n"
            + "        #endif\n";

    /** Colorwheel's whole source for one shader, just before it compiles: the pack's solid vertex program gets its motion. */
    public static String patchVertexSource(String source) {
        int declared = source.indexOf(PREV_POS);
        int main = source.indexOf("void _clrwl_main(");
        if (declared < 0 || main < 0) {
            return source;
        }
        // The pack's program (which declares cg_flw_prev_pos) has to come before Colorwheel's main that sets it.
        String out = declared < main ? source : null;
        int clock = out != null ? out.indexOf(PASS_CLOCK) : -1;
        if (clock < 0 || out.indexOf(PASS_END) < clock) {
            out = null;
        }
        out = insertAfter(out, PASS_END, CLOCK);
        out = insertAfter(out, COMMON_EMBEDDED, "    mat4 _cg_flw_prevModelMatrix;\n");
        out = insertBefore(out, COMMON_SECOND_PASS, PREVIOUS_POSITION);
        if (out != null && out.contains(INSTANCING_UNIFORM)) {
            out = insertAfter(out, INSTANCING_UNIFORM, "    uniform mat4 " + PREV_MODEL_UNIFORM + ";\n");
            out = insertAfter(out, INSTANCING_SET, "        _cg_flw_prevModelMatrix = " + PREV_MODEL_UNIFORM + ";\n");
        } else if (out != null) {
            out = insertAfter(out, INDIRECT_BUFFER, "\n#ifdef FLW_EMBEDDED\n"
                    + "// Create Galore DLAA: each matrix as it was last frame.\n"
                    + "layout(std430, binding = " + binding() + ") restrict readonly buffer _CgPreviousMatrixBuffer\n"
                    + "{\n    mat4 _cg_flw_prevMatrices[];\n};\n#endif\n");
            out = insertAfter(out, INDIRECT_SET, "        _cg_flw_prevModelMatrix = draw.matrixIndex < uint(_cg_flw_prevMatrices.length())\n"
                    + "            ? _cg_flw_prevMatrices[draw.matrixIndex] : _flw_modelMatrix;\n");
        }
        if (out == null) {
            if (!warned) {
                warned = true;
                LOG.warn("DLAA: this Colorwheel's shaders are not the ones the Bridge knows, Create's machines and contraptions "
                        + "get no motion vectors of their own (they still work)");
            }
            return source;
        }
        return out;
    }

    private static String replaceOnce(String source, String anchor, String replacement) {
        if (source == null) {
            return null;
        }
        int at = source.indexOf(anchor);
        if (at < 0 || source.indexOf(anchor, at + 1) >= 0) {
            return null;
        }
        return source.substring(0, at) + replacement + source.substring(at + anchor.length());
    }

    private static String insertAfter(String source, String anchor, String text) {
        return replaceOnce(source, anchor, anchor + text);
    }

    private static String insertBefore(String source, String anchor, String text) {
        return replaceOnce(source, anchor, text + anchor);
    }

    /** The storage buffer binding for last frame's matrices: the last one, clear of Flywheel's and the pack's. */
    private static int binding() {
        if (binding < 0) {
            binding = GL11C.glGetInteger(GL43C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1;
        }
        return binding;
    }

    /** Colorwheel flushed its environments' matrices: keep each one's last-frame matrix at its matrix index too. */
    public static void writePrevious(Iterable<?> environments, int capacity) {
        int bytes = capacity * 64;
        if (previous == null || previous.capacity() < bytes) {
            if (previous != null) {
                MemoryUtil.memFree(previous);
            }
            previous = MemoryUtil.memCalloc(Math.max(bytes, 64 * 64));
        }
        previousBytes = bytes;
        for (Object environment : environments) {
            if (!(environment instanceof Pose pose)) {
                continue;
            }
            int index = pose.create_galore_bridge$matrixIndex();
            if (index >= 0 && index < capacity) {
                pose.create_galore_bridge$previousPose().get(index * 64, previous);
            }
        }
    }

    /** The indirect backend uploaded its matrices: upload last frame's beside them. */
    public static void upload() {
        if (previous == null || previousBytes == 0) {
            return;
        }
        if (buffer == 0) {
            buffer = GL45C.glCreateBuffers();
        }
        ByteBuffer data = previous.duplicate().position(0).limit(previousBytes);
        if (bufferBytes != previousBytes) {
            GL45C.glNamedBufferData(buffer, data, GL15C.GL_DYNAMIC_DRAW);
            bufferBytes = previousBytes;
        } else {
            GL45C.glNamedBufferSubData(buffer, 0L, data);
        }
    }

    /** The indirect backend bound its matrices for drawing. */
    public static void bind() {
        if (buffer != 0 && bufferBytes > 0 && binding >= 0) {
            GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, binding, buffer, 0L, bufferBytes);
        }
    }
}

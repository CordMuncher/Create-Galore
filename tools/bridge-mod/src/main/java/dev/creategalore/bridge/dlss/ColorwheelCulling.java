package dev.creategalore.bridge.dlss;

import org.joml.Vector2f;

/**
 * Create's machines (shafts, cogwheels and the rest Colorwheel draws through Flywheel under Iris) vanished at some
 * angles with upscaling on. Colorwheel skips drawing a machine when a depth check says nearer things hide it: it
 * projects the machine onto the screen and reads that spot of a small copy of the depth buffer (the depth pyramid),
 * taking the buffer to hold the whole screen. With a render scale Photon draws the frame into the lower left corner of
 * its buffers instead (taau_render_scale), so each machine was checked against whatever showed further up and to the
 * right on screen, often a nearer block or tree, and was hidden in plain view.
 *
 * The check's screen position is scaled into that corner here: Colorwheel's culling program (as it puts it together,
 * just before it compiles) multiplies it by {@link #SCALE_UNIFORM}, which the Bridge sets to {@link Dlss#renderScale()}
 * whenever Colorwheel culls (1 at full resolution and with any other pack). A Colorwheel this does not recognise culls
 * as it is.
 */
public final class ColorwheelCulling {
    public static final String SCALE_UNIFORM = "_cg_render_scale";

    // Colorwheel's main-view occlusion test (the shadow one has its own and does not read the pack's buffers).
    private static final String TEST = "bool _clrwl_testOcclusionCull(";
    private static final String TO_UV = "    aabb = _clrwl_clipToUV(aabb);\n";

    private ColorwheelCulling() {}

    /** Colorwheel's whole source for one shader, just before it compiles: the main view's culling gets the render scale. */
    public static String patchCullSource(String source) {
        int test = source.indexOf(TEST);
        int toUv = source.indexOf(TO_UV);
        if (test < 0 || toUv < test || source.indexOf(TEST, test + 1) >= 0 || source.indexOf(TO_UV, toUv + 1) >= 0) {
            return source;
        }
        return source.substring(0, test)
                + "// Create Galore DLAA: the render size over the buffers' size, per axis (with upscaling the pack draws the\n"
                + "// frame into their lower left corner, so that is where the depth pyramid has the screen); 0 when not set.\n"
                + "uniform vec2 " + SCALE_UNIFORM + ";\n\n"
                + source.substring(test, toUv + TO_UV.length())
                + "    aabb *= (" + SCALE_UNIFORM + ".x > 0.0 ? " + SCALE_UNIFORM + " : vec2(1.0)).xyxy;\n"
                + source.substring(toUv + TO_UV.length());
    }

    /** The render scale for Colorwheel's culling: {@link Dlss#renderScale()}, or 1 while that is not known yet. */
    public static Vector2f scale() {
        Vector2f scale = Dlss.renderScale();
        if (!(scale.x > 0.0f && scale.x <= 1.0f) || !(scale.y > 0.0f && scale.y <= 1.0f)) {
            scale.set(1.0f, 1.0f);
        }
        return scale;
    }
}

package dev.creategalore.bridge.dlss;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;

/**
 * Named OpenGL debug groups around the Bridge's DLAA work (copying the inputs, DLSS or each FSR pass, writing the result
 * back), so GPU profilers (Nsight Systems and Graphics, RenderDoc) show what it costs. Only with
 * -Dcreate_galore_bridge.dlss.markers=true and a driver with KHR_debug; otherwise nothing is called.
 */
final class Markers {
    private static final boolean WANTED = Boolean.getBoolean("create_galore_bridge.dlss.markers");
    private static Boolean on;

    private Markers() {}

    /** Runs {@code work} inside a debug group called {@code name}. */
    static void around(String name, Runnable work) {
        if (!enabled()) {
            work.run();
            return;
        }
        KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, name);
        try {
            work.run();
        } finally {
            KHRDebug.glPopDebugGroup();
        }
    }

    private static boolean enabled() {
        if (on == null) {
            on = WANTED && GL.getCapabilities().GL_KHR_debug;
        }
        return on;
    }
}

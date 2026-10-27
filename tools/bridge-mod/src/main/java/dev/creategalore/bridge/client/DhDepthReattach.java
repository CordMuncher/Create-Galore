package dev.creategalore.bridge.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distant Horizons' terrain keeps its own depth buffer under Iris after a window resize (IrisDhDepthResizeMixin,
 * IrisDhDepthReattachMixin). On a resize Iris points every framebuffer of its render targets that has a depth
 * attachment at Minecraft's new depth buffer, Distant Horizons' too; Iris puts Distant Horizons' own depth back only
 * when its texture name changed, and Distant Horizons usually gets the same name again when it remakes it at the new
 * size. Its terrain then drew into the world's depth: after going in or out of full screen, its far terrain could stay
 * over the picture (upside down with Photon) until a shader reload. The same fix exists as the "Window Resize Fix
 * (Iris & Distant Horizons)" mod (mzg, MIT).
 */
public final class DhDepthReattach {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static volatile boolean needed;

    private DhDepthReattach() {}

    /** Iris just pointed its framebuffers at a new depth buffer of Minecraft's. */
    public static void depthReplaced() {
        needed = true;
    }

    /** True once after that: Distant Horizons' depth goes back on its framebuffers at its next frame. */
    public static boolean take() {
        if (!needed) {
            return false;
        }
        needed = false;
        LOG.info("Distant Horizons: its own depth buffer goes back on its framebuffers (Iris gave them the world's new one at the resize)");
        return true;
    }
}

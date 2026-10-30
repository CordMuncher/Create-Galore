package dev.creategalore.bridge.hud;

import static org.lwjgl.opengl.GL11C.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_COLOR;
import static org.lwjgl.opengl.GL11C.GL_ZERO;

import org.lwjgl.opengl.GL14C;

/**
 * The blend factors behind {@link HudSplit}'s coverage: while the HUD draws, the colour factors stay as the game set
 * them and only the alpha factors change, so the picture is the same and alpha collects how much the HUD covers.
 * A blend that covers what is underneath ("over", premultiplied, the crosshair's invert) adds its coverage:
 * alpha = src + dst * (1 - src alpha). One that only adds light (dst factor ONE) or darkens (multiplies) leaves
 * alpha as it is. Every blend change goes through here (see HudSplitBlendMixin), so this always knows what the
 * game asked for and can put it back exactly. Kept apart from HudSplit: GlStateManager loads it before the game has
 * a window.
 */
public final class HudSplitBlend {
    /** The factors as the game set them last: what OpenGL would hold without the split (its default to start). */
    private static int srcRgb = GL_ONE;
    private static int dstRgb = GL_ZERO;
    private static int srcAlpha = GL_ONE;
    private static int dstAlpha = GL_ZERO;
    private static boolean coverage;
    /** Set by the first blend change: the hook is in. */
    static boolean seen;

    private HudSplitBlend() {}

    /** GlStateManager._blendFunc's call to OpenGL (one factor pair for colour and alpha); true if this made it. */
    public static boolean blendFunc(int src, int dst) {
        return blendFuncSeparate(src, dst, src, dst);
    }

    /** GlStateManager.glBlendFuncSeparate's call to OpenGL; true if this made it. */
    public static boolean blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        seen = true;
        HudSplitBlend.srcRgb = srcRgb;
        HudSplitBlend.dstRgb = dstRgb;
        HudSplitBlend.srcAlpha = srcAlpha;
        HudSplitBlend.dstAlpha = dstAlpha;
        if (!coverage) {
            return false;
        }
        apply();
        return true;
    }

    static void begin() {
        coverage = true;
        apply();
    }

    static void end() {
        if (coverage) {
            coverage = false;
            GL14C.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    private static void apply() {
        boolean keep = dstRgb == GL_ONE || srcRgb == GL_ZERO || srcRgb == GL_DST_COLOR || dstRgb == GL_SRC_COLOR;
        GL14C.glBlendFuncSeparate(srcRgb, dstRgb, keep ? GL_ZERO : GL_ONE, keep ? GL_ONE : GL_ONE_MINUS_SRC_ALPHA);
    }
}

package dev.creategalore.bridge;

import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks for a 10-bit (30-bit colour) game window. Minecraft asks for 8 bits per colour, so on NVIDIA an exclusive
 * fullscreen window switches a 10-bit monitor down to 8-bit (black flash on entering fullscreen and on every Alt-Tab).
 * NVIDIA's "Prefer layered on DXGI Swapchain" present method avoids that but holds the GPU at about half use.
 * EXPERIMENTAL, OFF BY DEFAULT: tested 2026-10-16 on an RTX 4080 Super (driver 617.42), the driver granted a
 * 10/10/10 window but still switched the monitor to 8-bit on exclusive fullscreen, so it did not remove the flash.
 * Kept for other drivers and monitors. Colours are unchanged (still sRGB), only the precision goes up.
 *
 * The hint only works when NeoForge's early loading window is off (it creates the window before mods load), so when
 * it is on this switches it off in config/fml.toml for the next launch.
 *
 * Switch: config/create_galore_bridge-display.properties, tenBitWindow=true (default false).
 */
public final class TenBitWindow {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    private TenBitWindow() {}

    public static boolean enabled() {
        return DisplaySettings.get(DisplaySettings.TEN_BIT_WINDOW);
    }

    /** Called just before the game window is created. */
    public static void beforeCreate() {
        if (!enabled()) {
            LOG.info("10-bit window: off (config/{})", DisplaySettings.FILE);
            return;
        }
        if (dev.creategalore.bridge.present.Presentation.active()) {
            LOG.info("10-bit window: skipped, perfectPresentation shows the game through DirectX instead");
            return;
        }
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
            LOG.warn("10-bit window: NeoForge's early loading window already made an 8-bit window. "
                    + "Turned earlyWindowControl off in config/fml.toml; the 10-bit window takes effect after a restart.");
            return;
        }
        GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, 10);
        GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, 10);
        GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, 10);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 2);
        // Vanilla asks for OpenGL 3.2 core; NeoForge's early window would have asked for 4.6. Ask for 4.6 so
        // mods that check the version (Iris DSA, Veil, Distant Horizons) see the same context as usual.
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
        LOG.info("10-bit window: asking for 10/10/10/2 colour bits");
    }

    /** Called once the window and its GL context exist; logs what the driver actually gave. */
    public static void afterCreate() {
        if (!enabled() || dev.creategalore.bridge.present.Presentation.active()) {
            return;
        }
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            int r = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE);
            int g = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE);
            int b = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE);
            LOG.info("10-bit window: game window colour bits are {}/{}/{}", r, g, b);
        } catch (Throwable t) {
            LOG.info("10-bit window: could not read the window's colour bits ({})", t.toString());
        }
    }
}

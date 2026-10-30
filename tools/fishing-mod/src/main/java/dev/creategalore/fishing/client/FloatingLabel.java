package dev.creategalore.fishing.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Lines of text floating over a block like a name tag, each on a dark backing (the Cleaning Table's and the fish
 * mount's labels). The pose is already turned to the camera and scaled so one unit is a font pixel, y down.
 */
public final class FloatingLabel {
    private FloatingLabel() {}

    /**
     * Drawn the way the game draws a mob's name tag: backing and faint letters first without the depth test, then the
     * letters on top. Letters and backing in one normal pass sit only 0.01 apart, and shader packs (Photon,
     * Complementary) can't tell them apart: the backing covered the letters, leaving dark bars.
     */
    public static void draw(Font font, List<Component> lines, Matrix4f matrix, MultiBufferSource buffers, int light) {
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float x = -font.width(line) / 2f;
            font.drawInBatch(line, x, i * 11, 0x20ffffff, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH,
                    background, light);
            font.drawInBatch(line, x, i * 11, 0xffffffff, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        }
    }
}

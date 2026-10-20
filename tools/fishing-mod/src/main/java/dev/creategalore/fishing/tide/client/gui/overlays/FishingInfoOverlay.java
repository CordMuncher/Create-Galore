/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.overlays;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.config.TideClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class FishingInfoOverlay {
    public static Map<ResourceLocation, Component> CLIENT_INFO_DATA = Map.of();

    private static final int PADDING = 5;
    private static final int LINE_SPACING = 2;

    public static void render(GuiGraphics graphics, float dt) {
        Font font = Minecraft.getInstance().font;
        TideClientConfig.General.InfoPlacement placement = Tide.CLIENT_CONFIG.general.infoPlacement;
        if (placement == null) return;
        int offsetX = Tide.CLIENT_CONFIG.general.infoOffsetX;
        int offsetY = Tide.CLIENT_CONFIG.general.infoOffsetY;

        int i = 0;
        for (Map.Entry<ResourceLocation, Component> entry : CLIENT_INFO_DATA.entrySet()) {
            graphics.blit(
                    Tide.resource("textures/gui/info_icons/" + entry.getKey().getPath() + ".png"),
                    placement.isLeft()
                            ? PADDING + offsetX
                            : graphics.guiWidth() - PADDING - 9 + offsetX,
                    placement.isTop()
                            ? PADDING + (font.lineHeight + LINE_SPACING) * i + offsetY
                            : graphics.guiHeight() - PADDING - (font.lineHeight + LINE_SPACING) * i - 9 - offsetY,
                    0f, 0f, 9, 9, 9, 9
            );
            Component text = entry.getValue();
            graphics.drawString(
                    font, text,
                    placement.isLeft()
                            ? PADDING + 12 + offsetX
                            : graphics.guiWidth() - PADDING - 12 - font.width(text) - offsetX,
                    placement.isTop()
                            ? PADDING + (font.lineHeight + LINE_SPACING) * i + 1 + offsetY
                            : graphics.guiHeight() - PADDING - (font.lineHeight + LINE_SPACING) * i - font.lineHeight + 1 - offsetY,
                    0xffffff
            );
            i++;
        }
    }
}

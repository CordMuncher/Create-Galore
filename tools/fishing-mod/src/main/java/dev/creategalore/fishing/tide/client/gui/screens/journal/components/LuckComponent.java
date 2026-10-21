/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal.components;

import dev.creategalore.fishing.tide.client.gui.screens.journal.ProfileComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class LuckComponent extends ProfileComponent {
    private final Component text;

    public LuckComponent(int min, int max) {
        boolean hasMin = min > 0;
        boolean hasMax = max < Integer.MAX_VALUE;
        String range = Integer.toString(min);
        if (!hasMin && !hasMax) range = "any";
        else if (hasMin && hasMax) range = min + "-" + max;
        else if (hasMax) range = "<" + (max + 1);
        this.text = Component.translatable("journal.info.luck.title").append(": ").append(range);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        graphics.drawString(font, text, center - font.width(text) / 2, y, TEXT_COLOR, false);
    }

    @Override
    public int getRequiredHeight() {
        return 9;
    }
}

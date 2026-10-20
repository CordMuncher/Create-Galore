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
import dev.creategalore.fishing.tide.util.TideUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class HorizontalLineComponent extends ProfileComponent {
    private final boolean top;

    public HorizontalLineComponent(boolean top) {
        this.top = top;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {

        graphics.blitSprite(top ? ProfileComponent.LINE_TOP : ProfileComponent.LINE_BOTTOM, x, y + 4, ProfileComponent.AREA_WIDTH, 2);

    }

    @Override
    public int getRequiredHeight() {
        return 10;
    }
}

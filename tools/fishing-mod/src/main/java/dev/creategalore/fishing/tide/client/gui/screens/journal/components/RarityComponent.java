/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal.components;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.screens.journal.ProfileComponent;
import dev.creategalore.fishing.tide.data.journal.FishRarity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class RarityComponent extends ProfileComponent {
    private static final ResourceLocation STAR = Tide.resource("textures/gui/journal/star_note.png");

    public int numStars;

    public RarityComponent(FishRarity rarity) {
        this.numStars = rarity.getNumStars();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        int padding = 2;
        for (int i = 0; i < numStars; i++) {
            int cellSize = 7 + padding;
            graphics.blit(STAR, center - ((numStars - 1) * cellSize / 2) + (i * cellSize) - 4, y,
                    0, 0, 7, 6, 7, 6);
        }
    }

    @Override
    public int getRequiredHeight() {
        return 7;
    }
}

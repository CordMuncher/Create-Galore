/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal.components;
import dev.creategalore.fishing.tide.data.Season;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.screens.journal.ProfileComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public class SeasonsComponent extends ProfileComponent {
    private static final ResourceLocation SEASONS = Tide.resource("textures/gui/journal/seasons.png");
    private static final Component TITLE = Component.translatable("journal.info.seasons.title");

    public List<Season> seasons;

    public SeasonsComponent(List<Season> seasons) {
        this.seasons = seasons;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        graphics.drawString(font, TITLE, center - font.width(TITLE) / 2, y, TEXT_COLOR, false);

        int seasonCount = seasons.size();
        int seasonY = y + 12;
        int padding = 4;

        for (int i = 0; i < seasonCount; i++) {
            int cellSize = 8 + padding;
            int seasonX = center - ((seasonCount - 1) * cellSize / 2) + (i * cellSize) - 4;

            int offset = switch (seasons.get(i)) {
                case SPRING -> 0;
                case SUMMER -> 8;
                case FALL -> 16;
                case WINTER -> 24;
            };
            graphics.blit(SEASONS, seasonX, seasonY, offset, 0, 8, 8, 32, 8);

            if (mouseX >= seasonX && mouseX <= seasonX + 8 && mouseY >= seasonY && mouseY <= seasonY + 8)
                graphics.renderTooltip(font, Component.translatable("journal.info.seasons." + seasons.get(i).getSerializedName()), mouseX, mouseY);
        }
    }

    @Override
    public int getRequiredHeight() {
        return 24;
    }
}

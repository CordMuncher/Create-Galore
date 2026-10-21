/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal.components;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.screens.journal.ProfileComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public class WeatherComponent extends ProfileComponent {
    private static final ResourceLocation ICONS = Tide.resource("textures/gui/journal/weather.png");
    private static final Component TITLE = Component.translatable("journal.info.weather.title");

    public List<FishingCondition.WeatherType> weatherTypes;

    public WeatherComponent(List<FishingCondition.WeatherType> weatherTypes) {
        this.weatherTypes = weatherTypes;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        graphics.drawString(font, TITLE, center - font.width(TITLE) / 2, y, TEXT_COLOR, false);

        int count = weatherTypes.size();
        int spriteY = y + 12;
        int padding = 4;

        for (int i = 0; i < count; i++) {
            int cellSize = 10 + padding;
            int spriteX = center - ((count - 1) * cellSize / 2) + (i * cellSize) - 4;

            int offset = getOffset(weatherTypes.get(i)) * 10;
            graphics.blit(ICONS, spriteX, spriteY, offset, 0, 10, 10, 30, 10);

            if (mouseX >= spriteX && mouseX <= spriteX + 10 && mouseY >= spriteY && mouseY <= spriteY + 10)
                graphics.renderTooltip(font, Component.translatable("journal.info.weather." + weatherTypes.get(i).getSerializedName()), mouseX, mouseY);
        }
    }

    public static int getOffset(FishingCondition.WeatherType weatherType) {
        return switch (weatherType) {
            case CLEAR -> 0;
            case RAIN -> 1;
            case STORM -> 2;
        };
    }

    @Override
    public int getRequiredHeight() {
        return 26;
    }
}

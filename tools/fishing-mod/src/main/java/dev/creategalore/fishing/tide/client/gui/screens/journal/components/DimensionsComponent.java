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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.List;

public class DimensionsComponent extends ProfileComponent {
    private static final ResourceLocation DIMENSIONS = Tide.resource("textures/gui/journal/dimensions.png");
    private static final Component TITLE = Component.translatable("journal.info.dimensions.title");

    public List<ResourceKey<Level>> dimensions;

    public static boolean shouldCreate(FishingCondition.Dimensions condition, List<FishingCondition> others) {
        boolean isWater = others.stream().filter(cond -> cond instanceof FishingCondition.Medium)
                .findFirst().map(cond -> ((FishingCondition.Medium) cond).medium().equals("water")).orElse(false);
        if (isWater && condition.isOverworldOnly()) return false;
        return condition.dimensions().stream().anyMatch(DimensionsComponent::isKnown);
    }

    public DimensionsComponent(List<ResourceKey<Level>> dimensions) {
        this.dimensions = dimensions.stream().filter(DimensionsComponent::isKnown).toList();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        graphics.drawString(font, TITLE, center - font.width(TITLE) / 2, y, TEXT_COLOR, false);

        int count = dimensions.size();
        int spriteY = y + 12;
        int padding = 4;

        for (int i = 0; i < count; i++) {
            int cellSize = 10 + padding;
            int spriteX = center - ((count - 1) * cellSize / 2) + (i * cellSize) - 4;

            int offset = getOffset(dimensions.get(i));
            graphics.blit(DIMENSIONS, spriteX, spriteY, offset, 0, 10, 10, 30, 10);

            if (mouseX >= spriteX && mouseX <= spriteX + 10 && mouseY >= spriteY && mouseY <= spriteY + 10)
                graphics.renderTooltip(font, Component.translatable("journal.info.dimensions." + dimensions.get(i).location().getPath()), mouseX, mouseY);
        }
    }

    public static boolean isKnown(ResourceKey<Level> dimension) {
        return getOffset(dimension) >= 0;
    }

    public static int getOffset(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return 0;
        if (dimension == Level.NETHER) return 10;
        if (dimension == Level.END) return 20;
        return -1;
    }

    @Override
    public int getRequiredHeight() {
        return 26;
    }
}

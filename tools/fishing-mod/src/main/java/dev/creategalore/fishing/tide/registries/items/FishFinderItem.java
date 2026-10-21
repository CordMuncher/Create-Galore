/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public class FishFinderItem extends AbstractTooltipItem implements InfoItemContainer {
    private static final List<Item> CONTAINED = List.of(
            TideItems.POCKET_WATCH,
            TideItems.LUNAR_CALENDAR,
            TideItems.CLIMATE_GAUGE,
            TideItems.DEPTH_METER,
            TideItems.WEATHER_RADIO
    );

    public FishFinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
        Style gray = Component.empty().getStyle().withColor(ChatFormatting.GRAY);
        tooltip.accept(Component.translatable("item.tide.fish_finder.desc").setStyle(gray));
    }

    @Override
    public List<Item> getContainedInfoItems() {
        return CONTAINED;
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import dev.creategalore.fishing.tide.registries.TideArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

public class DragonfinBootsItem extends ArmorItem implements TooltipItem {
    public DragonfinBootsItem(Properties properties) {
        super(TideArmorMaterials.DRAGONFIN, ArmorItem.Type.BOOTS, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        this.addTooltip(stack, lines::add);
    }

    @Override
    public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
        Style gold = Component.empty().getStyle().withColor(ChatFormatting.GOLD);
        tooltip.accept(Component.translatable("item.tide.dragonfin_boots.desc_0").setStyle(gold));
        tooltip.accept(Component.translatable("item.tide.dragonfin_boots.desc_1").setStyle(gold));
    }
}

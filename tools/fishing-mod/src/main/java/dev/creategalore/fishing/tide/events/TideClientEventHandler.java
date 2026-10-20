/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.events;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.config.TideServerConfig;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.util.TideUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import java.util.List;

public class TideClientEventHandler {
    public static void onTooltipRender(ItemStack stack, List<Component> lines) {
        // (Tide's bait and rod lines are gone: our tackle tooltips describe bait and rods.)
        if (Minecraft.getInstance().level != null
                && TideItemData.IS_BUCKETABLE.getOrDefault(stack, false)
                && Tide.SERVER_CONFIG.items.bucketableFishItems == TideServerConfig.Items.BucketableMode.WHEN_LIVING)
            lines.add(Component.translatable("text.tide.fish.alive").withStyle(ChatFormatting.GRAY));

        if (TideItemData.FISH_LENGTH.isPresent(stack)) {
            lines.add(TideUtils.getFormattedLength(stack).withStyle(ChatFormatting.BLUE));
        }

        else if (stack.has(DataComponents.BUCKET_ENTITY_DATA)) {
            CustomData data = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            if (data.contains("Length")) {
                double length = data.copyTag().getDouble("Length");
                lines.add(TideUtils.getFormattedLength(length).withStyle(ChatFormatting.BLUE));
            }
        }

    }
}

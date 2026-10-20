/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.registries.TideItems;
import dev.creategalore.fishing.tide.registries.items.FishSatchelItem;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class TideItemModelProperties {
    public static final ResourceLocation PULLING_PROPERTY = Tide.resource("pulling");
    public static final ClampedItemPropertyFunction PULLING_FUNCTION = (stack, level, player, i) ->
            player != null && player.isUsingItem() && player.getUseItem() == stack ? 1.0f : 0.0f;
    public static final ResourceLocation PULL_PROPERTY = Tide.resource("pull");
    public static final ClampedItemPropertyFunction PULL_FUNCTION = (stack, level, player, i) -> {
        if (player == null) return 0f;
        return player.getUseItem() != stack ? 0f : (float)(stack.getUseDuration(player) - player.getUseItemRemainingTicks()) / 20f;
    };

    public static final ResourceLocation SATCHEL_STATE_PROPERTY = Tide.resource("satchel_state");
    public static final ClampedItemPropertyFunction SATCHEL_STATE_FUNCTION = (stack, level, player, i) -> {
        boolean isOpen = TideItemData.FISH_SATCHEL_OPENED.getOrDefault(stack, false);
        int fishCount = FishSatchelItem.getFishCount(stack);
        if (isOpen) {
            if (fishCount > 0) return fishCount > 1 ? 1f : 0.6f;
            return 0.3f;
        }
        return 0f;
    };

    public static void registerAll() {

        ItemProperties.register(TideItems.STARLIGHT_BOW, PULLING_PROPERTY, PULLING_FUNCTION);
        ItemProperties.register(TideItems.STARLIGHT_BOW, PULL_PROPERTY, PULL_FUNCTION);

        ItemProperties.register(TideItems.FISH_SATCHEL, SATCHEL_STATE_PROPERTY, SATCHEL_STATE_FUNCTION);
    }
}

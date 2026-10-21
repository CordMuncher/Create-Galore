/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.loot;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.config.TideServerConfig;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.registries.TideLootFunctions;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

public class RandomizeFishLengthFunction implements LootItemFunction {
    public static final MapCodec<RandomizeFishLengthFunction> CODEC = MapCodec.unit(RandomizeFishLengthFunction::new);

    public RandomizeFishLengthFunction() {}

    public LootItemFunctionType<RandomizeFishLengthFunction> getType() {
        return TideLootFunctions.RANDOMIZE_FISH_LENGTH;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        if (Tide.SERVER_CONFIG.items.fishItemSizes != TideServerConfig.Items.SizeMode.ALWAYS
                || FishData.get(stack).map(FishData::size).isEmpty()) return stack;
        FishData.get(stack).ifPresent(data -> TideItemData.FISH_LENGTH
                .set(stack, data.getRandomLength(context.getRandom())));
        return stack;
    }
}

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
import dev.creategalore.fishing.tide.data.FishLengthHolder;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.registries.TideLootFunctions;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.Set;

public class ApplyFishEntityLengthFunction implements LootItemFunction {
    public static final MapCodec<ApplyFishEntityLengthFunction> CODEC = MapCodec.unit(ApplyFishEntityLengthFunction::new);

    public ApplyFishEntityLengthFunction() {}

    public LootItemFunctionType<ApplyFishEntityLengthFunction> getType() {
        return TideLootFunctions.APPLY_FISH_ENTITY_LENGTH;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        if (Tide.SERVER_CONFIG.items.fishItemSizes != TideServerConfig.Items.SizeMode.ALWAYS
                || FishData.get(stack).map(FishData::size).isEmpty()) return stack;
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity == null) return stack;
        if (entity instanceof FishLengthHolder lengthHolder) {
            TideItemData.FISH_LENGTH.set(stack, lengthHolder.tide$getLength());
        }
        return stack;
    }
}

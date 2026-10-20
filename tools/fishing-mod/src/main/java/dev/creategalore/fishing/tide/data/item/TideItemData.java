/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.item;

import dev.creategalore.fishing.tide.data.ItemDataKey;
import dev.creategalore.fishing.tide.data.rods.BaitContents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class TideItemData {

    public static final ItemDataKey<Double> FISH_LENGTH = new ComponentItemDataKey<>(TideDataComponents.FISH_LENGTH);
    public static final ItemDataKey<Boolean> IS_BUCKETABLE = new ComponentItemDataKey<>(TideDataComponents.IS_BUCKETABLE);
    public static final ItemDataKey<CompoundTag> FISHING_LINE = new ComponentItemDataKey<>(TideDataComponents.FISHING_LINE);
    public static final ItemDataKey<CompoundTag> FISHING_BOBBER = new ComponentItemDataKey<>(TideDataComponents.FISHING_BOBBER);
    public static final ItemDataKey<CompoundTag> FISHING_HOOK = new ComponentItemDataKey<>(TideDataComponents.FISHING_HOOK);
    public static final ItemDataKey<BaitContents> BAIT_CONTENTS = new ComponentItemDataKey<>(TideDataComponents.BAIT_CONTENTS);
    public static final ItemDataKey<ResourceKey<Item>> FISHY_NOTE_VARIANT = new ComponentItemDataKey<>(TideDataComponents.FISHY_NOTE_VARIANT);
    public static final ItemDataKey<SatchelContents> SATCHEL_CONTENTS = new ComponentItemDataKey<>(TideDataComponents.SATCHEL_CONTENTS);
    public static final ItemDataKey<Boolean> FISH_SATCHEL_OPENED = new ComponentItemDataKey<>(TideDataComponents.FISH_SATCHEL_OPENED);

}

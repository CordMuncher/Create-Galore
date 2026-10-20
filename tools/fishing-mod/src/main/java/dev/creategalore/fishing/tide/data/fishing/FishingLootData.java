/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing: Tide's record and codec; selection as in CrateData.
 */
package dev.creategalore.fishing.tide.data.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingModifier;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A junk or treasure table for the spots where its conditions hold ({@code data/<ns>/fishing/loot/*.json}): lava
 * junk, cave junk, Nether and trial-chamber treasure and so on. Treasure has a positive quality (luck helps), junk a
 * negative one.
 */
public record FishingLootData(ResourceKey<LootTable> lootTable,
                              List<String> associatedMods,
                              List<FishingCondition> conditions,
                              List<FishingModifier> modifiers,
                              double weight, double quality) {

    public static final Codec<FishingLootData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(FishingLootData::lootTable),
            Codec.STRING.listOf().optionalFieldOf("associated_mods", List.of()).forGetter(FishingLootData::associatedMods),
            FishingCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(FishingLootData::conditions),
            FishingModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(FishingLootData::modifiers),
            Codec.DOUBLE.optionalFieldOf("weight", 0.0).forGetter(FishingLootData::weight),
            Codec.DOUBLE.optionalFieldOf("quality", 0.0).forGetter(FishingLootData::quality)
    ).apply(instance, FishingLootData::new));

    public boolean isTreasure() {
        return quality > 0;
    }

    public double weightAt(FishingContext context) {
        return CatchTables.weightAt(conditions, modifiers, weight, quality, context);
    }
}

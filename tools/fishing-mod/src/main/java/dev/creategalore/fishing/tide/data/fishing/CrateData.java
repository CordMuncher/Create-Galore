/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing: Tide's record and codec, with the selection reduced to what our bite roll
 * needs (Tide put crates, loot and fish in one weighted pool; we roll crates separately).
 */
package dev.creategalore.fishing.tide.data.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingModifier;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A loot crate that can be snagged where its conditions hold ({@code data/<ns>/fishing/crates/*.json}): the crate
 * block, the loot table that fills it, and its weight among the crates that fit the spot.
 */
public record CrateData(BlockStateProvider blockProvider,
                        Optional<ResourceKey<LootTable>> lootTable,
                        List<String> associatedMods,
                        List<FishingCondition> conditions,
                        List<FishingModifier> modifiers,
                        double weight, double quality) {

    public static final Codec<CrateData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockStateProvider.CODEC.fieldOf("block").forGetter(CrateData::blockProvider),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(CrateData::lootTable),
            Codec.STRING.listOf().optionalFieldOf("associated_mods", List.of()).forGetter(CrateData::associatedMods),
            FishingCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(CrateData::conditions),
            FishingModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(CrateData::modifiers),
            Codec.DOUBLE.optionalFieldOf("weight", 0.0).forGetter(CrateData::weight),
            Codec.DOUBLE.optionalFieldOf("quality", 0.0).forGetter(CrateData::quality)
    ).apply(instance, CrateData::new));

    /** This crate's weight at the spot (0 when a condition fails): weight + quality x luck, then the modifiers. */
    public double weightAt(FishingContext context) {
        return CatchTables.weightAt(conditions, modifiers, weight, quality, context);
    }
}

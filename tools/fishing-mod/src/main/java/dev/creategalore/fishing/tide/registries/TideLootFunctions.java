/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.loot.ApplyFishEntityLengthFunction;
import dev.creategalore.fishing.tide.data.loot.RandomizeFishLengthFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

import java.util.HashMap;

public class TideLootFunctions {
    public static final HashMap<String, LootItemFunctionType<?>> LOOT_FUNCTIONS = new HashMap<>();

    public static final LootItemFunctionType<ApplyFishEntityLengthFunction> APPLY_FISH_ENTITY_LENGTH = register(
            "apply_fish_entity_length", new LootItemFunctionType<>(ApplyFishEntityLengthFunction.CODEC));
    public static final LootItemFunctionType<RandomizeFishLengthFunction> RANDOMIZE_FISH_LENGTH = register(
            "randomize_fish_length", new LootItemFunctionType<>(RandomizeFishLengthFunction.CODEC));

    public static <T extends LootItemFunction> LootItemFunctionType<T> register(String key, LootItemFunctionType<T> type) {
        LOOT_FUNCTIONS.put(key, type);
        return Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Tide.resource(key), type);
    }

    public static void init() {}
}

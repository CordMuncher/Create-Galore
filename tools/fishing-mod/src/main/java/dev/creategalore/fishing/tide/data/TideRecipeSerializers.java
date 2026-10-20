/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import dev.creategalore.fishing.tide.Tide;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public class TideRecipeSerializers {
    public static final RecipeSerializer<? extends CraftingRecipe> FISHY_NOTE_RECIPE = register(
            "fishy_note_recipe", new SimpleCraftingRecipeSerializer<>(FishyNoteRecipe::new));

    public static RecipeSerializer<? extends CraftingRecipe> register(String key, RecipeSerializer<? extends CraftingRecipe> serializer) {
        return Tide.PLATFORM.register(BuiltInRegistries.RECIPE_SERIALIZER, Tide.resource(key), serializer);
    }

    public static void init() {}
}

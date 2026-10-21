/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import dev.creategalore.fishing.tide.registries.items.FishyNoteItem;
import dev.creategalore.fishing.tide.util.TideUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import net.minecraft.core.HolderLookup;

public class FishyNoteRecipe extends CustomRecipe {
    public FishyNoteRecipe(CraftingBookCategory category) {
        super(category);
    }

    private ItemStack getFishToConvert(CraftingInput input) {
        ItemStack stack1 = null, stack2 = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack inSlot = input.getItem(i);
            if (inSlot.isEmpty()) continue;
            if (stack1 == null) stack1 = inSlot;
            else {
                if (stack2 != null) return null;
                stack2 = inSlot;
            }
        }

        return stack1 != null && stack2 != null ? getFishFromInputs(stack1, stack2) : null;
    }

    private static ItemStack getFishFromInputs(ItemStack stack1, ItemStack stack2) {
        if (TideUtils.isJournalFish(stack1) && stack2.is(Items.PAPER)) return stack1;
        if (TideUtils.isJournalFish(stack2) && stack1.is(Items.PAPER)) return stack2;
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return getFishToConvert(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries
                                       ) {
        ItemStack fish = getFishToConvert(input);
        if (fish == null || fish.isEmpty()) return ItemStack.EMPTY;
        return FishyNoteItem.create(new ItemStack(fish.getItem()));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height > 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TideRecipeSerializers.FISHY_NOTE_RECIPE;
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.compat.hybridaquatic;

import dev.hybridlabs.aquatic.entity.fish.StingrayEntity;
import dev.hybridlabs.aquatic.entity.fish.StingrayEntity.Companion.Type;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Hybrid Aquatic's fish on a fish display: its stingrays come in two patterns, told apart by the item. Only loaded with Hybrid Aquatic. */
public class HybridAquaticCompat {
    // Im not sure how to change this appropriately but its probably not needed anymore -Mystic
    public static void applyVariant(Entity entity, ItemStack stack) {
        if (entity instanceof StingrayEntity stingray) {
            if (BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .getPath().contains("blue")) stingray.setVariant(Type.BLUE_SPOTTED);
            else stingray.setVariant(Type.SPOTTED_EAGLE);
        }
    }
}
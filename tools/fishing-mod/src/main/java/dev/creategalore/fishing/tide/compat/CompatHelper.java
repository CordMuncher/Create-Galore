/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.compat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import dev.creategalore.fishing.tide.compat.hybridaquatic.HybridAquaticCompat;
import dev.creategalore.fishing.tide.Tide;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import dev.creategalore.fishing.tide.compat.accessories.CuriosCompat;

/**
 * Tide's optional-mod hooks. Kept: the Curios slot for the information gadgets and Hybrid Aquatic's display variants: the rest served Tide's rod and
 * minigame (Hybrid Aquatic hooks, Starcatcher, Stardew Fishing, Fishing Real), which our own rod replaces.
 */
public class CompatHelper {
    // -- hybrid aquatic --

    public static boolean isHybridAquaticLoaded() {
        return Tide.PLATFORM.isModLoaded("hybrid_aquatic");
    }

    public static void hybridAquaticApplyVariant(Entity entity, ItemStack stack) {
        HybridAquaticCompat.applyVariant(entity, stack);
    }

    // -- curios --

    public static void addInformationItemsFromAccessories(Player player, ArrayList<Item> items) {
        if (Tide.PLATFORM.isModLoaded("curios")) CuriosCompat.addInformationalItems(player, items);
    }
}

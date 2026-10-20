/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.compat.accessories;

import dev.creategalore.fishing.tide.data.TideTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;

public class CuriosCompat {
    public static void addInformationalItems(Player player, ArrayList<Item> items) {
        var itemHandler = CuriosApi.getCuriosInventory(player);
        itemHandler.ifPresent(handler -> handler
                .findCurios(stack -> stack.is(TideTags.Items.INFORMATIONAL))
                .forEach(result -> {
                    Item item = result.stack().getItem();
                    if (!items.contains(item)) items.add(item);
                }));
    }
}

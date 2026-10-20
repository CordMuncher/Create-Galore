/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import dev.creategalore.fishing.tide.network.messages.ViewNoteMsg;
import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class FishyNoteItem extends Item {
    public static final int MAX_REROLLS = 2;

    public FishyNoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ItemStack fish) {
        ItemStack note = new ItemStack(TideItems.FISHY_NOTE);
        var key = BuiltInRegistries.ITEM.getResourceKey(fish.getItem());
        key.ifPresent(value -> TideItemData.FISHY_NOTE_VARIANT.set(note, value));
        return note;
    }

    public static void finalizeData(ItemStack note, ServerPlayer player) {
        if (!TideItemData.FISHY_NOTE_VARIANT.isPresent(note)) {
            var key = BuiltInRegistries.ITEM.getResourceKey(getRandomFish(player).getItem());
            key.ifPresent(value -> TideItemData.FISHY_NOTE_VARIANT.set(note, value));
        }
    }

    private static ItemStack getRandomFish(ServerPlayer player) {
        TidePlayerData data = TidePlayerData.getOrCreate(player);
        for (int i = 0; i < MAX_REROLLS; i++) {
            ItemStack fish = FishData.randomFish();
            if (!data.isFishUnlocked(fish) && !data.hasNote(fish)) return fish;
        }
        return FishData.randomFish();
    }

    public static ItemStack getFish(ItemStack note) {
        ResourceKey<Item> key = getFishKey(note);
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static ResourceKey<Item> getFishKey(ItemStack note) {
        return TideItemData.FISHY_NOTE_VARIANT.getOrDefault(note,
                BuiltInRegistries.ITEM.getResourceKey(Items.SALMON).orElseThrow());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack note = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            finalizeData(note, serverPlayer);
            TideItemData.FISHY_NOTE_VARIANT.set(note, getFishKey(note));

            int slot = player.getInventory().findSlotMatchingItem(note);
            player.getInventory().setItem(slot, note);
            Tide.NETWORK.sendToPlayer(new ViewNoteMsg(getFish(note)), serverPlayer);

            TidePlayerData data = TidePlayerData.getOrCreate(serverPlayer);
            Item fish = BuiltInRegistries.ITEM.get(getFishKey(note));
            if (fish != null) {
                data.markNoteUnlocked(fish);
                data.syncTo(serverPlayer);
            }
        }
        return InteractionResultHolder.success(note);
    }
}

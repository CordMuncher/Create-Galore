/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.network.messages;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ReadProfileMsg(int fish) implements TidePacketPayload {
    public static final ResourceLocation ID = Tide.resource("read_profile");
    @Override public ResourceLocation id() { return ID; }

    public ReadProfileMsg(ItemStack fishItem) {
        this(Item.getId(fishItem.getItem()));
    }

    public ReadProfileMsg(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public static void encode(ReadProfileMsg message, FriendlyByteBuf buf) {
        buf.writeInt(message.fish);
    }

    public static void handle(ReadProfileMsg message, Player player) {
        TidePlayerData data = TidePlayerData.getOrCreate((ServerPlayer) player);
        data.markAsRead(new ItemStack(Item.byId(message.fish)));
        data.syncTo((ServerPlayer) player);
    }
}
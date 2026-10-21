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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record SyncPlayerDataMsg(CompoundTag tag) implements TidePacketPayload {
    public static final ResourceLocation ID = Tide.resource("sync_player_data");
    @Override public ResourceLocation id() { return ID; }

    public SyncPlayerDataMsg(FriendlyByteBuf buf) {
        this(buf.readNbt());
    }

    public static void encode(SyncPlayerDataMsg message, FriendlyByteBuf buf) {
        buf.writeNbt(message.tag);
    }

    public static void handle(SyncPlayerDataMsg message, Player player) {
        TidePlayerData.CLIENT_DATA = new TidePlayerData(message.tag);
    }
}
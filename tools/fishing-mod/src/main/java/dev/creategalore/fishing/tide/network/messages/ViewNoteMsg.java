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
import dev.creategalore.fishing.tide.client.TideClientHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record ViewNoteMsg(ItemStack stack) implements TidePacketPayload {
    public static final ResourceLocation ID = Tide.resource("view_note");
    @Override public ResourceLocation id() { return ID; }

    public ViewNoteMsg(FriendlyByteBuf buf) {
        this(buf.readJsonWithCodec(ItemStack.SIMPLE_ITEM_CODEC));
    }

    public static void encode(ViewNoteMsg message, FriendlyByteBuf buf) {
        buf.writeJsonWithCodec(ItemStack.SIMPLE_ITEM_CODEC, message.stack);
    }

    public static void handle(ViewNoteMsg message, Player player) {
        TideClientHelper.openNoteScreen(message.stack);
    }
}
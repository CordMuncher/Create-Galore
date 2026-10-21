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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record ShowToastMsg(Component title, Component description, ItemStack display) implements TidePacketPayload {
    public static final ResourceLocation ID = Tide.resource("show_toast");
    @Override public ResourceLocation id() { return ID; }

    public ShowToastMsg(FriendlyByteBuf buf) {
        this(Component.translatable(buf.readUtf()), Component.translatable(buf.readUtf()), BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.tryParse(buf.readUtf())).orElseThrow().getDefaultInstance());
    }

    public static void encode(ShowToastMsg message, FriendlyByteBuf buf) {
        buf.writeUtf(message.title.getString());
        buf.writeUtf(message.description.getString());
        buf.writeUtf(BuiltInRegistries.ITEM.getKey(message.display.getItem()).toString());
    }

    public static void handle(ShowToastMsg message, Player player) {
        TideClientHelper.showToast(message.title, message.description, message.display);
    }
}
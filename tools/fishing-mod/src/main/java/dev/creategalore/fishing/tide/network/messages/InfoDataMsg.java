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
import dev.creategalore.fishing.tide.client.gui.overlays.FishingInfoOverlay;
import dev.creategalore.fishing.tide.registries.items.InformationalItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public record InfoDataMsg(Map<ResourceLocation, String> data) implements TidePacketPayload {
    public static final ResourceLocation ID = Tide.resource("survey_data");
    @Override public ResourceLocation id() { return ID; }

    public InfoDataMsg(FriendlyByteBuf buf) {
        this(fromBuffer(buf));
    }

    private static Map<ResourceLocation, String> fromBuffer(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Map<ResourceLocation, String> data = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            data.put(buf.readResourceLocation(), buf.readUtf());
        }
        return data;
    }

    public static void encode(InfoDataMsg message, FriendlyByteBuf buf) {
        buf.writeInt(message.data.size());
        message.data.forEach((item, data) -> {
            buf.writeResourceLocation(item);
            buf.writeUtf(data);
        });
    }

    public static void handle(InfoDataMsg message, Player player) {
        Map<ResourceLocation, Component> results = new HashMap<>();
        message.data().forEach((key, data) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            results.put(key, ((InformationalItem)item).parseResult(data));
        });
        FishingInfoOverlay.CLIENT_INFO_DATA = results;
    }
}
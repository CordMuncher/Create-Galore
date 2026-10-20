/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;
import java.util.function.Function;

public interface NetworkPlatform {
    <T extends CustomPacketPayload> void registerClientBoundPacket(Class<T> msgClass, ResourceLocation id,
                                                                   BiConsumer<T, RegistryFriendlyByteBuf> encoder,
                                                                   Function<RegistryFriendlyByteBuf, T> decoder,
                                                                   BiConsumer<T, Player> handler);

    <T extends CustomPacketPayload> void registerServerBoundPacket(Class<T> msgClass, ResourceLocation id,
                                                                   BiConsumer<T, RegistryFriendlyByteBuf> encoder,
                                                                   Function<RegistryFriendlyByteBuf, T> decoder,
                                                                   BiConsumer<T, Player> handler);

    void sendToPlayer(CustomPacketPayload message, ServerPlayer player);

    void sendToServer(CustomPacketPayload message);

    default void registerHandlers() {}

    record ClientMsgRegistration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                                BiConsumer<T, Player> handler) {}
}

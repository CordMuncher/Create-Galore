/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders.neoforge;

import dev.creategalore.fishing.tide.loaders.NetworkPlatform;
import dev.creategalore.fishing.tide.network.TideMessages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class NeoforgeNetworkPlatform implements NetworkPlatform {
    @Override
    public <T extends CustomPacketPayload> void registerClientBoundPacket(Class<T> msgClass, ResourceLocation id,
                                                                          BiConsumer<T, RegistryFriendlyByteBuf> encodeFunc,
                                                                          Function<RegistryFriendlyByteBuf, T> decodeFunc,
                                                                          BiConsumer<T, Player> handler) {
        NeoforgeEntrypoint.REGISTRAR.playToClient(
            this.idToType(id),
            StreamCodec.of(
                    (buf, msg) -> encodeFunc.accept(msg, buf),
                    decodeFunc::apply
            ),
            (msg, context) -> handler.accept(msg, context.player())
        );
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerBoundPacket(Class<T> msgClass, ResourceLocation id,
                                                                          BiConsumer<T, RegistryFriendlyByteBuf> encodeFunc,
                                                                          Function<RegistryFriendlyByteBuf, T> decodeFunc,
                                                                          BiConsumer<T, Player> handler) {
        NeoforgeEntrypoint.REGISTRAR.playToServer(
                this.idToType(id),
                StreamCodec.of(
                        (buf, msg) -> encodeFunc.accept(msg, buf),
                        decodeFunc::apply
                ),
                (msg, context) -> handler.accept(msg, context.player())
        );
    }

    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> CustomPacketPayload.Type<T> idToType(ResourceLocation id) {
        return (CustomPacketPayload.Type<T>) TideMessages.ID_TO_TYPE.computeIfAbsent(id, CustomPacketPayload.Type::new);
    }

    @Override
    public void sendToPlayer(CustomPacketPayload message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }

    @Override
    public void sendToServer(CustomPacketPayload message) {
        PacketDistributor.sendToServer(message);
    }
}

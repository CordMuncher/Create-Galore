package dev.creategalore.fishing.net;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.CatchHolding;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the angler clicked Keep ({@code release} false) or Release on the held fish. */
public record CatchChoice(boolean release) implements CustomPacketPayload {
    public static final Type<CatchChoice> TYPE = new Type<>(CreateGaloreFishing.id("catch_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CatchChoice> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, CatchChoice::release, CatchChoice::new);

    @Override
    public Type<CatchChoice> type() {
        return TYPE;
    }

    public static void handle(CatchChoice payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) CatchHolding.decide(player, payload.release());
    }
}

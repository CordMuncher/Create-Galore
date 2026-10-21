package dev.creategalore.fishing.net;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.RodLift;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player started or stopped lifting their rod (attack held while a line is out). */
public record LiftInput(boolean lifting) implements CustomPacketPayload {
    public static final Type<LiftInput> TYPE = new Type<>(CreateGaloreFishing.id("lift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LiftInput> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, LiftInput::lifting, LiftInput::new);

    @Override
    public Type<LiftInput> type() {
        return TYPE;
    }

    public static void handle(LiftInput payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) RodLift.set(player, payload.lifting());
    }
}

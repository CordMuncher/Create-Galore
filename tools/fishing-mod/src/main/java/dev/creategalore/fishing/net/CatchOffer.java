package dev.creategalore.fishing.net;

import dev.creategalore.fishing.CreateGaloreFishing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: the angler is holding a freshly landed fish; open the Keep / Release screen. Everything the
 * screen shows comes with it, so the client needs no fish data of its own.
 *
 * @param aliveUntil game time when the fish dies (after which it can only be kept)
 * @param bestGrams  the angler's best of this species so far, 0 if none
 */
public record CatchOffer(ItemStack fish, String rarity, String size, float kg, float cm, int keepXp, int releaseXp,
                         long aliveUntil, int bestGrams) implements CustomPacketPayload {
    public static final Type<CatchOffer> TYPE = new Type<>(CreateGaloreFishing.id("catch_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CatchOffer> STREAM_CODEC =
            StreamCodec.ofMember(CatchOffer::write, CatchOffer::read);

    private void write(RegistryFriendlyByteBuf buf) {
        ItemStack.STREAM_CODEC.encode(buf, fish);
        buf.writeUtf(rarity);
        buf.writeUtf(size);
        buf.writeFloat(kg);
        buf.writeFloat(cm);
        buf.writeVarInt(keepXp);
        buf.writeVarInt(releaseXp);
        buf.writeVarLong(aliveUntil);
        buf.writeVarInt(bestGrams);
    }

    private static CatchOffer read(RegistryFriendlyByteBuf buf) {
        return new CatchOffer(ItemStack.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt());
    }

    @Override
    public Type<CatchOffer> type() {
        return TYPE;
    }
}

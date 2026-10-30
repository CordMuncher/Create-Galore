package dev.creategalore.fishing.net;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.sim.SizeModel;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Server to client: each fish item's sizes (lengths and length-weight curve), so a small fish's tooltip can give
 * the weights its size class spans. The curve comes from our species files, which the Tide fish data in
 * {@link FishDataSync} doesn't carry. Sent with it, on join and after {@code /reload}.
 */
public record SizeSync(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SizeSync> TYPE = new Type<>(CreateGaloreFishing.id("fish_sizes"));

    record Entry(ResourceLocation item, double low, double high, double record, double a, double b) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::item,
                ByteBufCodecs.DOUBLE, Entry::low,
                ByteBufCodecs.DOUBLE, Entry::high,
                ByteBufCodecs.DOUBLE, Entry::record,
                ByteBufCodecs.DOUBLE, Entry::a,
                ByteBufCodecs.DOUBLE, Entry::b,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SizeSync> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SizeSync::entries, SizeSync::new);

    /** What the client knows, by item (a merged fish's item maps to its parent's sizes). */
    private static volatile Map<Item, SizeModel> sizes = Map.of();

    /** The sizes for every fish item, merged ones included. */
    public static SizeSync of(Map<Item, Species> byItem) {
        List<Entry> entries = new ArrayList<>();
        byItem.forEach((item, species) -> {
            SizeModel s = species.size();
            entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item), s.typicalLow(), s.typicalHigh(), s.record(), s.a(), s.b()));
        });
        return new SizeSync(entries);
    }

    /** On the client, an integrated server's host included (the client never reads the server's tables). */
    public static void handle(SizeSync payload) {
        Map<Item, SizeModel> out = new IdentityHashMap<>();
        for (Entry e : payload.entries()) {
            BuiltInRegistries.ITEM.getOptional(e.item())
                    .ifPresent(item -> out.put(item, new SizeModel(e.low(), e.high(), e.record(), e.a(), e.b())));
        }
        sizes = out;
    }

    /** Client side: the sizes of a fish item, if the server sent them. */
    public static Optional<SizeModel> sizes(Item item) {
        return Optional.ofNullable(sizes.get(item));
    }

    @Override
    public Type<SizeSync> type() {
        return TYPE;
    }
}

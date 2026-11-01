package dev.creategalore.bridge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Create - Xaero's Map draws Sable ships on the minimap and world map. When a ship leaves a player's view while it
 * stands still, the map keeps its picture where it was last seen (also on disk) and lets it go only once the player
 * comes within 48 blocks of that spot. The client can't tell a ship that is still there from one that is gone, so:
 * <ul>
 * <li>Aeronautics Discovery's flyover airships drift out of range, stop (nothing steers them outside the ticking
 * area), get stored unloaded and only despawn when their land loads again. Their pictures stayed on the map for good,
 * dozens of them along every route a player had travelled, high in the air where nobody comes within 48 blocks.</li>
 * <li>A ship broken up, taken apart or cleared away out of a player's view stayed on their map too.</li>
 * </ul>
 * The server knows both, so it tells each player's map which ships to forget: every passing flyover (they are never
 * worth a remembered picture) and every ship that is gone for good. A player's own ship parked out of view keeps its
 * picture, as before. Ships removed while a player was away are kept in a short list with the world, so their map
 * forgets them at the next login.
 */
public final class ShipMapForget {
    private static final Logger LOG = LogUtils.getLogger();
    /** How many removed ships each dimension remembers for players who were away (flyovers aren't counted). */
    static final int MAX_REMEMBERED = 2048;

    private ShipMapForget() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client without this Bridge version still joins; its map just keeps stale ships.
            var registrar = event.registrar("1").optional();
            registrar.playToClient(Forget.TYPE, Forget.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.ShipMapGone.onForget(payload));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
        });
    }

    /**
     * From Sable, as a ship leaves the server's list: {@code removed} when it is gone for good, otherwise it is only
     * being stored with its land.
     */
    public static void onShipLeaving(ServerLevel level, UUID id, boolean removed) {
        if (id == null) return;
        try {
            boolean flyover = isFlyover(level, id);
            if (!removed && !flyover) return; // a ship stored with its land is still there: its picture stays
            if (removed && !flyover) Gone.get(level).add(id);
            PacketDistributor.sendToPlayersInDimension(level, new Forget(level.dimension().location(), false, List.of(id)));
        } catch (RuntimeException e) {
            LOG.warn("Create Galore: couldn't tell the maps about ship {}", id, e);
        }
    }

    private static boolean isFlyover(ServerLevel level, UUID id) {
        return FlyoverManager.get(level).getEntry(id) != null;
    }

    /** The full list for the player's dimension: ships removed there, and every flyover still out there. */
    private static void sendAll(ServerPlayer player) {
        try {
            ServerLevel level = player.serverLevel();
            List<UUID> ids = new ArrayList<>(Gone.get(level).ids);
            ids.addAll(FlyoverManager.get(level).getAllFlyovers().keySet());
            PacketDistributor.sendToPlayer(player, new Forget(level.dimension().location(), true, ids));
        } catch (RuntimeException e) {
            LOG.warn("Create Galore: couldn't send the gone ships to {}", player.getGameProfile().getName(), e);
        }
    }

    /** Server to client: ships the map should not keep a picture of in {@code dim} ({@code reset}: the full list). */
    public record Forget(ResourceLocation dim, boolean reset, List<UUID> ids) implements CustomPacketPayload {
        public static final Type<Forget> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "ship_map_forget"));
        static final StreamCodec<FriendlyByteBuf, Forget> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeResourceLocation(f.dim);
            buf.writeBoolean(f.reset);
            buf.writeVarInt(f.ids.size());
            for (UUID id : f.ids) buf.writeUUID(id);
        }, buf -> {
            ResourceLocation dim = buf.readResourceLocation();
            boolean reset = buf.readBoolean();
            int n = buf.readVarInt();
            if (n < 0 || n > 1 << 16) throw new IllegalArgumentException("too many ships: " + n);
            List<UUID> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readUUID());
            return new Forget(dim, reset, ids);
        });

        @Override
        public Type<Forget> type() {
            return TYPE;
        }
    }

    /** Ships removed in one dimension, newest last, kept with that dimension's data. */
    private static final class Gone extends SavedData {
        private static final String NAME = "create_galore_gone_ships";
        final LinkedHashSet<UUID> ids = new LinkedHashSet<>();

        static Gone get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Gone::new, Gone::load, null), NAME);
        }

        void add(UUID id) {
            ids.remove(id);
            ids.add(id);
            while (ids.size() > MAX_REMEMBERED) ids.remove(ids.iterator().next());
            setDirty();
        }

        private static Gone load(CompoundTag tag, HolderLookup.Provider registries) {
            Gone data = new Gone();
            for (Tag t : tag.getList("ships", Tag.TAG_INT_ARRAY)) data.ids.add(NbtUtils.loadUUID(t));
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (UUID id : ids) list.add(NbtUtils.createUUID(id));
            tag.put("ships", list);
            return tag;
        }
    }
}

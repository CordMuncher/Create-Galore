package dev.creategalore.bridge;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Logging out while sitting on a Create seat on a Sable ship (an airship, a boat, a car, a coaster cart) brought you
 * back standing next to the seat. Sable puts a returning player back on the ship at their logout point, and to do that
 * it drops the saved vehicle from the player data (its tracking_points EntityMixin removes RootVehicle), so the game
 * never seats them again. A seat on the ground was fine.
 *
 * The entity-load mixin notes the seat block before Sable drops it; once the player is in the world, Sable has let go
 * of them (it holds a player in place until their ship has loaded) and the seat is loaded, empty and within a few
 * blocks, the player sits down on it again the way a click on the seat does.
 */
public final class ShipSeatRelog {
    private static final int GIVE_UP_TICKS = 400;
    private static final double MAX_DISTANCE_SQ = 6 * 6;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(BlockPos seat, int[] ticks) {
    }

    private ShipSeatRelog() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShipSeatRelog::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> PENDING.remove(event.getEntity().getUUID()));
    }

    /** Called from the player's Entity.load, before Sable's mixin removes RootVehicle. */
    public static void noteSavedSeat(ServerPlayer player, CompoundTag tag) {
        if (!tag.contains("LoginPoint") || !tag.contains("RootVehicle", Tag.TAG_COMPOUND)) return;
        CompoundTag root = tag.getCompound("RootVehicle");
        CompoundTag seat = root.getCompound("Entity");
        if (!"create:seat".equals(seat.getString("id")) || !seat.hasUUID("UUID") || !root.hasUUID("Attach")
            || !seat.getUUID("UUID").equals(root.getUUID("Attach"))) return;
        ListTag pos = seat.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() != 3) return;
        BlockPos seatPos = BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
        PENDING.put(player.getUUID(), new Pending(seatPos, new int[1]));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || PENDING.isEmpty()) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        if (player.isPassenger() || !player.isAlive() || ++pending.ticks()[0] > GIVE_UP_TICKS) {
            PENDING.remove(player.getUUID());
            return;
        }
        // Sable holds the player at the logout point until their ship has loaded; wait for it to let go.
        if (player instanceof PlayerFreezeExtension frozen && frozen.sable$getFrozenToSubLevel() != null) return;
        var level = player.serverLevel();
        BlockPos seat = pending.seat();
        if (!level.isLoaded(seat) || Sable.HELPER.getContaining(level, seat) == null) return;
        PENDING.remove(player.getUUID());
        if (!(level.getBlockState(seat).getBlock() instanceof SeatBlock) || SeatBlock.isSeatOccupied(level, seat)) return;
        Vec3 centre = Vec3.atBottomCenterOf(seat);
        if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.position(), centre.x, centre.y, centre.z) > MAX_DISTANCE_SQ) return;
        SeatBlock.sitDown(level, seat, player);
    }
}

package dev.creategalore.bridge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Create never saves a player's seat on a moving contraption (AbstractContraptionEntity.hasExactlyOnePlayerPassenger
 * is false, so the contraption stays in the world instead of travelling in the player data). Logging out while seated
 * on a bearing, cart, gantry or train seat therefore brought you back standing at the contraption's anchor.
 *
 * At logout the contraption and seat index are noted in the player data. Once the player is back, that contraption is
 * loaded, the seat is free and either the seat or the contraption is within a few blocks, the player sits down on it
 * again the way a click on the seat does. A contraption that has moved on (a train that left) is left alone.
 */
public final class ContraptionSeatRelog {
    private static final String KEY = "create_galore_bridge:contraption_seat";
    private static final int GIVE_UP_TICKS = 400;
    private static final double MAX_DISTANCE_SQ = 8 * 8;

    private ContraptionSeatRelog() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ContraptionSeatRelog::onLogout);
        NeoForge.EVENT_BUS.addListener(ContraptionSeatRelog::onTick);
    }

    /** Runs before the player is saved and before they are taken off the contraption. */
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CompoundTag data = event.getEntity().getPersistentData();
        data.remove(KEY);
        if (!(event.getEntity().getVehicle() instanceof AbstractContraptionEntity contraption) || contraption.getContraption() == null) return;
        Integer seat = contraption.getContraption().getSeatMapping().get(event.getEntity().getUUID());
        if (seat == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Entity", contraption.getUUID());
        tag.putInt("Seat", seat);
        data.put(KEY, tag);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY)) return;
        CompoundTag tag = data.getCompound(KEY);
        int ticks = tag.getInt("Ticks") + 1;
        if (player.isPassenger() || !player.isAlive() || ticks > GIVE_UP_TICKS || !tag.hasUUID("Entity")) {
            data.remove(KEY);
            return;
        }
        tag.putInt("Ticks", ticks);
        // The contraption's chunk may load its entities a little after the player arrives.
        if (!(player.serverLevel().getEntity(tag.getUUID("Entity")) instanceof AbstractContraptionEntity contraption)
            || contraption.getContraption() == null) return;
        data.remove(KEY);
        int seat = tag.getInt("Seat");
        List<BlockPos> seats = contraption.getContraption().getSeats();
        if (seat < 0 || seat >= seats.size() || contraption.getContraption().getSeatMapping().containsValue(seat)) return;
        Vec3 seatPos = contraption.toGlobalVector(Vec3.atCenterOf(seats.get(seat)), 1f);
        if (player.distanceToSqr(seatPos) > MAX_DISTANCE_SQ && player.distanceToSqr(contraption) > MAX_DISTANCE_SQ) return;
        contraption.addSittingPassenger(player, seat);
    }
}

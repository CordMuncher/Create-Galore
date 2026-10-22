package dev.creategalore.bridge;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Create never saves a player's seat on a moving contraption (AbstractContraptionEntity.hasExactlyOnePlayerPassenger
 * is false, so the contraption stays in the world instead of travelling in the player data). Logging out while seated
 * on a bearing, cart, gantry or train seat therefore brought you back standing at the contraption's anchor, and a train
 * running a schedule had left by then, dropping you onto the track (or into the sea under a viaduct).
 *
 * At logout the contraption, seat index and (for a train) train and carriage are noted in the player data. Once the
 * player is back and the seat is free, they sit down on it again the way a click on the seat does, wherever the
 * contraption is in their dimension: you were riding it, so this is no free travel. A train carriage whose chunk is not
 * loaded any more is found through Create's train list and the player is moved to it so it loads. While waiting, a
 * player with no ground under them falls slowly; if the contraption is gone they stay where they logged out.
 */
public final class ContraptionSeatRelog {
    private static final String KEY = "create_galore_bridge:contraption_seat";
    private static final int GIVE_UP_TICKS = 400;
    /** However slowly the client loads, stop trying after this long. */
    private static final int HARD_LIMIT_TICKS = 3 * 60 * 20;
    private static final int RETRY_TICKS = 20;
    /** Ticks after moving the player before sitting them down, so their client has the contraption by then. */
    private static final int SETTLE_TICKS = 5;
    private static final double NEAR_SQ = 8 * 8;
    /** A moving train goes on while the player settles; this much drift still counts as arrived. */
    private static final double MOVED_NEAR_SQ = 24 * 24;
    private static final int GROUND_CHECK = 4;

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
        if (contraption instanceof CarriageContraptionEntity carriage && carriage.trainId != null) {
            tag.putUUID("Train", carriage.trainId);
            tag.putInt("Carriage", carriage.carriageIndex);
        }
        data.put(KEY, tag);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY)) return;
        CompoundTag tag = data.getCompound(KEY);
        if (player.isPassenger() || !player.isAlive() || !tag.hasUUID("Entity")) {
            data.remove(KEY);
            return;
        }
        int age = tag.getInt("Age") + 1;
        tag.putInt("Age", age);
        // A client still loading the world ignores a move and keeps reporting where it joined; that time does not count.
        boolean bounced = tag.contains("TX")
            && player.distanceToSqr(tag.getDouble("TX"), tag.getDouble("TY"), tag.getDouble("TZ")) > MOVED_NEAR_SQ;
        int ticks = tag.getInt("Ticks") + (bounced ? 0 : 1);
        tag.putInt("Ticks", ticks);
        if (ticks > GIVE_UP_TICKS || age > HARD_LIMIT_TICKS) {
            data.remove(KEY);
            if (tag.getBoolean("Feather")) player.removeEffect(MobEffects.SLOW_FALLING);
            keepFromFalling(player, 600);
            return;
        }
        if (age == 1 && keepFromFalling(player, HARD_LIMIT_TICKS + 100)) tag.putBoolean("Feather", true);
        int sinceMoved = tag.contains("Moved") ? age - tag.getInt("Moved") : Integer.MAX_VALUE;
        int settle = bounced ? RETRY_TICKS : SETTLE_TICKS;
        ServerLevel level = player.serverLevel();

        // The contraption's chunk may load its entities a little after the player arrives.
        if (!(level.getEntity(tag.getUUID("Entity")) instanceof AbstractContraptionEntity contraption)
            || contraption.getContraption() == null) {
            // A train that drove on while its chunks were unloaded: go to where Create says the carriage is now.
            Vec3 anchor = carriageAnchor(level, tag);
            if (anchor != null && sinceMoved >= settle && player.distanceToSqr(anchor) > NEAR_SQ) moveTo(player, level, anchor, tag, age);
            return;
        }
        int seat = tag.getInt("Seat");
        List<BlockPos> seats = contraption.getContraption().getSeats();
        if (seat < 0 || seat >= seats.size() || contraption.getContraption().getSeatMapping().containsValue(seat)) {
            data.remove(KEY);
            return;
        }
        Vec3 seatPos = contraption.toGlobalVector(Vec3.atCenterOf(seats.get(seat)), 1f);
        if (sinceMoved < settle) return;
        if (player.distanceToSqr(seatPos) > (sinceMoved == Integer.MAX_VALUE ? NEAR_SQ : MOVED_NEAR_SQ)) {
            moveTo(player, level, seatPos, tag, age);
            return;
        }
        data.remove(KEY);
        contraption.addSittingPassenger(player, seat);
        if (tag.getBoolean("Feather")) player.removeEffect(MobEffects.SLOW_FALLING);
    }

    private static void moveTo(ServerPlayer player, ServerLevel level, Vec3 pos, CompoundTag tag, int age) {
        player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        tag.putInt("Moved", age);
        tag.putDouble("TX", pos.x);
        tag.putDouble("TY", pos.y);
        tag.putDouble("TZ", pos.z);
    }

    private static Vec3 carriageAnchor(ServerLevel level, CompoundTag tag) {
        if (!tag.hasUUID("Train")) return null;
        Train train = Create.RAILWAYS.sided(level).trains.get(tag.getUUID("Train"));
        int index = tag.getInt("Carriage");
        if (train == null || index < 0 || index >= train.carriages.size()) return null;
        Carriage.DimensionalCarriageEntity dce = train.carriages.get(index).getDimensionalIfPresent(level.dimension());
        return dce == null ? null : dce.positionAnchor;
    }

    /** Slow Falling for a player with nothing solid a few blocks under them (or no loaded chunk); true if given. */
    private static boolean keepFromFalling(ServerPlayer player, int duration) {
        BlockPos feet = player.blockPosition();
        for (int dy = 0; dy <= GROUND_CHECK; dy++) {
            BlockPos below = feet.below(dy);
            if (!player.level().isLoaded(below)) break;
            if (!player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty()) return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0, false, false, true));
        return true;
    }
}

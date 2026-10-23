package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ShipHold;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create Deep Seas keeps the sea out of a ship's dry hold for anyone whose eyes or feet are in a hold cell, but a
 * doorway, a stair or a slab is part of the hull, not the hold, and most decks sit a little under the waterline (the
 * launch in Emre's world 0.3 blocks). Walking through an open cabin door, standing on a stair or a feet position a
 * hair under the deck (the server sees players up to 0.1 low on a moving ship) left both points outside the hold for
 * a moment: the game put the player in the sea, so Cold Sweat soaked them on a dry deck. Also count the middle of the
 * body and just above the feet, and a partial block of the hull that opens onto the hold (see ShipHold).
 * <p>
 * A player who logs in on a ship stands at the ship's last spot in the sea for the few ticks until it loads (Sable
 * holds them there), and one tick of that soaked them as much as a swim. While Sable holds them for a ship, and for
 * 2 s after while they stand inside that ship and Deep Seas has not looked at its hold yet, they are on board.
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class ShipHoldDryMixin {
    @Unique private static final long create_galore_bridge$BOARDING_TICKS = 40;
    @Unique private UUID create_galore_bridge$boarding;
    @Unique private long create_galore_bridge$boardingUntil;

    @Dynamic("Added by Create Deep Seas' EntityWaterPhysicsMixin")
    @Inject(method = "createsubmarine$isInsideAirtightSub", at = @At("RETURN"), cancellable = true, remap = false)
    private void create_galore_bridge$holdDoorways(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        long now = self.level().getGameTime();
        if (self instanceof PlayerFreezeExtension frozen && frozen.sable$getFrozenToSubLevel() != null) {
            create_galore_bridge$boarding = frozen.sable$getFrozenToSubLevel();
            create_galore_bridge$boardingUntil = now + create_galore_bridge$BOARDING_TICKS;
            cir.setReturnValue(true);
            return;
        }
        if (cir.getReturnValueZ()) return;
        if (ShipHold.inDryHold(self)) {
            cir.setReturnValue(true);
        } else if (create_galore_bridge$boarding != null) {
            if (now <= create_galore_bridge$boardingUntil && ShipHold.boarding(self, create_galore_bridge$boarding)) cir.setReturnValue(true);
            else if (now > create_galore_bridge$boardingUntil) create_galore_bridge$boarding = null;
        }
    }
}

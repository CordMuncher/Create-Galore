package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ShipSeatRelog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes a player's ship seat before Sable drops it from the player data on login (see ShipSeatRelog). */
@Mixin(Entity.class)
public abstract class ShipSeatRelogMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private void create_galore_bridge$noteShipSeat(CompoundTag tag, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) ShipSeatRelog.noteSavedSeat(player, tag);
    }
}

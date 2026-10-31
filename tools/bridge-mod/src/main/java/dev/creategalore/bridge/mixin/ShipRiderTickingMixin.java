package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipRiderTicking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After Sable reads a position packet, other players standing on a ship are kept ticking (see {@link ShipRiderTicking}). */
@Mixin(ClientPacketListener.class)
public abstract class ShipRiderTickingMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleMoveEntity", at = @At("TAIL"))
    private void create_galore_bridge$moved(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (this.level != null) ShipRiderTicking.update(packet.getEntity(this.level));
    }

    @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
    private void create_galore_bridge$teleported(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (this.level != null) ShipRiderTicking.update(this.level.getEntity(packet.getId()));
    }
}

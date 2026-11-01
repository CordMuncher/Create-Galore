package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ShipMapForget;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link ShipMapForget}: every ship leaving the server's list, removed for good or stored with its land. */
@Mixin(value = ServerSubLevelContainer.class, remap = false)
public abstract class SableShipMapForgetMixin {
    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("HEAD"))
    private void create_galore_bridge$forgetOnMaps(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci) {
        ServerSubLevelContainer self = (ServerSubLevelContainer) (Object) this;
        SubLevel ship = self.getSubLevel(x, z);
        if (ship != null) ShipMapForget.onShipLeaving(self.getLevel(), ship.getUniqueId(), reason == SubLevelRemovalReason.REMOVED);
    }
}

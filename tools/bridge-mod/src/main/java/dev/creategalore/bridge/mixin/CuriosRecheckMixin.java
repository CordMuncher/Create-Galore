package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.wear.Wear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Curios re-checks every worn item when it sets up a player's slots; the wear screen's limits stay out of it. */
@Mixin(targets = "top.theillusivec4.curios.common.capability.CurioInventory", remap = false)
public abstract class CuriosRecheckMixin {
    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private void create_galore_bridge$start(CallbackInfo ci) {
        Wear.rechecking(true);
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void create_galore_bridge$end(CallbackInfo ci) {
        Wear.rechecking(false);
    }
}

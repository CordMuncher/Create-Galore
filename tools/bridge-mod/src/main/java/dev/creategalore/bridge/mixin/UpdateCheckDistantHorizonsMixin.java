package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons has its own auto updater: at startup it asks Modrinth for a newer jar and opens a screen that
 * offers to swap its own jar for it, which would take the mod off the version the pack tested. The pack's
 * DistantHorizons.toml turns it off for new installs, but that file is kept as the player left it, so instances that
 * already have it still say yes. SelfUpdater.onStart decides whether there is an update; it now always says no.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.jar.updater.SelfUpdater", remap = false)
public abstract class UpdateCheckDistantHorizonsMixin {
    @Inject(method = "onStart", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noSelfUpdate(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Collective asks for a newer version of itself and of every Serilum mod that registers with it (Starter Kit and
 * others) and logs "has an update available" for each. Only the log sees it, but the pack pins its mods, so the
 * check is off like every other mod's. Its own setting sits in Collective's config file with its other options.
 */
@Mixin(targets = "com.natamus.collective_common_neoforge.check.RegisterMod", remap = false)
public abstract class UpdateCheckCollectiveMixin {
    @Inject(method = "isUpdateCheckerEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

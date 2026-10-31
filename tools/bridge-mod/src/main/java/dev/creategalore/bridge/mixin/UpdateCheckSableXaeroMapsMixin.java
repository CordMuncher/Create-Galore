package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pack pins every mod until it has been tested with the rest, so no mod may tell players to update it.
 * Create - Xaero's Map (Sable Sublevels on Xaero's Maps) asks CurseForge for a newer file from its client tick and
 * prints "Version x is available ... Get it on CurseForge" in chat a few seconds after joining a world. Its switch
 * for this is a constant, not a setting, so the tick that starts the check and prints the line does nothing.
 */
@Mixin(targets = "com.sablexaeromaps.UpdateCheck", remap = false)
public abstract class UpdateCheckSableXaeroMapsMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }
}

package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Dupe Fix asks Modrinth for a newer version on the server and tells players about it in chat when they
 * join. Its switch sits in a config file that also holds every one of its fixes, so rather than ship that whole
 * file the pack turns the check and the join message off here. The pack pins its mods.
 */
@Mixin(targets = "avionics.create_dupe_fix.UpdateChecker", remap = false)
public abstract class UpdateCheckDupeFixMixin {
    @Inject(method = "checkForUpdates", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onPlayerJoin", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noJoinMessage(CallbackInfo ci) {
        ci.cancel();
    }
}

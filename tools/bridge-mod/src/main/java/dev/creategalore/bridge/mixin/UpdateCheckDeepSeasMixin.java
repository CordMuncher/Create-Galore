package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create: Deep Seas asks Modrinth for a newer version when the game starts and, if there is one, opens a full
 * screen with its changelog and an update link over the title menu. It has no setting for this. The pack pins its
 * mods, so the check never runs and the screen never sees an update.
 */
@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.system.UpdateChecker", remap = false)
public abstract class UpdateCheckDeepSeasMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "isUpdateAvailable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdate(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

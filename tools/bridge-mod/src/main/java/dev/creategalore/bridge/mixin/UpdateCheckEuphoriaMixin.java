package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Euphoria Patches asks Modrinth for a newer version at startup and writes "update available" into the shader
 * pack's own in-game message and its settings screen header. The setting for it lives in a file with all of its
 * other options, so the pack turns the check off here: nothing is fetched and every "should update" answer is no.
 * The pack pins its mods.
 */
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.util.UpdateChecker", remap = false)
public abstract class UpdateCheckEuphoriaMixin {
    @Inject(method = "checkForUpdates", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = {"isUpdateAvailable", "shouldUserUpdate", "isNewerCompVersionAvailable", "isImportantUpdate",
            "isPatchUpdateRecommended"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdate(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

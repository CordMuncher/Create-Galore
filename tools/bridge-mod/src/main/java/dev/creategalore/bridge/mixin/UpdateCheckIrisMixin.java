package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris asks for a newer version at startup and then prints an update line in chat on joining a world and on its
 * shader pack screen. Its own switch (disableUpdateMessage) lives in iris.properties next to the player's shader
 * pack choice, a file the pack must not overwrite. So the check never starts: with no answer, every update getter
 * Iris has returns nothing. The pack pins its mods.
 */
@Mixin(targets = "net.irisshaders.iris.UpdateChecker", remap = false)
public abstract class UpdateCheckIrisMixin {
    @Inject(method = "checkForUpdates", at = @At("HEAD"), cancellable = true, remap = false)
    private void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }
}

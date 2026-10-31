package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's Minimap and Xaero's World Map each ask Xaero's site for their newest version at startup and then flag
 * "outdated" with an update button in their menus. Their "Update Notifications" options sit in the same files as
 * the player's map and minimap settings, which the pack must not overwrite, so the version fetch never runs.
 * The pack pins its mods.
 */
@Mixin(targets = {"xaero.common.misc.Internet", "xaero.map.misc.Internet"}, remap = false)
public abstract class UpdateCheckXaeroMixin {
    @Inject(method = "checkModVersion", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$noUpdateCheck(CallbackInfo ci) {
        ci.cancel();
    }
}

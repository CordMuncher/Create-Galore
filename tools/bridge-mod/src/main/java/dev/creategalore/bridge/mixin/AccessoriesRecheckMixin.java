package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.wear.Wear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Accessories re-checks every worn item at join and after /reload; the wear screen's limits stay out of it. */
@Mixin(targets = "io.wispforest.accessories.impl.AccessoriesEventHandler", remap = false)
public abstract class AccessoriesRecheckMixin {
    @Inject(method = "revalidatePlayer", at = @At("HEAD"), remap = false)
    private static void create_galore_bridge$start(CallbackInfo ci) {
        Wear.rechecking(true);
    }

    @Inject(method = "revalidatePlayer", at = @At("RETURN"), remap = false)
    private static void create_galore_bridge$end(CallbackInfo ci) {
        Wear.rechecking(false);
    }
}

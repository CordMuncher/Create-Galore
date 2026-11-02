package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.PatPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While PatPat's pat keys include the use key, PatPat neither swallows the use key ({@code canPat} is what its key
 * mixin asks before cancelling a key press) nor pats from its own tick: the click goes to the game, and
 * {@link PatUseItemMixin} decides there whether the mob's own interaction or the pat gets it (see {@link PatPriority}).
 */
@Mixin(targets = "net.lopymine.patpat.client.manager.PatPatClientManager", remap = false)
public abstract class PatPatUseKeyMixin {
    @Inject(method = "canPat", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$keepUseKey(CallbackInfoReturnable<Boolean> cir) {
        if (PatPriority.sharesUseKey()) cir.setReturnValue(false);
    }

    @Inject(method = "requestPat", at = @At("HEAD"), cancellable = true, remap = false)
    private static void create_galore_bridge$patOnUse(CallbackInfo ci) {
        if (PatPriority.sharesUseKey()) ci.cancel();
    }
}

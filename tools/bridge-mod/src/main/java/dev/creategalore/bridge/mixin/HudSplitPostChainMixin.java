package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.hud.HudSplit;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A full-screen effect that runs during the HUD (the menu blur) makes the whole frame HUD (see {@link HudSplit}). */
@Mixin(PostChain.class)
public abstract class HudSplitPostChainMixin {
    @Shadow
    public abstract String getName();

    @Inject(method = "process", at = @At("RETURN"))
    private void create_galore_bridge$processed(float partialTick, CallbackInfo ci) {
        HudSplit.afterPostChain(getName());
    }
}

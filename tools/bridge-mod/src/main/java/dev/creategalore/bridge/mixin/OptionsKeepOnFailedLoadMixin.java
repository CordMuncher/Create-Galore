package dev.creategalore.bridge.mixin;

import net.minecraft.client.Options;
import net.neoforged.fml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a mod fails to load, the game only shows the loading error screen, but it still writes options.txt. By then the
 * mods' key bindings were never added and the resource packs that come with mods never showed up, so the file loses
 * every mod key binding and the whole resource pack list, and the next good start begins without them. A start that
 * failed to load mods now leaves options.txt as it was.
 */
@Mixin(Options.class)
public abstract class OptionsKeepOnFailedLoadMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$keepOnFailedLoad(CallbackInfo ci) {
        if (ModLoader.hasErrors()) ci.cancel();
    }
}

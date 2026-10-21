package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.TextInputGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory Profiles Next (through libIPN) runs its hotkeys on every key, even one typed into JEI's search box, so
 * typing an R there sorted the inventory. libIPN still records the key as held, so its chords stay in step, but no
 * hotkey runs while a text box has focus. Mouse buttons (negative key codes) go through as before, and so does
 * everything on IPN's own settings screens, where a key can be in the middle of being bound.
 */
@Mixin(targets = "org.anti_ad.mc.common.input.GlobalInputHandler", remap = false)
public abstract class IpnHotkeysWhileTypingMixin {
    @Shadow
    private static int lastKey;

    @Inject(method = "onInput(Lorg/anti_ad/mc/common/gui/NativeInputContextBase;)Z", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$notWhileTyping(CallbackInfoReturnable<Boolean> cir) {
        Screen screen = Minecraft.getInstance().screen;
        if (lastKey < 0 || screen == null || screen.getClass().getName().startsWith("org.anti_ad.")) return;
        if (TextInputGuard.isTyping()) cir.setReturnValue(false);
    }
}

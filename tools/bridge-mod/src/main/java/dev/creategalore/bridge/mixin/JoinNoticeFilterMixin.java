package dev.creategalore.bridge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two mods print a notice in chat on every join with no setting to turn it off: Create Train Utilities (its v4
 * rename, which this pack doesn't take) and Create Aeronautics: Transmission & Linkage (a Toolgun recommendation).
 * System messages and client-side notices both end up in ChatComponent.addMessage(Component), so drop those two
 * there. Player chat goes through the three-argument overload and is never touched.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent")
public abstract class JoinNoticeFilterMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$dropJoinNotices(Component message, CallbackInfo ci) {
        if (message.getContents() instanceof TranslatableContents t
                && t.getKey().startsWith("message.aeronautics_utility_objects.toolgun_")) {
            ci.cancel();
            return;
        }
        if (message.getString().startsWith("ATTENTION (Create Train Utilities)")) {
            ci.cancel();
        }
    }
}

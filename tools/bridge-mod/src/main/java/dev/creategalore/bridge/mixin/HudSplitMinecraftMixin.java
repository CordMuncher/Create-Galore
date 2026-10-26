package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.hud.HudSplit;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The HUD split's frame ends just before the frame goes to the window (or Perfect Presentation's swap chain). */
@Mixin(Minecraft.class)
public abstract class HudSplitMinecraftMixin {
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=blit"))
    private void create_galore_bridge$frameEnd(boolean renderLevel, CallbackInfo ci) {
        HudSplit.frameEnd();
    }
}

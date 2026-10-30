package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.hud.HudSplit;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where the HUD starts and ends, and the point just after the vignette where {@link HudSplit} copies the world. */
@Mixin(Gui.class)
public abstract class HudSplitGuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void create_galore_bridge$hudStart(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        HudSplit.hudStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void create_galore_bridge$hudEnd(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        HudSplit.hudEnd(graphics);
    }

    @Inject(method = "renderCameraOverlays", at = @At("HEAD"))
    private void create_galore_bridge$cameraOverlays(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        HudSplit.cameraOverlaysStart(graphics);
    }

    @Inject(method = "renderCameraOverlays", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/Gui;renderVignette(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;)V"))
    private void create_galore_bridge$afterVignette(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        HudSplit.afterVignette(graphics);
    }
}

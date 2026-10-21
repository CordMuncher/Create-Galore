package dev.creategalore.fishing.mixin;

import dev.creategalore.fishing.tackle.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla drops the item in hand out of view and raises it again every time use is pressed on it, as if it had just
 * been picked up. A rod is pressed on all the time (each cast, each spell of reeling), so it stays where it is.
 */
@Mixin(value = ItemInHandRenderer.class, remap = false)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "itemUsed(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void cgfish$keepRodUp(InteractionHand hand, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getItemInHand(hand).getItem() instanceof RodItem) ci.cancel();
    }
}

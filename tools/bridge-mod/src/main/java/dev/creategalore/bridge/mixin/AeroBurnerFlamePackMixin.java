package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.bridge.client.BurnerFlame;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under a shader pack the lit burner's flame is drawn by BurnerFlame through the pack instead of straight onto the
 * screen (Photon showed it as a see-through hole off to the side). The renderer's own draw then skips.
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerRenderer", remap = false)
public abstract class AeroBurnerFlamePackMixin {
    @Inject(method = "renderSafe(Ldev/eriksonn/aeronautics/content/blocks/hot_air/hot_air_burner/HotAirBurnerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"))
    private void create_galore_bridge$noteBurner(HotAirBurnerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                                                 int light, int overlay, CallbackInfo ci) {
        BurnerFlame.begin(be, buffer, partialTicks);
    }

    @Inject(method = "renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$throughPack(PoseStack poseStack, CallbackInfo ci) {
        if (BurnerFlame.draw(poseStack)) {
            ci.cancel();
        }
    }
}

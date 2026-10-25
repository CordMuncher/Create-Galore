package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.bridge.dlss.EntityMotion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: the hand and what it holds draw; their vertices are counted for their motion (see {@link EntityMotion}). */
@Mixin(ItemInHandRenderer.class)
public abstract class HandRenderMotionMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void create_galore_bridge$handMotion(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            LocalPlayer player, int light, CallbackInfo ci) {
        EntityMotion.beginHand();
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void create_galore_bridge$handMotionDone(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            LocalPlayer player, int light, CallbackInfo ci) {
        EntityMotion.end();
    }
}

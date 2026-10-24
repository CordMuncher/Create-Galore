package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.bridge.dlss.EntityMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: a block entity (a chest, a bell) draws; its vertices are counted for their motion (see {@link EntityMotion}). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderMotionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void create_galore_bridge$blockEntityMotion(E blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, CallbackInfo ci) {
        EntityMotion.begin(blockEntity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends BlockEntity> void create_galore_bridge$blockEntityMotionDone(E blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, CallbackInfo ci) {
        EntityMotion.end();
    }
}

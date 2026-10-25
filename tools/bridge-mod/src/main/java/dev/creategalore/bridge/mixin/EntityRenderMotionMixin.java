package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.bridge.dlss.EntityMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: an entity draws; its vertices are counted for their motion (see {@link EntityMotion}). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderMotionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void create_galore_bridge$entityMotion(E entity, double x, double y, double z, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityMotion.begin(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void create_galore_bridge$entityMotionDone(E entity, double x, double y, double z, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityMotion.end();
    }
}

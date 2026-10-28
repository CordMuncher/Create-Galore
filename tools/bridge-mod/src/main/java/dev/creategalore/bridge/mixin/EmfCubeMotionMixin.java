package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.creategalore.bridge.dlss.EntityMotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: an Entity Model Features cube draws (its own compile, which doesn't go through vanilla's); its vertices are
 * counted on their own for their motion (see {@link EntityMotion#cube}).
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom$EMFCube", remap = false)
public abstract class EmfCubeMotionMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void create_galore_bridge$cubeMotion(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        EntityMotion.cube(this, ((EntityMotion.Cube) this).create_galore_bridge$vertices());
    }
}

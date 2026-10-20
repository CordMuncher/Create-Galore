package dev.creategalore.fishing.mixin;

import dev.creategalore.fishing.client.HoldPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After vanilla poses a player's arms, an angler holding up a fish holds it up (see {@link HoldPose}). */
@Mixin(value = PlayerModel.class, remap = false)
public abstract class PlayerModelHoldMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cgfish$holdFish(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, CallbackInfo ci) {
        HoldPose.pose((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}

package dev.creategalore.bridge.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frogs eat Critters and Companions' dragonflies in the pack (they took over from Naturalist's, whose froglass a
 * frog leaves behind), and those can be tamed. A frog never eats a tamed animal.
 */
@Mixin(Frog.class)
public abstract class FrogSparesPetsMixin {
    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$sparePets(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof TamableAnimal pet && pet.isTame()) cir.setReturnValue(false);
    }
}

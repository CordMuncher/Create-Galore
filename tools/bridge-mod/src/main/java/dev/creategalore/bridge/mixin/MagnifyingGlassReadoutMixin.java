package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.AnimalCare;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.athisworkshop.animalhusbandry.care.AnimalCareData;
import org.athisworkshop.animalhusbandry.item.MagnifyingGlassItem;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tamed animals get the fuller Farm Magnifying Glass readout of {@link AnimalCare}; wild ones keep the mod's message. */
@Mixin(value = MagnifyingGlassItem.class, remap = false)
public abstract class MagnifyingGlassReadoutMixin {
    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$readout(ItemStack stack, Player player, LivingEntity target, InteractionHand hand,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (player.level().isClientSide() || !(target instanceof Animal animal)) return;
        AnimalCareData care = ModAttachments.getCareData(animal);
        if (care == null || !care.isDomesticated) return;
        AnimalCare.inspect(player, animal, care);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}

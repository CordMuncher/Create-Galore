package dev.creategalore.fishing.mixin;

import dev.creategalore.fishing.process.WholeFishIngredient;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cats and ocelots take every raw fish of the pack (tag {@code create_galore_fishing:cat_fish}, through
 * {@code minecraft:cat_food} and {@code minecraft:ocelot_food}), but not a fish too big to cook whole: the same
 * {@link WholeFishIngredient#small} rule as the furnace. Its fillets and steaks from the Cleaning Table are cat food.
 * Taming, trusting, healing and breeding all ask {@code isFood}.
 */
@Mixin({Cat.class, Ocelot.class})
public abstract class CatFoodMixin {
    @Inject(method = "isFood", at = @At("RETURN"), cancellable = true)
    private void cgfish$noBigFish(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !WholeFishIngredient.small(stack)) cir.setReturnValue(false);
    }
}

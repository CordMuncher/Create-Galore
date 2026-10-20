package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tide.Tide;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Items under the {@code tide} namespace are ours now (Tide itself is incompatible with this mod), so tooltips and
 * JEI name Create Galore Fishing as the mod that adds them instead of a mod that isn't installed.
 */
@Mixin(value = CommonHooks.class, remap = false)
public class CreatorModIdMixin {
    @Inject(method = "getDefaultCreatorModId", at = @At("HEAD"), cancellable = true)
    private static void cgfish$tideItems(ItemStack stack, CallbackInfoReturnable<String> cir) {
        if (!stack.isEmpty() && Tide.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()))
            cir.setReturnValue(CreateGaloreFishing.ID);
    }
}

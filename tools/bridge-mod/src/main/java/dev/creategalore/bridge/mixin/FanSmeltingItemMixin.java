package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.creategalore.bridge.FanSmeltingSmog;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dropped items a fan finishes smelting or smoking make smog where they are. */
@Mixin(value = FanProcessing.class, remap = false)
public abstract class FanSmeltingItemMixin {
    @Inject(method = "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
        at = @At("RETURN"))
    private static void create_galore_bridge$smog(ItemEntity entity, FanProcessingType type, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !entity.level().isClientSide) {
            FanSmeltingSmog.processed(entity.level(), entity.position(), type, entity.getItem().getCount());
        }
    }
}

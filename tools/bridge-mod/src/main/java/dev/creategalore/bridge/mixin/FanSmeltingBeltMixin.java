package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.creategalore.bridge.FanSmeltingSmog;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Items a fan finishes smelting or smoking on a belt or depot make smog where they are. */
@Mixin(value = AirCurrent.class, remap = false)
public abstract class FanSmeltingBeltMixin {
    @Inject(method = "lambda$tickAffectedHandlers$0", at = @At("RETURN"))
    private void create_galore_bridge$smog(Level level, FanProcessingType type, TransportedItemStackHandlerBehaviour handler,
            TransportedItemStack transported, CallbackInfoReturnable<TransportedResult> cir) {
        if (!level.isClientSide && !cir.getReturnValue().doesNothing()) {
            FanSmeltingSmog.processed(level, handler.getWorldPositionOf(transported), type, transported.stack.getCount());
        }
    }
}

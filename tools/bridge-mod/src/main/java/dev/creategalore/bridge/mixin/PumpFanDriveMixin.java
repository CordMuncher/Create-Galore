package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adchimneys.blocks.Pump;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced Chimneys pumps run off a turning shaft, which costs Create no stress at all. Here a pump needs an
 * Encased Fan right under it, blowing up or down, so pulling smog through a duct loads the network like any fan.
 */
@Mixin(value = Pump.RotationSource.class, remap = false)
public abstract class PumpFanDriveMixin {
    @Inject(method = "isKineticBlock", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$fanOnly(CallbackInfoReturnable<Boolean> cir) {
        Pump.RotationSource self = (Pump.RotationSource) (Object) this;
        BlockState state = self.level.getBlockState(self.pos);
        cir.setReturnValue(AllBlocks.ENCASED_FAN.has(state)
            && state.getValue(BlockStateProperties.FACING).getAxis().isVertical());
    }
}

package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.IFilterFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smog moving into a filter frame only goes as far as the frames take it. Pollution of the Realms otherwise carries
 * whatever a frame doesn't catch on up through it, which with the pack's rate-limited frames let smog slip past
 * unfiltered whenever a frame was busy.
 */
@Mixin(value = Pollutant.class, remap = false)
public abstract class PotrFilterNoBypassMixin {
    @Shadow
    protected abstract int pumpActiveFilters(LevelAccessor level, BlockPos pos, int amount);

    @Inject(method = "pumpEntitiesAt", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$onlyThroughFilters(LevelAccessor level, BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        if (!level.isClientSide() && level.getBlockState(pos).getBlock() instanceof IFilterFrame) {
            cir.setReturnValue(pumpActiveFilters(level, pos, amount));
        }
    }
}

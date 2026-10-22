package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.FilterFrame;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import dev.creategalore.bridge.Exhaust;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filtering leaves filtered exhaust behind, which has to get out at the top of the filter stack. A filter whose top
 * is closed off can't take any more smog: you can't filter into a sealed box.
 */
@Mixin(value = FilterFrame.class, remap = false)
public abstract class PotrFilterExhaustMixin {
    private static final String FILL =
        "fill(Lcom/endertech/minecraft/mods/adpother/blocks/FilterFrame$BlockTile;Lcom/endertech/minecraft/mods/adpother/blocks/Pollutant;I)I";

    @Inject(method = FILL, at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$needsOutlet(FilterFrame.BlockTile storage, Pollutant<?> pollutant, int amount, CallbackInfoReturnable<Integer> cir) {
        if (amount > 0 && storage.getLevel() instanceof ServerLevel level && !Exhaust.hasOutlet(level, storage.getBlockPos())) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = FILL, at = @At("RETURN"))
    private void create_galore_bridge$releaseExhaust(FilterFrame.BlockTile storage, Pollutant<?> pollutant, int amount, CallbackInfoReturnable<Integer> cir) {
        int count = cir.getReturnValue();
        if (count > 0 && storage.getLevel() instanceof ServerLevel level) Exhaust.release(level, storage.getBlockPos(), count);
    }
}

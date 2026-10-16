package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.creategalore.bridge.BurnerActivity;
import dev.creategalore.bridge.TfmgActivity;
import dev.eecler.smog.Emitters;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smog decides "is it burning" from the block state alone. For TFMG machines ask the block entity instead,
 * and count a smouldering Blaze Burner as idle.
 */
@Mixin(value = Emitters.class, remap = false)
public abstract class SmogEmittersMixin {
    @WrapOperation(
        method = "scan",
        at = @At(value = "INVOKE", target = "Ldev/eecler/smog/Emitters;activity(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private static float create_galore_bridge$tfmgActivity(BlockState state, Operation<Float> original, @Local BlockEntity be) {
        float activity = BurnerActivity.of(state);
        if (activity == TfmgActivity.UNKNOWN) activity = TfmgActivity.of(be);
        return activity == TfmgActivity.UNKNOWN ? original.call(state) : activity;
    }
}

package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import dev.creategalore.bridge.ExhaustPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smog under a roof tells the pressure check where to look. */
@Mixin(value = AbstractGas.class, remap = false)
public abstract class PotrGasTickMixin {
    @Inject(method = "randomTick", at = @At("HEAD"))
    private void create_galore_bridge$seedPressure(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ExhaustPressure.seed(level, pos);
    }
}

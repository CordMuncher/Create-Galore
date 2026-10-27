package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import dev.creategalore.bridge.Exhaust;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smog slips through any block without a full face in its way, belts and trapdoors included. A belt with casing on it
 * is sealed (items ride through a wall, smog stays in), so is a cased door or trapdoor while it is shut, and filtered
 * exhaust is not air smog can pass through.
 */
@Mixin(value = AbstractGas.class, remap = false)
public abstract class PotrGasPassMixin {
    @Inject(method = "canPassThrough", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$sealedBelts(LevelReader level, BlockPos pos, Direction input, Direction output,
            CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        if (Exhaust.isSealed(state) || Exhaust.isExhaust(state)) cir.setReturnValue(false);
    }
}

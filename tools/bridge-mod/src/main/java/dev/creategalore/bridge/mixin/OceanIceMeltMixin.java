package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.OceanIce;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice floating on a non-frozen ocean under open sky melts on its random ticks, in any season, so ice that formed
 * before oceans stopped freezing clears (see OceanIce). Ice under a roof, ice on land and packed or blue ice stay.
 * Priority below Cold Sweat's ice melting (which keeps ice that its custom freezing calls cold) so this runs first.
 */
@Mixin(value = IceBlock.class, priority = 800)
public abstract class OceanIceMeltMixin {
    @Shadow
    protected abstract void melt(BlockState state, Level level, BlockPos pos);

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$meltOnOpenOcean(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level.getFluidState(pos.below()).is(Fluids.WATER) && level.canSeeSky(pos.above()) && OceanIce.isOpenOcean(level, pos)) {
            melt(state, level, pos);
            ci.cancel();
        }
    }
}

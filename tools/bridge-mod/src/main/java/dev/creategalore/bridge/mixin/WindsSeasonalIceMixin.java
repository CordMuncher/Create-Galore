package dev.creategalore.bridge.mixin;

import com.enzonic.aeronauticswinds.weather.WeatherBlockEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aeronautics Winds picks its weather regions from noise alone, so a blizzard or hail region drifts over forests and
 * plains in any season. While one is overhead it froze surface water up to 32 blocks around each player every 5 s
 * without asking the biome or the season, so lakes iced over in the middle of summer (its snow already checks the
 * biome, its water freezing did not). Freeze only water the game itself would freeze there: Biome.shouldFreeze, which
 * Serene Seasons makes seasonal (the pack turns Cold Sweat's own freezing off) and which also keeps water next to a
 * light open. Ice that already formed melts over the warm seasons (Serene Seasons).
 */
@Mixin(value = WeatherBlockEffects.class, remap = false)
public abstract class WindsSeasonalIceMixin {
    @Inject(method = "freezeWater", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$freezeOnlyWhenCold(ServerLevel level, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        for (int i = 0; i < 3; i++) {
            BlockPos pos = top.below(i);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
                boolean freeze = level.getBiome(pos).value().shouldFreeze(level, pos, false);
                if (freeze) level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
                cir.setReturnValue(freeze);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}

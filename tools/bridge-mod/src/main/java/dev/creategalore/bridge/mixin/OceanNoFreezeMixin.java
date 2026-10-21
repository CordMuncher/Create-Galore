package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.OceanIce;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water in a non-frozen ocean never freezes, in any season (see OceanIce). Biome.shouldFreeze answers vanilla's
 * weather freezing and the Winds & Weather blizzard gate (WindsSeasonalIceMixin) alike. Priority below Cold Sweat's
 * (900) so this runs before its custom freezing, which would otherwise answer first when it is switched on.
 */
@Mixin(value = Biome.class, priority = 800)
public abstract class OceanNoFreezeMixin {
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$oceansStayOpen(LevelReader level, BlockPos pos, boolean mustBeAtEdge, CallbackInfoReturnable<Boolean> cir) {
        if (OceanIce.isOpenOcean(level, pos)) cir.setReturnValue(false);
    }
}

package dev.creategalore.structscan.mixin;

import dev.creategalore.structscan.ColumnCache;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Answers terrain-height questions from ColumnCache while a scan runs; does nothing otherwise. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseGeneratorMixin {
    @Inject(method = "getBaseHeight", at = @At("HEAD"), cancellable = true)
    private void structscan$cachedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs,
                                         CallbackInfoReturnable<Integer> cir) {
        Integer h = ColumnCache.height((NoiseBasedChunkGenerator) (Object) this, x, z, type, level, rs);
        if (h != null) cir.setReturnValue(h);
    }

    @Inject(method = "getBaseColumn", at = @At("HEAD"), cancellable = true)
    private void structscan$cachedColumn(int x, int z, LevelHeightAccessor level, RandomState rs, CallbackInfoReturnable<NoiseColumn> cir) {
        NoiseColumn c = ColumnCache.baseColumn((NoiseBasedChunkGenerator) (Object) this, x, z, level, rs);
        if (c != null) cir.setReturnValue(c);
    }
}

package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During Sable's physics update for a changed world block, a neighbour in a chunk that isn't ready reads as air
 * instead of making the server thread wait for that chunk. See {@link ChunkLoadGuard#sableTerrainUpdate}.
 */
@Mixin(value = LevelAccelerator.class, remap = false)
public abstract class SableAcceleratorNoWaitMixin {
    @Shadow private LevelChunk cachedChunkObj;

    // Ordinal 1 is the server's fallback when the chunk isn't fully loaded; ordinal 0 is the client's.
    @WrapOperation(method = "grabChunkFast", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk create_galore_bridge$airIfNotReady(Level level, int x, int z, Operation<LevelChunk> original) {
        if (ChunkLoadGuard.sableTerrainUpdate && level instanceof ServerLevel server && server.getServer().isSameThread()
                && level.getChunkSource().getChunkNow(x, z) == null) {
            return new EmptyLevelChunk(level, new ChunkPos(x, z),
                    level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        }
        return original.call(level, x, z);
    }

    // Never hand that stand-in out again from the one-chunk cache: the next read looks the chunk up afresh.
    @Inject(method = "getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"))
    private void create_galore_bridge$dropStandIn(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (cachedChunkObj instanceof EmptyLevelChunk) cachedChunkObj = null;
    }
}

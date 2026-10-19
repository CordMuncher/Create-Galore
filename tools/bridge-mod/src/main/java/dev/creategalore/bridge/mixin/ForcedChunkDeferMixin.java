package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** See {@link ChunkLoadGuard}. Every other mod's forced chunks still load at once, as NeoForge does it. */
@Mixin(value = ForcedChunkManager.class, remap = false)
public abstract class ForcedChunkDeferMixin {
    @WrapOperation(
        method = "forceChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Comparable;IIZZLnet/minecraft/server/level/TicketType;Ljava/util/function/Function;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private static LevelChunk create_galore_bridge$skipBlockingLoad(ServerLevel level, int x, int z, Operation<LevelChunk> original) {
        return ChunkLoadGuard.deferForcedLoads ? null : original.call(level, x, z);
    }
}

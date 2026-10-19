package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks Sable's physics update for a changed world block. See {@link ChunkLoadGuard#sableTerrainUpdate}. */
@Mixin(value = SableCommonEvents.class, remap = false)
public abstract class SableTerrainUpdateMixin {
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;handleBlockChange(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;IIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private static void create_galore_bridge$markTerrainUpdate(SubLevelPhysicsSystem system, SectionPos section, LevelChunkSection chunkSection,
                                                              int x, int y, int z, BlockState oldState, BlockState newState, Operation<Void> original) {
        boolean outer = ChunkLoadGuard.sableTerrainUpdate;
        ChunkLoadGuard.sableTerrainUpdate = true;
        try {
            original.call(system, section, chunkSection, x, y, z, oldState, newState);
        } finally {
            ChunkLoadGuard.sableTerrainUpdate = outer;
        }
    }
}

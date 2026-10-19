package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The other half of Sable's collision update for a changed world block: besides the neighbourhood reads covered by
 * {@link SableAcceleratorNoWaitMixin}, it reads each of the 6 neighbours straight from the world. A neighbour in a
 * chunk that isn't ready counts as air here too. See {@link ChunkLoadGuard#sableTerrainUpdate}.
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class SableRapierNeighboursNoWaitMixin {
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create_galore_bridge$airIfNotReady(ServerLevel level, BlockPos pos, Operation<BlockState> original) {
        if (level.getServer().isSameThread() && !ChunkLoadGuard.ready(level, pos)) return Blocks.AIR.defaultBlockState();
        return original.call(level, pos);
    }
}

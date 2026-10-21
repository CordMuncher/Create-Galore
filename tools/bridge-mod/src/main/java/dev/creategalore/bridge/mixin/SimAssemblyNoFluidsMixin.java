package dev.creategalore.bridge.mixin;

import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Physics Assembler (and the Swivel Bearing) gathers a ship through Create Simulated's own search, which takes every
 * block a glued face reaches and, unlike Create's contraptions, does not skip plain water or lava. Super glue spread over
 * a hull built in the sea glued the water cells next to it into the ship: they sailed along as floating water blocks and
 * sat in the boat's plot, where Deep Seas counts them as flooding. Pass over plain fluid blocks as if they were air
 * (waterlogged blocks are real blocks and still come along).
 */
@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SimAssemblyNoFluidsMixin {
    @Inject(method = "moveBlock(Lnet/minecraft/world/level/Level;Ljava/util/Queue;Ljava/util/Set;Ljava/util/Set;)Z", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$skipFluids(Level world, Queue<BlockPos> frontier, Set<BlockPos> visited, Set<BlockPos> visitedView,
                                                 CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = frontier.peek();
        if (pos == null || world.isOutsideBuildHeight(pos) || !world.isLoaded(pos)) return;
        if (!(world.getBlockState(pos).getBlock() instanceof LiquidBlock)) return;
        frontier.poll();
        visited.add(pos);
        cir.setReturnValue(true);
    }
}

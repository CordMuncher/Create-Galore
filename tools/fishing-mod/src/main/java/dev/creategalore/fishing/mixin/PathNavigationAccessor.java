package dev.creategalore.fishing.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Void fish plan paths through open air with their own navigation (ported from Tide), which needs these. */
@Mixin(value = PathNavigation.class, remap = false)
public interface PathNavigationAccessor {
    @Accessor("pathFinder")
    PathFinder cgfish$getPathFinder();

    @Accessor("maxVisitedNodesMultiplier")
    float cgfish$getMaxVisitedNodesMultiplier();

    @Accessor("targetPos")
    BlockPos cgfish$getTargetPos();

    @Accessor("targetPos")
    void cgfish$setTargetPos(BlockPos pos);

    @Accessor("reachRange")
    void cgfish$setReachRange(int range);

    @Invoker("resetStuckTimeout")
    void cgfish$resetStuckTimeout();
}

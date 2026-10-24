package dev.creategalore.bridge.mixin;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Plans a path farther than the mob's follow range (see AnimalTroughs). */
@Mixin(PathNavigation.class)
public interface PathRangeInvoker {
    @Invoker("createPath")
    Path create_galore_bridge$createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float range);
}

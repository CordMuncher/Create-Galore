package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Swivel Bearing gathers the group in front of it with Create Simulated's search, told to leave out glue that also
 * encloses the bearing (the ship's own glue). The search only did that for Honey Glue. With the whole ship glued in
 * Super Glue, the swivel took every block of the ship except the bearing itself: the ship became the "swivel group",
 * the bearing was left alone as a 0.75 kpg sub-level, and the engine behind the bearing ended up on the far side, so a
 * steerable propeller lost its power (256 su to 0 su) the moment the wheel turned. Leave out enclosing Super Glue
 * too; the group then is what is attached to the bearing's face plus any glue of its own, as on a ground-built swivel.
 */
@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SwivelEnclosingGlueMixin {
    @Shadow
    @Final
    public BlockPos anchor;

    @Shadow
    @Final
    public boolean ignoreEnclosingGlue;

    @ModifyExpressionValue(
        method = "checkAndCacheGlue",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T> List<T> create_galore_bridge$skipEnclosingSuperGlue(List<T> found) {
        if (anchor == null || !ignoreEnclosingGlue) return found;
        List<T> kept = new ArrayList<>(found.size());
        for (T entity : found) if (!(entity instanceof SuperGlueEntity glue && glue.contains(anchor))) kept.add(entity);
        return kept;
    }
}

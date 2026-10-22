package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Physics Assembler (and the Swivel Bearing) only carries the glue its search went through into the new ship. A
 * smaller glue box inside the ship's own glue, such as the one marking what a Swivel Bearing should turn, was never
 * needed to reach a block, so it stayed behind in the world where the ship had been. The swivel then had no glue of
 * its own on board and turned only the block on its face, leaving the rest (a propeller on a gearbox) unpowered on the
 * ship. Carry every Super Glue box that lies inside the gathered blocks' bounds and holds one of them too.
 */
@Mixin(value = SimAssemblyHelper.class, remap = false)
public abstract class SimAssemblyNestedGlueMixin {
    @WrapOperation(
        method = "assembleFromSingleBlock",
        at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/util/assembly/SimAssemblyContraption;getGlues()Ljava/util/Collection;"))
    private static Collection<SuperGlueEntity> create_galore_bridge$nestedGlue(SimAssemblyContraption contraption, Operation<Collection<SuperGlueEntity>> original,
                                                                                @Local(argsOnly = true) Level level) {
        Collection<SuperGlueEntity> used = original.call(contraption);
        Collection<BlockPos> blocks = contraption.getBlocks();
        if (blocks.isEmpty()) return used;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<SuperGlueEntity> all = new ArrayList<>(used);
        for (SuperGlueEntity glue : level.getEntitiesOfClass(SuperGlueEntity.class, bounds)) {
            if (all.contains(glue) || !create_galore_bridge$inside(glue.getBoundingBox(), bounds)) continue;
            // A swivel leaves the ship's glue (the box around the bearing) with the ship.
            if (contraption.ignoreEnclosingGlue && contraption.anchor != null && glue.contains(contraption.anchor)) continue;
            for (BlockPos pos : blocks) {
                if (glue.contains(pos)) {
                    all.add(glue);
                    break;
                }
            }
        }
        return all;
    }

    @Unique
    private static boolean create_galore_bridge$inside(AABB box, AABB bounds) {
        double e = 1.0E-4;
        return box.minX >= bounds.minX - e && box.minY >= bounds.minY - e && box.minZ >= bounds.minZ - e
            && box.maxX <= bounds.maxX + e && box.maxY <= bounds.maxY + e && box.maxZ <= bounds.maxZ + e;
    }
}

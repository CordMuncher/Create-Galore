package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glue a ship by its size. Create Simulated's search (Physics Assembler, Swivel Bearing) only glued two neighbouring
 * blocks when one single glue box held both, so the seam where two boxes met (side by side or overlapping) cut the ship
 * there, and players stacked many overlapping boxes and still lost parts. Now every glue box the search reaches joins
 * the ship's glue together with every box overlapping it or sharing a face with it, and any two neighbouring blocks
 * (by a face or an edge) inside that glue are glued, whichever box each one sits in. One Honey Glue box around the hull,
 * or several boxes side by side, glue the whole ship. A block that touches the rest only at a corner, or not at all,
 * still stays behind: the ship's physics would split it off as a loose piece anyway. A Swivel Bearing keeps its own
 * rule: the ship's glue around the bearing is still left out, and its own box does not spread into boxes next to it.
 */
@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SimAssemblyWholeGlueMixin {
    @Shadow
    @Final
    public BlockPos anchor;

    @Shadow
    @Final
    public boolean ignoreEnclosingGlue;

    @Shadow
    @Final
    private ObjectOpenHashSet<SuperGlueEntity> glueCache;

    @Shadow
    @Final
    private ObjectOpenHashSet<HoneyGlueEntity> honeyGlueCache;

    /** The ship's glue: every box reached so far, and the boxes joined to them. */
    @Unique
    private final Set<Entity> create_galore_bridge$shipGlue = new HashSet<>();

    /**
     * A block is about to go aboard: the glue boxes it sits in join the ship's glue before its neighbours are looked
     * at, so even the block the search starts from glues across a seam.
     */
    @Inject(method = "moveBlock(Lnet/minecraft/world/level/Level;Ljava/util/Queue;Ljava/util/Set;Ljava/util/Set;)Z", at = @At("HEAD"))
    private void create_galore_bridge$joinGlue(Level world, Queue<BlockPos> frontier, Set<BlockPos> visited, Set<BlockPos> visitedView,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = frontier.peek();
        if (pos == null || world.isOutsideBuildHeight(pos) || !world.isLoaded(pos) || pos.equals(anchor)) return;
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return;
        for (Entity glue : world.getEntitiesOfClass(Entity.class, new AABB(pos), SimAssemblyWholeGlueMixin::create_galore_bridge$isGlue)) {
            if (glue.getBoundingBox().contains(pos.getCenter())) create_galore_bridge$join(world, glue);
        }
    }

    /** Neighbours inside the ship's glue are glued, even when no single box holds both. */
    @Inject(method = "checkAndCacheGlue", at = @At("RETURN"), cancellable = true)
    private void create_galore_bridge$acrossBoxes(LevelAccessor level, BlockPos blockPos, BlockPos offsetDir, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || create_galore_bridge$shipGlue.isEmpty()) return;
        if (create_galore_bridge$inShipGlue(blockPos) && create_galore_bridge$inShipGlue(blockPos.offset(offsetDir))) cir.setReturnValue(true);
    }

    @Unique
    private boolean create_galore_bridge$inShipGlue(BlockPos pos) {
        var centre = pos.getCenter();
        for (Entity glue : create_galore_bridge$shipGlue) {
            if (glue.getBoundingBox().contains(centre)) return true;
        }
        return false;
    }

    @Unique
    private void create_galore_bridge$join(Level world, Entity glue) {
        if (create_galore_bridge$shipGlue.contains(glue)) return;
        AABB box = glue.getBoundingBox();
        // A swivel leaves the ship's glue (the box around the bearing) with the ship.
        if (ignoreEnclosingGlue && anchor != null && box.contains(anchor.getCenter())) return;
        create_galore_bridge$shipGlue.add(glue);
        // The glue goes aboard with the ship, as glue the search walked through always did.
        if (glue instanceof SuperGlueEntity superGlue) glueCache.add(superGlue);
        else if (glue instanceof HoneyGlueEntity honeyGlue) honeyGlueCache.add(honeyGlue);
        if (ignoreEnclosingGlue) return;
        // Boxes overlapping or sharing a face with this one belong to the same ship.
        for (Entity other : world.getEntitiesOfClass(Entity.class, box.inflate(0.5), SimAssemblyWholeGlueMixin::create_galore_bridge$isGlue)) {
            if (create_galore_bridge$touches(box, other.getBoundingBox())) create_galore_bridge$join(world, other);
        }
    }

    @Unique
    private static boolean create_galore_bridge$isGlue(Entity entity) {
        return entity instanceof SuperGlueEntity || entity instanceof HoneyGlueEntity;
    }

    /** Overlapping, or sharing a face (not only an edge or a corner). */
    @Unique
    private static boolean create_galore_bridge$touches(AABB a, AABB b) {
        double e = 1.0E-4;
        double ox = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX);
        double oy = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);
        double oz = Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ);
        if (ox < -e || oy < -e || oz < -e) return false;
        int flat = (ox <= e ? 1 : 0) + (oy <= e ? 1 : 0) + (oz <= e ? 1 : 0);
        return flat <= 1;
    }
}

package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maxenonyme.highseas.BoatBuoyancySystem;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Boat Engine pushes with 36 blocks/s² times the whole ship's mass, always level with the world, from the bottom of
 * its own block at the stern. That is over three times the boat's weight, so the point it pushes at decides the boat's
 * trim far more than its shape does. From the stern, a level push tips the boat further the moment it pitches (the
 * stern drops and the push lifts the bow, or the stern rises and it drives the bow down): with full throttle a plank
 * boat reared up onto its end within 6 s, and a hull reaching deeper than the engine dug its bow in 18 degrees and
 * crawled at 2.4 blocks/s. Every boat came out bow-heavy or flipped, whatever its shape.
 *
 * Push through the centre of mass instead, at the height of the centre of the submerged hull blocks, where Sable's
 * water drag acts, still steering from the propeller. The rest of the drag (the boat mod's hull drag and the universal
 * damping) acts at the centre of mass, above the push, so the bow lifts a little as the boat speeds up and settles
 * slightly bow-up at speed, the way a boat driven from the stern does.
 */
@Mixin(value = BoatEngineBlockEntity.class, remap = false)
public abstract class BoatEngineLevelThrustMixin {
    /** Solid blocks per layer of each ship's plot, recounted every 2 s. */
    @Unique
    private static final Map<UUID, create_galore_bridge$Layers> create_galore_bridge$layers = new ConcurrentHashMap<>();

    @Unique
    private record create_galore_bridge$Layers(long time, int minY, int[] counts) {
    }

    @WrapOperation(method = "sable$physicsTick", at = @At(value = "INVOKE", ordinal = 0,
        target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyImpulseAtPoint(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"))
    private void create_galore_bridge$pushWithTheHull(RigidBodyHandle handle, Vector3dc point, Vector3dc impulse, Operation<Void> original,
                                                      ServerSubLevel subLevel, RigidBodyHandle h, double timeStep) {
        Pose3dc pose = subLevel.logicalPose();
        Vector3dc centre = pose.rotationPoint();
        LevelPlot plot = subLevel.getPlot();
        Level level = ((BlockEntity) (Object) this).getLevel();
        double surface = BoatBuoyancySystem.surfaceFor(subLevel.getUniqueId());
        double y = centre.y();
        if (plot != null && level != null && surface != Double.NEGATIVE_INFINITY) {
            // The waterline under the centre of mass, in the ship's own blocks.
            Vector3d world = pose.transformPosition(new Vector3d(centre));
            double waterline = pose.transformPositionInverse(world.set(world.x, surface, world.z)).y;
            double hull = create_galore_bridge$submergedCentre(level, subLevel.getUniqueId(), plot, waterline);
            if (!Double.isNaN(hull)) y = Math.min(y, hull);
        }
        original.call(handle, new Vector3d(centre.x(), y, centre.z()), impulse);
    }

    /** Height of the centre of the ship's blocks under the waterline (each weighted by how deep it is in), or NaN. */
    @Unique
    private static double create_galore_bridge$submergedCentre(Level level, UUID id, LevelPlot plot, double waterline) {
        create_galore_bridge$Layers layers = create_galore_bridge$layers.get(id);
        if (layers == null || level.getGameTime() - layers.time() > 40 || layers.time() > level.getGameTime()) {
            BoundingBox3ic b = plot.getBoundingBox();
            int[] counts = new int[b.maxY() - b.minY() + 1];
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int by = b.minY(); by <= b.maxY(); by++) {
                        if (!level.getBlockState(p.set(x, by, z)).getCollisionShape(level, p).isEmpty()) counts[by - b.minY()]++;
                    }
                }
            }
            layers = new create_galore_bridge$Layers(level.getGameTime(), b.minY(), counts);
            create_galore_bridge$layers.put(id, layers);
            create_galore_bridge$layers.values().removeIf(l -> l.time() < level.getGameTime() - 1200);
        }
        double weight = 0.0, sum = 0.0;
        for (int i = 0; i < layers.counts().length; i++) {
            double wet = Math.max(0.0, Math.min(1.0, waterline - (layers.minY() + i)));
            if (wet <= 0.0) break;
            weight += layers.counts()[i] * wet;
            sum += layers.counts()[i] * wet * (layers.minY() + i + 0.5);
        }
        return weight > 0.0 ? sum / weight : Double.NaN;
    }
}

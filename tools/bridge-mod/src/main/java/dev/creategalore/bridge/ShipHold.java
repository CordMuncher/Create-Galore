package dev.creategalore.bridge;

import com.maxenonyme.createsubmarine.submarine.compartment.HullSeal;
import dev.creategalore.bridge.mixin.CompartmentTrackerAccessor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/**
 * Whether an entity stands in a ship's dry hold, as Create Deep Seas tracks it (see ShipHoldDryMixin). Deep Seas only
 * asks the cells holding the eyes and the feet; this also asks the cell just above the feet and the one at the middle
 * of the body, and counts a cell of the hull too when its block is a partial one (an open door, a stair, a slab)
 * whose open part touches the hold and nothing outside it, and holds no water: the hold's air goes on through it.
 */
public final class ShipHold {
    private ShipHold() {}

    public static boolean inDryHold(Entity entity) {
        AABB bounds = CompartmentTrackerAccessor.create_galore_bridge$sealedBounds();
        Level level = entity.level();
        if (bounds == null || level == null) return false;
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        if (!bounds.contains(x, y, z)) return false;
        Map<UUID, AABB> boxes = CompartmentTrackerAccessor.create_galore_bridge$worldBoxes();
        Map<UUID, Set<BlockPos>> holds = CompartmentTrackerAccessor.create_galore_bridge$sealed();
        double[] heights = {y + 0.15, y + entity.getBbHeight() * 0.5, entity.getEyeY()};
        for (Map.Entry<UUID, SubLevelAccess> e : CompartmentTrackerAccessor.create_galore_bridge$subs().entrySet()) {
            if (!(e.getValue() instanceof SubLevel ship) || ship.getLevel() == null || ship.getLevel().dimension() != level.dimension()) continue;
            AABB box = boxes.get(e.getKey());
            Set<BlockPos> hold = holds.get(e.getKey());
            LevelPlot plot = ship.getPlot();
            if (box == null || !box.contains(x, y, z) || hold == null || hold.isEmpty() || plot == null) continue;
            // The same pose Deep Seas measures with: what the player sees on the client, the physics pose on the server.
            Pose3dc pose = level.isClientSide && ship instanceof ClientSubLevel client ? client.renderPose() : ship.logicalPose();
            Vector3d local = new Vector3d();
            for (double h : heights) {
                pose.transformPositionInverse(local.set(x, h, z));
                if (dry(plot, hold, BlockPos.containing(local.x, local.y, local.z))) return true;
            }
        }
        return false;
    }

    /**
     * Whether a player who has just been put back on a ship after logging in stands inside it while Deep Seas has not
     * looked at its hold yet (see ShipHoldDryMixin).
     */
    public static boolean boarding(Entity entity, UUID shipId) {
        Set<BlockPos> hold = CompartmentTrackerAccessor.create_galore_bridge$sealed().get(shipId);
        if (CompartmentTrackerAccessor.create_galore_bridge$subs().containsKey(shipId) && hold != null) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
        SubLevel ship = container == null ? null : container.getSubLevel(shipId);
        LevelPlot plot = ship == null ? null : ship.getPlot();
        if (plot == null) return false;
        Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(entity.getX(), entity.getY() + 0.15, entity.getZ()));
        BlockPos cell = BlockPos.containing(local.x, local.y, local.z);
        BoundingBox3ic box = plot.getBoundingBox();
        if (cell.getX() < box.minX() || cell.getX() > box.maxX() || cell.getY() < box.minY() || cell.getY() > box.maxY()
                || cell.getZ() < box.minZ() || cell.getZ() > box.maxZ()) return false;
        BlockState state = blockAt(plot, cell);
        return state != null && state.getFluidState().isEmpty();
    }

    private static boolean dry(LevelPlot plot, Set<BlockPos> hold, BlockPos cell) {
        BlockState state = blockAt(plot, cell);
        if (state == null || !state.getFluidState().isEmpty()) return false;
        if (hold.contains(cell)) return true;
        HullSeal.Shape shape = HullSeal.of(state);
        if (!HullSeal.partial(shape)) return false;
        boolean touches = false;
        for (Direction dir : Direction.values()) {
            if (!shape.opens(dir)) continue;
            BlockPos next = cell.relative(dir);
            if (hold.contains(next)) {
                touches = true;
                continue;
            }
            // Its open part also reaches open air outside the hold (a trapdoor in the hull's skin): that is the outside.
            BlockState beside = blockAt(plot, next);
            if (beside == null || beside.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) return false;
        }
        return touches;
    }

    private static BlockState blockAt(LevelPlot plot, BlockPos cell) {
        LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(cell)));
        return chunk == null ? null : chunk.getBlockState(cell);
    }
}

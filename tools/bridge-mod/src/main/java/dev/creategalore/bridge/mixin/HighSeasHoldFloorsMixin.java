package dev.creategalore.bridge.mixin;

import com.maxenonyme.createsubmarine.submarine.compartment.HullSeal;
import com.maxenonyme.highseas.BoatBuoyancySystem;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deep Seas (Create High Seas) floats a boat on the air in its hold: every empty cell boxed in by the hull gets 1.5x its
 * weight in lift. A cell holding a slab, stair, trapdoor or other partial block counts as hull, so the air above a slab
 * floor, or beside a stair, displaced nothing. Slab floors are the usual way to build a hull, and the lost lift sat
 * where the floor was (the middle and bow of most boats) while solid sterns and engine rooms kept theirs, so such boats
 * floated bow-down: the slab-floored boat in Emre's world sat 2.4 degrees bow-down at rest, 1.5 with this.
 * <p>
 * Give each partial block next to a dry hold cell the lift of its own open part, at that part's centre, the same way
 * the hold's cells are lifted. A partial block counts only when its open part faces the hold cell (a slab under the
 * hold, a stair's open corner towards it) and it holds no water.
 */
@Mixin(value = BoatBuoyancySystem.class, remap = false)
public abstract class HighSeasHoldFloorsMixin {
    @Unique private static final double create_galore_bridge$LIFT_PER_CELL = 1.5;
    @Unique private static final int create_galore_bridge$GRID = 8;
    // Per ship: the hold cell array the floor list was built from (Deep Seas keeps the same array until it rescans) and
    // the open parts as {x, y, z, volume, height}, in plot coordinates.
    @Unique private static final Map<UUID, create_galore_bridge$Floors> create_galore_bridge$floors = new ConcurrentHashMap<>();
    @Unique private static final Map<BlockState, double[]> create_galore_bridge$openParts = new ConcurrentHashMap<>();

    @Unique private record create_galore_bridge$Floors(double[] cells, double[] parts) {}

    @Inject(method = "apply", at = @At("TAIL"))
    private static void create_galore_bridge$liftHoldFloors(ServerSubLevel sub, RigidBodyHandle handle, @Coerce Object hull, double g, double dt,
                                                             CallbackInfo ci) {
        UUID id = sub.getUniqueId();
        double surface = BoatBuoyancySystem.surfaceFor(id);
        if (surface == Double.NEGATIVE_INFINITY) return;
        create_galore_bridge$Floors floors = create_galore_bridge$floors.get(id);
        double[] cells = ((HullCellsAccessor) hull).create_galore_bridge$cells();
        if (floors == null || floors.cells() != cells) {
            // Physics ticks run on the server thread here, where the plot's blocks can be read.
            Level level = sub.getLevel();
            floors = new create_galore_bridge$Floors(cells, create_galore_bridge$findFloors(level, cells, ((HullCellsAccessor) hull).create_galore_bridge$count()));
            create_galore_bridge$floors.put(id, floors);
            if (create_galore_bridge$floors.size() > 256) create_galore_bridge$floors.keySet().removeIf(k -> BoatBuoyancySystem.surfaceFor(k) == Double.NEGATIVE_INFINITY);
        }
        double[] parts = floors.parts();
        if (parts.length == 0) return;

        Pose3dc pose = sub.logicalPose();
        Vector3d point = new Vector3d();
        double base = pose.transformPosition(point.set(0.0, 0.0, 0.0)).y;
        double ax = pose.transformPosition(point.set(1.0, 0.0, 0.0)).y - base;
        double ay = pose.transformPosition(point.set(0.0, 1.0, 0.0)).y - base;
        double az = pose.transformPosition(point.set(0.0, 0.0, 1.0)).y - base;
        double waterline = surface - base;
        double volume = 0.0;
        Vector3d centre = new Vector3d();
        for (int k = 0; k < parts.length; k += 5) {
            double x = parts[k], y = parts[k + 1], z = parts[k + 2], open = parts[k + 3], height = parts[k + 4];
            // Share of the open part under water, measured over its height as Deep Seas does for a whole cell.
            double immersion = Math.clamp((waterline - (ax * x + ay * y + az * z) + height / 2) / height, 0.0, 1.0);
            if (immersion <= 0.0) continue;
            double v = open * immersion;
            volume += v;
            centre.add(x * v, y * v, z * v);
        }
        if (volume < 1.0E-4) return;
        centre.div(volume);
        Vector3d lift = new Vector3d(0.0, volume * create_galore_bridge$LIFT_PER_CELL * g * dt, 0.0);
        pose.orientation().conjugate(new Quaterniond()).transform(lift);
        handle.applyImpulseAtPoint(centre, lift);
    }

    @Unique
    private static double[] create_galore_bridge$findFloors(Level level, double[] cells, int count) {
        Set<BlockPos> hold = new HashSet<>();
        for (int i = 0; i < count; i++) {
            hold.add(BlockPos.containing(cells[3 * i], cells[3 * i + 1], cells[3 * i + 2]));
        }
        Set<BlockPos> seen = new HashSet<>();
        List<Double> out = new ArrayList<>();
        for (BlockPos cell : hold) {
            for (Direction dir : Direction.values()) {
                BlockPos pos = cell.relative(dir);
                if (hold.contains(pos) || !seen.add(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) continue;
                HullSeal.Shape shape = HullSeal.of(state);
                if (!HullSeal.partial(shape) || !shape.opens(dir.getOpposite())) {
                    seen.remove(pos); // another hold cell may sit on its open side
                    continue;
                }
                if (create_galore_bridge$leaks(level, pos, shape, hold)) continue;
                double[] part = create_galore_bridge$openParts.computeIfAbsent(state, HighSeasHoldFloorsMixin::create_galore_bridge$measure);
                if (part[3] <= 0.0) continue;
                out.add(pos.getX() + part[0]);
                out.add(pos.getY() + part[1]);
                out.add(pos.getZ() + part[2]);
                out.add(part[3]);
                out.add(part[4]);
            }
        }
        double[] parts = new double[out.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = out.get(i);
        return parts;
    }

    // A partial block whose open part also reaches open air outside the hold (a trapdoor in the hull's skin, a slab on
    // the gunwale) is part of the outside, not the hold.
    @Unique
    private static boolean create_galore_bridge$leaks(Level level, BlockPos pos, HullSeal.Shape shape, Set<BlockPos> hold) {
        for (Direction dir : Direction.values()) {
            if (!shape.opens(dir)) continue;
            BlockPos next = pos.relative(dir);
            if (!hold.contains(next) && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) return true;
        }
        return false;
    }

    // The open part of a block's cell: {centre x, y, z within the cell, volume, height}.
    @Unique
    private static double[] create_galore_bridge$measure(BlockState state) {
        VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        List<AABB> boxes = collision.toAabbs();
        int n = create_galore_bridge$GRID, open = 0, minY = n, maxY = -1;
        double sx = 0, sy = 0, sz = 0;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    double cx = (x + 0.5) / n, cy = (y + 0.5) / n, cz = (z + 0.5) / n;
                    boolean solid = false;
                    for (AABB box : boxes) {
                        if (cx > box.minX && cx < box.maxX && cy > box.minY && cy < box.maxY && cz > box.minZ && cz < box.maxZ) {
                            solid = true;
                            break;
                        }
                    }
                    if (solid) continue;
                    open++;
                    sx += cx;
                    sy += cy;
                    sz += cz;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (open == 0) return new double[] {0.5, 0.5, 0.5, 0.0, 1.0};
        return new double[] {sx / open, sy / open, sz / open, open / (double) (n * n * n), (maxY - minY + 1) / (double) n};
    }
}

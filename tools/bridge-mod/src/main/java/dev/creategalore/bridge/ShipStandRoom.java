package dev.creategalore.bridge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Whether a player has room to stand (or crouch) among a ship's blocks.
 *
 * <p>Minecraft picks a player's pose every tick: standing if the standing box is free, else crouching if that box is
 * free, else crawling. Sable adds the ship's blocks to that test with the player's whole box against the ship as it was
 * one tick earlier. On a ship that is turning, that old ship overlaps a standing player by a few centimetres to a few
 * tenths of a block (a rail beside them, the deck under their feet on a roll), so the test fails for both standing and
 * crouching and the player drops into the crawl pose with plenty of headroom; it lasts as long as they are pressed
 * against that rail. Measured on a two-player server with both players standing on a whipped ship: every crawl came
 * from such a side or floor overlap, never from a ceiling.</p>
 *
 * <p>Here the ship is taken where it is now, and only the middle of the player counts: a column 0.3 blocks wide,
 * starting half a block above the feet, up to the height of the pose less a little slack for the deck gap. A real low roof over the player still
 * makes them crouch or crawl, a rail pressing on their shoulder or the deck tilting under their feet does not.</p>
 */
public final class ShipStandRoom {
    /** How much of the player's width is ignored on each side (the box is 0.6 wide, so 0.3 is left). */
    private static final double SIDE = 0.15;
    /** How far above the feet the column starts: a tilted deck or a slab-high step under the player is not a roof. */
    private static final double LIFT = 0.5;
    /**
     * Slack under a roof. Sable keeps a player standing a centimetre or two above the deck, so crouching under a slab
     * (a gap of exactly 1.5) came out a hair too tall and dropped them to the crawl; on land Minecraft lets that fit.
     */
    private static final double TOP = 0.05;

    private ShipStandRoom() {
    }

    /** True when a ship is close enough to the box to matter, so the ship test below should replace Sable's. */
    public static boolean nearShip(Level level, AABB box) {
        return Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(box.inflate(1.05))).iterator().hasNext();
    }

    /** True when no ship block reaches into the middle column of the pose box. */
    public static boolean roomOnShips(Level level, AABB box) {
        double lift = Math.min(LIFT, Math.max(0.0, box.getYsize() - TOP - 0.1));
        AABB column = new AABB(box.minX + SIDE, box.minY + lift, box.minZ + SIDE, box.maxX - SIDE, box.maxY - TOP, box.maxZ - SIDE);
        LevelReusedVectors sink = new LevelReusedVectors();
        Quaterniond columnTurn = new Quaterniond();
        OrientedBoundingBox3d columnBox = new OrientedBoundingBox3d(
                new Vector3d((column.minX + column.maxX) / 2.0, (column.minY + column.maxY) / 2.0, (column.minZ + column.maxZ) / 2.0),
                new Vector3d(column.getXsize(), column.getYsize(), column.getZsize()), columnTurn, sink);
        OrientedBoundingBox3d blockBox = new OrientedBoundingBox3d(sink);
        BoundingBox3d local = new BoundingBox3d();
        Matrix4d baked = new Matrix4d();
        Vector3d center = new Vector3d();
        Vector3d overlap = new Vector3d();
        for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(column.inflate(1.05)))) {
            Pose3dc pose = ship.logicalPose();
            local.set(column);
            local.transformInverse(pose, baked, local);
            blockBox.getOrientation().set(pose.orientation());
            columnTurn.identity().rotateY(SubLevelEntityCollision.getHitBoxYaw(pose));
            columnBox.setOrientation(columnTurn);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(local.minX(), local.minY() - 1.0, local.minZ()),
                    BlockPos.containing(local.maxX(), local.maxY(), local.maxZ()))) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                Iterator<BoundingBox3dc> boxes = ((FastVoxelShapeIterable) state.getCollisionShape(level, pos)).sable$allBoxes();
                while (boxes.hasNext()) {
                    BoundingBox3dc part = boxes.next();
                    part.center(center);
                    blockBox.getPosition().set(pos.getX() + center.x, pos.getY() + center.y, pos.getZ() + center.z);
                    pose.transformPosition(blockBox.getPosition());
                    part.size(blockBox.getDimensions());
                    OrientedBoundingBox3d.sat(columnBox, blockBox, overlap);
                    if (overlap.lengthSquared() > 0.0 && overlap.x != Double.MAX_VALUE && overlap.y != Double.MAX_VALUE
                            && overlap.z != Double.MAX_VALUE) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

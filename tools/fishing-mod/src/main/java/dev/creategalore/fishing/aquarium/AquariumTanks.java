package dev.creategalore.fishing.aquarium;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How Aquarium Tank blocks join into tanks. Tank blocks that touch and together fill a box (up to
 * {@link #MOST_SIDE} on every side) are one tank; the box's lowest corner is its controller.
 *
 * <ul>
 * <li>A placed block that completes a box joins everything in it into one tank, with all the water, fish and food
 * of the tanks it joined. One that doesn't is a tank on its own; the tanks next to it stay as they were.</li>
 * <li>A broken block breaks its tank. What is left becomes single blocks again, then joins back into a tank if it
 * still fills a box (the tank lost a whole end): the contents move there. Otherwise the water is lost and the fish
 * spill out where the glass broke.</li>
 * </ul>
 * The blocks' connection properties (which faces show glass and frame) follow tank membership, so a tank looks
 * like one piece and two tanks side by side look like two.
 */
public final class AquariumTanks {
    private AquariumTanks() {
    }

    public static final int MOST_SIDE = 8;

    /** Joins the block at {@code pos} into a tank (scheduled a tick after it was placed, once it has its entity). */
    public static void form(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AquariumBlockEntity be)) return;
        Set<BlockPos> group = connected(level, pos, null);
        int[] box = box(group);
        if (box != null) {
            merge(level, group, box);
        } else if (be.controllerPos() == null || be.tank() == null) {
            be.lead(1, 1, 1);
        }
        refresh(level, group);
    }

    /** The block at {@code pos} (with entity {@code be}) is being broken. */
    public static void removed(ServerLevel level, BlockPos pos, AquariumBlockEntity be) {
        AquariumBlockEntity tank = be.tank();
        if (tank == null) return;
        AquariumBlockEntity.Contents contents = tank.takeContents();
        List<BlockPos> rest = new ArrayList<>();
        for (BlockPos p : tank.members()) {
            if (!p.equals(pos) && level.getBlockEntity(p) instanceof AquariumBlockEntity member) {
                rest.add(p);
                member.lead(1, 1, 1);
            }
        }
        // What's left joins back up where it still fills a box; the biggest such tank takes the contents.
        Set<BlockPos> left = new HashSet<>(rest);
        Set<BlockPos> seen = new HashSet<>();
        AquariumBlockEntity heir = null;
        for (BlockPos p : rest) {
            if (seen.contains(p)) continue;
            Set<BlockPos> part = connected(level, p, left);
            seen.addAll(part);
            int[] box = box(part);
            if (box == null) continue;
            AquariumBlockEntity formed = merge(level, part, box);
            if (heir == null || formed.volume() > heir.volume()) heir = formed;
        }
        List<net.minecraft.world.item.ItemStack> spilled = heir != null
                ? heir.put(new AquariumBlockEntity.Contents(contents.fish(), contents.water(), contents.food()))
                : contents.fish();
        for (var f : spilled) Block.popResource(level, pos, f);
        refresh(level, rest);
    }

    /**
     * Tank blocks connected to {@code start} (within {@code within} if given), stopping one past the most a tank can
     * hold so a huge wall of glass doesn't flood the search.
     */
    private static Set<BlockPos> connected(ServerLevel level, BlockPos start, Set<BlockPos> within) {
        Set<BlockPos> out = new LinkedHashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start.immutable());
        int most = MOST_SIDE * MOST_SIDE * MOST_SIDE + 1;
        while (!todo.isEmpty() && out.size() < most) {
            BlockPos p = todo.poll();
            if (out.contains(p) || (within != null && !within.contains(p))) continue;
            if (!(level.getBlockState(p).getBlock() instanceof AquariumBlock)) continue;
            out.add(p);
            for (Direction d : Direction.values()) todo.add(p.relative(d));
        }
        return out;
    }

    /** {min x, y, z, size x, y, z} if {@code group} fills its bounding box within the size limit, else null. */
    private static int[] box(Set<BlockPos> group) {
        if (group.isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : group) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        if (sx > MOST_SIDE || sy > MOST_SIDE || sz > MOST_SIDE || sx * sy * sz != group.size()) return null;
        return new int[]{x0, y0, z0, sx, sy, sz};
    }

    /** Makes {@code group} (which fills {@code box}) one tank holding everything its old tanks held. */
    private static AquariumBlockEntity merge(ServerLevel level, Set<BlockPos> group, int[] box) {
        List<net.minecraft.world.item.ItemStack> fish = new ArrayList<>();
        int water = 0, food = 0;
        Set<BlockPos> drained = new HashSet<>();
        for (BlockPos p : group) {
            if (!(level.getBlockEntity(p) instanceof AquariumBlockEntity be)) continue;
            AquariumBlockEntity old = be.tank();
            if (old == null || !drained.add(old.getBlockPos())) continue;
            AquariumBlockEntity.Contents c = old.takeContents();
            fish.addAll(c.fish());
            water += c.water();
            food += c.food();
        }
        BlockPos corner = new BlockPos(box[0], box[1], box[2]);
        AquariumBlockEntity controller = (AquariumBlockEntity) level.getBlockEntity(corner);
        for (BlockPos p : group) {
            if (!p.equals(corner) && level.getBlockEntity(p) instanceof AquariumBlockEntity be) be.joinTank(corner);
        }
        controller.lead(box[3], box[4], box[5]);
        for (var f : controller.put(new AquariumBlockEntity.Contents(fish, water, food))) Block.popResource(level, corner, f);
        return controller;
    }

    /** Sets which faces of these blocks (and their neighbours) join a block of the same tank. */
    static void refresh(ServerLevel level, Iterable<BlockPos> positions) {
        Set<BlockPos> todo = new HashSet<>();
        for (BlockPos p : positions) {
            todo.add(p);
            for (Direction d : Direction.values()) todo.add(p.relative(d));
        }
        for (BlockPos p : todo) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof AquariumBlock) || !(level.getBlockEntity(p) instanceof AquariumBlockEntity be)) continue;
            BlockState want = state;
            for (Direction d : Direction.values()) {
                boolean joined = be.controllerPos() != null && level.getBlockEntity(p.relative(d)) instanceof AquariumBlockEntity n
                        && be.controllerPos().equals(n.controllerPos());
                want = want.setValue(AquariumBlock.SIDES.get(d), joined);
            }
            if (want != state) level.setBlock(p, want, Block.UPDATE_CLIENTS);
        }
    }
}

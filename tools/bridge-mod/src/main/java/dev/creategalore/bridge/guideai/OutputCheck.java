package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Where a machine's finished items go, by Create's own rules. A basin pushes out sideways only (BasinBlock.canOutputTo)
 * when the block beside it is open (no collision, or a funnel) and the block one down from that takes items from the
 * side the way a belt does: a depot, belt, chute, or a funnel facing up and set to insert, on top of a chest. A chest,
 * barrel or hopper there gets nothing, and neither does anything beside the basin at its own height; with no output
 * the results stay inside the basin. A millstone never pushes at all: something has to pull from it (a hopper or chute
 * under it, a funnel set to pull on it). Checked on models before the book shows them, and on live builds as they stand.
 */
final class OutputCheck {
    private static final String RULE = "a basin pushes finished items out only onto a depot, belt or chute, or into a funnel "
            + "facing up and set to insert (placed while sneaking) on top of a chest, in the spot one block down beside the "
            + "basin, with the block beside the basin at its own height left open (air, or that funnel's own block)";

    private OutputCheck() {
    }

    /** Null when the model has no basin or millstone. */
    static BuildCheck.Report check(Map<BlockPos, BlockState> blocks, ServerLevel level) {
        List<BlockPos> machines = new ArrayList<>();
        blocks.forEach((pos, state) -> {
            if (state.getBlock() instanceof BasinBlock || state.getBlock() instanceof MillstoneBlock) machines.add(pos);
        });
        if (machines.isEmpty()) return null;
        try {
            SchematicLevel world = new SchematicLevel(level);
            blocks.forEach((pos, state) -> world.setBlock(pos, state, 0));
            List<String> faults = new ArrayList<>(), notes = new ArrayList<>();
            for (BlockPos p : machines) machine(world, p, faults, notes);
            StringBuilder out = new StringBuilder("Where each basin's and millstone's finished items go" + BuildCheck.FOR_YOU + ":\n");
            for (String n : notes) out.append("- ").append(n).append(".\n");
            for (String f : faults) out.append("- PROBLEM: ").append(f).append(".\n");
            return new BuildCheck.Report(out.toString().trim(), faults);
        } catch (RuntimeException | LinkageError e) {
            return new BuildCheck.Report("(The output check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    private static void machine(Level world, BlockPos p, List<String> faults, List<String> notes) {
        if (world.getBlockState(p).getBlock() instanceof BasinBlock) basin(world, p, faults, notes);
        else millstone(world, p, faults, notes);
    }

    /** The same for the basins and millstones of a live build, as they stand now; empty when it has none. */
    static String live(ServerLevel level, List<BlockPos> build) {
        StringBuilder out = new StringBuilder();
        for (BlockPos p : build) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof BasinBlock) && !(state.getBlock() instanceof MillstoneBlock)) continue;
            List<String> faults = new ArrayList<>(), notes = new ArrayList<>();
            machine(level, p, faults, notes);
            for (String n : notes) out.append("- ").append(n).append(".\n");
            for (String f : faults) out.append("- ").append(f).append(".\n");
            var inv = level.getBlockEntity(p) instanceof BasinBlockEntity be ? be.getOutputInventory()
                    : level.getBlockEntity(p) instanceof MillstoneBlockEntity m ? m.outputInv : null;
            if (inv != null) {
                List<String> held = new ArrayList<>();
                for (int i = 0; i < inv.getSlots(); i++) {
                    var s = inv.getStackInSlot(i);
                    if (!s.isEmpty()) held.add(s.getCount() + "x " + s.getHoverName().getString());
                }
                if (!held.isEmpty()) out.append("  Finished items waiting inside it now: ").append(String.join(", ", held)).append(".\n");
            }
        }
        return out.isEmpty() ? "" : "Where each basin's and millstone's finished items go, as built now" + BuildCheck.FOR_YOU + ":\n" + out;
    }

    private static void basin(Level world, BlockPos p, List<String> faults, List<String> notes) {
        Direction out = null;
        List<String> blocked = new ArrayList<>();
        // Create takes the last open side in this order (a wrench on another open side picks that one instead).
        for (Direction d : Iterate.horizontalDirections) {
            String why = closed(world, p, d);
            if (why == null) out = d;
            else if (!why.isEmpty()) blocked.add(why);
        }
        String basin = "the basin at " + xyz(p);
        if (out != null) {
            BlockPos target = p.relative(out).below();
            BlockState t = world.getBlockState(target);
            String into = "its " + compass(out) + " side onto " + name(t) + " at " + xyz(target);
            if (t.getBlock() instanceof FunnelBlock) {
                BlockPos under = target.below();
                if (!holdsItems(world, under)) {
                    faults.add(basin + " pushes out " + into + ", but that funnel has no chest or other storage under it to "
                            + "fill, so items stop there; put storage under the funnel");
                    return;
                }
                notes.add(basin + " pushes finished items out " + into + ", which fills the " + name(world.getBlockState(under))
                        + " under it. The funnel must be set to insert: place it while sneaking (a funnel placed normally pulls "
                        + "out of what it sits on), or hit it with a wrench to switch");
            } else {
                notes.add(basin + " pushes finished items out " + into);
            }
            return;
        }
        if (!blocked.isEmpty()) {
            for (String b : blocked) faults.add(basin + ": " + b + ", so it keeps its results inside; " + RULE);
        } else {
            notes.add(basin + " has no output, so finished items stay inside it until taken out by hand or pulled out "
                    + "(a funnel pulling from a basin needs a filter, or it takes the ingredients too); " + RULE);
        }
    }

    /**
     * A millstone keeps what it grinds until something pulls it out: a hopper or an open chute right under it (they
     * pull from the block above), a funnel set to pull with its back on the millstone, or a Mechanical Arm.
     */
    private static void millstone(Level world, BlockPos p, List<String> faults, List<String> notes) {
        String mill = "the millstone at " + xyz(p);
        BlockPos below = p.below();
        BlockState b = world.getBlockState(below);
        String pulled = null;
        if (b.getBlock() instanceof HopperBlock) pulled = "the hopper under it";
        else if (AbstractChuteBlock.isOpenChute(b)) pulled = "the chute under it";
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            BlockState s = world.getBlockState(n);
            if (pulled == null && s.getBlock() instanceof FunnelBlock && FunnelBlock.getFunnelFacing(s) == d && s.getValue(FunnelBlock.EXTRACTING))
                pulled = "the funnel on its " + face(d) + ", which drops them in front of itself";
        }
        if (pulled == null && arm(world, p)) pulled = "a Mechanical Arm (if the player sets the millstone as its input)";
        if (pulled != null) {
            notes.add(mill + " keeps what it grinds until pulled out; " + pulled + " takes it out");
            return;
        }
        List<String> waiting = new ArrayList<>();
        // A hopper there feeds the millstone (one under it would already be pulling).
        for (Direction d : Direction.values())
            if (holdsItems(world, p.relative(d)) && !(world.getBlockState(p.relative(d)).getBlock() instanceof HopperBlock)) waiting.add(name(world.getBlockState(p.relative(d))) + " at " + xyz(p.relative(d)));
        String rule = "a millstone never pushes its results out; a hopper or chute right under it, or a funnel placed "
                + "normally (so it pulls) on its side, takes them out";
        if (!waiting.isEmpty()) faults.add(mill + " is next to " + String.join(" and ", waiting) + ", which get nothing from it: " + rule);
        else notes.add(mill + " keeps what it grinds inside until taken out by hand: " + rule);
    }

    /** A Mechanical Arm within its reach of {@code p}, which a player can point at it. */
    private static boolean arm(Level world, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-5, -5, -5), p.offset(5, 5, 5)))
            if (BuiltInRegistries.BLOCK.getKey(world.getBlockState(q).getBlock()).toString().equals("create:mechanical_arm")) return true;
        return false;
    }

    private static String face(Direction d) {
        return d == Direction.UP ? "top" : d == Direction.DOWN ? "bottom" : compass(d) + " side";
    }

    /**
     * Null when the basin can push out of side {@code d}; otherwise why not, worded for the player when something there
     * looks meant to take the output, or "" when that side simply has nothing to push into.
     */
    private static String closed(Level world, BlockPos p, Direction d) {
        BlockPos side = p.relative(d), target = side.below();
        BlockState s = world.getBlockState(side), t = world.getBlockState(target);
        boolean takes = takes(world, target, t, d);
        boolean sideOpen = FunnelBlock.isFunnel(s) ? FunnelBlock.getFunnelFacing(s) != d : s.getCollisionShape(world, side).isEmpty();
        if (takes && sideOpen) return null;
        if (takes) {
            return name(t) + " at " + xyz(target) + " could take its output on the " + compass(d) + " side, but " + name(s)
                    + " at " + xyz(side) + " fills the spot beside the basin and closes that side";
        }
        if (t.getBlock() instanceof FunnelBlock && !(t.getBlock() instanceof BeltFunnelBlock)) {
            if (FunnelBlock.getFunnelFacing(t) != Direction.UP)
                return "the funnel at " + xyz(target) + " is on the side of what it's attached to; only a funnel facing up "
                        + "(sitting on top of a chest) takes a basin's output";
            if (t.getValue(FunnelBlock.EXTRACTING))
                return "the funnel at " + xyz(target) + " is set to pull out of the chest under it, not to insert into it";
        }
        if (holdsItems(world, target) && !(t.getBlock() instanceof BasinBlock))
            return name(t) + " at " + xyz(target) + " sits one down on its " + compass(d) + " side, but a basin can't push "
                    + "straight into a " + name(t);
        return "";
    }

    /** Create's check on the block one down beside a basin: does it take items from that side, the way a belt does. */
    private static boolean takes(Level world, BlockPos target, BlockState t, Direction d) {
        // A chute only takes items once it ticks in a real world; any open chute there does.
        if (AbstractChuteBlock.isOpenChute(t)) return true;
        DirectBeltInputBehaviour input = BlockEntityBehaviour.get(world, target, DirectBeltInputBehaviour.TYPE);
        return input != null && input.canInsertFromSide(d);
    }

    /** A chest, barrel, vault or anything else with an item inventory. */
    private static boolean holdsItems(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.hasBlockEntity()) return false;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Container) return true;
        try {
            return Capabilities.ItemHandler.BLOCK.getCapability(world, pos, state, be, Direction.UP) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String name(BlockState s) {
        return s.isAir() ? "air" : BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String compass(Direction d) {
        return switch (d) {
            case NORTH -> "north (-z)";
            case SOUTH -> "south (+z)";
            case WEST -> "west (-x)";
            case EAST -> "east (+x)";
            default -> d.getName();
        };
    }
}

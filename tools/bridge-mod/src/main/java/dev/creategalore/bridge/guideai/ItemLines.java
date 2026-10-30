package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The Works lens's item test for a sequenced assembly line: follows the item the way Create moves it (along each belt
 * the way it turns, onto the next belt where one ends against another) and checks that it passes under a machine for
 * every step of the recipe, in order, and comes back round as many times as the recipe loops. Which way the whole
 * line turns depends on the source, which a model can't always tell, so the line is tried both ways round: running
 * the wrong way is something to tell the player, not a broken design.
 */
final class ItemLines {
    private static final int MAX_RIDE = 256;

    /** A place an item stops or passes on its way, and the machine over it (2 blocks up) if any. */
    private record Stop(BlockPos pos, String machine) {
    }

    private ItemLines() {
    }

    /** Null when the model has no belt or depot to follow. */
    static BuildCheck.Report check(Map<BlockPos, BlockState> cells, Map<BlockPos, Float> rpm, SequencedAssemblyRecipe recipe,
            String goalText) {
        List<Assembly.Step> steps = Assembly.steps(recipe);
        int loops = Assembly.loops(recipe);
        boolean byHand = goalText.toLowerCase(java.util.Locale.ROOT).contains("by hand");
        String heading = "How the item travels through the line" + BuildCheck.FOR_YOU + ":\n";
        Map<BlockPos, Direction> move = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            if (!id(e.getValue()).equals("create:belt")) continue;
            Direction facing = dir(e.getValue(), "facing");
            Float speed = rpm.get(e.getKey());
            if (facing == null || speed == null || speed == 0) continue;
            Direction.Axis axis = facing.getAxis();
            boolean negative = speed < 0 ^ axis == Direction.Axis.X;
            move.put(e.getKey(), Direction.fromAxisAndDirection(axis, negative ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE));
        }
        List<BlockPos> depots = new ArrayList<>();
        cells.forEach((p, s) -> {
            if (id(s).equals("create:depot")) depots.add(p);
        });
        if (move.isEmpty() && depots.isEmpty()) return null;
        String need = need(steps);
        for (Assembly.Step st : steps)
            if (!List.of("deploying", "pressing", "filling").contains(st.kind()))
                return new BuildCheck.Report(heading + "- Not shown: its step " + st.what() + " isn't one the item can be followed "
                        + "through here; say plainly how the item reaches each machine.", List.of());
        List<String> notes = new ArrayList<>();
        Result best = null;
        for (int way : new int[] {1, -1}) {
            for (BlockPos start : starts(cells, move, depots, way)) {
                List<Stop> ride = ride(cells, move, start, way);
                Result r = match(ride, steps, loops);
                if (best == null || r.score() > best.score()) best = r.withWay(way);
            }
        }
        if (best == null || best.done() == 0) {
            String fault = "no belt or depot here passes the item under the machines of the sequenced assembly (" + need
                    + "): every step works on an item lying 2 blocks under the machine, on a belt that carries it on to the next";
            return new BuildCheck.Report(heading + "- PROBLEM: " + fault + ".", List.of(fault));
        }
        String order = String.join(" -> ", best.passed());
        if (best.done() < steps.size()) {
            String fault = "following the item along the line it passes " + order + ", which does only " + best.done() + " of the "
                    + steps.size() + " steps in order (" + need + "); the next one, " + steps.get(best.done()).what()
                    + ", has no machine over the line after that";
            return new BuildCheck.Report(heading + "- PROBLEM: " + fault + ".", List.of(fault));
        }
        String turn = " The belts have to run so the item meets the machines in this order; which way they run follows the "
                + "source, so if they run the other way, reverse it (or which side water hits a wheel).";
        if (loops > 1 && !best.loops()) {
            String text = "the item passes " + order + " once and then leaves the line, but it has to go round " + loops
                    + " times";
            if (byHand) return new BuildCheck.Report(heading + "- " + text + ": the player puts it back at the start each time by "
                    + "hand." + turn, List.of());
            return new BuildCheck.Report(heading + "- PROBLEM: " + text + ". Bring the line back to its start (belts hand items on "
                    + "where one ends against another), or say in the goal that it is fed back by hand." + turn, List.of(text));
        }
        notes.add("The item passes " + order + ", every step in order" + (loops > 1 ? ", and the line brings it back round for all "
                + loops + " loops" : "") + "." + turn);
        notes.add("Each deployer holds the item for its own step (" + need + "); write which in the answer.");
        return new BuildCheck.Report(heading + "- " + String.join("\n- ", notes), List.of());
    }

    private record Result(int done, List<String> passed, boolean loops, int way) {
        int score() {
            return done * 2 + (loops ? 1 : 0);
        }

        Result withWay(int w) {
            return new Result(done, passed, loops, w);
        }
    }

    private static String need(List<Assembly.Step> steps) {
        List<String> out = new ArrayList<>();
        for (Assembly.Step s : steps) out.add(s.what());
        return String.join(", ", out);
    }

    /** Where an item can be put on: anywhere on a belt (a ring has no first segment) and each depot. */
    private static List<BlockPos> starts(Map<BlockPos, BlockState> cells, Map<BlockPos, Direction> move, List<BlockPos> depots, int way) {
        List<BlockPos> out = new ArrayList<>(depots);
        out.addAll(move.keySet());
        return out;
    }

    /** The stops an item rides through from {@code start}, belt to belt, until it falls off or comes round again. */
    private static List<Stop> ride(Map<BlockPos, BlockState> cells, Map<BlockPos, Direction> move, BlockPos start, int way) {
        List<Stop> out = new ArrayList<>();
        Map<BlockPos, Integer> seen = new LinkedHashMap<>();
        BlockPos p = start;
        while (p != null && out.size() < MAX_RIDE) {
            if (seen.containsKey(p)) {
                out.add(new Stop(p, "LOOP"));
                break;
            }
            seen.put(p, out.size());
            out.add(new Stop(p, machine(cells, p)));
            Direction d = move.get(p);
            if (d == null) break; // a depot: nothing moves the item on
            if (way < 0) d = d.getOpposite();
            BlockState s = cells.get(p);
            String slope = value(s, "slope");
            BlockPos next = p.relative(d);
            boolean along = d.getAxis() == dir(s, "facing").getAxis();
            if (along && slope != null) {
                // A sloped belt climbs toward its facing: going with the facing on an upward slope goes up.
                boolean withFacing = d == dir(s, "facing");
                if (slope.equals("upward")) next = withFacing ? next.above() : next.below();
                else if (slope.equals("downward")) next = withFacing ? next.below() : next.above();
            }
            if (move.containsKey(next)) p = next;
            else if (move.containsKey(next.below())) p = next.below();
            else if (cells.containsKey(next) && id(cells.get(next)).equals("create:depot")) p = next;
            else p = null;
        }
        return out;
    }

    /** Greedy: how many steps the ride does in order, the machines it passed for them, and whether it comes round again. */
    private static Result match(List<Stop> ride, List<Assembly.Step> steps, int loops) {
        int done = 0;
        List<String> passed = new ArrayList<>();
        boolean loop = !ride.isEmpty() && ride.get(ride.size() - 1).machine().equals("LOOP");
        int first = -1, last = -1;
        for (int i = 0; i < ride.size() && done < steps.size(); i++) {
            Stop s = ride.get(i);
            if (s.machine().isEmpty() || s.machine().equals("LOOP")) continue;
            if (s.machine().equals(steps.get(done).kind())) {
                passed.add(label(s));
                if (first < 0) first = i;
                last = i;
                done++;
            }
        }
        // The loop has to take the item back through the steps: the ride comes round to a stop at or before the first step.
        boolean loops2 = false;
        if (loop && first >= 0) {
            BlockPos back = ride.get(ride.size() - 1).pos();
            for (int i = 0; i <= first; i++) if (ride.get(i).pos().equals(back)) loops2 = true;
        }
        return new Result(done, passed, loops == 1 || loops2, 1);
    }

    private static String label(Stop s) {
        String name = switch (s.machine()) {
            case "deploying" -> "a deployer";
            case "pressing" -> "a press";
            case "filling" -> "a spout";
            default -> s.machine();
        };
        return name + " over " + s.pos().getX() + "," + s.pos().getY() + "," + s.pos().getZ();
    }

    /** The kind of work the machine two blocks over this spot does on an item there, or "" for none. */
    private static String machine(Map<BlockPos, BlockState> cells, BlockPos p) {
        BlockState over = cells.get(p.above(2));
        if (over == null) return "";
        BlockState gap = cells.get(p.above());
        if (gap != null && !gap.isAir() && !gap.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, p.above()).isEmpty())
            return "";
        return switch (id(over)) {
            case "create:deployer" -> dir(over, "facing") == Direction.DOWN ? "deploying" : "";
            case "create:mechanical_press" -> "pressing";
            case "create:spout" -> "filling";
            default -> "";
        };
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static String value(BlockState s, String name) {
        for (Property<?> p : s.getProperties()) if (p.getName().equals(name)) return valueName(s, p);
        return null;
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    private static Direction dir(BlockState s, String name) {
        String v = value(s, name);
        return v == null ? null : Direction.byName(v);
    }
}

package dev.creategalore.bridge.guideai;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

/**
 * Whether a build works for the player using it, not only for its machines: posts that rest on something, and on a
 * vehicle a crew that can walk from the helm to everything they have to tend, climb aboard once it has landed, see
 * out ahead from the helm, and stay warm at the heights it climbs to.
 *
 * <p>Players are modelled the way Minecraft moves them: 0.6 wide and 1.8 tall, eyes 1.62 up, a jump of 1.25 (a full
 * block and a slab's step), a 4.5 block reach that needs a clear line to the block. Doors, trapdoors and fence gates
 * are walked through (a player opens them); ladders, vines and scaffolding are climbed. Warmth uses Cold Sweat
 * Altitude's own shelter test (ShelterManager.enclosure, radius 4 as the pack's coldsweat_altitude-server.toml sets
 * it) on the blocks of the vehicle: from y 128 the open air chills, from y 192 (low_sky, wind 0.65) it freezes a bare
 * player within about half a minute, and a place counts as sheltered from 0.9 enclosure.
 */
final class PlayerCheck {
    private static final double REACH = 4.5, EYE = 1.62, HEIGHT = 1.8, JUMP = 1.25, DROP = 3.5;
    private static final int COLD = 128, FREEZING = 192, SHELTER_RADIUS = 4;
    private static final double SHELTERED = 0.9;
    /** A seated player's eyes above the seat block's bottom, and how far a line of sight is followed. */
    private static final double SEATED_EYE = 1.3, SIGHT = 24;

    private PlayerCheck() {
    }

    /** Where a player can be: feet in {@code cell} on a floor at {@code top}, or holding a ladder there. */
    private record Node(BlockPos cell, double top, boolean climb, boolean ground) {
        Vec3 eye() {
            return new Vec3(cell.getX() + 0.5, top + EYE, cell.getZ() + 0.5);
        }

        /** The block Cold Sweat Altitude tests shelter from: one above the player's feet. */
        BlockPos head() {
            return BlockPos.containing(cell.getX() + 0.5, top + 1, cell.getZ() + 0.5);
        }
    }

    /** The blocks of a model as a world of their own, air everywhere else. */
    private record Cells(Map<BlockPos, BlockState> blocks) implements BlockGetter {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    // ---------- posts ----------

    /**
     * Fences and walls stand as posts and rails: a post shows a gap, and joins nothing, where the block it stands on
     * or meets has no full face under its middle (a bottom slab's top, a top slab's underside, a trapdoor's edge).
     * Null when nothing is wrong.
     */
    static BuildCheck.Report posts(Map<BlockPos, BlockState> blocks) {
        BlockGetter getter = new Cells(blocks);
        List<String> gaps = new ArrayList<>();
        for (BlockPos p : sorted(blocks.keySet())) {
            BlockState s = blocks.get(p);
            if (!post(s)) continue;
            BlockState below = blocks.get(p.below()), above = blocks.get(p.above());
            if (below != null && !post(below) && !below.getCollisionShape(getter, p.below()).isEmpty()
                    && !below.isFaceSturdy(getter, p.below(), Direction.UP, SupportType.CENTER))
                gaps.add(name(s) + " at " + xyz(p) + " stands on " + what(below) + " at " + xyz(p.below()));
            if (above != null && !post(above) && !above.getCollisionShape(getter, p.above()).isEmpty()
                    && !above.isFaceSturdy(getter, p.above(), Direction.DOWN, SupportType.CENTER))
                gaps.add(name(s) + " at " + xyz(p) + " meets " + what(above) + " at " + xyz(p.above()) + " above it");
        }
        if (gaps.isEmpty()) return null;
        String list = gaps.size() > 6 ? String.join("; ", gaps.subList(0, 6)) + " and " + (gaps.size() - 6) + " more"
                : String.join("; ", gaps);
        String fault = "posts that don't meet what holds them: " + list + ". A fence or wall post only reaches a full face, so "
                + "a half-block gap shows there and the post joins nothing; stand posts on full blocks or top slabs, and end "
                + "them under full blocks or bottom slabs";
        return new BuildCheck.Report("How its posts meet the blocks around them" + BuildCheck.FOR_YOU + ":\n- PROBLEM: "
                + fault + ".", List.of(fault));
    }

    private static boolean post(BlockState s) {
        return s != null && (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS));
    }

    private static String what(BlockState s) {
        if (s.getBlock() instanceof SlabBlock && s.hasProperty(SlabBlock.TYPE))
            return (s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? "a bottom " : "a top ") + name(s);
        if (s.getBlock() instanceof TrapDoorBlock) return "the edge of " + name(s);
        return name(s);
    }

    // ---------- the crew ----------

    /**
     * The crew's checks on a vehicle, added to its faults and notes: fuel and the Physics Assembler within walking
     * reach of the helm, a way aboard from the ground it lands on (when it lands), and a sheltered helm at the heights
     * it reaches ({@code ceiling}, NaN when it doesn't fly).
     */
    static void vehicle(Map<BlockPos, BlockState> blocks, double ceiling, boolean lands, List<String> faults, List<String> notes) {
        BlockGetter getter = new Cells(blocks);
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int groundY = Integer.MAX_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            if (!e.getValue().getCollisionShape(getter, p).isEmpty()) groundY = Math.min(groundY, p.getY());
        }
        if (groundY == Integer.MAX_VALUE) return;
        final int ground = groundY;

        Map<BlockPos, Node> aboard = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            if (e.getValue().is(BlockTags.CLIMBABLE)) aboard.put(p, new Node(p, p.getY(), true, false));
            BlockPos cell = p.above();
            if (aboard.containsKey(cell)) continue;
            Double top = VehicleCheck.footing(blocks, getter, Vec3.atBottomCenterOf(cell).add(0, 0.25, 0));
            if (top == null || top < cell.getY() - 0.5 || top > cell.getY() + 0.25) continue;
            if (!free(blocks, getter, cell, top, top + HEIGHT)) continue;
            aboard.put(cell, new Node(cell, top, false, false));
        }
        // Landed, it rests on its lowest blocks: the ground's top is their bottom, and a player can stand anywhere
        // around and under it that leaves room.
        Map<BlockPos, Node> landed = new HashMap<>(aboard);
        for (int x = minX - 2; x <= maxX + 2; x++)
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                BlockPos cell = new BlockPos(x, groundY, z);
                if (!landed.containsKey(cell) && free(blocks, getter, cell, groundY, groundY + HEIGHT))
                    landed.put(cell, new Node(cell, groundY, false, true));
            }

        // The helm: wherever the controls can be worked from, or beside a seat when there are none.
        List<BlockPos> controls = new ArrayList<>(), seats = new ArrayList<>(), fuel = new ArrayList<>(), assemblers = new ArrayList<>();
        blocks.forEach((p, s) -> {
            String id = id(s);
            if (VehicleCheck.isControl(id)) controls.add(p);
            if (id.endsWith("_seat") || id.endsWith(":seat")) seats.add(p);
            if (fuelled(id)) fuel.add(p);
            if (id.equals("simulated:physics_assembler")) assemblers.add(p);
        });
        Set<Node> helm = new LinkedHashSet<>();
        for (Node n : aboard.values()) {
            if (n.climb) continue;
            if (!controls.isEmpty() ? controls.stream().anyMatch(c -> reaches(blocks, getter, n.eye(), c, null))
                    : seats.stream().anyMatch(c -> Math.abs(c.getX() - n.cell.getX()) + Math.abs(c.getZ() - n.cell.getZ()) <= 1
                    && Math.abs(c.getY() - n.cell.getY()) <= 1)) helm.add(n);
        }

        if (!helm.isEmpty()) {
            // The crew walks from the helm and back again: only places both ways count as in their reach.
            Set<Node> from = walk(blocks, getter, aboard, helm, false), back = walk(blocks, getter, aboard, helm, true);
            Set<Node> crew = new HashSet<>(from);
            crew.retainAll(back);
            // Grouped by what keeps the crew away: one line for every engine stranded the same way.
            Map<Boolean, List<String>> cut = new java.util.TreeMap<>();
            List<String> cold = new ArrayList<>();
            boolean freezing = ceiling >= FREEZING;
            for (BlockPos f : sorted(fuel)) {
                // Fuel goes in by hand at the engine, or anywhere along a funnel, chute or hopper line feeding it and the
                // storage that line takes from; a funnel fed from nothing the crew reaches doesn't help.
                Set<BlockPos> points = feeds(blocks, f);
                List<Node> at = aboard.values().stream().filter(n -> points.stream().anyMatch(q -> reaches(blocks, getter, n.eye(), q, null))).toList();
                List<Node> mine = at.stream().filter(crew::contains).toList();
                String what = name(blocks.get(f));
                if (mine.isEmpty())
                    cut.computeIfAbsent(at.isEmpty(), k -> new ArrayList<>()).add(what + " at " + xyz(f));
                else if (freezing && mine.stream().noneMatch(n -> sheltered(getter, n.head()))) cold.add(what + " at " + xyz(f));
            }
            if (!cut.isEmpty()) {
                List<String> why = new ArrayList<>();
                cut.forEach((nowhere, list) -> {
                    String it = list.size() > 1 ? "them" : "it";
                    why.add(String.join(", ", list) + ": " + (nowhere ? "there is nowhere on board to stand within reach of " + it
                            + ", in plain view" : "the places to stand by " + it + " can't be walked to from the helm (no floor, "
                            + "steps or ladder leads there)"));
                });
                faults.add("no one can get to " + String.join("; ", why) + ". So nobody can put fuel in while it travels, and it "
                        + "stops when the fuel runs out. Put whatever burns fuel where the crew walks: in or beside the cabin, or "
                        + "along a railed walkway, steps or ladder from it, within 4.5 blocks of where they stand with nothing in "
                        + "between (or feed it with a funnel or chute from a hold they can reach)");
            }
            if (!cold.isEmpty())
                notes.add("fuelling " + String.join(", ", cold) + " means standing out in the open air, and at the heights it "
                        + "climbs (from y " + FREEZING + ") that freezes a player in under a minute: better reached from inside a "
                        + "closed room");

            if (lands) boarding(blocks, getter, landed, crew, groundY, faults);

            view(blocks, getter, helm, controls, seats, faults);

            if (ceiling >= COLD) {
                double best = 0;
                for (Node n : helm) best = Math.max(best, enclosure(getter, n.head()));
                for (BlockPos s : seats) if (helm.stream().anyMatch(n -> n.cell.distManhattan(s) <= 2)) best = Math.max(best, enclosure(getter, s.above()));
                if (!Double.isNaN(best) && best < SHELTERED) {
                    String where = controls.isEmpty() ? "its seat" : controls.stream().limit(3).map(c -> name(blocks.get(c)) + " at " + xyz(c))
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    String fix = "Close a cabin around the helm: walls, a roof and a door or hatch that shuts (glass and glass panes "
                            + "count), within 4 blocks of the pilot on every side, so whoever flies it stays warm";
                    if (freezing)
                        faults.add(String.format(Locale.ROOT, "it climbs to about y %.0f, and above y %d the open wind freezes a player in "
                                + "under a minute (the cold starts at y %d), but whoever works %s stands in the open air. %s",
                                ceiling, FREEZING, COLD, where, fix));
                    else
                        notes.add(String.format(Locale.ROOT, "it climbs to about y %.0f, where the open wind chills a player who stays "
                                + "out in it; whoever works %s stands in the open air there. %s", ceiling, where, fix));
                }
            }
        }

        for (BlockPos a : assemblers) {
            boolean used = landed.values().stream().anyMatch(n -> reaches(blocks, getter, n.eye(), a, ground));
            if (!used)
                faults.add("no one can use the Physics Assembler at " + xyz(a) + ": it is shut in by blocks on every side a player "
                        + "could reach it from (to assemble or put the ship down, they hold right-click on it and pull its lever), "
                        + "so set it where it stays open to a floor the crew stands on, such as into the cabin floor or the deck");
        }
    }

    /** Can a player climb aboard from the ground the vehicle has landed on, and walk to the helm? */
    private static void boarding(Map<BlockPos, BlockState> blocks, BlockGetter getter, Map<BlockPos, Node> landed, Set<Node> crew,
                                 int groundY, List<String> faults) {
        Set<Node> start = new HashSet<>();
        for (Node n : landed.values()) if (n.ground) start.add(n);
        Set<Node> reached = walk(blocks, getter, landed, start, false);
        if (crew.stream().anyMatch(reached::contains)) return;
        // The lowest place the crew can step out from: a spot with open air beside it.
        Node low = null;
        for (Node n : crew) {
            if (n.climb) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos out = n.cell.relative(d);
                if (landed.containsKey(out) && !landed.get(out).ground) continue;
                if (!free(blocks, getter, out, n.top, n.top + HEIGHT)) continue;
                if (low == null || n.top < low.top) low = n;
            }
        }
        if (low == null) {
            faults.add("once it has landed no one can climb aboard: nothing leads in from the ground to where the crew stands. "
                    + "Give it a door at the side with steps or a ladder down to the ground");
            return;
        }
        BlockState at = blocks.get(low.cell);
        String way = at != null && at.getBlock() instanceof DoorBlock ? "the " + name(at) + " at " + xyz(low.cell)
                : "the opening at " + xyz(low.cell);
        faults.add(String.format(Locale.ROOT, "once it has landed no one can climb aboard: the lowest way in, %s, is %.1f blocks above "
                + "the ground it rests on (its lowest blocks stand on it), and a player only jumps %.2f. Give it a ladder or steps "
                + "outside the door down to the ground, or lower the door to the floor of a lower deck", way, low.top - groundY, JUMP));
    }

    /**
     * Every place a player gets to from {@code start} (or, {@code back}, every place they get to {@code start} from):
     * stepping and jumping up to 1.25 between neighbouring places with room to pass, dropping up to 3.5, and climbing.
     */
    private static Set<Node> walk(Map<BlockPos, BlockState> blocks, BlockGetter getter, Map<BlockPos, Node> nodes, Set<Node> start,
                                  boolean back) {
        Set<Node> seen = new HashSet<>(start);
        ArrayDeque<Node> todo = new ArrayDeque<>(start);
        while (!todo.isEmpty()) {
            Node a = todo.poll();
            for (Node b : near(nodes, a)) {
                if (seen.contains(b)) continue;
                if (back ? !step(blocks, getter, b, a) : !step(blocks, getter, a, b)) continue;
                seen.add(b);
                todo.add(b);
            }
        }
        return seen;
    }

    private static List<Node> near(Map<BlockPos, Node> nodes, Node a) {
        List<Node> out = new ArrayList<>();
        for (int dy = -4; dy <= 2; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Node n = nodes.get(a.cell.relative(d).above(dy));
                if (n != null) out.add(n);
            }
            if (dy != 0) {
                Node n = nodes.get(a.cell.above(dy));
                if (n != null) out.add(n);
            }
        }
        return out;
    }

    /** One move from {@code a} to {@code b}, a neighbouring place. */
    private static boolean step(Map<BlockPos, BlockState> blocks, BlockGetter getter, Node a, Node b) {
        boolean beside = a.cell.getX() != b.cell.getX() || a.cell.getZ() != b.cell.getZ();
        if (!beside) {
            // Up or down one column: only on a ladder, or off its top onto a floor.
            return (a.climb || b.climb) && Math.abs(a.cell.getY() - b.cell.getY()) <= 1;
        }
        double rise = b.top - a.top;
        if (a.climb || b.climb) return rise <= JUMP + 1 && rise >= -DROP;
        if (rise > JUMP || rise < -DROP) return false;
        double hi = Math.max(a.top, b.top);
        // Room to rise in its own column and to pass over into the next.
        return free(blocks, getter, a.cell, a.top, hi + HEIGHT) && free(blocks, getter, b.cell, hi, hi + HEIGHT);
    }

    /** Room for a player's body in {@code cell} from y0 to y1, walking through doors, trapdoors, gates and ladders. */
    private static boolean free(Map<BlockPos, BlockState> blocks, BlockGetter getter, BlockPos cell, double y0, double y1) {
        AABB body = new AABB(cell.getX() + 0.2, y0 + 0.01, cell.getZ() + 0.2, cell.getX() + 0.8, y1, cell.getZ() + 0.8);
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            BlockState s = blocks.get(q);
            if (s == null || passable(s)) continue;
            VoxelShape shape = s.getCollisionShape(getter, q);
            if (!shape.isEmpty() && shape.move(q.getX(), q.getY(), q.getZ()).toAabbs().stream().anyMatch(b -> b.intersects(body)))
                return false;
        }
        return true;
    }

    private static boolean passable(BlockState s) {
        return s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock || s.getBlock() instanceof FenceGateBlock
                || s.is(BlockTags.CLIMBABLE);
    }

    /**
     * Whether a player with eyes at {@code eye} can click {@code target}: some point of it within 4.5 blocks with
     * nothing in the way. With {@code groundY}, the vehicle has landed and the ground below that height blocks the view.
     */
    private static boolean reaches(Map<BlockPos, BlockState> blocks, BlockGetter getter, Vec3 eye, BlockPos target, Integer groundY) {
        if (new AABB(target).distanceToSqr(eye) > REACH * REACH) return false;
        BlockState t = blocks.get(target);
        VoxelShape shape = t == null ? null : t.getShape(getter, target);
        List<AABB> boxes = shape == null || shape.isEmpty() ? List.of(new AABB(target)) : shape.move(target.getX(), target.getY(), target.getZ()).toAabbs();
        for (AABB box : boxes) {
            AABB in = box.deflate(0.02);
            Vec3 c = in.getCenter();
            for (Vec3 pt : new Vec3[] {c, new Vec3(in.minX, c.y, c.z), new Vec3(in.maxX, c.y, c.z), new Vec3(c.x, in.minY, c.z),
                    new Vec3(c.x, in.maxY, c.z), new Vec3(c.x, c.y, in.minZ), new Vec3(c.x, c.y, in.maxZ)}) {
                if (pt.distanceToSqr(eye) > REACH * REACH) continue;
                if (groundY != null && Math.min(pt.y, eye.y) < groundY) continue;
                if (clear(blocks, getter, eye, pt, target)) return true;
            }
        }
        return false;
    }

    private static boolean clear(Map<BlockPos, BlockState> blocks, BlockGetter getter, Vec3 from, Vec3 to, BlockPos target) {
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
                BlockPos.containing(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)))) {
            if (q.equals(target)) continue;
            BlockState s = blocks.get(q);
            if (s == null) continue;
            VoxelShape shape = s.getShape(getter, q);
            if (shape.isEmpty()) continue;
            for (AABB b : shape.move(q.getX(), q.getY(), q.getZ()).toAabbs())
                if (!b.contains(from) && b.clip(from, to).isPresent()) return false;
        }
        return true;
    }

    /** Blocks a player puts fuel in by hand. */
    private static boolean fuelled(String id) {
        return id.startsWith("simulated:") && id.endsWith("_portable_engine") || id.equals("create_high_seas:boat_engine")
                || id.equals("create:blaze_burner") || id.equals("createpropulsion:solid_burner")
                || id.equals("createpropulsion:solid_fuel_thruster");
    }

    /**
     * Where fuel can be put in for the engine at {@code p}: the engine itself, and the funnels, chutes and hoppers feeding
     * it, followed back through each other to the storage, belts and depots they take from (up to 24 blocks).
     */
    private static Set<BlockPos> feeds(Map<BlockPos, BlockState> blocks, BlockPos p) {
        Set<BlockPos> out = new LinkedHashSet<>();
        out.add(p);
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(p);
        while (!todo.isEmpty() && out.size() < 25) {
            BlockPos a = todo.poll();
            boolean line = a.equals(p) || isFeedLine(id(blocks.get(a)));
            if (!line) continue;
            for (Direction d : Direction.values()) {
                BlockPos q = a.relative(d);
                BlockState s = blocks.get(q);
                if (s == null || out.contains(q)) continue;
                String id = id(s);
                if (isFeedLine(id) || !a.equals(p) && java.util.stream.Stream.of("barrel", "chest", "vault", "crate", "shulker_box", "belt", "depot").anyMatch(id::contains)) {
                    out.add(q);
                    todo.add(q);
                }
            }
        }
        return out;
    }

    private static boolean isFeedLine(String id) {
        return id.contains("funnel") || id.contains("chute") || id.endsWith(":hopper");
    }

    // ---------- the view ahead ----------

    /** Where the pilot's eyes are and how that place reads in a report. */
    private record Eye(Vec3 at, String where) {
    }

    /** What one place at the helm sees ahead: lines out in the build and through its shell alone, and what stops the rest. */
    private record Sight(Eye eye, int open, int shell, Map<BlockPos, Integer> blockers) {
    }

    /**
     * Whether whoever steers can see where they are going. From each place at the helm, and each seat beside it (eyes
     * about 1.3 above the seat block), lines of sight fan out ahead toward the controls they work (the Steering Wheel when
     * there is one): 35 degrees either side, 15 down to 10 up. Glass, panes and ice let sight through; anything else stops
     * it. The same lines are cast again through the vehicle's shell alone (full blocks, glass, stairs, slabs, fences,
     * walls, doors and trapdoors) for the view its windows and openings give, and whatever stops a line the shell leaves
     * open (a control, link, machine or fitting) is blocking the view. The pilot takes the best place at the helm: from
     * there, more than a quarter of the view out lost, or no view out ahead at all, is a fault.
     */
    private static void view(Map<BlockPos, BlockState> blocks, BlockGetter getter, Set<Node> helm, List<BlockPos> controls,
                             List<BlockPos> seats, List<String> faults) {
        if (controls.isEmpty()) return;
        List<Eye> eyes = new ArrayList<>();
        for (Node n : helm) eyes.add(new Eye(n.eye(), "standing at " + xyz(n.cell)));
        for (BlockPos s : seats)
            if (helm.stream().anyMatch(n -> n.cell.distManhattan(s) <= 2))
                eyes.add(new Eye(new Vec3(s.getX() + 0.5, s.getY() + SEATED_EYE, s.getZ() + 0.5), "sitting on the " + name(blocks.get(s))
                        + " at " + xyz(s)));
        Sight best = null;
        for (Eye eye : eyes) {
            Vec3 ahead = ahead(blocks, getter, eye.at, controls);
            if (ahead == null) continue;
            Sight s = sight(blocks, getter, eye, ahead);
            if (best == null || s.open > best.open || s.open == best.open && s.shell > best.shell) best = s;
        }
        if (best == null) return;
        if (best.shell == 0) {
            faults.add("whoever steers can't see where it goes: " + best.eye.where + " (the best place at the helm), every line of "
                    + "sight ahead ends at the vehicle's own walls, roof or floor. Put windows (glass or panes) or an opening in "
                    + "the wall ahead of the helm, at the pilot's eye height (about 1.6 above the floor they stand on)");
            return;
        }
        int lost = best.shell - best.open;
        if (lost * 4 <= best.shell) return;
        List<String> what = best.blockers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                .map(e -> name(blocks.get(e.getKey())) + " at " + xyz(e.getKey())).toList();
        int more = best.blockers.size() - what.size();
        faults.add(String.format(Locale.ROOT, "the pilot can't see out ahead: %s (the best place at the helm), %d%% of the view out "
                + "through the windows and openings ahead is blocked by %s%s. Set controls, links, levers and anything else at "
                + "the window on a dashboard below the window sill (under the pilot's eye height) or on the wall beside the "
                + "window, and keep the glass in front of the pilot clear", best.eye.where, Math.round(100.0 * lost / best.shell),
                String.join(", ", what), more > 0 ? " and " + more + " more" : ""));
    }

    /** Level, from the eye toward what the pilot steers with: the nearest Steering Wheel in reach, else the controls in reach. */
    private static Vec3 ahead(Map<BlockPos, BlockState> blocks, BlockGetter getter, Vec3 eye, List<BlockPos> controls) {
        List<BlockPos> mine = controls.stream().filter(c -> reaches(blocks, getter, eye, c, null)).toList();
        if (mine.isEmpty()) return null;
        Vec3 to = mine.stream().filter(c -> id(blocks.get(c)).equals("simulated:steering_wheel")).map(Vec3::atCenterOf)
                .min(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(eye))).orElse(null);
        if (to == null) {
            double x = 0, z = 0;
            for (BlockPos c : mine) {
                x += c.getX() + 0.5;
                z += c.getZ() + 0.5;
            }
            to = new Vec3(x / mine.size(), eye.y, z / mine.size());
        }
        Vec3 f = new Vec3(to.x - eye.x, 0, to.z - eye.z);
        return f.length() < 0.3 ? null : f.normalize();
    }

    private static Sight sight(Map<BlockPos, BlockState> blocks, BlockGetter getter, Eye eye, Vec3 ahead) {
        int open = 0, shell = 0;
        Map<BlockPos, Integer> blockers = new HashMap<>();
        for (int yaw = -35; yaw <= 35; yaw += 5)
            for (int pitch = -15; pitch <= 10; pitch += 5) {
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                double x = ahead.x * Math.cos(y) - ahead.z * Math.sin(y), z = ahead.x * Math.sin(y) + ahead.z * Math.cos(y);
                Vec3 to = eye.at.add(new Vec3(x * Math.cos(p), Math.sin(p), z * Math.cos(p)).scale(SIGHT));
                BlockPos all = sightStop(blocks, getter, eye.at, to, false);
                if (all == null) open++;
                if (sightStop(blocks, getter, eye.at, to, true) != null) continue;
                shell++;
                if (all != null) blockers.merge(all, 1, Integer::sum);
            }
        return new Sight(eye, open, shell, blockers);
    }

    /** The first block that stops sight from {@code from} to {@code to}, or null; {@code shell} looks past all but the shell. */
    private static BlockPos sightStop(Map<BlockPos, BlockState> blocks, BlockGetter getter, Vec3 from, Vec3 to, boolean shell) {
        return BlockGetter.traverseBlocks(from, to, null, (ctx, pos) -> {
            BlockState s = blocks.get(pos);
            if (s == null || seeThrough(s) || shell && !shell(getter, pos, s)) return null;
            VoxelShape shape = s.getShape(getter, pos);
            if (shape.isEmpty() || shape.move(pos.getX(), pos.getY(), pos.getZ()).toAabbs().stream().anyMatch(b -> b.contains(from)))
                return null;
            return shape.clip(from, to, pos) != null ? pos.immutable() : null;
        }, ctx -> null);
    }

    private static boolean seeThrough(BlockState s) {
        return s.getBlock() instanceof HalfTransparentBlock || s.getBlock() instanceof IronBarsBlock
                || s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES);
    }

    /** What a vehicle is built of, as against what is fitted into it: controls, links, machines, lamps and the like. */
    private static boolean shell(BlockGetter getter, BlockPos p, BlockState s) {
        if (s.hasBlockEntity() || VehicleCheck.isControl(id(s))) return false;
        Block b = s.getBlock();
        return seeThrough(s) || s.isCollisionShapeFullBlock(getter, p) || b instanceof StairBlock || b instanceof SlabBlock
                || b instanceof WallBlock || b instanceof FenceBlock || b instanceof FenceGateBlock || b instanceof DoorBlock
                || b instanceof TrapDoorBlock;
    }

    // ---------- warmth ----------

    private static Method enclosure;

    /** Cold Sweat Altitude's enclosure (0 open to 1 shut in) around {@code head}, or NaN when the mod isn't there. */
    private static double enclosure(BlockGetter getter, BlockPos head) {
        try {
            if (enclosure == null) {
                Method m = Class.forName("net.sprocketgames.coldsweataltitude.shelter.ShelterManager")
                        .getDeclaredMethod("enclosure", BlockGetter.class, BlockPos.class, int.class);
                m.setAccessible(true);
                enclosure = m;
            }
            return (double) enclosure.invoke(null, getter, head, SHELTER_RADIUS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Double.NaN;
        }
    }

    private static boolean sheltered(BlockGetter getter, BlockPos head) {
        double e = enclosure(getter, head);
        return Double.isNaN(e) || e >= SHELTERED;
    }

    /** Bottom to top, then along x and z, so a report reads the same each time. */
    private static List<BlockPos> sorted(java.util.Collection<BlockPos> ps) {
        return ps.stream().sorted(java.util.Comparator.<BlockPos>comparingInt(q -> q.getY()).thenComparingInt(q -> q.getX())
                .thenComparingInt(q -> q.getZ())).toList();
    }

    private static String name(BlockState s) {
        return dev.creategalore.bridge.DiagonalJoins.plain(s.getBlock()).getName().getString();
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }
}

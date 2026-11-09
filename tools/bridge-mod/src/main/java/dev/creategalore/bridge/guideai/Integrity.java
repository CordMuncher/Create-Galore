package dev.creategalore.bridge.guideai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Whether a vehicle looks built rather than scattered: every part joins the rest where you can see it, and the hull
 * can't be seen through. Sable and glue join blocks by their cells, so a Grindstone under a top slab, a fin above a
 * slab boom or a sail laid on top of a block is part of the ship, but what the player sees is a part hanging in the
 * air with a gap round it. And a fuselage of slabs and loose parts shows the sky through it from the side. Both are
 * judged from the blocks' own shapes (what the game draws round them), so stairs, slabs and thin parts count as they
 * look.
 */
final class Integrity {
    /** Blocks smaller than this (lanterns, levers, buttons, torches, links) are trim: they neither join nor hang. */
    private static final double TRIM = 0.15;
    /** Sub-pixels per block edge for the see-through test. */
    private static final int RES = 4;
    /** A see-through hole at least this big (blocks²) is a fault: about a pane's worth, not a crack between slabs. */
    private static final double HOLE = 0.5;

    private Integrity() {
    }

    /** Adds what hangs loose and where the hull is see-through to faults (a model) or notes (a build in the world). */
    static void check(Map<BlockPos, BlockState> blocks, BlockGetter g, boolean model, List<String> faults, List<String> notes) {
        Map<BlockPos, List<AABB>> shapes = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockState s = e.getValue();
            if (s.isAir() || s.getBlock() instanceof LiquidBlock) continue;
            VoxelShape v;
            try {
                v = s.getShape(g, e.getKey());
            } catch (RuntimeException ex) {
                continue;
            }
            if (v.isEmpty()) continue;
            List<AABB> boxes = v.toAabbs();
            double vol = 0;
            for (AABB b : boxes) vol += b.getXsize() * b.getYsize() * b.getZsize();
            // Small parts are trim, unless they run right through their cell (a shaft, a chain, a fence post).
            boolean through = false;
            for (AABB b : boxes)
                for (Direction.Axis a : Direction.Axis.values()) if (b.min(a) < 1e-4 && b.max(a) > 1 - 1e-4) through |= b.getSize() > 0 && vol > 0.02;
            if (vol < TRIM && !through) continue;
            shapes.put(e.getKey(), boxes);
        }
        loose(blocks, shapes, model ? faults : notes, model);
        seeThrough(blocks, shapes, model ? faults : notes, model);
    }

    // ---------- loose parts ----------

    private static void loose(Map<BlockPos, BlockState> blocks, Map<BlockPos, List<AABB>> shapes, List<String> out, boolean model) {
        Set<BlockPos> left = new HashSet<>(shapes.keySet());
        List<List<BlockPos>> parts = new ArrayList<>();
        while (!left.isEmpty()) {
            BlockPos start = left.iterator().next();
            left.remove(start);
            List<BlockPos> part = new ArrayList<>(List.of(start));
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(part);
            while (!todo.isEmpty()) {
                BlockPos p = todo.poll();
                for (Direction d : Direction.values()) {
                    BlockPos q = p.relative(d);
                    if (!left.contains(q)) continue;
                    // Two Create parts side by side mesh or couple (cogwheels' teeth reach past their shapes).
                    boolean kinetic = blocks.get(p).getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate
                            && blocks.get(q).getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate;
                    // A burner sits under the balloon's open bottom by design: its flame fills the gap up to the envelope.
                    boolean flame = d.getAxis() == Direction.Axis.Y && (burner(blocks.get(d == Direction.UP ? p : q)));
                    if (!kinetic && !flame && !joined(shapes.get(p), shapes.get(q), d)) continue;
                    left.remove(q);
                    part.add(q);
                    todo.add(q);
                }
                // A large cogwheel meshes with small ones a step off its axis diagonally; their teeth meet.
                if (!(blocks.get(p).getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.ICogWheel)) continue;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 2) continue;
                            BlockPos q = p.offset(dx, dy, dz);
                            if (!left.contains(q) || !(blocks.get(q).getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.ICogWheel))
                                continue;
                            left.remove(q);
                            part.add(q);
                            todo.add(q);
                        }
            }
            parts.add(part);
        }
        if (parts.size() < 2) return;
        parts.sort((a, b) -> b.size() - a.size());
        if (Boolean.getBoolean("creategalore.integrity.debug"))
            for (List<BlockPos> part : parts.subList(1, parts.size())) {
                StringBuilder sb = new StringBuilder("[integrity] loose:");
                for (BlockPos q : part) sb.append(' ').append(BuiltInRegistries.BLOCK.getKey(blocks.get(q).getBlock()).getPath())
                        .append('@').append(xyz(q)).append(gapTo(q, blocks, shapes));
                if (part.size() == 1) for (Direction d : Direction.values()) {
                    BlockState n = blocks.get(part.get(0).relative(d));
                    if (n != null) sb.append(" [").append(d).append('=').append(n).append(']');
                }
                System.out.println(sb);
            }
        List<String> list = new ArrayList<>();
        for (int i = 1; i < parts.size() && list.size() < 6; i++) {
            List<BlockPos> part = parts.get(i);
            BlockPos at = part.get(0);
            String name = blocks.get(at).getBlock().getName().getString();
            String gap = gapTo(at, blocks, shapes);
            list.add((part.size() == 1 ? "the " + name : part.size() + " blocks (" + name + " and more)") + " at " + xyz(at) + gap);
        }
        int more = parts.size() - 1 - list.size();
        out.add(String.format(Locale.ROOT, "%s parts that don't visibly join the rest%s: %s%s. They are glued to the ship, but "
                        + "their shapes don't meet the blocks beside them, so each hangs in the air with a gap round it (a "
                        + "Grindstone under a top slab, a sail laid on top of a block, a fin over a slab boom). Join each part "
                        + "where its shape meets its neighbour's: a full block, a bottom slab under it, a top slab over it, or "
                        + "a part set into the side of a block (sails and wings join at mid-height of the block beside them)",
                model ? "it has" : "the build has", model ? "" : " (a note for the player)", String.join("; ", list),
                more > 0 ? "; and " + more + " more" : ""));
    }

    /** Whether a's shape and b's (b one step toward d) meet on the face between them. */
    private static boolean burner(BlockState s) {
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        return id.equals("aeronautics:adjustable_burner") || id.equals("aeronautics:steam_vent");
    }

    static boolean joined(List<AABB> a, List<AABB> b, Direction d) {
        if (a == null || b == null) return false;
        Direction.Axis ax = d.getAxis();
        boolean pos = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for (AABB x : a) {
            if (pos ? x.max(ax) < 1 - 1e-4 : x.min(ax) > 1e-4) continue;
            for (AABB y : b) {
                if (pos ? y.min(ax) > 1e-4 : y.max(ax) < 1 - 1e-4) continue;
                if (overlap(x, y, ax) > 1.0 / 256) return true;
            }
        }
        return false;
    }

    /** Area two boxes share across a face normal to ax. */
    private static double overlap(AABB a, AABB b, Direction.Axis ax) {
        double area = 1;
        for (Direction.Axis o : Direction.Axis.values()) {
            if (o == ax) continue;
            double lo = Math.max(a.min(o), b.min(o)), hi = Math.min(a.max(o), b.max(o));
            if (hi <= lo) return 0;
            area *= hi - lo;
        }
        return area;
    }

    /** ", a gap of 0.5 below it", naming the nearest neighbour face it fails to meet, or empty. */
    private static String gapTo(BlockPos at, Map<BlockPos, BlockState> blocks, Map<BlockPos, List<AABB>> shapes) {
        List<AABB> own = shapes.get(at);
        for (Direction d : Direction.values()) {
            List<AABB> other = shapes.get(at.relative(d));
            if (other == null) continue;
            Direction.Axis ax = d.getAxis();
            boolean pos = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            double mine = 0, theirs = 1;
            for (AABB x : own) mine = pos ? Math.max(mine, x.max(ax)) : Math.max(mine, 1 - x.min(ax));
            for (AABB y : other) theirs = Math.min(theirs, pos ? y.min(ax) : 1 - y.max(ax));
            double gap = (1 - mine) + theirs;
            if (gap > 0.01) return String.format(Locale.ROOT, " (%s gap %s it, to the %s)", gap >= 0.95 ? "a full-block"
                    : String.format(Locale.ROOT, "a %.2f-block", gap), word(d), blocks.get(at.relative(d)).getBlock().getName().getString());
        }
        return "";
    }

    private static String word(Direction d) {
        return switch (d) {
            case UP -> "above";
            case DOWN -> "below";
            default -> "on its " + d.getSerializedName() + " side";
        };
    }

    // ---------- see-through ----------

    /**
     * Looks along x (from the side) and along z (from the end): any patch of sky enclosed inside the hull's outline is
     * a see-through hole. The hull here is everything but lift and drag surfaces (sails, wings, fins), propellers,
     * rigging and railings (chains, fences, bars, ropes), whose gaps are meant; glass and panes count as hull (windows).
     */
    private static void seeThrough(Map<BlockPos, BlockState> blocks, Map<BlockPos, List<AABB>> shapes, List<String> out, boolean model) {
        Map<BlockPos, List<AABB>> hull = new HashMap<>();
        shapes.forEach((p, b) -> {
            if (hull(blocks.get(p)) && !rod(b)) hull.put(p, b);
        });
        if (hull.size() < 8) return;
        List<String> holes = new ArrayList<>();
        holes.addAll(view(hull, Direction.Axis.X, blocks));
        holes.addAll(view(hull, Direction.Axis.Z, blocks));
        if (holes.isEmpty()) return;
        out.add(String.format(Locale.ROOT, "%s hull can be seen through: %s. The sky shows through gaps inside its outline (open "
                        + "sides between slabs, holes between parts); close them with walls, full blocks, slabs, stairs or trapdoors "
                        + "(glass or panes where the crew should see out)%s", model ? "its" : "the build's", String.join("; ", holes),
                model ? "" : " (a note for the player)"));
    }

    /** A thin part that runs through its cell along one axis only (a shaft, a post): a strut, not a wall. */
    private static boolean rod(List<AABB> boxes) {
        double vol = 0;
        int spans = 0;
        for (AABB b : boxes) vol += b.getXsize() * b.getYsize() * b.getZsize();
        for (Direction.Axis a : Direction.Axis.values()) {
            double lo = 1, hi = 0;
            for (AABB b : boxes) {
                lo = Math.min(lo, b.min(a));
                hi = Math.max(hi, b.max(a));
            }
            if (lo < 1e-4 && hi > 1 - 1e-4) spans++;
        }
        return vol < 0.25 && spans <= 1;
    }

    private static boolean hull(BlockState s) {
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if (id.contains("sail") || id.contains("wing") || id.contains("propeller") || id.contains("rope") || id.contains("chain")
                || id.contains("windcatcher") || id.contains("yard") || id.contains("mast")) return false;
        if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.CLIMBABLE)) return false;
        // A gearbox on a drive line is machinery, not skin: where one ends a line that runs past a gap (a steering line
        // across the slot between a gondola's roof and its envelope), the gap is still open air, not a hole in a wall.
        return !id.equals("minecraft:iron_bars") && !id.equals("create:gearbox");
    }

    /** Enclosed holes seen looking along ax, described. */
    private static List<String> view(Map<BlockPos, List<AABB>> hull, Direction.Axis ax, Map<BlockPos, BlockState> blocks) {
        // The two axes of the picture: u across, v up (or the other level axis when looking down).
        Direction.Axis u = ax == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X, v = Direction.Axis.Y;
        int u0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v0 = Integer.MAX_VALUE, v1 = Integer.MIN_VALUE;
        for (BlockPos p : hull.keySet()) {
            u0 = Math.min(u0, coord(p, u));
            u1 = Math.max(u1, coord(p, u));
            v0 = Math.min(v0, coord(p, v));
            v1 = Math.max(v1, coord(p, v));
        }
        int w = (u1 - u0 + 3) * RES, h = (v1 - v0 + 3) * RES;
        boolean[][] fill = new boolean[w][h];
        for (Map.Entry<BlockPos, List<AABB>> e : hull.entrySet()) {
            int bu = (coord(e.getKey(), u) - u0 + 1) * RES, bv = (coord(e.getKey(), v) - v0 + 1) * RES;
            for (AABB b : e.getValue()) {
                int a0 = (int) Math.floor(b.min(u) * RES + 1e-6), a1 = (int) Math.ceil(b.max(u) * RES - 1e-6);
                int c0 = (int) Math.floor(b.min(v) * RES + 1e-6), c1 = (int) Math.ceil(b.max(v) * RES - 1e-6);
                for (int i = a0; i < a1; i++)
                    for (int j = c0; j < c1; j++) fill[bu + i][bv + j] = true;
            }
        }
        // Outside: everything empty reachable from the picture's border.
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        for (int i = 0; i < w; i++) {
            todo.add(new int[] {i, 0});
            todo.add(new int[] {i, h - 1});
        }
        for (int j = 0; j < h; j++) {
            todo.add(new int[] {0, j});
            todo.add(new int[] {w - 1, j});
        }
        flood(fill, seen, todo, null);
        List<String> found = new ArrayList<>();
        if (Boolean.getBoolean("creategalore.integrity.debug")) {
            StringBuilder sb = new StringBuilder("[integrity] view along " + ax + " (# hull, . hole, space outside), u from " + (u0 - 1) + ", v from " + (v0 - 1) + "\n");
            for (int j = h - 1; j >= 0; j -= 2) {
                for (int i = 0; i < w; i += 2) sb.append(fill[i][j] ? '#' : seen[i][j] ? ' ' : '.');
                sb.append('\n');
            }
            System.out.println(sb);
        }
        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++) {
                if (fill[i][j] || seen[i][j]) continue;
                List<int[]> hole = new ArrayList<>();
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[] {i, j});
                flood(fill, seen, q, hole);
                double area = hole.size() / (double) (RES * RES);
                if (area < HOLE) continue;
                double mu = 0, mv = 0;
                for (int[] c : hole) {
                    mu += c[0];
                    mv += c[1];
                }
                mu = mu / hole.size() / RES - 1 + u0;
                mv = mv / hole.size() / RES - 1 + v0;
                if (Boolean.getBoolean("creategalore.integrity.debug")) {
                    StringBuilder sb = new StringBuilder("[integrity] hole ray:");
                    for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
                        if (Math.abs(coord(e.getKey(), u) - (int) Math.floor(mu)) <= 2 && Math.abs(coord(e.getKey(), v) - (int) Math.floor(mv)) <= 1) sb.append(' ').append(xyz(e.getKey())).append('=').append(e.getValue());
                    System.out.println(sb);
                }
                found.add(String.format(Locale.ROOT, "from the %s, about %.0f blocks² at %s %.0f, y %.0f", ax == Direction.Axis.X ? "side"
                        : "front or back", area, u == Direction.Axis.Z ? "z" : "x", Math.floor(mu), Math.floor(mv)));
            }
        return found;
    }

    private static void flood(boolean[][] fill, boolean[][] seen, ArrayDeque<int[]> todo, List<int[]> into) {
        int w = fill.length, h = fill[0].length;
        while (!todo.isEmpty()) {
            int[] c = todo.poll();
            int i = c[0], j = c[1];
            if (i < 0 || j < 0 || i >= w || j >= h || fill[i][j] || seen[i][j]) continue;
            seen[i][j] = true;
            if (into != null) into.add(c);
            todo.add(new int[] {i + 1, j});
            todo.add(new int[] {i - 1, j});
            todo.add(new int[] {i, j + 1});
            todo.add(new int[] {i, j - 1});
        }
    }

    private static int coord(BlockPos p, Direction.Axis a) {
        return switch (a) {
            case X -> p.getX();
            case Y -> p.getY();
            case Z -> p.getZ();
        };
    }

    private static String xyz(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }
}

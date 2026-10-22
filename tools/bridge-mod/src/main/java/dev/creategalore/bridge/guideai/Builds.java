package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

/**
 * The book's way of finding and reading what players built. It only ever reads blocks a player placed
 * ({@link PlacedBlocks}), the fluids and blocks touching them, and blocks that were on the player's screen when they
 * asked; nothing else of the world, so it can't be used to look for ores or structures. Everything is limited to
 * the chunks the server simulates around the asking player, in their dimension, so builds near the other player
 * but far from this one are out of reach. This is meant for cooperative play: any player's blocks count.
 */
final class Builds {
    private static final int MAX_SCAN = 200_000, MAX_MATCHES = 60, MAX_BUILDS = 15, MAX_TRACE = 450, MAX_TOUCHING = 220,
            MAX_GOGGLES = 30, MAX_AREA_SIDE = 24, GAP = 2,
            FLUID_MARGIN = 16, MAX_FLUID = 600, MAX_FLUID_LINES = 160;

    private Builds() {
    }

    // ---------- tool definitions ----------

    static void definitions(JsonArray tools) {
        JsonObject find = new JsonObject();
        find.add("query", WorldTools.prop("string", "Part of a block's English name or id, such as 'water wheel', 'shaft' or "
                + "'create:mechanical_press'; an empty string lists the builds instead."));
        tools.add(WorldTools.tool("find_placed_blocks", "Searches the blocks players have placed in the chunks the server is "
                + "running around the asking player (their simulation distance, this dimension) and returns the matching ones "
                + "with exact x y z, block state and which build each belongs to (blocks within " + GAP + " of each other form "
                + "one build). Guess from the question which blocks the build must have and search for them. With an empty "
                + "query it lists every build nearby: size, bounds, distance and its main blocks. It never shows natural "
                + "blocks, so it can't find ores or structures.", find));
        JsonObject trace = new JsonObject();
        trace.add("x", WorldTools.prop("integer", "x of a placed block, from find_placed_blocks or get_view."));
        trace.add("y", WorldTools.prop("integer", "See x."));
        trace.add("z", WorldTools.prop("integer", "See x."));
        tools.add(WorldTools.tool("trace_build", "Starts at a placed block and follows the build outwards through every placed "
                + "block within " + GAP + " blocks of the last, until the build trails off. Returns every block with its x y z "
                + "and full state (facing, axis), Create speed (RPM; the sign is the turning direction), whether it is a "
                + "source, its kinetic network and that network's stress used of capacity, what Engineer's Goggles show for "
                + "machines, the results of Create's own rules run on its machines (for a fan: how far its air really "
                + "reaches, what each cell processes, and whether items on a belt stay in the air long enough; for a vehicle: "
                + "whether it floats upright or lifts, its top speed, turn rate, climb and ceiling, and what keeps it "
                + "from being of use), and the "
                + "fluids and other blocks touching the build. Use it to work out how the parts sit relative to each "
                + "other and why a machine does or doesn't work. Big builds are cut off and say where to continue.", trace));
        JsonObject area = new JsonObject();
        for (String k : new String[]{"x1", "y1", "z1", "x2", "y2", "z2"})
            area.add(k, WorldTools.prop("integer", k.endsWith("1") ? "One corner of the box." : "The opposite corner."));
        tools.add(WorldTools.tool("read_area", "Reads a box up to " + MAX_AREA_SIDE + " blocks a side, in the same detail as "
                + "trace_build: the placed blocks in it, the fluids and blocks touching them, and blocks the player had on "
                + "screen. For one part of a big build, or the space between two builds.", area));
    }

    // ---------- find_placed_blocks ----------

    static String find(ServerPlayer player, PlacedBlocks placed, String query) {
        ServerLevel level = player.serverLevel();
        int radius = simulation(player);
        BlockPos here = player.blockPosition();
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int[] matchingBuilds = {0};
        boolean any = section(out, level, here, placed.around(level, player.chunkPosition(), radius, MAX_SCAN), q, query, null, matchingBuilds,
                "Placed blocks within " + radius + " chunks of the player (they stand at " + Vision.xyz(here) + ")");
        for (Ships.Ship ship : Ships.near(player, radius)) {
            List<BlockPos> blocks = placed.inChunks(level, ship::holds, MAX_SCAN);
            if (blocks.isEmpty()) continue;
            any |= section(out, level, here, blocks, q, query, ship, matchingBuilds, String.format(Locale.ROOT,
                    "On the ship or vehicle \"%s\", now at %.0f %.0f %.0f (Sable keeps its blocks in coordinates of their own; they "
                            + "only matter relative to each other, and trace_build and read_area take them as they are)",
                    ship.name(), ship.at().x, ship.at().y, ship.at().z));
        }
        if (!any) return "No blocks placed by a player are in the " + (2 * radius + 1) + "x" + (2 * radius + 1)
                + " chunks the server runs around " + player.getGameProfile().getName() + ". Blocks placed before this book "
                + "update aren't known unless an operator adds them with /fieldguide builds add.";
        if (matchingBuilds[0] > 1) out.append("The matches are in ").append(matchingBuilds[0]).append(" different builds. If the "
                + "question could mean more than one, ask which, naming where each is and what sets it apart, before answering.\n");
        return out.toString().strip();
    }

    /** One part of the answer: the builds among these blocks, or the blocks matching the query. False if none. */
    private static boolean section(StringBuilder out, ServerLevel level, BlockPos here, List<BlockPos> all, String q, String query,
                                   Ships.Ship ship, int[] matchingBuilds, String heading) {
        if (all.isEmpty()) return false;
        Long2IntOpenHashMap build = clusters(all);
        int builds = 0;
        for (int id : build.values()) builds = Math.max(builds, id + 1);
        out.append(heading).append(": ").append(all.size()).append(" blocks in ").append(builds).append(builds == 1 ? " build" : " builds").append(".\n");
        BlockPos from = ship == null ? here : null;
        List<Summary> summaries = new ArrayList<>();
        for (int i = 0; i < builds; i++) summaries.add(new Summary(i + 1));
        for (BlockPos p : all) summaries.get(build.get(p.asLong())).add(p, level.getBlockState(p).getBlock(), from);

        if (q.isEmpty()) {
            summaries.sort(Comparator.comparingDouble(s -> s.nearest));
            for (int i = 0; i < Math.min(MAX_BUILDS, summaries.size()); i++) out.append(summaries.get(i).describe(from)).append('\n');
            if (summaries.size() > MAX_BUILDS) out.append("...and ").append(summaries.size() - MAX_BUILDS).append(" smaller or further builds.\n");
            return true;
        }
        List<BlockPos> matches = new ArrayList<>();
        for (BlockPos p : all) {
            Block b = level.getBlockState(p).getBlock();
            if (BuiltInRegistries.BLOCK.getKey(b).toString().contains(q.replace(' ', '_'))
                    || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) matches.add(p);
        }
        if (matches.isEmpty()) {
            out.append("None of them match \"").append(query.strip()).append("\".\n");
            return true;
        }
        if (from != null) matches.sort(Comparator.comparingDouble(p -> p.distSqr(here)));
        Map<Integer, List<BlockPos>> byBuild = new LinkedHashMap<>();
        for (BlockPos p : matches) byBuild.computeIfAbsent(build.get(p.asLong()) + 1, k -> new ArrayList<>()).add(p);
        matchingBuilds[0] += byBuild.size();
        out.append(matches.size()).append(" match \"").append(query.strip()).append("\", in ").append(byBuild.size())
                .append(byBuild.size() == 1 ? " build" : " different builds").append(":\n");
        int shown = 0;
        for (var e : byBuild.entrySet()) {
            out.append(summaries.get(e.getKey() - 1).describe(from)).append('\n');
            for (BlockPos p : e.getValue()) {
                if (shown >= MAX_MATCHES) break;
                out.append("    ").append(Vision.xyz(p)).append(' ').append(BlockStateParser.serialize(level.getBlockState(p))).append('\n');
                shown++;
            }
            if (shown >= MAX_MATCHES) {
                out.append("...more matches left out; search a narrower name.\n");
                break;
            }
        }
        return true;
    }

    private static final class Summary {
        final int id;
        int count, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE,
                maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        double nearest = Double.MAX_VALUE;
        BlockPos nearestPos;
        final Map<Block, Integer> blocks = new HashMap<>();

        Summary(int id) {
            this.id = id;
        }

        void add(BlockPos p, Block b, BlockPos here) {
            count++;
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
            double d = here == null ? 0 : p.distSqr(here);
            if (nearestPos == null || d < nearest) {
                nearest = d;
                nearestPos = p;
            }
            blocks.merge(b, 1, Integer::sum);
        }

        String describe(BlockPos here) {
            List<Map.Entry<Block, Integer>> kinds = new ArrayList<>(blocks.entrySet());
            kinds.sort(Map.Entry.<Block, Integer>comparingByValue().reversed());
            List<String> top = new ArrayList<>();
            for (int i = 0; i < Math.min(10, kinds.size()); i++)
                top.add(kinds.get(i).getValue() + " " + kinds.get(i).getKey().getName().getString());
            return "- Build " + id + ": " + count + " blocks, x " + minX + ".." + maxX + ", y " + minY + ".." + maxY + ", z " + minZ
                    + ".." + maxZ + (here == null ? ", starting at " + Vision.xyz(nearestPos) : ", nearest part "
                    + TouchedStorage.offset(here, nearestPos) + " (" + Vision.xyz(nearestPos) + ")") + "; " + String.join(", ", top) + (kinds.size() > 10 ? ", and " + (kinds.size() - 10) + " other kinds" : "");
        }
    }

    /** Groups blocks into builds: blocks within {@link #GAP} of each other belong together. Returns position -> build. */
    private static Long2IntOpenHashMap clusters(List<BlockPos> all) {
        LongOpenHashSet set = new LongOpenHashSet(all.size());
        for (BlockPos p : all) set.add(p.asLong());
        Long2IntOpenHashMap build = new Long2IntOpenHashMap(all.size());
        build.defaultReturnValue(-1);
        int next = 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : all) {
            if (build.get(start.asLong()) >= 0) continue;
            int id = next++;
            build.put(start.asLong(), id);
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                for (BlockPos n : near(p)) {
                    long k = n.asLong();
                    if (set.contains(k) && build.get(k) < 0) {
                        build.put(k, id);
                        queue.add(n.immutable());
                    }
                }
            }
        }
        return build;
    }

    private static Iterable<BlockPos> near(BlockPos p) {
        return BlockPos.betweenClosed(p.offset(-GAP, -GAP, -GAP), p.offset(GAP, GAP, GAP));
    }

    // ---------- trace_build and read_area ----------

    static String trace(ServerPlayer player, PlacedBlocks placed, Vision.Snapshot view, BlockPos start) {
        ServerLevel level = player.serverLevel();
        int radius = simulation(player);
        if (!inRange(player, start, radius)) return "That block is outside the chunks the server runs around the player.";
        BlockPos seed = start;
        if (!placed.isPlaced(level, start)) {
            // A block from their screen that nobody placed: start at a placed block touching it, if there is one.
            seed = null;
            if (view != null && view.saw(level, start))
                for (BlockPos n : near(start)) if (placed.isPlaced(level, n)) {
                    seed = n.immutable();
                    break;
                }
            if (seed == null) return "No player placed the block at " + Vision.xyz(start) + " or anything right next to it, so it "
                    + "isn't part of a known build. Use find_placed_blocks to locate the build. (Blocks placed before this book "
                    + "update are only known if an operator added them with /fieldguide builds add.)";
        }
        Ships.Ship ship = Ships.holding(player, radius, seed);
        ChunkPos centre = player.chunkPosition();
        java.util.function.Predicate<BlockPos> reachable = ship != null ? ship::holds
                : p -> Math.abs((p.getX() >> 4) - centre.x) <= radius && Math.abs((p.getZ() >> 4) - centre.z) <= radius && level.isLoaded(p);
        List<BlockPos> build = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(seed.asLong());
        queue.add(seed);
        int beyond = 0;
        BlockPos continueAt = null;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (build.size() >= MAX_TRACE) {
                beyond++;
                if (continueAt == null) continueAt = p;
            } else {
                build.add(p);
            }
            if (visited.size() > MAX_TRACE * 8) continue; // stop growing; what is queued is counted as more
            for (BlockPos n : near(p)) {
                long k = n.asLong();
                if (visited.contains(k) || !reachable.test(n) || !placed.isPlaced(level, n)) continue;
                visited.add(k);
                queue.add(n.immutable());
            }
        }
        StringBuilder out = new StringBuilder("Build traced from " + Vision.xyz(seed) + (seed.equals(start) ? "" : " (the placed block next to "
                + Vision.xyz(start) + ")") + ": ");
        out.append(describe(level, placed, build, null));
        if (beyond > 0) out.append("\nThe build goes on: at least ").append(beyond).append(" more connected placed blocks were left out. "
                + "Continue with trace_build from ").append(Vision.xyz(continueAt)).append(", or read_area for one part.");
        return out.toString();
    }

    static String area(ServerPlayer player, PlacedBlocks placed, Vision.Snapshot view, BlockPos a, BlockPos b) {
        ServerLevel level = player.serverLevel();
        int radius = simulation(player);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        if (max.getX() - min.getX() >= MAX_AREA_SIDE || max.getY() - min.getY() >= MAX_AREA_SIDE || max.getZ() - min.getZ() >= MAX_AREA_SIDE)
            return "The box can be at most " + MAX_AREA_SIDE + " blocks a side.";
        if (!inRange(player, min, radius) || !inRange(player, max, radius))
            return "Part of that box is outside the chunks the server runs around the player.";
        List<BlockPos> inside = new ArrayList<>();
        List<BlockPos> seen = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (placed.isPlaced(level, p)) inside.add(p.immutable());
            else if (view != null && view.saw(level, p) && !level.getBlockState(p).isAir()) seen.add(p.immutable());
        }
        StringBuilder out = new StringBuilder("Box x " + min.getX() + ".." + max.getX() + ", y " + min.getY() + ".." + max.getY()
                + ", z " + min.getZ() + ".." + max.getZ() + ": ");
        if (inside.isEmpty() && seen.isEmpty()) return out.append("no placed blocks, and nothing in it was on the player's screen.").toString();
        out.append(describe(level, placed, inside, net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(min, max)));
        if (!seen.isEmpty()) {
            out.append("\nOther blocks in the box the player had on screen:\n");
            appendRuns(out, level, seen, MAX_TOUCHING, Map.of());
        }
        return out.toString();
    }

    /** Blocks copied out of the world for a model (build_model's from_world), keyed by their place in the box; or why not. */
    record Copy(BlockPos min, Map<BlockPos, BlockState> blocks, String problem) {
    }

    /**
     * The blocks of a box that the book may read, as they stand: the placed ones, the water and lava in the box, the
     * solid blocks touching a placed one (what it stands on or sits in) and what was on the player's screen. Everything
     * else stays air, so a copy shows no more of the world than trace_build and read_area do.
     */
    static Copy copy(ServerPlayer player, PlacedBlocks placed, Vision.Snapshot view, BlockPos a, BlockPos b, int maxSide) {
        ServerLevel level = player.serverLevel();
        int radius = simulation(player);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        if (max.getX() - min.getX() >= maxSide || max.getY() - min.getY() >= maxSide || max.getZ() - min.getZ() >= maxSide)
            return new Copy(min, Map.of(), "the from_world box can be at most " + maxSide + " blocks a side");
        if (!inRange(player, min, radius) || !inRange(player, max, radius))
            return new Copy(min, Map.of(), "part of the from_world box is outside the chunks the server runs around the player");
        LongOpenHashSet mine = new LongOpenHashSet();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) if (placed.isPlaced(level, p)) mine.add(p.asLong());
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            boolean take = mine.contains(p.asLong()) || !level.getFluidState(p).isEmpty() || (view != null && view.saw(level, p));
            if (!take && !(s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock))
                for (Direction d : Direction.values())
                    if (mine.contains(p.relative(d).asLong())) {
                        take = true;
                        break;
                    }
            if (take) out.put(p.subtract(min), s);
        }
        if (mine.isEmpty()) return new Copy(min, Map.of(), "no player placed any block in the from_world box " + min.getX() + " "
                + min.getY() + " " + min.getZ() + " to " + max.getX() + " " + max.getY() + " " + max.getZ()
                + " (find_placed_blocks or trace_build give a build's bounds)");
        return new Copy(min, out, null);
    }

    /** The placed blocks in full, their networks, goggle readouts, and the fluids and blocks touching them. */
    private static String describe(ServerLevel level, PlacedBlocks placed, List<BlockPos> build, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        StringBuilder out = new StringBuilder();
        if (build.isEmpty()) return "no placed blocks.";
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        LongOpenHashSet set = new LongOpenHashSet();
        for (BlockPos p : build) {
            set.add(p.asLong());
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        out.append(build.size()).append(" placed blocks, x ").append(minX).append("..").append(maxX).append(", y ").append(minY)
                .append("..").append(maxY).append(", z ").append(minZ).append("..").append(maxZ).append(".\n");

        // Kinetic networks get short numbers in the order they turn up.
        Map<Long, Integer> networks = new LinkedHashMap<>();
        Map<Long, KineticNetwork> networkObjects = new HashMap<>();
        Map<Long, String> extra = new HashMap<>();
        List<BlockPos> goggled = new ArrayList<>();
        for (BlockPos p : build) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof KineticBlockEntity k) {
                StringBuilder s = new StringBuilder(String.format(Locale.ROOT, " %s RPM", rpm(k.getSpeed())));
                if (k.isSource()) s.append(String.format(Locale.ROOT, ", source making %s", rpm(k.getGeneratedSpeed())));
                if (k.getSpeed() == 0 && k.getTheoreticalSpeed() != 0) s.append(String.format(Locale.ROOT, ", would turn at %s but is stopped", rpm(k.getTheoreticalSpeed())));
                if (!k.hasNetwork()) s.append(", on no network (nothing turns it)");
                if (k.hasNetwork()) {
                    int n = networks.computeIfAbsent(k.network, id -> networks.size() + 1);
                    networkObjects.computeIfAbsent(k.network, id -> k.getOrCreateNetwork());
                    s.append(", net ").append(n);
                }
                extra.put(p.asLong(), s.toString());
            }
            if (be instanceof IHaveGoggleInformation && !(be instanceof KineticBlockEntity && !hasMoreThanKinetics(be))) goggled.add(p);
        }
        out.append("Placed blocks (x y z state; a run like 3..7 64 10 is the same block along x):\n");
        appendRuns(out, level, build, MAX_TRACE, extra);
        if (!networks.isEmpty()) {
            out.append("Kinetic networks (stress in stress units, su):\n");
            networks.forEach((id, n) -> {
                KineticNetwork net = networkObjects.get(id);
                float stress = net.calculateStress(), capacity = net.calculateCapacity();
                out.append("- net ").append(n).append(": ").append(net.sources.size()).append(net.sources.size() == 1 ? " source, " : " sources, ")
                        .append(net.members.size()).append(" members, ").append(String.format(Locale.ROOT, "%.0f of %.0f su used", stress, capacity))
                        .append(stress > capacity ? ", OVERSTRESSED (everything on it is stopped)" : "").append('\n');
            });
        }
        StringBuilder goggleText = new StringBuilder();
        int shown = 0;
        for (BlockPos p : goggled) {
            List<String> lines = goggles(level.getBlockEntity(p));
            if (lines.isEmpty()) continue;
            if (shown++ >= MAX_GOGGLES) {
                goggleText.append("  ...more machines left out\n");
                break;
            }
            goggleText.append("  ").append(Vision.xyz(p)).append(' ').append(level.getBlockState(p).getBlock().getName().getString())
                    .append(": ").append(String.join("; ", lines)).append('\n');
        }
        if (!goggleText.isEmpty()) out.append("Engineer's Goggles show:\n").append(goggleText);
        out.append(MachineCheck.check(level, build));

        // Everything solid touching the build that isn't part of it: the ground, a wall it sits in.
        LongOpenHashSet touching = new LongOpenHashSet();
        List<BlockPos> touchingList = new ArrayList<>();
        int outside = 0;
        ArrayDeque<BlockPos> wetQueue = new ArrayDeque<>();
        LongOpenHashSet wet = new LongOpenHashSet();
        for (BlockPos p : build) {
            if (!level.getFluidState(p).isEmpty() && wet.add(p.asLong())) wetQueue.add(p); // waterlogged
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                long k = n.asLong();
                if (set.contains(k) || touching.contains(k) || !level.isLoaded(n)) continue;
                if (placed.isPlaced(level, n)) {
                    outside++; // placed, but not part of what is being read (read_area's box ends there)
                    continue;
                }
                BlockState s = level.getBlockState(n);
                if (!level.getFluidState(n).isEmpty() && wet.add(k)) wetQueue.add(n);
                if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
                touching.add(k);
                touchingList.add(n);
            }
        }
        if (!touchingList.isEmpty()) {
            out.append("Solid blocks touching the build, not placed by a player:\n");
            appendRuns(out, level, touchingList, MAX_TOUCHING, Map.of());
        }
        if (outside > 0) out.append(outside).append(" more placed blocks touch it from outside what was read.\n");

        // The water and lava the build touches, followed through the fluid it is connected to (a channel, a fall),
        // within a margin around the build: wheels and pumps care which way it flows and from where.
        BlockPos lo = new BlockPos(minX - FLUID_MARGIN, minY - FLUID_MARGIN, minZ - FLUID_MARGIN);
        BlockPos hi = new BlockPos(maxX + FLUID_MARGIN, maxY + FLUID_MARGIN, maxZ + FLUID_MARGIN);
        for (BlockPos p : placed.pouredIn(level, lo, hi, 200)) if (wet.add(p.asLong())) wetQueue.add(p);
        List<BlockPos> fluidCells = new ArrayList<>();
        while (!wetQueue.isEmpty() && fluidCells.size() < MAX_FLUID) {
            BlockPos p = wetQueue.poll();
            fluidCells.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < lo.getX() || n.getY() < lo.getY() || n.getZ() < lo.getZ() || n.getX() > hi.getX() || n.getY() > hi.getY()
                        || n.getZ() > hi.getZ() || !level.isLoaded(n) || level.getFluidState(n).isEmpty() || !wet.add(n.asLong())) continue;
                wetQueue.add(n);
            }
        }
        if (!fluidCells.isEmpty()) {
            Map<Long, String> poured = new HashMap<>();
            for (BlockPos p : fluidCells) if (placed.isPoured(level, p)) poured.put(p.asLong(), ", poured by a player");
            out.append("Water and lava at and around the build, connected to it (flow is the way it runs; waterlogged placed blocks count):\n");
            // Cells at the build (touching it, corners included) and poured sources in full; the rest of a spread summed up.
            List<BlockPos> near = new ArrayList<>(), far = new ArrayList<>();
            for (BlockPos p : fluidCells) {
                boolean close = poured.containsKey(p.asLong()) || level.getFluidState(p).isSource() || (box != null && box.isInside(p));
                for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1)))
                    if (close || set.contains(n.asLong())) {
                        close = true;
                        break;
                    }
                (close ? near : far).add(p);
            }
            appendFluidRuns(out, level, near, MAX_FLUID_LINES, poured);
            if (!far.isEmpty()) {
                int fx0 = Integer.MAX_VALUE, fx1 = Integer.MIN_VALUE, fy0 = Integer.MAX_VALUE, fy1 = Integer.MIN_VALUE, fz0 = Integer.MAX_VALUE, fz1 = Integer.MIN_VALUE;
                for (BlockPos p : far) {
                    fx0 = Math.min(fx0, p.getX());
                    fx1 = Math.max(fx1, p.getX());
                    fy0 = Math.min(fy0, p.getY());
                    fy1 = Math.max(fy1, p.getY());
                    fz0 = Math.min(fz0, p.getZ());
                    fz1 = Math.max(fz1, p.getZ());
                }
                out.append("  and ").append(far.size()).append(" more flowing cells further from the build, spread over x ").append(fx0)
                        .append("..").append(fx1).append(", y ").append(fy0).append("..").append(fy1).append(", z ").append(fz0).append("..")
                        .append(fz1).append(" (read_area over them lists them cell by cell)\n");
            }
            if (!wetQueue.isEmpty()) out.append("  ...the water goes on further\n");
        } else {
            out.append("No water or lava touches the build.\n");
        }
        out.append("Cells next to the build not listed above are air.");
        return out.toString().strip();
    }

    private static void appendFluidRuns(StringBuilder out, ServerLevel level, List<BlockPos> cells, int maxLines, Map<Long, String> extra) {
        cells.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
        int lines = 0, i = 0;
        while (i < cells.size()) {
            BlockPos p = cells.get(i);
            String what = fluid(level, p) + extra.getOrDefault(p.asLong(), "");
            int j = i + 1;
            while (j < cells.size()) {
                BlockPos q = cells.get(j);
                if (q.getY() != p.getY() || q.getZ() != p.getZ() || q.getX() != p.getX() + (j - i)
                        || !(fluid(level, q) + extra.getOrDefault(q.asLong(), "")).equals(what)) break;
                j++;
            }
            if (lines++ >= maxLines) {
                out.append("  ...").append(cells.size() - i).append(" more cells left out\n");
                return;
            }
            BlockPos last = cells.get(j - 1);
            out.append("  ").append(j - i > 1 ? p.getX() + ".." + last.getX() : String.valueOf(p.getX())).append(' ').append(p.getY())
                    .append(' ').append(p.getZ()).append(' ').append(what).append('\n');
            i = j;
        }
    }

    /** Lines of "x y z state extra", with runs along x of the same state and extra folded into one line. */
    private static void appendRuns(StringBuilder out, ServerLevel level, List<BlockPos> blocks, int maxLines, Map<Long, String> extra) {
        List<BlockPos> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
        int lines = 0, i = 0;
        while (i < sorted.size()) {
            BlockPos p = sorted.get(i);
            String what = what(level, p) + extra.getOrDefault(p.asLong(), "");
            int j = i + 1;
            while (j < sorted.size()) {
                BlockPos q = sorted.get(j);
                if (q.getY() != p.getY() || q.getZ() != p.getZ() || q.getX() != p.getX() + (j - i)
                        || !(what(level, q) + extra.getOrDefault(q.asLong(), "")).equals(what)) break;
                j++;
            }
            if (lines++ >= maxLines) {
                out.append("  ...").append(sorted.size() - i).append(" more left out\n");
                return;
            }
            BlockPos last = sorted.get(j - 1);
            out.append("  ").append(j - i > 1 ? p.getX() + ".." + last.getX() : String.valueOf(p.getX())).append(' ').append(p.getY())
                    .append(' ').append(p.getZ()).append(' ').append(what).append('\n');
            i = j;
        }
    }

    private static String what(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            FluidState fs = level.getFluidState(p);
            return fs.isEmpty() ? "air" : fluid(level, p);
        }
        String state = BlockStateParser.serialize(s);
        if (s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) state += " (waterlogged)";
        return state;
    }

    /** "water source" or "water flowing east, level 5, falling", and what holds it if it sits in a block. */
    private static String fluid(ServerLevel level, BlockPos p) {
        FluidState fs = level.getFluidState(p);
        String name = BuiltInRegistries.FLUID.getKey(fs.getType()).getPath().replace("flowing_", "");
        BlockState s = level.getBlockState(p);
        String in = s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || s.isAir() ? ""
                : " in " + BuiltInRegistries.BLOCK.getKey(s.getBlock());
        net.minecraft.world.phys.Vec3 flow = fs.getFlow(level, p);
        String dir = "";
        if (flow.horizontalDistanceSqr() > 1e-4) {
            dir = " " + (Math.abs(flow.x) > Math.abs(flow.z) ? (flow.x > 0 ? "east" : "west") : (flow.z > 0 ? "south" : "north"));
            if (Math.abs(flow.x) > 0.01 && Math.abs(flow.z) > 0.01 && Math.abs(Math.abs(flow.x) - Math.abs(flow.z)) < 0.2)
                dir = " " + (flow.z > 0 ? "south" : "north") + "-" + (flow.x > 0 ? "east" : "west");
        }
        boolean falling = fs.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING)
                && fs.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING);
        if (fs.isSource()) return name + " source" + in + (dir.isEmpty() ? "" : ", pushing" + dir);
        return name + " flowing" + (dir.isEmpty() ? "" : dir) + ", level " + fs.getAmount() + (falling ? ", falling" : "") + in;
    }

    private static String rpm(float speed) {
        return Math.abs(speed - Math.round(speed)) < 0.05 ? String.valueOf(Math.round(speed)) : String.format(Locale.ROOT, "%.1f", speed);
    }

    /** Kinetic blocks whose goggle text says more than speed and stress (a tank, a basin, a deployer's item...). */
    private static boolean hasMoreThanKinetics(BlockEntity be) {
        return !be.getClass().getName().startsWith("com.simibubi.create.content.kinetics.simpleRelays")
                && !be.getClass().getName().startsWith("com.simibubi.create.content.kinetics.transmission");
    }

    private static List<String> goggles(BlockEntity be) {
        List<String> out = new ArrayList<>();
        if (!(be instanceof IHaveGoggleInformation g)) return out;
        List<Component> lines = new ArrayList<>();
        try {
            g.addToGoggleTooltip(lines, false);
        } catch (RuntimeException | LinkageError ignored) {
            // Some add-ons only fill this on the client.
        }
        for (Component c : lines) {
            String s = c.getString().strip();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    // ---------- range ----------

    static int simulation(ServerPlayer player) {
        return Math.max(2, player.server.getPlayerList().getSimulationDistance());
    }

    /** In the chunks the server runs around the player, or on a ship near them. */
    static boolean inRange(ServerPlayer player, BlockPos pos, int radius) {
        ChunkPos c = player.chunkPosition();
        if (Math.abs((pos.getX() >> 4) - c.x) <= radius && Math.abs((pos.getZ() >> 4) - c.z) <= radius) return player.serverLevel().isLoaded(pos);
        return Ships.holding(player, radius, pos) != null;
    }
}

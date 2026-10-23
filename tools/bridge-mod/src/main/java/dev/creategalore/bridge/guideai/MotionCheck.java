package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.LinearChassisBlock;
import com.simibubi.create.content.contraptions.chassis.RadialChassisBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlock;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlock;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The moving parts of a model: what a bearing turns, a piston pushes, a pulley lowers or a gantry carriage carries,
 * found with Create's own rules for which blocks come along (BlockMovementChecks: blocks that hang on others, Super
 * Glue areas, chassis, the line a piston pushes), then swept through its motion in the model to see what it runs
 * into, how fast it goes at its speed and what stress it puts on its network. The client gets each moving part's
 * cells and motion so the book can play it (client/ModelView).
 */
final class MotionCheck {
    static final int MAX_MOVERS = 4;
    private static final TagKey<Block> SAILS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "windmill_sails"));
    /** A chassis reaches this far until a wrench sets it (half the longest range). */
    private static final int CHASSIS_RANGE_FALLBACK = 8;

    private MotionCheck() {
    }

    static JsonObject definition() {
        JsonObject box = new JsonObject();
        box.addProperty("type", "object");
        JsonObject bp = new JsonObject();
        bp.add("x", WorldTools.prop("integer", "One corner cell of a Super Glue area."));
        bp.add("y", WorldTools.prop("integer", "See x."));
        bp.add("z", WorldTools.prop("integer", "See x."));
        bp.add("x2", WorldTools.prop("integer", "The opposite corner cell (the same as x, y, z for one block)."));
        bp.add("y2", WorldTools.prop("integer", "See x2."));
        bp.add("z2", WorldTools.prop("integer", "See x2."));
        box.add("properties", bp);
        JsonArray boxRequired = new JsonArray();
        bp.keySet().forEach(boxRequired::add);
        box.add("required", boxRequired);
        box.addProperty("additionalProperties", false);

        JsonObject mover = new JsonObject();
        mover.addProperty("type", "object");
        JsonObject mp = new JsonObject();
        mp.add("x", WorldTools.prop("integer", "The cell of the block that moves the part: a Mechanical, Windmill or Clockwork "
                + "Bearing, a Mechanical or Sticky Mechanical Piston, a Rope or Elevator Pulley, or a Gantry Carriage."));
        mp.add("y", WorldTools.prop("integer", "See x."));
        mp.add("z", WorldTools.prop("integer", "See x."));
        JsonObject glue = new JsonObject();
        glue.addProperty("type", "array");
        glue.addProperty("description", "The Super Glue areas the player puts on, as boxes of cells: two blocks side by side "
                + "inside one area stick together. Blocks that hang on others (torches, doors, ladders, carpets, sails on "
                + "frames) and blocks on chassis faces come along without glue; everything else needs it. Often one box "
                + "round the whole moving part; every block inside a box that touches the part comes along, so keep boxes off "
                + "what stays put (a roof, a wall, the floor).");
        glue.add("items", box);
        mp.add("glue", glue);
        mp.add("travel", WorldTools.prop("integer", "How far it moves when it runs: for a bearing the degrees it turns (360 "
                + "for a windmill, turntable or anything that keeps spinning, 90 for a door or drawbridge driven a quarter "
                + "turn); for a piston or gantry the blocks it travels, for a pulley how far it lowers its load, 0 for as far as it can go."));
        mp.add("rpm", WorldTools.prop("integer", "The speed the player drives it at in RPM, 0 to take what the model's own "
                + "source gives it."));
        mover.add("properties", mp);
        JsonArray required = new JsonArray();
        mp.keySet().forEach(required::add);
        mover.add("required", required);
        mover.addProperty("additionalProperties", false);

        JsonObject moving = new JsonObject();
        moving.addProperty("type", "array");
        moving.addProperty("description", "Usually empty. The parts of the build that move as Create contraptions, at most "
                + MAX_MOVERS + ": a windmill, a turning door, drawbridge or turntable, a piston-driven wall, a lift, a "
                + "gantry crane. Each names the block that moves it and the glue the player puts on; the book plays the "
                + "motion on the model, and you get back what moves with it, what it runs into, its speed and stress. A "
                + "windmill turns with at least " + minimumSails() + " Windcatchers (aeronauticswinds:windcatcher_canvas) on it. "
                + "Write pistons retracted with their poles behind them, and a pulley's load where it starts: hanging under it, or resting lower down, straight below it.");
        moving.add("items", mover);
        return moving;
    }

    /** One moving part as the client plays it, plus its numbers for the model. */
    record Result(BuildCheck.Report report, JsonArray moving, Set<BlockPos> riding, Map<BlockPos, BlockState> moved) {
    }

    private enum Kind { BEARING, PISTON, PULLEY, GANTRY }

    private record Glue(BlockPos min, BlockPos max) {
        boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getY() >= min.getY() && p.getZ() >= min.getZ() && p.getX() <= max.getX()
                    && p.getY() <= max.getY() && p.getZ() <= max.getZ();
        }
    }

    /**
     * Works out the model's moving parts, or adds what is wrong with the entries to {@code problems} and returns null.
     * {@code input} is the build_model call's moving array.
     */
    static Result check(Map<BlockPos, BlockState> cells, JsonArray input, ServerLevel level, int sx, int sy, int sz,
                        List<String> problems) {
        if (input.isEmpty()) return null;
        if (input.size() > MAX_MOVERS) {
            problems.add("moving takes at most " + MAX_MOVERS + " parts");
            return null;
        }
        if (level == null) return null;
        SchematicLevel world = new SchematicLevel(level);
        cells.forEach((pos, state) -> world.setBlock(pos, state, 0));
        StringBuilder out = new StringBuilder("How the moving parts move" + BuildCheck.FOR_YOU + ":\n");
        List<String> faults = new ArrayList<>();
        JsonArray moving = new JsonArray();
        Set<BlockPos> riding = new LinkedHashSet<>();
        Map<BlockPos, BlockState> moved = new HashMap<>(cells);
        List<Part> parts = new ArrayList<>();
        int before = problems.size();
        for (int i = 0; i < input.size(); i++) {
            if (!input.get(i).isJsonObject()) {
                problems.add("moving entry " + i + " is not an object");
                continue;
            }
            Part part = part(i, input.get(i).getAsJsonObject(), cells, world, sx, sy, sz, problems);
            if (part != null) parts.add(part);
        }
        if (problems.size() > before) return null;
        // Speeds come from the parts that stay put: the blocks that ride along leave the network while they move.
        Map<BlockPos, BlockState> fixed = new HashMap<>(cells);
        for (Part p : parts) p.blocks.forEach(fixed::remove);
        List<String> assumed = new ArrayList<>();
        Map<BlockPos, Float> rpm = BuildCheck.rpm(fixed, level, assumed);
        for (Part p : parts) {
            riding.addAll(p.blocks);
            sweep(p, cells, world, rpm, out, faults);
            moving.add(p.json(sx, sy, sz));
            p.moveEnd(moved, cells);
        }
        stress(parts, fixed, level, rpm, out, faults);
        if (faults.isEmpty()) out.append("- Nothing stops these parts moving as described.\n");
        else for (String f : faults) out.append("- PROBLEM: ").append(f).append(".\n");
        return new Result(new BuildCheck.Report(out.toString().trim(), faults), moving, riding, moved);
    }

    /** One moving part: its mover, the blocks that come along (with poles, carriage) and how it moves. */
    private static final class Part {
        final int index;
        final Kind kind;
        final BlockPos mover;
        final BlockState state;
        final List<Glue> glue;
        final Set<BlockPos> blocks = new LinkedHashSet<>();
        /** Blocks that come along but pass through the mover's own cell or the shaft it rides (piston poles): no sweep. */
        final Set<BlockPos> passing = new LinkedHashSet<>();
        /** The Windcatchers a windmill carries. */
        int sails;
        Direction.Axis axis;
        Direction step;
        int travel, asked, rpmAsked;
        float degrees;
        float rpm;
        float seconds;
        String speedText = "";

        Part(int index, Kind kind, BlockPos mover, BlockState state, List<Glue> glue) {
            this.index = index;
            this.kind = kind;
            this.mover = mover;
            this.state = state;
            this.glue = glue;
        }

        String name() {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + mover.getX() + "," + mover.getY() + "," + mover.getZ();
        }

        JsonObject json(int sx, int sy, int sz) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", kind == Kind.BEARING ? "turn" : "slide");
            if (kind == Kind.BEARING) {
                o.addProperty("axis", axis.getName());
                JsonArray pivot = new JsonArray();
                pivot.add(mover.getX());
                pivot.add(mover.getY());
                pivot.add(mover.getZ());
                o.add("pivot", pivot);
                o.addProperty("degrees", degrees);
            } else {
                JsonArray s = new JsonArray();
                s.add(step.getStepX());
                s.add(step.getStepY());
                s.add(step.getStepZ());
                o.add("step", s);
                o.addProperty("blocks", travel);
                if (kind == Kind.PULLEY) {
                    // The rope runs from the pulley down to the load; the book draws it as the load moves.
                    JsonArray rope = new JsonArray();
                    rope.add(mover.getX());
                    rope.add(mover.getY());
                    rope.add(mover.getZ());
                    o.add("rope", rope);
                }
            }
            o.addProperty("seconds", Math.round(seconds * 100) / 100f);
            JsonArray runs = new JsonArray();
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    for (int x = 0; x < sx; x++) {
                        if (!blocks.contains(new BlockPos(x, y, z))) continue;
                        int end = x;
                        while (end + 1 < sx && blocks.contains(new BlockPos(end + 1, y, z))) end++;
                        JsonArray r = new JsonArray();
                        for (int v : new int[] {x, y, z, end, y, z}) r.add(v);
                        runs.add(r);
                        x = end;
                    }
            o.add("cells", runs);
            JsonArray boxes = new JsonArray();
            for (Glue g : glue) {
                JsonArray b = new JsonArray();
                for (int v : new int[] {g.min.getX(), g.min.getY(), g.min.getZ(), g.max.getX(), g.max.getY(), g.max.getZ()}) b.add(v);
                boxes.add(b);
            }
            o.add("glue", boxes);
            return o;
        }

        /** The part at the end of its motion (a bearing a quarter turn on, or its whole turn if less), for draft pictures. */
        void moveEnd(Map<BlockPos, BlockState> moved, Map<BlockPos, BlockState> cells) {
            Map<BlockPos, BlockState> placed = new HashMap<>();
            for (BlockPos p : blocks) moved.remove(p);
            if (kind == Kind.BEARING) {
                int quarter = Math.abs(degrees) >= 45 ? 90 : 0;
                if (quarter == 0) {
                    for (BlockPos p : blocks) placed.put(p, cells.get(p));
                } else {
                    float turn = Math.signum(degrees) * quarter;
                    StructureTransform t = new StructureTransform(mover, axis == Direction.Axis.X ? turn : 0,
                            axis == Direction.Axis.Y ? turn : 0, axis == Direction.Axis.Z ? turn : 0);
                    for (BlockPos p : blocks) placed.put(t.apply(p.subtract(mover)), t.apply(cells.get(p)));
                }
            } else {
                for (BlockPos p : blocks) placed.put(p.relative(step, travel), cells.get(p));
            }
            moved.putAll(placed);
        }
    }

    private static Part part(int i, JsonObject in, Map<BlockPos, BlockState> cells, SchematicLevel world, int sx, int sy, int sz,
                             List<String> problems) {
        BlockPos pos = new BlockPos(Diagrams.intOr(in, "x", -1), Diagrams.intOr(in, "y", -1), Diagrams.intOr(in, "z", -1));
        String where = "moving entry " + i + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
        BlockState state = cells.get(pos);
        if (state == null) {
            problems.add(where + ": that cell is empty; put the bearing, piston, pulley or gantry carriage there");
            return null;
        }
        Block b = state.getBlock();
        Kind kind = b instanceof MechanicalBearingBlock || b instanceof WindmillBearingBlock || b instanceof ClockworkBearingBlock ? Kind.BEARING
                : b instanceof MechanicalPistonBlock ? Kind.PISTON
                : b instanceof PulleyBlock || b instanceof ElevatorPulleyBlock ? Kind.PULLEY
                : b instanceof GantryCarriageBlock ? Kind.GANTRY : null;
        if (kind == null) {
            problems.add(where + " holds " + BuiltInRegistries.BLOCK.getKey(b) + ", which doesn't move a structure in a model: use "
                    + "a bearing, mechanical piston, rope or elevator pulley or gantry carriage (a minecart contraption can't be "
                    + "played in a model; show it standing)");
            return null;
        }
        List<Glue> glue = new ArrayList<>();
        var boxes = in.has("glue") && in.get("glue").isJsonArray() ? in.getAsJsonArray("glue") : new JsonArray();
        for (int g = 0; g < boxes.size(); g++) {
            if (!boxes.get(g).isJsonObject()) continue;
            JsonObject o = boxes.get(g).getAsJsonObject();
            int x = Diagrams.intOr(o, "x", -1), y = Diagrams.intOr(o, "y", -1), z = Diagrams.intOr(o, "z", -1);
            int x2 = Diagrams.intOr(o, "x2", x), y2 = Diagrams.intOr(o, "y2", y), z2 = Diagrams.intOr(o, "z2", z);
            if (Math.min(x, x2) < 0 || Math.min(y, y2) < 0 || Math.min(z, z2) < 0 || Math.max(x, x2) >= sx || Math.max(y, y2) >= sy
                    || Math.max(z, z2) >= sz) {
                problems.add(where + ": glue area " + g + " reaches outside the model");
                return null;
            }
            glue.add(new Glue(new BlockPos(Math.min(x, x2), Math.min(y, y2), Math.min(z, z2)),
                    new BlockPos(Math.max(x, x2), Math.max(y, y2), Math.max(z, z2))));
        }
        Part part = new Part(i, kind, pos, state, glue);
        part.asked = Diagrams.intOr(in, "travel", 0);
        part.rpmAsked = Diagrams.intOr(in, "rpm", 0);
        Set<BlockPos> anchors = new LinkedHashSet<>(List.of(pos));
        BlockPos start;
        Direction forced = null;
        switch (kind) {
            case BEARING -> {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                part.axis = facing.getAxis();
                start = pos.relative(facing);
            }
            case PISTON -> {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                if (state.getValue(MechanicalPistonBlock.STATE) != MechanicalPistonBlock.PistonState.RETRACTED) {
                    problems.add(where + ": write the piston retracted (state=retracted) with its extension poles behind it, as "
                            + "a player builds it; the book plays it pushing out");
                    return null;
                }
                // Poles go on the piston's back, in line with it; each one is a block of reach.
                BlockPos back = pos.relative(facing.getOpposite());
                while (cells.containsKey(back) && MechanicalPistonBlock.isExtensionPole(cells.get(back))
                        && cells.get(back).getValue(BlockStateProperties.FACING).getAxis() == facing.getAxis()) {
                    part.passing.add(back);
                    anchors.add(back);
                    back = back.relative(facing.getOpposite());
                }
                part.step = facing;
                part.travel = part.passing.size();
                start = pos.relative(facing);
                forced = facing;
            }
            case PULLEY -> {
                // The rope comes down to the first block below the pulley and takes that with it.
                BlockPos below = pos.below();
                while (below.getY() >= 0 && (!cells.containsKey(below) || cells.get(below).getBlock() instanceof PulleyBlock.RopeBlock
                        || cells.get(below).getBlock() instanceof PulleyBlock.MagnetBlock)) {
                    anchors.add(below);
                    below = below.below();
                }
                if (below.getY() < 0) {
                    problems.add(where + ": nothing hangs below the pulley for it to lift; build the part it carries under it");
                    return null;
                }
                part.step = Direction.DOWN;
                start = below;
            }
            default -> {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                BlockPos shaft = pos.relative(facing.getOpposite());
                BlockState s = cells.get(shaft);
                if (s == null || !(s.getBlock() instanceof GantryShaftBlock)) {
                    problems.add(where + ": a gantry carriage rides a create:gantry_shaft on its back (the face opposite its "
                            + "facing, at " + shaft.getX() + "," + shaft.getY() + "," + shaft.getZ() + "), and there is none");
                    return null;
                }
                Direction run = s.getValue(BlockStateProperties.FACING);
                // How far the shaft runs each way past the carriage; it rides to the far end.
                int ahead = 0, behind = 0;
                while (isShaft(cells.get(shaft.relative(run, ahead + 1)), run)) ahead++;
                while (isShaft(cells.get(shaft.relative(run, -(behind + 1))), run)) behind++;
                part.step = ahead >= behind ? run : run.getOpposite();
                part.travel = Math.max(ahead, behind);
                anchors.clear();
                anchors.add(shaft);
                start = pos;
            }
        }
        if (!gather(part, start, forced, anchors, world, where, problems)) return null;
        if (kind == Kind.PISTON) part.blocks.addAll(part.passing);
        if (part.blocks.isEmpty()) {
            problems.add(where + ": nothing comes along with it (" + describe(cells, start) + "): build the moving part against its "
                    + (kind == Kind.PULLEY ? "rope" : "front face") + " and glue it");
            return null;
        }
        if (kind == Kind.PISTON && part.travel == 0) {
            problems.add(where + ": the piston has no extension poles behind it, so it can't push: put "
                    + "create:piston_extension_pole blocks in line behind it, one per block of reach");
            return null;
        }
        return part;
    }

    private static boolean isShaft(BlockState s, Direction run) {
        return s != null && s.getBlock() instanceof GantryShaftBlock && s.getValue(BlockStateProperties.FACING) == run;
    }

    private static String describe(Map<BlockPos, BlockState> cells, BlockPos at) {
        BlockState s = cells.get(at);
        return (s == null ? "the cell at " : BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " at ") + at.getX() + "," + at.getY() + "," + at.getZ()
                + (s == null ? " is empty" : " doesn't come along");
    }

    private static boolean glued(Part part, BlockPos a, BlockPos b) {
        for (Glue g : part.glue) if (g.contains(a) && g.contains(b)) return true;
        return false;
    }

    /** Create's Contraption.searchMovedStructure on the model: the blocks a mover takes, with the same rules. */
    private static boolean gather(Part part, BlockPos start, Direction forced, Set<BlockPos> anchors, SchematicLevel world, String where,
                                  List<String> problems) {
        int max;
        try {
            max = AllConfigs.server().kinetics.maxBlocksMoved.get();
        } catch (RuntimeException e) {
            max = 2048;
        }
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> queued = new java.util.HashSet<>(), visited = new java.util.HashSet<>();
        if (!BlockMovementChecks.isBrittle(world.getBlockState(start))) {
            frontier.add(start);
            queued.add(start);
        }
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            visited.add(pos);
            if (anchors.contains(pos) || !inside(world, pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (!BlockMovementChecks.isMovementNecessary(state, world, pos)) continue;
            if (!BlockMovementChecks.isMovementAllowed(state, world, pos)) {
                problems.add(where + ": " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + pos.getX() + "," + pos.getY()
                        + "," + pos.getZ() + " can't be moved, so the part won't start moving; take it off the moving part");
                return false;
            }
            List<BlockPos> extra = new ArrayList<>();
            Block block = state.getBlock();
            if (block instanceof AbstractChassisBlock) extra.addAll(chassis(world, pos, state, forced));
            if (block instanceof BeltBlock) {
                BlockPos next = BeltBlock.nextSegmentPosition(state, pos, true), prev = BeltBlock.nextSegmentPosition(state, pos, false);
                if (next != null) extra.add(next);
                if (prev != null) extra.add(prev);
            }
            if (block instanceof StickerBlock && state.getValue(StickerBlock.EXTENDED)) {
                Direction d = state.getValue(StickerBlock.FACING);
                if (!BlockMovementChecks.isNotSupportive(world.getBlockState(pos.relative(d)), d.getOpposite())) extra.add(pos.relative(d));
            }
            if (state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                extra.add(pos.relative(ChestBlock.getConnectedDirection(state)));
            if (block instanceof MechanicalBearingBlock || block instanceof WindmillBearingBlock || block instanceof GantryCarriageBlock)
                extra.add(pos.relative(state.getValue(BlockStateProperties.FACING)));
            for (Direction offset : Direction.values()) {
                BlockPos next = pos.relative(offset);
                if (anchors.contains(next)) continue;
                BlockState other = world.getBlockState(next);
                if (!BlockMovementChecks.isMovementAllowed(other, world, next)) {
                    if (offset == forced) {
                        problems.add(where + ": it pushes straight into " + BuiltInRegistries.BLOCK.getKey(other.getBlock()) + " at "
                                + next.getX() + "," + next.getY() + "," + next.getZ() + ", which can't be moved");
                        return false;
                    }
                    continue;
                }
                boolean stick = !BlockMovementChecks.isBrittle(other) && state.canStickTo(other) && other.canStickTo(state);
                if (stick && (state.getPistonPushReaction() == PushReaction.PUSH_ONLY || other.getPistonPushReaction() == PushReaction.PUSH_ONLY
                        || BlockMovementChecks.isNotSupportive(state, offset) || BlockMovementChecks.isNotSupportive(other, offset.getOpposite())))
                    stick = false;
                if (stick || BlockMovementChecks.isBlockAttachedTowards(other, world, next, offset.getOpposite()) || glued(part, pos, next)
                        || offset == forced && !BlockMovementChecks.isNotSupportive(state, forced))
                    extra.add(next);
            }
            for (BlockPos e : extra) if (!visited.contains(e) && queued.add(e)) frontier.add(e);
            part.blocks.add(pos);
            if (part.blocks.size() > max) {
                problems.add(where + ": more than " + max + " blocks come along, more than one contraption can move");
                return false;
            }
        }
        return true;
    }

    /** What the model's box holds; outside it is empty air for the search (a model has no ground around it). */
    private static boolean inside(SchematicLevel world, BlockPos pos) {
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0 && pos.getX() < Models.MAX_SIZE && pos.getY() < Models.MAX_SIZE
                && pos.getZ() < Models.MAX_SIZE;
    }

    /** ChassisBlockEntity.getIncludedBlockPositions and addAttachedChasses, with a chassis at the range it is placed with. */
    private static List<BlockPos> chassis(SchematicLevel world, BlockPos pos, BlockState state, Direction forced) {
        List<BlockPos> out = new ArrayList<>();
        AbstractChassisBlock block = (AbstractChassisBlock) state.getBlock();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        int range = chassisRange();
        if (block instanceof RadialChassisBlock) {
            for (int o : new int[] {-1, 1}) {
                BlockPos n = pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis), o);
                BlockState ns = world.getBlockState(n);
                if (ns.getBlock() instanceof RadialChassisBlock && ns.getValue(BlockStateProperties.AXIS) == axis) out.add(n);
            }
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() == axis || !state.getValue(block.getGlueableSide(state, facing))) continue;
                ArrayDeque<BlockPos> local = new ArrayDeque<>(List.of(pos.relative(facing)));
                Set<BlockPos> seen = new java.util.HashSet<>();
                while (!local.isEmpty()) {
                    BlockPos p = local.poll();
                    BlockState s = world.getBlockState(p);
                    if (seen.contains(p) || !p.closerThan(pos, range + 0.5) || !inside(world, p)
                            || !BlockMovementChecks.isMovementNecessary(s, world, p) || BlockMovementChecks.isBrittle(s)) continue;
                    seen.add(p);
                    if (!p.equals(pos)) out.add(p);
                    for (Direction d : Direction.values())
                        if (d.getAxis() != axis && (!p.equals(pos) || d == facing) && !BlockMovementChecks.isNotSupportive(s, d))
                            local.add(p.relative(d));
                }
            }
        } else {
            for (Direction d : Direction.values()) {
                BlockState ns = world.getBlockState(pos.relative(d));
                if (LinearChassisBlock.isChassis(ns) && LinearChassisBlock.sameKind(state, ns) && ns.getValue(BlockStateProperties.AXIS) == axis)
                    out.add(pos.relative(d));
            }
            for (Direction facing : new Direction[] {Direction.get(Direction.AxisDirection.POSITIVE, axis),
                    Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
                boolean sticky = state.getValue(block.getGlueableSide(state, facing));
                for (int i = 1; i <= range; i++) {
                    BlockPos p = pos.relative(facing, i);
                    BlockState s = world.getBlockState(p);
                    if (forced != facing && !sticky || !BlockMovementChecks.isMovementNecessary(s, world, p) || BlockMovementChecks.isBrittle(s)) break;
                    out.add(p);
                    if (BlockMovementChecks.isNotSupportive(s, facing)) break;
                }
            }
        }
        return out;
    }

    private static int chassisRange() {
        try {
            return AllConfigs.server().kinetics.maxChassisRange.get() / 2;
        } catch (RuntimeException e) {
            return CHASSIS_RANGE_FALLBACK;
        }
    }

    /** What happens when a moving block meets a block that stays: it digs or harvests it, or it is in the way. */
    private enum Meet { PASS, DIGS, BLOCKED }

    private static Meet meet(BlockState mover, SchematicLevel world, BlockPos at, BlockState there) {
        boolean empty = there.getCollisionShape(world, at).isEmpty();
        try {
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(mover);
            if (behaviour instanceof BlockBreakingMovementBehaviour breaking)
                return breaking.canBreak(world, at, there) ? Meet.DIGS : empty ? Meet.PASS : Meet.BLOCKED;
            if (behaviour instanceof HarvesterMovementBehaviour harvester)
                return harvester.isValidCrop(world, at, there) || harvester.isValidOther(world, at, there) ? Meet.DIGS : empty ? Meet.PASS : Meet.BLOCKED;
        } catch (RuntimeException | LinkageError e) {
            // treated as a plain block
        }
        return empty || there.canBeReplaced() ? Meet.PASS : Meet.BLOCKED;
    }

    /** Runs the part through its motion: how far it gets, what it meets, how long it takes at its speed. */
    private static void sweep(Part p, Map<BlockPos, BlockState> cells, SchematicLevel world, Map<BlockPos, Float> rpm, StringBuilder out,
                              List<String> faults) {
        Block block = p.state.getBlock();
        boolean windmill = block instanceof WindmillBearingBlock, clock = block instanceof ClockworkBearingBlock;
        // A gantry carriage runs at its shaft's speed.
        Float driven = rpm.get(p.kind == Kind.GANTRY ? p.mover.relative(p.state.getValue(BlockStateProperties.FACING).getOpposite()) : p.mover);
        String speedFrom;
        if (p.rpmAsked != 0) {
            p.rpm = p.rpmAsked;
            speedFrom = "at " + p.rpmAsked + " RPM";
        } else if (driven != null && driven != 0) {
            p.rpm = driven;
            speedFrom = "at " + trim(Math.abs(driven)) + " RPM from the model's source";
        } else {
            p.rpm = 32;
            speedFrom = "at 32 RPM (no source in the model reaches it; give rpm for the speed the player will drive it at)";
        }
        Map<String, Integer> tally = new TreeMap<>();
        int sails = 0;
        for (BlockPos q : p.blocks) {
            BlockState s = cells.get(q);
            if (s == null || p.passing.contains(q)) continue;
            tally.merge(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), 1, Integer::sum);
            // Only the wind-catching sails make a windmill turn here; Create's own sail blocks are decoration.
            if (s.is(SAILS) && BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("aeronauticswinds")) sails++;
        }
        List<String> parts = new ArrayList<>();
        tally.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> parts.add(e.getValue() + " " + e.getKey()));
        int count = p.blocks.size() - p.passing.size();
        p.sails = sails;
        out.append("- ").append(p.name()).append(count == 0 ? " moves only its own head" : " carries " + count + (count == 1 ? " block: " : " blocks: ")
                + String.join(", ", parts)).append(".\n");
        Map<String, Meet> met = new LinkedHashMap<>();
        if (p.kind == Kind.BEARING) {
            float want = clock ? 360 : p.asked > 0 ? Math.min(p.asked, 360) : 360;
            float dir = Math.signum(p.rpm) == 0 ? 1 : Math.signum(p.rpm);
            p.degrees = want * dir;
            float degPerSecond = Math.abs(p.rpm) * 6;
            if (windmill) {
                if (sails < minimumSails())
                    faults.add(p.name() + " carries " + sails + " Windcatcher or sail cloth blocks and needs at least " + minimumSails()
                            + " to turn (windmill sails and other blocks don't catch the wind)");
                p.seconds = want / degPerSecond;
                out.append("- It turns with the wind, a full turn in about ").append(trim(360 / degPerSecond)).append(" s at ")
                        .append(trim(Math.abs(p.rpm))).append(" RPM (").append(sails).append(" Windcatcher and sail cloth blocks).\n");
            } else if (clock) {
                p.seconds = 12;
                out.append("- It turns like the hour hand of a clock, once round every 12 hours of the day (10 minutes); a "
                        + "second part glued in front of the first, apart from it, turns as the minute hand.\n");
            } else {
                p.seconds = want / degPerSecond;
                out.append("- It turns ").append(trim(want)).append(" degrees ").append(speedFrom).append(": ")
                        .append(trim(degPerSecond)).append(" degrees a second, ")
                        .append(want < 360 ? trim(want) + " degrees in " + trim(want / degPerSecond) + " s; stopping it there takes a "
                                + "Sequenced Gearshift or a redstone signal timed to it" : "a full turn in " + trim(360 / degPerSecond) + " s")
                        .append(".\n");
            }
            out.append("- The player sets it going by right-clicking the bearing with an empty hand, once it is built.\n");
            float hit = turnSweep(p, cells, world, Math.abs(p.degrees), dir, met);
            if (hit >= 0 && want < 360) {
                // A part that turns only so far can be driven either way round: see if the other way is clear.
                Map<String, Meet> other = new LinkedHashMap<>();
                float back = turnSweep(p, cells, world, Math.abs(p.degrees), -dir, other);
                if (back < 0) {
                    out.append("- Turned one way it swings into what is beside it (").append(String.join("; ", blockedList(met)))
                            .append("); the other way round it is clear, so it must be driven that way (reverse its rotation with a "
                                    + "gearshift or by turning the source round).\n");
                    p.degrees = -p.degrees;
                    met.clear();
                    met.putAll(other);
                    hit = -1;
                }
            }
            if (hit >= 0) faults.add(blockedText(p, met) + " after " + trim(hit) + " degrees of turning" + (want < 360 ? ", whichever way it turns" : "")
                    + ": a turning part passes straight through blocks in its way, so leave the whole circle it sweeps clear");
            else if (met.values().stream().anyMatch(m -> m == Meet.DIGS)) out.append("- On its way round ").append(diggings(met)).append(".\n");
        } else {
            float perTick = Math.min(Math.abs(p.rpm) / 512f, block instanceof ElevatorPulleyBlock ? 1.99f : 0.49f);
            if (block instanceof ElevatorPulleyBlock) perTick = Math.min(Math.abs(p.rpm) * 2 / 512f, 1.99f);
            float perSecond = perTick * 20;
            int room = p.kind == Kind.PULLEY ? 64 : p.travel;
            int reach = slideSweep(p, cells, world, room, met);
            if (p.kind == Kind.PULLEY && reach == 0) {
                // Resting on something already: show it being drawn up to the pulley instead.
                int top = p.blocks.stream().mapToInt(BlockPos::getY).max().orElse(p.mover.getY() - 1);
                p.step = Direction.UP;
                met.clear();
                reach = slideSweep(p, cells, world, p.mover.getY() - 1 - top, met);
            }
            int goes = p.asked > 0 ? Math.min(p.asked, reach) : reach;
            if (p.asked > 0 && p.kind != Kind.PULLEY && p.asked > p.travel)
                faults.add(p.name() + " reaches " + p.travel + " blocks, not " + p.asked
                        + (p.kind == Kind.PISTON ? ": add extension poles behind it, one per block" : ": the gantry shaft ends there"));
            else if (p.asked > reach || p.asked == 0 && reach < room && p.kind != Kind.PULLEY)
                faults.add(blockedText(p, met) + " after " + reach + (reach == 1 ? " block" : " blocks") + ", so it stops there");
            p.travel = goes;
            p.seconds = perSecond <= 0 ? 0 : goes / perSecond;
            String way = p.kind == Kind.PISTON ? "pushes out " : p.kind == Kind.GANTRY ? "rides along the shaft " : p.step == Direction.UP ? "draws its load " : "lowers its load ";
            out.append("- It ").append(way).append(goes).append(goes == 1 ? " block " : " blocks ").append(dirName(p.step)).append(' ')
                    .append(speedFrom).append(": ").append(trim(perSecond)).append(" blocks a second, ").append(trim(p.seconds)).append(" s");
            if (p.kind == Kind.PISTON)
                out.append(MechanicalPistonBlock.isStickyPiston(p.state) ? "; run backwards, the sticky piston pulls it back"
                        : "; run backwards, this piston comes back on its own and leaves the blocks out there (a sticky piston brings them back)");
            if (p.kind == Kind.GANTRY) out.append("; reversed, it rides back the other way");
            if (p.kind == Kind.PULLEY) out.append(p.step == Direction.UP ? "; reversed, it lowers it again. Built like this, it is first driven "
                    + "the lowering way: its rope runs down to the load, takes hold and stops there, and reversed it then draws it up"
                    : "; reversed, it draws it back up");
            out.append(".\n");
            if (!met.isEmpty() && met.values().stream().anyMatch(m -> m == Meet.DIGS)) out.append("- On its way ").append(diggings(met)).append(".\n");
        }
    }

    private static String dirName(Direction d) {
        return switch (d) {
            case UP -> "up";
            case DOWN -> "down";
            case NORTH -> "north (-z)";
            case SOUTH -> "south (+z)";
            case WEST -> "west (-x)";
            case EAST -> "east (+x)";
        };
    }

    private static String blockedText(Part p, Map<String, Meet> met) {
        return "the part on " + p.name() + " runs into what is in its way (" + String.join("; ", blockedList(met)) + ")";
    }

    private static List<String> blockedList(Map<String, Meet> met) {
        List<String> hits = new ArrayList<>();
        met.forEach((k, v) -> {
            if (v == Meet.BLOCKED && hits.size() < 4) hits.add(k);
        });
        return hits;
    }

    private static String diggings(Map<String, Meet> met) {
        List<String> dug = new ArrayList<>();
        met.forEach((k, v) -> {
            if (v == Meet.DIGS && dug.size() < 4) dug.add(k);
        });
        return "its drills, saws or harvesters break or harvest what they pass (" + String.join("; ", dug) + ")";
    }

    /** Steps the part out a block at a time; returns how many blocks it moves before something stops it. */
    private static int slideSweep(Part p, Map<BlockPos, BlockState> cells, SchematicLevel world, int room, Map<String, Meet> met) {
        for (int n = 1; n <= room; n++) {
            boolean blocked = false;
            for (BlockPos q : p.blocks) {
                if (p.passing.contains(q)) continue;
                BlockPos at = q.relative(p.step, n);
                if (p.blocks.contains(at) || at.equals(p.mover) || p.passing.contains(at)) continue;
                if (p.kind == Kind.PULLEY && at.getY() < 0) return n - 1; // the model's floor
                BlockState there = cells.get(at);
                if (there == null) continue;
                Meet m = meet(cells.get(q), world, at, there);
                if (m == Meet.PASS) continue;
                met.putIfAbsent(hitName(cells, q, at, there), m);
                if (m == Meet.BLOCKED) blocked = true;
            }
            if (blocked) return n - 1;
        }
        return room;
    }

    /**
     * Turns the part round its bearing in small steps, testing points spread over the shape of every block that turns
     * against the shapes of the blocks that stay; returns the angle at which it first passes into one, or -1.
     */
    private static float turnSweep(Part p, Map<BlockPos, BlockState> cells, SchematicLevel world, float total, float dir, Map<String, Meet> met) {
        Vec3 pivot = Vec3.atCenterOf(p.mover);
        double far = 1;
        Map<BlockPos, List<Vec3>> points = new LinkedHashMap<>();
        for (BlockPos q : p.blocks) {
            BlockState s = cells.get(q);
            if (s == null) continue;
            List<Vec3> pts = new ArrayList<>();
            for (AABB b : s.getShape(world, q).toAabbs()) {
                // A fifth of a block in from each edge: a hinge block's corners grazing the wall beside it is no clash.
                AABB in = b.deflate(Math.min(0.2, b.getXsize() / 4), Math.min(0.2, b.getYsize() / 4), Math.min(0.2, b.getZsize() / 4));
                pts.add(in.getCenter().add(q.getX(), q.getY(), q.getZ()));
                for (double x : new double[] {in.minX, in.maxX}) for (double y : new double[] {in.minY, in.maxY}) for (double z : new double[] {in.minZ, in.maxZ})
                    pts.add(new Vec3(q.getX() + x, q.getY() + y, q.getZ() + z));
            }
            if (pts.isEmpty()) continue;
            points.put(q, pts);
            for (Vec3 v : pts) far = Math.max(far, v.distanceTo(pivot));
        }
        double stepDeg = Math.min(5, Math.toDegrees(0.2 / far));
        Map<BlockPos, List<AABB>> shapes = new HashMap<>();
        for (double a = stepDeg; a <= total + 1e-6; a += stepDeg) {
            double r = Math.toRadians(a * dir), cos = Math.cos(r), sin = Math.sin(r);
            boolean hit = false;
            for (Map.Entry<BlockPos, List<Vec3>> e : points.entrySet()) {
                BlockPos q = e.getKey();
                for (Vec3 v : e.getValue()) {
                    double x = v.x - pivot.x, y = v.y - pivot.y, z = v.z - pivot.z, nx = x, ny = y, nz = z;
                    switch (p.axis) {
                        case X -> {
                            ny = y * cos - z * sin;
                            nz = y * sin + z * cos;
                        }
                        case Y -> {
                            nx = x * cos + z * sin;
                            nz = -x * sin + z * cos;
                        }
                        case Z -> {
                            nx = x * cos - y * sin;
                            ny = x * sin + y * cos;
                        }
                    }
                    Vec3 w = new Vec3(pivot.x + nx, pivot.y + ny, pivot.z + nz);
                    BlockPos at = BlockPos.containing(w);
                    if (p.blocks.contains(at) || at.equals(p.mover)) continue;
                    BlockState there = cells.get(at);
                    if (there == null) continue;
                    Meet m = meet(cells.get(q), world, at, there);
                    if (m == Meet.PASS) continue;
                    // Only where the turning block really passes into the other's shape, not just its cell.
                    Vec3 local = w.subtract(at.getX(), at.getY(), at.getZ());
                    List<AABB> boxes = shapes.computeIfAbsent(at, k -> there.getShape(world, k).toAabbs());
                    if (boxes.stream().noneMatch(b -> b.contains(local))) continue;
                    met.putIfAbsent(hitName(cells, q, at, there), m);
                    if (m == Meet.BLOCKED) hit = true;
                }
            }
            if (hit) return Math.round(a);
        }
        return -1;
    }

    private static String hitName(Map<BlockPos, BlockState> cells, BlockPos from, BlockPos at, BlockState there) {
        return BuiltInRegistries.BLOCK.getKey(cells.get(from).getBlock()) + " from " + from.getX() + "," + from.getY() + ","
                + from.getZ() + " into " + BuiltInRegistries.BLOCK.getKey(there.getBlock()) + " at " + at.getX() + "," + at.getY() + "," + at.getZ();
    }

    /** The stress the movers add to the networks that drive them, against what those networks' sources give. */
    private static void stress(List<Part> parts, Map<BlockPos, BlockState> fixed, ServerLevel level, Map<BlockPos, Float> rpm,
                               StringBuilder out, List<String> faults) {
        try {
            Set<BlockPos> done = new java.util.HashSet<>();
            Map<BlockPos, Part> windmills = new HashMap<>();
            for (Part p : parts) if (p.state.getBlock() instanceof WindmillBearingBlock) windmills.put(p.mover, p);
            for (Part p : parts) {
                if (p.state.getBlock() instanceof WindmillBearingBlock) {
                    Set<BlockPos> network = BuildCheck.network(fixed, level, p.mover);
                    if (!done.addAll(network)) continue;
                    double need = 0, give = 0;
                    for (BlockPos q : network) {
                        Float r = rpm.get(q);
                        BlockState s = fixed.get(q);
                        if (r == null || s == null) continue;
                        need += BlockStressValues.getImpact(s.getBlock()) * Math.abs(r);
                        Part w = windmills.get(q);
                        give += w != null ? windCapacity(w) : BlockStressValues.getCapacity(s.getBlock()) * Math.abs(r);
                    }
                    out.append("- ").append(p.name()).append(" gives ").append(trim((float) windCapacity(p))).append(" su at ")
                            .append(trim(Math.abs(p.rpm))).append(" RPM from its ").append(p.sails).append(" Windcatchers (")
                            .append(trim((float) (BlockStressValues.getCapacity(p.state.getBlock()) * windShare()))).append(" su per RPM for each one after the first, ")
                            .append(trim((float) windCap())).append(" su at most); ").append(need > 0 ? "the machines it drives take " + trim((float) need) + " su" : "nothing on its shafts takes stress")
                            .append(network.size() > 1 && give > windCapacity(p) + 1e-3 ? ", of the " + trim((float) give) + " su all its network's sources give" : "")
                            .append(". It turns at the wind's speed, faster higher up and in a storm.\n");
                    if (need > give + 1e-3)
                        faults.add("the network " + p.name() + " drives is overstressed (" + trim((float) need) + " su asked of "
                                + trim((float) give) + "), so all of it stops: add Windcatchers or another source, or gear it slower");
                    continue;
                }
                if (p.state.getBlock() instanceof ClockworkBearingBlock) continue;
                BlockPos at = p.kind == Kind.GANTRY ? p.mover.relative(p.state.getValue(BlockStateProperties.FACING).getOpposite()) : p.mover;
                double own = BlockStressValues.getImpact(fixed.getOrDefault(at, p.state).getBlock()) * Math.abs(p.rpm);
                Set<BlockPos> network = BuildCheck.network(fixed, level, at);
                double need = 0, give = 0;
                boolean anySource = false;
                for (BlockPos q : network) {
                    Float r = rpm.get(q);
                    BlockState s = fixed.get(q);
                    if (r == null || s == null) continue;
                    need += BlockStressValues.getImpact(s.getBlock()) * Math.abs(r);
                    Part w = windmills.get(q);
                    double cap = w != null ? windCapacity(w) : BlockStressValues.getCapacity(s.getBlock()) * Math.abs(r);
                    if (cap > 0) anySource = true;
                    give += cap;
                }
                String mover = BuiltInRegistries.BLOCK.getKey(fixed.getOrDefault(at, p.state).getBlock()).toString();
                if (!anySource || !rpm.containsKey(at)) {
                    if (own <= 0) continue;
                    out.append("- ").append(mover).append(" takes ").append(trim((float) own)).append(" su at ").append(trim(Math.abs(p.rpm)))
                            .append(" RPM; whatever drives it must give that on top of its other machines.\n");
                    continue;
                }
                if (!done.addAll(network)) continue;
                out.append("- Stress on the network that drives ").append(p.name()).append(": its machines take ").append(trim((float) need))
                        .append(" su").append(own > 0 ? " (" + mover + " " + trim((float) own) + ")" : "").append(" of the ")
                        .append(trim((float) give)).append(" su its sources give.\n");
                if (need > give + 1e-3)
                    faults.add("the network driving " + p.name() + " is overstressed (" + trim((float) need) + " su asked of "
                            + trim((float) give) + "), so all of it stops: add a source, or gear it slower");
            }
        } catch (RuntimeException | LinkageError e) {
            out.append("- (Stress couldn't be worked out for this model.)\n");
        }
    }

    /**
     * What a windmill gives in su, the way Winds & Weather works it out: Create's capacity per RPM, grown by a share of it
     * for every Windcatcher after the first, times the speed, up to a ceiling.
     */
    private static double windCapacity(Part p) {
        if (p.sails <= 0) return 0;
        double perRpm = BlockStressValues.getCapacity(p.state.getBlock()) * (1 + Math.max(0, p.sails - 1) * windShare());
        return Math.min(perRpm * Math.abs(p.rpm), windCap());
    }

    private static double windShare() {
        try {
            Object common = Class.forName("com.enzonic.aeronauticswinds.config.WindsConfig").getField("COMMON").get(null);
            double v = (Double) ((net.neoforged.neoforge.common.ModConfigSpec.DoubleValue) common.getClass()
                    .getField("windmillSuPerWindcatcher").get(common)).get();
            return Math.abs(v - 0.65) < 1e-6 ? 0.55 : v; // Winds & Weather reads its old default as the new one
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0.55;
        }
    }

    private static double windCap() {
        try {
            Object common = Class.forName("com.enzonic.aeronauticswinds.config.WindsConfig").getField("COMMON").get(null);
            return (Double) ((net.neoforged.neoforge.common.ModConfigSpec.DoubleValue) common.getClass()
                    .getField("windmillMaxStressCapacity").get(common)).get();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 8192;
        }
    }

    private static int minimumSails() {
        try {
            return AllConfigs.server().kinetics.minimumWindmillSails.get();
        } catch (RuntimeException e) {
            return 8;
        }
    }

    private static String trim(float f) {
        float r = Math.round(f * 10) / 10f;
        return r == Math.round(r) ? String.valueOf(Math.round(r)) : String.valueOf(r);
    }
}

package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tries a model's machines before the book shows them: places the blocks in a scratch copy of the world (Create's
 * own schematic level, as the schematicannon uses) and follows rotation the way Create does, through shaft ends, cogs,
 * chain drives, chain gearshifts, speed controllers and belts. It reports what turns with which source, at what speed and direction, and the faults a
 * player would hit: a shaft that ends against the side of a machine that doesn't take rotation there, a machine the
 * rotation never reaches, two sources fighting over one network, a water wheel with no water on it.
 */
final class BuildCheck {
    /**
     * Ends the heading of every check result: the book reads these to know the world, and tells the player what a build does,
     * never that it was checked or by whose rules.
     */
    static final String FOR_YOU = " (for you only: tell the player what it does and what to change, as plain facts about their "
            + "build; never mention these notes, a check, passing, faults or the rules or mods behind them)";

    record Report(String text, List<String> faults) {
    }

    private record Cell(BlockPos pos, BlockState state) {
    }

    private BuildCheck() {
    }

    /** Null when the model has no Create machines in it. */
    static Report check(Map<BlockPos, BlockState> blocks, ServerLevel level) {
        return check(blocks, level, Drives.set());
    }

    /** {@code set} holds the speed each Rotation Speed Controller is set to; one not in it is at 16 RPM, as placed. */
    static Report check(Map<BlockPos, BlockState> blocks, ServerLevel level, Map<BlockPos, Float> set) {
        try {
            return run(blocks, level, set);
        } catch (RuntimeException | LinkageError e) {
            return new Report("(The rotation check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    private static Report run(Map<BlockPos, BlockState> blocks, ServerLevel level, Map<BlockPos, Float> set) {
        SchematicLevel world = new SchematicLevel(level);
        blocks.forEach((pos, state) -> world.setBlock(pos, state, 0));
        Map<BlockPos, Cell> kinetic = new LinkedHashMap<>();
        blocks.forEach((pos, state) -> {
            if (state.getBlock() instanceof IRotate) kinetic.put(pos, new Cell(pos, state));
        });
        if (kinetic.isEmpty()) return null;
        List<String> faults = new ArrayList<>(), notes = new ArrayList<>();

        // Shaft ends that meet a machine which doesn't take rotation on that face.
        for (Cell c : kinetic.values()) {
            IRotate def = (IRotate) c.state.getBlock();
            for (Direction d : Direction.values()) {
                Cell n = kinetic.get(c.pos.relative(d));
                if (n == null || !def.hasShaftTowards(world, c.pos, c.state, d)) continue;
                if (modifier(world, c, n) != 0) continue;
                IRotate nd = (IRotate) n.state.getBlock();
                if (nd.hasShaftTowards(world, n.pos, n.state, d.getOpposite())) continue;
                faults.add(name(c) + " ends against the " + face(d.getOpposite(), n.state) + " of " + name(n)
                        + ", which doesn't take rotation there" + takes(world, n));
            }
        }

        // Follow rotation out from every source, the way Create spreads it: relative speed 1 at the source. Past a speed
        // controller the speed is the one it is set to, so that side counts from 1 again, from the block behind it (its root).
        Map<BlockPos, Float> speed = new LinkedHashMap<>();
        Map<BlockPos, BlockPos> sourceOf = new LinkedHashMap<>(), rootOf = new LinkedHashMap<>();
        Map<BlockPos, Cell> controlled = new LinkedHashMap<>();
        Map<BlockPos, Integer> steps = new java.util.HashMap<>();
        Map<BlockPos, Direction> fed = new java.util.HashMap<>();
        List<Cell> sources = new ArrayList<>();
        java.util.Set<BlockPos> conflicts = new java.util.LinkedHashSet<>();
        for (Cell c : kinetic.values()) if (isSource(c)) sources.add(c);
        for (Cell src : sources) {
            if (speed.containsKey(src.pos)) {
                Cell first = kinetic.get(sourceOf.get(src.pos));
                Integer a = spin(first), b = spin(src);
                if (a != null && b != null && Math.abs(Math.abs(speed.get(src.pos)) - 1) < 1e-3) {
                    // Both are Portable Engines, which turn the same speed; only the way round can disagree.
                    if (a * Math.signum(speed.get(src.pos)) == b) notes.add(name(src) + " drives the same line as " + name(first)
                            + ", turning it the same way, so their stress adds up");
                    else notes.add(name(src) + " drives the same line as " + name(first) + " but, as each comes from placing, "
                            + "turns it the other way, which would break the line: tell the player to set it to turn the other "
                            + "way (its Rotation Direction setting, the value box on the engine) before lighting it; "
                            + "then their stress adds up");
                } else {
                    notes.add(name(src) + " drives the same network as " + name(first)
                            + "; sources on one network must turn it at the same speed and direction, or it breaks");
                }
                continue;
            }
            speed.put(src.pos, 1f);
            sourceOf.put(src.pos, src.pos);
            rootOf.put(src.pos, src.pos);
            steps.put(src.pos, 0);
            ArrayDeque<Cell> todo = new ArrayDeque<>(List.of(src));
            while (!todo.isEmpty()) {
                Cell c = todo.poll();
                for (Cell n : neighbours(kinetic, c)) {
                    float m = modifier(world, c, n, fed);
                    if (m == 0) continue;
                    Cell ctrl = controller(c, n);
                    BlockPos root = ctrl != null ? n.pos : rootOf.get(c.pos);
                    float s = ctrl != null ? 1 : speed.get(c.pos) * m;
                    Float had = speed.get(n.pos);
                    // Back across a controller: it sets the far side's speed, so the two sides never disagree.
                    if (ctrl != null && had != null) continue;
                    if (had == null) {
                        speed.put(n.pos, s);
                        sourceOf.put(n.pos, src.pos);
                        rootOf.put(n.pos, root);
                        if (ctrl != null) controlled.put(n.pos, ctrl);
                        steps.put(n.pos, ctrl != null ? 0 : steps.get(c.pos)
                                + (Math.abs(Math.abs(m) - 1) > 1e-3 && (ICogWheel.isLargeCog(c.state) || ICogWheel.isLargeCog(n.state)) ? 1 : 0));
                        feed(fed, n, c);
                        todo.add(n);
                    } else if (Math.abs(had - s) > 1e-3 || !rootOf.get(n.pos).equals(root)) {
                        conflicts.add(n.pos);
                    }
                }
            }
        }
        for (BlockPos p : conflicts) {
            faults.add(name(kinetic.get(p)) + " is reached at two different speeds or directions, so the network would "
                    + "break (Create pops a block off): gear ratios or a loop of cogs disagree");
        }

        // A source whose output face meets nothing that takes rotation turns nothing, however the rest is geared.
        if (kinetic.size() > sources.size()) {
            for (Cell src : sources) {
                if (name(src).contains("steering_wheel") || neighbours(kinetic, src).stream().anyMatch(n -> modifier(world, src, n) != 0)) continue;
                faults.add(name(src) + " turns nothing: it drives only through " + outputs(world, src) + ", and nothing there takes "
                        + "its rotation" + turnTo(world, kinetic, src));
            }
        }

        for (Cell c : kinetic.values()) {
            if (c.state.getBlock() instanceof WaterWheelBlock && !wet(blocks, c)) {
                faults.add(name(c) + " has no water on it in the model, so it won't turn; water must flow against "
                        + "its rim (the blocks around it, not along its axle)");
            }
        }

        // Machines that stand still below their minimum speed (a mixer needs medium speed): a water wheel's speed is
        // fixed, so that is a fault; other sources can be set faster, so it is a note on the speed the model assumes.
        Map<BlockPos, Float> rpm = rpm(blocks, level, new ArrayList<>(), set);
        for (Cell c : kinetic.values()) {
            IRotate.SpeedLevel min = ((IRotate) c.state.getBlock()).getMinimumRequiredSpeedLevel();
            Float r = rpm.get(c.pos);
            if (min == IRotate.SpeedLevel.NONE || r == null || Math.abs(r) >= min.getSpeedValue() || !sourceOf.containsKey(c.pos)) continue;
            Cell src = kinetic.get(sourceOf.get(c.pos));
            Cell ctrl = controlled.get(rootOf.get(c.pos));
            String slow = name(c) + " turns at " + trim(Math.abs(r)) + " RPM, but needs at least " + trim(min.getSpeedValue())
                    + " RPM to work, and below that it stands still";
            if (ctrl != null)
                faults.add(slow + ": set " + name(ctrl) + " to at least " + trim((float) Math.ceil(min.getSpeedValue() / Math.abs(r)
                        * Math.abs(rpm.getOrDefault(rootOf.get(c.pos), r)))) + " RPM (give it in speeds, and tell the player)");
            else if (src.state.getBlock() instanceof WaterWheelBlock || src.state.getBlock() instanceof LargeWaterWheelBlock)
                faults.add(slow + " (" + name(src) + " always turns at " + trim(nominalRpm(src.state)) + " RPM; speed it up with "
                        + Drives.change(Math.abs(r), min.getSpeedValue()) + ")");
            else notes.add(slow + " with " + name(src) + " at its usual " + trim(nominalRpm(src.state)) + " RPM; set the source "
                    + "faster or speed it up with " + Drives.change(Math.abs(r), min.getSpeedValue()));
        }

        // A machine geared through several cog steps: say what else the player could do it with, and leave the choice.
        Cell geared = null;
        for (Cell c : kinetic.values())
            if (steps.getOrDefault(c.pos, 0) >= 2 && !relay(c) && (geared == null || steps.get(c.pos) > steps.get(geared.pos))) geared = c;
        if (geared != null) {
            Float to = rpm.get(geared.pos), from = rpm.get(rootOf.get(geared.pos));
            String others = Drives.besidesCogs(from == null ? 0 : from, to == null ? 0 : to);
            if (!others.isEmpty()) notes.add(name(geared) + " gets its speed through " + steps.get(geared.pos) + " cog steps"
                    + (to != null && from != null ? " (" + trim(Math.abs(from)) + " to " + trim(Math.abs(to)) + " RPM)" : "")
                    + ", which works; it could also be " + others);
        }
        for (Cell c : kinetic.values())
            if (c.state.getBlock() instanceof ChainGearshiftBlock && c.state.getValue(ChainGearshiftBlock.POWERED))
                notes.add(name(c) + " is powered, taken as signal 15 (twice the speed out to the chain drives it drives); a "
                        + "weaker signal gives less, 1 + (signal + 1) / 16 times");

        StringBuilder out = new StringBuilder("How these blocks turn and connect" + BuildCheck.FOR_YOU + ":\n");
        if (sources.isEmpty()) {
            out.append("- No rotation source in the model (water wheel, windmill, motor, engine, crank). Machines are listed "
                    + "by what they connect to.\n");
        }
        for (Cell src : sources) {
            out.append("- ").append(name(src)).append(name(src).contains("steering_wheel")
                    ? " is the helm: it turns what its shaft or cog drives only while the pilot steers, and comparators at its sides "
                    + "read it without anything turning (\"nothing\" below is right for a wheel that steers by comparators). Turning with it:"
                    : " is a source. Turning with it:");
            list(out, kinetic, src.pos, rootOf, speed);
            for (Map.Entry<BlockPos, Cell> e : controlled.entrySet()) {
                if (!sourceOf.get(e.getKey()).equals(src.pos)) continue;
                Float at = set.get(e.getValue().pos);
                out.append("- ").append(name(kinetic.get(e.getKey()))).append(" turns at the speed set on ").append(name(e.getValue()))
                        .append(at != null ? ", " + trim(Math.abs(at)) + " RPM (tell the player to set it on its panel)"
                                : ": not given in speeds, so 16 RPM as placed").append(". Turning with it:");
                list(out, kinetic, e.getKey(), rootOf, speed);
            }
        }
        if (!sources.isEmpty()) {
            List<String> idle = new ArrayList<>();
            for (Cell c : kinetic.values()) if (!speed.containsKey(c.pos)) idle.add(name(c));
            if (!idle.isEmpty()) {
                notes.add("Not turning (no source reaches it): " + String.join("; ", idle));
                if (idle.stream().anyMatch(s -> !s.contains("create:shaft") && !s.contains("cogwheel")))
                    faults.add("rotation never reaches " + String.join("; ", idle));
            }
        }
        for (String note : notes) out.append("- ").append(note).append(".\n");
        if (faults.isEmpty()) out.append("- Nothing here stops it working.\n");
        else for (String f : faults) out.append("- PROBLEM: ").append(f).append(".\n");
        return new Report(out.toString().trim(), faults);
    }

    private static void list(StringBuilder out, Map<BlockPos, Cell> kinetic, BlockPos root, Map<BlockPos, BlockPos> rootOf,
            Map<BlockPos, Float> speed) {
        int n = 0;
        for (Map.Entry<BlockPos, BlockPos> e : rootOf.entrySet()) {
            if (!e.getValue().equals(root) || e.getKey().equals(root)) continue;
            if (n++ > 0) out.append(';');
            out.append(' ').append(name(kinetic.get(e.getKey()))).append(' ').append(relative(speed.get(e.getKey())));
        }
        out.append(n == 0 ? " nothing.\n" : ".\n");
    }

    /** Parts that only pass rotation on (shafts, cogs, gearboxes, chain drives, belts), as against machines it drives. */
    private static boolean relay(Cell c) {
        String id = BuiltInRegistries.BLOCK.getKey(c.state.getBlock()).toString();
        return id.contains("cogwheel") || c.state.getBlock() instanceof ChainDriveBlock
                || c.state.getBlock() instanceof BeltBlock || id.endsWith("shaft") || id.endsWith("gearbox") || id.endsWith("clutch")
                || id.endsWith("gearshift") || id.endsWith("speed_controller");
    }

    /**
     * The Rotation Speed Controller when {@code a} and {@code b} are one and the large cogwheel on top of it (axis level
     * and across the controller's own shaft), as Create joins them; null otherwise.
     */
    private static Cell controller(Cell a, Cell b) {
        for (Cell[] pair : new Cell[][] {{a, b}, {b, a}}) {
            Cell ctrl = pair[0], cog = pair[1];
            if (!BuiltInRegistries.BLOCK.getKey(ctrl.state.getBlock()).toString().equals("create:rotation_speed_controller")) continue;
            if (!ICogWheel.isLargeCog(cog.state) || !cog.pos.equals(ctrl.pos.above())) continue;
            Direction.Axis axis = cog.state.getValue(BlockStateProperties.AXIS);
            if (axis.isVertical() || ctrl.state.getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS) == axis) continue;
            return ctrl;
        }
        return null;
    }

    /**
     * Each kinetic block's speed in RPM when every source in a model turns at its usual speed (a model holds no
     * settings but its speed controllers'): engines and motors at their usual output, water wheels at their usual flow.
     * {@code assumed} gets one line per source saying the speed used.
     */
    static Map<BlockPos, Float> rpm(Map<BlockPos, BlockState> blocks, ServerLevel level, List<String> assumed) {
        return rpm(blocks, level, assumed, Drives.set());
    }

    /** As above, with each speed controller at the speed in {@code set}, or at 16 RPM as placed. */
    static Map<BlockPos, Float> rpm(Map<BlockPos, BlockState> blocks, ServerLevel level, List<String> assumed, Map<BlockPos, Float> set) {
        Map<BlockPos, Float> out = new LinkedHashMap<>();
        Map<BlockPos, Direction> fed = new java.util.HashMap<>();
        try {
            SchematicLevel world = new SchematicLevel(level);
            blocks.forEach((pos, state) -> world.setBlock(pos, state, 0));
            Map<BlockPos, Cell> kinetic = new LinkedHashMap<>();
            blocks.forEach((pos, state) -> {
                if (state.getBlock() instanceof IRotate) kinetic.put(pos, new Cell(pos, state));
            });
            for (Cell src : kinetic.values()) {
                // A steering wheel is a control, turned by hand only while steering: it drives nothing for long.
                if (!isSource(src) || out.containsKey(src.pos) || name(src).contains("steering_wheel")) continue;
                float r = nominalRpm(src.state);
                assumed.add(name(src) + " at " + trim(r) + " RPM");
                out.put(src.pos, r);
                ArrayDeque<Cell> todo = new ArrayDeque<>(List.of(src));
                while (!todo.isEmpty()) {
                    Cell c = todo.poll();
                    for (Cell n : neighbours(kinetic, c)) {
                        float m = modifier(world, c, n, fed);
                        if (m == 0 || out.containsKey(n.pos)) continue;
                        Cell ctrl = controller(c, n);
                        out.put(n.pos, ctrl == null ? out.get(c.pos) * m
                                : Math.signum(out.get(c.pos)) * Math.abs(set.getOrDefault(ctrl.pos, 16f)));
                        feed(fed, n, c);
                        todo.add(n);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // Speeds stay unknown; the vehicle check then says what it couldn't work out.
        }
        return out;
    }

    /** The kinetic blocks connected to {@code start} (shafts, cogs, belts, machines), start included; empty if it isn't kinetic. */
    static java.util.Set<BlockPos> network(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos start) {
        java.util.Set<BlockPos> out = new java.util.LinkedHashSet<>();
        SchematicLevel world = new SchematicLevel(level);
        blocks.forEach((pos, state) -> world.setBlock(pos, state, 0));
        Map<BlockPos, Cell> kinetic = new LinkedHashMap<>();
        blocks.forEach((pos, state) -> {
            if (state.getBlock() instanceof IRotate) kinetic.put(pos, new Cell(pos, state));
        });
        Cell first = kinetic.get(start);
        if (first == null) return out;
        out.add(start);
        ArrayDeque<Cell> todo = new ArrayDeque<>(List.of(first));
        while (!todo.isEmpty()) {
            Cell c = todo.poll();
            for (Cell n : neighbours(kinetic, c)) if (modifier(world, c, n) != 0 && out.add(n.pos)) todo.add(n);
        }
        return out;
    }

    /**
     * The part of start's line on the cogwheel side of any Simulated Analog Transmission: what start reaches without
     * passing along a transmission's shaft (its cogwheel is the side a redstone signal slows), and whether an engine or
     * other source is in it. A propeller whose part holds a transmission and no source is throttled by that transmission.
     */
    record Throttled(java.util.Set<BlockPos> part, boolean source) {
    }

    static Throttled cogSide(Map<BlockPos, BlockState> blocks, ServerLevel level, BlockPos start) {
        java.util.Set<BlockPos> out = new java.util.LinkedHashSet<>();
        boolean source = false;
        SchematicLevel world = new SchematicLevel(level);
        blocks.forEach((pos, state) -> world.setBlock(pos, state, 0));
        Map<BlockPos, Cell> kinetic = new LinkedHashMap<>();
        blocks.forEach((pos, state) -> {
            if (state.getBlock() instanceof IRotate) kinetic.put(pos, new Cell(pos, state));
        });
        Cell first = kinetic.get(start);
        if (first == null) return new Throttled(out, false);
        out.add(start);
        ArrayDeque<Cell> todo = new ArrayDeque<>(List.of(first));
        while (!todo.isEmpty()) {
            Cell c = todo.poll();
            source |= isSource(c) && !name(c).contains("steering_wheel");
            for (Cell n : neighbours(kinetic, c)) {
                if (alongShaft(c, n) || modifier(world, c, n) == 0 || !out.add(n.pos)) continue;
                todo.add(n);
            }
        }
        return new Throttled(out, source);
    }

    /** Whether a and b touch along an Analog Transmission's shaft (one of them being the transmission). */
    private static boolean alongShaft(Cell a, Cell b) {
        BlockPos d = b.pos.subtract(a.pos);
        if (d.distManhattan(BlockPos.ZERO) != 1) return false;
        Direction.Axis axis = Direction.getNearest(d.getX(), d.getY(), d.getZ()).getAxis();
        for (Cell c : List.of(a, b))
            if (BuiltInRegistries.BLOCK.getKey(c.state.getBlock()).toString().equals("simulated:analog_transmission")
                    && c.state.getValue(BlockStateProperties.AXIS) == axis) return true;
        return false;
    }

    /** The speed a source turns at when nobody has set it: what a player gets on placing it and starting it. */
    private static float nominalRpm(BlockState state) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (id.endsWith("portable_engine")) return 32; // 64 superheated with a Blaze Cake
        if (id.contains("stirling")) return 64;
        if (id.equals("create:large_water_wheel")) return 4;
        if (id.equals("create:water_wheel")) return 8;
        if (id.contains("windmill")) return 4;
        if (id.equals("create:steam_engine")) return 32;
        if (id.equals("create:creative_motor")) return 16;
        return 32;
    }

    private static boolean hasShaftAt(SchematicLevel world, Cell c, Direction d) {
        return ((IRotate) c.state.getBlock()).hasShaftTowards(world, c.pos, c.state, d);
    }

    private static List<Cell> neighbours(Map<BlockPos, Cell> kinetic, Cell c) {
        List<Cell> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Cell n = kinetic.get(c.pos.offset(dx, dy, dz));
                    if (n != null) out.add(n);
                }
        // Belts carry rotation between the shafts at their two ends, however long the belt is.
        if (c.state.getBlock() instanceof BeltBlock) {
            for (Direction d : Direction.values()) {
                BlockPos p = c.pos.relative(d);
                while (kinetic.containsKey(p) && kinetic.get(p).state.getBlock() instanceof BeltBlock) {
                    if (!out.contains(kinetic.get(p))) out.add(kinetic.get(p));
                    p = p.relative(d);
                }
            }
        }
        return out;
    }

    /** Create's RotationPropagator.getRotationSpeedModifier, on block states: 0 when the two don't connect. */
    static float modifier(SchematicLevel world, Cell from, Cell to) {
        return modifier(world, from, to, Map.of());
    }

    /**
     * How fast and which way {@code to} turns for each turn of {@code from}; {@code fed} holds the face each block
     * reached so far took its rotation in through, since a gearbox turns its other faces' shafts each its own way by
     * that face, and a powered gearshift or clutch turns or cuts its far side.
     */
    static float modifier(SchematicLevel world, Cell from, Cell to, Map<BlockPos, Direction> fed) {
        BlockState sf = from.state, st = to.state;
        IRotate df = (IRotate) sf.getBlock(), dt = (IRotate) st.getBlock();
        BlockPos diff = to.pos.subtract(from.pos);
        if (sf.getBlock() instanceof BeltBlock && st.getBlock() instanceof BeltBlock) {
            // Along one belt every segment turns together.
            return sf.getValue(BeltBlock.HORIZONTAL_FACING).getAxis() == st.getValue(BeltBlock.HORIZONTAL_FACING).getAxis() ? 1 : 0;
        }
        Direction dir = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        boolean aligned = diff.distManhattan(BlockPos.ZERO) == 1;
        boolean byAxis = aligned && df.hasShaftTowards(world, from.pos, sf, dir) && dt.hasShaftTowards(world, to.pos, st, dir.getOpposite());
        if (byAxis) {
            float back = axisModifier(st, dir.getOpposite(), fed.get(to.pos));
            return axisModifier(sf, dir, fed.get(from.pos)) * (back == 0 ? 0 : 1 / back);
        }
        if (aligned && sf.getBlock() instanceof ChainDriveBlock && st.getBlock() instanceof ChainDriveBlock)
            return ChainDriveBlock.areBlocksConnected(sf, st, dir) ? chainModifier(sf) / chainModifier(st) : 0;
        if (controller(from, to) != null) return 1;
        if (ICogWheel.isLargeCog(sf) && ICogWheel.isLargeCog(st)) {
            Direction.Axis a = sf.getValue(BlockStateProperties.AXIS), b = st.getValue(BlockStateProperties.AXIS);
            if (a == b) return 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                int v = axis.choose(diff.getX(), diff.getY(), diff.getZ());
                if (axis == a || axis == b ? v == 0 : v != 0) return 0;
            }
            return (a.choose(diff.getX(), diff.getY(), diff.getZ()) > 0) ^ (b.choose(diff.getX(), diff.getY(), diff.getZ()) > 0) ? -1 : 1;
        }
        if (ICogWheel.isLargeCog(sf) && smallCog(st) && largeToSmall(sf, st, dt, diff)) return -2;
        if (ICogWheel.isLargeCog(st) && smallCog(sf) && largeToSmall(st, sf, df, diff)) return -0.5f;
        if (smallCog(sf) && smallCog(st) && aligned && !ICogWheel.isLargeCog(st)
                && dir.getAxis() != df.getRotationAxis(sf) && df.getRotationAxis(sf) == dt.getRotationAxis(st)) return -1;
        return 0;
    }

    /**
     * A small cogwheel, or Simulated's Analog Transmission, whose cogwheel meshes like one. Unpowered its cogwheel turns
     * with its shaft; a redstone signal slows the cogwheel (taken as full throttle here, the speed it gives at signal 0).
     */
    private static boolean smallCog(BlockState state) {
        return ICogWheel.isSmallCog(state) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("simulated:analog_transmission");
    }

    /** Notes the face {@code to} took its rotation in through, when it came straight along a shaft from {@code from}. */
    private static void feed(Map<BlockPos, Direction> fed, Cell to, Cell from) {
        BlockPos d = from.pos.subtract(to.pos);
        if (d.distManhattan(BlockPos.ZERO) == 1) fed.put(to.pos, Direction.getNearest(d.getX(), d.getY(), d.getZ()));
    }

    /** Create's own rule (RotationPropagator.getAxisModifier) for a gearbox, gearshift or clutch turned through {@code fed}. */
    private static float axisModifier(BlockState state, Direction out, Direction fed) {
        if (fed == null) return 1;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (id.equals("create:gearbox"))
            return out.getAxis() == fed.getAxis() ? out == fed ? 1 : -1 : out.getAxisDirection() == fed.getAxisDirection() ? -1 : 1;
        boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        if (out == fed || !powered) return 1;
        if (id.equals("create:gearshift")) return -1;
        if (id.equals("create:clutch")) return 0;
        return 1;
    }

    /** Create's ChainGearshiftBlockEntity.getModifier for a model: a powered Adjustable Chain Gearshift taken at signal 15. */
    private static float chainModifier(BlockState state) {
        return state.getBlock() instanceof ChainGearshiftBlock && state.getValue(ChainGearshiftBlock.POWERED) ? 2 : 1;
    }

    private static boolean largeToSmall(BlockState large, BlockState small, IRotate smallDef, BlockPos diff) {
        Direction.Axis axis = large.getValue(BlockStateProperties.AXIS);
        if (axis != smallDef.getRotationAxis(small) || axis.choose(diff.getX(), diff.getY(), diff.getZ()) != 0) return false;
        for (Direction.Axis a : Direction.Axis.values())
            if (a != axis && Math.abs(a.choose(diff.getX(), diff.getY(), diff.getZ())) != 1) return false;
        return true;
    }

    private static boolean isSource(Cell c) {
        if (!(c.state.getBlock() instanceof EntityBlock eb)) return false;
        try {
            var be = eb.newBlockEntity(c.pos, c.state);
            // A Mechanical Bearing is built on the windmill's block entity but only takes rotation; the windmill makes it.
            if (be instanceof com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
                    && !(be instanceof com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity)) return false;
            return be instanceof GeneratingKineticBlockEntity;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A small water wheel turns when water touches one of the four blocks around its rim. */
    private static boolean wet(Map<BlockPos, BlockState> blocks, Cell c) {
        if (c.state.getBlock() instanceof LargeWaterWheelBlock) return true;
        Direction.Axis axle = ((IRotate) c.state.getBlock()).getRotationAxis(c.state);
        for (Direction d : Direction.values()) {
            if (d.getAxis() == axle) continue;
            BlockState s = blocks.get(c.pos.relative(d));
            if (s != null && s.getFluidState().is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /**
     * Which way a Portable Engine turns its shaft as placed (+1 or -1 along its axis): its speed is set along the way it
     * faces, until its rotation setting is changed. Null for other sources.
     */
    private static Integer spin(Cell c) {
        if (!name(c).contains("portable_engine") || !c.state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        return c.state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
    }

    /** The faces a source drives through, named for the player. */
    private static String outputs(SchematicLevel world, Cell c) {
        List<String> faces = new ArrayList<>();
        for (Direction d : Direction.values()) if (hasShaftAt(world, c, d)) faces.add(face(d, c.state));
        return faces.isEmpty() ? "its cog teeth" : "its " + String.join(" and ", faces);
    }

    /**
     * A facing that would join an idle source to the blocks around it, said as the fix; empty when no turn of it in place
     * connects (then it has to move: an engine sits at the end of a shaft or against a cog's axle, with its front to it).
     */
    private static String turnTo(SchematicLevel world, Map<BlockPos, Cell> kinetic, Cell src) {
        var facing = src.state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? BlockStateProperties.HORIZONTAL_FACING
                : src.state.hasProperty(BlockStateProperties.FACING) ? BlockStateProperties.FACING : null;
        if (facing != null) {
            for (Direction d : facing.getPossibleValues()) {
                if (d == src.state.getValue(facing)) continue;
                Cell turned = new Cell(src.pos, src.state.setValue(facing, d));
                for (Cell n : neighbours(kinetic, turned))
                    if (modifier(world, turned, n) != 0)
                        return ": turn it to face " + face(d, turned.state).replace(" face (its front)", "") + ", toward " + name(n)
                                + ", or move it so its front meets a shaft end or a cog's axle";
            }
        }
        return ": move it so its output meets a shaft end or a cogwheel's axle (two engines can share one shaft from its two "
                + "ends, or each drive its own shaft into the same cogwheel train), with nothing but the drive line in front of it";
    }

    private static String relative(float s) {
        String way = s > 0 ? "same direction" : "opposite direction";
        float a = Math.abs(s);
        String rate = Math.abs(a - 1) < 1e-3 ? "same speed" : (a > 1 ? "x" + trim(a) + " speed" : "1/" + trim(1 / a) + " speed");
        return "(" + way + ", " + rate + ")";
    }

    private static String trim(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(f);
    }

    private static String takes(SchematicLevel world, Cell c) {
        List<String> faces = new ArrayList<>();
        for (Direction d : Direction.values()) if (hasShaftAt(world, c, d)) faces.add(face(d, c.state));
        return faces.isEmpty() ? " (it only connects by its cog teeth)" : " (it takes rotation through its " + String.join(" and ", faces) + ")";
    }

    /** A face by compass and axis (north is -z, the model's back), plus "its back/front" for a block with a facing,
     *  since which face takes rotation follows the facing (a saw facing north takes its shaft at its back, south). */
    static String face(Direction d, BlockState state) {
        String compass = switch (d) {
            case NORTH -> "north (-z)";
            case SOUTH -> "south (+z)";
            case WEST -> "west (-x)";
            case EAST -> "east (+x)";
            case UP -> "top (+y)";
            case DOWN -> "bottom (-y)";
        };
        Direction facing = state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING)
                : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
        return compass + " face" + (facing == d ? " (its front)" : facing == d.getOpposite() ? " (its back)" : "");
    }

    static String name(Cell c) {
        return BuiltInRegistries.BLOCK.getKey(c.state.getBlock()) + " at " + c.pos.getX() + "," + c.pos.getY() + "," + c.pos.getZ();
    }
}

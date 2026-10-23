package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
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
 * chain drives and belts. It reports what turns with which source, at what speed and direction, and the faults a
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
        try {
            return run(blocks, level);
        } catch (RuntimeException | LinkageError e) {
            return new Report("(The rotation check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    private static Report run(Map<BlockPos, BlockState> blocks, ServerLevel level) {
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

        // Follow rotation out from every source, the way Create spreads it: relative speed 1 at the source.
        Map<BlockPos, Float> speed = new LinkedHashMap<>();
        Map<BlockPos, BlockPos> sourceOf = new LinkedHashMap<>();
        List<Cell> sources = new ArrayList<>();
        java.util.Set<BlockPos> conflicts = new java.util.LinkedHashSet<>();
        for (Cell c : kinetic.values()) if (isSource(c)) sources.add(c);
        for (Cell src : sources) {
            if (speed.containsKey(src.pos)) {
                notes.add(name(src) + " drives the same network as " + name(kinetic.get(sourceOf.get(src.pos)))
                        + "; sources on one network must turn it at the same speed and direction, or it breaks");
                continue;
            }
            speed.put(src.pos, 1f);
            sourceOf.put(src.pos, src.pos);
            ArrayDeque<Cell> todo = new ArrayDeque<>(List.of(src));
            while (!todo.isEmpty()) {
                Cell c = todo.poll();
                for (Cell n : neighbours(kinetic, c)) {
                    float m = modifier(world, c, n);
                    if (m == 0) continue;
                    float s = speed.get(c.pos) * m;
                    Float had = speed.get(n.pos);
                    if (had == null) {
                        speed.put(n.pos, s);
                        sourceOf.put(n.pos, src.pos);
                        todo.add(n);
                    } else if (Math.abs(had - s) > 1e-3) {
                        conflicts.add(n.pos);
                    }
                }
            }
        }
        for (BlockPos p : conflicts) {
            faults.add(name(kinetic.get(p)) + " is reached at two different speeds or directions, so the network would "
                    + "break (Create pops a block off): gear ratios or a loop of cogs disagree");
        }

        for (Cell c : kinetic.values()) {
            if (c.state.getBlock() instanceof WaterWheelBlock && !wet(blocks, c)) {
                faults.add(name(c) + " has no water on it in the model, so it won't turn; water must flow against "
                        + "its rim (the blocks around it, not along its axle)");
            }
        }

        StringBuilder out = new StringBuilder("How these blocks turn and connect" + BuildCheck.FOR_YOU + ":\n");
        if (sources.isEmpty()) {
            out.append("- No rotation source in the model (water wheel, windmill, motor, engine, crank). Machines are listed "
                    + "by what they connect to.\n");
        }
        for (Cell src : sources) {
            out.append("- ").append(name(src)).append(" is a source. Turning with it:");
            int n = 0;
            for (Map.Entry<BlockPos, BlockPos> e : sourceOf.entrySet()) {
                if (!e.getValue().equals(src.pos) || e.getKey().equals(src.pos)) continue;
                if (n++ > 0) out.append(';');
                out.append(' ').append(name(kinetic.get(e.getKey()))).append(' ').append(relative(speed.get(e.getKey())));
            }
            out.append(n == 0 ? " nothing.\n" : ".\n");
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

    /**
     * Each kinetic block's speed in RPM when every source in a model turns at its usual speed (a model holds no
     * settings): engines and motors at their usual output, water wheels at their usual flow. {@code assumed} gets one
     * line per source saying the speed used.
     */
    static Map<BlockPos, Float> rpm(Map<BlockPos, BlockState> blocks, ServerLevel level, List<String> assumed) {
        Map<BlockPos, Float> out = new LinkedHashMap<>();
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
                        float m = modifier(world, c, n);
                        if (m == 0 || out.containsKey(n.pos)) continue;
                        out.put(n.pos, out.get(c.pos) * m);
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
        if (byAxis) return 1;
        if (aligned && sf.getBlock() instanceof ChainDriveBlock && st.getBlock() instanceof ChainDriveBlock)
            return ChainDriveBlock.areBlocksConnected(sf, st, dir) ? 1 : 0;
        if (ICogWheel.isLargeCog(sf) && ICogWheel.isLargeCog(st)) {
            Direction.Axis a = sf.getValue(BlockStateProperties.AXIS), b = st.getValue(BlockStateProperties.AXIS);
            if (a == b) return 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                int v = axis.choose(diff.getX(), diff.getY(), diff.getZ());
                if (axis == a || axis == b ? v == 0 : v != 0) return 0;
            }
            return (a.choose(diff.getX(), diff.getY(), diff.getZ()) > 0) ^ (b.choose(diff.getX(), diff.getY(), diff.getZ()) > 0) ? -1 : 1;
        }
        if (ICogWheel.isLargeCog(sf) && ICogWheel.isSmallCog(st) && largeToSmall(sf, st, dt, diff)) return -2;
        if (ICogWheel.isLargeCog(st) && ICogWheel.isSmallCog(sf) && largeToSmall(st, sf, df, diff)) return -0.5f;
        if (ICogWheel.isSmallCog(sf) && ICogWheel.isSmallCog(st) && aligned && !ICogWheel.isLargeCog(st)
                && dir.getAxis() != df.getRotationAxis(sf) && df.getRotationAxis(sf) == dt.getRotationAxis(st)) return -1;
        return 0;
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

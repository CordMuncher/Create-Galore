package dev.creategalore.bridge.guideai;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Runs Create's own rules on the live machines of a build, so the book reads what the machine really does instead of
 * working it out. For each Encased Fan: how far its air reaches right now (Create's AirCurrent, which already stopped at
 * the first block in the way), the processing in each cell, the belts and depots the air sweeps, and for a belt line how
 * long an item stays in the air against the time the processing needs (the server's fanProcessingTime). An item on a
 * belt keeps its progress from one fan's air into the next of the same kind (Create's FanProcessing only starts over
 * when a different kind of air takes it), and a belt hands it to the next belt with that progress, so a line is judged
 * as a whole: the time in every fan's air of one kind adds up, gaps of plain air included.
 */
final class MachineCheck {
    /** A belt hands an item to the fan while it is within this far of the segment's centre (Create's 0.51). */
    private static final float WINDOW = 1.02f;
    /** Belt items move speed/480 blocks a tick (BeltBlockEntity.getBeltMovementSpeed). */
    private static final float BELT_DIVISOR = 480f;
    private static final int MAX_FANS = 8;
    /** A belt line is followed this far, belt to belt. */
    private static final int MAX_LINE = 256;

    /** One fan's air on one belt segment. */
    private record Hit(BlockPos fan, FanProcessingType type) {
    }

    /** One stretch of a belt line, in the order an item rides it. */
    private record Step(BeltBlockEntity controller, int index, float rpm) {
    }

    private MachineCheck() {
    }

    /** Empty when the build has no machine these checks cover. */
    static String check(ServerLevel level, List<BlockPos> build) {
        return check(level, build, build);
    }

    /** As above, with the vehicle check run on {@code whole}: all of a ship whose listing was cut short. */
    static String check(ServerLevel level, List<BlockPos> build, List<BlockPos> whole) {
        StringBuilder out = new StringBuilder();
        Map<BlockPos, Map<Integer, List<Hit>>> air = new LinkedHashMap<>(); // belt controller -> segment -> fans' air on it
        int fans = 0;
        for (BlockPos p : build) {
            if (!(level.getBlockEntity(p) instanceof EncasedFanBlockEntity fan)) continue;
            if (fans++ >= MAX_FANS) {
                out.append("- ...more fans left out\n");
                break;
            }
            fan(out, level, p, fan, air);
        }
        lines(out, level, air);
        String machines = out.isEmpty() ? "" : "How these machines work as they are now" + BuildCheck.FOR_YOU + ":\n" + out;
        return machines + OutputCheck.live(level, build) + vehicle(level, whole);
    }

    /** The vehicle check on the build's blocks as they stand, with each part turning as fast as it does now. */
    private static String vehicle(ServerLevel level, List<BlockPos> build) {
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos p : build) {
            var s = level.getBlockState(p);
            if (!s.isAir()) blocks.put(p.immutable(), s);
        }
        Map<BlockPos, Float> rpm = new LinkedHashMap<>();
        boolean turning = false;
        for (BlockPos p : blocks.keySet()) {
            if (level.getBlockEntity(p) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k) {
                rpm.put(p, k.getSpeed());
                turning |= k.getSpeed() != 0;
            }
        }
        String still = "";
        if (!turning && !rpm.isEmpty()) {
            // Engines off (or a ship not yet started): judge it at the speeds its engines give once running.
            List<String> assumed = new ArrayList<>();
            // A speed controller in the world keeps the speed set on its panel.
            Map<BlockPos, Float> set = new LinkedHashMap<>();
            for (BlockPos p : blocks.keySet())
                if (level.getBlockEntity(p) instanceof com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity c)
                    set.put(p, (float) c.targetSpeed.getValue());
            rpm = BuildCheck.rpm(blocks, level, assumed, set);
            if (!assumed.isEmpty()) still = "- Nothing turns right now, so speeds are worked out with " + String.join(", ", assumed) + ".\n";
        }
        BuildCheck.Report r = VehicleCheck.check(blocks, level, rpm, false);
        if (r == null) return "";
        String text = r.text();
        int nl = text.indexOf('\n');
        return "\n" + (nl < 0 ? text : text.substring(0, nl + 1) + still + text.substring(nl + 1)) + "\n";
    }

    private static void fan(StringBuilder out, ServerLevel level, BlockPos pos, EncasedFanBlockEntity fan,
            Map<BlockPos, Map<Integer, List<Hit>>> air) {
        float speed = fan.getSpeed();
        out.append("- Encased Fan ").append(Vision.xyz(pos)).append(": ");
        if (speed == 0) {
            out.append("not turning, so no air moves.\n");
            return;
        }
        AirCurrent current = fan.airCurrent;
        Direction dir = fan.getAirflowOriginSide();
        if (dir == null) {
            out.append("no air direction yet.\n");
            return;
        }
        boolean pushing = fan.getAirFlowDirection() == dir;
        float full = fan.getMaxDistance();
        out.append(String.format(Locale.ROOT, "%s RPM, %s air %s; at this speed the air reaches %.1f blocks", fmt(speed),
                pushing ? "blowing" : "pulling", dir.getName(), full));
        if (current.maxDistance + 0.01f < full) {
            BlockPos stop = pos.relative(dir, (int) Math.floor(current.maxDistance) + 1);
            out.append(String.format(Locale.ROOT, ", but it stops after %.1f, at the %s at %s", current.maxDistance,
                    level.getBlockState(stop).getBlock().getName().getString(), Vision.xyz(stop)));
        }
        var kinetics = AllConfigs.server().kinetics;
        int far = (pushing ? kinetics.fanPushDistance : kinetics.fanPullDistance).get(), argmax = kinetics.fanRotationArgmax.get();
        out.append(String.format(Locale.ROOT, ". Turning faster only reaches further (3 blocks plus %d x RPM/%d more, up to %d at %d RPM); "
                + "it doesn't process any faster.\n", far - 3, argmax, far, argmax));
        int limit = (int) Math.ceil(current.maxDistance);
        List<String> cells = new ArrayList<>();
        for (int i = 1; i <= limit; i++) cells.add(i + " " + type(current.getTypeAt(i - 1)));
        if (!cells.isEmpty()) out.append("  Air in each cell out from the fan: ").append(String.join(", ", cells)).append(".\n");
        else out.append("  The block right in front of it stops the air.\n");

        // The same cells Create's AirCurrent.findAffectedHandlers looks at: each cell and the one below it (sideways air).
        Map<BlockPos, int[]> belts = new LinkedHashMap<>(); // belt controller -> segments in the air
        Map<BlockPos, FanProcessingType> beltType = new LinkedHashMap<>();
        Map<BlockPos, List<Integer>> beltSegments = new LinkedHashMap<>();
        for (int i = 1; i <= limit; i++) {
            FanProcessingType segment = current.getTypeAt(i - 1);
            for (int below = 0; below <= 1; below++) {
                BlockPos at = pos.relative(dir, i).below(below);
                TransportedItemStackHandlerBehaviour holder = BlockEntityBehaviour.get(level, at, TransportedItemStackHandlerBehaviour.TYPE);
                if (holder != null) {
                    FanProcessingType here = FanProcessingType.getAt(level, at);
                    if (here == null) here = segment;
                    BlockEntity be = level.getBlockEntity(at);
                    if (be instanceof BeltBlockEntity belt && belt.getController() != null) {
                        BlockPos controller = belt.getController();
                        belts.computeIfAbsent(controller, k -> new int[1])[0]++;
                        beltType.putIfAbsent(controller, here);
                        beltSegments.computeIfAbsent(controller, k -> new ArrayList<>()).add(belt.index);
                    } else {
                        out.append("  It reaches ").append(level.getBlockState(at).getBlock().getName().getString()).append(' ')
                                .append(Vision.xyz(at)).append(" with ").append(type(here))
                                .append("; items there sit still, so they stay in the air until done.\n");
                    }
                }
                if (dir.getAxis().isVertical()) break;
            }
        }
        belts.forEach((controller, count) -> {
            if (!(level.getBlockEntity(controller) instanceof BeltBlockEntity belt)) return;
            FanProcessingType type = beltType.get(controller);
            out.append("  It reaches a belt (").append(count[0]).append(count[0] == 1 ? " segment" : " segments").append(" in the air, at ")
                    .append(segments(level, controller, beltSegments.get(controller))).append(") with ").append(type(type)).append(". ");
            if (type == null) {
                out.append("Plain air processes nothing.\n");
                return;
            }
            out.append("How long items stay in the air is under Belt lines below.\n");
            for (int index : beltSegments.get(controller)) {
                air.computeIfAbsent(controller, k -> new LinkedHashMap<>()).computeIfAbsent(index, k -> new ArrayList<>()).add(new Hit(pos, type));
            }
            List<String> riding = new ArrayList<>();
            for (TransportedItemStack t : belt.getInventory().getTransportedItems()) {
                if (riding.size() >= 4) break;
                boolean inAir = false;
                for (int index : beltSegments.get(controller)) inAir |= Math.abs(index + 0.5f - t.beltPosition) < WINDOW / 2;
                if (!inAir) continue;
                riding.add(t.stack.getCount() + "x " + t.stack.getHoverName().getString() + riding(t, type, level));
            }
            if (!riding.isEmpty()) out.append("    In the air on it now: ").append(String.join(", ", riding)).append(".\n");
        });
    }

    /** Where an item in the air stands: no recipe here, or how far along it is. */
    private static String riding(TransportedItemStack t, FanProcessingType type, ServerLevel level) {
        if (!type.canProcess(t.stack, level)) return " (this air has no recipe for it)";
        if (t.processedBy != type) return " (just reached this air)";
        if (t.processingTime < 0) return "";
        return String.format(Locale.ROOT, " (%.1f s of %s left)", (t.processingTime + 1) / 20f, type(type));
    }

    /**
     * Each belt line a fan's air reaches, ridden start to end the way Create moves items: the time in the air of one
     * kind adds up across fans and plain-air gaps, a different kind starts it over, and belt-to-belt hand-overs keep it.
     */
    private static void lines(StringBuilder out, ServerLevel level, Map<BlockPos, Map<Integer, List<Hit>>> air) {
        if (air.isEmpty()) return;
        // A line starts at a belt no other reached belt feeds into, so a line fed by another is told as part of that one.
        Map<BlockPos, List<Step>> rides = new LinkedHashMap<>();
        for (BlockPos c : air.keySet()) rides.put(c, ride(level, c, air.get(c)));
        Set<BlockPos> fed = new HashSet<>();
        for (var e : rides.entrySet()) {
            for (Step s : e.getValue()) {
                BlockPos c = s.controller().getBlockPos();
                if (!c.equals(e.getKey()) && air.containsKey(c)) fed.add(c);
            }
        }
        int fanTime = AllConfigs.server().kinetics.fanProcessingTime.get();
        out.append("- Belt lines (an item keeps its progress from one fan's air into the next of the same kind, also across "
                + "gaps and from belt to belt; only a different kind of air starts it over). ")
                .append(String.format(Locale.ROOT, "Processing takes %.1f s for a stack of up to 16 (twice that up to 32, three "
                        + "times up to 48, four times up to 64):\n", (fanTime + 2) / 20f));
        // Belts that all feed each other in a loop: tell the loop once, from the first.
        boolean loop = fed.containsAll(rides.keySet());
        BlockPos firstBelt = rides.keySet().iterator().next();
        for (var e : rides.entrySet()) {
            if (fed.contains(e.getKey()) && !(loop && e.getKey().equals(firstBelt))) continue;
            line(out, level, e.getValue(), air, fanTime);
        }
    }

    /** The segments an item rides from the first air on this belt to the end of the line, belt to belt. */
    private static List<Step> ride(ServerLevel level, BlockPos controller, Map<Integer, List<Hit>> hits) {
        List<Step> steps = new ArrayList<>();
        if (!(level.getBlockEntity(controller) instanceof BeltBlockEntity belt)) return steps;
        boolean positive = belt.getDirectionAwareBeltMovementSpeed() >= 0;
        int from = positive ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (int i : hits.keySet()) from = positive ? Math.min(from, i) : Math.max(from, i);
        Set<BlockPos> seen = new HashSet<>();
        while (belt != null && seen.add(belt.getBlockPos()) && steps.size() < MAX_LINE) {
            float rpm = Math.abs(belt.getSpeed());
            positive = belt.getDirectionAwareBeltMovementSpeed() >= 0;
            int length = belt.beltLength;
            for (int i = from; i >= 0 && i < length && steps.size() < MAX_LINE; i += positive ? 1 : -1) steps.add(new Step(belt, i, rpm));
            if (rpm == 0) break;
            // Belt.Ending.INSERT: the end of the belt hands the item, as it is, to the belt in front of it.
            BlockPos next = com.simibubi.create.content.kinetics.belt.BeltHelper.getPositionForOffset(belt, positive ? length : -1);
            belt = null;
            if (level.getBlockEntity(next) instanceof BeltBlockEntity n && n.getController() != null
                    && level.getBlockEntity(n.getController()) instanceof BeltBlockEntity nc) {
                belt = nc;
                from = n.index;
            }
        }
        return steps;
    }

    /** One line: each stretch of one kind of air, the time an item spends in it, and whether that is enough. */
    private static void line(StringBuilder out, ServerLevel level, List<Step> steps, Map<BlockPos, Map<Integer, List<Hit>>> air, int fanTime) {
        if (steps.isEmpty()) return;
        Step first = steps.get(0), last = steps.get(steps.size() - 1);
        Set<BlockPos> belts = new LinkedHashSet<>();
        for (Step s : steps) belts.add(s.controller().getBlockPos());
        out.append("  Line from ").append(Vision.xyz(position(first))).append(" to ").append(Vision.xyz(position(last)))
                .append(belts.size() == 1 ? " (one belt)" : " (" + belts.size() + " belts, each handing items to the next)").append(":\n");
        FanProcessingType type = null;
        float ticks = 0, blocks = 0;
        Set<BlockPos> fans = new LinkedHashSet<>();
        Set<Float> rpms = new LinkedHashSet<>();
        boolean stopped = false, mixed = false;
        int runs = 0;
        for (int k = 0; k <= steps.size(); k++) {
            Step s = k < steps.size() ? steps.get(k) : null;
            List<Hit> hits = s == null ? List.of() : air.getOrDefault(s.controller().getBlockPos(), Map.of()).getOrDefault(s.index(), List.of());
            Set<FanProcessingType> kinds = new LinkedHashSet<>();
            for (Hit h : hits) kinds.add(h.type());
            if (kinds.size() > 1) mixed = true;
            boolean ends = s == null || (kinds.size() == 1 && kinds.iterator().next() != type) || kinds.size() > 1;
            if (ends && type != null) {
                if (runs++ >= 6) {
                    out.append("    ...more stretches left out\n");
                    return;
                }
                run(out, type, ticks, blocks, fans, rpms, stopped, fanTime, s == null);
                type = null;
            }
            if (s == null) break;
            if (kinds.size() > 1) {
                out.append("    At ").append(Vision.xyz(position(s))).append(" two kinds of air (");
                List<String> names = new ArrayList<>();
                for (FanProcessingType t : kinds) names.add(type(t));
                out.append(String.join(" and ", names)).append(") hit the same segment and start each other over every tick, "
                        + "so nothing is processed there.\n");
                continue;
            }
            if (kinds.size() == 1 && type == null) {
                type = kinds.iterator().next();
                ticks = 0;
                blocks = WINDOW - 1; // an item is caught 0.51 before the first segment's centre
                if (s.rpm() > 0) ticks = (WINDOW - 1) * BELT_DIVISOR / s.rpm();
                fans.clear();
                rpms.clear();
                stopped = false;
            }
            if (type == null) continue;
            // Plain air or no air between fans of one kind: the item keeps its progress, it just gains none.
            if (hits.isEmpty()) continue;
            for (Hit h : hits) fans.add(h.fan());
            rpms.add(s.rpm());
            blocks += 1;
            if (s.rpm() == 0) stopped = true;
            // Each fan whose air covers the segment counts the item's time down once a tick.
            else ticks += hits.size() * BELT_DIVISOR / s.rpm();
        }
    }

    private static void run(StringBuilder out, FanProcessingType type, float ticks, float blocks, Set<BlockPos> fans,
            Set<Float> rpms, boolean stopped, int fanTime, boolean lineEnd) {
        List<String> at = new ArrayList<>();
        for (BlockPos f : fans) at.add(Vision.xyz(f));
        out.append("    ").append(type(type)).append(String.format(Locale.ROOT, " from %d %s (%s) over %.0f %s",
                fans.size(), fans.size() == 1 ? "fan" : "fans", String.join("; ", at), Math.floor(blocks + 0.5f),
                Math.floor(blocks + 0.5f) == 1 ? "segment" : "segments"));
        List<String> speeds = new ArrayList<>();
        for (float r : rpms) speeds.add(fmt(r) + " RPM");
        out.append(", belt at ").append(String.join(" and ", speeds)).append(": ");
        if (stopped) {
            out.append("a belt there is stopped, so items stay in this air until done.\n");
            return;
        }
        out.append(String.format(Locale.ROOT, "an item spends %.1f s in it in all. ", ticks / 20f));
        int tiers = 0;
        while (tiers < 4 && ticks >= fanTime * (tiers + 1) + 2) tiers++;
        if (tiers > 0) {
            out.append("Items of up to ").append(16 * tiers).append(" come out done");
        } else {
            out.append("Items leave it before they are done, so they come out unprocessed");
            if (rpms.size() == 1) {
                int maxRpm = (int) Math.floor(rpms.iterator().next() * ticks / (fanTime + 2));
                out.append("; single items finish with the belt at ").append(maxRpm == 0 ? "under 1" : String.valueOf(maxRpm))
                        .append(" RPM or slower, or with more fans of this kind along the same line");
            } else {
                out.append(String.format(Locale.ROOT, "; they need %.1fx the time: slow the belts or add fans of this kind along the line",
                        (fanTime + 2) / ticks));
            }
        }
        out.append(lineEnd ? ".\n" : ". A different kind of air comes next and starts the item over.\n");
    }

    private static BlockPos position(Step s) {
        return com.simibubi.create.content.kinetics.belt.BeltHelper.getPositionForOffset(s.controller(), s.index());
    }

    private static String segments(ServerLevel level, BlockPos controller, List<Integer> indexes) {
        if (!(level.getBlockEntity(controller) instanceof BeltBlockEntity c)) return "?";
        List<String> at = new ArrayList<>();
        for (int index : indexes) {
            BlockPos p = com.simibubi.create.content.kinetics.belt.BeltHelper.getPositionForOffset(c, index);
            at.add(Vision.xyz(p));
        }
        return String.join("; ", at);
    }

    private static String type(FanProcessingType type) {
        if (type == null) return "plain air";
        ResourceLocation id = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
        return (id == null ? "processing" : id.getPath()) + " air";
    }

    private static String fmt(float rpm) {
        return rpm == Math.rint(rpm) ? String.valueOf((int) rpm) : String.format(Locale.ROOT, "%.1f", rpm);
    }
}

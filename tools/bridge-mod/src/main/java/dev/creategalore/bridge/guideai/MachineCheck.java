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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Runs Create's own rules on the live machines of a build, so the book reads what the machine really does instead of
 * working it out. For each Encased Fan: how far its air reaches right now (Create's AirCurrent, which already stopped at
 * the first block in the way), the processing in each cell, the belts and depots the air sweeps, and for a belt how
 * long an item stays in the air against the time the processing needs (the server's fanProcessingTime).
 */
final class MachineCheck {
    /** A belt hands an item to the fan while it is within this far of the segment's centre (Create's 0.51). */
    private static final float WINDOW = 1.02f;
    /** Belt items move speed/480 blocks a tick (BeltBlockEntity.getBeltMovementSpeed). */
    private static final float BELT_DIVISOR = 480f;
    private static final int MAX_FANS = 8;

    private MachineCheck() {
    }

    /** Empty when the build has no machine these checks cover. */
    static String check(ServerLevel level, List<BlockPos> build) {
        StringBuilder out = new StringBuilder();
        int fans = 0;
        for (BlockPos p : build) {
            if (!(level.getBlockEntity(p) instanceof EncasedFanBlockEntity fan)) continue;
            if (fans++ >= MAX_FANS) {
                out.append("- ...more fans left out\n");
                break;
            }
            fan(out, level, p, fan);
        }
        String machines = out.isEmpty() ? "" : "How these machines work as they are now" + BuildCheck.FOR_YOU + ":\n" + out;
        return machines + OutputCheck.live(level, build) + vehicle(level, build);
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
            rpm = BuildCheck.rpm(blocks, level, assumed);
            if (!assumed.isEmpty()) still = "- Nothing turns right now, so speeds are worked out with " + String.join(", ", assumed) + ".\n";
        }
        BuildCheck.Report r = VehicleCheck.check(blocks, level, rpm, false);
        if (r == null) return "";
        String text = r.text();
        int nl = text.indexOf('\n');
        return "\n" + (nl < 0 ? text : text.substring(0, nl + 1) + still + text.substring(nl + 1)) + "\n";
    }

    private static void fan(StringBuilder out, ServerLevel level, BlockPos pos, EncasedFanBlockEntity fan) {
        float speed = fan.getSpeed();
        out.append("- Encased Fan ").append(Vision.xyz(pos)).append(": ");
        if (speed == 0) {
            out.append("not turning, so no air moves.\n");
            return;
        }
        AirCurrent air = fan.airCurrent;
        Direction dir = fan.getAirflowOriginSide();
        if (dir == null) {
            out.append("no air direction yet.\n");
            return;
        }
        boolean pushing = fan.getAirFlowDirection() == dir;
        float full = fan.getMaxDistance();
        out.append(String.format(Locale.ROOT, "%s RPM, %s air %s; at this speed the air reaches %.1f blocks", fmt(speed),
                pushing ? "blowing" : "pulling", dir.getName(), full));
        if (air.maxDistance + 0.01f < full) {
            BlockPos stop = pos.relative(dir, (int) Math.floor(air.maxDistance) + 1);
            out.append(String.format(Locale.ROOT, ", but it stops after %.1f, at the %s at %s", air.maxDistance,
                    level.getBlockState(stop).getBlock().getName().getString(), Vision.xyz(stop)));
        }
        var kinetics = AllConfigs.server().kinetics;
        int far = (pushing ? kinetics.fanPushDistance : kinetics.fanPullDistance).get(), argmax = kinetics.fanRotationArgmax.get();
        out.append(String.format(Locale.ROOT, ". Turning faster only reaches further (3 blocks plus %d x RPM/%d more, up to %d at %d RPM); "
                + "it doesn't process any faster.\n", far - 3, argmax, far, argmax));
        int limit = (int) Math.ceil(air.maxDistance);
        List<String> cells = new ArrayList<>();
        for (int i = 1; i <= limit; i++) cells.add(i + " " + type(air.getTypeAt(i - 1)));
        if (!cells.isEmpty()) out.append("  Air in each cell out from the fan: ").append(String.join(", ", cells)).append(".\n");
        else out.append("  The block right in front of it stops the air.\n");

        // The same cells Create's AirCurrent.findAffectedHandlers looks at: each cell and the one below it (sideways air).
        Map<BlockPos, int[]> belts = new LinkedHashMap<>(); // belt controller -> segments in the air
        Map<BlockPos, FanProcessingType> beltType = new LinkedHashMap<>();
        Map<BlockPos, List<Integer>> beltSegments = new LinkedHashMap<>();
        for (int i = 1; i <= limit; i++) {
            FanProcessingType segment = air.getTypeAt(i - 1);
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
        int needed = AllConfigs.server().kinetics.fanProcessingTime.get() + 2; // set to time+1, then counted down past 0
        belts.forEach((controller, count) -> {
            if (!(level.getBlockEntity(controller) instanceof BeltBlockEntity belt)) return;
            FanProcessingType type = beltType.get(controller);
            float beltRpm = belt.getSpeed();
            out.append("  It reaches a belt (").append(count[0]).append(count[0] == 1 ? " segment" : " segments").append(" in the air, at ")
                    .append(segments(level, controller, beltSegments.get(controller))).append(") with ").append(type(type)).append(". ");
            if (type == null) {
                out.append("Plain air processes nothing.\n");
                return;
            }
            float window = count[0] - 1 + WINDOW;
            if (beltRpm == 0) {
                out.append("The belt is stopped, so items on it stay in the air until done.\n");
            } else {
                float ticks = window * BELT_DIVISOR / Math.abs(beltRpm);
                int maxRpm = (int) Math.floor(window * BELT_DIVISOR / needed);
                out.append(String.format(Locale.ROOT, "The belt turns at %s RPM, moving items %.2f blocks a second, so an item is in "
                        + "the air for %.1f s. Processing takes %.1f s for a stack of up to 16 (twice that up to 32, three times "
                        + "up to 48, four times up to 64). ", fmt(beltRpm), Math.abs(beltRpm) / BELT_DIVISOR * 20f, ticks / 20f, needed / 20f));
                if (ticks >= needed) out.append("Items of up to 16 finish before they leave the air.");
                else out.append(String.format(Locale.ROOT, "Items leave the air before they are done, so they come off unprocessed; "
                        + "with %d %s in the air, single items finish only with the belt at %s RPM or slower.", count[0],
                        count[0] == 1 ? "segment" : "segments", maxRpm == 0 ? "under 1" : String.valueOf(maxRpm)));
                out.append('\n');
            }
            List<String> riding = new ArrayList<>();
            for (TransportedItemStack t : belt.getInventory().getTransportedItems()) {
                if (riding.size() >= 4) break;
                boolean inAir = false;
                for (int index : beltSegments.get(controller)) inAir |= Math.abs(index + 0.5f - t.beltPosition) < WINDOW / 2;
                if (!inAir) continue;
                riding.add(t.stack.getCount() + "x " + t.stack.getHoverName().getString()
                        + (type.canProcess(t.stack, level) ? "" : " (this air has no recipe for it)"));
            }
            if (!riding.isEmpty()) out.append("    In the air on it now: ").append(String.join(", ", riding)).append(".\n");
        });
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

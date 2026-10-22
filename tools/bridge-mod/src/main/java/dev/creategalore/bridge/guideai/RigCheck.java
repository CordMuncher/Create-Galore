package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Checks a model's sailing rig against how Aeronautics: Winds & Weather places and counts its blocks, the way
 * BuildCheck does for Create's machines. A Windcatcher panel stands upright only with a sideways facing (its default,
 * facing up, lies flat and catches no wind); a Yard runs along its axis and joins only the Masts and Yards at its two
 * ends; the Sail Kit hangs cloth straight below a run of Yards, in the Yard's own plane, facing across it.
 */
final class RigCheck {
    private static final ResourceLocation WINDCATCHER = id("windcatcher_canvas"), YARD = id("yard"), MAST = id("mast"),
            WINDMILL_BEARING = ResourceLocation.fromNamespaceAndPath("create", "windmill_bearing");
    private static final List<ResourceLocation> CLOTH = List.of(id("cotton_sail_cloth"), id("canvas_sail_cloth"),
            id("cotton_sail_tack"), id("canvas_sail_tack"));

    private RigCheck() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aeronauticswinds", path);
    }

    /** Null when the model has no rigging in it. */
    static BuildCheck.Report check(Map<BlockPos, BlockState> blocks) {
        List<String> faults = new ArrayList<>(), notes = new ArrayList<>();
        boolean rigging = false, windmill = blocks.values().stream().anyMatch(s -> is(s, WINDMILL_BEARING));
        List<BlockPos> flat = new ArrayList<>(), flatCloth = new ArrayList<>(), unhung = new ArrayList<>(),
                upright = new ArrayList<>(), crossed = new ArrayList<>(), loose = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            BlockState s = e.getValue();
            if (is(s, WINDCATCHER)) {
                rigging = true;
                if (!windmill && s.getValue(BlockStateProperties.FACING).getAxis() == Direction.Axis.Y) flat.add(p);
            } else if (isCloth(s)) {
                rigging = true;
                Direction facing = s.getValue(BlockStateProperties.FACING);
                if (facing.getAxis() == Direction.Axis.Y) flatCloth.add(p);
                else if (!hangsBelowYard(blocks, p, facing)) unhung.add(p);
            } else if (is(s, YARD)) {
                rigging = true;
                Direction.Axis axis = s.getValue(BlockStateProperties.AXIS);
                if (axis == Direction.Axis.Y) {
                    upright.add(p);
                    continue;
                }
                boolean negative = s.getValue(bool(s, "negative")), positive = s.getValue(bool(s, "positive"));
                if (negative || positive) continue;
                // A lone Yard with another Yard beside it, across its axis: the run was written turned the wrong way.
                Direction across = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
                if (is(blocks.get(p.relative(across)), YARD) || is(blocks.get(p.relative(across.getOpposite())), YARD)) crossed.add(p);
                else loose.add(p);
            } else if (is(s, MAST)) {
                rigging = true;
            }
        }
        if (!rigging) return null;
        if (!flat.isEmpty()) faults.add("aeronauticswinds:windcatcher_canvas lies flat (facing=up or down) at " + list(flat)
                + ", so it catches no wind. A panel stands upright with a sideways facing: facing is the way its broad face "
                + "points, so a sail across a ship that runs along z faces north or south, and panels side by side with the "
                + "same facing join into one sail. Write facing on every panel");
        if (!flatCloth.isEmpty()) faults.add("sail cloth lies flat (facing=up or down) at " + list(flatCloth) + "; the Sail Kit "
                + "only hangs cloth upright, facing across the Yard above it");
        if (!unhung.isEmpty()) faults.add("sail cloth at " + list(unhung) + " doesn't hang below a Yard: the Sail Kit fills the "
                + "cells straight below a run of Yards, in the Yards' own line, facing across them (Yards with axis=x carry "
                + "cloth facing north or south, axis=z carries cloth facing east or west)");
        if (!upright.isEmpty()) faults.add("aeronauticswinds:yard stands upright (axis=y) at " + list(upright) + "; Yards run "
                + "sideways from a Mast: axis=x runs east-west, axis=z runs north-south");
        if (!crossed.isEmpty()) faults.add("aeronauticswinds:yard at " + list(crossed) + " runs across its neighbours, so each "
                + "piece shows as a loose stub: a Yard's axis is the way the run goes (axis=x for a run along x, axis=z along z) "
                + "and it joins only the Masts and Yards at its two ends");
        if (!loose.isEmpty()) notes.add("aeronauticswinds:yard at " + list(loose) + " touches no Mast or Yard at its ends, so it "
                + "floats as a loose stub; a Yard is placed against a Mast and runs out from it along its axis");
        StringBuilder out = new StringBuilder("How this rigging hangs" + BuildCheck.FOR_YOU + ":\n");
        for (String note : notes) out.append("- ").append(note).append(".\n");
        if (faults.isEmpty()) out.append("- Nothing here stops it working.\n");
        else for (String f : faults) out.append("- PROBLEM: ").append(f).append(".\n");
        return new BuildCheck.Report(out.toString().trim(), faults);
    }

    /** Straight up through the sail (cloth above cloth) to a Yard that runs across the cloth's facing. */
    private static boolean hangsBelowYard(Map<BlockPos, BlockState> blocks, BlockPos p, Direction facing) {
        BlockPos up = p.above();
        while (isCloth(blocks.get(up))) up = up.above();
        BlockState top = blocks.get(up);
        return is(top, YARD) && top.getValue(BlockStateProperties.AXIS) != facing.getAxis()
                && top.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private static boolean isCloth(BlockState s) {
        return s != null && CLOTH.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock())) && s.hasProperty(BlockStateProperties.FACING);
    }

    private static boolean is(BlockState s, ResourceLocation block) {
        return s != null && BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(block);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.level.block.state.properties.Property<Boolean> bool(BlockState s, String name) {
        return (net.minecraft.world.level.block.state.properties.Property<Boolean>) s.getBlock().getStateDefinition().getProperty(name);
    }

    private static String list(List<BlockPos> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(6, cells.size()); i++) {
            BlockPos p = cells.get(i);
            b.append(i == 0 ? "" : "; ").append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
        }
        if (cells.size() > 6) b.append(" and ").append(cells.size() - 6).append(" more");
        return b.toString();
    }
}

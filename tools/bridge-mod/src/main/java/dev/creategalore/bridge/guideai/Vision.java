package dev.creategalore.bridge.guideai;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * What was on the player's screen when they asked: a grid of sight lines over their field of view, out to
 * {@link #RANGE} blocks, the way a camera would see it (through glass and water, stopped by everything else). It is
 * a written description, never a picture, and it only holds blocks the player could see, so nothing hidden behind
 * a wall or underground gets in. Taken on the server thread when the question arrives.
 */
final class Vision {
    static final int RANGE = 48;
    private static final int COLUMNS = 80, ROWS = 45, MAX_KINDS = 45, MAX_POSITIONS = 120, MAX_CREATURES = 25;

    private Vision() {
    }

    /** The description, and every block position it saw (which later lets the book look closer at them). */
    record Snapshot(String text, String dimension, LongOpenHashSet seen) {
        boolean saw(ServerLevel level, BlockPos pos) {
            return dimension.equals(level.dimension().location().toString()) && seen.contains(pos.asLong());
        }
    }

    private static final class Kind {
        final Block block;
        int count, placed;
        boolean onShip;
        double nearest = Double.MAX_VALUE, sumU, sumV;
        final List<BlockPos> notable = new ArrayList<>();

        Kind(Block block) {
            this.block = block;
        }
    }

    private static final class FluidKind {
        int sources, flowing;
        double nearest = Double.MAX_VALUE, sumU, sumV;
    }

    static Snapshot capture(ServerPlayer player, int fovDegrees, float aspect, PlacedBlocks placed) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 f = player.getViewVector(1f);
        Vec3 right = f.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = Vec3.directionFromRotation(0, player.getYRot() + 90); // looking straight up or down
        right = right.normalize();
        Vec3 up = right.cross(f).normalize();
        double fov = Mth.clamp(fovDegrees, 30, 110), asp = Mth.clamp(aspect, 1f, 3.5f);
        double tanV = Math.tan(Math.toRadians(fov / 2)), tanH = tanV * asp;

        LongOpenHashSet seen = new LongOpenHashSet();
        Map<Block, Kind> kinds = new LinkedHashMap<>();
        Map<Fluid, FluidKind> fluids = new LinkedHashMap<>();
        LongOpenHashSet fluidSeen = new LongOpenHashSet();
        BlockPos crosshair = null;
        for (int row = 0; row < ROWS; row++) {
            double v = 1 - (row + 0.5) * 2 / ROWS;
            for (int col = 0; col < COLUMNS; col++) {
                double u = (col + 0.5) * 2 / COLUMNS - 1;
                Vec3 dir = f.add(right.scale(u * tanH)).add(up.scale(v * tanV)).normalize();
                BlockPos hit = trace(level, player, eye, dir, seen, kinds, placed, u, v);
                if (row == ROWS / 2 && col == COLUMNS / 2) crosshair = hit;
                // Water and lava surfaces, which the first trace looks through.
                BlockHitResult wet = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.ANY, player));
                if (wet.getType() == HitResult.Type.BLOCK) {
                    BlockPos p = wet.getBlockPos();
                    FluidState fs = level.getFluidState(p);
                    if (!fs.isEmpty() && fluidSeen.add(p.asLong())) {
                        seen.add(p.asLong());
                        FluidKind k = fluids.computeIfAbsent(fs.getType() instanceof net.minecraft.world.level.material.FlowingFluid flow
                                ? flow.getSource() : fs.getType(), x -> new FluidKind());
                        if (fs.isSource()) k.sources++;
                        else k.flowing++;
                        k.nearest = Math.min(k.nearest, Math.sqrt(eye.distanceToSqr(Vec3.atCenterOf(p))));
                        k.sumU += u;
                        k.sumV += v;
                    }
                }
            }
        }

        String name = player.getGameProfile().getName();
        StringBuilder out = new StringBuilder();
        out.append("What ").append(name).append(" saw when asking: their screen, out to ").append(RANGE).append(" blocks, from the eye at ")
                .append(String.format(Locale.ROOT, "%.1f %.1f %.1f", eye.x, eye.y, eye.z)).append(" in ")
                .append(level.dimension().location()).append(", facing ").append(facing(player.getYRot()))
                .append(pitch(player.getXRot())).append(".\n");
        double reach = crosshair == null ? RANGE : Math.sqrt(eye.distanceToSqr(where(level, crosshair, eye)));
        var aimed = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, eye.add(f.scale(reach)),
                player.getBoundingBox().expandTowards(f.scale(reach)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable() && !e.isInvisibleTo(player), reach * reach);
        if (aimed != null) {
            Entity e = aimed.getEntity();
            out.append("At the crosshair: ").append(creature(e)).append(String.format(Locale.ROOT, ", %.0f blocks away at %s.\n",
                    Math.sqrt(eye.distanceToSqr(e.position())), xyz(e.blockPosition())));
        } else if (crosshair != null) {
            BlockState s = level.getBlockState(crosshair);
            out.append("At the crosshair: ").append(blockName(s.getBlock())).append(" at ").append(xyz(crosshair)).append(", ")
                    .append(String.format(Locale.ROOT, "%.0f", reach)).append(" blocks away")
                    .append(where(level, crosshair, eye).equals(Vec3.atCenterOf(crosshair)) ? "" : ", on a ship or vehicle (ship coordinates)").append(".\n");
        } else {
            out.append("At the crosshair: nothing within ").append(RANGE).append(" blocks (sky or far away).\n");
        }

        List<Kind> list = new ArrayList<>(kinds.values());
        list.sort(Comparator.comparingInt((Kind k) -> k.count).reversed());
        out.append("Blocks seen, most first (count of visible blocks, nearest distance, where on screen; \"placed\" = put there by a player):");
        if (list.isEmpty()) out.append(" none");
        for (int i = 0; i < list.size(); i++) {
            Kind k = list.get(i);
            if (i >= MAX_KINDS) {
                out.append("\n- ...and ").append(list.size() - MAX_KINDS).append(" rarer kinds");
                break;
            }
            out.append("\n- ").append(k.count).append(' ').append(blockName(k.block)).append(String.format(Locale.ROOT, ", from %.0f blocks, ", k.nearest))
                    .append(region(k.sumU / k.count, k.sumV / k.count));
            if (k.placed > 0) out.append(k.placed == k.count ? ", placed" : ", " + k.placed + " placed");
            if (k.onShip) out.append(", some on a ship or vehicle");
        }

        List<Kind> withPositions = new ArrayList<>();
        for (Kind k : list) if (!k.notable.isEmpty()) withPositions.add(k);
        if (!withPositions.isEmpty()) {
            out.append("\nPlaced blocks and machines seen, nearest first (x y z; blocks on a Sable ship or vehicle are in the ship's own "
                    + "coordinates, far from the player's, and only matter relative to each other):");
            int shown = 0;
            for (Kind k : withPositions) {
                if (!k.onShip) k.notable.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(eye)));
                out.append("\n- ").append(blockName(k.block)).append(": ");
                List<String> at = new ArrayList<>();
                for (BlockPos p : k.notable) {
                    if (shown >= MAX_POSITIONS) break;
                    at.add(xyz(p));
                    shown++;
                }
                out.append(String.join(", ", at));
                if (at.size() < k.notable.size()) out.append(" and ").append(k.notable.size() - at.size()).append(" more");
            }
        }

        if (!fluids.isEmpty()) {
            out.append("\nFluid surfaces seen:");
            fluids.forEach((fluid, k) -> {
                int n = k.sources + k.flowing;
                out.append("\n- ").append(fluid.getFluidType().getDescription().getString()).append(": ").append(k.sources)
                        .append(" still, ").append(k.flowing).append(" flowing, from ").append(String.format(Locale.ROOT, "%.0f", k.nearest))
                        .append(" blocks, ").append(region(k.sumU / n, k.sumV / n));
            });
        }

        List<String> creatures = creatures(player, eye, f, right, up, tanH, tanV);
        out.append("\nCreatures, players and dropped items seen, nearest first:").append(creatures.isEmpty() ? " none" : "");
        creatures.forEach(c -> out.append("\n- ").append(c));
        return new Snapshot(out.toString(), level.dimension().location().toString(), seen);
    }

    /** One sight line: through glass, ice and panes, stopped by anything else. Returns the block it stopped at. */
    private static BlockPos trace(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 dir, LongOpenHashSet seen,
                                  Map<Block, Kind> kinds, PlacedBlocks placed, double u, double v) {
        Vec3 from = eye;
        BlockPos first = null;
        for (int pass = 0; pass < 5; pass++) {
            double left = RANGE - from.distanceTo(eye);
            if (left <= 0) break;
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(left)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) break;
            BlockPos p = hit.getBlockPos();
            if (first == null) first = p;
            BlockState state = level.getBlockState(p);
            if (seen.add(p.asLong())) {
                Kind k = kinds.computeIfAbsent(state.getBlock(), Kind::new);
                k.count++;
                // A block on a Sable ship comes back in the ship's own coordinates; measure it where the ship is.
                Vec3 centre = where(level, p, eye);
                if (!centre.equals(Vec3.atCenterOf(p))) k.onShip = true;
                k.nearest = Math.min(k.nearest, Math.sqrt(eye.distanceToSqr(centre)));
                k.sumU += u;
                k.sumV += v;
                boolean isPlaced = placed != null && placed.isPlaced(level, p);
                if (isPlaced) k.placed++;
                if (isPlaced || state.hasBlockEntity()) k.notable.add(p);
            }
            if (!seeThrough(state)) break;
            // Step out of the see-through block and look on from just beyond it.
            Vec3 next = hit.getLocation();
            for (int i = 0; i < 40 && BlockPos.containing(next).equals(p); i++) next = next.add(dir.scale(0.05));
            from = next;
        }
        return first;
    }

    /** A block's centre in the world: for a block on a Sable ship (far off in its plot), where the ship has it now. */
    private static Vec3 where(ServerLevel level, BlockPos p, Vec3 eye) {
        Vec3 centre = Vec3.atCenterOf(p);
        if (centre.distanceToSqr(eye) <= 4.0 * RANGE * RANGE) return centre;
        Vec3 world = Ships.toWorld(level, p);
        return world == null ? centre : world;
    }

    private static boolean seeThrough(BlockState state) {
        return state.getBlock() instanceof HalfTransparentBlock || state.getBlock() instanceof IronBarsBlock
                || state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES);
    }

    private static List<String> creatures(ServerPlayer player, Vec3 eye, Vec3 f, Vec3 right, Vec3 up, double tanH, double tanV) {
        record Seen(String what, double dist, BlockPos at) {
        }
        List<Seen> seen = new ArrayList<>();
        for (Entity e : player.level().getEntities(player, new AABB(eye, eye).inflate(RANGE),
                e -> !e.isSpectator() && !e.isInvisibleTo(player) && !(e instanceof ExperienceOrb))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double z = to.dot(f), dist = to.length();
            if (z <= 0.1 || dist > RANGE) continue;
            double u = to.dot(right) / (z * tanH), v = to.dot(up) / (z * tanV);
            if (Math.abs(u) > 1 || Math.abs(v) > 1 || !player.hasLineOfSight(e)) continue;
            seen.add(new Seen(creature(e), dist, e.blockPosition()));
        }
        seen.sort(Comparator.comparingDouble(Seen::dist));
        List<String> out = new ArrayList<>();
        for (Seen s : seen) {
            if (out.size() >= MAX_CREATURES) {
                out.add("...and " + (seen.size() - MAX_CREATURES) + " more");
                break;
            }
            out.add(s.what + String.format(Locale.ROOT, " at %s, %.0f blocks", xyz(s.at), s.dist));
        }
        return out;
    }

    /** A creature or thing as the player would describe it: kind, name, health, whether it is hostile. */
    private static String creature(Entity e) {
        if (e instanceof ItemEntity item) return "dropped " + item.getItem().getCount() + "x " + item.getItem().getHoverName().getString();
        StringBuilder s = new StringBuilder(e.getType().getDescription().getString());
        if (e.hasCustomName()) s.append(" named ").append(e.getCustomName().getString());
        s.append(" (").append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).append(')');
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            s.append(String.format(Locale.ROOT, ", health %.0f of %.0f", living.getHealth(), living.getMaxHealth()));
            if (living.isBaby()) s.append(", young");
            s.append(e instanceof net.minecraft.world.entity.monster.Enemy ? ", hostile" : "");
        }
        if (e instanceof net.minecraft.world.entity.player.Player p) s.append(", the player ").append(p.getGameProfile().getName());
        return s.toString();
    }

    static String blockName(Block block) {
        return block.getName().getString() + " (" + BuiltInRegistries.BLOCK.getKey(block) + ")";
    }

    static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String region(double u, double v) {
        String h = u < -0.33 ? "left" : u > 0.33 ? "right" : "centre";
        String vert = v > 0.33 ? "upper " : v < -0.33 ? "lower " : "";
        return vert.isEmpty() && h.equals("centre") ? "middle of the screen" : vert + h;
    }

    static String facing(float yRot) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[Math.floorMod(Math.round(Mth.wrapDegrees(yRot) / 45f), 8)];
    }

    private static String pitch(float xRot) {
        int d = Math.round(xRot);
        if (Math.abs(d) < 5) return ", looking level";
        return d > 0 ? ", looking " + d + "° down" : ", looking " + -d + "° up";
    }
}

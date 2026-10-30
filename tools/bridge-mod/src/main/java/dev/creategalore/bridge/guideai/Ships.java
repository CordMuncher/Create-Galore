package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable ships and vehicles near the player. Sable keeps a ship's blocks in a plot of their own far out in the
 * world, so their coordinates only mean something relative to each other; the book reads them there. Sable's
 * classes are only touched when it is installed.
 */
final class Ships {
    /** A ship whose middle is within the chunks the server runs around the player; plot chunks are inclusive. */
    record Ship(String name, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Vec3 at) {
        boolean holds(ChunkPos c) {
            return c.x >= minChunkX && c.x <= maxChunkX && c.z >= minChunkZ && c.z <= maxChunkZ;
        }

        boolean holds(BlockPos p) {
            return holds(new ChunkPos(p));
        }
    }

    private Ships() {
    }

    static List<Ship> near(ServerPlayer player, int radius) {
        if (!ModList.get().isLoaded("sable")) return List.of();
        try {
            return Sable.near(player, radius);
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    static Ship holding(ServerPlayer player, int radius, BlockPos pos) {
        for (Ship s : near(player, radius)) if (s.holds(pos)) return s;
        return null;
    }

    /** Where a block on a Sable ship is in the world right now, or null if pos isn't on a ship. */
    static Vec3 toWorld(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("sable")) return null;
        try {
            return Sable.toWorld(level, pos);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** The ids of every ship in the level; the casebook compares them to find the ships an assembly made. */
    static java.util.Set<java.util.UUID> ids(net.minecraft.server.level.ServerLevel level) {
        if (!ModList.get().isLoaded("sable")) return java.util.Set.of();
        try {
            return Sable.ids(level);
        } catch (RuntimeException | LinkageError e) {
            return java.util.Set.of();
        }
    }

    /** Takes the ships with these ids out of the level, blocks and all, forgetting the placed marks in their plots (casebook). */
    static void remove(net.minecraft.server.level.ServerLevel level, java.util.Set<java.util.UUID> ids, PlacedBlocks placed) {
        if (ids.isEmpty() || !ModList.get().isLoaded("sable")) return;
        try {
            Sable.remove(level, ids, placed);
        } catch (RuntimeException | LinkageError e) {
            // Nothing to clear up then.
        }
    }

    /** The ids of the ships whose middle is within {@code radius} blocks (horizontally) of {@code at} (casebook). */
    static java.util.Set<java.util.UUID> near(net.minecraft.server.level.ServerLevel level, BlockPos at, int radius) {
        if (!ModList.get().isLoaded("sable")) return java.util.Set.of();
        try {
            return Sable.near(level, at, radius);
        } catch (RuntimeException | LinkageError e) {
            return java.util.Set.of();
        }
    }

    private static final class Sable {
        static java.util.Set<java.util.UUID> near(net.minecraft.server.level.ServerLevel level, BlockPos at, int radius) {
            java.util.Set<java.util.UUID> out = new java.util.HashSet<>();
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return out;
            for (var sub : container.getAllSubLevels()) {
                var p = sub.logicalPose().position();
                if (!sub.isRemoved() && Math.abs(p.x() - at.getX()) <= radius && Math.abs(p.z() - at.getZ()) <= radius) out.add(sub.getUniqueId());
            }
            return out;
        }

        static java.util.Set<java.util.UUID> ids(net.minecraft.server.level.ServerLevel level) {
            java.util.Set<java.util.UUID> out = new java.util.HashSet<>();
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container != null) for (var sub : container.getAllSubLevels()) if (!sub.isRemoved()) out.add(sub.getUniqueId());
            return out;
        }

        static void remove(net.minecraft.server.level.ServerLevel level, java.util.Set<java.util.UUID> ids, PlacedBlocks placed) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return;
            for (var sub : container.getAllSubLevels()) {
                if (sub.isRemoved() || !ids.contains(sub.getUniqueId())) continue;
                var plot = sub.getPlot();
                ChunkPos min = plot.getChunkMin(), max = plot.getChunkMax();
                if (placed != null)
                    for (BlockPos p : placed.inChunks(level, c -> c.x >= min.x && c.x <= max.x && c.z >= min.z && c.z <= max.z, Integer.MAX_VALUE))
                        placed.remove(level, p);
                if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server) server.getPlot().destroyAllBlocks();
                sub.markRemoved();
            }
        }

        static Vec3 toWorld(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return null;
            var plot = container.getPlot(new ChunkPos(pos));
            if (plot == null || plot.getSubLevel() == null) return null;
            return plot.getSubLevel().logicalPose().transformPosition(Vec3.atCenterOf(pos));
        }

        static List<Ship> near(ServerPlayer player, int radius) {
            List<Ship> out = new ArrayList<>();
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.serverLevel());
            if (container == null) return out;
            ChunkPos here = player.chunkPosition();
            for (var sub : container.getAllSubLevels()) {
                if (sub.isRemoved()) continue;
                var p = sub.logicalPose().position();
                int cx = (int) Math.floor(p.x()) >> 4, cz = (int) Math.floor(p.z()) >> 4;
                if (Math.abs(cx - here.x) > radius || Math.abs(cz - here.z) > radius) continue;
                var plot = sub.getPlot();
                ChunkPos min = plot.getChunkMin(), max = plot.getChunkMax();
                String name = sub.getName();
                out.add(new Ship(name == null || name.isBlank() ? "unnamed" : name, min.x, min.z, max.x, max.z, new Vec3(p.x(), p.y(), p.z())));
            }
            return out;
        }
    }
}

package dev.creategalore.bridge.guideai;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.slf4j.Logger;

/**
 * Every block a player has placed, so the book can find and read builds without seeing the natural world: it only
 * looks at blocks in this list (and at what was on the player's screen). Any player's blocks count, deployers
 * included, which is meant for cooperative play. A position stays listed while anything but air or loose fluid stands
 * there, so copper that weathers, crops that grow, a wrenched or encased shaft keep counting; one that was broken,
 * burnt or blown up drops out the next time it is looked at. Blocks that move keep their mark: pistons here, Create
 * contraptions and Sable ships through mixins (ContraptionPlacedMixin, SableMovePlacedMixin). Operators can add older
 * builds with /fieldguide builds add. Each change is one hash set write, so placing blocks costs nothing noticeable.
 * Kept per world, next to the book's pages.
 */
final class PlacedBlocks {
    private static final Logger LOG = LogUtils.getLogger();
    static final int MAX_BLOCKS = 1_000_000;
    private static final int FORMAT = 1;

    private final Path file;
    /** Dimension -> chunk -> placed block positions. */
    private final Map<String, Long2ObjectOpenHashMap<LongOpenHashSet>> placed = new HashMap<>();
    /** Dimension -> water and lava a player poured from a bucket (positions of the sources). */
    private final Map<String, LongOpenHashSet> fluids = new HashMap<>();
    private static final int MAX_FLUIDS = 100_000;
    private int size;
    private boolean dirty, fullWarned;

    PlacedBlocks(Path folder) {
        this.file = folder.resolve("placed_blocks.dat");
        load();
    }

    // ---------- tracking (server thread) ----------

    void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) add(level, snapshot.getPos(), level.getBlockState(snapshot.getPos()));
        } else {
            add(level, event.getPos(), event.getPlacedBlock());
        }
    }

    /** Placed blocks a piston pushes or pulls keep their mark where they land. */
    void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !hasNear(level, event.getPos())) return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        Direction dir = resolver.getPushDirection();
        List<BlockPos> moved = new ArrayList<>();
        for (BlockPos p : resolver.getToPush()) if (isPlaced(level, p)) moved.add(p);
        moved.forEach(p -> remove(level, p));
        moved.forEach(p -> addPos(level, p.relative(dir)));
    }

    /** A player emptied a bucket here (a new source, or water put into a waterloggable block). */
    void onPour(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = fluids.computeIfAbsent(dim(level), k -> new LongOpenHashSet());
        if (set.size() < MAX_FLUIDS && set.add(pos.asLong())) dirty = true;
    }

    /** Whether a player poured the fluid source that is there now; drops the entry once no source is left. */
    boolean isPoured(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = fluids.get(dim(level));
        if (set == null || !set.contains(pos.asLong())) return false;
        if (!level.isLoaded(pos) || level.getFluidState(pos).isSource()) return true;
        set.remove(pos.asLong());
        dirty = true;
        return false;
    }

    /** Poured sources still there within a box, at most max. */
    List<BlockPos> pouredIn(ServerLevel level, BlockPos min, BlockPos max, int limit) {
        List<BlockPos> out = new ArrayList<>();
        LongOpenHashSet set = fluids.get(dim(level));
        if (set == null) return out;
        LongArrayList check = new LongArrayList();
        for (LongIterator it = set.iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            BlockPos p = BlockPos.of(k);
            if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) check.add(k);
        }
        for (int i = 0; i < check.size() && out.size() < limit; i++) {
            BlockPos p = BlockPos.of(check.getLong(i));
            if (isPoured(level, p)) out.add(p);
        }
        return out;
    }

    void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) remove(level, event.getPos());
    }

    /** False when the list is full. */
    boolean add(ServerLevel level, BlockPos pos, BlockState state) {
        if (!counts(state)) {
            remove(level, pos);
            return true;
        }
        return addPos(level, pos);
    }

    /** Marks a position whatever stands there now (a block that is about to land). False when the list is full. */
    boolean addPos(ServerLevel level, BlockPos pos) {
        LongOpenHashSet chunk = placed.computeIfAbsent(dim(level), k -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new LongOpenHashSet());
        long key = pos.asLong();
        if (size >= MAX_BLOCKS && !chunk.contains(key)) {
            if (!fullWarned) LOG.warn("Field Guide voice: {} placed blocks are tracked, the most it keeps; newer ones are not", MAX_BLOCKS);
            fullWarned = true;
            return false;
        }
        if (chunk.add(key)) {
            size++;
            dirty = true;
        }
        return true;
    }

    /** Whether anything is tracked in this dimension, so events elsewhere skip all work. */
    boolean has(ServerLevel level) {
        var chunks = placed.get(dim(level));
        return chunks != null && !chunks.isEmpty();
    }

    /** Whether any placed block is tracked in the 3x3 chunks around pos (a piston reaches at most 13 blocks). */
    private boolean hasNear(ServerLevel level, BlockPos pos) {
        var chunks = placed.get(dim(level));
        if (chunks == null || chunks.isEmpty()) return false;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (chunks.containsKey(ChunkPos.asLong(cx + x, cz + z))) return true;
        return false;
    }

    /** Anything but air and loose water or lava counts as the build still standing. */
    static boolean counts(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock);
    }

    void remove(ServerLevel level, BlockPos pos) {
        var chunks = placed.get(dim(level));
        if (chunks == null) return;
        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet chunk = chunks.get(ck);
        if (chunk == null || !chunk.remove(pos.asLong())) return;
        size--;
        if (chunk.isEmpty()) chunks.remove(ck);
        dirty = true;
    }

    int size() {
        return size;
    }

    /** Whether a player placed the block standing there now; drops the entry if only air or fluid is left. */
    boolean isPlaced(ServerLevel level, BlockPos pos) {
        var chunks = placed.get(dim(level));
        if (chunks == null) return false;
        LongOpenHashSet chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (chunk == null || !chunk.contains(pos.asLong())) return false;
        if (!level.isLoaded(pos) || counts(level.getBlockState(pos))) return true;
        remove(level, pos);
        return false;
    }

    /**
     * The placed blocks still standing in loaded chunks within {@code radius} chunks of a chunk (a square, like the
     * server's simulation distance), at most {@code max}. Entries whose block is gone are dropped on the way.
     */
    List<BlockPos> around(ServerLevel level, ChunkPos centre, int radius, int max) {
        List<BlockPos> out = new ArrayList<>();
        var chunks = placed.get(dim(level));
        if (chunks == null) return out;
        LongArrayList gone = new LongArrayList();
        for (int dx = -radius; dx <= radius && out.size() < max; dx++) {
            for (int dz = -radius; dz <= radius && out.size() < max; dz++) {
                int cx = centre.x + dx, cz = centre.z + dz;
                long ck = ChunkPos.asLong(cx, cz);
                LongOpenHashSet chunk = chunks.get(ck);
                if (chunk == null || !level.hasChunk(cx, cz)) continue;
                for (LongIterator it = chunk.iterator(); it.hasNext(); ) {
                    long key = it.nextLong();
                    BlockPos p = BlockPos.of(key);
                    if (!counts(level.getBlockState(p))) gone.add(key);
                    else if (out.size() < max) out.add(p);
                }
            }
        }
        for (int i = 0; i < gone.size(); i++) remove(level, BlockPos.of(gone.getLong(i)));
        return out;
    }

    /**
     * The placed blocks still standing in chunks the test accepts, at most {@code max}; for a Sable ship's plot,
     * whose chunks the normal chunk lookups don't see.
     */
    List<BlockPos> inChunks(ServerLevel level, java.util.function.Predicate<ChunkPos> accept, int max) {
        List<BlockPos> out = new ArrayList<>();
        var chunks = placed.get(dim(level));
        if (chunks == null) return out;
        LongArrayList gone = new LongArrayList();
        for (var e : chunks.long2ObjectEntrySet()) {
            if (out.size() >= max) break;
            if (!accept.test(new ChunkPos(e.getLongKey()))) continue;
            for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                BlockPos p = BlockPos.of(key);
                if (!counts(level.getBlockState(p))) gone.add(key);
                else if (out.size() < max) out.add(p);
            }
        }
        for (int i = 0; i < gone.size(); i++) remove(level, BlockPos.of(gone.getLong(i)));
        return out;
    }

    private static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    // ---------- saving ----------

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != FORMAT) throw new IOException("unknown format");
            int dims = in.readInt();
            for (int d = 0; d < dims; d++) {
                String dim = in.readUTF();
                int n = in.readInt();
                var chunks = placed.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
                for (int i = 0; i < n; i++) {
                    long pos = in.readLong();
                    if (size >= MAX_BLOCKS) continue;
                    BlockPos p = BlockPos.of(pos);
                    if (chunks.computeIfAbsent(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4), k -> new LongOpenHashSet()).add(pos)) size++;
                }
            }
            int fluidDims = in.readInt();
            for (int d = 0; d < fluidDims; d++) {
                LongOpenHashSet set = fluids.computeIfAbsent(in.readUTF(), k -> new LongOpenHashSet());
                int n = in.readInt();
                for (int i = 0; i < n; i++) {
                    long pos = in.readLong();
                    if (set.size() < MAX_FLUIDS) set.add(pos);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Field Guide voice: couldn't read {} ({}); starting a fresh list of placed blocks", file, e.toString());
            placed.clear();
            fluids.clear();
            size = 0;
        }
    }

    /** Server thread; writes only when something changed. */
    void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                out.writeInt(FORMAT);
                out.writeInt(placed.size());
                for (var d : placed.entrySet()) {
                    out.writeUTF(d.getKey());
                    int n = 0;
                    for (LongOpenHashSet chunk : d.getValue().values()) n += chunk.size();
                    out.writeInt(n);
                    for (LongOpenHashSet chunk : d.getValue().values())
                        for (LongIterator it = chunk.iterator(); it.hasNext(); ) out.writeLong(it.nextLong());
                }
                out.writeInt(fluids.size());
                for (var d : fluids.entrySet()) {
                    out.writeUTF(d.getKey());
                    out.writeInt(d.getValue().size());
                    for (LongIterator it = d.getValue().iterator(); it.hasNext(); ) out.writeLong(it.nextLong());
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            LOG.warn("Field Guide voice: couldn't save {} ({})", file, e.toString());
        }
    }
}

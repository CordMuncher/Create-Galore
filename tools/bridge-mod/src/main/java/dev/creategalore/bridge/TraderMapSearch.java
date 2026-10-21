package dev.creategalore.bridge;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.common.WorldWorkerManager;

/**
 * The Soaring Trader's structure map search, spread over spare tick time. Discovery looked for the nearest structure
 * of each kind in one go, the first time anything read the trader's offers (often the autosave right after a flyover
 * arrived), and each candidate spot meant working out whether that structure could generate there: 2 to 7 s of frozen
 * server per trader. This checks the same spots in the same order, with the same limits (50 chunks, 800 spots per
 * placement), one spot at a time between ticks, and hands the first find to {@code onFound}.
 */
public final class TraderMapSearch implements WorldWorkerManager.IWorker {
    private static final int MAX_CHUNK_RADIUS = 50;
    private static final int MAX_CHECKS = 800;

    private final ServerLevel level;
    private final Entity trader;
    private final ChunkGeneratorStructureState state;
    private final long seed;
    private final int originX;
    private final int originZ;
    private final Deque<String> ids;
    private final BiConsumer<String, BlockPos> onFound;

    private String id;
    private Structure structure;
    private Iterator<StructurePlacement> placements = Collections.emptyIterator();
    private RandomSpreadStructurePlacement placement;
    private int maxRings;
    private int ring;
    private int cursor;
    private int checks;
    private boolean done;

    public TraderMapSearch(ServerLevel level, Entity trader, List<String> ids, BiConsumer<String, BlockPos> onFound) {
        this.level = level;
        this.trader = trader;
        this.state = level.getChunkSource().getGeneratorState();
        this.seed = state.getLevelSeed();
        this.originX = SectionPos.blockToSectionCoord(trader.blockPosition().getX());
        this.originZ = SectionPos.blockToSectionCoord(trader.blockPosition().getZ());
        this.ids = new ArrayDeque<>(ids);
        this.onFound = onFound;
    }

    public void start() {
        WorldWorkerManager.addWorker(this);
    }

    @Override
    public boolean hasWork() {
        return !done;
    }

    @Override
    public boolean doWork() {
        if (done) return false;
        if (trader.isRemoved() || (placement == null && !nextPlacement())) {
            done = true;
            return false;
        }
        ChunkPos candidate = placement.getPotentialStructureChunk(seed, originX + placement.spacing() * dx(), originZ + placement.spacing() * dz());
        if (level.structureManager().checkStructurePresence(candidate, structure, placement, false) != StructureCheckResult.START_NOT_PRESENT) {
            done = true;
            onFound.accept(id, placement.getLocatePos(candidate));
            return false;
        }
        advance();
        return true;
    }

    /** The next placement to search: the structure's own next one, else the first of the next structure in the list. */
    private boolean nextPlacement() {
        while (true) {
            while (placements.hasNext()) {
                if (placements.next() instanceof RandomSpreadStructurePlacement spread) {
                    placement = spread;
                    maxRings = MAX_CHUNK_RADIUS / spread.spacing() + 1;
                    ring = cursor = checks = 0;
                    return true;
                }
            }
            if (ids.isEmpty()) return false;
            id = ids.poll();
            Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
            if (holder.isEmpty()) continue;
            structure = holder.get().value();
            placements = state.getPlacementsForStructure(holder.get()).iterator();
        }
    }

    /** Rings of placement cells around the trader, nearest first, walking each ring's four sides in turn. */
    private int dx() {
        if (ring == 0) return 0;
        int pos = cursor % (ring * 2);
        return switch (cursor / (ring * 2)) {
            case 0 -> -ring + pos;
            case 1 -> ring;
            case 2 -> ring - pos;
            default -> -ring;
        };
    }

    private int dz() {
        if (ring == 0) return 0;
        int pos = cursor % (ring * 2);
        return switch (cursor / (ring * 2)) {
            case 0 -> -ring;
            case 1 -> -ring + pos;
            case 2 -> ring;
            default -> ring - pos;
        };
    }

    private void advance() {
        if (++checks >= MAX_CHECKS) {
            placement = null;
            return;
        }
        if (++cursor >= (ring == 0 ? 1 : ring * 8)) {
            ring++;
            cursor = 0;
            if (ring > maxRings) placement = null;
        }
    }
}

package dev.creategalore.structscan;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** The scan itself: list candidate chunks from the placement math, then run the real structure-start step on each. */
final class Scanner {
    /** Structures that only pick their height when placed: vanilla's (and copies of) shipwrecks, ocean ruins, igloos, buried treasure. */
    enum Deferred { NO, SEA_FLOOR, BURIED }

    private static Deferred deferred(StructureStart start) {
        for (var piece : start.getPieces())
            for (Class<?> c = piece.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                String n = c.getName();
                if (n.endsWith("BuriedTreasurePieces$BuriedTreasurePiece")) return Deferred.BURIED;
                if (n.endsWith("ShipwreckPieces$ShipwreckPiece") || n.endsWith("OceanRuinPieces$OceanRuinPiece")
                        || n.endsWith("IglooPieces$IglooPiece")) return Deferred.SEA_FLOOR;
            }
        return Deferred.NO;
    }

    /** Where a structure sits, judged at the middle of its bounding box. */
    enum Layer { SURFACE, UNDERGROUND, UNDERWATER, SKY }

    /**
     * One structure found. x/z is the middle of its bounding box; floor is the top of the solid ground there
     * (the sea floor in water) and water the top of the water (same as floor on land). biome is the biome at the
     * middle of the bounding box (at the ground for structures that pick their height later).
     */
    record Start(String set, String structure, int x, int z, int minY, int maxY, int floor, int water, long chunk, Deferred deferred, String biome) {
        Layer layer() {
            // Their height is only picked when the structure is placed, so the start's y means nothing yet.
            if (deferred == Deferred.BURIED) return Layer.UNDERGROUND;
            if (deferred == Deferred.SEA_FLOOR) return water - floor >= 3 ? Layer.UNDERWATER : Layer.SURFACE;
            if (minY >= water + 16) return Layer.SKY;
            if (maxY < floor - 4) return Layer.UNDERGROUND;
            if (water - floor >= 3 && maxY <= water + 2) return Layer.UNDERWATER;
            return Layer.SURFACE;
        }
    }

    /** One structure set as scanned: its placement before and after the what-if rules, and how often it succeeded. */
    static final class SetInfo {
        String id, type, structures, rule = "";
        final List<String> structureIds = new ArrayList<>();
        int spacing = -1, separation = -1, newSpacing = -1, newSeparation = -1;
        float frequency = 1, newFrequency = 1;
        int candidates;
        final AtomicInteger placed = new AtomicInteger();
        /** Time spent on chunks this set was a candidate in (shared out when several were). */
        final java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        final AtomicInteger[] byLayer = {new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger()};
    }

    record Result(String dim, long seed, int radius, int centerX, int centerZ, List<Start> starts, List<SetInfo> sets,
                  int chunksChecked, int errors, long millis, boolean whatif, Map<String, double[]> biomeArea) {}

    /** How far apart the biome-area samples are, in blocks. */
    static final int BIOME_STEP = 64;
    /** Underground biome areas are sampled at this height (deep enough for the cave biomes). */
    static final int CAVE_Y = -20;

    /**
     * Area of every biome in the scanned square, in km², at the ground ([0]) and at {@link #CAVE_Y} ([1]),
     * from a grid of samples. Lets the report say how dense structures are per biome.
     */
    private static Map<String, double[]> biomeArea(ServerLevel level, ChunkGenerator gen, RandomState rs, StructScan.Options opt, ForkJoinPool pool) throws Exception {
        Registry<Biome> biomeReg = level.registryAccess().registryOrThrow(Registries.BIOME);
        int n = 2 * opt.radius() / BIOME_STEP;
        double cell = BIOME_STEP * (double) BIOME_STEP / 1e6;
        Map<String, double[]> area = new java.util.concurrent.ConcurrentHashMap<>();
        pool.submit(() -> java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
            ColumnCache.begin(rs);
            try {
                for (int j = 0; j < n; j++) {
                    int x = opt.centerX() - opt.radius() + i * BIOME_STEP + BIOME_STEP / 2;
                    int z = opt.centerZ() - opt.radius() + j * BIOME_STEP + BIOME_STEP / 2;
                    int y = gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                    String top = biomeAt(gen, rs, biomeReg, x, y, z), cave = biomeAt(gen, rs, biomeReg, x, CAVE_Y, z);
                    synchronized (area) {
                        area.computeIfAbsent(top, k -> new double[2])[0] += cell;
                        area.computeIfAbsent(cave, k -> new double[2])[1] += cell;
                    }
                }
            } finally {
                ColumnCache.end();
            }
        })).get();
        return area;
    }

    static String biomeAt(ChunkGenerator gen, RandomState rs, Registry<Biome> reg, int x, int y, int z) {
        Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, rs.sampler());
        return b.unwrapKey().map(k -> k.location().toString()).orElseGet(() -> String.valueOf(reg.getKey(b.value())));
    }

    static Result scan(ServerLevel level, StructScan.Options opt, List<Rules.Rule> rules, Consumer<String> log) throws Exception {
        long t0 = System.currentTimeMillis();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState base = level.getChunkSource().getGeneratorState();
        RandomState rs = base.randomState();
        long seed = base.getLevelSeed();
        Registry<Structure> structureReg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<Biome> biomeReg = level.registryAccess().registryOrThrow(Registries.BIOME);

        // Structure sets this dimension can actually place (the game already dropped the ones with no biome here).
        List<Holder<StructureSet>> holders = new ArrayList<>();
        List<SetInfo> infos = new ArrayList<>();
        Map<Structure, String> setOfStructure = new IdentityHashMap<>();
        boolean changed = false;
        for (Holder<StructureSet> h : base.possibleStructureSets()) {
            StructureSet set = h.value();
            SetInfo info = new SetInfo();
            // Some mods (Create Structures Arise) add sets without a registry name: name those after their first structure.
            info.id = h.unwrapKey().map(k -> k.location().toString()).orElseGet(() -> set.structures().isEmpty() ? "(unnamed)"
                    : "(set of " + structureReg.getKey(set.structures().get(0).structure().value()) + ")");
            StructurePlacement p = set.placement();
            info.type = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(p.type()));
            info.frequency = info.newFrequency = (float) call(p, "frequency");
            List<String> names = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry e : set.structures()) {
                ResourceLocation id = structureReg.getKey(e.structure().value());
                info.structureIds.add(String.valueOf(id));
                names.add(id + (set.structures().size() > 1 ? "×" + e.weight() : ""));
                setOfStructure.putIfAbsent(e.structure().value(), info.id);
            }
            info.structures = String.join(" ", names);
            Holder<StructureSet> use = h;
            if (p instanceof RandomSpreadStructurePlacement r) {
                info.spacing = info.newSpacing = r.spacing();
                info.separation = info.newSeparation = r.separation();
            }
            Rules.Rule rule = Rules.match(rules, info.id, info.structureIds);
            if (rule != null) {
                info.rule = rule.pattern() + " " + rule.spacing() + (rule.frequency() != 1 ? " " + rule.frequency() : "");
                StructurePlacement np = rescale(p, rule, info);
                if (np != null) {
                    use = Holder.direct(new StructureSet(set.structures(), np));
                    changed = true;
                } else {
                    info.rule += " (can't rescale " + info.type + ")";
                }
            }
            holders.add(use);
            infos.add(info);
        }

        ChunkGeneratorStructureState state = base;
        if (changed) {
            // A fresh placement state with the what-if sets (same seeds, so everything else lands where it always does).
            Constructor<ChunkGeneratorStructureState> c = ChunkGeneratorStructureState.class.getDeclaredConstructor(
                    RandomState.class, BiomeSource.class, long.class, long.class, List.class);
            c.setAccessible(true);
            Field ringSeed = ChunkGeneratorStructureState.class.getDeclaredField("concentricRingsSeed");
            ringSeed.setAccessible(true);
            state = c.newInstance(rs, gen.getBiomeSource(), seed, ringSeed.getLong(base), holders);
        }
        state.ensureStructuresGenerated();

        // Candidate chunks: wherever any set's placement (grid, frequency, exclusion zones) says "try here".
        int r = (opt.radius() + 15) >> 4;
        int cx0 = opt.centerX() >> 4, cz0 = opt.centerZ() >> 4;
        int minX = cx0 - r, maxX = cx0 + r, minZ = cz0 - r, maxZ = cz0 + r;
        Long2ObjectOpenHashMap<IntArrayList> chunks = new Long2ObjectOpenHashMap<>();
        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        if (sets.size() != holders.size()) throw new IllegalStateException("structure sets changed: " + holders.size() + " -> " + sets.size());
        for (int i = 0; i < sets.size(); i++) {
            StructurePlacement p = sets.get(i).value().placement();
            // Same order as ours, but a mod may hand back new holders (Create Structures Arise does), so go by position.
            int info = i, n = 0;
            SetInfo si0 = infos.get(i);
            boolean ruled = si0.newSpacing != si0.spacing || si0.newFrequency != si0.frequency;
            if (ruled && holders.get(i).value().placement() != p) {
                // Create Structures Arise swaps in its own placements when the state is built, so the rule can't reach it.
                si0.rule += " (not applied: the mod sets this placement itself)";
                si0.newSpacing = p instanceof RandomSpreadStructurePlacement rp0 ? rp0.spacing() : si0.spacing;
                si0.newSeparation = p instanceof RandomSpreadStructurePlacement rp0 ? rp0.separation() : si0.separation;
                si0.newFrequency = si0.frequency;
            }
            if (p.getClass() == RandomSpreadStructurePlacement.class) {
                RandomSpreadStructurePlacement rp = (RandomSpreadStructurePlacement) p;
                int s = rp.spacing();
                for (int gx = Math.floorDiv(minX, s); gx <= Math.floorDiv(maxX, s); gx++)
                    for (int gz = Math.floorDiv(minZ, s); gz <= Math.floorDiv(maxZ, s); gz++) {
                        ChunkPos cp = rp.getPotentialStructureChunk(seed, gx * s, gz * s);
                        if (cp.x < minX || cp.x > maxX || cp.z < minZ || cp.z > maxZ) continue;
                        if (p.isStructureChunk(state, cp.x, cp.z)) { chunks.computeIfAbsent(cp.toLong(), k -> new IntArrayList()).add(info); n++; }
                    }
            } else if (p instanceof ConcentricRingsStructurePlacement cr) {
                for (ChunkPos cp : state.getRingPositionsFor(cr)) {
                    if (cp.x < minX || cp.x > maxX || cp.z < minZ || cp.z > maxZ) continue;
                    if (p.isStructureChunk(state, cp.x, cp.z)) { chunks.computeIfAbsent(cp.toLong(), k -> new IntArrayList()).add(info); n++; }
                }
            } else {
                // Some other placement type (a mod's own): just ask it about every chunk. Still only math.
                for (int x = minX; x <= maxX; x++)
                    for (int z = minZ; z <= maxZ; z++)
                        if (p.isStructureChunk(state, x, z)) { chunks.computeIfAbsent(ChunkPos.asLong(x, z), k -> new IntArrayList()).add(info); n++; }
            }
            infos.get(info).candidates = n;
        }
        log.accept("StructScan " + level.dimension().location() + ": " + sets.size() + " structure sets, "
                + chunks.size() + " candidate chunks in ±" + opt.radius() + " blocks, checking biomes and terrain...");

        // Run the game's own structure-start step on a throwaway chunk for each candidate. Nothing is generated or saved.
        Map<String, SetInfo> infoById = new HashMap<>();
        for (SetInfo i : infos) infoById.put(i.id, i);
        ConcurrentLinkedQueue<Start> found = new ConcurrentLinkedQueue<>();
        AtomicInteger done = new AtomicInteger(), errors = new AtomicInteger();
        // In row order, so each thread works through a strip of neighbouring chunks and the caches get reused.
        LongArrayList list = new LongArrayList(chunks.keySet());
        list.sort((it.unimi.dsi.fastutil.longs.LongComparator) (p1, p2) -> {
            int c = Integer.compare(ChunkPos.getZ(p1), ChunkPos.getZ(p2));
            return c != 0 ? c : Integer.compare(ChunkPos.getX(p1), ChunkPos.getX(p2));
        });
        int total = list.size();
        final ChunkGeneratorStructureState st = state;
        ForkJoinPool pool = new ForkJoinPool(Math.max(1, opt.threads()));
        Map<String, double[]> biomes;
        try {
            pool.submit(() -> list.longParallelStream().forEach(packed -> {
                ChunkPos cp = new ChunkPos(packed);
                long began = System.nanoTime();
                ColumnCache.begin(rs);
                try {
                    ProtoChunk chunk = new ProtoChunk(cp, UpgradeData.EMPTY, level, biomeReg, null);
                    gen.createStructures(level.registryAccess(), st, level.structureManager(), chunk, level.getStructureManager());
                    for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                        StructureStart s = e.getValue();
                        if (!s.isValid()) continue;
                        BoundingBox b = s.getBoundingBox();
                        int x = (b.minX() + b.maxX()) / 2, z = (b.minZ() + b.maxZ()) / 2;
                        int[] h = heights(gen.getBaseColumn(x, z, level, rs), gen.getMinY(), level.getHeight());
                        String setId = setOfStructure.getOrDefault(e.getKey(), "?");
                        Deferred d = deferred(s);
                        String biome = biomeAt(gen, rs, biomeReg, x, d == Deferred.NO ? (b.minY() + b.maxY()) / 2 : h[0], z);
                        Start found1 = new Start(setId, String.valueOf(structureReg.getKey(e.getKey())), x, z, b.minY(), b.maxY(), h[0], h[1], packed, d, biome);
                        found.add(found1);
                        SetInfo si = infoById.get(setId);
                        if (si != null) {
                            si.placed.incrementAndGet();
                            si.byLayer[found1.layer().ordinal()].incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    if (errors.incrementAndGet() <= 5) StructScan.LOG.warn("StructScan: chunk {} failed", cp, t);
                } finally {
                    ColumnCache.end();
                }
                IntArrayList cand = chunks.get(packed);
                long share = (System.nanoTime() - began) / cand.size();
                for (int k = 0; k < cand.size(); k++) infos.get(cand.getInt(k)).nanos.addAndGet(share);
                int d = done.incrementAndGet();
                if (d % Math.max(1, total / 10) == 0) log.accept("StructScan: " + (d * 100L / total) + "% (" + found.size() + " structures)");
            })).get();
            log.accept("StructScan: sampling biome areas...");
            biomes = biomeArea(level, gen, rs, opt, pool);
        } finally {
            pool.shutdown();
        }
        log.accept("StructScan: cache " + ColumnCache.stats());
        return new Result(level.dimension().location().toString(), seed, opt.radius(), opt.centerX(), opt.centerZ(),
                new ArrayList<>(found), infos, total, errors.get(), System.currentTimeMillis() - t0, changed, biomes);
    }

    /**
     * Check against the real thing: takes the chunks within ±radius of the scan's centre through the game's
     * structure-starts step for real (this does write them to the world, so only use it on a throwaway world)
     * and compares what they hold with what the scan predicted.
     */
    static List<String> verify(ServerLevel level, Result r, int radius) {
        int R = (radius + 15) >> 4, cx0 = r.centerX() >> 4, cz0 = r.centerZ() >> 4;
        java.util.Set<String> predicted = new java.util.HashSet<>(), real = new java.util.HashSet<>();
        for (Start s : r.starts()) {
            ChunkPos cp = new ChunkPos(s.chunk());
            if (Math.abs(cp.x - cx0) <= R && Math.abs(cp.z - cz0) <= R) predicted.add(s.structure() + " @ chunk " + cp.x + "," + cp.z);
        }
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int x = cx0 - R; x <= cx0 + R; x++)
            for (int z = cz0 - R; z <= cz0 + R; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, true);
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet())
                    if (e.getValue().isValid()) real.add(reg.getKey(e.getKey()) + " @ chunk " + x + "," + z);
            }
        List<String> missed = real.stream().filter(k -> !predicted.contains(k)).sorted().toList();
        List<String> extra = predicted.stream().filter(k -> !real.contains(k)).sorted().toList();
        List<String> out = new ArrayList<>();
        out.add(String.format(java.util.Locale.ROOT, "Verify ±%d blocks: real world has %d structure starts, scan predicted %d; %d match, %d missed by the scan, %d predicted but not real",
                radius, real.size(), predicted.size(), real.size() - missed.size(), missed.size(), extra.size()));
        for (String m : missed) out.add("  missed: " + m);
        for (String m : extra) out.add("  extra: " + m);
        return out;
    }

    /** {top of solid ground, top of water} from one terrain column (the raw noise terrain, before surface blocks). */
    private static int[] heights(NoiseColumn col, int minY, int height) {
        int floor = minY, water = minY;
        boolean seenWater = false;
        for (int y = minY + height - 1; y >= minY; y--) {
            BlockState b = col.getBlock(y);
            if (b.isAir()) continue;
            if (!b.getFluidState().isEmpty() && !b.blocksMotion()) {
                if (!seenWater) { water = y + 1; seenWater = true; }
                continue;
            }
            floor = y + 1;
            break;
        }
        return new int[]{floor, seenWater ? water : floor};
    }

    /** A random-spread placement with spacing/separation (and frequency) scaled; null for other types. */
    private static StructurePlacement rescale(StructurePlacement p, Rules.Rule rule, SetInfo info) throws Exception {
        if (!(p instanceof RandomSpreadStructurePlacement r)) return null;
        // A mod's own random spread (Aquamirae's configurable one, Create Structures Arise's) becomes a plain one with
        // the same numbers; the real change would go through that mod's config instead.
        if (p.getClass() != RandomSpreadStructurePlacement.class) info.rule += " (as plain random_spread)";
        int spacing = Math.max(1, (int) Math.round(r.spacing() * rule.spacing()));
        int separation = Math.max(0, Math.min(spacing - 1, (int) Math.round(r.separation() * rule.spacing())));
        float freq = (float) Math.min(1.0, (float) call(p, "frequency") * rule.frequency());
        info.newSpacing = spacing;
        info.newSeparation = separation;
        info.newFrequency = freq;
        @SuppressWarnings("unchecked")
        Optional<StructurePlacement.ExclusionZone> zone = (Optional<StructurePlacement.ExclusionZone>) call(p, "exclusionZone");
        return new RandomSpreadStructurePlacement((Vec3i) call(p, "locateOffset"),
                (StructurePlacement.FrequencyReductionMethod) call(p, "frequencyReductionMethod"), freq,
                (int) call(p, "salt"), zone, spacing, separation, r.spreadType());
    }

    /** StructurePlacement's getters are protected; read them reflectively. */
    private static Object call(StructurePlacement p, String name) {
        try {
            Method m = StructurePlacement.class.getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("StructurePlacement." + name, e);
        }
    }

    private Scanner() {}
}

package dev.creategalore.structscan;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Remembers terrain heights, columns and climate samples (what biome lookups are made of) while a scan runs,
 * so structures that probe the same spots over and over (jigsaw pieces, Mowzie's chamber, Alex's Caves biome
 * searches) don't recompute the noise each time. The answers are exactly what the
 * generator would give: each height or column is computed once, by the generator itself. Only scan threads use it,
 * between begin() and end(); real world generation never sees it.
 */
public final class ColumnCache {
    private static final int SIZE = 16384, CLIMATE_SIZE = 262144;
    private static final ThreadLocal<Cache> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Cache> KEPT = new ThreadLocal<>();
    private static final AtomicLong HITS = new AtomicLong(), MISSES = new AtomicLong(), CLIMATE_HITS = new AtomicLong(), CLIMATE_MISSES = new AtomicLong();
    private static final Field MIN_Y, COLUMN;

    static {
        try {
            MIN_Y = NoiseColumn.class.getDeclaredField("minY");
            COLUMN = NoiseColumn.class.getDeclaredField("column");
            MIN_Y.setAccessible(true);
            COLUMN.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Column(int minY, BlockState[] states) {}

    private static final class Lru<V> extends LinkedHashMap<Long, V> {
        final int max;
        Lru() { this(SIZE); }
        Lru(int max) { super(1024, 0.75f, true); this.max = max; }
        @Override protected boolean removeEldestEntry(Map.Entry<Long, V> e) { return size() > max; }
    }

    private static final class Cache {
        final RandomState rs;
        final int generation;
        final Lru<Column> columns = new Lru<>();
        /** Heights are cached on their own: computing one stops at the surface, which is cheaper than a whole column. */
        @SuppressWarnings("unchecked")
        final Lru<Integer>[] heights = new Lru[Heightmap.Types.values().length];
        /** Climate samples (the noise behind every biome lookup), keyed by quart position, for this RandomState's sampler. */
        final Lru<Climate.TargetPoint> climate = new Lru<>(CLIMATE_SIZE);
        boolean computing;
        Cache(RandomState rs, int generation) { this.rs = rs; this.generation = generation; }
    }

    private static volatile int generation;

    /** Turns the cache on for this thread (kept between chunks, so neighbouring probes still hit). */
    static void begin(RandomState rs) {
        Cache c = KEPT.get();
        if (c == null || c.rs != rs || c.generation != generation) { c = new Cache(rs, generation); KEPT.set(c); }
        CURRENT.set(c);
    }

    static void end() { CURRENT.remove(); }

    /** Forgets everything (each thread drops its cache at its next begin), so the next scan's timing is honest. */
    static void clear() { generation++; }

    static String stats() {
        return "terrain " + ratio(HITS, MISSES) + ", climate " + ratio(CLIMATE_HITS, CLIMATE_MISSES);
    }

    private static String ratio(AtomicLong hits, AtomicLong misses) {
        long h = hits.getAndSet(0), m = misses.getAndSet(0);
        return String.format(java.util.Locale.ROOT, "%d hits / %d misses (%.0f%%)", h, m, h + m == 0 ? 0 : 100.0 * h / (h + m));
    }

    private static Cache active(RandomState rs) {
        Cache c = CURRENT.get();
        return c == null || c.computing || c.rs != rs ? null : c;
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static Column column(Cache c, NoiseBasedChunkGenerator gen, int x, int z, LevelHeightAccessor level, RandomState rs) {
        long key = key(x, z);
        Column col = c.columns.get(key);
        if (col != null) { HITS.incrementAndGet(); return col; }
        MISSES.incrementAndGet();
        c.computing = true;
        try {
            NoiseColumn nc = gen.getBaseColumn(x, z, level, rs);
            col = new Column(MIN_Y.getInt(nc), (BlockState[]) COLUMN.get(nc));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            c.computing = false;
        }
        c.columns.put(key, col);
        return col;
    }

    /** getBaseHeight from a cached column, or null to let the generator do it. */
    public static Integer height(NoiseBasedChunkGenerator gen, int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs) {
        Cache c = active(rs);
        if (c == null) return null;
        long key = key(x, z);
        Lru<Integer> heights = c.heights[type.ordinal()];
        if (heights == null) heights = c.heights[type.ordinal()] = new Lru<>();
        Integer h = heights.get(key);
        if (h != null) { HITS.incrementAndGet(); return h; }
        Column col = c.columns.get(key);
        if (col != null) {
            HITS.incrementAndGet();
            h = level.getMinBuildHeight();
            var test = type.isOpaque();
            for (int i = col.states().length - 1; i >= 0; i--)
                if (test.test(col.states()[i])) { h = col.minY() + i + 1; break; }
        } else {
            MISSES.incrementAndGet();
            c.computing = true;
            try {
                h = gen.getBaseHeight(x, z, type, level, rs);
            } finally {
                c.computing = false;
            }
        }
        heights.put(key, h);
        return h;
    }

    private static Lru<Climate.TargetPoint> climateCache(Climate.Sampler sampler) {
        Cache c = CURRENT.get();
        return c == null || c.rs.sampler() != sampler ? null : c.climate;
    }

    private static long quartKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
    }

    /** A cached climate sample, or null to let the sampler compute it. */
    public static Climate.TargetPoint climate(Climate.Sampler sampler, int x, int y, int z) {
        Lru<Climate.TargetPoint> m = climateCache(sampler);
        if (m == null) return null;
        Climate.TargetPoint t = m.get(quartKey(x, y, z));
        if (t != null) CLIMATE_HITS.incrementAndGet(); else CLIMATE_MISSES.incrementAndGet();
        return t;
    }

    public static void storeClimate(Climate.Sampler sampler, int x, int y, int z, Climate.TargetPoint t) {
        Lru<Climate.TargetPoint> m = climateCache(sampler);
        if (m != null) m.put(quartKey(x, y, z), t);
    }

    /** getBaseColumn from a cached column (a copy, since callers may change it), or null. */
    public static NoiseColumn baseColumn(NoiseBasedChunkGenerator gen, int x, int z, LevelHeightAccessor level, RandomState rs) {
        Cache c = active(rs);
        if (c == null) return null;
        Column col = column(c, gen, x, z, level, rs);
        return new NoiseColumn(col.minY(), col.states().clone());
    }

    private ColumnCache() {}
}

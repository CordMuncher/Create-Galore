package dev.creategalore.bridge.grid;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkModifiedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the far heights ({@link FarHeights}) from Distant Horizons' terrain data on a thread of its own: the nearest
 * section (16 x 16 cells, 64 x 64 blocks) the window holds that isn't read yet, or that Distant Horizons said changed,
 * or that had nothing last time and is due again. Only loaded when Distant Horizons is there.
 */
final class FarHeightsDh {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final int S = FarHeights.SECTIONS;
    private static final long RETRY_MS = 5000;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private static final Object LOCK = new Object();
    /** Per section slot (its section's place modulo the window, x and z): the section it holds (absolute). */
    private static final int[] heldX = new int[S * S];
    private static final int[] heldZ = new int[S * S];
    /** 0 to read, 1 read, 2 nothing there yet (try again at retryAt). */
    private static final byte[] state = new byte[S * S];
    private static final long[] retryAt = new long[S * S];
    private static volatile String dimension = "";

    private FarHeightsDh() {
    }

    static void start() {
        DhApi.events.bind(DhApiChunkModifiedEvent.class, new DhApiChunkModifiedEvent() {
            @Override
            public void onChunkModified(DhApiEventParam<DhApiChunkModifiedEvent.EventParam> event) {
                DhApiChunkModifiedEvent.EventParam p = event.value;
                if (p == null || p.levelWrapper == null || !dimension.equals(p.levelWrapper.getDimensionName())) {
                    return;
                }
                // A section is 4 x 4 chunks.
                int sx = Math.floorDiv(p.chunkX, 4);
                int sz = Math.floorDiv(p.chunkZ, 4);
                int slot = (sz & (S - 1)) * S + (sx & (S - 1));
                synchronized (LOCK) {
                    if (heldX[slot] == sx && heldZ[slot] == sz) {
                        state[slot] = 0;
                    }
                }
            }
        });
        Thread thread = new Thread(FarHeightsDh::run, "Create Galore far heights");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void run() {
        int generation = -1;
        int[] cx = new int[256];
        int[] cz = new int[256];
        int[] w = new int[256];
        while (true) {
            try {
                IDhApiWorldProxy world = DhApi.Delayed.worldProxy;
                IDhApiTerrainDataRepo repo = DhApi.Delayed.terrainRepo;
                if (FarHeights.level == null || world == null || repo == null || !world.worldLoaded()) {
                    Thread.sleep(1000);
                    continue;
                }
                IDhApiLevelWrapper level = world.getSinglePlayerLevel();
                if (level == null) {
                    Thread.sleep(1000);
                    continue;
                }
                int gen = FarHeights.generation;
                if (gen != generation) {
                    generation = gen;
                    synchronized (LOCK) {
                        java.util.Arrays.fill(state, (byte) 0);
                        java.util.Arrays.fill(heldX, Integer.MIN_VALUE);
                    }
                    dimension = level.getDimensionName();
                }
                // The nearest section of the window that needs reading.
                int sx0 = Math.floorDiv(FarHeights.lowX, FarHeights.STEP);
                int sz0 = Math.floorDiv(FarHeights.lowZ, FarHeights.STEP);
                long now = System.currentTimeMillis();
                int best = -1;
                int bestDist = Integer.MAX_VALUE;
                synchronized (LOCK) {
                    for (int j = 0; j < S; j++) {
                        for (int i = 0; i < S; i++) {
                            int sx = sx0 + i;
                            int sz = sz0 + j;
                            int slot = (sz & (S - 1)) * S + (sx & (S - 1));
                            boolean held = heldX[slot] == sx && heldZ[slot] == sz;
                            boolean need = !held || state[slot] == 0 || (state[slot] == 2 && now >= retryAt[slot]);
                            if (!need) {
                                continue;
                            }
                            int di = i - S / 2;
                            int dj = j - S / 2;
                            int dist = di * di + dj * dj;
                            if (dist < bestDist) {
                                bestDist = dist;
                                best = j * S + i;
                            }
                        }
                    }
                }
                if (best < 0) {
                    Thread.sleep(200);
                    continue;
                }
                int sx = sx0 + best % S;
                int sz = sz0 + best / S;
                int slot = (sz & (S - 1)) * S + (sx & (S - 1));
                synchronized (LOCK) {
                    heldX[slot] = sx;
                    heldZ[slot] = sz;
                    state[slot] = 1; // a change while it's read sets it back to 0
                }
                int n = 0;
                // A cache of its own for each section (one of Distant Horizons' data sources, 64 x 64 blocks), so a
                // section read again after a change never gets the copy from before it.
                IDhApiTerrainDataCache cache = repo.createSoftCache();
                try {
                    for (int j = 0; j < FarHeights.STEP; j++) {
                        for (int i = 0; i < FarHeights.STEP; i++) {
                            int cellX = sx * FarHeights.STEP + i;
                            int cellZ = sz * FarHeights.STEP + j;
                            int bx = cellX * FarHeights.CELL;
                            int bz = cellZ * FarHeights.CELL;
                            int top = Math.max(top(repo, level, bx + 1, bz + 1, cache),
                                    top(repo, level, bx + 3, bz + 3, cache));
                            if (top == UNKNOWN) {
                                continue;
                            }
                            cx[n] = cellX;
                            cz[n] = cellZ;
                            w[n] = FarHeights.word(cellX, cellZ, top);
                            n++;
                        }
                    }
                } finally {
                    cache.close();
                }
                if (n == 0) {
                    synchronized (LOCK) {
                        if (state[slot] == 1) {
                            state[slot] = 2;
                            retryAt[slot] = System.currentTimeMillis() + RETRY_MS;
                        }
                    }
                } else {
                    FarHeights.put(generation, cx, cz, w, n);
                }
                Thread.sleep(1); // leave Distant Horizons its own threads' time
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                LOG.warn("Far heights: reading Distant Horizons' terrain failed ({}); trying again shortly", e.toString());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    /** The top of the ground in one column (the highest block that isn't air or a liquid), or UNKNOWN. */
    private static int top(IDhApiTerrainDataRepo repo, IDhApiLevelWrapper level, int x, int z, IDhApiTerrainDataCache cache) {
        DhApiResult<DhApiTerrainDataPoint[]> result = repo.getColumnDataAtBlockPos(level, x, z, cache);
        if (result == null || !result.success || result.payload == null || result.payload.length == 0) {
            return UNKNOWN;
        }
        int top = level.getMinHeight();
        for (DhApiTerrainDataPoint p : result.payload) {
            if (p == null || p.blockStateWrapper == null || p.blockStateWrapper.isAir() || p.blockStateWrapper.isLiquid()) {
                continue;
            }
            top = Math.max(top, p.topYBlockPos);
        }
        return top;
    }
}

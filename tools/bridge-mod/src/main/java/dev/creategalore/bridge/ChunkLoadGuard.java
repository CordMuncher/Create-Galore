package dev.creategalore.bridge;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Flags for the places where a mod asked for a chunk and waited for it on the server thread. When that chunk was
 * never generated, the server generated it right there and froze for 5 to 20 s. Server thread only.
 */
public final class ChunkLoadGuard {
    /**
     * Set while Aeronautics Discovery force-loads the chunks for an airship it is about to assemble, or under one it
     * is despawning. NeoForge's forceChunk then skips its "load it right now" call. The forced ticket still loads
     * the chunks in the background, and Discovery already waits until every one of them is ticking before it builds
     * the ship or clears its crew.
     */
    public static boolean deferForcedLoads;

    /**
     * Set while Sable keeps the terrain around an airship nobody is aboard (Discovery's flyovers) loaded. Its
     * chunk ticket still loads the terrain in the background; the ship gets that terrain's collision a moment
     * later. Ships with a player aboard still wait for their terrain, as Sable intends.
     */
    public static boolean deferUnmannedShipLoads;

    /**
     * Set while Sable updates the physics collision of a world block that just changed. It reads the neighbours
     * around that block, and when one sits in a chunk that is still being generated (water settling in a freshly
     * generated chunk at the edge of the loaded area), the server thread waited for it, and that chunk's own
     * water then pulled in the next one: freezes of 5 to 16 s while exploring. A neighbour in a chunk that isn't
     * ready counts as air for that one update instead.
     */
    public static boolean sableTerrainUpdate;

    /**
     * True when the chunk holding this spot is fully loaded right now. Unlike {@link Level#isLoaded}, which is
     * already true while a chunk with a ticket is still being generated, reading a block here can never make the
     * server thread wait. Off the server thread the server never hands out chunks directly, so there it falls back to
     * isLoaded (and a read there doesn't stall a tick).
     */
    public static boolean ready(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) return level.isLoaded(pos);
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /**
     * How far one flow of water reads the world: it lands a block away, wakes that block's neighbours, and each woken
     * water block looks at its own neighbours (NeoForge's fluid mixing check). That is three blocks out; one more
     * covers a block the wake-up changes. Two blocks (pack 0.4.52) still let a 2.3 s freeze through.
     */
    public static final int FLUID_REACH = 4;

    /** True when every chunk within this many blocks (sideways) of the spot is fully loaded right now. See {@link #ready}. */
    public static boolean readyAround(Level level, BlockPos pos, int blocks) {
        for (int cx = (pos.getX() - blocks) >> 4; cx <= (pos.getX() + blocks) >> 4; cx++) {
            for (int cz = (pos.getZ() - blocks) >> 4; cz <= (pos.getZ() + blocks) >> 4; cz++) {
                if (!ready(level, new BlockPos(cx << 4, pos.getY(), cz << 4))) return false;
            }
        }
        return true;
    }

    /**
     * How far lava (or any fluid that mixes) reads the world when a neighbour wakes it: the blocks touching it, then
     * the shape updates of the stone it may turn into reach one block further.
     */
    public static final int MIXING_REACH = 2;

    /** Fluid blocks whose mixing waited for a chunk to finish loading, per world. Server thread only. */
    private static final Map<Level, LongOpenHashSet> MIXING_LATER = new WeakHashMap<>();
    /** A spot leaves the list when its fluid ticks; one whose fluid was removed first never does, so the list is capped. */
    private static final int MIXING_LATER_MAX = 4096;

    /** Remembers that the fluid here should check for mixing at its next tick. */
    public static void mixLater(Level level, BlockPos pos) {
        LongOpenHashSet later = MIXING_LATER.computeIfAbsent(level, l -> new LongOpenHashSet());
        if (later.size() >= MIXING_LATER_MAX) later.clear();
        later.add(pos.asLong());
    }

    /** True (once) when the fluid here was told to check for mixing later. */
    public static boolean takeMixLater(Level level, BlockPos pos) {
        LongOpenHashSet later = MIXING_LATER.get(level);
        return later != null && !later.isEmpty() && later.remove(pos.asLong());
    }

    private ChunkLoadGuard() {}
}

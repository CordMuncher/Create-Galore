package dev.creategalore.bridge;

/**
 * Flags for the places where a mod asked for a chunk and waited for it on the server thread. When that chunk was
 * never generated, the server generated it right there and froze for 5 to 20 s. Server thread only.
 */
public final class ChunkLoadGuard {
    /**
     * Set while Aeronautics Discovery force-loads the chunks for an airship it is about to assemble. NeoForge's
     * forceChunk then skips its "load it right now" call. The forced ticket still loads the chunks in the
     * background, and Discovery already waits until every one of them is ticking before it builds the ship.
     */
    public static boolean deferForcedLoads;

    /**
     * Set while Sable keeps the terrain around an airship nobody is aboard (Discovery's flyovers) loaded. Its
     * chunk ticket still loads the terrain in the background; the ship gets that terrain's collision a moment
     * later. Ships with a player aboard still wait for their terrain, as Sable intends.
     */
    public static boolean deferUnmannedShipLoads;

    private ChunkLoadGuard() {}
}

package dev.creategalore.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A per-player token bucket for requests a client can send as often as it likes (a modified client ignores the
 * Bridge's own pacing): up to {@code burst} at once, then {@code perSecond} on average. Buckets of players who have
 * been quiet long enough to be full again are dropped when many pile up, so the map stays small, and leaving and
 * rejoining doesn't refill one early.
 */
public final class PlayerRateLimit {
    private static final int PRUNE_ABOVE = 256;

    private final double burst;
    private final double perNano;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    private static final class Bucket {
        double tokens;
        long at;
    }

    public PlayerRateLimit(int burst, double perSecond) {
        this.burst = burst;
        this.perNano = perSecond / 1e9;
    }

    /** Takes one request's token; false when the player is over the limit (the request should be dropped). */
    public synchronized boolean tryAcquire(UUID player) {
        long now = System.nanoTime();
        if (buckets.size() > PRUNE_ABOVE) {
            buckets.values().removeIf(b -> refill(b, now) >= burst);
        }
        Bucket b = buckets.get(player);
        if (b == null) {
            b = new Bucket();
            b.tokens = burst;
            b.at = now;
            buckets.put(player, b);
        }
        if (refill(b, now) < 1) {
            return false;
        }
        b.tokens -= 1;
        return true;
    }

    private double refill(Bucket b, long now) {
        b.tokens = Math.min(burst, b.tokens + (now - b.at) * perNano);
        b.at = now;
        return b.tokens;
    }
}

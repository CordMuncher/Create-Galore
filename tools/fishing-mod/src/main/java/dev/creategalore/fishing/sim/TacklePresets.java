package dev.creategalore.fishing.sim;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference rigs for balancing, one per progression step. They mirror the default tackle data (a typical blank,
 * reel, line and hook of each tier); the game builds real {@link Tackle} values from the parts on a rod instead.
 */
public final class TacklePresets {
    /** A rig plus the hook and bait size an angler would use with it. */
    public record Preset(String name, Tackle tackle, int hookIndex, int baitSize, double castDistance) {
    }

    private TacklePresets() {
    }

    public static final Map<String, Preset> ALL = new LinkedHashMap<>();

    static {
        // Bamboo pole: no reel, short fixed line of string, lifts small fish out.
        add("pole", new Tackle(4, 0.8, false, 0, 0.9, 4, 0, 8, 3, 0.8, Double.NaN, false, 1), 1, 0, 6);
        // Wooden rod, copper spinning reel, string line: the first real rig.
        add("starter", new Tackle(5, 0.6, true, 3, 1.0, 6, 0.3, 100, 4, 0.7, Double.NaN, false, 1), 3, 0, 18);
        // Iron rod and reel, mono line.
        add("iron", new Tackle(9, 0.5, true, 6, 1.2, 10, 0.5, 150, 8, 0.5, Double.NaN, false, 1), 5, 1, 25);
        // Steel telescopic rod, brass precision reel, braid with a clear leader.
        add("steel", new Tackle(16, 0.45, true, 10, 1.4, 16, 0.85, 200, 18, 0.1, 12, false, 1), 7, 1, 32);
        // Heavy boat rod, big-game reel, heavy mono with a steel leader: tuna and sharks, from holders.
        add("boat", new Tackle(45, 0.6, true, 30, 1.8, 45, 0.8, 600, 50, 0.45, 60, true, 1), 10, 2, 30);
    }

    private static void add(String name, Tackle tackle, int hook, int bait, double cast) {
        ALL.put(name, new Preset(name, tackle, hook, bait, cast));
    }
}

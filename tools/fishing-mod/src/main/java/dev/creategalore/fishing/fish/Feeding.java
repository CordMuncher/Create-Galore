package dev.creategalore.fishing.fish;

import com.mojang.serialization.Codec;
import dev.creategalore.fishing.bait.BaitKind;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.StringRepresentable;

/**
 * Broad feeding habits. Each gives a default diet, so only species that eat something unusual need their own diet
 * in data.
 */
public enum Feeding implements StringRepresentable {
    /** Small fish that pick at worms, maggots and insects. */
    PANFISH,
    /** Freshwater hunters: live fish and lures first. */
    PREDATOR,
    /** Saltwater hunters: live fish, squid, cut bait, lures. */
    SEA_PREDATOR,
    /** Freshwater bottom feeders: dough, boilies, worms. */
    BOTTOM,
    /** Saltwater bottom feeders: crustaceans, cut bait, squid. */
    SEA_BOTTOM,
    /** Reef and other small sea fish. */
    REEF,
    /** Lava fish: their own bait, anything else only now and then. */
    LAVA,
    /** Void fish: their own bait, anything else only now and then. */
    VOID;

    public static final Codec<Feeding> CODEC = StringRepresentable.fromEnum(Feeding::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The default preference (0..1) of this feeding type for every bait kind it cares about; others are 0.1. */
    public Map<BaitKind, Double> diet() {
        Map<BaitKind, Double> d = new EnumMap<>(BaitKind.class);
        switch (this) {
            case PANFISH -> {
                put(d, BaitKind.WORM, 1.0); put(d, BaitKind.MAGGOT, 0.9); put(d, BaitKind.INSECT, 0.8);
                put(d, BaitKind.DOUGH, 0.5); put(d, BaitKind.LEECH, 0.4); put(d, BaitKind.JIG, 0.4);
                put(d, BaitKind.SOFT_PLASTIC, 0.35); put(d, BaitKind.SPINNER, 0.3);
            }
            case PREDATOR -> {
                put(d, BaitKind.LIVE_FISH, 1.0); put(d, BaitKind.SOFT_PLASTIC, 0.9); put(d, BaitKind.SPINNER, 0.85);
                put(d, BaitKind.SPOON, 0.85); put(d, BaitKind.CRANKBAIT, 0.8); put(d, BaitKind.JIG, 0.75);
                put(d, BaitKind.TOPWATER, 0.6); put(d, BaitKind.LEECH, 0.6); put(d, BaitKind.WORM, 0.5);
                put(d, BaitKind.CUT_BAIT, 0.5); put(d, BaitKind.INSECT, 0.3);
            }
            case SEA_PREDATOR -> {
                put(d, BaitKind.LIVE_FISH, 1.0); put(d, BaitKind.SQUID, 0.9); put(d, BaitKind.CUT_BAIT, 0.85);
                put(d, BaitKind.SPOON, 0.8); put(d, BaitKind.CRANKBAIT, 0.8); put(d, BaitKind.JIG, 0.75);
                put(d, BaitKind.SOFT_PLASTIC, 0.7); put(d, BaitKind.TOPWATER, 0.5); put(d, BaitKind.CRUSTACEAN, 0.5);
            }
            case BOTTOM -> {
                put(d, BaitKind.DOUGH, 1.0); put(d, BaitKind.BOILIE, 1.0); put(d, BaitKind.WORM, 0.85);
                put(d, BaitKind.MAGGOT, 0.6); put(d, BaitKind.CUT_BAIT, 0.5); put(d, BaitKind.LEECH, 0.5);
                put(d, BaitKind.INSECT, 0.2);
            }
            case SEA_BOTTOM -> {
                put(d, BaitKind.CRUSTACEAN, 1.0); put(d, BaitKind.CUT_BAIT, 0.9); put(d, BaitKind.SQUID, 0.85);
                put(d, BaitKind.WORM, 0.6); put(d, BaitKind.JIG, 0.4);
            }
            case REEF -> {
                put(d, BaitKind.CRUSTACEAN, 0.9); put(d, BaitKind.SQUID, 0.7); put(d, BaitKind.CUT_BAIT, 0.7);
                put(d, BaitKind.WORM, 0.5); put(d, BaitKind.JIG, 0.4); put(d, BaitKind.DOUGH, 0.3);
            }
            case LAVA -> {
                put(d, BaitKind.LAVA_BAIT, 1.0);
                for (BaitKind k : BaitKind.values()) d.putIfAbsent(k, 0.25);
            }
            case VOID -> {
                put(d, BaitKind.VOID_BAIT, 1.0);
                for (BaitKind k : BaitKind.values()) d.putIfAbsent(k, 0.25);
            }
        }
        for (BaitKind k : BaitKind.values()) d.putIfAbsent(k, 0.1);
        return d;
    }

    private static void put(Map<BaitKind, Double> d, BaitKind k, double v) {
        d.put(k, v);
    }
}

package dev.creategalore.fishing.sim;
import java.util.random.RandomGenerator;

/**
 * Sizes of one species: how long individual fish are, how much they weigh, which size class they fall in, and
 * how hooks and baits select among them.
 *
 * <p>Lengths come from Tide's data (typical range and record, in cm). Weight follows the length-weight relation
 * fisheries biologists use, {@code W = a * L^b} (grams, centimetres), where {@code b} is close to 3 and {@code a}
 * depends on body shape: about 0.0015 for eels, 0.006 for pike, 0.01 for trout, 0.018 for carp, 0.025 for
 * deep-bodied panfish.
 *
 * @param typicalLow  shortest usual length, cm
 * @param typicalHigh longest usual length, cm
 * @param record      the species' record length, cm
 * @param a           length-weight coefficient (g / cm^b)
 * @param b           length-weight exponent
 */
public record SizeModel(double typicalLow, double typicalHigh, double record, double a, double b) {
    /** Hook sizes from small to large, as anglers write them; the index is what the code uses. */
    public static final String[] HOOK_SIZES = {"#12", "#10", "#8", "#6", "#4", "#2", "#1", "1/0", "2/0", "4/0", "6/0", "8/0"};

    public SizeModel {
        if (typicalLow <= 0) typicalLow = 1;
        if (typicalHigh < typicalLow) typicalHigh = typicalLow;
        if (record < typicalHigh) record = typicalHigh;
    }

    /** Weight in kg of a fish {@code lengthCm} long. */
    public double weightKg(double lengthCm) {
        return a * Math.pow(lengthCm, b) / 1000;
    }

    /** Length in cm of a fish weighing {@code kg} (inverse of {@link #weightKg}). */
    public double lengthCm(double kg) {
        return Math.pow(kg * 1000 / a, 1 / b);
    }

    /**
     * The usual length of a fish of size class {@code size}: the middle of the class's band (for a young fish, a bit
     * under the usual range). For fish whose exact size wasn't kept.
     */
    public double usualLengthCm(SizeClass size) {
        double over = record - typicalHigh;
        return switch (size) {
            case YOUNG -> typicalLow * 0.8;
            case COMMON -> (typicalLow + typicalHigh) / 2;
            case LARGE -> typicalHigh + over * 0.25;
            case TROPHY -> typicalHigh + over * 0.675;
            case UNIQUE -> typicalHigh + over * 0.925;
        };
    }

    /**
     * The lengths a fish of size class {@code size} can have, {lowest, highest} in cm: the class's band in
     * {@link #classify}, a young fish from the shortest {@link #sampleLength} draws.
     */
    public double[] lengthBandCm(SizeClass size) {
        double over = record - typicalHigh;
        return switch (size) {
            case YOUNG -> new double[] {typicalLow * 0.55, typicalLow};
            case COMMON -> new double[] {typicalLow, typicalHigh};
            case LARGE -> new double[] {typicalHigh, typicalHigh + over * 0.5};
            case TROPHY -> new double[] {typicalHigh + over * 0.5, typicalHigh + over * 0.85};
            case UNIQUE -> new double[] {typicalHigh + over * 0.85, record};
        };
    }

    /** The weight of a middle-of-the-range fish, for "how big was it" comparisons. */
    public double typicalWeightKg() {
        return weightKg((typicalLow + typicalHigh) / 2);
    }

    /**
     * Draws a length: about 12% young fish below the usual range, 78% in it (bunched toward the middle), 10% above
     * it with a tail that thins out toward the record.
     */
    public double sampleLength(RandomGenerator rng) {
        double u = rng.nextDouble();
        if (u < 0.12) return typicalLow * (0.55 + 0.45 * rng.nextDouble());
        if (u < 0.90) return typicalLow + (typicalHigh - typicalLow) * (rng.nextDouble() + rng.nextDouble()) / 2;
        double tail = Math.min(1, -Math.log(1 - rng.nextDouble()) * 0.25);
        return typicalHigh + (record - typicalHigh) * tail;
    }

    public SizeClass classify(double lengthCm) {
        if (lengthCm < typicalLow) return SizeClass.YOUNG;
        if (lengthCm <= typicalHigh) return SizeClass.COMMON;
        double into = (lengthCm - typicalHigh) / Math.max(1e-6, record - typicalHigh);
        if (into < 0.5) return SizeClass.LARGE;
        if (into < 0.85) return SizeClass.TROPHY;
        return SizeClass.UNIQUE;
    }

    /** The hook size index that suits a fish {@code lengthCm} long: #12 for a 5 cm fish, 4/0 for a 2 m one. */
    public static double idealHook(double lengthCm) {
        return Math.max(0, Math.min(HOOK_SIZES.length - 1, 1.6 * Math.log(Math.max(1, lengthCm) / 5) / Math.log(2)));
    }

    /**
     * Chance a fish of this length takes a hook of size {@code hookIndex}. Hooks too big for its mouth scare it off
     * (so a big hook filters out small fish); hooks too small are taken but hold badly (see {@link #hookHold}).
     */
    public static double hookAcceptance(int hookIndex, double lengthCm) {
        double d = hookIndex - idealHook(lengthCm);
        return d <= 0 ? 1 : Math.exp(-d * d / 2);
    }

    /** How well a hook of {@code hookIndex} holds in the mouth of a fish this long, 0.15..1. */
    public static double hookHold(int hookIndex, double lengthCm) {
        double d = hookIndex - idealHook(lengthCm);
        return d >= 0 ? 1 : Math.max(0.15, 1 + 0.18 * d);
    }

    /**
     * Chance a fish this long goes for a bait of the given size (0 small, 1 medium, 2 large): softer than hooks,
     * but a big bait still finds bigger fish.
     */
    public static double baitAcceptance(int baitSize, double lengthCm) {
        double ideal = baitSize <= 0 ? 15 : baitSize == 1 ? 40 : 100;
        double octaves = Math.log(Math.max(1, lengthCm) / ideal) / Math.log(2);
        return Math.exp(-octaves * octaves / (2 * 1.8 * 1.8));
    }

    /**
     * Draws the length of the fish that actually bites, given the hook and bait, by rejection sampling over
     * {@link #sampleLength}. Returns {@code NaN} if nothing in this species would take this rig (after a fair
     * number of tries), which the bite code treats as "this species didn't bite".
     */
    public double sampleBitingLength(RandomGenerator rng, int hookIndex, int baitSize) {
        for (int i = 0; i < 24; i++) {
            double length = sampleLength(rng);
            double accept = hookAcceptance(hookIndex, length) * baitAcceptance(baitSize, length);
            if (rng.nextDouble() < accept) return length;
        }
        return Double.NaN;
    }

    /** About how readily this species bites on a rig, 0..1, averaged over its sizes (deterministic). */
    public double rigAppeal(int hookIndex, int baitSize) {
        double total = 0;
        int n = 0;
        for (double t = 0.05; t < 1; t += 0.1, n++) {
            double length = typicalLow + (typicalHigh - typicalLow) * t;
            total += hookAcceptance(hookIndex, length) * baitAcceptance(baitSize, length);
        }
        return total / n;
    }
}

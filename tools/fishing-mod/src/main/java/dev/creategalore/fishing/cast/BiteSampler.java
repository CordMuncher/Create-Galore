package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.bait.BaitKind;
import dev.creategalore.fishing.fish.Feeding;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TacklePart;
import dev.creategalore.fishing.tide.data.FishingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * Who bites, and how soon, at one spot with one rig.
 *
 * <p>Every species that can live here (Tide's conditions) starts from its Tide selection weight, scaled by how much
 * it likes the bait or lure, how well the hook and bait sizes suit its mouth, how much a wary fish minds the line,
 * and for lures whether the lure is moving at a speed it works at. The sum, compared to the spot's full potential,
 * sets the average wait for a bite: a well-matched rig in a good spot bites every quarter minute or so, a bare
 * hook in the wrong water may wait minutes. Pressure, enchantments, hooks and weather shift the wait further.
 */
public final class BiteSampler {
    private BiteSampler() {
    }

    /** A fish that took the bait: species and size, decided at the bite. */
    public record Bite(Species species, double lengthCm, double kg, SizeClass size) {
    }

    /** The species that could bite here with this rig, and their weights. */
    public record Pool(List<Species> species, double[] weights, double total, double quality) {
        public static final Pool EMPTY = new Pool(List.of(), new double[0], 0, 0);
    }

    /** What the rig offers a fish. */
    record Offer(BaitKind kind, int size, boolean live, int hook, double lineVisibility, boolean lure) {
    }

    static Offer offer(RodStats rod) {
        Optional<TacklePart> terminal = rod.terminal();
        double visibility = rod.leader().map(TacklePart.Leader::visibility)
                .orElse(rod.line().map(TacklePart.Line::visibility).orElse(0.5));
        if (terminal.isPresent() && terminal.get() instanceof TacklePart.Lure lure) {
            return new Offer(lure.bait(), lure.size(), false, rod.hookSize(), visibility, true);
        }
        return rod.bait().map(b -> new Offer(b.bait(), b.size(), b.live(), rod.hookSize(), visibility, false))
                .orElse(new Offer(null, 0, false, rod.hookSize(), visibility, false));
    }

    /**
     * The bite pool at {@code context} for {@code rod}. {@code lureWorking} is how well a lure is being worked (1 at a
     * good speed, low when it just sits); ignored for bait.
     */
    public static Pool pool(FishingContext context, RodStats rod, double lureWorking) {
        Offer offer = offer(rod);
        List<Species> kept = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        double potential = 0;
        for (Species s : SpeciesManager.all()) {
            if (!s.medium().equals(context.medium())) continue;
            if (!s.tide().canBiteAt(context)) continue;
            double base = s.tide().weightAt(context);
            if (base <= 0) continue;
            potential += base;
            double pref = offer.kind() == null ? 0.05 : s.preference(offer.kind());
            if (offer.live() && (s.feeding() == Feeding.PREDATOR || s.feeding() == Feeding.SEA_PREDATOR)) pref *= 1.5;
            double fit = s.size().rigAppeal(offer.hook(), offer.size());
            double seen = 1 - s.wary() * offer.lineVisibility() * 0.7;
            double worked = offer.lure() ? 0.25 + 0.75 * lureWorking : 1;
            double w = base * pref * fit * seen * worked;
            if (w <= 0) continue;
            kept.add(s);
            weights.add(w);
            total += w;
        }
        double[] arr = weights.stream().mapToDouble(Double::doubleValue).toArray();
        return new Pool(kept, arr, total, potential <= 0 ? 0 : Math.min(1, total / potential));
    }

    /**
     * Average seconds to a bite for this pool before outside factors: about 17 s for a perfect match, a minute for a
     * poor one, two for nothing that wants the rig.
     */
    public static double meanWaitSeconds(Pool pool) {
        return 40 / (0.3 + 2.0 * Math.sqrt(pool.quality()));
    }

    /** Picks the fish that bites, or empty if the one that came couldn't take this hook (a nibble). */
    public static Optional<Bite> roll(Pool pool, RodStats rod, RandomSource rng) {
        if (pool.total() <= 0) return Optional.empty();
        double pick = rng.nextDouble() * pool.total();
        int index = 0;
        for (; index < pool.weights().length - 1; index++) {
            pick -= pool.weights()[index];
            if (pick <= 0) break;
        }
        Species s = pool.species().get(index);
        Offer offer = offer(rod);
        double length = s.size().sampleBitingLength(adapt(rng), offer.hook(), offer.size());
        if (Double.isNaN(length)) return Optional.empty();
        return Optional.of(new Bite(s, length, s.size().weightKg(length), s.size().classify(length)));
    }

    /** The game's random source as the sim's random generator. */
    public static java.util.random.RandomGenerator adapt(RandomSource rng) {
        return new java.util.random.RandomGenerator() {
            @Override
            public long nextLong() {
                return rng.nextLong();
            }

            @Override
            public double nextDouble() {
                return rng.nextDouble();
            }

            @Override
            public double nextGaussian() {
                return rng.nextGaussian();
            }
        };
    }
}

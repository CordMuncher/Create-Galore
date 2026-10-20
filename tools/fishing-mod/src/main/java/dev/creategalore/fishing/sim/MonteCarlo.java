package dev.creategalore.fishing.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Runs many fights to see how a species plays against each rig: how often it lands, snaps the line, breaks the
 * rod or throws the hook, how long fights take. Used by {@code /cgfish simulate} in game and standalone:
 *
 * <pre>java -cp create-galore-fishing.jar dev.creategalore.fishing.sim.MonteCarlo species.csv [fights] [seed]</pre>
 *
 * where {@code species.csv} is what {@code /cgfish simulate export} writes (one species per line). Fixed seeds
 * make runs repeatable.
 */
public final class MonteCarlo {
    /** One species as the runner needs it. */
    public record Species(String id, FishFight fight, SizeModel size) {
    }

    /** Counts for one species, rig and angler. */
    public record Result(String species, String rig, String angler, int fights, int noBite,
                         Map<Outcome, Integer> outcomes, double avgSeconds, double avgWeight, double avgDifficulty,
                         double avgReelWear, double avgRodWear) {
        public double rate(Outcome o) {
            int bitten = fights - noBite;
            return bitten == 0 ? 0 : outcomes.getOrDefault(o, 0) / (double) bitten;
        }

        public static String header() {
            return "species,rig,angler,fights,no_bite,landed,snapped,bitten_off,rod_broke,hook_thrown,spooled,timeout,avg_s,avg_kg,avg_difficulty,avg_reel_wear,avg_rod_wear";
        }

        public String csv() {
            return String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.2f,%.2f,%.2f,%.2f",
                    species, rig, angler, fights, noBite, rate(Outcome.LANDED), rate(Outcome.LINE_SNAPPED),
                    rate(Outcome.BITTEN_OFF), rate(Outcome.ROD_BROKE), rate(Outcome.HOOK_THROWN), rate(Outcome.SPOOLED),
                    rate(Outcome.FIGHTING), avgSeconds, avgWeight, avgDifficulty, avgReelWear, avgRodWear);
        }
    }

    /** Fights are cut off after this many ticks (10 minutes) and counted as still fighting. */
    public static final int MAX_TICKS = 20 * 600;

    private MonteCarlo() {
    }

    public static Result run(Species species, TacklePresets.Preset rig, Supplier<Angler> anglers, int fights, long seed) {
        RandomGenerator rng = new SplittableRandom(seed);
        Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        int noBite = 0;
        double seconds = 0, weight = 0, difficulty = 0, reelWear = 0, rodWear = 0;
        String anglerName = anglers.get().name();
        Controls controls = new Controls();
        for (int i = 0; i < fights; i++) {
            double length = species.size().sampleBitingLength(rng, rig.hookIndex(), rig.baitSize());
            if (Double.isNaN(length)) {
                noBite++;
                continue;
            }
            double kg = species.size().weightKg(length);
            Tackle tackle = rig.tackle().withHookHold(SizeModel.hookHold(rig.hookIndex(), length));
            Fight fight = new Fight(species.fight(), tackle, kg, rig.castDistance());
            Angler angler = anglers.get();
            while (!fight.outcome().over() && fight.ticks() < MAX_TICKS) {
                angler.act(fight, controls);
                fight.tick(controls, rng);
            }
            outcomes.merge(fight.outcome(), 1, Integer::sum);
            seconds += fight.seconds();
            weight += kg;
            difficulty += fight.difficulty();
            reelWear += fight.reelWear();
            rodWear += fight.rodWear();
        }
        int bitten = Math.max(1, fights - noBite);
        return new Result(species.id(), rig.name(), anglerName, fights, noBite, outcomes, seconds / bitten,
                weight / bitten, difficulty / bitten, reelWear / bitten, rodWear / bitten);
    }

    /** Every rig preset against one species, for the careful and the careless player. */
    public static List<Result> runAll(Species species, int fights, long seed) {
        List<Result> results = new ArrayList<>();
        for (TacklePresets.Preset rig : TacklePresets.ALL.values()) {
            results.add(run(species, rig, Angler::careful, fights, seed));
            results.add(run(species, rig, Angler::careless, fights, seed));
        }
        return results;
    }

    /**
     * Columns: id, fight_per_kg, stamina, speed, profile (preset name), runs, jumps, charges, toothy (true/false),
     * typical_low, typical_high, record, a, b. A header line starting with "id" is skipped.
     */
    public static List<Species> readCsv(Path file) throws IOException {
        List<Species> species = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("id,")) continue;
            String[] f = line.split(",");
            Curve profile = Curve.preset(f[4]);
            if (profile == null) profile = Curve.preset("middle");
            FishFight fight = new FishFight(d(f[1]), d(f[2]), d(f[3]), profile, d(f[5]), d(f[6]), d(f[7]),
                    Boolean.parseBoolean(f[8]));
            species.add(new Species(f[0], fight, new SizeModel(d(f[9]), d(f[10]), d(f[11]), d(f[12]), d(f[13]))));
        }
        return species;
    }

    private static double d(String s) {
        return Double.parseDouble(s.trim());
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: MonteCarlo species.csv [fights=400] [seed=1]");
            System.exit(2);
        }
        int fights = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        System.out.println(Result.header());
        for (Species species : readCsv(Path.of(args[0]))) {
            for (Result r : runAll(species, fights, seed)) System.out.println(r.csv());
        }
    }
}

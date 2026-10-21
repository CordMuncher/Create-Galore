package dev.creategalore.fishing.sim;

/**
 * How one species fights, independent of the individual fish's size. All values come from data files (with
 * defaults derived from Tide's old minigame numbers), so tuning a fish never needs code.
 *
 * @param fightPerKg kilograms of pull (kgf) per kilogram of fish at full intensity. Strong, muscular fish
 *                   (tuna, trout) are near 1.2, lazy ones (carp, catfish) near 0.6. Very heavy fish are tapered by
 *                   {@link #effectiveWeight}, so a 600 kg shark is brutal but not physically impossible.
 * @param stamina    how long the fish takes to tire, relative to an average fish (1). Bigger fish also last
 *                   longer on their own; this is the per-species multiplier on top.
 * @param speed      how fast it swims away at full intensity, in metres per second. This is how fast it takes line
 *                   when the drag slips.
 * @param profile    intensity over the fight, see {@link Curve}.
 * @param runs       chance per second, at full intensity, of a run: a burst of extra pull and speed for a few
 *                   seconds. Darting fish (mackerel, sailfish) have high values.
 * @param jumps      chance per second of a jump or head shake once the fish is in the last third of the fight. A
 *                   jump on a slack line can throw the hook.
 * @param charges    chance per second of the fish swimming toward the angler, which slackens the line unless the
 *                   angler keeps reeling.
 * @param toothy     bites through any line but a steel leader when it shakes its head.
 */
public record FishFight(double fightPerKg, double stamina, double speed, Curve profile,
                        double runs, double jumps, double charges, boolean toothy) {

    /** A plain, average fish; used when a species has no fight data at all. */
    public static final FishFight AVERAGE = new FishFight(0.8, 1.0, 1.6, Curve.preset("middle"), 0.05, 0.05, 0.05, false);

    /** Weight that counts toward pull: the real weight up to 20 kg, then growing only logarithmically. */
    public static double effectiveWeight(double kg) {
        return kg <= 20 ? kg : 20 + 12 * Math.log(kg / 20);
    }

    /** Peak pull, in kgf, of a fish of this species weighing {@code kg}. */
    public double maxPull(double kg) {
        return Math.max(0.05, fightPerKg * effectiveWeight(kg));
    }

    /**
     * Seconds of full-effort pulling this fish has in it before it is spent, before the angler's side pressure.
     * Grows with the square root of weight, so a ten times heavier fish lasts about two and a half times longer.
     */
    public double enduranceSeconds(double kg) {
        return stamina * (5 + 3 * Math.sqrt(Math.max(0.01, kg)));
    }
}

package dev.creategalore.fishing.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Checks of the fight's rules on made-up, steady fish (no runs, jumps or charges), so each rule shows on its own:
 * how reel grinding scales with the drag and waits half a second, that worn-out parts break mid-fight, what an empty
 * spool does, and towing. Pure Java: the self-test runs it in game, and it runs on its own outside the game too.
 */
public final class FightRules {
    private FightRules() {
    }

    /** One check: whether it held, and what was measured. */
    public record Check(boolean ok, String what) {
    }

    /** A fish that pulls a steady {@code fightPerKg} per kg and never runs, jumps or swims at the angler. */
    private static FishFight steady(double fightPerKg, double stamina) {
        return new FishFight(fightPerKg, stamina, 1.6, new Curve(new double[]{0}, new double[]{1}), 0, 0, 0, false);
    }

    private static Tackle rig(double reelMaxDrag, double lineTest, double lineCapacity) {
        return new Tackle(100, 0.6, true, reelMaxDrag, 1.2, 100, 0.5, lineCapacity, lineTest, 0.5, Double.NaN, false, 1);
    }

    /** Reel wear after {@code ticks} of cranking a fish that out-pulls a drag of {@code drag} (of a 10 kg reel). */
    private static double grind(double drag, int ticks) {
        Fight fight = new Fight(steady(1.5, 1000), rig(10, 1000, 10_000), 20, 30);
        Controls c = new Controls();
        c.drag = drag;
        c.reeling = true;
        SplittableRandom rng = new SplittableRandom(1);
        for (int i = 0; i < ticks; i++) fight.tick(c, rng);
        return fight.reelWear();
    }

    public static List<Check> run() {
        List<Check> out = new ArrayList<>();

        double full = grind(10, 200), half = grind(5, 200);
        double ratio = half / full;
        out.add(new Check(Math.abs(ratio - 0.25) < 0.01, String.format(Locale.ROOT,
                "cranking at half drag grinds %.3f of what full drag does (want 0.25)", ratio)));
        out.add(new Check(grind(10, Fight.GRIND_GRACE_TICKS) == 0, "no grinding in the first half second of slip"));
        double perSecond = (grind(10, 220) - grind(10, 200)) / (20 * Fight.DT);
        out.add(new Check(Math.abs(perSecond - Fight.GRIND_PER_SECOND) < 1e-6, String.format(Locale.ROOT,
                "full-drag grinding wears %.2f per second (want %.2f)", perSecond, Fight.GRIND_PER_SECOND)));

        // Letting line go without cranking costs the reel nothing beyond the fight itself.
        Fight letGo = new Fight(steady(1.5, 1000), rig(10, 1000, 10_000), 20, 30);
        Controls idle = new Controls();
        idle.drag = 5;
        SplittableRandom rng = new SplittableRandom(2);
        for (int i = 0; i < 200; i++) letGo.tick(idle, rng);
        out.add(new Check(letGo.reelWear() == 0 && letGo.slipping(), "a slipping drag alone doesn't wear the reel"));
        // The half second runs from when the fish starts taking line: cranking into a run that has gone on longer
        // grinds at once.
        idle.reeling = true;
        letGo.tick(idle, rng);
        out.add(new Check(letGo.reelWear() > 0 && letGo.grinding(),
                "pressing use during a run that has lasted over half a second grinds at once"));
        idle.reeling = false;

        // A reel with 30 durability left, ground at full drag (12 a second), seizes in about 3 s and the fish is lost.
        Fight seize = new Fight(steady(1.5, 1000), rig(10, 1000, 10_000), 20, 30);
        seize.wearLimits(30, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Controls crank = new Controls();
        crank.drag = 10;
        crank.reeling = true;
        rng = new SplittableRandom(3);
        while (!seize.outcome().over() && seize.ticks() < 2000) seize.tick(crank, rng);
        out.add(new Check(seize.outcome() == Outcome.REEL_BROKE && seize.outcome().losesTackle(),
                String.format(Locale.ROOT, "a reel worn through mid-fight seizes and loses the fish (%s after %.1f s)",
                        seize.outcome(), seize.seconds())));

        // A rod with 1 durability left, overloaded, breaks during the fight.
        Fight crack = new Fight(steady(1.0, 1000), new Tackle(5, 0.6, true, 10, 1.2, 100, 0.5, 10_000, 1000, 0.5,
                Double.NaN, false, 1), 20, 30);
        crack.wearLimits(Double.POSITIVE_INFINITY, 1, Double.POSITIVE_INFINITY);
        idle.drag = 10;
        rng = new SplittableRandom(4);
        while (!crack.outcome().over() && crack.ticks() < 2000) crack.tick(idle, rng);
        out.add(new Check(crack.outcome() == Outcome.ROD_BROKE, "a rod worn through mid-fight breaks (" + crack.outcome() + ")"));

        // Emre (22 Oct): a line holds or snaps; load never weakens it. Only teeth fray it, so a line with almost no
        // fraying left, fought by a fish without teeth well below its test, still holds however long the fight.
        Fight fray = new Fight(steady(0.5, 1000), rig(10, 1000, 10_000), 10, 30);
        fray.wearLimits(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.001);
        idle.drag = 10;
        rng = new SplittableRandom(9);
        while (!fray.outcome().over() && fray.ticks() < 2000) fray.tick(idle, rng);
        out.add(new Check(fray.outcome() != Outcome.LINE_SNAPPED && fray.outcome() != Outcome.BITTEN_OFF,
                String.format(Locale.ROOT, "a nearly frayed line fought hard by a fish without teeth holds (%s after %.1f s)",
                        fray.outcome(), fray.seconds())));

        // Empty spool: the drag is as good as locked. A fish that pulls less than the line is held; one that pulls
        // more breaks it.
        Fight held = new Fight(steady(0.5, 1000), rig(2, 20, 40), 10, 35);
        idle.drag = 2;
        rng = new SplittableRandom(5);
        for (int i = 0; i < 1200 && !held.outcome().over(); i++) held.tick(idle, rng);
        out.add(new Check(!held.outcome().over() && held.spooled() && held.lineOut() <= 40 && !held.slipping(),
                String.format(Locale.ROOT, "a 5 kgf fish on an empty spool and 20 kg line is held with %.0f m out (%s)",
                        held.lineOut(), held.outcome())));
        Fight strip = new Fight(steady(1.5, 1000), rig(2, 20, 40), 20, 35);
        rng = new SplittableRandom(6);
        while (!strip.outcome().over() && strip.ticks() < 2400) strip.tick(idle, rng);
        out.add(new Check(strip.outcome() == Outcome.SPOOLED, "a 30 kgf fish strips the spool and snaps the 20 kg line ("
                + strip.outcome() + ")"));

        // Towing: a boat at 6 m/s tows a 0.3 kg fish along on a 10 kg drag; a 15 kg fish takes line through it.
        out.add(tow(0.3, false));
        out.add(tow(15, true));

        // A fish swimming at an angler who walks off faster just gets towed: no line comes off the spool.
        Fight charge = new Fight(new FishFight(0.5, 1000, 1.0, new Curve(new double[]{0}, new double[]{1}), 0, 0, 1, false),
                rig(10, 1000, 10_000), 0.5, 20);
        Controls walk = new Controls();
        walk.drag = 10;
        walk.recede = 4;
        rng = new SplittableRandom(8);
        double most = 0;
        int charged = 0;
        for (int i = 0; i < 400 && !charge.outcome().over(); i++) {
            charge.tick(walk, rng);
            if (charge.charging()) charged++;
            most = Math.max(most, charge.lineOut());
        }
        out.add(new Check(charged > 0 && most <= 20 + 1e-9, String.format(Locale.ROOT,
                "walking off from a charging fish takes no line (most out %.1f m of 20, %d ticks charging)", most, charged)));

        // Lifting: a flick with a light fish, a slow lift under a heavy one.
        double light = liftSeconds(0.5), heavy = liftSeconds(30);
        out.add(new Check(light < 0.6 && heavy > 1.4, String.format(Locale.ROOT,
                "a full lift takes %.2f s with a 0.5 kg fish and %.2f s with a 30 kg one on a 20 kg rod", light, heavy)));

        // Pumping: a fish that pulls more than the crank can wind against (but less than the drag) can't be reeled
        // in; lifting and winding down on the way back brings it in.
        out.add(pump(false));
        out.add(pump(true));

        // Lowering the rod without winding leaves the line slack.
        Fight lower = new Fight(steady(0.3, 1000), rig(10, 1000, 10_000), 2, 20);
        lower.setRodHeight(1);
        Controls drop = new Controls();
        drop.drag = 10;
        drop.liftSweep = 2.5;
        rng = new SplittableRandom(11);
        for (int i = 0; i < 10; i++) lower.tick(drop, rng);
        out.add(new Check(lower.slack() > 0.3 && lower.tension() == 0, String.format(Locale.ROOT,
                "lowering the rod without winding leaves %.2f m of slack line", lower.slack())));

        // A drag below the line's test never lets the line snap, however hard the fish runs and jumps; one set over
        // the test does.
        Bout below = bouts(wild(1.2, false), 10, new Tackle(100, 0.3, true, 6, 1.2, 100, 0.2, 10_000, 4, 0.2,
                Double.NaN, false, 1), 3.6, 300, 21);
        out.add(new Check(below.count(Outcome.LINE_SNAPPED) == 0 && below.highest < 4, String.format(Locale.ROOT,
                "a 12 kgf fish that runs and jumps never snaps a 4 kg line on a 3.6 kg drag (%d of 300 snapped, highest %.2f kg)",
                below.count(Outcome.LINE_SNAPPED), below.highest)));
        // Set exactly at the test (what a frayed line holds, shown as equal to the drag), it still holds.
        Bout at = bouts(wild(1.2, false), 10, new Tackle(100, 0.3, true, 6, 1.2, 100, 0.2, 10_000, 3.55, 0.2,
                Double.NaN, false, 1), 3.55, 300, 27);
        out.add(new Check(at.count(Outcome.LINE_SNAPPED) == 0, String.format(Locale.ROOT,
                "a drag set exactly at what the line holds never snaps it (%d of 300 snapped, highest %.2f of 3.55 kg)",
                at.count(Outcome.LINE_SNAPPED), at.highest)));
        Bout over = bouts(wild(1.2, false), 10, new Tackle(100, 0.3, true, 6, 1.2, 100, 0.2, 10_000, 4, 0.2,
                Double.NaN, false, 1), 5, 300, 22);
        out.add(new Check(over.count(Outcome.LINE_SNAPPED) > 30 && over.allExplained, String.format(Locale.ROOT,
                "the same fish on a 5 kg drag (over the line's test) snaps it in %d of 300 fights, each saying why",
                over.count(Outcome.LINE_SNAPPED))));

        // Teeth on mono: a small toothy fish only frays the line a little, a bigger one frays it faster, and one far too
        // big for the line bites straight through. A steel leader stops all of it.
        Tackle mono = new Tackle(100, 0.6, true, 10, 1.2, 100, 0.5, 10_000, 4, 0.5, Double.NaN, false, 1);
        Bout small = bouts(wild(1.0, true), 1.2, mono, 3, 200, 23);
        Bout big = bouts(wild(1.0, true), 6, mono, 3, 200, 24);
        Bout huge = bouts(wild(1.0, true), 16, mono, 3, 200, 25);
        Bout steel = bouts(wild(1.0, true), 16, new Tackle(100, 0.6, true, 10, 1.2, 100, 0.5, 10_000, 4, 0.5, 60, true, 1),
                3, 200, 26);
        out.add(new Check(small.count(Outcome.BITTEN_OFF) == 0 && small.teeth > 0 && small.teeth < 0.05, String.format(Locale.ROOT,
                "a toothy fish pulling 0.3x the line's test never bites through fresh mono and frays it %.3f a fight",
                small.teeth)));
        out.add(new Check(big.teeth > 10 * small.teeth, String.format(Locale.ROOT,
                "one pulling 1.5x the test frays it %.3f a fight (%.0fx more), %d of 200 chewed through",
                big.teeth, big.teeth / Math.max(1e-9, small.teeth), big.count(Outcome.BITTEN_OFF))));
        out.add(new Check(huge.count(Outcome.BITTEN_OFF) > 100, String.format(Locale.ROOT,
                "one pulling 4x the test bites through in %d of 200 fights", huge.count(Outcome.BITTEN_OFF))));
        out.add(new Check(steel.count(Outcome.BITTEN_OFF) == 0 && steel.teeth == 0, "a steel leader is never bitten through"));

        // A small, tired fish being wound in pulls next to nothing, but the line is moving: the hook stays. Stop
        // winding with it not pulling, and the slack line lets it shake free.
        Tackle strong = new Tackle(100, 0.6, true, 10, 1.2, 100, 0.5, 10_000, 20, 0.5, Double.NaN, false, 0.8);
        FishFight limp = new FishFight(0.5, 0.05, 1.0, new Curve(new double[]{0}, new double[]{0.1}), 0, 0, 0, false);
        int wound = 0, dropped = 0;
        for (int f = 0; f < 200; f++) {
            Fight in = new Fight(limp, strong, 0.3, 25);
            Controls c = new Controls();
            c.drag = 5;
            c.reeling = true;
            SplittableRandom r = new SplittableRandom(30 + f);
            while (!in.outcome().over() && in.ticks() < 2400) in.tick(c, r);
            if (in.outcome() == Outcome.HOOK_THROWN) wound++;
            Fight still = new Fight(limp, strong, 0.3, 25);
            c.reeling = false;
            while (!still.outcome().over() && still.ticks() < 1200) still.tick(c, r);
            if (still.loss() == Loss.HOOK_SLACK) dropped++;
        }
        out.add(new Check(wound == 0 && dropped > 20, String.format(Locale.ROOT,
                "a limp 0.3 kg fish wound in never throws the hook (%d of 200); left on a slack line it does in %d of 200",
                wound, dropped)));

        // A fish that jumps all the time, close in: winding through every jump keeps the line tight and it never
        // throws the hook on one; with nobody winding it does. One swimming at the angler faster than a slow reel
        // winds leaves the line slack even while winding, and then a jump can throw the hook.
        FishFight jumper = new FishFight(0.8, 2, 1.6, new Curve(new double[]{0}, new double[]{1}), 0, 3, 0, false);
        FishFight dasher = new FishFight(0.8, 2, 6, new Curve(new double[]{0}, new double[]{1}), 0, 3, 3, false);
        Tackle slowReel = new Tackle(100, 0.6, true, 10, 0.3, 100, 0.5, 10_000, 20, 0.5, Double.NaN, false, 0.8);
        int reeledThrown = 0, idleThrown = 0, outswum = 0;
        for (int f = 0; f < 200; f++) {
            SplittableRandom r = new SplittableRandom(500 + f);
            Controls c = new Controls();
            c.drag = 5;
            c.rodAngle = Math.toRadians(60);
            c.reeling = true;
            Fight reeled = new Fight(jumper, strong, 3, 6);
            while (!reeled.outcome().over() && reeled.ticks() < 600) reeled.tick(c, r);
            if (reeled.loss() == Loss.HOOK_JUMP || reeled.loss() == Loss.HOOK_JUMP_SLACK) reeledThrown++;
            Fight dashing = new Fight(dasher, slowReel, 3, 6);
            while (!dashing.outcome().over() && dashing.ticks() < 600) dashing.tick(c, r);
            if (dashing.loss() == Loss.HOOK_JUMP_SLACK) outswum++;
            c.reeling = false;
            Fight idle2 = new Fight(jumper, strong, 3, 6);
            while (!idle2.outcome().over() && idle2.ticks() < 600) idle2.tick(c, r);
            if (idle2.loss() == Loss.HOOK_JUMP) idleThrown++;
        }
        out.add(new Check(reeledThrown == 0 && idleThrown > 20 && outswum > 0, String.format(Locale.ROOT,
                "a fish jumping 3 times a second never throws the hook while wound in (%d of 200); not wound, %d of 200;"
                        + " outswimming a slow reel, %d of 200", reeledThrown, idleThrown, outswum)));
        return out;
    }

    /** A fish that runs and jumps often, pulling {@code fightPerKg} per kg. */
    private static FishFight wild(double fightPerKg, boolean toothy) {
        return new FishFight(fightPerKg, 2, 1.6, new Curve(new double[]{0}, new double[]{1}), 0.3, 0.3, 0.05, toothy);
    }

    /** What {@code n} fights against one fish came to. */
    private static final class Bout {
        final int[] outcomes = new int[Outcome.values().length];
        double highest;
        double teeth;
        boolean allExplained = true;

        int count(Outcome o) {
            return outcomes[o.ordinal()];
        }
    }

    /**
     * {@code n} fights of up to a minute, the angler winding half the time (in bursts) at {@code drag}, the rod held
     * at a good angle; teeth is the mean fraying from teeth per fight.
     */
    private static Bout bouts(FishFight fish, double kg, Tackle tackle, double drag, int n, long seed) {
        Bout b = new Bout();
        SplittableRandom rng = new SplittableRandom(seed);
        for (int f = 0; f < n; f++) {
            Fight fight = new Fight(fish, tackle, kg, 30);
            fight.wearLimits(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.99);
            Controls c = new Controls();
            c.drag = drag;
            c.rodAngle = Math.toRadians(60);
            while (!fight.outcome().over() && fight.ticks() < 1200) {
                c.reeling = fight.ticks() / 40 % 2 == 0;
                fight.tick(c, rng);
            }
            b.outcomes[fight.outcome().ordinal()]++;
            b.highest = Math.max(b.highest, fight.highestTension());
            b.teeth += fight.teethFray() / n;
            if (fight.outcome().over() && fight.outcome() != Outcome.LANDED && fight.loss() == null) b.allExplained = false;
        }
        return b;
    }

    /** Seconds a full lift takes against a steady fish of {@code kg} (pulling 0.5 kgf per kg) on a 20 kg rod. */
    private static double liftSeconds(double kg) {
        Fight fight = new Fight(steady(0.5, 1000), new Tackle(20, 0.6, true, 50, 1.2, 100, 0.5, 10_000, 1000, 0.5,
                Double.NaN, false, 1), kg, 20);
        Controls c = new Controls();
        c.drag = 50;
        c.lifting = true;
        c.liftSweep = 2.5;
        SplittableRandom rng = new SplittableRandom(10);
        while (fight.rodHeight() < 1 && fight.ticks() < 400 && !fight.outcome().over()) fight.tick(c, rng);
        return fight.seconds();
    }

    /** 30 s against a steady 8 kgf fish on a 10 kg drag whose crank winds against 6 kgf at most. */
    private static Check pump(boolean pumping) {
        Fight fight = new Fight(steady(0.8, 1000), new Tackle(20, 0.6, true, 10, 1.2, 6, 0.5, 10_000, 1000, 0.5,
                Double.NaN, false, 1), 10, 30);
        Controls c = new Controls();
        c.drag = 10;
        c.liftSweep = 2.5;
        SplittableRandom rng = new SplittableRandom(12);
        boolean up = true;
        for (int i = 0; i < 600 && !fight.outcome().over(); i++) {
            if (pumping) {
                if (up && fight.rodHeight() >= 1) up = false;
                if (!up && fight.rodHeight() <= 0) up = true;
                c.lifting = up;
                c.reeling = !up;
            } else {
                c.reeling = true;
            }
            fight.tick(c, rng);
        }
        double gained = 30 - fight.distance();
        boolean ok = pumping ? gained > 10 : gained < 0.5;
        return new Check(ok, String.format(Locale.ROOT, "%s an 8 kgf fish the crank can't turn against gains %.1f m in 30 s",
                pumping ? "pumping" : "reeling only", gained));
    }

    private static Check tow(double kg, boolean wantsLine) {
        Fight fight = new Fight(steady(0.3, 1000), rig(10, 1000, 10_000), kg, 20);
        Controls c = new Controls();
        c.drag = 10;
        c.recede = 6;
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 100; i++) fight.tick(c, rng);
        double gained = fight.lineOut() - 20;
        boolean ok = wantsLine ? gained > 5 : gained < 0.01;
        return new Check(ok, String.format(Locale.ROOT, "a %.1f kg fish behind a boat at 6 m/s on a 10 kg drag %s (%.1f m of line out in 5 s)",
                kg, wantsLine ? "takes line" : "is towed", gained));
    }

    public static void main(String[] args) {
        int failed = 0;
        for (Check c : run()) {
            System.out.println((c.ok() ? "PASS " : "FAIL ") + c.what());
            if (!c.ok()) failed++;
        }
        System.exit(failed);
    }
}

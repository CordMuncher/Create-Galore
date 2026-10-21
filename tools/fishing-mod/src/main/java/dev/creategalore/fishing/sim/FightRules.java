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
        seize.wearLimits(30, Double.POSITIVE_INFINITY);
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
        crack.wearLimits(Double.POSITIVE_INFINITY, 1);
        idle.drag = 10;
        rng = new SplittableRandom(4);
        while (!crack.outcome().over() && crack.ticks() < 2000) crack.tick(idle, rng);
        out.add(new Check(crack.outcome() == Outcome.ROD_BROKE, "a rod worn through mid-fight breaks (" + crack.outcome() + ")"));

        // Empty spool: the drag is as good as locked. A fish that pulls less than the line is held; one that pulls
        // more breaks it.
        Fight held = new Fight(steady(0.5, 1000), rig(2, 20, 40), 10, 35);
        idle.drag = 2;
        rng = new SplittableRandom(5);
        for (int i = 0; i < 1200 && !held.outcome().over(); i++) held.tick(idle, rng);
        out.add(new Check(!held.outcome().over() && held.spooled() && held.distance() <= 40 && !held.slipping(),
                String.format(Locale.ROOT, "a 5 kgf fish on an empty spool and 20 kg line is held at %.0f m (%s)",
                        held.distance(), held.outcome())));
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
            most = Math.max(most, charge.distance());
        }
        out.add(new Check(charged > 0 && most <= 20 + 1e-9, String.format(Locale.ROOT,
                "walking off from a charging fish takes no line (most out %.1f m of 20, %d ticks charging)", most, charged)));
        return out;
    }

    private static Check tow(double kg, boolean wantsLine) {
        Fight fight = new Fight(steady(0.3, 1000), rig(10, 1000, 10_000), kg, 20);
        Controls c = new Controls();
        c.drag = 10;
        c.recede = 6;
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 100; i++) fight.tick(c, rng);
        double gained = fight.distance() - 20;
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

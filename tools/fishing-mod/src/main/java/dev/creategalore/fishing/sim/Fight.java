package dev.creategalore.fishing.sim;

import java.util.random.RandomGenerator;

/**
 * One fight between an angler and a hooked fish, stepped once per game tick (1/20 s).
 *
 * <p>This is a deliberately cheap proxy, not a simulation of a real fish: the fish has a peak pull (from its
 * weight and species), a hidden stamina, and an intensity that follows its species' {@link Curve} over the
 * fight's progress. Every tick:
 * <ol>
 *   <li>The fish pulls: {@code peak pull x intensity x stamina factor}, boosted during runs, near zero while it
 *       swims toward the angler ("charges").</li>
 *   <li>If the pull is more than the drag, the drag slips: line goes out at the fish's speed and the tension is the
 *       drag. Otherwise reeling gains line (slower the closer the pull gets to what the crank can wind against)
 *       and the tension is the pull. While the fish charges, the line goes slack unless the angler keeps
 *       reeling.</li>
 *   <li>Runs and jumps add sudden spikes. Line stretch, a smooth drag and a bent rod soak part of each spike; a
 *       rod pointed straight down the line soaks nothing.</li>
 *   <li>Failures: tension over the breaking load snaps the line, a toothy fish bites through non-steel line,
 *       overloading the rod can crack it, a slack line or a jump on a loose line can throw the hook.</li>
 *   <li>The fish tires in proportion to the tension it works against (so a drag set too light never tires it)
 *       and faster under side pressure; it recovers a little while the line is slack.</li>
 * </ol>
 * A fish reaching the angler lands once it is tired; a fresh one bolts again.
 *
 * <p>Pure Java with no game classes, so the Monte Carlo runner and the game share exactly the same code.
 */
public final class Fight {
    /** Seconds per tick. */
    public static final double DT = 0.05;
    /** Distance in metres at which a tired fish is landed. */
    public static final double LAND_DISTANCE = 1.5;

    private final FishFight fish;
    private final Tackle tackle;
    private final double weight;
    private final double maxPull;
    private final double endurance;
    private final double startDistance;

    private double distance;
    private double stamina = 1;
    private double pull;
    private double tension;
    private double peakTension;
    private boolean slipping;
    private boolean stalled;
    private double runLeft;
    private double chargeLeft;
    private double jitter;
    private int slackTicks;
    private int ticks;
    private Outcome outcome = Outcome.FIGHTING;
    private boolean jumped;
    private boolean runStarted;
    private double rodWear;
    private double reelWear;
    private double lineWear;
    private double highestTension;

    /**
     * @param fish          the species' fight data
     * @param tackle        the rig, with the hook hold already worked out for this fish's mouth
     * @param weightKg      this fish's weight
     * @param startDistance metres of line between the angler and the fish at the hook-set
     */
    public Fight(FishFight fish, Tackle tackle, double weightKg, double startDistance) {
        this.fish = fish;
        this.tackle = tackle;
        this.weight = weightKg;
        this.maxPull = fish.maxPull(weightKg);
        this.endurance = fish.enduranceSeconds(weightKg);
        this.startDistance = Math.max(LAND_DISTANCE + 1, startDistance);
        this.distance = this.startDistance;
    }

    /** Advances the fight by one tick. Does nothing once it is over. */
    public void tick(Controls in, RandomGenerator rng) {
        if (outcome.over()) return;
        ticks++;
        jumped = false;
        runStarted = false;

        double progress = progress();
        double intensity = fish.profile().at(progress);
        double staminaFactor = 0.25 + 0.75 * stamina;
        double breakLoad = tackle.breakingLoad();

        // Behaviour: runs, charges toward the angler, jumps and head shakes near the end.
        if (runLeft > 0) {
            runLeft -= DT;
        } else if (chargeLeft <= 0 && rng.nextDouble() < fish.runs() * intensity * stamina * DT) {
            runLeft = 1.5 + 2.5 * rng.nextDouble();
            runStarted = true;
        }
        if (chargeLeft > 0) {
            chargeLeft -= DT;
        } else if (runLeft <= 0 && rng.nextDouble() < fish.charges() * (0.5 + stamina) * DT) {
            chargeLeft = 0.8 + 1.7 * rng.nextDouble();
        }
        boolean charging = chargeLeft > 0;
        double spike = 0;
        if ((progress > 0.66 || distance < 8) && rng.nextDouble() < fish.jumps() * (0.3 + 0.7 * stamina) * DT) {
            jumped = true;
            spike += maxPull * (0.6 + 0.6 * rng.nextDouble()) * staminaFactor;
        }
        if (runStarted) spike += maxPull * 0.5 * staminaFactor;

        jitter = 0.85 * jitter + 0.15 * rng.nextGaussian() * 0.25;
        double boost = runLeft > 0 ? 1.5 : 1;
        pull = charging ? maxPull * 0.05 : Math.max(0, maxPull * intensity * staminaFactor * boost * (1 + jitter));
        double fishSpeed = fish.speed() * (0.4 + 0.6 * intensity) * (runLeft > 0 ? 1.8 : 1) * (0.3 + 0.7 * stamina);

        // Drag, reel and line.
        double drag = tackle.hasReel() ? clamp(in.drag, 0.05, tackle.reelMaxDrag()) : Double.POSITIVE_INFINITY;
        double crank = in.crankLimit(tackle);
        double retrieve = tackle.reelRetrieve() * in.retrieveScale;
        slipping = !charging && pull > drag;
        stalled = false;
        if (charging) {
            distance -= fish.speed() * 0.6 * DT;
            if (in.reeling) {
                distance -= retrieve * DT;
                tension = maxPull * 0.08;
            } else {
                tension = 0;
            }
        } else if (slipping) {
            distance += fishSpeed * 0.8 * (1 - drag / pull) * DT;
            tension = drag;
            reelWear += DT * 0.3 * drag / tackle.reelMaxDrag();
            if (in.reeling && in.crankThroughSlip) {
                // Cranking against a slipping drag: the gears grind (Fishing Planet's "reel overload").
                reelWear += DT * 4 * (1 + (pull - drag) / drag);
            }
        } else if (in.reeling) {
            if (pull >= crank) {
                stalled = true;
                tension = pull;
            } else {
                distance -= retrieve * (1 - pull / crank) * DT;
                tension = pull * 1.1;
            }
        } else {
            tension = pull;
        }
        distance = Math.max(LAND_DISTANCE, distance);

        // Spikes, softened by stretch, a smooth drag and the bent rod.
        peakTension = tension;
        if (spike > 0) {
            double absorb = clamp(0.45 * tackle.lineStretch() + 0.25 * tackle.reelSmooth()
                    + 0.45 * tackle.rodAbsorb() * bendFactor(in.rodAngle), 0, 0.85);
            double peak = tension + spike * (1 - absorb);
            if (tackle.hasReel() && peak > drag) peak = drag + (peak - drag) * (0.55 - 0.45 * tackle.reelSmooth());
            peakTension = peak;
        }
        highestTension = Math.max(highestTension, peakTension);

        // Failures.
        if (peakTension > breakLoad) {
            outcome = Outcome.LINE_SNAPPED;
            return;
        }
        if (fish.toothy() && !tackle.steelLeader() && (jumped || runStarted) && rng.nextDouble() < 0.25) {
            outcome = Outcome.BITTEN_OFF;
            return;
        }
        double rodLoad = peakTension / tackle.rodMaxLoad();
        if (rodLoad > 0.7) rodWear += DT * (rodLoad - 0.7) * 2;
        if (rodLoad > 1 && rng.nextDouble() < (rodLoad - 1) * 0.04) {
            outcome = Outcome.ROD_BROKE;
            return;
        }
        double hold = tackle.hookHold();
        // Slack means the line isn't tight on the fish at all (it swam toward the angler and nobody reeled), not
        // merely a small fish pulling little.
        boolean slack = tension < 0.06 * maxPull;
        slackTicks = slack ? slackTicks + 1 : Math.max(0, slackTicks - 2);
        if (slackTicks > 20 && rng.nextDouble() < 0.01 + 0.03 * (1 - hold)) {
            outcome = Outcome.HOOK_THROWN;
            return;
        }
        // A jump throws the hook more often on a loose line: reeling through it keeps the line tight.
        if (jumped && rng.nextDouble() < (0.08 + 0.4 * (1 - hold)) * (in.reeling ? 0.4 : 1) * (charging ? 2 : 1)) {
            outcome = Outcome.HOOK_THROWN;
            return;
        }
        if (spike > 0 && hold < 0.5 && rng.nextDouble() < (0.5 - hold) * 0.6) {
            outcome = Outcome.HOOK_THROWN;
            return;
        }

        // Stamina: spent against tension, faster under side pressure; a little back while slack.
        double effort = Math.min(tension, pull) / maxPull;
        stamina -= DT * effort * (1 + 0.6 * sidePressure(in.rodAngle)) / endurance;
        if (tension < 0.15 * maxPull) stamina += DT * 0.015;
        stamina = clamp(stamina, 0, 1);
        lineWear += DT * 0.02 * tension / breakLoad;

        if (tackle.hasReel() && distance > tackle.lineCapacity()) {
            outcome = Outcome.SPOOLED;
            return;
        }
        if (distance <= LAND_DISTANCE) {
            if (stamina < 0.35 || weight < 0.4) {
                outcome = Outcome.LANDED;
            } else if (runLeft <= 0) {
                // Still fresh at the angler's feet: it bolts again.
                runLeft = 1.5;
                runStarted = true;
            }
        }
    }

    /** How much of the rod's shock-soaking works at this angle to the line: none straight down it, full at 30-100°. */
    public static double bendFactor(double angle) {
        double deg = Math.toDegrees(Math.abs(angle));
        if (deg < 5) return 0.1;
        if (deg < 30) return 0.1 + 0.9 * (deg - 5) / 25;
        if (deg <= 100) return 1;
        return Math.max(0.5, 1 - (deg - 100) / 100);
    }

    /** Side pressure from holding the rod off the line, 0..1. */
    public static double sidePressure(double angle) {
        return Math.max(0, Math.sin(Math.min(Math.abs(angle), Math.PI / 2)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** 0 at the hook-set, 1 at the angler. */
    public double progress() {
        return clamp(1 - (distance - LAND_DISTANCE) / (startDistance - LAND_DISTANCE), 0, 1);
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Ends the fight from outside (the angler cut the line, the rod left the hand...). */
    public void end(Outcome why) {
        if (!outcome.over()) outcome = why;
    }

    public double distance() {
        return distance;
    }

    public double startDistance() {
        return startDistance;
    }

    /** Steady line tension this tick in kgf (what the HUD's bar shows, against the breaking load). */
    public double tension() {
        return tension;
    }

    /** Tension including this tick's spike. */
    public double peakTension() {
        return peakTension;
    }

    public double highestTension() {
        return highestTension;
    }

    /** What the fish pulls with this tick, before the drag. */
    public double pull() {
        return pull;
    }

    public double maxPull() {
        return maxPull;
    }

    public boolean slipping() {
        return slipping;
    }

    /** The crank couldn't turn against the pull this tick (for drives: the network or engine stalled). */
    public boolean stalled() {
        return stalled;
    }

    public boolean charging() {
        return chargeLeft > 0;
    }

    public boolean jumped() {
        return jumped;
    }

    public boolean runStarted() {
        return runStarted;
    }

    public boolean running() {
        return runLeft > 0;
    }

    /** Hidden from players; for tests and drives. */
    public double stamina() {
        return stamina;
    }

    public int ticks() {
        return ticks;
    }

    public double seconds() {
        return ticks * DT;
    }

    public double weight() {
        return weight;
    }

    public Tackle tackle() {
        return tackle;
    }

    public FishFight fish() {
        return fish;
    }

    /** Rod condition used up, in "seconds at full overload": the caller turns it into durability. */
    public double rodWear() {
        return rodWear;
    }

    public double reelWear() {
        return reelWear;
    }

    public double lineWear() {
        return lineWear;
    }

    /**
     * How hard the fight was, about 0..2: how close the fish's peak pull came to what the rig is rated for, and how
     * long it took. Feeds the catch XP.
     */
    public double difficulty() {
        double strength = Math.min(2, maxPull / Math.max(0.1, tackle.ratedLoad()));
        double length = Math.min(2, seconds() / 40);
        return 0.5 * strength + 0.5 * length;
    }
}

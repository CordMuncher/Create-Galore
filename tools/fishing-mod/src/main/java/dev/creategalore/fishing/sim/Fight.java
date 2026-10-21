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
 *       reeling (or moves off faster than it comes). With the spool empty there is no line left to give: the drag
 *       acts as locked.</li>
 *   <li>An angler moving away from the fish (walking, or a boat) tows it: the water's drag on the fish, from its size
 *       and the speed, adds to its pull. A small fish on a strong rig is towed along; a big one takes line through
 *       the drag.</li>
 *   <li>Lifting the rod (pumping): the rod comes up at a speed set by the load, fast as a flick with a light fish
 *       and slow under a heavy one, and its tip drags the fish back by the rod's sweep, against the drag but not
 *       against the reel's crank. Lowering it again leaves that much line slack, which the reel winds back with no
 *       load on it; a slack line left too long can throw the hook. So a heavy fish the crank can't move is pumped
 *       in: lift, then wind down.</li>
 *   <li>Runs and jumps add sudden spikes. Line stretch, a smooth drag and a bent rod soak part of each spike; a
 *       rod pointed straight down the line soaks nothing.</li>
 *   <li>Failures: tension over the breaking load snaps the line, a toothy fish bites through non-steel line,
 *       overloading the rod can crack it, a slack line or a jump on a loose line can throw the hook, and a rod or
 *       reel worn through mid-fight breaks there and then.</li>
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
    /** Ticks the drag may slip under a cranking angler before the reel starts to grind: time to let go of use. */
    public static final int GRIND_GRACE_TICKS = 10;
    /** Reel wear per second of cranking through a slipping drag at the reel's full drag; it scales with drag squared. */
    public static final double GRIND_PER_SECOND = 4;
    /** Durability one unit of {@link #reelWear()} costs a reel. */
    public static final double REEL_DURABILITY_PER_WEAR = 3;
    /** Durability one unit of {@link #rodWear()} costs a rod. */
    public static final double ROD_DURABILITY_PER_WEAR = 15;
    /** Fraying of a line (0 new, 1 worn through) per unit of a fight's line wear. */
    public static final double LINE_FRAY_PER_WEAR = 0.5;
    /** Water drag on a towed fish, kgf per kg^(2/3) per (m/s)^2. */
    public static final double TOW_DRAG = 0.07;
    /** Rod lift speed with nothing on the line, in full lifts per second: a strike is a flick of a third of a second. */
    public static final double LIFT_RATE = 3;
    /** How much the load slows the lift: at the rod's rated load a lift is this many times + 1 slower. */
    public static final double LIFT_HEAVY = 6;
    /** How fast a rod let go of comes back down, in full lifts per second. */
    public static final double LOWER_RATE = 0.8;
    /** Slack (metres) up to which the line still counts as tight. */
    public static final double SLACK_TIGHT = 0.05;

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
    private int grindTicks;
    private boolean grinding;
    private boolean spooled;
    private double reelLife = Double.POSITIVE_INFINITY;
    private double rodLife = Double.POSITIVE_INFINITY;
    private double lineLife = Double.POSITIVE_INFINITY;
    /** Rod height, 0 lowered to 1 fully lifted. */
    private double rodHeight;
    /** Line given back by lowering the rod and not yet wound in or taken by the fish, metres. */
    private double slack;

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

    /**
     * Durability the reel and rod have left (after the fight's base wear of 1 each), and how much fraying the line
     * can still take (after the fight's base fraying). When the fight's wear uses one up, that part breaks mid-fight
     * and the fish is gone. Unlimited unless set (the simulator).
     */
    public void wearLimits(double reelDurabilityLeft, double rodDurabilityLeft, double lineFrayLeft) {
        this.reelLife = reelDurabilityLeft;
        this.rodLife = rodDurabilityLeft;
        this.lineLife = lineFrayLeft;
    }

    /**
     * How fast a rod lifts, in full lifts per second, against a line load of {@code rodLoad} (a share of the rod's
     * rating): a light fish flicks up, a heavy one is a slow lift. The client draws its own rod with the same speed.
     */
    public static double liftRate(double rodLoad) {
        return LIFT_RATE / (1 + LIFT_HEAVY * Math.max(0, rodLoad));
    }

    /** Where the rod starts when the fight does (it was being lifted for the strike). */
    public void setRodHeight(double height) {
        rodHeight = clamp(height, 0, 1);
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

        // Drag, reel and line. An empty spool has nothing left to give: the drag is as good as locked.
        spooled = tackle.hasReel() && distance >= tackle.lineCapacity();
        double drag = tackle.hasReel() && !spooled ? clamp(in.drag, 0.05, tackle.reelMaxDrag()) : Double.POSITIVE_INFINITY;
        double crank = in.crankLimit(tackle);
        double retrieve = tackle.reelRetrieve() * in.retrieveScale;
        double recede = Math.max(0, in.recede);
        // Towing the fish through the water as the angler moves off: drag from its size and the speed.
        double load = charging ? pull : pull + TOW_DRAG * Math.pow(weight, 2.0 / 3) * recede * recede;

        // The rod: lifting draws the fish back by the tip's travel (once any slack is taken up), lowering gives slack.
        // Winding while the rod comes down: it comes down as fast as the reel takes up the line ("winding down"), so
        // the line stays tight and the crank turns with no load.
        double drawn = 0;
        boolean windingDown = false;
        if (in.liftSweep > 0) {
            double before = rodHeight;
            windingDown = !in.lifting && in.reeling && rodHeight > 0;
            double lower = windingDown ? Math.min(LOWER_RATE, retrieve / in.liftSweep) : LOWER_RATE;
            rodHeight = in.lifting ? Math.min(1, rodHeight + liftRate(load / tackle.rodMaxLoad()) * DT)
                    : Math.max(0, rodHeight - lower * DT);
            double travel = (rodHeight - before) * in.liftSweep;
            if (travel < 0) {
                slack -= travel;
            } else {
                double taken = Math.min(slack, travel);
                slack -= taken;
                drawn = travel - taken;
            }
            if (windingDown) slack = Math.max(0, slack - retrieve * DT);
        }
        if (drawn > 0 && !charging) {
            // Dragging the fish through the water at the tip's speed.
            double speed = drawn / DT;
            load += TOW_DRAG * Math.pow(weight, 2.0 / 3) * speed * speed;
        }
        boolean loose = slack > SLACK_TIGHT;
        slipping = !charging && !loose && load > drag;
        stalled = false;
        if (loose && !charging) {
            // Slack from a lowered rod: the reel winds it back unloaded, and the fish swims off with the rest.
            double swim = Math.min(slack, fishSpeed * 0.8 * DT);
            slack -= swim;
            distance += swim;
            if (in.reeling && !windingDown) slack = Math.max(0, slack - retrieve * DT);
            tension = 0;
        } else if (charging) {
            // Swimming at the angler. One who walks or sails off faster than that just tows it: the line is never
            // slack enough to come off the spool, so the gap can only close.
            distance -= Math.max(0, fish.speed() * 0.6 - recede) * DT;
            if (in.reeling) {
                distance -= retrieve * DT;
                tension = maxPull * 0.08;
            } else {
                // Moving off faster than it comes keeps the line tight, as reeling does.
                tension = recede > fish.speed() * 0.6 ? maxPull * 0.08 : 0;
            }
        } else if (slipping) {
            distance += (fishSpeed * 0.8 + recede) * (1 - drag / load) * DT;
            tension = drag;
        } else if (drawn > 0) {
            // The lift brings the fish in; cranking at the same time adds what the reel can wind on top.
            distance -= drawn;
            if (in.reeling && load < crank) distance -= retrieve * (1 - load / crank) * DT;
            stalled = in.reeling && load >= crank;
            tension = in.reeling ? load * 1.1 : load;
        } else if (in.reeling && !windingDown) {
            if (load >= crank) {
                stalled = true;
                tension = load;
            } else {
                distance -= retrieve * (1 - load / crank) * DT;
                tension = load * 1.1;
            }
        } else {
            tension = load;
        }
        distance = Math.max(LAND_DISTANCE, distance);
        if (tackle.hasReel()) distance = Math.min(distance, tackle.lineCapacity());

        // Cranking while the fish takes line (or against it on an empty spool, which is the reel's full drag): the
        // gears grind (Fishing Planet's "reel overload"). Once the fish starts taking line the angler has half a
        // second to stop cranking; after that it grinds with the square of the drag, so half the reel's drag grinds a
        // quarter as fast as full.
        boolean overloaded = slipping || spooled && stalled;
        grindTicks = overloaded ? grindTicks + 1 : 0;
        grinding = overloaded && in.reeling && in.crankThroughSlip && grindTicks > GRIND_GRACE_TICKS;
        if (grinding) {
            double share = slipping ? drag / tackle.reelMaxDrag() : 1;
            reelWear += DT * GRIND_PER_SECOND * share * share;
        }

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
        if (peakTension > breakLoad || lineWear * LINE_FRAY_PER_WEAR >= lineLife) {
            outcome = spooled ? Outcome.SPOOLED : Outcome.LINE_SNAPPED;
            return;
        }
        if (reelWear * REEL_DURABILITY_PER_WEAR >= reelLife) {
            outcome = Outcome.REEL_BROKE;
            return;
        }
        if (fish.toothy() && !tackle.steelLeader() && (jumped || runStarted) && rng.nextDouble() < 0.25) {
            outcome = Outcome.BITTEN_OFF;
            return;
        }
        double rodLoad = peakTension / tackle.rodMaxLoad();
        if (rodLoad > 0.7) rodWear += DT * (rodLoad - 0.7) * 2;
        if (rodWear * ROD_DURABILITY_PER_WEAR >= rodLife || rodLoad > 1 && rng.nextDouble() < (rodLoad - 1) * 0.04) {
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

    /** Rod height, 0 lowered to 1 fully lifted. */
    public double rodHeight() {
        return rodHeight;
    }

    /** Metres of slack line from lowering the rod, not yet wound in. */
    public double slack() {
        return slack;
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

    /** Every metre of line is out: the reel can give no more. */
    public boolean spooled() {
        return spooled;
    }

    /** The reel ground this tick: cranked against a slipping drag (or an empty spool) past the half-second grace. */
    public boolean grinding() {
        return grinding;
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

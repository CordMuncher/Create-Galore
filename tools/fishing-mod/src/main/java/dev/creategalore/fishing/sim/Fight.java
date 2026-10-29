package dev.creategalore.fishing.sim;

import java.util.random.RandomGenerator;

/**
 * One fight between an angler and a hooked fish, stepped once per game tick (1/20 s).
 *
 * <p>This is a deliberately cheap proxy, not a simulation of a real fish: the fish has a peak pull (from its
 * weight and species), a hidden stamina, and an intensity that follows its species' {@link Curve} over the
 * fight's progress. Every tick:
 * <ol>
 *   <li>The fish decides what it is doing ({@link Mood}): holding and pulling steadily with a slow sway as it works,
 *       running (more pull and speed, sweeping off to one side), swimming at the angler ("charging"), shaking its
 *       head (a quick throb on the line) or, once tired, sulking for a moment. Its effort follows what it is doing
 *       over a few tenths of a second instead of jumping: there is no per-tick noise.</li>
 *   <li>The line is a spring. The rod's bend and the line's stretch (a lot for mono and string, hardly any for braid)
 *       hold how far the fish is beyond the line out, and that stretch over the rig's give is the tension. Less
 *       than none is slack line. The fish swims by the balance of its pull and the tension (free with no load,
 *       dragged back through the water once the tension is more); the reel winds what its crank can against the
 *       tension; the drag lets line go once the tension reaches it. With the spool empty there is no line left to
 *       give: the drag acts as locked. A fish swimming at the angler faster than the line is wound leaves slack.</li>
 *   <li>An angler moving away from the fish (walking, or a boat) tows it: the water's drag on the fish, from its size
 *       and the speed, is what the line has to pull. A small fish on a strong rig is towed along; a big one takes
 *       line through the drag.</li>
 *   <li>Lifting the rod (pumping): the rod comes up at a speed set by the load, fast as a flick with a light fish
 *       and slow under a heavy one, and its tip draws line back by the rod's sweep, against the drag but not
 *       against the reel's crank. Lowering it gives that line back as slack; winding while it comes down ("winding
 *       down") takes it up as it comes, the crank turning with no load. So a heavy fish the crank can't move is
 *       pumped in: lift, then wind down.</li>
 *   <li>Runs and jumps add sudden spikes. Line stretch, a smooth drag and a bent rod soak part of each spike; a
 *       rod pointed straight down the line soaks nothing. A working drag set at or below the line's test always
 *       lets line go before the line breaks, so the spike can't snap it.</li>
 *   <li>Failures: tension over the breaking load snaps the line (only possible with the drag over the line's test,
 *       on a pole with no drag, or with the spool empty), a toothy fish chews line without an iron or steel leader,
 *       fraying it a little all fight and more on each run or jump, in proportion to how big it is for the line
 *       (one far too big can bite straight through), overloading the rod can crack it, a slack line (the fish not
 *       pulling and the angler not winding) or a jump on a loose line can throw the hook, and a rod, reel or line
 *       worn through mid-fight breaks there and then.</li>
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
    /** Fraying per second from a toothy fish's teeth on non-steel line, per unit of oversize (pull over line test). */
    public static final double TEETH_FRAY_PER_SECOND = 0.002;
    /** Fraying from one run or jump of a toothy fish on non-steel line, times oversize squared. */
    public static final double TEETH_FRAY_PER_LUNGE = 0.05;
    /** Oversize (peak pull over line test) from which one lunge may bite straight through. */
    public static final double TEETH_CLEAN_BITE = 3;
    /** Line give, metres per kgf, per metre out and unit of stretch, at 1 kg test. */
    public static final double LINE_GIVE = 0.12;
    /** How fast the fish's effort follows what it is doing, seconds; and into a run. */
    public static final double EFFORT_TAU = 0.3;
    public static final double EFFORT_TAU_RUN = 0.12;
    /** The slow sway of a holding fish's pull (period 3-6 s) and the quicker one on top (1.3-2.2 s), as fractions. */
    public static final double SWAY_SLOW = 0.10;
    public static final double SWAY_FAST = 0.05;
    /** Head shakes: bouts per second at full intensity, and how deep the throb is. */
    public static final double SHAKE_RATE = 0.12;
    public static final double SHAKE_DEPTH = 0.22;
    /** A tired fish sulking: bouts per second once its stamina is under 45%, and its pull meanwhile. */
    public static final double REST_RATE = 0.15;
    public static final double REST_PULL = 0.7;
    /**
     * Swimming round the angler: a holding fish drifts sideways at up to this share of its top speed, a running one
     * sweeps off at {@link #SIDE_RUN_LOW} to all of it. Sideways is swimming like any other: it never makes a fish
     * faster than it can swim, however far out it is.
     */
    public static final double SIDE_HOLD = 0.3;
    public static final double SIDE_RUN_LOW = 0.6;
    /** How fast the fish's sideways speed follows what it is doing, seconds: it leans into a turn, it doesn't snap. */
    public static final double SIDE_TAU = 0.6;
    /** A fish swimming straight out (or in) at this share of its top speed or more still has this much left to turn. */
    public static final double SIDE_FLOOR = 0.4;

    private final FishFight fish;
    private final Tackle tackle;
    private final double weight;
    private final double maxPull;
    private final double endurance;
    private final double startDistance;

    /** Line off the spool, metres. */
    private double lineOut;
    /**
     * How far the fish is beyond the line out, metres: the line's stretch and the rod's bend, which hold the tension.
     * Below zero the line is slack by that much.
     */
    private double stretch;
    private double stamina = 1;
    private double pull;
    private double tension;
    private double peakTension;
    private boolean slipping;
    private boolean stalled;
    private Mood mood = Mood.HOLD;
    private double moodLeft;
    private double moodTime;
    private double holdFor;
    private double holdSide;
    /** Which way and how hard the fish wants to swim round the angler, a signed share of its top speed. */
    private double side;
    /** How fast it is swimming round the angler, m/s (signed): what the game moves it by. */
    private double sideSpeed;
    /** Its top swimming speed right now, m/s. */
    private double topSpeed;
    /** How its size compares with its species' usual size, as a speed factor (bigger fish swim faster). */
    private double sizeSpeed = 1;
    private double shakeHz;
    /** How fast this charge comes in, a share of the fish's speed. */
    private double chargePace;
    /** The fish's effort, following what it is doing over a few tenths of a second. */
    private double effort;
    private double swayPeriod1;
    private double swayPeriod2;
    private double swayPhase1;
    private double swayPhase2;
    private double slackFor;
    private int slackTicks;
    private int ticks;
    private Outcome outcome = Outcome.FIGHTING;
    private boolean jumped;
    private boolean runStarted;
    private double rodWear;
    private double reelWear;
    private double highestTension;
    private int grindTicks;
    private boolean grinding;
    private boolean spooled;
    private double reelLife = Double.POSITIVE_INFINITY;
    private double rodLife = Double.POSITIVE_INFINITY;
    private double lineLife = Double.POSITIVE_INFINITY;
    /** Rod height, 0 lowered to 1 fully lifted. */
    private double rodHeight;
    /** Fraying from a toothy fish's teeth this fight (the only thing that frays a line). */
    private double teethFray;
    /** Why the fight was lost, once it is. */
    private Loss loss;
    private boolean takingLine;
    private boolean slackLine;

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
        this.lineOut = this.startDistance;
    }

    /** What the fish is doing. */
    public enum Mood {
        /** Pulling steadily, swaying as it works and drifting round on a slow arc. */
        HOLD,
        /** A run: a burst of extra pull and speed, sweeping off to one side. */
        RUN,
        /** Swimming at the angler: the line goes slack unless it is wound in as fast. */
        CHARGE,
        /** Head shakes: a quick throb on the line. */
        SHAKE,
        /** Tired, it sulks for a moment and pulls less. */
        REST
    }

    /**
     * Durability the reel and rod have left (after the fight's base wear of 1 each), and how much fraying the line
     * can still take. When the fight's wear uses one up, that part breaks mid-fight
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
        if (ticks == 1) {
            swayPeriod1 = 3 + 3 * rng.nextDouble();
            swayPeriod2 = 1.3 + 0.9 * rng.nextDouble();
            swayPhase1 = 2 * Math.PI * rng.nextDouble();
            swayPhase2 = 2 * Math.PI * rng.nextDouble();
            holdSide = (rng.nextDouble() * 2 - 1) * SIDE_HOLD;
            side = holdSide;
        }

        // Behaviour: what the fish is doing for the next second or few.
        moodLeft -= DT;
        moodTime += DT;
        if (mood != Mood.HOLD && moodLeft <= 0) hold(rng);
        if (mood == Mood.HOLD && moodTime > holdFor) hold(rng);
        if (mood != Mood.RUN && mood != Mood.CHARGE && rng.nextDouble() < fish.runs() * intensity * stamina * DT) {
            startRun(1.5 + 2.5 * rng.nextDouble(), rng);
        } else if (mood == Mood.HOLD && rng.nextDouble() < fish.charges() * (0.5 + stamina) * DT) {
            setMood(Mood.CHARGE, 0.8 + 1.7 * rng.nextDouble());
            // Some come in lazily, some dash.
            chargePace = 0.3 + 0.5 * rng.nextDouble();
            side = 0;
        } else if (mood == Mood.HOLD && rng.nextDouble() < SHAKE_RATE * (0.4 + stamina) * intensity * DT) {
            setMood(Mood.SHAKE, 0.6 + 0.8 * rng.nextDouble());
            shakeHz = 2.5 + rng.nextDouble();
            side = 0;
        } else if (mood == Mood.HOLD && stamina < 0.45 && rng.nextDouble() < REST_RATE * DT) {
            setMood(Mood.REST, 1.5 + 1.5 * rng.nextDouble());
            side = 0.3 * holdSide;
        }
        boolean charging = mood == Mood.CHARGE;
        double spike = 0;
        if ((progress > 0.66 || distance() < 8) && rng.nextDouble() < fish.jumps() * (0.3 + 0.7 * stamina) * DT) {
            jumped = true;
            spike += maxPull * (0.6 + 0.6 * rng.nextDouble()) * staminaFactor;
        }
        if (runStarted) spike += maxPull * 0.5 * staminaFactor;

        // The pull: the species' intensity, how tired it is and what it is doing, with a slow sway as it works its
        // tail and changes angle. Muscles don't step: the effort follows its target over a few tenths of a second,
        // faster into a run. Head shakes ride on top as a quick throb the line passes on.
        double seconds = ticks * DT;
        double sway = 1 + SWAY_SLOW * Math.sin(2 * Math.PI * seconds / swayPeriod1 + swayPhase1)
                + SWAY_FAST * Math.sin(2 * Math.PI * seconds / swayPeriod2 + swayPhase2);
        double base = maxPull * intensity * staminaFactor;
        double target = switch (mood) {
            case RUN -> base * 1.5 * sway;
            case CHARGE -> 0;
            case REST -> base * REST_PULL;
            default -> base * sway;
        };
        if (ticks == 1) effort = target;
        double tau = mood == Mood.RUN && moodTime < 0.5 ? EFFORT_TAU_RUN : EFFORT_TAU;
        effort += (target - effort) * Math.min(1, DT / tau);
        double shake = mood == Mood.SHAKE ? SHAKE_DEPTH * Math.sin(2 * Math.PI * shakeHz * moodTime) : 0;
        pull = Math.max(0, effort * (1 + shake));
        double swim = fish.speed() * (0.4 + 0.6 * intensity) * (mood == Mood.RUN ? 1.8 : 1) * (0.3 + 0.7 * stamina) * 0.8;
        double chargeSpeed = fish.speed() * chargePace;
        double towDrag = TOW_DRAG * Math.pow(weight, 2.0 / 3);

        // Drag, reel and line. An empty spool has nothing left to give: the drag is as good as locked.
        spooled = tackle.hasReel() && lineOut >= tackle.lineCapacity() - 1e-9;
        double drag = tackle.hasReel() && !spooled ? clamp(in.drag, 0.05, tackle.reelMaxDrag()) : Double.POSITIVE_INFINITY;
        double crank = in.crankLimit(tackle);
        double retrieve = tackle.reelRetrieve() * in.retrieveScale;
        double recede = Math.max(0, in.recede);

        // The rod: lifting draws line back by the tip's travel, lowering gives it. Winding while the rod comes down:
        // it comes down as fast as the reel takes up the line ("winding down"): the angler's arms hold the fish while
        // the crank turns with no load, and the line stays as it was.
        double rodIn = 0;
        boolean windingDown = false;
        if (in.liftSweep > 0) {
            double before = rodHeight;
            windingDown = !in.lifting && in.reeling && rodHeight > 0;
            double lower = windingDown ? Math.min(LOWER_RATE, retrieve / in.liftSweep) : LOWER_RATE;
            rodHeight = in.lifting ? Math.min(1, rodHeight + liftRate(tension / tackle.rodMaxLoad()) * DT)
                    : Math.max(0, rodHeight - lower * DT);
            rodIn = (rodHeight - before) * in.liftSweep;
        }

        // The line is a spring: the rod's bend and the line's stretch (mono a lot, braid hardly) store how far the
        // fish is beyond the line out, and the tension is that stretch over the rig's give. A stretch below zero is
        // slack. Each tick the fish swims by the balance of its pull against the tension (free with no load, slower
        // as the tension nears its pull, dragged back through the water once the tension is more), the reel winds
        // what its crank can against the tension, the rod and an angler moving off add their bit, and the drag lets
        // line go once the tension reaches it. The tension that satisfies all of that at once is found by bisection,
        // so a stiff braid on a short line is as steady as a long stretchy mono.
        double give = give();
        double before = stretch;
        double cap = drag;
        double fishPull = pull;
        boolean reeling = in.reeling;
        double drawn = rodIn;
        boolean unloaded = windingDown;
        java.util.function.DoubleUnaryOperator next = t -> before + drawn
                + DT * (fishVelocity(t, fishPull, swim, charging, chargeSpeed, towDrag) + recede
                + (reeling ? unloaded ? retrieve : retrieve * Math.max(0, 1 - t / crank) : 0));
        double free = next.applyAsDouble(0);
        double out = 0;
        if (free <= 0) {
            tension = 0;
            stretch = free;
        } else {
            double hi = cap;
            if (Double.isInfinite(hi)) {
                hi = Math.max(1, 2 * maxPull);
                while (hi < next.applyAsDouble(hi) / give) hi *= 2;
            }
            if (next.applyAsDouble(hi) / give >= hi) {
                // At the drag: it slips, and what the fish gains goes out as line.
                tension = hi;
                stretch = hi * give;
                out = next.applyAsDouble(hi) - stretch;
            } else {
                double lo = 0;
                for (int i = 0; i < 40 && hi - lo > 1e-5 * (1 + hi); i++) {
                    double mid = 0.5 * (lo + hi);
                    if (next.applyAsDouble(mid) / give > mid) lo = mid;
                    else hi = mid;
                }
                tension = 0.5 * (lo + hi);
                stretch = tension * give;
            }
        }
        double distanceBefore = distance();
        double wound = !reeling ? 0 : windingDown ? retrieve * DT : retrieve * Math.max(0, 1 - tension / crank) * DT;
        double lineBefore = lineOut;
        lineOut += out - wound - rodIn;
        if (tackle.hasReel()) lineOut = Math.min(lineOut, tackle.lineCapacity());
        lineOut = Math.max(lineOut, LAND_DISTANCE - stretch);
        slipping = out > 1e-9 && Double.isFinite(cap);
        stalled = reeling && !windingDown && tension >= crank;
        takingLine = lineOut > lineBefore + 1e-9;
        if (stretch < -SLACK_TIGHT) {
            slackFor += DT;
        } else {
            slackFor = 0;
        }

        // Cranking while the fish takes line (or against it on an empty spool, which is the reel's full drag): the
        // gears grind (Fishing Planet's "reel overload"). Once the fish starts taking line the angler has half a
        // second to stop cranking; after that it grinds with the square of the drag, so half the reel's drag grinds a
        // quarter as fast as full.
        // Round the angler: the fish leans into its sideways swim over half a second or so, and never swims faster
        // than it can, all directions together. What it spends going out (or being hauled in) it can't spend going
        // sideways, though it always keeps enough to turn. Its top speed is its species' speed for its size, more
        // on a run, less as it tires.
        topSpeed = fish.speed() * sizeSpeed * (0.4 + 0.6 * intensity) * (mood == Mood.RUN ? 1.8 : 1) * (0.3 + 0.7 * stamina);
        double radial = Math.min(Math.abs(distance() - distanceBefore) / DT, (1 - SIDE_FLOOR * SIDE_FLOOR) * topSpeed);
        double sideRoom = Math.sqrt(Math.max(0, topSpeed * topSpeed - radial * radial));
        sideSpeed += (side * topSpeed - sideSpeed) * Math.min(1, DT / SIDE_TAU);
        sideSpeed = clamp(sideSpeed, -sideRoom, sideRoom);

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
            if (tackle.hasReel() && peak > drag) {
                peak = drag + (peak - drag) * (0.55 - 0.45 * tackle.reelSmooth());
                // A drag set at or below the line's test slips before the line can break: the jolt can take the
                // line close to its test, never past it.
                if (drag <= breakLoad) peak = Math.min(peak, drag + (breakLoad - drag) * 0.9);
            }
            peakTension = peak;
        }
        highestTension = Math.max(highestTension, peakTension);

        // Teeth on non-steel line: a little fraying all fight, more on every run or jump, by how big the fish is for
        // the line. One far too big for it can bite straight through.
        boolean teeth = fish.toothy() && !tackle.steelLeader();
        boolean cleanBite = false;
        if (teeth && tension > 0) {
            double oversize = maxPull / breakLoad;
            teethFray += DT * TEETH_FRAY_PER_SECOND * oversize;
            if (jumped || runStarted) {
                teethFray += TEETH_FRAY_PER_LUNGE * oversize * oversize;
                cleanBite = oversize > TEETH_CLEAN_BITE && rng.nextDouble() < Math.min(0.5, 0.25 * (oversize - TEETH_CLEAN_BITE + 1));
            }
        }

        // Failures.
        if (peakTension > breakLoad) {
            outcome = spooled ? Outcome.SPOOLED : Outcome.LINE_SNAPPED;
            loss = spooled ? Loss.SPOOLED : tackle.hasReel() ? Loss.SNAP_DRAG : Loss.SNAP_POLE;
            return;
        }
        // A line of unknown wear (the simulator) counts as new: it frays through at 1.
        if (cleanBite || teethFray > 0 && teethFray >= Math.min(1, lineLife)) {
            outcome = Outcome.BITTEN_OFF;
            loss = cleanBite ? Loss.BITTEN_CLEAN : Loss.BITTEN_CHEWED;
            return;
        }
        if (reelWear * REEL_DURABILITY_PER_WEAR >= reelLife) {
            outcome = Outcome.REEL_BROKE;
            loss = Loss.REEL_SEIZED;
            return;
        }
        double rodLoad = peakTension / tackle.rodMaxLoad();
        if (rodLoad > 0.7) rodWear += DT * (rodLoad - 0.7) * 2;
        if (rodWear * ROD_DURABILITY_PER_WEAR >= rodLife) {
            outcome = Outcome.ROD_BROKE;
            loss = Loss.ROD_WORN;
            return;
        }
        if (rodLoad > 1 && rng.nextDouble() < (rodLoad - 1) * 0.04) {
            outcome = Outcome.ROD_BROKE;
            loss = Loss.ROD_OVERLOAD;
            return;
        }
        double hold = tackle.hookHold();
        // Slack means the fish isn't pulling and nobody is winding (it swam toward the angler, or the rod was let
        // down), not merely a small fish pulling little while it is wound in: a hook being drawn in stays put.
        slackLine = tension < 0.06 * maxPull && !in.reeling;
        slackTicks = slackLine ? slackTicks + 1 : Math.max(0, slackTicks - 2);
        if (slackTicks > 20 && rng.nextDouble() < 0.01 + 0.03 * (1 - hold)) {
            outcome = Outcome.HOOK_THROWN;
            loss = Loss.HOOK_SLACK;
            return;
        }
        // A jump throws the hook only on a loose line: the fish leaps up and toward the angler and shakes its head
        // in the air. Winding (or lifting the rod) through the jump takes that up and the line stays tight, so the
        // hook stays put; a hook too small for its mouth can still tear out on the jolt (below). Winding doesn't
        // help if the line is slack anyway because the fish swims at the angler faster than the reel takes it up.
        boolean takingUp = in.reeling || rodIn > 1e-9;
        boolean outswum = stretch < -SLACK_TIGHT;
        if (jumped && (!takingUp || outswum)
                && rng.nextDouble() < (0.08 + 0.4 * (1 - hold)) * (charging ? 2 : 1)) {
            outcome = Outcome.HOOK_THROWN;
            loss = takingUp ? Loss.HOOK_JUMP_SLACK : Loss.HOOK_JUMP;
            return;
        }
        if (spike > 0 && hold < 0.5 && rng.nextDouble() < (0.5 - hold) * 0.6) {
            outcome = Outcome.HOOK_THROWN;
            loss = Loss.HOOK_HOLD;
            return;
        }

        // Stamina: spent against tension, faster under side pressure; a little back while slack.
        double effort = Math.min(tension, pull) / maxPull;
        stamina -= DT * effort * (1 + 0.6 * sidePressure(in.rodAngle)) / endurance;
        if (tension < 0.15 * maxPull) stamina += DT * 0.015;
        stamina = clamp(stamina, 0, 1);

        if (distance() <= LAND_DISTANCE + 1e-9) {
            if (stamina < 0.35 || weight < 0.4) {
                outcome = Outcome.LANDED;
            } else if (mood != Mood.RUN) {
                // Still fresh at the angler's feet: it bolts again.
                startRun(1.5, rng);
            }
        }
    }


    /**
     * How fast the fish moves away from the angler through the water at a line tension of {@code t}, in m/s: as fast
     * as it swims with no load, slower as the tension nears its pull, and dragged back once the tension is more (the
     * water's drag on it sets how fast). A fish swimming at the angler comes in on its own, faster if the line pulls.
     */
    private static double fishVelocity(double t, double pull, double swim, boolean charging, double chargeSpeed, double towDrag) {
        if (charging) return -chargeSpeed - Math.sqrt(t / towDrag);
        if (t < pull) return swim * (1 - t / pull);
        return -Math.sqrt((t - pull) / towDrag);
    }

    /**
     * The rig's give, in metres of stretch per kgf: the line's (by its stretch, how much is out and its test) plus the
     * rod's bend (a soft rod gives more, a stout one less).
     */
    private double give() {
        double line = LINE_GIVE * tackle.lineStretch() * Math.max(1, lineOut) / Math.max(0.1, tackle.lineTest());
        double rod = (0.4 + 0.6 * tackle.rodAbsorb()) / Math.max(0.1, tackle.rodMaxLoad());
        return Math.max(0.002, line + rod);
    }

    private void setMood(Mood next, double seconds) {
        mood = next;
        moodLeft = seconds;
        moodTime = 0;
    }

    /** Back to holding: steady pulling, drifting round on a slow arc that it changes every few seconds. */
    private void hold(RandomGenerator rng) {
        setMood(Mood.HOLD, 0);
        holdFor = 3 + 4 * rng.nextDouble();
        holdSide = (rng.nextDouble() * 2 - 1) * SIDE_HOLD;
        side = holdSide;
    }

    private void startRun(double seconds, RandomGenerator rng) {
        setMood(Mood.RUN, seconds);
        runStarted = true;
        side = (rng.nextBoolean() ? 1 : -1) * (SIDE_RUN_LOW + (1 - SIDE_RUN_LOW) * rng.nextDouble());
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
        return clamp(1 - (distance() - LAND_DISTANCE) / (startDistance - LAND_DISTANCE), 0, 1);
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Ends the fight from outside (the angler cut the line, the rod left the hand...). */
    public void end(Outcome why) {
        if (!outcome.over()) outcome = why;
    }

    /** Why the fight was lost, or {@code null} while it goes on, once landed, or when ended from outside. */
    public Loss loss() {
        return loss;
    }

    /** The fish took line this tick (through the drag, or off a slack line). */
    public boolean takingLine() {
        return takingLine;
    }

    /** The line was slack this tick: the fish not pulling and the angler not winding. */
    public boolean slackLine() {
        return slackLine;
    }

    /** Fraying from teeth this fight, counted against the line like its other wear. */
    public double teethFray() {
        return teethFray;
    }

    /**
     * How hard the reel is working, 0..1 and over: the tension against the most drag it can apply, or (while
     * winding) against what its crank can wind against; a grinding reel is past its limit. NaN without a reel.
     */
    public double reelLoad(Controls in) {
        if (!tackle.hasReel()) return Double.NaN;
        if (grinding) return 1.1;
        double load = tension / tackle.reelMaxDrag();
        if (in.reeling) load = Math.max(load, tension / Math.max(0.01, in.crankLimit(tackle)));
        return load;
    }

    /** Where the fish is: the line out plus its stretch (less when the line is slack). */
    public double distance() {
        return lineOut + stretch;
    }

    /** Line off the spool, metres. */
    public double lineOut() {
        return lineOut;
    }

    public Mood mood() {
        return mood;
    }

    /** How fast the fish swims round the angler, m/s: positive one way, negative the other. */
    public double sideSpeed() {
        return sideSpeed;
    }

    /** How fast the fish can swim right now, m/s, all directions together. */
    public double topSpeed() {
        return topSpeed;
    }

    /** Something (a shore, the bottom) is in its way sideways: it turns back the other way, from a standstill. */
    public void blockedSideways() {
        side = -side;
        holdSide = -holdSide;
        sideSpeed = 0;
    }

    /**
     * This fish's length over its species' usual length (the middle of the usual range). A bigger fish swims a bit
     * faster round the angler, with the square root of its length, from 0.6 to 1.6 times its species' speed.
     */
    public void setSizeRatio(double lengthRatio) {
        sizeSpeed = clamp(Math.sqrt(Math.max(0, lengthRatio)), 0.6, 1.6);
    }

    /** The fish is swimming at the angler and the line has gone slack: wind faster. */
    public boolean comingAtYou() {
        return mood == Mood.CHARGE && stretch < -SLACK_TIGHT;
    }

    /** How long the line has been slack, seconds. */
    public double slackSeconds() {
        return slackFor;
    }

    /** Rod height, 0 lowered to 1 fully lifted. */
    public double rodHeight() {
        return rodHeight;
    }

    /** Metres of slack line: the fish closer than the line out (it swam in, or the rod was let down). */
    public double slack() {
        return Math.max(0, -stretch);
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
        return mood == Mood.CHARGE;
    }

    public boolean jumped() {
        return jumped;
    }

    public boolean runStarted() {
        return runStarted;
    }

    public boolean running() {
        return mood == Mood.RUN;
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

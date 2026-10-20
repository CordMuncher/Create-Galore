package dev.creategalore.fishing.sim;

/**
 * Decides the controls each tick for the Monte Carlo runner and for the reel drives. A player in game is not an
 * Angler: their controls come from what they actually do.
 */
public interface Angler {
    /** Sets {@code controls} for the next tick of {@code fight}. Called before {@link Fight#tick}. */
    void act(Fight fight, Controls controls);

    /** Short name for reports. */
    String name();

    /**
     * A sensible player: drag at about a third of the breaking load, reels while the drag holds and the tension is
     * safe, keeps reeling when the line goes slack, holds the rod well off the line.
     */
    static Angler careful() {
        return new Angler() {
            @Override
            public void act(Fight fight, Controls c) {
                Tackle t = fight.tackle();
                c.drag = Math.min(t.reelMaxDrag(), 0.3 * t.breakingLoad());
                c.reeling = !fight.slipping() && fight.tension() < 0.8 * t.breakingLoad();
                c.rodAngle = Math.toRadians(50);
                c.crankThroughSlip = false;
            }

            @Override
            public String name() {
                return "careful";
            }
        };
    }

    /** A careless player: drag cranked high, reels non-stop, points the rod at the fish. */
    static Angler careless() {
        return new Angler() {
            @Override
            public void act(Fight fight, Controls c) {
                Tackle t = fight.tackle();
                c.drag = Math.min(t.reelMaxDrag(), 0.75 * t.breakingLoad());
                c.reeling = true;
                c.rodAngle = Math.toRadians(8);
                c.crankThroughSlip = true;
            }

            @Override
            public String name() {
                return "careless";
            }
        };
    }

    /**
     * The Basic Reel Drive: reels all the time at a fixed speed, even while the drag slips. {@code drag} is what the
     * player set on the rod before mounting it; {@code torque} the pull the drive's input can wind against.
     */
    static Angler basicDrive(double drag, double torque) {
        return new Angler() {
            @Override
            public void act(Fight fight, Controls c) {
                c.drag = drag;
                c.reeling = true;
                c.crankThroughSlip = true;
                c.rodAngle = Math.toRadians(40);
                c.crankLimit = torque;
                c.retrieveScale = 1;
            }

            @Override
            public String name() {
                return "basic_drive";
            }
        };
    }

    /** The Geared (or Fuel) Reel Drive: auto-shifting, never cranks while the drag slips. */
    static Angler gearedDrive(double drag, double torqueAtOne) {
        Gearbox box = new Gearbox(0, Gearbox.GEARS.length - 1);
        return new Angler() {
            @Override
            public void act(Fight fight, Controls c) {
                c.drag = drag;
                box.update(torqueAtOne, fight.pull());
                box.apply(c, torqueAtOne);
                c.reeling = !fight.slipping();
                c.crankThroughSlip = false;
                c.rodAngle = Math.toRadians(40);
            }

            @Override
            public String name() {
                return "geared_drive";
            }
        };
    }
}

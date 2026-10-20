package dev.creategalore.fishing.sim;

/** How a fight ended, or {@link #FIGHTING} while it goes on. */
public enum Outcome {
    FIGHTING,
    /** The fish is at the angler and tired enough to land. */
    LANDED,
    /** Tension went over the line's (or leader's) breaking load. */
    LINE_SNAPPED,
    /** A toothy fish bit through a line without a steel leader. */
    BITTEN_OFF,
    /** The rod was overloaded until it cracked. */
    ROD_BROKE,
    /** The hook came out: slack line, a jump on a loose line, or a hook too small for the mouth. */
    HOOK_THROWN,
    /** The fish took every metre on the spool. */
    SPOOLED;

    public boolean over() {
        return this != FIGHTING;
    }

    /** Ends that cost the angler line and terminal tackle. */
    public boolean losesTackle() {
        return this == LINE_SNAPPED || this == BITTEN_OFF || this == SPOOLED;
    }
}

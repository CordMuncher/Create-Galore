package dev.creategalore.fishing.sim;

/**
 * The numbers of an assembled rig that the fight reads. Built from the rod's parts by the tackle code; the fight
 * itself never looks at items.
 *
 * <p>All forces are in kilograms-force (kgf), the unit anglers use for drag and line ratings.
 *
 * @param rodMaxLoad   load the rod blank is rated for. Above it the rod is overloaded and may crack.
 * @param rodAbsorb    how much shock the bent rod soaks, 0..1. Slow (soft) actions soak more than fast ones.
 * @param hasReel      false for a fixed-line pole: no drag, the line and rod take everything, "reeling" is lifting.
 * @param reelMaxDrag  the most drag the reel can apply.
 * @param reelRetrieve line wound in per second at full cranking with no load, in metres.
 * @param reelPower    the pull the angler can still crank against; above it retrieval stops.
 * @param reelSmooth   how evenly the drag starts to slip, 0..1. A smooth drag catches sudden spikes before they
 *                     reach the line; a sticky one lets part of the spike through.
 * @param lineCapacity metres of line on the spool. A fish that takes it all breaks off.
 * @param lineTest     the main line's breaking load.
 * @param lineStretch  how much the line stretches, 0..1 (mono stretches, braid almost not at all). Soaks shocks.
 * @param leaderTest   breaking load of the leader, or {@code Double.NaN} with no leader.
 * @param steelLeader  a steel leader: toothy fish can't bite through it.
 * @param hookHold     how well the hook holds in this fish's mouth, 0..1 (depends on hook size vs mouth).
 */
public record Tackle(double rodMaxLoad, double rodAbsorb, boolean hasReel, double reelMaxDrag, double reelRetrieve,
                     double reelPower, double reelSmooth, double lineCapacity, double lineTest, double lineStretch,
                     double leaderTest, boolean steelLeader, double hookHold) {

    /** The load at which the rig breaks off: the weaker of line and leader. */
    public double breakingLoad() {
        return Double.isNaN(leaderTest) ? lineTest : Math.min(lineTest, leaderTest);
    }

    /** The same rig with another hook hold, for a fish with a different mouth. */
    public Tackle withHookHold(double hold) {
        return new Tackle(rodMaxLoad, rodAbsorb, hasReel, reelMaxDrag, reelRetrieve, reelPower, reelSmooth,
                lineCapacity, lineTest, lineStretch, leaderTest, steelLeader, hold);
    }

    /**
     * The heaviest steady pull this rig can hold without anything failing, which tooltips call "rated for". It is the
     * weakest of the rod, the line or leader and (for reels) the drag.
     */
    public double ratedLoad() {
        double load = Math.min(rodMaxLoad, breakingLoad());
        return hasReel ? Math.min(load, reelMaxDrag) : load;
    }
}

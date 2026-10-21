package dev.creategalore.fishing.sim;

/**
 * What the angler (a player, or a reel drive) does this tick. Mutable and reused between ticks.
 */
public final class Controls {
    /** Cranking the reel (a player holding use; a drive turning). */
    public boolean reeling;
    /** Drag setting in kgf, already clamped to the reel's maximum. Ignored for poles. */
    public double drag;
    /**
     * Angle in radians between where the rod points and the line to the fish. Around 30-100 degrees the rod bends
     * and soaks shocks (and adds side pressure that tires the fish); pointing straight down the line soaks nothing.
     */
    public double rodAngle = Math.toRadians(45);
    /** The pull the crank can still wind against; players use the reel's own power, drives their torque. */
    public double crankLimit = Double.NaN;
    /** Retrieve speed multiplier: 1 for a player, the gear ratio for drives. */
    public double retrieveScale = 1;
    /**
     * Keep cranking while the drag slips. Players who do this and the Basic Reel Drive (which can't tell) wear the
     * reel out fast; the geared drives pause instead.
     */
    public boolean crankThroughSlip = true;
    /** How fast the rod tip moves away from the fish, m/s (walking off, or a boat under way); 0 in the simulator. */
    public double recede;

    /** Returns the crank limit to use against {@code tackle}: {@link #crankLimit} if set, else the reel's power. */
    public double crankLimit(Tackle tackle) {
        return Double.isNaN(crankLimit) ? tackle.reelPower() : crankLimit;
    }
}

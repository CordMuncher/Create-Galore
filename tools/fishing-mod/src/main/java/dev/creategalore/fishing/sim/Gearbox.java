package dev.creategalore.fishing.sim;

/**
 * The automatic gearbox in the Geared and Fuel Reel Drives. Stepping down gives more torque and less speed;
 * stepping up the opposite. It shifts down when the fish pulls close to what the drive can wind against at the
 * current gear (instead of stalling) and up again once the fish eases off.
 *
 * <p>Gear values are retrieve multipliers: 0.25 (lowest, four times the torque) to 2 (highest, half the torque).
 * The drive can limit the range with its value box.
 */
public final class Gearbox {
    public static final double[] GEARS = {0.25, 0.5, 0.75, 1.0, 1.5, 2.0};

    private final int lowest;
    private final int highest;
    private int gear;
    private int cooldown;

    /** @param lowest index into {@link #GEARS} @param highest index into {@link #GEARS} */
    public Gearbox(int lowest, int highest) {
        this.lowest = Math.max(0, Math.min(lowest, GEARS.length - 1));
        this.highest = Math.max(this.lowest, Math.min(highest, GEARS.length - 1));
        this.gear = Math.max(this.lowest, Math.min(3, this.highest));
    }

    public double ratio() {
        return GEARS[gear];
    }

    public int index() {
        return gear;
    }

    /**
     * Picks this tick's gear. {@code torqueAtOne} is the pull the drive can wind against in gear 1.0 (its network
     * headroom or engine rating, in kgf); {@code pull} is what the fish pulls now. Shifts at most once every half
     * second so it doesn't hunt.
     */
    public void update(double torqueAtOne, double pull) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        double limit = torqueAtOne / ratio();
        if (pull > 0.85 * limit && gear > lowest) {
            gear--;
            cooldown = 10;
        } else if (gear < highest && pull < 0.35 * torqueAtOne / GEARS[gear + 1]) {
            gear++;
            cooldown = 10;
        }
    }

    /** Sets the controls for this gear: retrieve speed scales with the ratio, the crank limit inversely. */
    public void apply(Controls controls, double torqueAtOne) {
        controls.retrieveScale = ratio();
        controls.crankLimit = torqueAtOne / ratio();
    }
}

package dev.creategalore.bridge;

/**
 * Filter frames in this pack need no filter material: every frame catches every kind of smog, and its metal sets how
 * fast. A frame takes up to {@link #rate} units a second, shared between smog touching it, pumps and pressure.
 */
public final class FilterUpkeep {
    private FilterUpkeep() {}

    /** Units a second for a frame of this slot limit: iron 16 -> 2, gold 32 -> 4, diamond 64 -> 8. */
    public static int rate(int slotLimit) {
        return Math.max(1, slotLimit / 8);
    }

    /** What a frame's budget tracks: the second it was last used in and how much it has taken in it. */
    public interface Budget {
        long create_galore_bridge$second();

        int create_galore_bridge$used();

        void create_galore_bridge$set(long second, int used);
    }

    /** Takes up to {@code amount} units from this second's budget and returns how many it got. */
    public static int take(Budget budget, int slotLimit, int amount, long gameTime) {
        long second = gameTime / 20;
        int used = budget.create_galore_bridge$second() == second ? budget.create_galore_bridge$used() : 0;
        int granted = Math.min(amount, rate(slotLimit) - used);
        if (granted <= 0) return 0;
        budget.create_galore_bridge$set(second, used + granted);
        return granted;
    }
}

package dev.creategalore.fishing.cast;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Which players are lifting their rod right now (holding attack while their line is out; see
 * {@code client/FishingInput} and {@link dev.creategalore.fishing.net.LiftInput}). The hook reads it every tick:
 * a lift strikes a bite, pumps a hooked fish in and twitches a lure. Keyed by the player object, so the client's and
 * the integrated server's players stay apart, and forgotten with them.
 */
public final class RodLift {
    private static final Map<Player, Boolean> LIFTING = Collections.synchronizedMap(new WeakHashMap<>());

    private RodLift() {
    }

    public static boolean lifting(Player player) {
        return LIFTING.containsKey(player);
    }

    public static void set(Player player, boolean lifting) {
        if (lifting) LIFTING.put(player, Boolean.TRUE);
        else LIFTING.remove(player);
    }
}

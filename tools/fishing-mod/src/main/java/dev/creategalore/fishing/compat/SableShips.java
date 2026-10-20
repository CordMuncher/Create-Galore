package dev.creategalore.fishing.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Rod machines on Sable ships (Create: Aeronautics and friends). A ship's blocks and block entities live in a plot
 * of their own, far from where the ship is drawn, so a machine on deck works out its rod tip, its cast direction
 * and its angler's position in the plot and has them projected into the world here. Off a ship, or without Sable,
 * points come back unchanged.
 */
public final class SableShips {
    private SableShips() {
    }

    /** Sable's companion library, bundled by Sable and by the mods that support its ships. */
    public static final String MOD = "sablecompanion";
    private static final boolean LOADED = ModList.get().isLoaded(MOD);

    /** Where {@code local} (in the block grid a block entity lives in) is in the world. */
    public static Vec3 toWorld(Level level, Vec3 local) {
        return LOADED ? Companion.toWorld(level, local) : local;
    }

    /** {@code direction} at {@code local}, turned the way the ship is turned. */
    public static Vec3 directionToWorld(Level level, Vec3 local, Vec3 direction) {
        if (!LOADED) return direction;
        return Companion.toWorld(level, local.add(direction)).subtract(Companion.toWorld(level, local));
    }

    /** Only loaded when the companion library is. */
    private static final class Companion {
        static Vec3 toWorld(Level level, Vec3 local) {
            return SableCompanion.INSTANCE.projectOutOfSubLevel(level, local);
        }
    }
}

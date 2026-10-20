package dev.creategalore.fishing.machine;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A machine holding a rod (Rod Holder, Reel Drives): what the cast hook asks it in place of a player's hands. The
 * hook's owner is the machine's {@link MachineAngler}, so everything else about the hook (bites, the fight, other
 * mods' hooks and events) works as it does for a player.
 */
public interface RodMount {
    /** Where the machine is (the hook keeps it in its synced data, so clients can draw the line). */
    BlockPos mountPos();

    /** The rod tip in world space. */
    Vec3 tip();

    /** Whether the reel turns this tick, outside a fight (retrieving a lure, winding in to recast). */
    boolean reeling(CastHook hook);

    /** Retrieve speed outside a fight as a multiple of the reel's (gear and shaft speed). */
    default double retrieveScale() {
        return 1;
    }

    /** Ticks a bite lasts before the fish lets go: long on a holder, so someone can come and take the rod. */
    int biteTicks();

    /** A fish took the bait (alarm, redstone). Whether the machine strikes is its {@link #strikes}. */
    void bite(CastHook hook);

    /** True if the machine sets the hook itself when it feels a bite (the geared drives). */
    boolean strikes(CastHook hook);

    /**
     * This tick of a fight: the machine fills in what the angler would do (cranking, the crank's limit, retrieve
     * speed, whether it cranks through a slipping drag, the rod's angle to the line).
     */
    void fight(CastHook hook, Fight fight, RodStats stats, Controls controls);

    /**
     * The fight is won: the fish and anything else that came up, and the XP a player would have earned (the machine
     * banks half). The items are the machine's to route.
     */
    void landed(CastHook hook, List<ItemStack> items, int handXp);

    /** The fight is lost (or the line came back empty). */
    void lost(CastHook hook, Outcome outcome);

    /** The name catches are credited to ("caught by"), the player who set the rod up; empty if unknown. */
    String creditName();

    /** The machine's shared parts (rod, banked XP, a hanging fish), also what the renderer draws. */
    MountedRod mounted();

    /** The way the rod points. */
    net.minecraft.core.Direction rodFacing();

    /** Where the rod's butt sits, from the block's corner (blocks). */
    Vec3 rodBase();
}

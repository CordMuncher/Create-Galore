package dev.creategalore.fishing.sim;

/** Why a fight was lost: what broke or gave, and so what to do differently. Each has a message for the angler. */
public enum Loss {
    /** The fish pulled past the line's test: the drag was set at or above it. */
    SNAP_DRAG,
    /** The fish pulled past the line's test on a pole, which has no drag to give line. */
    SNAP_POLE,
    /** The fish took every metre on the spool. */
    SPOOLED,
    /** Teeth chewed through non-steel line over the fight. */
    BITTEN_CHEWED,
    /** A fish far too big for the line bit straight through it. */
    BITTEN_CLEAN,
    /** The reel was cranked while the drag slipped until it seized. */
    REEL_SEIZED,
    /** The rod was loaded past its rating and cracked. */
    ROD_OVERLOAD,
    /** The rod was worn through. */
    ROD_WORN,
    /** The line went slack (the fish not pulling, nobody winding) and the hook fell out. */
    HOOK_SLACK,
    /** It jumped and threw the hook while nobody was winding or lifting to keep the line tight. */
    HOOK_JUMP,
    /** It jumped and threw the hook while swimming at the angler faster than the reel took the line up. */
    HOOK_JUMP_SLACK,
    /** The hook was too small for its mouth and tore out on a lunge. */
    HOOK_HOLD;

    /** The key of its message. */
    public String key() {
        return "message.create_galore_fishing.lost_why." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

package dev.creategalore.bridge.mixin;

import dev.thebluetaco.hostileskies.HostileSkies;
import dev.thebluetaco.hostileskies.raid.RaidManager;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a restart Hostile Skies gives each saved airship raid one minute to find its ship. A ship whose chunks nobody
 * loads in that minute (it is away from spawn and the player joins later, or the world is still loading) is dropped:
 * the ship stays in the world with its crew seated, but nothing steers it, so it hangs in the sky forever and never
 * patrols or leaves. Instead, hand those raids to the running list, which already waits for ships that unload while
 * the server runs: the raid is saved with the world and carries on (patrol, then departure) once its ship loads.
 */
@Mixin(value = RaidManager.class, remap = false)
public abstract class AirshipRestoreKeepMixin {
    @Shadow @Final private static List<Object> activeRaids;
    @Shadow @Final private static List<Object> pendingRestore;

    /** The "sub-level never appeared, dropping" warning is the only one in tickRestore; it no longer drops anything. */
    @Redirect(method = "tickRestore", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private static void create_galore_bridge$keepMessage(Logger logger, String message, Object ship, Object subLevel) {
        logger.info("Raid {} ({}): ship not loaded yet, kept until its chunks load", ship, subLevel);
    }

    /** Runs just before the timeout branch clears the waiting list (its only List.clear()). */
    @Inject(method = "tickRestore", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private static void create_galore_bridge$keepWaiting(MinecraftServer server, CallbackInfo ci) {
        if (pendingRestore.isEmpty()) return;
        activeRaids.addAll(pendingRestore);
        HostileSkies.LOGGER.info("Kept {} raid(s) whose ship has not loaded yet", pendingRestore.size());
    }
}

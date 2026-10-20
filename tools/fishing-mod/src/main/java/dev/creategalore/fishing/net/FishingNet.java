package dev.creategalore.fishing.net;

import dev.creategalore.fishing.tackle.TackleManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Network payloads. The mod is required on both sides, so payloads are not optional; the version string changes
 * whenever a payload's layout does, so mismatched builds refuse to connect instead of misreading each other.
 */
public final class FishingNet {
    public static final String VERSION = "1";

    private FishingNet() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FishingNet::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TackleManager.Sync.TYPE, TackleManager.Sync.STREAM_CODEC,
                (payload, context) -> TackleManager.Sync.handle(payload));
    }

    /** True in singleplayer and on a LAN host, where the client shares the server's data already. */
    public static boolean isIntegratedServerHost() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }
}

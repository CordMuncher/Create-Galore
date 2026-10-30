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
    public static final String VERSION = "4";

    private FishingNet() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FishingNet::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TackleManager.Sync.TYPE, TackleManager.Sync.STREAM_CODEC,
                (payload, context) -> TackleManager.Sync.handle(payload));
        registrar.playToServer(DragAdjust.TYPE, DragAdjust.STREAM_CODEC, DragAdjust::handle);
        registrar.playToServer(LiftInput.TYPE, LiftInput.STREAM_CODEC, LiftInput::handle);
        registrar.playToClient(CatchOffer.TYPE, CatchOffer.STREAM_CODEC,
                (payload, context) -> dev.creategalore.fishing.client.ClientPackets.catchOffer(payload));
        registrar.playToServer(CatchChoice.TYPE, CatchChoice.STREAM_CODEC, CatchChoice::handle);
        registrar.playToClient(FishDataSync.TYPE, FishDataSync.STREAM_CODEC,
                (payload, context) -> FishDataSync.handle(payload));
        registrar.playToClient(SizeSync.TYPE, SizeSync.STREAM_CODEC, (payload, context) -> SizeSync.handle(payload));
    }

    /** True in singleplayer and on a LAN host, where the client shares the server's data already. */
    public static boolean isIntegratedServerHost() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }
}

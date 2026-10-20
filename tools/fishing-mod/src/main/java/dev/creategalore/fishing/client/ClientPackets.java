package dev.creategalore.fishing.client;

import dev.creategalore.fishing.net.CatchOffer;

/**
 * Client ends of server-to-client payloads. Common code calls these by name only; the client classes they use are
 * only loaded when a packet actually arrives on a client.
 */
public final class ClientPackets {
    private ClientPackets() {
    }

    public static void catchOffer(CatchOffer offer) {
        CatchScreen.open(offer);
    }
}

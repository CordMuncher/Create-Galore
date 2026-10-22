package dev.creategalore.bridge.client;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade loads its plugins on servers too, so everything that touches the client lives in {@link GasJadeClient}. */
@WailaPlugin
public final class GasJade implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        GasJadeClient.register(registration);
    }
}

package dev.creategalore.fishing.client;

import net.neoforged.bus.api.IEventBus;

/**
 * Client-only setup. Only ever called when {@code FMLEnvironment.dist.isClient()}, so classes reached from here may
 * use client code freely; nothing on the common side may refer to this package.
 */
public final class FishingClient {
    private FishingClient() {
    }

    public static void init(IEventBus modBus) {
    }
}

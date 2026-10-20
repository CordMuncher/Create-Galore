package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup. Only ever called when {@code FMLEnvironment.dist.isClient()}, so classes reached from here may
 * use client code freely; nothing on the common side may refer to this package.
 */
public final class FishingClient {
    private FishingClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) ->
                event.register(FishingRegistry.TACKLE_BENCH_MENU.get(), TackleBenchScreen::new));
    }
}

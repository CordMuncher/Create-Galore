package dev.creategalore.bridge.client;

import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.mixin.ItemPropertiesInvoker;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Client-only setup. Loaded only on the client, so the server never touches the client classes it names. */
public final class BridgeClient {
    private BridgeClient() {
    }

    public static void init(IEventBus modBus) {
        // Mods are built before Minecraft reads options.txt, so the file can still be fixed here.
        KeyRebindMigration.run();
        modBus.addListener(BridgeClient::setup);
        modBus.addListener(ShipWaterCutaway::registerShaders);
        GuideAiClient.init(modBus);
        WearClient.init(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShipGlueView::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SchematicTrace::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SchematicTrace::render);
        PinnedSchematicView.init();
        dev.creategalore.bridge.dlss.Dlss.init(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> ShipAnimatedSprites.clear());
    }

    private static void setup(FMLClientSetupEvent event) {
        // The boss compass needle points at the lair it found last, the same way a lodestone compass does.
        event.enqueueWork(() -> {
            for (DeferredItem<? extends Item> compass : CreateGaloreBridge.BOSS_COMPASSES) {
                ItemPropertiesInvoker.create_galore_bridge$register(compass.get(), ResourceLocation.withDefaultNamespace("angle"),
                        new CompassItemPropertyFunction((level, stack, entity) -> {
                            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                            return tracker == null ? null : tracker.target().orElse(null);
                        }));
            }
        });
    }
}

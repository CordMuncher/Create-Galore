package dev.creategalore.fishing;

import dev.creategalore.fishing.client.FishingClient;
import dev.creategalore.fishing.command.FishingCommand;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.fish.SpeciesSelfTest;
import dev.creategalore.fishing.net.FishingNet;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TackleSelfTest;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create Galore Fishing: realistic-lite fishing that replaces Tide in the pack.
 *
 * <p>Entry point only. Each feature area registers itself from here; see {@code tools/fishing-mod/README.md} for
 * the layout and {@code DESIGN.md} for the plan.
 */
@Mod(CreateGaloreFishing.ID)
public final class CreateGaloreFishing {
    public static final String ID = "create_galore_fishing";
    public static final Logger LOG = LoggerFactory.getLogger("Create Galore Fishing");

    public CreateGaloreFishing(IEventBus modBus) {
        FishingRegistry.register(modBus);
        FishingNet.register(modBus);
        NeoForge.EVENT_BUS.addListener(FishingCommand::register);
        SelfTest.register();
        SpeciesManager.register();
        SpeciesSelfTest.register();
        TackleManager.register();
        TackleSelfTest.register();
        if (FMLEnvironment.dist.isClient()) {
            FishingClient.init(modBus);
        }
    }

    /** A resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}

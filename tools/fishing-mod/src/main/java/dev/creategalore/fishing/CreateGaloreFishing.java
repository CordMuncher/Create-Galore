package dev.creategalore.fishing;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.creategalore.fishing.aquarium.AquariumSelfTest;
import dev.creategalore.fishing.bait.BaitSources;
import dev.creategalore.fishing.bait.Chum;
import dev.creategalore.fishing.cast.AliveFish;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.client.FishingClient;
import dev.creategalore.fishing.command.FishingCommand;
import dev.creategalore.fishing.compat.IntegratedFarmingNets;
import dev.creategalore.fishing.compat.RodSwap;
import dev.creategalore.fishing.compat.TideAliases;
import dev.creategalore.fishing.compat.TideSelfTest;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.fish.SpeciesSelfTest;
import dev.creategalore.fishing.machine.MachineSelfTest;
import dev.creategalore.fishing.machine.ReelDriveBlockEntity;
import dev.creategalore.fishing.net.FishingNet;
import dev.creategalore.fishing.process.ProcessingSelfTest;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TackleSelfTest;
import dev.creategalore.fishing.tide.data.fishing.CatchTables;
import dev.creategalore.fishing.tide.loaders.neoforge.NeoforgeEntrypoint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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

    public CreateGaloreFishing(IEventBus modBus, ModContainer container) {
        // The ported Tide content (fish, journal, extras) registers under the "tide" namespace.
        new NeoforgeEntrypoint(modBus, container);
        TideAliases.register(modBus);
        RodSwap.register(modBus);
        FishingRegistry.register(modBus);
        FishingNet.register(modBus);
        CatchHolding.register(modBus);
        NeoForge.EVENT_BUS.addListener(FishingCommand::register);
        SelfTest.register();
        SpeciesManager.register();
        CatchTables.register();
        SpeciesSelfTest.register();
        TackleManager.register();
        TackleSelfTest.register();
        TideSelfTest.register();
        Chum.register();
        AliveFish.register();
        BaitSources.register();
        ProcessingSelfTest.register();
        AquariumSelfTest.register();
        MachineSelfTest.register();
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            DispenserBlock.registerProjectileBehavior(FishingRegistry.CHUM.get());
            if (ModList.get().isLoaded(IntegratedFarmingNets.MOD)) IntegratedFarmingNets.register();
            // The drives' stress at rest, for Create's tooltips; while a fish is on it is set live (ReelDriveBlockEntity).
            BlockStressValues.IMPACTS.register(FishingRegistry.REEL_DRIVE.get(), () -> ReelDriveBlockEntity.IDLE);
            BlockStressValues.IMPACTS.register(FishingRegistry.GEARED_REEL_DRIVE.get(), () -> ReelDriveBlockEntity.IDLE);
        }));
        if (FMLEnvironment.dist.isClient()) {
            FishingClient.init(modBus);
        }
    }

    /** A resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}

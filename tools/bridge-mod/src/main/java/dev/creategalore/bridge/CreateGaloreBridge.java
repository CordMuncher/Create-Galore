package dev.creategalore.bridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Small fixes between mods that a datapack or config can't reach, plus the pack's own items. */
@Mod(CreateGaloreBridge.ID)
public final class CreateGaloreBridge {
    public static final String ID = "create_galore_bridge";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);

    /** The space-age gate: every way off the planet needs one. Recipe lives in the pack datapack. */
    public static final DeferredItem<StellarCoreItem> STELLAR_CORE = ITEMS.register("stellar_core",
            () -> new StellarCoreItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16)));

    public CreateGaloreBridge(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(CreateGaloreBridge::addToTabs);
    }

    private static final ResourceLocation INGREDIENTS_TAB = ResourceLocation.withDefaultNamespace("ingredients");

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(INGREDIENTS_TAB)) {
            event.accept(STELLAR_CORE);
        }
    }
}

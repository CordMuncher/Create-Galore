package dev.creategalore.bridge;

import dev.creategalore.bridge.client.BridgeClient;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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

    /**
     * One compass per boss lair on the road to the Stars (the Hydra drops the core's Fiery Blood; the Naga, Lich and
     * Minoshroom unlock the way to it). Bosses fought anywhere else (Wither, Ender Dragon, Hullbreaker) need none.
     */
    private static final String TWILIGHT = "twilightforest:twilight_forest";
    public static final DeferredItem<BossCompassItem> NAGA_COMPASS = bossCompass("naga_compass", "twilightforest:naga_courtyard");
    public static final DeferredItem<BossCompassItem> LICH_COMPASS = bossCompass("lich_compass", "twilightforest:lich_tower");
    public static final DeferredItem<BossCompassItem> MINOSHROOM_COMPASS = bossCompass("minoshroom_compass", "twilightforest:labyrinth");
    public static final DeferredItem<BossCompassItem> HYDRA_COMPASS = bossCompass("hydra_compass", "twilightforest:hydra_lair");
    public static final List<DeferredItem<BossCompassItem>> BOSS_COMPASSES =
            List.of(NAGA_COMPASS, LICH_COMPASS, MINOSHROOM_COMPASS, HYDRA_COMPASS);

    private static DeferredItem<BossCompassItem> bossCompass(String name, String structure) {
        return ITEMS.register(name, () -> new BossCompassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), structure, TWILIGHT));
    }

    public CreateGaloreBridge(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(CreateGaloreBridge::addToTabs);
        modBus.addListener((FMLCommonSetupEvent event) -> KnifeUnify.resolve());
        GuideCleanup.register();
        NatureHeal.register();
        if (FMLEnvironment.dist.isClient()) {
            BridgeClient.init(modBus);
        }
    }

    private static final ResourceLocation INGREDIENTS_TAB = ResourceLocation.withDefaultNamespace("ingredients");
    private static final ResourceLocation TOOLS_TAB = ResourceLocation.withDefaultNamespace("tools_and_utilities");

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(INGREDIENTS_TAB)) {
            event.accept(STELLAR_CORE);
        } else if (event.getTabKey().location().equals(TOOLS_TAB)) {
            BOSS_COMPASSES.forEach(event::accept);
        }
    }
}

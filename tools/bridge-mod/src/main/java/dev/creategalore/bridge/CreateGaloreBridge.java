package dev.creategalore.bridge;

import dev.creategalore.bridge.client.BridgeClient;
import dev.creategalore.bridge.encased.EncasedDoors;
import dev.creategalore.bridge.guideai.GuideAi;
import dev.creategalore.bridge.waterpump.WaterPumps;
import dev.creategalore.bridge.wear.Wear;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Small fixes between mods that a datapack or config can't reach, plus the pack's own items. */
@Mod(CreateGaloreBridge.ID)
public final class CreateGaloreBridge {
    public static final String ID = "create_galore_bridge";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);

    /** Cleaned air leaving a filter: harmless, but it has to get out under the open sky before it thins away. */
    public static final DeferredBlock<FilteredExhaustBlock> FILTERED_EXHAUST = BLOCKS.register("filtered_exhaust",
            () -> new FilteredExhaustBlock(BlockBehaviour.Properties.of().replaceable().noCollission().noOcclusion()
                .noLootTable().instabreak().pushReaction(PushReaction.DESTROY).isValidSpawn((state, level, pos, type) -> false)
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false)));

    /** What every filter frame shows inside it; frames need no material, so this is never made or handed out. */
    public static final DeferredItem<Item> FILTER_MEDIA = ITEMS.register("filter_media", () -> new Item(new Item.Properties()));

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

    /** The Field Guide worn as a necklace: its keys open the guide and Ask the Book. Recipe lives in the pack datapack. */
    public static final DeferredItem<FieldGuidePendantItem> FIELD_GUIDE_PENDANT = ITEMS.register("field_guide_pendant",
            () -> new FieldGuidePendantItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private static DeferredItem<BossCompassItem> bossCompass(String name, String structure) {
        return ITEMS.register(name, () -> new BossCompassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), structure, TWILIGHT));
    }

    public CreateGaloreBridge(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        modBus.addListener(CreateGaloreBridge::addToTabs);
        modBus.addListener((FMLCommonSetupEvent event) -> KnifeUnify.resolve());
        GuideCleanup.register();
        NatureHeal.register();
        PredatorFeeding.register();
        VoltaicSeaGrass.register();
        CritterUnify.register();
        AirshipCaptains.register();
        StructureFixes.register();
        ShipGluedWater.register();
        ShipSeatRelog.register();
        ContraptionSeatRelog.register();
        ExhaustPressure.register();
        GuideAi.register(modBus);
        ShipGlueCheck.register(modBus);
        PinnedSchematics.register(modBus);
        SharedSchematics.register(modBus);
        SchematicannonGlue.register(modBus);
        AnimalCare.register();
        AnimalTroughs.register();
        Wear.register(modBus);
        EncasedDoors.register(modBus);
        WaterPumps.register(modBus);
        if (FMLEnvironment.dist.isClient()) {
            BridgeClient.init(modBus, container);
        }
    }

    private static final ResourceLocation INGREDIENTS_TAB = ResourceLocation.withDefaultNamespace("ingredients");
    private static final ResourceLocation TOOLS_TAB = ResourceLocation.withDefaultNamespace("tools_and_utilities");

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(INGREDIENTS_TAB)) {
            event.accept(STELLAR_CORE);
        } else if (event.getTabKey().location().equals(TOOLS_TAB)) {
            BOSS_COMPASSES.forEach(event::accept);
            event.accept(FIELD_GUIDE_PENDANT);
        }
    }
}

package dev.creategalore.fishing;

import com.mojang.serialization.Codec;
import dev.creategalore.fishing.aquarium.AquariumBlock;
import dev.creategalore.fishing.aquarium.AquariumBlockEntity;
import dev.creategalore.fishing.bait.ChumItem;
import dev.creategalore.fishing.bait.LiveBaitBucketItem;
import dev.creategalore.fishing.bait.ThrownChum;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.machine.FuelReelDriveBlock;
import dev.creategalore.fishing.machine.FuelReelDriveBlockEntity;
import dev.creategalore.fishing.machine.ReelDriveBlock;
import dev.creategalore.fishing.machine.ReelDriveBlockEntity;
import dev.creategalore.fishing.machine.RodHolderBlock;
import dev.creategalore.fishing.machine.RodHolderBlockEntity;
import dev.creategalore.fishing.process.CleaningTableBlock;
import dev.creategalore.fishing.process.CleaningTableBlockEntity;
import dev.creategalore.fishing.process.WholeFishIngredient;
import dev.creategalore.fishing.tackle.LineItem;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.TackleBenchBlock;
import dev.creategalore.fishing.tackle.TackleBenchMenu;
import dev.creategalore.fishing.tackle.TackleItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Everything this mod registers. Feature code refers to the holders here. */
public final class FishingRegistry {
    private FishingRegistry() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateGaloreFishing.ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateGaloreFishing.ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(CreateGaloreFishing.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreateGaloreFishing.ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateGaloreFishing.ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CreateGaloreFishing.ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CreateGaloreFishing.ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateGaloreFishing.ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateGaloreFishing.ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreateGaloreFishing.ID);
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, CreateGaloreFishing.ID);

    // ---- Data components ----

    /** The parts fitted to a rod. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RodParts>> ROD_PARTS =
            COMPONENTS.registerComponentType("rod_parts", b -> b.persistent(RodParts.CODEC).networkSynchronized(RodParts.STREAM_CODEC).cacheEncoding());
    /** The drag knob position of the rod's reel, in reel steps. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DRAG_STEP =
            COMPONENTS.registerComponentType("drag_step", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** How deep a float holds the bait, in blocks. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FLOAT_DEPTH =
            COMPONENTS.registerComponentType("float_depth", b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** Metres left on a spool of line. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LINE_METERS =
            COMPONENTS.registerComponentType("line_meters", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** What is known about a caught fish (size class, and for big fish its exact numbers). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CatchInfo>> CATCH =
            COMPONENTS.registerComponentType("catch", b -> b.persistent(CatchInfo.CODEC).networkSynchronized(CatchInfo.STREAM_CODEC));
    /** How frayed a line is, 0 (new) to 1 (about to go): weakens it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> LINE_WEAR =
            COMPONENTS.registerComponentType("line_wear", b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** How worn a rigged leader, hook, float, sinker or lure is, 0 (new) to 1 (worn out): set only once it has fished. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> PART_WEAR =
            COMPONENTS.registerComponentType("part_wear", b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** Game time until which a fish caught by a machine and put on a belt is still alive (dropped in water, it swims off). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> ALIVE_UNTIL =
            COMPONENTS.registerComponentType("alive_until", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    /** Bait on a rod that came out of a Live-Bait Bucket, still alive. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> LIVE =
            COMPONENTS.registerComponentType("live_bait", b -> b.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    // ---- Ingredients ----

    /** A fish light enough to cook or press whole. */
    public static final DeferredHolder<IngredientType<?>, IngredientType<WholeFishIngredient>> WHOLE_FISH =
            INGREDIENT_TYPES.register("whole_fish", () -> WholeFishIngredient.TYPE);

    // ---- Entities ----

    /** The cast rig (float, sinker or lure) and, during a fight, the hooked fish. */
    public static final DeferredHolder<EntityType<?>, EntityType<CastHook>> CAST_HOOK = ENTITIES.register("cast_hook",
            () -> EntityType.Builder.<CastHook>of(CastHook::new, MobCategory.MISC).noSave().noSummon()
                    .sized(0.25f, 0.25f).clientTrackingRange(10).updateInterval(2)
                    .build(CreateGaloreFishing.ID + ":cast_hook"));
    /** A ball of chum in flight. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownChum>> THROWN_CHUM = ENTITIES.register("chum",
            () -> EntityType.Builder.<ThrownChum>of(ThrownChum::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(4).updateInterval(10).build(CreateGaloreFishing.ID + ":chum"));

    // ---- Sounds (sounds.json points them at vanilla clips; resource packs can replace them) ----

    public static final DeferredHolder<SoundEvent, SoundEvent> DRAG_CLICK = sound("reel.drag");
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL_CRANK = sound("reel.crank");
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL_GRIND = sound("reel.grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL_BAIL = sound("reel.bail");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_STRAIN = sound("line.strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_SNAP = sound("line.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_ZIP = sound("line.zip");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_WHIP = sound("line.whip");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_WHOOSH = sound("cast.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_PLOP = sound("cast.plop");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE_TAP = sound("bite.tap");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE_STRIKE = sound("bite.strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCH_FLOP = sound("catch.flop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ALARM = sound("machine.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_MOTOR = sound("machine.motor");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ENGINE = sound("machine.engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_SHIFT = sound("machine.shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_STALL = sound("machine.stall");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CreateGaloreFishing.id(name)));
    }

    // ---- Rods ----

    /** Every rod item, in progression order. */
    public static final List<DeferredItem<RodItem>> RODS = new ArrayList<>();

    public static final DeferredItem<RodItem> BAMBOO_POLE = rod("bamboo_pole", 150, p -> p);
    public static final DeferredItem<RodItem> WOODEN_ROD = rod("wooden_rod", 250, p -> p);
    public static final DeferredItem<RodItem> IRON_ROD = rod("iron_rod", 450, p -> p);
    public static final DeferredItem<RodItem> STEEL_ROD = rod("steel_rod", 800, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<RodItem> BOAT_ROD = rod("boat_rod", 1200, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<RodItem> NETHERITE_ROD = rod("netherite_rod", 2000, p -> p.rarity(Rarity.RARE).fireResistant());

    private static DeferredItem<RodItem> rod(String name, int durability, UnaryOperator<Item.Properties> extra) {
        DeferredItem<RodItem> item = ITEMS.register(name, () -> new RodItem(extra.apply(new Item.Properties().durability(durability))));
        RODS.add(item);
        return item;
    }

    // ---- Parts ----

    /** Every part item, in the order the creative tab shows them. */
    public static final List<DeferredItem<? extends Item>> PARTS = new ArrayList<>();

    public static final DeferredItem<TackleItem> COPPER_REEL = part("copper_reel", () -> new TackleItem(new Item.Properties().durability(300)));
    public static final DeferredItem<TackleItem> IRON_REEL = part("iron_reel", () -> new TackleItem(new Item.Properties().durability(500)));
    public static final DeferredItem<TackleItem> BRASS_REEL = part("brass_reel", () -> new TackleItem(new Item.Properties().durability(900).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TackleItem> STEEL_BAITCASTER = part("steel_baitcaster", () -> new TackleItem(new Item.Properties().durability(900).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TackleItem> TROLLING_REEL = part("trolling_reel", () -> new TackleItem(new Item.Properties().durability(1500).rarity(Rarity.RARE)));

    public static final DeferredItem<LineItem> STRING_LINE = part("string_line", () -> new LineItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<LineItem> MONO_LINE = part("mono_line", () -> new LineItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<LineItem> CLEAR_LINE = part("clear_line", () -> new LineItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<LineItem> BRAIDED_LINE = part("braided_line", () -> new LineItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<LineItem> HEAVY_LINE = part("heavy_line", () -> new LineItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<TackleItem> CLEAR_LEADER = part("clear_leader", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> STEEL_LEADER = part("steel_leader", () -> new TackleItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<TackleItem> SMALL_HOOK = part("small_hook", () -> new TackleItem(new Item.Properties()));
    public static final DeferredItem<TackleItem> MEDIUM_HOOK = part("medium_hook", () -> new TackleItem(new Item.Properties()));
    public static final DeferredItem<TackleItem> LARGE_HOOK = part("large_hook", () -> new TackleItem(new Item.Properties()));
    public static final DeferredItem<TackleItem> BIG_GAME_HOOK = part("big_game_hook", () -> new TackleItem(new Item.Properties()));
    public static final DeferredItem<TackleItem> GOLDEN_HOOK = part("golden_hook", () -> new TackleItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TackleItem> NETHERITE_HOOK = part("netherite_hook", () -> new TackleItem(new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<TackleItem> FLOAT = part("float", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> SINKER = part("sinker", () -> new TackleItem(new Item.Properties()));
    public static final DeferredItem<TackleItem> SPOON = part("spoon", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> SPINNER = part("spinner", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> CRANKBAIT = part("crankbait", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> DEEP_CRANKBAIT = part("deep_crankbait", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> JIG = part("jig", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> POPPER = part("popper", () -> new TackleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<TackleItem> SOFT_WORM = part("soft_worm", () -> new TackleItem(new Item.Properties().stacksTo(16)));

    private static <T extends Item> DeferredItem<T> part(String name, Supplier<T> factory) {
        DeferredItem<T> item = ITEMS.register(name, factory);
        PARTS.add(item);
        return item;
    }

    // ---- Bait and fish processing ----

    /** Bait and processing items, in the order the creative tab shows them. */
    public static final List<DeferredItem<? extends Item>> GOODS = new ArrayList<>();

    public static final DeferredItem<Item> MAGGOTS = good("maggots", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> BOILIES = good("boilies", () -> new Item(new Item.Properties()));
    public static final DeferredItem<ChumItem> CHUM = good("chum", () -> new ChumItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<LiveBaitBucketItem> LIVE_BAIT_BUCKET = good("live_bait_bucket",
            () -> new LiveBaitBucketItem(new Item.Properties().stacksTo(1)
                    .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)));
    public static final DeferredItem<Item> FISH_SCRAPS = good("fish_scraps", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FISH_MEAL = good("fish_meal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FISH_ROE = good("fish_roe", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.4f).build())));
    public static final DeferredItem<Item> SHARK_SKIN = good("shark_skin", () -> new Item(new Item.Properties()));

    private static <T extends Item> DeferredItem<T> good(String name, Supplier<T> factory) {
        DeferredItem<T> item = ITEMS.register(name, factory);
        GOODS.add(item);
        return item;
    }

    // ---- Fish oil ----

    public static final DeferredHolder<FluidType, FluidType> FISH_OIL_TYPE = FLUID_TYPES.register("fish_oil",
            () -> new FluidType(FluidType.Properties.create().descriptionId("fluid_type.create_galore_fishing.fish_oil")
                    .density(920).viscosity(2000).canExtinguish(false).canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FISH_OIL = FLUIDS.register("fish_oil",
            () -> new BaseFlowingFluid.Source(FishOil.PROPERTIES));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_FISH_OIL = FLUIDS.register("flowing_fish_oil",
            () -> new BaseFlowingFluid.Flowing(FishOil.PROPERTIES));
    public static final DeferredBlock<LiquidBlock> FISH_OIL_BLOCK = BLOCKS.register("fish_oil",
            () -> new LiquidBlock(FISH_OIL.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_YELLOW)));
    public static final DeferredItem<BucketItem> FISH_OIL_BUCKET = good("fish_oil_bucket",
            () -> new BucketItem(FISH_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /** Kept apart so the fluid holders above exist before the properties name them. */
    private static final class FishOil {
        static final BaseFlowingFluid.Properties PROPERTIES = new BaseFlowingFluid.Properties(FISH_OIL_TYPE, FISH_OIL,
                FLOWING_FISH_OIL).bucket(FISH_OIL_BUCKET).block(FISH_OIL_BLOCK).slopeFindDistance(2).levelDecreasePerBlock(2);
    }

    // ---- Blocks ----

    public static final DeferredBlock<TackleBenchBlock> TACKLE_BENCH = BLOCKS.register("tackle_bench",
            () -> new TackleBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> TACKLE_BENCH_ITEM = ITEMS.registerSimpleBlockItem(TACKLE_BENCH);

    public static final DeferredBlock<CleaningTableBlock> CLEANING_TABLE = BLOCKS.register("cleaning_table",
            () -> new CleaningTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> CLEANING_TABLE_ITEM = ITEMS.registerSimpleBlockItem(CLEANING_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CleaningTableBlockEntity>> CLEANING_TABLE_ENTITY =
            BLOCK_ENTITIES.register("cleaning_table", () -> BlockEntityType.Builder.of(CleaningTableBlockEntity::new,
                    CLEANING_TABLE.get()).build(null));

    public static final DeferredBlock<RodHolderBlock> ROD_HOLDER = BLOCKS.register("rod_holder",
            () -> new RodHolderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> ROD_HOLDER_ITEM = ITEMS.registerSimpleBlockItem(ROD_HOLDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RodHolderBlockEntity>> ROD_HOLDER_ENTITY =
            BLOCK_ENTITIES.register("rod_holder", () -> BlockEntityType.Builder.of(RodHolderBlockEntity::new,
                    ROD_HOLDER.get()).build(null));

    public static final DeferredBlock<ReelDriveBlock> REEL_DRIVE = BLOCKS.register("reel_drive",
            () -> new ReelDriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f)
                    .sound(SoundType.WOOD).noOcclusion(), false));
    public static final DeferredItem<BlockItem> REEL_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(REEL_DRIVE);
    public static final DeferredBlock<ReelDriveBlock> GEARED_REEL_DRIVE = BLOCKS.register("geared_reel_drive",
            () -> new ReelDriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(2.0f)
                    .sound(SoundType.WOOD).noOcclusion(), true));
    public static final DeferredItem<BlockItem> GEARED_REEL_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(GEARED_REEL_DRIVE);
    /** One block entity type for the Basic and Geared drives; the block says which. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReelDriveBlockEntity>> REEL_DRIVE_ENTITY =
            BLOCK_ENTITIES.register("reel_drive", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ReelDriveBlockEntity(FishingRegistry.REEL_DRIVE_ENTITY.get(), pos, state),
                    REEL_DRIVE.get(), GEARED_REEL_DRIVE.get()).build(null));
    public static final DeferredBlock<FuelReelDriveBlock> FUEL_REEL_DRIVE = BLOCKS.register("fuel_reel_drive",
            () -> new FuelReelDriveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.5f)
                    .sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredItem<BlockItem> FUEL_REEL_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(FUEL_REEL_DRIVE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelReelDriveBlockEntity>> FUEL_REEL_DRIVE_ENTITY =
            BLOCK_ENTITIES.register("fuel_reel_drive", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new FuelReelDriveBlockEntity(FishingRegistry.FUEL_REEL_DRIVE_ENTITY.get(), pos, state),
                    FUEL_REEL_DRIVE.get()).build(null));

    public static final DeferredBlock<AquariumBlock> AQUARIUM_TANK = BLOCKS.register("aquarium_tank",
            () -> new AquariumBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f)
                    .sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredItem<BlockItem> AQUARIUM_TANK_ITEM = ITEMS.registerSimpleBlockItem(AQUARIUM_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AquariumBlockEntity>> AQUARIUM_ENTITY =
            BLOCK_ENTITIES.register("aquarium_tank", () -> BlockEntityType.Builder.of(AquariumBlockEntity::new,
                    AQUARIUM_TANK.get()).build(null));

    // ---- Menus ----

    public static final DeferredHolder<MenuType<?>, MenuType<TackleBenchMenu>> TACKLE_BENCH_MENU =
            MENUS.register("tackle_bench", () -> new MenuType<>(TackleBenchMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // ---- Creative tab ----

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("fishing",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_galore_fishing"))
                    .icon(() -> new ItemStack(IRON_ROD.get()))
                    .displayItems((params, out) -> {
                        out.accept(TACKLE_BENCH_ITEM.get());
                        RODS.forEach(r -> out.accept(r.get()));
                        PARTS.forEach(p -> out.accept(p.get()));
                        out.accept(CLEANING_TABLE_ITEM.get());
                        out.accept(AQUARIUM_TANK_ITEM.get());
                        out.accept(ROD_HOLDER_ITEM.get());
                        out.accept(REEL_DRIVE_ITEM.get());
                        out.accept(GEARED_REEL_DRIVE_ITEM.get());
                        out.accept(FUEL_REEL_DRIVE_ITEM.get());
                        GOODS.forEach(g -> out.accept(g.get()));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        INGREDIENT_TYPES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CLEANING_TABLE_ENTITY.get(), (table, side) -> table.items());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AQUARIUM_ENTITY.get(), (tank, side) -> tank.items());
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AQUARIUM_ENTITY.get(), (tank, side) -> tank.fluids());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REEL_DRIVE_ENTITY.get(),
                    (drive, side) -> drive.drive().kind().geared() ? drive.drive().bait() : null);
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FUEL_REEL_DRIVE_ENTITY.get(), (drive, side) -> drive.items());
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FUEL_REEL_DRIVE_ENTITY.get(), (drive, side) -> drive.tank());
        });
    }
}

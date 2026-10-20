package dev.creategalore.fishing;

import com.mojang.serialization.Codec;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    /** How frayed a line is, 0 (new) to 1 (about to go): weakens it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> LINE_WEAR =
            COMPONENTS.registerComponentType("line_wear", b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

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

    public static final DeferredItem<LineItem> STRING_LINE = part("string_line", () -> new LineItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LineItem> MONO_LINE = part("mono_line", () -> new LineItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LineItem> CLEAR_LINE = part("clear_line", () -> new LineItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LineItem> BRAIDED_LINE = part("braided_line", () -> new LineItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LineItem> HEAVY_LINE = part("heavy_line", () -> new LineItem(new Item.Properties().stacksTo(1)));

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

    // ---- Blocks ----

    public static final DeferredBlock<TackleBenchBlock> TACKLE_BENCH = BLOCKS.register("tackle_bench",
            () -> new TackleBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> TACKLE_BENCH_ITEM = ITEMS.registerSimpleBlockItem(TACKLE_BENCH);

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
                    })
                    .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}

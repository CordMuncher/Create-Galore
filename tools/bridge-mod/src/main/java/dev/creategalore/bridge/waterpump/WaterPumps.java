package dev.creategalore.bridge.waterpump;

import dev.creategalore.bridge.CreateGaloreBridge;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Water pumps that clean polluted water. Set one in water with a Filter Frame on top and give it a redstone signal: it
 * finds the patches of polluted water around it and pulls them across the surface toward itself, one block at a time,
 * and each patch that reaches it comes out clean. The pump's metal sets how far it reaches, the frame's metal how fast
 * it pulls (it filters all the water it draws, clean or not). The frame never needs filter material.
 */
public final class WaterPumps {
    private WaterPumps() {}

    public enum Tier {
        IRON("iron", 25, 4, MapColor.METAL),
        GOLD("gold", 50, 8, MapColor.GOLD),
        DIAMOND("diamond", 100, 16, MapColor.DIAMOND);

        public final String id;
        /** Blocks from the pump, every way (iron reaches a 51 x 51 area around it). */
        public final int reach;
        /** Patches it pulls at the same time. */
        public final int patches;
        final MapColor color;

        Tier(String id, int reach, int patches, MapColor color) {
            this.id = id;
            this.reach = reach;
            this.patches = patches;
            this.color = color;
        }
    }

    /** Blocks a second a patch moves toward the pump, by the Filter Frame on top: iron 1, gold 2, diamond 4. */
    public static int pullSpeed(BlockState frame) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(frame.getBlock());
        if (!"adpother".equals(key.getNamespace())) return 0;
        return switch (key.getPath()) {
            case "iron_filter_frame" -> 1;
            case "gold_filter_frame" -> 2;
            case "diamond_filter_frame" -> 4;
            default -> 0;
        };
    }

    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateGaloreBridge.ID);

    public static final Map<Tier, DeferredBlock<WaterPumpBlock>> PUMPS = new EnumMap<>(Tier.class);
    public static final Map<Tier, DeferredItem<BlockItem>> ITEMS = new EnumMap<>(Tier.class);

    static {
        for (Tier tier : Tier.values()) {
            DeferredBlock<WaterPumpBlock> block = CreateGaloreBridge.BLOCKS.register(tier.id + "_water_pump",
                    () -> new WaterPumpBlock(tier, BlockBehaviour.Properties.of().mapColor(tier.color).strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops().sound(SoundType.COPPER)));
            PUMPS.put(tier, block);
            ITEMS.put(tier, CreateGaloreBridge.ITEMS.registerSimpleBlockItem(block, new Item.Properties()));
        }
    }

    public static final Supplier<BlockEntityType<WaterPumpBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("water_pump",
            () -> BlockEntityType.Builder.of(WaterPumpBlockEntity::new,
                    PUMPS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(WaterPumps::addToTabs);
    }

    private static final ResourceLocation FUNCTIONAL_TAB = ResourceLocation.withDefaultNamespace("functional_blocks");

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(FUNCTIONAL_TAB)) ITEMS.values().forEach(event::accept);
    }
}

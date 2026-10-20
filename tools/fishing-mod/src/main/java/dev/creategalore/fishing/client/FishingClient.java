package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.aquarium.AquariumBlock;
import dev.creategalore.fishing.cast.AliveFish;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.process.WholeFishIngredient;
import dev.creategalore.fishing.tide.loaders.neoforge.NeoforgeClientEntrypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(FishingRegistry.CAST_HOOK.get(), CastHookRenderer::new);
            event.registerEntityRenderer(FishingRegistry.THROWN_CHUM.get(), ThrownItemRenderer::new);
            event.registerBlockEntityRenderer(FishingRegistry.CLEANING_TABLE_ENTITY.get(), CleaningTableRenderer::new);
            event.registerBlockEntityRenderer(FishingRegistry.AQUARIUM_ENTITY.get(), AquariumRenderer::new);
            event.registerBlockEntityRenderer(FishingRegistry.ROD_HOLDER_ENTITY.get(), context -> new MountRenderer<>());
            event.registerBlockEntityRenderer(FishingRegistry.REEL_DRIVE_ENTITY.get(), ReelDriveRenderer::new);
            event.registerBlockEntityRenderer(FishingRegistry.FUEL_REEL_DRIVE_ENTITY.get(), context -> new MountRenderer<>());
        });
        modBus.addListener(FishingClient::fishOil);
        AquariumBlock.clientTick = AquariumSwim::tick;
        modBus.addListener((RegisterColorHandlersEvent.Item event) ->
                event.register(new DynamicFluidContainerModel.Colors(), FishingRegistry.FISH_OIL_BUCKET.get()));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FishingRegistry.FISH_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(FishingRegistry.FLOWING_FISH_OIL.get(), RenderType.translucent());
            ReelDriveRenderer.registerVisual();
        }));
        FishingInput.init(modBus);
        FishingHud.init(modBus);
        RodItemRenderer.init(modBus);
        HoldPose.init(modBus);
        FishItems.init(modBus);
        FishIcons.init(modBus);
        NeoforgeClientEntrypoint.init(modBus);
        if (Boolean.getBoolean("cgfish.debug")) DebugCommands.init();
        CatchScreen.listen();
        FishingSounds.init();
        NeoForge.EVENT_BUS.addListener(FishingClient::catchTooltip);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> MountRenderer.nextFrame());
    }

    /** Fish oil looks like water tinted amber, in the world, in tanks and in its bucket. */
    private static void fishOil(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
            private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xe8c89a30;
            }
        }, FishingRegistry.FISH_OIL_TYPE.get());
    }

    /**
     * Fish items belong to many mods, so the catch line is added by event rather than by the item class. A fish too
     * heavy to cook whole says where to take it.
     */
    private static void catchTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = new ArrayList<>(catchLines(stack));
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        if (info != null && info.kg().orElse(0.0) >= WholeFishIngredient.MAX_KG) {
            lines.add(Component.translatable("tooltip.create_galore_fishing.too_big_to_cook").withStyle(ChatFormatting.DARK_AQUA));
        }
        long alive = Minecraft.getInstance().level == null ? 0 : AliveFish.ticksLeft(stack, Minecraft.getInstance().level);
        if (alive > 0) {
            long seconds = alive / 20;
            lines.add(Component.translatable("tooltip.create_galore_fishing.alive", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60))
                    .withStyle(ChatFormatting.AQUA));
        }
        event.getToolTip().addAll(1, lines);
    }

    /**
     * What a caught fish's item says about it: its size class, and for a big fish or a trophy its weight, length and
     * who caught it when. Empty for a fish that wasn't caught with this mod. Also the label on a Trophy Mount.
     */
    public static List<Component> catchLines(ItemStack stack) {
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        if (info == null) return List.of();
        Component size = Component.translatable("tooltip.create_galore_fishing.size_class." + info.size().key())
                .withStyle(info.size().isTrophy() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        if (!info.exact()) return List.of(size);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.create_galore_fishing.catch", size,
                CatchInfo.weightText(info.kg().orElse(0.0)), String.format(Locale.ROOT, "%.0f", info.cm().orElse(0.0)))
                .withStyle(ChatFormatting.GRAY));
        info.angler().ifPresent(angler -> lines.add(Component.translatable(
                "tooltip.create_galore_fishing.caught_by", angler, info.day().orElse(0L)).withStyle(ChatFormatting.DARK_GRAY)));
        return lines;
    }
}

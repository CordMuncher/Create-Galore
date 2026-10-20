package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(FishingRegistry.CAST_HOOK.get(), CastHookRenderer::new));
        FishingInput.init(modBus);
        FishingHud.init(modBus);
        RodItemRenderer.init(modBus);
        HoldPose.init(modBus);
        if (Boolean.getBoolean("cgfish.debug")) DebugCommands.init();
        CatchScreen.listen();
        FishingSounds.init();
        NeoForge.EVENT_BUS.addListener(FishingClient::catchTooltip);
    }

    /** Fish items belong to many mods, so the catch line is added by event rather than by the item class. */
    private static void catchTooltip(ItemTooltipEvent event) {
        CatchInfo info = event.getItemStack().get(FishingRegistry.CATCH.get());
        if (info == null) return;
        Component size = Component.translatable("tooltip.create_galore_fishing.size_class." + info.size().key())
                .withStyle(info.size().isTrophy() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        if (info.exact()) {
            event.getToolTip().add(1, Component.translatable("tooltip.create_galore_fishing.catch", size,
                    CatchInfo.weightText(info.kg().orElse(0.0)), String.format(Locale.ROOT, "%.0f", info.cm().orElse(0.0)))
                    .withStyle(ChatFormatting.GRAY));
            info.angler().ifPresent(angler -> event.getToolTip().add(2, Component.translatable(
                    "tooltip.create_galore_fishing.caught_by", angler, info.day().orElse(0L)).withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            event.getToolTip().add(1, size);
        }
    }
}

package dev.creategalore.bridge.client;

import dev.creategalore.bridge.guideai.GuideAiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** The book side of the Field Guide's voice: an "Ask the Book" button on the guide and the screen it opens. */
public final class GuideAiClient {
    static final ResourceLocation FIELD_GUIDE = ResourceLocation.fromNamespaceAndPath("create_galore", "field_guide");

    private GuideAiClient() {
    }

    static void init() {
        if (ModList.get().isLoaded("modonomicon")) NeoForge.EVENT_BUS.addListener(GuideAiClient::addButton);
        // /askbook opens the same screen without the guide in hand.
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("askbook").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new AskScreen(null)));
                    return 1;
                })));
    }

    private static void addButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!Modonomicon.isFieldGuide(screen) || Minecraft.getInstance().getConnection() == null) return;
        int w = 90;
        event.addListener(Button.builder(Component.literal("Ask the Book"), b -> Minecraft.getInstance().setScreen(new AskScreen(screen)))
                .bounds(screen.width - w - 6, 6, w, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Ask the Field Guide a question in your own words")))
                .build());
    }

    /** Kept apart so Modonomicon's classes are only loaded when it is installed. */
    private static final class Modonomicon {
        static boolean isFieldGuide(Screen screen) {
            return screen instanceof com.klikli_dev.modonomicon.client.gui.book.BookParentScreen book
                    && book.getBook() != null && FIELD_GUIDE.equals(book.getBook().getId());
        }
    }

    static boolean serverHasVoice() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(GuideAiNet.Request.TYPE);
    }

    static void send(int action, String pageId, String text) {
        if (serverHasVoice()) PacketDistributor.sendToServer(new GuideAiNet.Request(action, pageId, text));
    }

    public static void onPages(GuideAiNet.Pages pages) {
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showPages(pages);
    }

    public static void onLive(GuideAiNet.Live live) {
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showLive(live);
    }

    public static void onView(GuideAiNet.View view) {
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showView(view);
    }
}

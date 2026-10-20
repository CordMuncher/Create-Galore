package dev.creategalore.bridge.client;

import dev.creategalore.bridge.guideai.GuideAiNet;
import net.minecraft.client.Minecraft;
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
        NeoForge.EVENT_BUS.addListener(ClaimBoxView::render);
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
        event.addListener(new AskTab(screen));
    }

    /**
     * A bookmark ribbon on the right edge of the guide, above Modonomicon's bookmarks and search ribbons and drawn
     * the same way (tucked under the frame, nudged out on hover), with a speech bubble on it.
     */
    private static final class AskTab extends net.minecraft.client.gui.components.AbstractButton {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "textures/gui/ask_tab.png");
        private final Screen screen;

        AskTab(Screen screen) {
            // Where BookParentNodeScreen puts its search ribbon (frame is the screen less 30 on each side), two ribbons up.
            super(screen.width - 31, screen.height - 43 - 44, 44, 20, Component.literal("Ask the Book"));
            this.screen = screen;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Ask the Book: ask the Field Guide a question in your own words")));
        }

        @Override
        public void onPress() {
            Minecraft.getInstance().setScreen(new AskScreen(screen));
        }

        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX() + (isHovered() ? 1 : 0), clip = screen.width - 30;
            g.enableScissor(clip, getY(), screen.width, getY() + height);
            g.blit(TEXTURE, x, getY(), 15, 0, 29, 20, 44, 20);
            g.disableScissor();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
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
        if (!serverHasVoice()) return;
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        PacketDistributor.sendToServer(new GuideAiNet.Request(action, pageId, text, mc.options.fov().get(), aspect));
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

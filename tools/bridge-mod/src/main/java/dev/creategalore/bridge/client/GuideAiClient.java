package dev.creategalore.bridge.client;

import dev.creategalore.bridge.guideai.GuideAiNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** The book side of the Field Guide's voice: an "Ask the Book" button on the guide and the screen it opens. */
public final class GuideAiClient {
    static final ResourceLocation FIELD_GUIDE = ResourceLocation.fromNamespaceAndPath("create_galore", "field_guide");

    private GuideAiClient() {
    }

    static void init(IEventBus modBus) {
        if (ModList.get().isLoaded("modonomicon")) {
            NeoForge.EVENT_BUS.addListener(GuideAiClient::addButton);
            modBus.addListener(GuideAiClient::decorate);
        }
        // Another server, another book: what was unread there means nothing here.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> unread.clear());
        NeoForge.EVENT_BUS.addListener(ClaimBoxView::render);
        // /askbook opens the same screen without the guide in hand.
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("askbook").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new AskScreen(null)));
                    return 1;
                })));
    }

    /**
     * While the book has an answer the player hasn't opened, the Field Guide glows green wherever it sits in a slot
     * (hotbar, inventory, chests), with a dot in its corner, until the answer is read.
     */
    private static void decorate(RegisterItemDecorationsEvent event) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("modonomicon", "modonomicon"));
        if (item == Items.AIR) return;
        event.register(item, (g, font, stack, x, y) -> {
            if (unread.isEmpty() || !isFieldGuide(stack)) return false;
            double breath = 0.5 + 0.5 * Math.sin(Util.getMillis() / 450.0);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            // A ring at the slot's edges (a fill over the whole slot would hide the book itself).
            int edge = (int) (0x90 + 0x6F * breath) << 24 | 0x5FD64A, soft = (int) (0x40 + 0x50 * breath) << 24 | 0x4CB43A;
            g.renderOutline(x, y, 16, 16, edge);
            g.renderOutline(x + 1, y + 1, 14, 14, soft);
            // A dot in the top right corner, like a new message.
            g.fill(x + 11, y, x + 16, y + 5, 0xFF1F4A17);
            g.fill(x + 12, y + 1, x + 15, y + 4, 0xFF6BE356);
            g.pose().popPose();
            return false;
        });
    }

    private static final ResourceLocation BOOK_ID = ResourceLocation.fromNamespaceAndPath("modonomicon", "book_id");

    static boolean isFieldGuide(ItemStack stack) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_ID);
        return type != null && FIELD_GUIDE.equals(stack.get(type));
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
            int x = getX() + (isHovered() || !unread.isEmpty() ? 1 : 0), clip = screen.width - 30;
            g.enableScissor(clip, getY(), screen.width, getY() + height);
            g.blit(TEXTURE, x, getY(), 15, 0, 29, 20, 44, 20);
            if (!unread.isEmpty()) {
                // An answer is waiting: the ribbon glows green, breathing.
                double breath = 0.5 + 0.5 * Math.sin(Util.getMillis() / 450.0);
                g.fill(x, getY(), x + 29, getY() + 20, (int) (0x40 + 0x50 * breath) << 24 | 0x4CB43A);
            }
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
        send(action, pageId, text, "");
    }

    static void send(int action, String pageId, String text, String ticket) {
        if (!serverHasVoice()) return;
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        PacketDistributor.sendToServer(new GuideAiNet.Request(action, pageId, text, mc.options.fov().get(), aspect, ticket));
    }

    /** Pages with an answer the player hasn't opened yet, as the server last said plus answers since. */
    static final java.util.Set<String> unread = new java.util.HashSet<>();

    static void syncUnread(java.util.List<GuideAiNet.Summary> pages) {
        unread.clear();
        for (GuideAiNet.Summary p : pages) if (p.unread()) unread.add(p.id());
    }

    /**
     * An answer is in. The page that is open shows it as it comes; anywhere else (another page, the list, the game
     * with the book closed) a notice slides in, and the page glows in the list until it is opened.
     */
    public static void onAnswered(GuideAiNet.Answered answered) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AskScreen ask && ask.isShowing(answered.pageId())) return;
        unread.add(answered.pageId());
        if (mc.screen instanceof AskScreen ask) ask.unreadChanged();
        mc.getToasts().addToast(new AnswerToast(answered.title(), answered.answered()));
        if (AskSettings.sound())
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f));
    }

    /** "The book has answered" and the page's title, under a picture of the guide; gone after a few seconds. */
    private static final class AnswerToast implements net.minecraft.client.gui.components.toasts.Toast {
        private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
        private static final long SHOWN_MS = 7000;
        private final String title;
        private final boolean answered;

        AnswerToast(String title, boolean answered) {
            this.title = title;
            this.answered = answered;
        }

        @Override
        public Visibility render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.components.toasts.ToastComponent toasts, long time) {
            var font = toasts.getMinecraft().font;
            g.blitSprite(BACKGROUND, 0, 0, width(), height());
            g.renderFakeItem(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.fromNamespaceAndPath("modonomicon", "modonomicon_red"))
                    .orElse(net.minecraft.world.item.Items.BOOK)), 8, 8);
            g.drawString(font, answered ? "The book has answered" : "The book couldn't answer", 30, 7, 0xFFFFFF00, false);
            g.drawString(font, font.plainSubstrByWidth(title, width() - 36), 30, 18, 0xFFFFFFFF, false);
            return time >= SHOWN_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    public static void onPages(GuideAiNet.Pages pages) {
        // Also sent on joining, so the Field Guide can glow for answers left unread last time.
        syncUnread(pages.pages());
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showPages(pages);
    }

    public static void onLive(GuideAiNet.Live live) {
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showLive(live);
    }

    public static void onView(GuideAiNet.View view) {
        if (Minecraft.getInstance().screen instanceof AskScreen ask) ask.showView(view);
    }
}

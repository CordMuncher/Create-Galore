package dev.creategalore.bridge.client;

import dev.creategalore.bridge.guideai.GuideAiNet;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Asking the Field Guide, drawn as the guide itself: its dark slate background, its frame and a sheet of its paper.
 * Two views: the player's pages (each a separate conversation, newest first) and one open page, laid out like a chat
 * with the player's questions on the right and the book's answers on the left. While the book answers, its words are
 * typed onto the page with a soft scratch of the quill, a little cog turns, and a line says what the book is up to.
 * Answers keep their shape: headings, bullets, bold words, item icons and drawings. Everything shown comes from the
 * server.
 */
final class AskScreen extends Screen {
    private static final int TIMEOUT_MS = 4000;
    // The guide's colours: its paper, ink and headings, and the browns of its frame.
    private static final int PAPER = 0xFFF9EED2, PAPER_SHADE = 0xFFEFE0BC, INK = 0xFF2B2014, FADED = 0xFF7A6A50,
            HEAD = 0xFF6B2E12, RULE = 0xFFD6C39A, WARN = 0xFF8A2A1A, YOU_FILL = 0xFFEADAB2, YOU_EDGE = 0xFF9C8058,
            BUTTON = 0xFFEFE2C2, BUTTON_HOVER = 0xFFFFF7E2, BUTTON_EDGE = 0xFF5A4630, ROW_HOVER = 0xFFF1E4C4;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("modonomicon", "textures/gui/book_frame.png");
    private static final ResourceLocation SLATE = ResourceLocation.fromNamespaceAndPath("modonomicon", "textures/gui/dark_slate_seamless.png");
    private static final int LINE = 10;
    private static final Pattern DIAGRAM_MARK = Pattern.compile("^\\s*\\[diagram (\\d+)]\\s*$");
    private static final Pattern RECIPE_MARK = Pattern.compile("^\\s*\\[recipe ([a-z0-9_.-]+:[a-z0-9_./-]+)]\\s*$");
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,4}\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*•]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d{1,2})[.)]\\s+(.*)$");
    private static final Pattern LEADING_ITEM = Pattern.compile("^\\{([a-z0-9_.-]+:[a-z0-9_./-]+)}\\s*(.*)$");
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|\\{([a-z0-9_.-]+:[a-z0-9_./-]+)}|`([^`]+)`");
    private static final Random RANDOM = new Random();

    /** What the book says it is doing, by what it is doing. Picked at random and changed every few seconds. */
    private static final Map<String, List<String>> FLAVOR = Map.ofEntries(
            Map.entry("thinking", List.of("Mining for an answer", "Smelting your question down", "Asking a villager (he said hmm)",
                    "Spinning up the water wheel", "Checking the stress units", "Feeding the Blaze Burner",
                    "Untangling the redstone", "Reading by torchlight", "Greasing the bearings", "Counting cobblestone",
                    "Consulting the elder pages", "Waiting for the gears to mesh", "Brewing a thought potion",
                    "Shaking the Wandering Trader for gossip", "Letting the mixer stir")),
            Map.entry("writing", List.of("Inking the page", "Scribbling in the margins", "Dipping the quill", "Writing it down neatly")),
            Map.entry("look_at", List.of("Squinting at what you're looking at", "Holding up the Engineer's Goggles")),
            Map.entry("get_inventory", List.of("Rummaging through your pockets", "Peeking in your backpack")),
            Map.entry("search_storage", List.of("Opening your chests (politely)", "Counting what's in your barrels",
                    "Peering into the vault")),
            Map.entry("get_surroundings", List.of("Sniffing the weather", "Licking a finger to test the wind", "Checking the season")),
            Map.entry("get_advancements", List.of("Leafing through your achievements", "Checking how far you've come")),
            Map.entry("lookup_recipes", List.of("Flipping to the recipe pages", "Following the crafting trail")),
            Map.entry("find_items", List.of("Thumbing through the item index", "Checking every chest in the library")),
            Map.entry("draw_diagram", List.of("Sketching a little diagram", "Sharpening the charcoal", "Drawing it out")),
            Map.entry("web_search", List.of("Asking the wiki librarians", "Sending a parrot to the wiki")),
            Map.entry("web_fetch", List.of("Reading a page from the wiki", "Unrolling a wiki scroll")));

    private final Screen parent;
    /** null while the page list shows; "" for a new page that has no id yet. */
    private String pageId;
    private String title = "";
    private List<GuideAiNet.Entry> entries = List.of();
    private List<GuideAiNet.Summary> pages = List.of();
    /** The open page's kind: a conversation, or a page keeping a saved drawing (no question line there). */
    private int kind = GuideAiNet.KIND_CHAT;
    private boolean pending, enabled = true, heard;
    private String notice = "";
    private String draft = "", lastQuestion = "";
    private int listOffset, scroll;
    private boolean stickToBottom = true;
    private final long opened = Util.getMillis();
    private EditBox input;
    private BookButton askButton;

    // The answer being written: what has arrived, how much of it is typed out, and the drawings made so far.
    private final StringBuilder liveText = new StringBuilder();
    private final List<String> liveDiagrams = new ArrayList<>();
    private String status = "thinking", flavor = "";
    private long flavorSince, lastTick, lastFrame;
    private double typed;
    /** After the answer is in: the last answer keeps typing from where it was instead of appearing all at once. */
    private int typingEntry = -1;

    /** Laid-out page, rebuilt when the page or the width changes. */
    private Layout laidOut;
    private int laidOutWidth;
    private final Map<String, DiagramPainter.Drawing> drawings = new HashMap<>();
    private final Map<String, RecipeView> recipes = new HashMap<>();
    private final Map<String, ModelView> models = new HashMap<>();
    /** Drawings and models on screen this frame, for right-click saving, dragging and zooming. */
    private final List<Hot> hots = new ArrayList<>();
    private ModelView dragging;

    private record Hot(int x, int y, int w, int h, String json, int entry, ModelView model) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // Where things go, worked out from the window size: the frame, the paper inside it and the text area on it.
    private int frameX, frameY, frameW, frameH, paperX, paperY, paperW, paperH, textX, textW, contentTop, contentBottom;

    AskScreen(Screen parent) {
        super(Component.literal("Ask the Book"));
        this.parent = parent;
    }

    private void geometry() {
        frameW = Math.min(width - 16, 440);
        frameH = height - 12;
        frameX = (width - frameW) / 2;
        frameY = (height - frameH) / 2;
        paperX = frameX + 7;
        paperY = frameY + 7;
        paperW = frameW - 14;
        paperH = frameH - 14;
        textX = paperX + 10;
        textW = paperW - 20;
        contentTop = paperY + 26;
        // Below the text: the question line (on an open page) and the row of buttons.
        contentBottom = paperY + paperH - (pageId == null || kind != GuideAiNet.KIND_CHAT ? 26 : 46);
    }

    @Override
    protected void init() {
        geometry();
        if (!heard && pages.isEmpty() && pageId == null) GuideAiClient.send(GuideAiNet.LIST, "", "");
        int barY = paperY + paperH - 22;
        if (pageId == null) {
            BookButton newPage = addRenderableWidget(new BookButton(textX, barY, 70, 16, Component.literal("New page"), this::openNew));
            newPage.active = enabled && GuideAiClient.serverHasVoice();
            Component back = Component.literal(parent == null ? "Close" : "Back to the guide");
            int bw = font.width(back) + 16;
            addRenderableWidget(new BookButton(textX + textW - bw, barY, bw, 16, back, this::onClose));
            int rows = listRows();
            for (int i = 0; i < rows && listOffset + i < pages.size(); i++) {
                GuideAiNet.Summary p = pages.get(listOffset + i);
                int y = contentTop + 16 + i * 24;
                addRenderableWidget(new PageRow(textX, y, textW - 18, 22, p));
                BookButton tear = addRenderableWidget(new BookButton(textX + textW - 14, y + 5, 12, 12, Component.literal("x"),
                        () -> GuideAiClient.send(GuideAiNet.DELETE, p.id(), "")));
                tear.setTooltip(Tooltip.create(Component.literal("Tear out this page")));
            }
        } else if (kind != GuideAiNet.KIND_CHAT) {
            input = null;
            askButton = null;
            addRenderableWidget(new BookButton(textX, barY, 66, 16, Component.literal("All pages"), this::backToList));
            addRenderableWidget(new BookButton(textX + 70, barY, 66, 16, Component.literal("New page"), this::openNew));
        } else {
            int inputY = paperY + paperH - 42;
            input = new EditBox(font, textX + 4, inputY + 4, textW - 48, 10, Component.literal("Your question"));
            input.setBordered(false);
            input.setMaxLength(500);
            input.setTextColor(0xFFFFFFFF);
            input.setHint(Component.literal("Ask the book about the pack...").withStyle(ChatFormatting.GRAY));
            input.setValue(draft);
            input.setResponder(s -> {
                draft = s;
                updateAsk();
            });
            addRenderableWidget(input);
            setInitialFocus(input);
            askButton = addRenderableWidget(new BookButton(textX + textW - 38, inputY + 1, 38, 16, Component.literal("Ask"), this::ask));
            addRenderableWidget(new BookButton(textX, barY, 66, 16, Component.literal("All pages"), this::backToList));
            addRenderableWidget(new BookButton(textX + 70, barY, 66, 16, Component.literal("New page"), this::openNew));
            BookButton sound = addRenderableWidget(new BookButton(textX + 140, barY, 70, 16, soundLabel(), null));
            sound.action = () -> {
                AskSettings.setSound(!AskSettings.sound());
                sound.setMessage(soundLabel());
            };
            sound.setTooltip(Tooltip.create(Component.literal("The quill's scratching while the book writes")));
            updateAsk();
        }
    }

    private static Component soundLabel() {
        return Component.literal(AskSettings.sound() ? "Sound: on" : "Sound: off");
    }

    private int listRows() {
        return Math.max(1, (contentBottom - contentTop - 16) / 24);
    }

    private void updateAsk() {
        if (askButton != null) askButton.active = enabled && !pending && !draft.isBlank();
    }

    private void clearLive() {
        liveText.setLength(0);
        liveDiagrams.clear();
        typed = 0;
        typingEntry = -1;
        status = "thinking";
        flavor = "";
    }

    private void openNew() {
        pageId = "";
        kind = GuideAiNet.KIND_CHAT;
        title = "New page";
        entries = List.of();
        pending = false;
        notice = "";
        stickToBottom = true;
        clearLive();
        laidOut = null;
        rebuildWidgets();
    }

    private void open(String id, int pageKind) {
        pageId = id;
        kind = pageKind;
        title = "";
        entries = List.of();
        notice = "";
        stickToBottom = true;
        clearLive();
        laidOut = null;
        GuideAiClient.send(GuideAiNet.OPEN, id, "");
        rebuildWidgets();
    }

    private void backToList() {
        pageId = null;
        notice = "";
        GuideAiClient.send(GuideAiNet.LIST, "", "");
        rebuildWidgets();
    }

    private void ask() {
        if (!enabled || pending || draft.isBlank()) return;
        lastQuestion = draft;
        GuideAiClient.send(GuideAiNet.ASK, pageId == null ? "" : pageId, draft.strip());
        pending = true;
        notice = "";
        clearLive();
        laidOut = null;
        stickToBottom = true;
        rebuildWidgets();
    }

    void showPages(GuideAiNet.Pages update) {
        heard = true;
        enabled = update.enabled();
        pages = update.pages();
        notice = update.notice();
        listOffset = Math.max(0, Math.min(listOffset, pages.size() - listRows()));
        laidOut = null;
        if (!enabled) pageId = null;
        // A page that was torn out elsewhere goes back to the list; a new page keeps its notice (a refused question).
        if (pageId != null && !pageId.isEmpty() && pages.stream().noneMatch(p -> p.id().equals(pageId))) pageId = null;
        pending = false;
        rebuildWidgets();
    }

    void showView(GuideAiNet.View view) {
        heard = true;
        // A page that just got its id (the first question on a new page) or the page already open.
        if (pageId == null || !(pageId.isEmpty() || pageId.equals(view.pageId()))) return;
        pageId = view.pageId();
        title = view.title();
        boolean kindChanged = kind != view.kind();
        kind = view.kind();
        if (kindChanged) geometry();
        boolean finished = pending && !view.pending();
        entries = view.entries();
        // The question is on the page now, so the line it was typed on can clear; a refused one stays to retry.
        if (view.pending() && draft.equals(lastQuestion)) draft = "";
        if (finished) {
            // Keep typing the answer that just arrived from where the live text had got to.
            int last = -1;
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).who() == GuideAiNet.WHO_BOOK) {
                    last = i;
                    break;
                }
                if (entries.get(i).who() == GuideAiNet.WHO_PLAYER) break;
            }
            typingEntry = last;
            if (last >= 0 && !entries.get(last).text().startsWith(liveText.substring(0, (int) Math.min(typed, liveText.length()))))
                typed = 0;
            if (last < 0) typed = 0;
            liveText.setLength(0);
            liveDiagrams.clear();
            if (last >= 0) pageTurn();
        }
        pending = view.pending();
        notice = view.notice();
        laidOut = null;
        rebuildWidgets();
    }

    void showLive(GuideAiNet.Live live) {
        if (pageId == null || !pending || !(pageId.isEmpty() || pageId.equals(live.pageId()))) return;
        if (live.reset()) {
            liveText.setLength(0);
            typed = 0;
        }
        if (liveText.isEmpty() && !live.text().isEmpty() && typed == 0) pageTurn();
        liveText.append(live.text());
        if (!live.diagram().isEmpty()) liveDiagrams.add(live.diagram());
        if (!live.status().equals(status)) {
            status = live.status();
            flavor = "";
        }
    }

    // ---------- sound ----------

    private void pageTurn() {
        if (minecraft != null && AskSettings.sound())
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9f + RANDOM.nextFloat() * 0.2f, 0.35f));
    }

    /** A soft quill tick for each few words typed; quiet, varied in pitch, and never faster than about 12 a second. */
    private void quillTick() {
        long now = Util.getMillis();
        if (minecraft == null || !AskSettings.sound() || now - lastTick < 85) return;
        lastTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.7f + RANDOM.nextFloat() * 0.4f, 0.06f));
    }

    // ---------- input ----------

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (input != null && pageId != null && input.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            ask();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private Hot hotAt(double mouseX, double mouseY) {
        if (pageId == null || mouseY < contentTop || mouseY > contentBottom) return null;
        for (Hot h : hots) if (h.contains(mouseX, mouseY)) return h;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Hot hot = hotAt(mouseX, mouseY);
        if (hot != null && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && canSave(hot.entry)) {
            GuideAiClient.send(GuideAiNet.SAVE, pageId, GuideAiNet.drawingKey(hot.json));
            pageTurn();
            return true;
        }
        if (hot != null && hot.model != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = hot.model;
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // A button that switched views was replaced, but the screen still focuses it: typing went nowhere and Space
        // pressed it again. Focus the question line instead.
        if (getFocused() != null && !children().contains(getFocused())) setFocused(pageId != null ? input : null);
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.turn(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Hot hot = hotAt(mouseX, mouseY);
        if (hot != null && hot.model != null) {
            hot.model.zoom(scrollY);
            return true;
        }
        if (pageId == null) {
            int max = Math.max(0, pages.size() - listRows());
            int next = Math.max(0, Math.min(max, listOffset - (int) Math.signum(scrollY)));
            if (next != listOffset) {
                listOffset = next;
                rebuildWidgets();
            }
        } else {
            scroll = Math.max(0, scroll - (int) (scrollY * 3 * LINE));
            stickToBottom = false;
        }
        return true;
    }

    // ---------- the book around it all ----------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The guide's slate, tiled, then its frame around a sheet of its paper.
        g.blit(SLATE, 0, 0, 0, 0f, 0f, width, height, 512, 512);
        g.fill(0, 0, width, height, 0x40000000);
        // The paper runs under the frame's edges so no gap shows between them.
        g.fill(frameX + 3, frameY + 3, frameX + frameW - 3, frameY + frameH - 3, PAPER);
        g.fill(paperX, paperY, paperX + paperW, paperY + 2, PAPER_SHADE);
        g.fill(paperX, paperY + paperH - 2, paperX + paperW, paperY + paperH, PAPER_SHADE);
        g.fill(paperX, paperY, paperX + 2, paperY + paperH, PAPER_SHADE);
        g.fill(paperX + paperW - 2, paperY, paperX + paperW, paperY + paperH, PAPER_SHADE);
        nineSlice(g, FRAME, frameX, frameY, frameW, frameH, 140, 50);
        // The heading, the way the guide titles its pages: dark ink over a rule.
        String heading = pageId == null ? "Ask the Book" : (title.isEmpty() ? "..." : title);
        heading = font.plainSubstrByWidth(heading, textW);
        Component h = Component.literal(heading).withStyle(ChatFormatting.BOLD);
        g.drawString(font, h, paperX + (paperW - font.width(h)) / 2, paperY + 9, INK, false);
        g.fill(textX + 10, paperY + 20, textX + textW - 10, paperY + 21, RULE);
        g.fill(textX + 10, paperY + 21, textX + textW - 10, paperY + 22, PAPER_SHADE);
        if (pageId != null && kind == GuideAiNet.KIND_CHAT) {
            // The question line: a strip of the frame's dark leather to write on.
            int y = paperY + paperH - 42;
            g.fill(textX - 1, y, textX + textW - 42, y + 18, BUTTON_EDGE);
            g.fill(textX, y + 1, textX + textW - 43, y + 17, 0xFF3B2E22);
        }
    }

    /** A 9-slice of a square frame texture: corners as drawn, edges and middle stretched. */
    private static void nineSlice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int size, int border) {
        int b = Math.min(border, Math.min(w, h) / 2), mid = size - 2 * border;
        int iw = w - 2 * b, ih = h - 2 * b;
        g.blit(tex, x, y, b, b, 0, 0, b, b, 256, 256);
        g.blit(tex, x + w - b, y, b, b, size - b, 0, b, b, 256, 256);
        g.blit(tex, x, y + h - b, b, b, 0, size - b, b, b, 256, 256);
        g.blit(tex, x + w - b, y + h - b, b, b, size - b, size - b, b, b, 256, 256);
        if (iw > 0) {
            g.blit(tex, x + b, y, iw, b, border, 0, mid, b, 256, 256);
            g.blit(tex, x + b, y + h - b, iw, b, border, size - b, mid, b, 256, 256);
        }
        if (ih > 0) {
            g.blit(tex, x, y + b, b, ih, 0, border, b, mid, 256, 256);
            g.blit(tex, x + w - b, y + b, b, ih, size - b, border, b, mid, 256, 256);
        }
    }

    // ---------- drawing the page ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        boolean noServer = !heard && Util.getMillis() - opened > TIMEOUT_MS;
        if (!GuideAiClient.serverHasVoice() || noServer) {
            centered(g, contentTop + 10, "This server can't give the book a voice (its Create Galore Bridge is missing or older "
                    + "than yours). The written guide works as always.", FADED);
            return;
        }
        if (pageId == null) {
            if (!notice.isEmpty()) centered(g, contentTop + 2, notice, enabled ? WARN : FADED);
            else if (heard && pages.isEmpty() && enabled)
                centered(g, contentTop + 20, "No pages yet. Start a new page and ask the book anything about the pack: how "
                        + "something is made, what to do next, or why a machine won't run.", FADED);
            else g.drawString(font, "Your pages", textX, contentTop + 2, FADED, false);
            return;
        }
        advanceTyping();
        if (laidOut == null || laidOutWidth != textW || typingEntry >= 0) {
            laidOut = layout(textW, false);
            laidOutWidth = textW;
        }
        Layout page = pending ? layoutWithLive(textW) : laidOut;
        int view = contentBottom - contentTop, maxScroll = Math.max(0, page.height - view);
        if (stickToBottom || scroll > maxScroll) scroll = maxScroll;
        if (scroll >= maxScroll) stickToBottom = true;
        hots.clear();
        g.enableScissor(paperX, contentTop, paperX + paperW, contentBottom);
        ItemStack hovered = page.paint(g, textX, contentTop - scroll, mouseX, mouseY, contentTop, contentBottom);
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, view * view / page.height), barY = contentTop + (view - barH) * scroll / maxScroll;
            g.fill(paperX + paperW - 5, barY, paperX + paperW - 3, barY + barH, RULE);
        }
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void centered(GuiGraphics g, int y, String text, int color) {
        for (FormattedCharSequence s : font.split(Component.literal(text), textW - 20)) {
            g.drawString(font, s, paperX + (paperW - font.width(s)) / 2, y, color, false);
            y += LINE;
        }
    }

    /** Types the answer out at a steady pace that speeds up when a lot is waiting, ticking the quill as it goes. */
    private void advanceTyping() {
        long now = Util.getMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        String target = typingTarget();
        if (target == null) return;
        double backlog = target.length() - typed;
        if (backlog <= 0) {
            if (!pending && typingEntry >= 0) {
                typingEntry = -1;
                laidOut = null;
            }
            return;
        }
        int before = (int) typed;
        typed = Math.min(target.length(), typed + dt * (45 + backlog * 2.5));
        for (int i = before; i < (int) typed; i++) {
            if (target.charAt(i) == ' ' || target.charAt(i) == '\n') {
                quillTick();
                break;
            }
        }
    }

    private String typingTarget() {
        if (pending) return liveText.toString();
        if (typingEntry >= 0 && typingEntry < entries.size()) return entries.get(typingEntry).text();
        return null;
    }

    // ---------- layout ----------

    /** One thing on the page at a fixed spot (relative to the top of the conversation). */
    private sealed interface Piece permits Text, Icon, Box, Face, Drawn, Spinner, Shown, Model {
    }

    private record Text(int x, int y, FormattedCharSequence text, int color) implements Piece {
    }

    private record Icon(int x, int y, ItemStack stack) implements Piece {
    }

    private record Box(int x, int y, int w, int h, int fill, int edge) implements Piece {
    }

    private record Face(int x, int y) implements Piece {
    }

    /** A drawing; entry is its place in the page's entries, or -1 while the answer is still being written. */
    private record Drawn(int x, int y, int w, DiagramPainter.Drawing drawing, String json, int entry) implements Piece {
    }

    private record Model(int x, int y, int w, ModelView view, String json, int entry) implements Piece {
    }

    private record Spinner(int x, int y) implements Piece {
    }

    private record Shown(int x, int y, int w, RecipeView recipe) implements Piece {
    }

    private final class Layout {
        final List<Piece> pieces = new ArrayList<>();
        int height;

        /** Paints everything at the given origin; returns the item under the mouse, if any. */
        ItemStack paint(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, int clipTop, int clipBottom) {
            ItemStack hovered = ItemStack.EMPTY;
            int scale = DiagramPainter.scale(textW - 24);
            boolean mouseIn = mouseY >= clipTop && mouseY <= clipBottom;
            for (Piece p : pieces) {
                switch (p) {
                    case Box b -> {
                        if (oy + b.y > clipBottom || oy + b.y + b.h < clipTop) continue;
                        plate(g, ox + b.x, oy + b.y, b.w, b.h, b.fill, b.edge);
                    }
                    case Text t -> {
                        if (oy + t.y > clipBottom || oy + t.y + LINE < clipTop) continue;
                        g.drawString(font, t.text, ox + t.x, oy + t.y, t.color, false);
                    }
                    case Icon i -> {
                        int x = ox + i.x, y = oy + i.y;
                        if (y > clipBottom || y + 12 < clipTop) continue;
                        g.pose().pushPose();
                        g.pose().translate(x, y, 0);
                        g.pose().scale(0.75f, 0.75f, 1f);
                        g.renderItem(i.stack, 0, 0);
                        g.pose().popPose();
                        if (mouseIn && mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12) hovered = i.stack;
                    }
                    case Face f -> {
                        if (minecraft != null && minecraft.player != null)
                            PlayerFaceRenderer.draw(g, minecraft.player.getSkin(), ox + f.x, oy + f.y, 8);
                    }
                    case Drawn d -> {
                        int dh = d.drawing.screenHeight(font, d.w, scale);
                        if (oy + d.y > clipBottom || oy + d.y + dh < clipTop) continue;
                        ItemStack h = d.drawing.paint(g, font, ox + d.x, oy + d.y, d.w, scale, mouseX, mouseY);
                        if (!h.isEmpty() && mouseIn) hovered = h;
                        Hot hot = new Hot(ox + d.x, oy + d.y, d.w, dh, d.json, d.entry, null);
                        hots.add(hot);
                        if (mouseIn && hot.contains(mouseX, mouseY) && canSave(d.entry)) saveHint(g, hot);
                    }
                    case Model m -> {
                        int mh = m.view.screenHeight(font, m.w);
                        if (oy + m.y > clipBottom || oy + m.y + mh < clipTop) continue;
                        Hot hot = new Hot(ox + m.x, oy + m.y, m.w, mh, m.json, m.entry, m.view);
                        boolean over = mouseIn && hot.contains(mouseX, mouseY);
                        m.view.paint(g, font, ox + m.x, oy + m.y, m.w, over || dragging == m.view
                                ? (canSave(m.entry) ? "Drag to turn, scroll to zoom, right-click to save" : "Drag to turn, scroll to zoom")
                                : null);
                        hots.add(hot);
                    }
                    case Spinner s -> drawSpinner(g, ox + s.x, oy + s.y);
                    case Shown r -> {
                        ItemStack h = r.recipe.paint(g, font, ox + r.x, oy + r.y, r.w, mouseX, mouseY);
                        if (!h.isEmpty() && mouseIn) hovered = h;
                    }
                }
            }
            return hovered;
        }
    }

    private boolean canSave(int entry) {
        return entry >= 0 && kind == GuideAiNet.KIND_CHAT && pageId != null && !pageId.isEmpty();
    }

    /** "Right-click to save" in the drawing's bottom corner while the mouse is over it. */
    private void saveHint(GuiGraphics g, Hot hot) {
        String hint = "Right-click to save as a page";
        int hw = font.width(hint) + 6, x = hot.x + hot.w - hw - 4, y = hot.y + hot.h - 13;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + hw, y + 10, 0xEEF9EED2);
        g.drawString(font, hint, x + 3, y + 1, FADED, false);
        g.pose().popPose();
    }

    /** A box with its corners cut, the way the guide draws its little plates. */
    private static void plate(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x + 1, y, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + w, y + h - 1, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private Layout layoutWithLive(int w) {
        Layout page = layout(w, true);
        String shown = liveText.substring(0, (int) Math.min(typed, liveText.length()));
        int y = page.height;
        if (!shown.isEmpty() || !liveDiagrams.isEmpty()) y = addBook(page, y, shown, liveDiagrams, List.of(), w, true);
        page.pieces.add(new Spinner(0, y));
        page.height = y + 18;
        return page;
    }

    private Layout layout(int w, boolean live) {
        Layout page = new Layout();
        int y = 2;
        boolean afterAnswer = false;
        for (int i = 0; i < entries.size(); i++) {
            GuideAiNet.Entry e = entries.get(i);
            if (e.who() == GuideAiNet.WHO_DIAGRAM) {
                // Drawn with the answer before it; on a saved page it stands on its own.
                if (!afterAnswer) y = addDrawing(page, y + 2, e.text(), 0, w - 12, i) + 8;
                continue;
            }
            afterAnswer = e.who() == GuideAiNet.WHO_BOOK;
            switch (e.who()) {
                case GuideAiNet.WHO_PLAYER -> y = addPlayer(page, y, e.text(), w);
                case GuideAiNet.WHO_BOOK -> {
                    List<String> diagrams = new ArrayList<>();
                    List<Integer> at = new ArrayList<>();
                    for (int j = i + 1; j < entries.size() && entries.get(j).who() == GuideAiNet.WHO_DIAGRAM; j++) {
                        diagrams.add(entries.get(j).text());
                        at.add(j);
                    }
                    String text = e.text();
                    boolean typing = i == typingEntry;
                    if (typing) text = text.substring(0, (int) Math.min(typed, text.length()));
                    y = addBook(page, y, text, diagrams, at, w, typing);
                }
                default -> y = addNote(page, y, e.text(), w, FADED);
            }
        }
        if (entries.isEmpty() && !pending && !live)
            y = addNote(page, y + 6, "Ask anything about the pack: how something is made, what to do next, why a machine won't "
                    + "run, or what you're looking at. The book sees what you see and carry and the chests you've opened nearby, "
                    + "never the map, and draws a diagram or builds a little 3D model when a picture helps.", w, FADED);
        if (!notice.isEmpty()) y = addNote(page, y, notice, w, notice.startsWith("Saved as") ? FADED : WARN);
        page.height = y;
        return page;
    }

    /** The player's question: a tan note on the right with their face beside it. */
    private int addPlayer(Layout page, int y, String text, int w) {
        int maxW = Math.max(60, w * 7 / 10);
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxW - 12);
        int inner = 0;
        for (FormattedCharSequence l : lines) inner = Math.max(inner, font.width(l));
        int bw = inner + 12, bh = lines.size() * LINE + 7, right = w - 12, x = right - bw;
        page.pieces.add(new Box(x, y, bw, bh, YOU_FILL, YOU_EDGE));
        for (int i = 0; i < lines.size(); i++) page.pieces.add(new Text(x + 6, y + 4 + i * LINE, lines.get(i), INK));
        page.pieces.add(new Face(w - 8, y + bh - 9));
        return y + bh + 10;
    }

    private int addNote(Layout page, int y, String text, int w, int color) {
        for (FormattedCharSequence s : font.split(Component.literal(text).withStyle(ChatFormatting.ITALIC), w - 30)) {
            page.pieces.add(new Text((w - font.width(s)) / 2, y, s, color));
            y += LINE;
        }
        return y + 8;
    }

    private static ItemStack fieldGuide() {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("modonomicon", "modonomicon_red"))
                .orElse(Items.BOOK));
    }

    /**
     * The book's answer on the left, under a small picture of the guide, in the shape it was written: headings,
     * bullets (with item icons where the book named an item), bold words and drawings. Each [diagram N] line becomes
     * drawing N; drawings it never placed go at the end.
     */
    private int addBook(Layout page, int y, String text, List<String> diagrams, List<Integer> at, int w, boolean partial) {
        page.pieces.add(new Icon(0, y, fieldGuide()));
        int x = 16, aw = w - 16 - 12;
        if (partial) text = hideUnfinished(text);
        boolean[] placed = new boolean[diagrams.size()];
        String[] lines = text.strip().split("\n", -1);
        boolean first = true, gap = false;
        int top = y;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (line.isBlank()) {
                gap = true;
                continue;
            }
            if (!first && gap) y += 5;
            gap = false;
            Matcher m = DIAGRAM_MARK.matcher(line);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1)) - 1;
                if (n >= 0 && n < diagrams.size()) {
                    y = addDrawing(page, y + 2, diagrams.get(n), x, aw, n < at.size() ? at.get(n) : -1) + 4;
                    placed[n] = true;
                    first = false;
                }
                continue;
            }
            Matcher rm = RECIPE_MARK.matcher(line);
            if (rm.matches()) {
                RecipeView r = recipes.computeIfAbsent(rm.group(1), RecipeView::of);
                page.pieces.add(new Shown(x, y, aw, r));
                y += r.height(font) + 2;
                first = false;
                continue;
            }
            // While typing, a half-typed "[diagram" line stays hidden until it is complete.
            if (partial && li == lines.length - 1 && line.stripLeading().startsWith("[")) continue;
            Matcher hd = HEADING.matcher(line);
            Matcher bl = BULLET.matcher(line);
            Matcher nm = NUMBERED.matcher(line);
            if (hd.matches()) {
                if (!first) y += 4;
                MutableComponent c = rich(hd.group(1).replace("**", "")).withStyle(s -> s.withBold(true));
                for (FormattedCharSequence s : font.split(c, aw)) {
                    page.pieces.add(new Text(x, y, s, HEAD));
                    y += LINE;
                }
                page.pieces.add(new Box(x, y, Math.min(aw, 120), 1, RULE, RULE));
                y += 4;
            } else if (bl.matches() || nm.matches()) {
                int level = Math.min(2, (bl.matches() ? bl.group(1) : nm.group(1)).replace("\t", "  ").length() / 2);
                String body = bl.matches() ? bl.group(2) : nm.group(3);
                int bx = x + level * 10;
                Matcher item = LEADING_ITEM.matcher(body);
                ItemStack icon = item.matches() ? stackOf(item.group(1)) : ItemStack.EMPTY;
                int tx;
                if (!icon.isEmpty()) {
                    page.pieces.add(new Icon(bx, y - 2, icon));
                    // The icon's name leads the line, unless the book already wrote it there.
                    String name = itemName(item.group(1)), rest = item.group(2);
                    body = rest.toLowerCase(java.util.Locale.ROOT).replace("**", "").startsWith(name.toLowerCase(java.util.Locale.ROOT))
                            ? rest : name + (rest.isEmpty() ? "" : " " + rest);
                    tx = bx + 14;
                } else if (bl.matches()) {
                    page.pieces.add(new Text(bx + 2, y, Component.literal("•").getVisualOrderText(), FADED));
                    tx = bx + 9;
                } else {
                    String label = nm.group(2) + ".";
                    page.pieces.add(new Text(bx, y, Component.literal(label).getVisualOrderText(), FADED));
                    tx = bx + Math.max(9, font.width(label) + 3);
                }
                for (FormattedCharSequence s : font.split(rich(body), x + aw - tx)) {
                    page.pieces.add(new Text(tx, y, s, INK));
                    y += LINE;
                }
                y += 1;
            } else {
                for (FormattedCharSequence s : font.split(rich(line.strip()), aw)) {
                    page.pieces.add(new Text(x, y, s, INK));
                    y += LINE;
                }
            }
            first = false;
        }
        if (!partial)
            for (int i = 0; i < diagrams.size(); i++)
                if (!placed[i]) y = addDrawing(page, y + 2, diagrams.get(i), x, aw, i < at.size() ? at.get(i) : -1) + 4;
        return Math.max(y, top + 14) + 10;
    }

    private int addDrawing(Layout page, int y, String json, int x, int w, int entry) {
        if (ModelView.isModel(json)) {
            ModelView m = models.computeIfAbsent(json, ModelView::parse);
            page.pieces.add(new Model(x, y, w, m, json, entry));
            return y + m.screenHeight(font, w);
        }
        DiagramPainter.Drawing d = drawings.computeIfAbsent(json, DiagramPainter::parse);
        page.pieces.add(new Drawn(x, y, w, d, json, entry));
        return y + d.screenHeight(font, w, DiagramPainter.scale(textW - 24));
    }

    /** While typing, a half-written **bold** or {item} at the very end stays hidden until it is complete. */
    private static String hideUnfinished(String text) {
        int brace = text.lastIndexOf('{');
        if (brace >= 0 && text.indexOf('}', brace) < 0) text = text.substring(0, brace);
        int stars = 0;
        for (int i = text.indexOf("**"); i >= 0; i = text.indexOf("**", i + 2)) stars++;
        if (stars % 2 == 1) {
            int last = text.lastIndexOf("**");
            text = text.substring(0, last) + text.substring(last + 2);
        }
        return text;
    }

    /** Inline markup: **bold**, {item ids} as the item's name, `code` as plain words. */
    private static MutableComponent rich(String text) {
        MutableComponent out = Component.empty();
        Matcher m = INLINE.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) out.append(Component.literal(text.substring(at, m.start())));
            if (m.group(1) != null) out.append(Component.literal(m.group(1)).withStyle(Style.EMPTY.withBold(true)));
            else if (m.group(2) != null) out.append(Component.literal(itemName(m.group(2))).withStyle(Style.EMPTY.withBold(true)));
            else out.append(Component.literal(m.group(3)));
            at = m.end();
        }
        if (at < text.length()) out.append(Component.literal(text.substring(at)));
        return out;
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static String itemName(String id) {
        ItemStack s = stackOf(id);
        if (!s.isEmpty()) return s.getHoverName().getString();
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private void drawSpinner(GuiGraphics g, int x, int y) {
        long now = Util.getMillis();
        if (flavor.isEmpty() || now - flavorSince > 3200) {
            List<String> words = FLAVOR.getOrDefault(status, FLAVOR.get("thinking"));
            String next = words.get(RANDOM.nextInt(words.size()));
            if (words.size() > 1) while (next.equals(flavor)) next = words.get(RANDOM.nextInt(words.size()));
            flavor = next;
            flavorSince = now;
        }
        // A turning cog, the way a Create machine shows it is working.
        ItemStack cog = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "cogwheel"))
                .orElse(Items.CLOCK));
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 6, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((now % 3600) / 10f));
        g.pose().scale(0.75f, 0.75f, 1f);
        g.pose().translate(-8, -8, 0);
        g.renderItem(cog, 0, 0);
        g.pose().popPose();
        String dots = ".".repeat((int) (now / 400 % 4));
        g.drawString(font, Component.literal(flavor + dots).withStyle(ChatFormatting.ITALIC), x + 16, y + 2, FADED, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- widgets in the guide's style ----------

    /** A small plate of the guide's paper with a brown edge and ink lettering. */
    private final class BookButton extends AbstractButton {
        Runnable action;

        BookButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
        }

        @Override
        public void onPress() {
            if (action != null) action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean lit = active && (isHovered() || isFocused());
            plate(g, getX(), getY(), width, height, lit ? BUTTON_HOVER : BUTTON, active ? BUTTON_EDGE : RULE);
            if (lit) g.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height - 1, RULE);
            Component label = getMessage();
            g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2, active ? INK : FADED, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** One of the player's pages in the list, like a line in the guide's table of contents. */
    private final class PageRow extends AbstractButton {
        private final GuiGraphicsSummary summary;

        PageRow(int x, int y, int w, int h, GuideAiNet.Summary p) {
            super(x, y, w, h, Component.literal(p.title()));
            this.summary = new GuiGraphicsSummary(p);
        }

        @Override
        public void onPress() {
            open(summary.page.id(), summary.page.kind());
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (isHovered() || isFocused()) plate(g, getX(), getY(), width, height, ROW_HOVER, RULE);
            g.renderItem(icon(summary.page.kind()), getX() + 3, getY() + 3);
            int tx = getX() + 24, tw = width - 28;
            g.drawString(font, font.plainSubstrByWidth(summary.page.title(), tw), tx, getY() + 3, INK, false);
            g.drawString(font, font.plainSubstrByWidth(summary.meta, tw), tx, getY() + 12, FADED, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static ItemStack icon(int kind) {
        return switch (kind) {
            case GuideAiNet.KIND_DRAWING -> new ItemStack(Items.PAINTING);
            case GuideAiNet.KIND_MODEL -> new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "schematic"))
                    .orElse(Items.MAP));
            default -> new ItemStack(Items.PAPER);
        };
    }

    private record GuiGraphicsSummary(GuideAiNet.Summary page, String meta) {
        GuiGraphicsSummary(GuideAiNet.Summary page) {
            this(page, (switch (page.kind()) {
                case GuideAiNet.KIND_DRAWING -> "Saved drawing";
                case GuideAiNet.KIND_MODEL -> "Saved 3D model";
                default -> page.questions() + (page.questions() == 1 ? " question" : " questions");
            }) + ", last used " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(page.updated())));
        }
    }
}

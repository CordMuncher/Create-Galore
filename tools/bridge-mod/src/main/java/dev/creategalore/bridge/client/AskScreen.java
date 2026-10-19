package dev.creategalore.bridge.client;

import dev.creategalore.bridge.guideai.GuideAiNet;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Asking the Field Guide. Two views: the player's pages (each a separate conversation, newest first) and one open
 * page with its questions and answers and a line to ask on. While the book answers, its words arrive as they are
 * written and are typed onto the page with a soft scratch of the quill, a little cog turns, and a line says what
 * the book is up to. Drawings appear where the book puts them. Everything shown comes from the server.
 */
final class AskScreen extends Screen {
    private static final int TIMEOUT_MS = 4000;
    private static final int PAPER = 0xF0E8DCC0, INK = 0xFF2B2014, FADED = 0xFF6E5E48, YOU = 0xFF7A3E0C, BORDER = 0xFF5A4630,
            WARN = 0xFF8A2A1A;
    private static final Pattern DIAGRAM_MARK = Pattern.compile("^\\s*\\[diagram (\\d+)]\\s*$");
    private static final Random RANDOM = new Random();

    /** What the book says it is doing, by what it is doing. Picked at random and changed every few seconds. */
    private static final Map<String, List<String>> FLAVOR = Map.of(
            "thinking", List.of("Mining for an answer", "Smelting your question down", "Asking a villager (he said hmm)",
                    "Spinning up the water wheel", "Checking the stress units", "Feeding the Blaze Burner",
                    "Untangling the redstone", "Reading by torchlight", "Greasing the bearings", "Counting cobblestone",
                    "Consulting the elder pages", "Waiting for the gears to mesh", "Brewing a thought potion",
                    "Shaking the Wandering Trader for gossip", "Letting the mixer stir"),
            "writing", List.of("Inking the page", "Scribbling in the margins", "Dipping the quill", "Writing it down neatly"),
            "look_at", List.of("Squinting at what you're looking at", "Holding up the Engineer's Goggles"),
            "get_inventory", List.of("Rummaging through your pockets", "Peeking in your backpack"),
            "get_surroundings", List.of("Sniffing the weather", "Licking a finger to test the wind", "Checking the season"),
            "lookup_recipes", List.of("Flipping to the recipe pages", "Following the crafting trail"),
            "find_items", List.of("Thumbing through the item index", "Checking every chest in the library"),
            "draw_diagram", List.of("Sketching a little diagram", "Sharpening the charcoal", "Drawing it out"),
            "web_search", List.of("Asking the wiki librarians", "Sending a parrot to the wiki"),
            "web_fetch", List.of("Reading a page from the wiki", "Unrolling a wiki scroll"));

    private final Screen parent;
    /** null while the page list shows; "" for a new page that has no id yet. */
    private String pageId;
    private String title = "";
    private List<GuideAiNet.Entry> entries = List.of();
    private List<GuideAiNet.Summary> pages = List.of();
    private boolean pending, enabled = true, heard;
    private String notice = "";
    private String draft = "", lastQuestion = "";
    private int listOffset, scroll;
    private boolean stickToBottom = true;
    private final long opened = Util.getMillis();
    private EditBox input;
    private Button askButton;

    // The answer being written: what has arrived, how much of it is typed out, and the drawings made so far.
    private final StringBuilder liveText = new StringBuilder();
    private final List<String> liveDiagrams = new ArrayList<>();
    private String status = "thinking", flavor = "";
    private long flavorSince, lastTick, lastFrame;
    private double typed;
    /** After the answer is in: the last answer keeps typing from where it was instead of appearing all at once. */
    private int typingEntry = -1;

    /** Laid-out page, rebuilt when the page or the width changes. */
    private List<Block> blocks;
    private int blocksWidth;
    private final java.util.HashMap<String, DiagramPainter.Drawing> drawings = new java.util.HashMap<>();

    AskScreen(Screen parent) {
        super(Component.literal("Ask the Book"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!heard && pages.isEmpty() && pageId == null) GuideAiClient.send(GuideAiNet.LIST, "", "");
        int w = panelWidth(), left = (width - w) / 2;
        if (pageId == null) {
            Button newPage = addRenderableWidget(Button.builder(Component.literal("New page"), b -> openNew())
                    .bounds(left, height - 28, 100, 20).build());
            newPage.active = enabled && GuideAiClient.serverHasVoice();
            addRenderableWidget(Button.builder(Component.literal(parent == null ? "Close" : "Back to the guide"), b -> onClose())
                    .bounds(left + w - 120, height - 28, 120, 20).build());
            int rows = listRows();
            for (int i = 0; i < rows && listOffset + i < pages.size(); i++) {
                GuideAiNet.Summary p = pages.get(listOffset + i);
                int y = 44 + i * 24;
                String label = p.title() + "  (" + p.questions() + (p.questions() == 1 ? " question)" : " questions)");
                addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, w - 36)), b -> open(p.id()))
                        .bounds(left, y, w - 26, 20)
                        .tooltip(Tooltip.create(Component.literal("Last used "
                                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(p.updated())))))
                        .build());
                addRenderableWidget(Button.builder(Component.literal("x"), b -> GuideAiClient.send(GuideAiNet.DELETE, p.id(), ""))
                        .bounds(left + w - 22, y, 22, 20)
                        .tooltip(Tooltip.create(Component.literal("Tear out this page")))
                        .build());
            }
        } else {
            input = new EditBox(font, left, height - 52, w, 20, Component.literal("Your question"));
            input.setMaxLength(500);
            input.setHint(Component.literal("Ask the book about the pack...").withStyle(ChatFormatting.DARK_GRAY));
            input.setValue(draft);
            input.setResponder(s -> {
                draft = s;
                updateAsk();
            });
            addRenderableWidget(input);
            setInitialFocus(input);
            askButton = addRenderableWidget(Button.builder(Component.literal("Ask"), b -> ask()).bounds(left + w - 60, height - 28, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal("All pages"), b -> backToList()).bounds(left, height - 28, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("New page"), b -> openNew()).bounds(left + 84, height - 28, 80, 20).build());
            addRenderableWidget(Button.builder(soundLabel(), b -> {
                AskSettings.setSound(!AskSettings.sound());
                b.setMessage(soundLabel());
            }).bounds(left + 168, height - 28, 70, 20).tooltip(Tooltip.create(Component.literal("The quill's scratching while the book writes")))
                    .build());
            updateAsk();
        }
    }

    private static Component soundLabel() {
        return Component.literal(AskSettings.sound() ? "Sound: on" : "Sound: off");
    }

    private int panelWidth() {
        return Math.min(width - 24, 380);
    }

    private int listRows() {
        return Math.max(1, (height - 44 - 40) / 24);
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
        title = "New page";
        entries = List.of();
        pending = false;
        notice = "";
        stickToBottom = true;
        clearLive();
        blocks = null;
        rebuildWidgets();
    }

    private void open(String id) {
        pageId = id;
        title = "";
        entries = List.of();
        notice = "";
        stickToBottom = true;
        clearLive();
        blocks = null;
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
        blocks = null;
        stickToBottom = true;
        rebuildWidgets();
    }

    void showPages(GuideAiNet.Pages update) {
        heard = true;
        enabled = update.enabled();
        pages = update.pages();
        notice = update.notice();
        listOffset = Math.max(0, Math.min(listOffset, pages.size() - listRows()));
        blocks = null;
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
        blocks = null;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // A button that switched views was replaced, but the screen still focuses it: typing went nowhere and Space
        // pressed it again. Focus the question line instead.
        if (getFocused() != null && !children().contains(getFocused())) setFocused(pageId != null ? input : null);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pageId == null) {
            int max = Math.max(0, pages.size() - listRows());
            int next = Math.max(0, Math.min(max, listOffset - (int) Math.signum(scrollY)));
            if (next != listOffset) {
                listOffset = next;
                rebuildWidgets();
            }
        } else {
            scroll = Math.max(0, scroll - (int) (scrollY * 3 * font.lineHeight));
            stickToBottom = false;
        }
        return true;
    }

    // ---------- drawing the page ----------

    /** A piece of the laid-out page: one line of text, or a drawing. */
    private record Block(FormattedCharSequence text, int color, DiagramPainter.Drawing drawing, int height) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = panelWidth(), left = (width - w) / 2;
        g.drawCenteredString(font, pageId == null ? "Ask the Book: your pages" : (title.isEmpty() ? "..." : title), width / 2, 10, 0xFFFFFFFF);
        boolean noServer = !heard && Util.getMillis() - opened > TIMEOUT_MS;
        if (!GuideAiClient.serverHasVoice() || noServer) {
            drawLines(g, left, 30, w, "This server can't give the book a voice (its Create Galore Bridge is missing or older than yours). "
                    + "The written guide works as always.", 0xFFDDDDDD);
            return;
        }
        if (pageId == null) {
            if (!notice.isEmpty()) drawLines(g, left, 26, w, notice, enabled ? 0xFFFFDD88 : 0xFFDDDDDD);
            else if (heard && pages.isEmpty() && enabled)
                drawLines(g, left, 26, w, "No pages yet. Start a new page and ask the book anything about the pack.", 0xFFDDDDDD);
            return;
        }
        advanceTyping();
        // The open page: a sheet of paper with the conversation, newest at the bottom.
        int top = 24, bottom = height - 58 - (pending ? 20 : 0);
        g.fill(left - 4, top - 4, left + w + 4, height - 54, BORDER);
        g.fill(left - 3, top - 3, left + w + 3, height - 55, PAPER);
        int textW = w - 8;
        if (blocks == null || blocksWidth != textW) {
            blocks = layout(textW);
            blocksWidth = textW;
        }
        List<Block> page = new ArrayList<>(typingEntry >= 0 ? layout(textW) : blocks);
        if (pending) addLive(page, textW);
        int total = 0;
        for (Block b : page) total += b.height;
        int view = bottom - top, maxScroll = Math.max(0, total - view);
        if (stickToBottom || scroll > maxScroll) scroll = maxScroll;
        if (scroll >= maxScroll) stickToBottom = true;
        g.enableScissor(left, top, left + w, bottom);
        int y = top - scroll;
        ItemStack hovered = ItemStack.EMPTY;
        int scale = DiagramPainter.scale(textW);
        for (Block b : page) {
            if (y + b.height >= top && y <= bottom) {
                if (b.drawing != null) {
                    ItemStack h = b.drawing.paint(g, font, left + 4, y, textW, scale, mouseX, mouseY);
                    if (!h.isEmpty() && mouseY >= top && mouseY <= bottom) hovered = h;
                } else {
                    g.drawString(font, b.text, left + 4, y, b.color, false);
                }
            }
            y += b.height;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, view * view / total), barY = top + (view - barH) * scroll / maxScroll;
            g.fill(left + w - 2, barY, left + w, barY + barH, BORDER);
        }
        if (pending) drawSpinner(g, left, bottom + 2, w);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
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
                blocks = null;
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

    private void addLive(List<Block> page, int w) {
        String shown = liveText.substring(0, (int) Math.min(typed, liveText.length()));
        if (shown.isEmpty() && liveDiagrams.isEmpty()) return;
        if (!page.isEmpty()) page.add(gap());
        addAnswer(page, shown, liveDiagrams, w, true);
    }

    private void drawSpinner(GuiGraphics g, int left, int y, int w) {
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
        g.pose().translate(left + 8, y + 8, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((now % 3600) / 10f));
        g.pose().translate(-8, -8, 0);
        g.renderItem(cog, 0, 0);
        g.pose().popPose();
        String dots = ".".repeat((int) (now / 400 % 4));
        g.drawString(font, flavor + dots, left + 22, y + 4, FADED, false);
    }

    private static Block gap() {
        return new Block(FormattedCharSequence.EMPTY, INK, null, 9);
    }

    private List<Block> layout(int w) {
        List<Block> out = new ArrayList<>();
        String name = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "You";
        for (int i = 0; i < entries.size(); i++) {
            GuideAiNet.Entry e = entries.get(i);
            if (e.who() == GuideAiNet.WHO_DIAGRAM) continue; // drawn with the answer before it
            if (!out.isEmpty()) out.add(gap());
            switch (e.who()) {
                case GuideAiNet.WHO_PLAYER -> add(out, Component.literal(name + ": " + e.text()), w, YOU);
                case GuideAiNet.WHO_BOOK -> {
                    List<String> diagrams = new ArrayList<>();
                    for (int j = i + 1; j < entries.size() && entries.get(j).who() == GuideAiNet.WHO_DIAGRAM; j++) diagrams.add(entries.get(j).text());
                    String text = e.text();
                    boolean typing = i == typingEntry;
                    if (typing) text = text.substring(0, (int) Math.min(typed, text.length()));
                    addAnswer(out, text, diagrams, w, typing);
                }
                default -> add(out, Component.literal(e.text()).withStyle(ChatFormatting.ITALIC), w, FADED);
            }
        }
        if (entries.isEmpty() && !pending)
            add(out, Component.literal("Ask anything about the pack: how something is made, what to do next, why a machine "
                    + "won't run, or what you're looking at. The book sees what you see and carry, never the map, and draws "
                    + "a little diagram when a picture helps.").withStyle(ChatFormatting.ITALIC), w, FADED);
        if (!notice.isEmpty()) {
            if (!out.isEmpty()) out.add(gap());
            add(out, Component.literal(notice).withStyle(ChatFormatting.ITALIC), w, WARN);
        }
        return out;
    }

    /** An answer with its drawings: each [diagram N] line becomes drawing N; drawings it never placed go at the end. */
    private void addAnswer(List<Block> out, String text, List<String> diagrams, int w, boolean partial) {
        boolean[] placed = new boolean[diagrams.size()];
        StringBuilder para = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            Matcher m = DIAGRAM_MARK.matcher(lines[li]);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1)) - 1;
                if (n >= 0 && n < diagrams.size()) {
                    flush(out, para, w);
                    out.add(drawingBlock(diagrams.get(n), w));
                    placed[n] = true;
                    continue;
                }
            }
            // While typing, a half-typed "[diagram" line stays hidden until it is complete.
            if (partial && li == lines.length - 1 && lines[li].stripLeading().startsWith("[")) continue;
            if (!para.isEmpty()) para.append('\n');
            para.append(lines[li]);
        }
        flush(out, para, w);
        if (!partial)
            for (int i = 0; i < diagrams.size(); i++) if (!placed[i]) out.add(drawingBlock(diagrams.get(i), w));
    }

    private void flush(List<Block> out, StringBuilder para, int w) {
        String t = para.toString().strip();
        para.setLength(0);
        if (!t.isEmpty()) add(out, Component.literal(t), w, INK);
    }

    private Block drawingBlock(String json, int w) {
        DiagramPainter.Drawing d = drawings.computeIfAbsent(json, DiagramPainter::parse);
        return new Block(null, INK, d, d.screenHeight(font, w, DiagramPainter.scale(w)) + 4);
    }

    private void add(List<Block> out, Component text, int w, int color) {
        for (String paragraph : text.getString().split("\n", -1)) {
            Component p = Component.literal(paragraph).withStyle(text.getStyle());
            List<FormattedCharSequence> split = font.split(p, w);
            if (split.isEmpty()) out.add(new Block(FormattedCharSequence.EMPTY, color, null, font.lineHeight));
            for (FormattedCharSequence s : split) out.add(new Block(s, color, null, font.lineHeight));
        }
    }

    private void drawLines(GuiGraphics g, int left, int y, int w, String text, int color) {
        for (FormattedCharSequence s : font.split(Component.literal(text), w)) {
            g.drawString(font, s, left, y, color);
            y += font.lineHeight + 1;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

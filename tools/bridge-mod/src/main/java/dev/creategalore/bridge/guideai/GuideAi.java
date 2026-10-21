package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * The Field Guide's voice: players ask the book in plain words and the server asks Claude, with the whole guide as
 * a cached prefix and read-only tools that see what the asking player sees. Runs only on a dedicated server whose
 * owner saved an API key at start (see {@link GuideAiSettings}); everywhere else the book says the voice is off.
 * The key and the raw API traffic never leave this class's worker threads and the HTTPS connection.
 */
public final class GuideAi {
    private static final Logger LOG = LogUtils.getLogger();
    /** Model rounds per question: tool calls, then one last round that must answer. */
    private static final int MAX_ROUNDS = 8;
    /** Drawings and models in one answer. */
    private static final int MAX_DRAWINGS = 4;
    /** Pages one player can have the book writing on at once. */
    static final int MAX_AT_ONCE = 3;
    private static final String NOTICE_OFF = "The book's voice is off on this server. It only speaks on a home server whose owner "
            + "saved an Anthropic API key when starting it. The written guide works as always.";
    private static final String NOTICE_NOT_DEDICATED = "The book's voice only speaks on a dedicated home server, not in singleplayer "
            + "or a LAN world. The written guide works as always.";

    private GuideAi() {
    }

    /** Everything the voice needs while the server runs; null while it is off. */
    private record Live(MinecraftServer server, GuideAiSettings settings, AnthropicClient client, JsonArray system,
                        JsonArray customTools, JsonArray webTools, ThreadStore store, ExecutorService workers) {
    }

    private static volatile Live live;
    private static volatile String offNotice = NOTICE_OFF;
    /** The pages the book is writing on, per player. */
    private static final Map<UUID, Set<String>> pending = new HashMap<>();
    /** The page each player has open in the book, if any (server thread): its answers aren't unread. */
    private static final Map<UUID, String> viewing = new HashMap<>();
    /**
     * What the book has written so far on each page it is writing on (server thread), so a page opened halfway
     * through an answer shows it from the start.
     */
    private static final Map<String, Writing> writing = new HashMap<>();

    private static final class Writing {
        final StringBuilder text = new StringBuilder();
        final List<String> diagrams = new ArrayList<>();
        String status = "thinking";
    }
    private static final Map<UUID, Long> lastAsked = new HashMap<>();
    /** Request fields dropped after the API rejected them once (e.g. web tools on an account without them). */
    private static volatile boolean webRejected, fallbacksRejected, strictRejected;

    public static void register(IEventBus modBus) {
        GuideAiNet.register(modBus);
        NeoForge.EVENT_BUS.addListener(GuideAi::onStarted);
        NeoForge.EVENT_BUS.addListener(GuideAi::onStopping);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) -> {
            TouchedStorage s = storage;
            if (s != null) s.onRightClick(e);
        });
        // Last, and only if nothing cancelled it, so a placement another mod refused isn't counted.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) -> {
            PlacedBlocks p = placed;
            if (p != null && !e.isCanceled()) p.onPlace(e);
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) -> {
            PlacedBlocks p = placed;
            if (p != null && !e.isCanceled()) p.onBreak(e);
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (net.neoforged.neoforge.event.level.PistonEvent.Pre e) -> {
            PlacedBlocks p = placed;
            if (p != null && !e.isCanceled()) p.onPiston(e);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) ->
                viewing.remove(e.getEntity().getUUID()));
        // Their page list on joining, so the Field Guide can show answers they haven't opened yet.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            Live l = live;
            if (l != null && e.getEntity() instanceof ServerPlayer player) sendPages(player, l.store.book(player.getUUID()), "");
        });
        NeoForge.EVENT_BUS.addListener(BuildClaims::onCommands);
        NeoForge.EVENT_BUS.addListener(BuildClaims::onTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Save e) -> {
            if (!(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                    || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
            TouchedStorage s = storage;
            if (s != null) s.save();
            PlacedBlocks p = placed;
            if (p != null) p.save();
        });
    }

    /** The storage players have opened; kept on a dedicated server even while the voice is off, so it is ready. */
    private static volatile TouchedStorage storage;

    static TouchedStorage storage() {
        return storage;
    }

    /** The blocks players have placed; kept on a dedicated server even while the voice is off, like storage. */
    private static volatile PlacedBlocks placed;

    static PlacedBlocks placed() {
        return placed;
    }

    /** What each player had on screen when they last asked (server thread only). */
    private static final Map<UUID, Vision.Snapshot> views = new HashMap<>();

    static Vision.Snapshot view(UUID player) {
        return views.get(player);
    }

    private static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            offNotice = NOTICE_NOT_DEDICATED;
            return;
        }
        storage = new TouchedStorage(server.getWorldPath(LevelResource.ROOT).resolve("create_galore_field_guide_ai"));
        placed = new PlacedBlocks(server.getWorldPath(LevelResource.ROOT).resolve("create_galore_field_guide_ai"));
        GuideAiSettings settings = GuideAiSettings.load();
        if (settings == null) {
            offNotice = NOTICE_OFF;
            LOG.info("Field Guide voice is off (no key saved for it; the updater asks at server start)");
            return;
        }
        GuideText.Guide guide = GuideText.load(server.getResourceManager());
        if (guide.notes().length() < 1000) {
            offNotice = "The book's voice is off: the server couldn't read the Field Guide.";
            LOG.warn("Field Guide voice: the Field Guide text is missing ({} characters); staying off", guide.notes().length());
            return;
        }
        if (!guide.fullSections().isEmpty()) {
            LOG.warn("Field Guide voice: the AI notes of {} are missing or older than their entries, so those sections go to "
                    + "the voice in full (tools/guide_digest.py --check says what to update)", guide.fullSections());
        }
        JsonArray system = new JsonArray();
        system.add(textBlock(GuideText.PERSONA));
        JsonObject reference = textBlock("The Create Galore Field Guide follows as compact notes: every fact of the guide, "
                + "written once, one block per entry headed '## <entry id> | <entry name>' (a section that is sent in full "
                + "instead uses '--- <entry name> (entry <entry id>) ---'). Then every mod on this server.\n\n"
                + guide.notes() + "\n\n=== Mods on this server (mod id: name version) ===\n" + GuideText.mods());
        // One cache entry for persona, tools and guide, kept for an hour so both players' questions reuse it.
        JsonObject cache = new JsonObject();
        cache.addProperty("type", "ephemeral");
        cache.addProperty("ttl", "1h");
        reference.add("cache_control", cache);
        system.add(reference);
        // Room for both players to have three pages being written at once.
        ExecutorService workers = Executors.newFixedThreadPool(2 * MAX_AT_ONCE, r -> {
            Thread t = new Thread(r, "Field Guide voice");
            t.setDaemon(true);
            return t;
        });
        live = new Live(server, settings, new AnthropicClient(settings.apiUrl, settings.key()), system, WorldTools.definitions(),
                settings.webSearch ? webTools() : new JsonArray(), new ThreadStore(server.getWorldPath(LevelResource.ROOT)
                .resolve("create_galore_field_guide_ai")), workers);
        LOG.info("Field Guide voice is on: {} at {} effort, guide notes {} characters (the full guide is {}), web search {}",
                settings.model, settings.effort, guide.notes().length(), guide.full(), settings.webSearch ? "on" : "off");
    }

    private static void onStopping(ServerStoppingEvent event) {
        TouchedStorage s = storage;
        storage = null;
        if (s != null) s.save();
        PlacedBlocks p = placed;
        placed = null;
        if (p != null) p.save();
        views.clear();
        viewing.clear();
        writing.clear();
        Live l = live;
        live = null;
        if (l != null) l.workers.shutdownNow();
        synchronized (pending) {
            pending.clear();
        }
    }

    private static JsonObject textBlock(String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("text", text);
        return o;
    }

    /** Web search and fetch, limited to Modrinth, the Minecraft wiki and each mod's own page; sorted so the cache holds. */
    private static JsonArray webTools() {
        Set<String> domains = new TreeSet<>(List.of("modrinth.com", "minecraft.wiki"));
        Set<String> skip = Set.of("discord.gg", "discord.com", "youtube.com", "youtu.be", "patreon.com", "ko-fi.com", "twitter.com",
                "x.com", "curseforge.com", "www.curseforge.com", "legacy.curseforge.com", "reddit.com", "twitch.tv", "paypal.com");
        ModList.get().getMods().forEach(m -> m.getModURL().map(URL::getHost).ifPresent(host -> {
            String h = host.toLowerCase(Locale.ROOT);
            if (h.startsWith("www.")) h = h.substring(4);
            if (!h.matches("[a-z0-9.-]{3,80}") || !h.contains(".") || skip.contains(h)) return;
            domains.add(h);
        }));
        JsonArray allowed = new JsonArray();
        domains.stream().limit(60).forEach(allowed::add);
        JsonArray tools = new JsonArray();
        JsonObject search = new JsonObject();
        search.addProperty("type", "web_search_20260209");
        search.addProperty("name", "web_search");
        search.addProperty("max_uses", 2);
        search.add("allowed_domains", allowed);
        tools.add(search);
        JsonObject fetch = new JsonObject();
        fetch.addProperty("type", "web_fetch_20260209");
        fetch.addProperty("name", "web_fetch");
        fetch.addProperty("max_uses", 2);
        fetch.add("allowed_domains", allowed.deepCopy());
        tools.add(fetch);
        return tools;
    }

    // ---------- requests from the book screen (server thread) ----------

    static void handle(GuideAiNet.Request request, Player sender) {
        if (!(sender instanceof ServerPlayer player)) return;
        Live l = live;
        if (l == null) {
            send(player, new GuideAiNet.Pages(false, offNotice, List.of()));
            return;
        }
        ThreadStore.Book book = l.store.book(player.getUUID());
        UUID id = player.getUUID();
        switch (request.action()) {
            case GuideAiNet.LIST -> {
                viewing.remove(id);
                sendPages(player, book, "");
            }
            case GuideAiNet.AWAY -> viewing.remove(id);
            case GuideAiNet.OPEN -> {
                ThreadStore.Page page = book.page(request.pageId());
                if (page == null) {
                    viewing.remove(id);
                    sendPages(player, book, "That page is gone.");
                } else {
                    viewing.put(id, page.id);
                    if (page.unread) {
                        page.unread = false;
                        l.store.save(id);
                    }
                    sendView(player, page, "");
                    catchUp(player, page);
                }
            }
            case GuideAiNet.DELETE -> {
                ThreadStore.Page page = book.page(request.pageId());
                if (page != null && !isPending(id, page.id)) {
                    book.pages.remove(page);
                    l.store.save(id);
                }
                sendPages(player, book, "");
            }
            case GuideAiNet.ASK -> ask(l, player, book, request.pageId(), request.text(), request.fov(), request.aspect(), request.ticket());
            case GuideAiNet.SAVE -> save(l, player, book, request.pageId(), request.text());
            default -> {
            }
        }
    }

    private static void ask(Live l, ServerPlayer player, ThreadStore.Book book, String pageId, String rawQuestion, int fov, float aspect,
                            String ticket) {
        UUID id = player.getUUID();
        String question = GuideAiNet.clip(rawQuestion.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").strip(), GuideAiNet.MAX_QUESTION);
        ThreadStore.Page page = pageId.isEmpty() ? null : book.page(pageId);
        if (question.isEmpty()) {
            if (page != null) sendView(player, page, "");
            return;
        }
        if (!pageId.isEmpty() && page == null) {
            sendPages(player, book, "That page is gone.");
            return;
        }
        String refusal = null;
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        if (!today.equals(book.day)) {
            book.day = today;
            book.askedToday = 0;
        }
        if (page != null && isPending(id, page.id)) refusal = "The book is still writing its answer on this page.";
        else if (pendingCount(id) >= MAX_AT_ONCE)
            refusal = "The book is already writing " + MAX_AT_ONCE + " answers for you. Ask again when one of them is done.";
        else if (lastAsked.containsKey(id) && now - lastAsked.get(id) < l.settings.cooldownSeconds * 1000L)
            refusal = "Give the book a few seconds between questions.";
        else if (book.askedToday >= l.settings.dailyQuestions)
            refusal = "The book has answered " + l.settings.dailyQuestions + " of your questions today and needs to rest until tomorrow.";
        else if (page != null && page.kind != ThreadStore.KIND_CHAT)
            refusal = "This page keeps a saved drawing. Start a new page to ask.";
        else if (page != null && page.questions >= ThreadStore.MAX_QUESTIONS)
            refusal = "This page is full. Start a new page to keep asking.";
        if (refusal != null) {
            if (page != null) sendView(player, page, refusal);
            else sendPages(player, book, refusal);
            return;
        }
        boolean fresh = page == null;
        if (page == null) page = l.store.newPage(book, GuideAiNet.clip(question.replace('\n', ' '), 60), ThreadStore.KIND_CHAT);
        ThreadStore.touch(book, page);
        page.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_PLAYER, question));
        page.unread = false;
        book.askedToday++;
        lastAsked.put(id, now);
        synchronized (pending) {
            pending.computeIfAbsent(id, k -> new HashSet<>()).add(page.id);
        }
        writing.put(page.id, new Writing());
        viewing.put(id, page.id);
        l.store.save(id);
        // What is on their screen right now; the book reads it only if it calls get_view.
        try {
            views.put(id, Vision.capture(player, fov, aspect, placed));
        } catch (RuntimeException e) {
            views.remove(id);
            LOG.warn("Field Guide voice: couldn't take in what {} sees ({})", player.getGameProfile().getName(), e.toString());
        }
        sendView(player, page, "", fresh ? GuideAiNet.clip(ticket, 64) : "");

        // The API sees the conversation so far plus this question; the page only keeps it once it has an answer.
        JsonArray messages = page.messages.deepCopy();
        unbindThinking(messages);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", player.getGameProfile().getName() + " asks: " + question);
        messages.add(user);
        String playerName = player.getGameProfile().getName();
        ThreadStore.Page target = page;
        l.workers.execute(() -> {
            Outcome outcome;
            try {
                outcome = converse(l, id, target.id, messages);
            } catch (InterruptedException e) {
                return; // server stopping
            } catch (RuntimeException e) {
                LOG.warn("Field Guide voice: answering {} failed", playerName, e);
                outcome = Outcome.failed("The book lost its train of thought (an error on the server). Try again.");
            }
            Outcome done = outcome;
            l.server.execute(() -> finish(l, id, target, messages, done));
        });
    }

    private static void finish(Live l, UUID id, ThreadStore.Page page, JsonArray messages, Outcome outcome) {
        synchronized (pending) {
            Set<String> mine = pending.get(id);
            if (mine != null) {
                mine.remove(page.id);
                if (mine.isEmpty()) pending.remove(id);
            }
        }
        writing.remove(page.id);
        if (live != l) return;
        if (outcome.answer != null) {
            page.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_BOOK, GuideAiNet.clipText(outcome.answer)));
            for (String d : outcome.diagrams) page.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_DIAGRAM, d));
            if (outcome.keep) {
                dropPictures(messages);
                page.messages = messages;
                page.questions++;
            }
        }
        if (outcome.note != null) page.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_NOTE, outcome.note));
        if (outcome.answer != null && page.questions == ThreadStore.SUGGEST_NEW_PAGE)
            page.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_NOTE, "This page is getting long. A new page answers faster and "
                    + "costs less; the book still knows the whole guide there."));
        ThreadStore.Book book = l.store.book(id);
        ThreadStore.touch(book, page);
        // Unread until they open it, unless it is the page they have open now.
        boolean open = page.id.equals(viewing.get(id));
        page.unread = !open && book.pages.contains(page);
        l.store.save(id);
        ServerPlayer player = l.server.getPlayerList().getPlayer(id);
        if (player == null || !book.pages.contains(page)) return;
        send(player, new GuideAiNet.Answered(page.id, GuideAiNet.clip(page.title, 64), outcome.answer != null));
        if (open) sendView(player, page, "");
        else if (!viewing.containsKey(id)) sendPages(player, book, "");
    }

    /** Opening a page the book is writing on: what it has written so far, as the live packets would have brought it. */
    private static void catchUp(ServerPlayer player, ThreadStore.Page page) {
        Writing w = writing.get(page.id);
        if (w == null) return;
        send(player, new GuideAiNet.Live(page.id, true, w.text.toString(), w.status, ""));
        for (String d : w.diagrams) send(player, new GuideAiNet.Live(page.id, false, "", w.status, d));
    }

    /** Copies a drawing or model from a page onto a new page of its own, so the player can find it again. */
    private static void save(Live l, ServerPlayer player, ThreadStore.Book book, String pageId, String key) {
        ThreadStore.Page page = book.page(pageId);
        if (page == null) {
            sendPages(player, book, "That page is gone.");
            return;
        }
        String found = null;
        for (GuideAiNet.Entry e : page.entries)
            if (e.who() == GuideAiNet.WHO_DIAGRAM && GuideAiNet.drawingKey(e.text()).equals(key)) found = e.text();
        String json = found;
        if (json == null || page.kind != ThreadStore.KIND_CHAT) {
            sendView(player, page, "");
            return;
        }
        for (ThreadStore.Page p : book.pages) {
            if (p.kind != ThreadStore.KIND_CHAT && p.entries.stream().anyMatch(e -> e.who() == GuideAiNet.WHO_DIAGRAM && e.text().equals(json))) {
                sendView(player, page, "Already saved as \"" + p.title + "\" in your pages.");
                return;
            }
        }
        if (ThreadStore.saved(book) >= ThreadStore.MAX_SAVED) {
            sendView(player, page, "Your book holds " + ThreadStore.MAX_SAVED + " saved drawings. Tear one out to save another.");
            return;
        }
        boolean model;
        String name;
        try {
            JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            model = Models.isModel(o);
            name = GuideText.string(o, "title").strip();
        } catch (RuntimeException e) {
            model = false;
            name = "";
        }
        if (name.isEmpty()) name = model ? "A model" : "A drawing";
        ThreadStore.Page saved = l.store.newPage(book, GuideAiNet.clip(name, 60), model ? ThreadStore.KIND_MODEL : ThreadStore.KIND_DRAWING);
        saved.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_NOTE, "Saved from the page \"" + page.title + "\"."));
        saved.entries.add(new GuideAiNet.Entry(GuideAiNet.WHO_DIAGRAM, json));
        l.store.save(player.getUUID());
        sendView(player, page, "Saved as its own page, \"" + saved.title + "\". Find it under All pages.");
    }

    private static boolean isPending(UUID id, String pageId) {
        synchronized (pending) {
            Set<String> mine = pending.get(id);
            return mine != null && mine.contains(pageId);
        }
    }

    private static int pendingCount(UUID id) {
        synchronized (pending) {
            Set<String> mine = pending.get(id);
            return mine == null ? 0 : mine.size();
        }
    }

    private static void sendPages(ServerPlayer player, ThreadStore.Book book, String notice) {
        List<GuideAiNet.Summary> list = new ArrayList<>();
        for (ThreadStore.Page p : book.pages)
            list.add(new GuideAiNet.Summary(p.id, GuideAiNet.clip(p.title, 64), p.questions, p.updated, p.kind,
                    isPending(player.getUUID(), p.id), p.unread));
        send(player, new GuideAiNet.Pages(true, notice, list));
    }

    private static void sendView(ServerPlayer player, ThreadStore.Page page, String notice) {
        sendView(player, page, notice, "");
    }

    /** ticket: the one the screen sent with the first question of a new page, so it knows this page is that one. */
    private static void sendView(ServerPlayer player, ThreadStore.Page page, String notice, String ticket) {
        // The newest entries that fit in one packet (models make pages heavy; the game drops a player over a packet too large).
        int from = page.entries.size(), bytes = 0;
        while (from > 0 && page.entries.size() - from < 500) {
            int size = page.entries.get(from - 1).text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 16;
            if (bytes + size > VIEW_BUDGET) break;
            bytes += size;
            from--;
        }
        List<GuideAiNet.Entry> entries = new ArrayList<>(page.entries.subList(from, page.entries.size()));
        // Drawings belong to the answer before them; don't start on one whose answer was cut off.
        while (!entries.isEmpty() && entries.get(0).who() == GuideAiNet.WHO_DIAGRAM && from > 0) {
            entries.remove(0);
            from++;
        }
        if (from > 0) entries.add(0, new GuideAiNet.Entry(GuideAiNet.WHO_NOTE, "Earlier questions on this page are too long to show here."));
        if (notice.isEmpty() && page.kind == ThreadStore.KIND_CHAT && page.edition < ThreadStore.EDITION && !page.entries.isEmpty())
            notice = "This page is from an older edition of the book. You can keep asking here; start a new page for the "
                    + "newest features.";
        send(player, new GuideAiNet.View(page.id, GuideAiNet.clip(page.title, 64), List.copyOf(entries), isPending(player.getUUID(), page.id),
                notice, page.kind, ticket));
    }

    /** Bytes of page text per view packet, well under the game's 1 MiB limit for one packet. */
    private static final int VIEW_BUDGET = 700_000;

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    // ---------- the conversation with the API (worker thread) ----------

    /** What came of a question: the text to show, its drawings, a note under it, and whether it joins the page's history. */
    private record Outcome(String answer, List<String> diagrams, String note, boolean keep) {
        static Outcome failed(String note) {
            return new Outcome(null, List.of(), note, false);
        }
    }

    /**
     * Sends the answer to the book as it is written: text in small batches (a few a second, so the screen can type it
     * out), what the book is doing while it calls tools, and each drawing as it is made. Called from the worker thread;
     * the packets go out from the server thread.
     */
    private static final class Streamer implements AnthropicClient.Listener {
        private final Live l;
        private final UUID player;
        private final String pageId;
        private final StringBuilder text = new StringBuilder();
        private boolean reset = true;
        private String status = "thinking";
        private long lastSent;

        Streamer(Live l, UUID player, String pageId) {
            this.l = l;
            this.player = player;
            this.pageId = pageId;
        }

        /** A new round: whatever the book wrote before calling its tools is replaced by what comes next. */
        void newRound() {
            text.setLength(0);
            reset = true;
            status = "thinking";
            flush("");
        }

        void status(String s) {
            status = s;
            flush("");
        }

        void diagram(String json) {
            flush(json);
        }

        @Override
        public void blockStart(JsonObject block) {
            String type = GuideText.string(block, "type");
            if (type.equals("tool_use") || type.equals("server_tool_use")) status(GuideText.string(block, "name"));
            else if (type.equals("thinking") || type.equals("redacted_thinking")) status("thinking");
            else if (type.equals("text") && !status.equals("writing")) status("writing");
        }

        @Override
        public void text(String delta) {
            text.append(delta);
            if (System.currentTimeMillis() - lastSent >= 120) flush("");
        }

        void flush(String diagram) {
            String chunk = text.toString();
            text.setLength(0);
            boolean r = reset;
            reset = false;
            lastSent = System.currentTimeMillis();
            GuideAiNet.Live packet = new GuideAiNet.Live(pageId, r, chunk, status, diagram);
            l.server.execute(() -> {
                Writing w = writing.get(pageId);
                if (w != null) {
                    // A new round starts the text over but keeps the drawings made so far (they are part of the answer).
                    if (r) w.text.setLength(0);
                    w.text.append(chunk);
                    w.status = packet.status();
                    if (!diagram.isEmpty()) w.diagrams.add(diagram);
                }
                ServerPlayer p = l.server.getPlayerList().getPlayer(player);
                // Only to the page they have open; the others catch up when opened.
                if (p != null && pageId.equals(viewing.get(player))) send(p, packet);
            });
        }
    }

    private static Outcome converse(Live l, UUID playerId, String pageId, JsonArray messages) throws InterruptedException {
        Usage usage = new Usage();
        StringBuilder note = new StringBuilder();
        List<String> diagrams = new ArrayList<>();
        Streamer streamer = new Streamer(l, playerId, pageId);
        try {
            for (int round = 1; ; round++) {
                boolean last = round >= MAX_ROUNDS;
                if (round > 1) streamer.newRound();
                JsonObject response = call(l, messages, last, streamer);
                streamer.flush("");
                usage.add(response);
                JsonArray content = response.has("content") ? response.getAsJsonArray("content") : new JsonArray();
                String stop = GuideText.string(response, "stop_reason");
                if (stop.equals("refusal")) {
                    usage.log(playerId, round, "refused");
                    return new Outcome("The book would rather not answer that one. Ask it something else about the pack.", List.of(), null, false);
                }
                JsonObject assistant = new JsonObject();
                assistant.addProperty("role", "assistant");
                assistant.add("content", content);
                messages.add(assistant);
                switch (stop) {
                    case "tool_use" -> {
                        if (last) {
                            note.append("The book ran out of time looking things up. ");
                            usage.log(playerId, round, "rounds");
                            return finishText(messages, diagrams, note, true);
                        }
                        messages.add(toolResults(l, playerId, content, diagrams, streamer));
                    }
                    case "pause_turn" -> {
                        // A long web search paused the turn; sending the conversation back lets it carry on.
                    }
                    case "max_tokens" -> {
                        note.append("The book ran out of page here; ask it to go on, or ask something narrower. ");
                        usage.log(playerId, round, stop);
                        return finishText(messages, diagrams, note, true);
                    }
                    case "model_context_window_exceeded" -> {
                        note.append("This page is too long for the book to keep in mind. Start a new page. ");
                        usage.log(playerId, round, stop);
                        return finishText(messages, diagrams, note, false);
                    }
                    default -> {
                        usage.log(playerId, round, stop);
                        return finishText(messages, diagrams, note, true);
                    }
                }
            }
        } catch (AnthropicClient.ApiException e) {
            LOG.warn("Field Guide voice: the API refused a request ({} {})", e.status, e.type);
            return Outcome.failed(e.getMessage());
        }
    }

    private static final String PICTURE_NOTE = "(The draft's picture was here.)";

    /**
     * Draft pictures only help while the book works on that answer; the page keeps a note in their place so later
     * questions don't send them again. The API binds each thinking block to everything before it, so the thinking
     * written after a picture no longer matches once the picture is gone, and the next question on the page would be
     * refused; those thinking blocks go too (earlier turns' thinking is optional).
     */
    private static void dropPictures(JsonArray messages) {
        for (JsonElement m : messages) {
            if (!m.isJsonObject() || !m.getAsJsonObject().has("content") || !m.getAsJsonObject().get("content").isJsonArray()) continue;
            for (JsonElement block : m.getAsJsonObject().getAsJsonArray("content")) {
                if (!block.isJsonObject() || !block.getAsJsonObject().has("content")) continue;
                JsonElement parts = block.getAsJsonObject().get("content");
                if (!parts.isJsonArray()) continue;
                JsonArray kept = new JsonArray();
                for (JsonElement part : parts.getAsJsonArray()) {
                    if (part.isJsonObject() && GuideText.string(part.getAsJsonObject(), "type").equals("image")) {
                        JsonObject note = new JsonObject();
                        note.addProperty("type", "text");
                        note.addProperty("text", PICTURE_NOTE);
                        kept.add(note);
                    } else {
                        kept.add(part);
                    }
                }
                block.getAsJsonObject().add("content", kept);
            }
        }
        unbindThinking(messages);
    }

    /**
     * Takes the thinking out of the page's earlier turns once a picture note is in them: the API binds a thinking block
     * to the whole conversation around it (its error names blocks on both sides of the note), so none can be trusted
     * to match. Thinking from earlier turns is optional. Also run on each new question, which mends pages saved
     * before this was done.
     */
    static void unbindThinking(JsonArray messages) {
        if (!messages.toString().contains(PICTURE_NOTE)) return;
        for (JsonElement m : messages) {
            if (!m.isJsonObject()) continue;
            JsonObject o = m.getAsJsonObject();
            if (!GuideText.string(o, "role").equals("assistant") || !o.has("content") || !o.get("content").isJsonArray()) continue;
            JsonArray kept = new JsonArray();
            for (JsonElement block : o.getAsJsonArray("content")) {
                String type = block.isJsonObject() ? GuideText.string(block.getAsJsonObject(), "type") : "";
                if (!type.equals("thinking") && !type.equals("redacted_thinking")) kept.add(block);
            }
            o.add("content", kept);
        }
    }

    /** The answer is the text of the book's last turn; earlier "let me check" lines between tool calls are dropped. */
    private static Outcome finishText(JsonArray messages, List<String> diagrams, StringBuilder note, boolean keep) {
        String answer = "";
        for (int i = messages.size() - 1; i >= 0 && answer.isEmpty(); i--) {
            JsonObject m = messages.get(i).getAsJsonObject();
            if (!GuideText.string(m, "role").equals("assistant")) break;
            answer = text(m.getAsJsonArray("content"));
        }
        if (answer.isEmpty() && diagrams.isEmpty()) answer = "The book turned a few pages but found nothing to say. Try asking another way.";
        String n = note.toString().strip();
        return new Outcome(answer, List.copyOf(diagrams), n.isEmpty() ? null : n, keep);
    }

    private static String text(JsonArray content) {
        StringBuilder out = new StringBuilder();
        for (JsonElement e : content) {
            JsonObject block = e.getAsJsonObject();
            if (GuideText.string(block, "type").equals("text")) out.append(GuideText.string(block, "text"));
        }
        return out.toString().strip();
    }

    private static JsonObject call(Live l, JsonArray messages, boolean last, Streamer streamer)
            throws AnthropicClient.ApiException, InterruptedException {
        try {
            return l.client.stream(body(l, messages, last), streamer);
        } catch (AnthropicClient.ApiException e) {
            String t = e.type.toLowerCase(Locale.ROOT);
            if (e.status != 400) throw e;
            if (!webRejected && l.webTools.size() > 0 && (t.contains("web_") || t.contains("domain"))) {
                webRejected = true;
                LOG.warn("Field Guide voice: the API rejected the web tools ({}); asking without them from now on", e.type);
            } else if (!fallbacksRejected && t.contains("fallback")) {
                fallbacksRejected = true;
                LOG.warn("Field Guide voice: the API rejected the fallback setting ({}); asking without it from now on", e.type);
            } else if (!strictRejected && (t.contains("strict") || t.contains("schema"))) {
                strictRejected = true;
                LOG.warn("Field Guide voice: the API rejected a strict tool schema ({}); sending the tools unchecked from now on", e.type);
            } else {
                throw e;
            }
            return call(l, messages, last, streamer);
        }
    }

    static JsonObject body(Live l, JsonArray messages, boolean last) {
        JsonObject body = new JsonObject();
        body.addProperty("model", l.settings.model);
        body.addProperty("max_tokens", l.settings.maxTokens);
        body.add("system", l.system);
        JsonArray tools = l.customTools.deepCopy();
        if (strictRejected) tools.forEach(t -> t.getAsJsonObject().remove("strict"));
        if (!webRejected) tools.addAll(l.webTools);
        body.add("tools", tools);
        if (last) {
            JsonObject none = new JsonObject();
            none.addProperty("type", "none");
            body.add("tool_choice", none);
        }
        body.add("messages", messages);
        JsonObject output = new JsonObject();
        output.addProperty("effort", l.settings.effort);
        body.add("output_config", output);
        // A second cache entry (five minutes) at the end of the page, so a follow-up re-reads its page cheaply too.
        JsonObject cache = new JsonObject();
        cache.addProperty("type", "ephemeral");
        body.add("cache_control", cache);
        if (!fallbacksRejected) body.addProperty("fallbacks", "default");
        return body;
    }

    /** Runs the book's tool calls on the server thread, for the player who asked, and returns them as one user turn. */
    private static JsonObject toolResults(Live l, UUID playerId, JsonArray content, List<String> diagrams, Streamer streamer)
            throws InterruptedException {
        JsonArray results = new JsonArray();
        for (JsonElement e : content) {
            JsonObject block = e.getAsJsonObject();
            if (!GuideText.string(block, "type").equals("tool_use")) continue;
            String name = GuideText.string(block, "name");
            JsonObject input = block.has("input") && block.get("input").isJsonObject() ? block.getAsJsonObject("input") : new JsonObject();
            streamer.status(name);
            String result;
            byte[] picture = null;
            try {
                if (name.equals("draw_diagram")) {
                    // A copy: the check fills in any models the drawing holds, and the conversation keeps the input as sent.
                    JsonObject drawing = input.deepCopy();
                    List<String> earlier = List.copyOf(diagrams);
                    String problem = l.server.submit(() -> Diagrams.check(drawing, earlier)).get(10, TimeUnit.SECONDS);
                    if (problem == null && diagrams.size() >= MAX_DRAWINGS) problem = "Not drawn: " + MAX_DRAWINGS
                            + " drawings and models per answer is the limit.";
                    if (problem == null) {
                        diagrams.add(drawing.toString());
                        streamer.diagram(drawing.toString());
                        result = "Drawn as diagram " + diagrams.size() + ". Write [diagram " + diagrams.size()
                                + "] on a line of its own in your answer where it should appear.";
                    } else {
                        result = problem;
                    }
                } else if (name.equals("build_model")) {
                    Models.Result built = l.server.submit(() -> {
                        ServerPlayer player = l.server.getPlayerList().getPlayer(playerId);
                        PlacedBlocks placedBlocks = placed;
                        Models.Copier copier = player == null || placedBlocks == null ? null
                                : (a, b, max) -> Builds.copy(player, placedBlocks, views.get(playerId), a, b, max);
                        return Models.build(input, player != null ? player.serverLevel() : l.server.overworld(), copier);
                    }).get(10, TimeUnit.SECONDS);
                    if (built.problem() != null) {
                        result = built.problem();
                    } else if (built.draft()) {
                        picture = built.picture();
                        result = "Draft only, not in the answer. " + (picture == null ? "The pictures couldn't be drawn." :
                                "The picture shows it from the front-left and back-right corners (top row) and straight from "
                                + "the front and the right side (bottom row), in each block's shape and map colour, without "
                                + "textures. Check the shape, proportions and colours; fix what looks wrong and try again, or "
                                + "call with draft false to put it in the answer.")
                                + (built.check().isEmpty() ? "" : "\n" + built.check());
                    } else if (diagrams.size() >= MAX_DRAWINGS) {
                        result = "Not built: " + MAX_DRAWINGS + " drawings and models per answer is the limit.";
                    } else {
                        diagrams.add(built.model().toString());
                        streamer.diagram(built.model().toString());
                        result = "Built as diagram " + diagrams.size() + ". Write [diagram " + diagrams.size() + "] on a line of "
                                + "its own in your answer where it should appear, or draw it into a diagram with a shape of kind "
                                + "model and text \"" + diagrams.size() + "\"."
                                + (built.check().isEmpty() ? "" : "\n" + built.check());
                    }
                } else {
                    result = l.server.submit(() -> {
                        ServerPlayer player = l.server.getPlayerList().getPlayer(playerId);
                        return player == null ? "The player has left the server." : WorldTools.run(name, input, player, l.server);
                    }).get(10, TimeUnit.SECONDS);
                }
            } catch (TimeoutException ex) {
                result = "The server was too busy to look.";
            } catch (java.util.concurrent.ExecutionException ex) {
                result = "The tool failed on this server.";
            }
            JsonObject r = new JsonObject();
            r.addProperty("type", "tool_result");
            r.addProperty("tool_use_id", GuideText.string(block, "id"));
            if (picture == null) {
                r.addProperty("content", GuideAiNet.clip(result, 40000));
            } else {
                JsonArray parts = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", GuideAiNet.clip(result, 40000));
                parts.add(text);
                JsonObject image = new JsonObject(), source = new JsonObject();
                image.addProperty("type", "image");
                source.addProperty("type", "base64");
                source.addProperty("media_type", "image/png");
                source.addProperty("data", java.util.Base64.getEncoder().encodeToString(picture));
                image.add("source", source);
                parts.add(image);
                r.add("content", parts);
            }
            results.add(r);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", results);
        return user;
    }

    /** Token use and an estimate of what one question cost, for the server log. Never logs any text. */
    private static final class Usage {
        long input, cacheRead, cacheWrite5m, cacheWrite1h, output, searches;
        String model = "";

        void add(JsonObject response) {
            if (response.has("model")) model = GuideText.string(response, "model");
            if (!response.has("usage") || !response.get("usage").isJsonObject()) return;
            JsonObject u = response.getAsJsonObject("usage");
            input += num(u, "input_tokens");
            output += num(u, "output_tokens");
            cacheRead += num(u, "cache_read_input_tokens");
            if (u.has("cache_creation") && u.get("cache_creation").isJsonObject()) {
                JsonObject c = u.getAsJsonObject("cache_creation");
                cacheWrite5m += num(c, "ephemeral_5m_input_tokens");
                cacheWrite1h += num(c, "ephemeral_1h_input_tokens");
            } else {
                cacheWrite5m += num(u, "cache_creation_input_tokens");
            }
            if (u.has("server_tool_use") && u.get("server_tool_use").isJsonObject())
                searches += num(u.getAsJsonObject("server_tool_use"), "web_search_requests");
        }

        private static long num(JsonObject o, String key) {
            try {
                return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }

        void log(UUID player, int rounds, String stop) {
            // Dollars per million tokens: input, output, cache read, 5-minute write, 1-hour write.
            double[] p = model.contains("opus") ? new double[] {4, 20, 0.2, 5, 8}
                    : model.contains("sonnet") ? new double[] {2, 10, 0.1, 2.5, 4} : null;
            String cost = p == null ? "unknown" : String.format(Locale.ROOT, "$%.3f", (input * p[0] + output * p[1] + cacheRead * p[2]
                    + cacheWrite5m * p[3] + cacheWrite1h * p[4]) / 1e6 + searches * 0.01);
            LOG.info("Field Guide voice answered {} ({}, {} rounds, {}): input {}, cache read {}, cache write {}+{}, output {}, "
                    + "web searches {}, about {}", player, model, rounds, stop, input, cacheRead, cacheWrite5m, cacheWrite1h, output,
                    searches, cost);
        }
    }
}

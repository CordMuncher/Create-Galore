package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Each player's pages (conversation threads), one file per player in the world folder. A page keeps two things:
 * what the screen shows, and the exact messages sent to the API, assistant turns stored verbatim (thinking blocks
 * included) so a follow-up continues the same conversation. A new page starts with no messages at all, so it costs
 * only the shared cached guide plus its first question.
 *
 * Used only on the server thread.
 */
final class ThreadStore {
    private static final Logger LOG = LogUtils.getLogger();
    static final int MAX_PAGES = 50;
    /** After this many questions the book suggests a new page: every follow-up re-sends the whole page. */
    static final int SUGGEST_NEW_PAGE = 15;
    static final int MAX_QUESTIONS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Pages started before the book's 0.2.22 layout (formatted answers, recipes, storage, 3D models) are edition 1. */
    static final int EDITION = 2;
    /** A conversation, or a page that keeps one saved drawing or model. */
    static final int KIND_CHAT = 0, KIND_DRAWING = 1, KIND_MODEL = 2;
    static final int MAX_SAVED = 50;

    static final class Page {
        final String id;
        int edition = EDITION;
        int kind = KIND_CHAT;
        String title;
        long updated;
        int questions;
        /** An answer came while the page wasn't open; cleared when it is opened. */
        boolean unread;
        final List<GuideAiNet.Entry> entries = new ArrayList<>();
        JsonArray messages = new JsonArray();

        Page(String id) {
            this.id = id;
        }
    }

    static final class Book {
        final List<Page> pages = new ArrayList<>();
        String day = "";
        int askedToday;

        Page page(String id) {
            for (Page p : pages) if (p.id.equals(id)) return p;
            return null;
        }
    }

    private final Path folder;
    private final Map<UUID, Book> books = new HashMap<>();

    ThreadStore(Path folder) {
        this.folder = folder;
    }

    Book book(UUID player) {
        return books.computeIfAbsent(player, this::load);
    }

    /** Saved drawings never fall out: when the book is full, the oldest conversation goes. */
    Page newPage(Book book, String title, int kind) {
        Page page = new Page(HexFormat.of().formatHex(randomBytes()));
        page.title = title;
        page.kind = kind;
        page.updated = System.currentTimeMillis();
        book.pages.add(0, page);
        for (int i = book.pages.size() - 1; i > 0 && chats(book) > MAX_PAGES; i--)
            if (book.pages.get(i).kind == KIND_CHAT) book.pages.remove(i);
        return page;
    }

    private static int chats(Book book) {
        int n = 0;
        for (Page p : book.pages) if (p.kind == KIND_CHAT) n++;
        return n;
    }

    static int saved(Book book) {
        return book.pages.size() - chats(book);
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return b;
    }

    /** Most recently used first. */
    static void touch(Book book, Page page) {
        page.updated = System.currentTimeMillis();
        book.pages.remove(page);
        book.pages.add(0, page);
    }

    void save(UUID player) {
        Book book = books.get(player);
        if (book == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("day", book.day);
        root.addProperty("asked_today", book.askedToday);
        JsonArray pages = new JsonArray();
        for (Page p : book.pages) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.id);
            o.addProperty("title", p.title);
            o.addProperty("edition", p.edition);
            o.addProperty("kind", p.kind);
            o.addProperty("updated", p.updated);
            o.addProperty("questions", p.questions);
            if (p.unread) o.addProperty("unread", true);
            JsonArray entries = new JsonArray();
            for (GuideAiNet.Entry e : p.entries) {
                JsonObject eo = new JsonObject();
                eo.addProperty("who", e.who());
                eo.addProperty("text", e.text());
                entries.add(eo);
            }
            o.add("entries", entries);
            o.add("messages", p.messages);
            pages.add(o);
        }
        root.add("pages", pages);
        try {
            Files.createDirectories(folder);
            Path file = file(player);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Field Guide voice: couldn't save the pages of {} ({})", player, e.toString());
        }
    }

    private Path file(UUID player) {
        return folder.resolve(player + ".json");
    }

    private Book load(UUID player) {
        Book book = new Book();
        Path file = file(player);
        if (!Files.isRegularFile(file)) return book;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            book.day = GuideText.string(root, "day");
            book.askedToday = root.has("asked_today") ? root.get("asked_today").getAsInt() : 0;
            for (JsonElement pe : root.getAsJsonArray("pages")) {
                JsonObject o = pe.getAsJsonObject();
                Page p = new Page(GuideText.string(o, "id"));
                p.title = GuideText.string(o, "title");
                p.edition = o.has("edition") ? o.get("edition").getAsInt() : 1;
                p.kind = o.has("kind") ? o.get("kind").getAsInt() : KIND_CHAT;
                p.updated = o.get("updated").getAsLong();
                p.questions = o.get("questions").getAsInt();
                p.unread = o.has("unread") && o.get("unread").getAsBoolean();
                for (JsonElement ee : o.getAsJsonArray("entries")) {
                    JsonObject eo = ee.getAsJsonObject();
                    p.entries.add(new GuideAiNet.Entry(eo.get("who").getAsInt(), GuideText.string(eo, "text")));
                }
                p.messages = o.getAsJsonArray("messages");
                if (!p.id.isEmpty()) book.pages.add(p);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Field Guide voice: couldn't read the pages of {} ({}); starting a fresh book for them", player, e.toString());
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Saving over it later is fine too.
            }
            return new Book();
        }
        return book;
    }
}

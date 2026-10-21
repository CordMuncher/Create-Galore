package dev.creategalore.bridge.guideai;

import dev.creategalore.bridge.CreateGaloreBridge;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The messages between the book screen and the server. Only questions go up and only page text comes down:
 * no message carries a setting, a model reply in raw form, or anything from the settings folder, so there is
 * nothing in the game protocol a client (or someone faking one) could ask for to learn the key.
 */
public final class GuideAiNet {
    static final int MAX_QUESTION = 500;
    private static final int MAX_TEXT = 32000;

    private GuideAiNet() {
    }

    /** AWAY: the player isn't looking at any page (a new blank page, or the book closed). */
    public static final int LIST = 0, OPEN = 1, ASK = 2, DELETE = 3, SAVE = 4, AWAY = 5;
    /** Page kinds: a conversation, or a page keeping one saved drawing or model. */
    public static final int KIND_CHAT = ThreadStore.KIND_CHAT, KIND_DRAWING = ThreadStore.KIND_DRAWING, KIND_MODEL = ThreadStore.KIND_MODEL;
    public static final int WHO_PLAYER = 0, WHO_BOOK = 1, WHO_NOTE = 2, WHO_DIAGRAM = 3;

    /**
     * Client to server: list my pages, open one, ask on one (empty id starts a new page), tear one out, or save a
     * drawing on a page as a page of its own (text: the drawing's {@link #drawingKey}). Every request carries the
     * player's field of view (degrees, vertical) and window aspect, so the book knows how much of the world was on
     * their screen when they asked. A question that starts a new page carries a ticket the screen made up, which comes
     * back on that page's view so the screen knows the new page is the one it asked on.
     */
    public record Request(int action, String pageId, String text, int fov, float aspect, String ticket) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("ask_request"));
        static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::action,
                ByteBufCodecs.stringUtf8(64), Request::pageId,
                ByteBufCodecs.stringUtf8(MAX_QUESTION * 4), Request::text,
                ByteBufCodecs.VAR_INT, Request::fov,
                ByteBufCodecs.FLOAT, Request::aspect,
                ByteBufCodecs.stringUtf8(64), Request::ticket,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** One page in the list: writing, the book is answering on it now; unread, an answer came that they haven't opened. */
    public record Summary(String id, String title, int questions, long updated, int kind, boolean writing, boolean unread) {
        private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64), TITLE = ByteBufCodecs.stringUtf8(256);
        // More fields than StreamCodec.composite takes, so written out.
        static final StreamCodec<ByteBuf, Summary> CODEC = StreamCodec.of((buf, s) -> {
            ID.encode(buf, s.id);
            TITLE.encode(buf, s.title);
            ByteBufCodecs.VAR_INT.encode(buf, s.questions);
            ByteBufCodecs.VAR_LONG.encode(buf, s.updated);
            ByteBufCodecs.VAR_INT.encode(buf, s.kind);
            buf.writeBoolean(s.writing);
            buf.writeBoolean(s.unread);
        }, buf -> new Summary(ID.decode(buf), TITLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean(), buf.readBoolean()));
    }

    /** Server to client: whether the voice is on here, a line to show if not, and the player's pages. */
    public record Pages(boolean enabled, String notice, List<Summary> pages) implements CustomPacketPayload {
        public static final Type<Pages> TYPE = new Type<>(id("ask_pages"));
        static final StreamCodec<ByteBuf, Pages> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Pages::enabled,
                ByteBufCodecs.stringUtf8(1024), Pages::notice,
                ByteBufCodecs.collection(ArrayList::new, Summary.CODEC, 256), Pages::pages,
                Pages::new);

        @Override
        public Type<Pages> type() {
            return TYPE;
        }
    }

    public record Entry(int who, String text) {
        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::who,
                ByteBufCodecs.stringUtf8(MAX_TEXT * 4), Entry::text,
                Entry::new);
    }

    /** Server to client: one page of conversation, whether the book is still writing on it, and the ticket of a new page. */
    public record View(String pageId, String title, List<Entry> entries, boolean pending, String notice, int kind, String ticket)
            implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(id("ask_view"));
        private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64), TITLE = ByteBufCodecs.stringUtf8(256),
                NOTICE = ByteBufCodecs.stringUtf8(1024);
        private static final StreamCodec<ByteBuf, List<Entry>> ENTRIES = ByteBufCodecs.collection(ArrayList::new, Entry.CODEC, 512);
        static final StreamCodec<ByteBuf, View> CODEC = StreamCodec.of((buf, v) -> {
            ID.encode(buf, v.pageId);
            TITLE.encode(buf, v.title);
            ENTRIES.encode(buf, v.entries);
            buf.writeBoolean(v.pending);
            NOTICE.encode(buf, v.notice);
            ByteBufCodecs.VAR_INT.encode(buf, v.kind);
            ID.encode(buf, v.ticket);
        }, buf -> new View(ID.decode(buf), TITLE.decode(buf), ENTRIES.decode(buf), buf.readBoolean(), NOTICE.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ID.decode(buf)));

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    /**
     * Server to client while the book writes: more text for the answer being written (reset: start over, a new round
     * after looking something up), what the book is doing (thinking, writing, or the tool it is using), and a drawing
     * when one is made.
     */
    public record Live(String pageId, boolean reset, String text, String status, String diagram) implements CustomPacketPayload {
        public static final Type<Live> TYPE = new Type<>(id("ask_live"));
        static final StreamCodec<ByteBuf, Live> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Live::pageId,
                ByteBufCodecs.BOOL, Live::reset,
                ByteBufCodecs.stringUtf8(MAX_TEXT * 4), Live::text,
                ByteBufCodecs.stringUtf8(64), Live::status,
                ByteBufCodecs.stringUtf8(MAX_TEXT * 4), Live::diagram,
                Live::new);

        @Override
        public Type<Live> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: the book finished an answer on this page (answered: false when it couldn't), sent wherever the
     * player is, so a closed book or another page can tell them.
     */
    public record Answered(String pageId, String title, boolean answered) implements CustomPacketPayload {
        public static final Type<Answered> TYPE = new Type<>(id("ask_answered"));
        static final StreamCodec<ByteBuf, Answered> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Answered::pageId,
                ByteBufCodecs.stringUtf8(256), Answered::title,
                ByteBufCodecs.BOOL, Answered::answered,
                Answered::new);

        @Override
        public Type<Answered> type() {
            return TYPE;
        }
    }

    /** Names a drawing on a page without sending it back up: both sides hash the same JSON text. */
    public static String drawingKey(String json) {
        return Integer.toHexString(json.hashCode()) + ":" + json.length();
    }

    static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String clipText(String s) {
        return clip(s, MAX_TEXT);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, path);
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client or server without this Bridge version still connects; the button just says so.
            // Version 2 (pack 0.2.22): page kinds and saving drawings. Version 3: field of view in requests, claim box.
            // Version 4: several pages written at once, unread pages, answer notices.
            var registrar = event.registrar("4").optional();
            registrar.playToServer(Request.TYPE, codec(Request.CODEC), (payload, context) -> GuideAi.handle(payload, context.player()));
            registrar.playToClient(Pages.TYPE, codec(Pages.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onPages(payload));
            registrar.playToClient(View.TYPE, codec(View.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onView(payload));
            registrar.playToClient(Live.TYPE, codec(Live.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onLive(payload));
            registrar.playToClient(Answered.TYPE, codec(Answered.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onAnswered(payload));
            registrar.playToClient(ClaimBox.TYPE, codec(ClaimBox.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.ClaimBoxView.show(payload));
        });
    }

    /**
     * Server to client: outline this box (an operator's /fieldguide builds add or remove) for so many ticks; 0 ticks
     * takes the outline away.
     */
    public record ClaimBox(net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max, boolean add, int ticks) implements CustomPacketPayload {
        public static final Type<ClaimBox> TYPE = new Type<>(id("claim_box"));
        static final StreamCodec<ByteBuf, ClaimBox> CODEC = StreamCodec.composite(
                net.minecraft.core.BlockPos.STREAM_CODEC, ClaimBox::min,
                net.minecraft.core.BlockPos.STREAM_CODEC, ClaimBox::max,
                ByteBufCodecs.BOOL, ClaimBox::add,
                ByteBufCodecs.VAR_INT, ClaimBox::ticks,
                ClaimBox::new);

        @Override
        public Type<ClaimBox> type() {
            return TYPE;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> codec(StreamCodec<ByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) (StreamCodec<?, T>) codec;
    }
}

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

    public static final int LIST = 0, OPEN = 1, ASK = 2, DELETE = 3, SAVE = 4;
    /** Page kinds: a conversation, or a page keeping one saved drawing or model. */
    public static final int KIND_CHAT = ThreadStore.KIND_CHAT, KIND_DRAWING = ThreadStore.KIND_DRAWING, KIND_MODEL = ThreadStore.KIND_MODEL;
    public static final int WHO_PLAYER = 0, WHO_BOOK = 1, WHO_NOTE = 2, WHO_DIAGRAM = 3;

    /**
     * Client to server: list my pages, open one, ask on one (empty id starts a new page), tear one out, or save a
     * drawing on a page as a page of its own (text: the drawing's {@link #drawingKey}). Every request carries the
     * player's field of view (degrees, vertical) and window aspect, so the book knows how much of the world was on
     * their screen when they asked.
     */
    public record Request(int action, String pageId, String text, int fov, float aspect) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("ask_request"));
        static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::action,
                ByteBufCodecs.stringUtf8(64), Request::pageId,
                ByteBufCodecs.stringUtf8(MAX_QUESTION * 4), Request::text,
                ByteBufCodecs.VAR_INT, Request::fov,
                ByteBufCodecs.FLOAT, Request::aspect,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record Summary(String id, String title, int questions, long updated, int kind) {
        static final StreamCodec<ByteBuf, Summary> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Summary::id,
                ByteBufCodecs.stringUtf8(256), Summary::title,
                ByteBufCodecs.VAR_INT, Summary::questions,
                ByteBufCodecs.VAR_LONG, Summary::updated,
                ByteBufCodecs.VAR_INT, Summary::kind,
                Summary::new);
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

    /** Server to client: one page of conversation, and whether the book is still writing on it. */
    public record View(String pageId, String title, List<Entry> entries, boolean pending, String notice, int kind)
            implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(id("ask_view"));
        static final StreamCodec<ByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), View::pageId,
                ByteBufCodecs.stringUtf8(256), View::title,
                ByteBufCodecs.collection(ArrayList::new, Entry.CODEC, 512), View::entries,
                ByteBufCodecs.BOOL, View::pending,
                ByteBufCodecs.stringUtf8(1024), View::notice,
                ByteBufCodecs.VAR_INT, View::kind,
                View::new);

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
            var registrar = event.registrar("3").optional();
            registrar.playToServer(Request.TYPE, codec(Request.CODEC), (payload, context) -> GuideAi.handle(payload, context.player()));
            registrar.playToClient(Pages.TYPE, codec(Pages.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onPages(payload));
            registrar.playToClient(View.TYPE, codec(View.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onView(payload));
            registrar.playToClient(Live.TYPE, codec(Live.CODEC),
                    (payload, context) -> dev.creategalore.bridge.client.GuideAiClient.onLive(payload));
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

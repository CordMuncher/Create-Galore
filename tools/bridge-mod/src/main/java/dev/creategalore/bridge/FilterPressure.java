package dev.creategalore.bridge;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.IFilterFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Filter gauge: what Engineer's Goggles show on a filter frame. The server reads the space under the frame stack the
 * same way {@link ExhaustPressure} does (a sealed room presses smog up into the frames in its ceiling) and sends back
 * one reading: green when the filters keep up, yellow when smog is pressing on them, red when a sealed room is close to
 * full or the top of the stack is closed, which is when a machine making smog in that room can blow (it explodes once
 * its smog has nowhere left to go, see {@link FanSmeltingSmog} and Pollution of the Realms' enclosed emitters).
 */
public final class FilterPressure {
    private FilterPressure() {}

    /** How far from the player a frame may be read (blocks). */
    static final double RANGE = 24;
    /** A sealed room this full of smog (of what it can hold) presses on its filters. */
    public static final float PRESSURE_FULLNESS = 0.25f;
    /** A sealed room this full is close to the point where machines in it have nowhere to put their smog. */
    public static final float OVER_FULLNESS = 0.6f;
    /** Smog units one cell can hold (three levels of thickness). */
    private static final int UNITS_PER_CELL = 3;
    /** Flues (pumps, chimneys) followed down from the bottom frame to the room it drains. */
    private static final int FLUE_REACH = 16;

    private static final Map<UUID, Long> lastRead = new HashMap<>();

    /** What the space under a filter is doing. Ordered from calm to dangerous. */
    public enum State {
        /** Nothing under the frame to drain: it only catches smog that drifts into it. */
        NO_ROOM,
        /** Open space below (outdoors or too big to count as a room): smog drifts in, nothing builds up. */
        OPEN,
        /** Open space below, but the top of the stack is closed, so it filters nothing. */
        OPEN_BLOCKED,
        /** Sealed room with no smog in it. */
        CLEAN,
        /** Sealed room, the filters keep up. */
        WORKING,
        /** Sealed room pressing smog into the filters (filling up or the filters running flat out). */
        PRESSURE,
        /** Sealed room close to full: machines making smog in it can blow. */
        OVER_FULL,
        /** Sealed room with smog in it while the top of the stack is closed: nothing gets out, it only fills up. */
        OVER_BLOCKED;

        /** 0 green, 1 yellow, 2 red. */
        public int level() {
            return switch (this) {
                case NO_ROOM, OPEN, CLEAN, WORKING -> 0;
                case OPEN_BLOCKED, PRESSURE -> 1;
                case OVER_FULL, OVER_BLOCKED -> 2;
            };
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client or server without this Bridge version still connects, the goggles just show nothing.
            var registrar = event.registrar("1").optional();
            registrar.playToServer(Request.TYPE, Request.CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) handle(player, payload.frame());
            });
            registrar.playToClient(Reading.TYPE, Reading.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.FilterGoggles.onReading(payload));
        });
    }

    private static void handle(ServerPlayer player, BlockPos frame) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = lastRead.get(player.getUUID());
        // The client asks about twice a second while the goggles rest on a frame; anything faster is ignored.
        if (last != null && now - last < 5 && now >= last) return;
        lastRead.put(player.getUUID(), now);
        if (lastRead.size() > 256) lastRead.clear();
        if (player.distanceToSqr(frame.getCenter()) > RANGE * RANGE || !level.isLoaded(frame)) return;
        Reading reading = read(level, frame);
        if (reading != null) PacketDistributor.sendToPlayer(player, reading);
    }

    /** Reads the frame stack holding {@code frame} and the space under it, or null when there is no frame there. */
    public static Reading read(ServerLevel level, BlockPos frame) {
        if (!(level.getBlockState(frame).getBlock() instanceof IFilterFrame)) return null;
        BlockPos bottom = frame;
        while (level.getBlockState(bottom.below()).getBlock() instanceof IFilterFrame) bottom = bottom.below();
        // The whole stack shares one room and one top, so its frames add up.
        int frames = 0;
        int rate = 0;
        int used = 0;
        long second = level.getGameTime() / 20;
        for (BlockPos cursor = bottom; ; cursor = cursor.above()) {
            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof IFilterFrame)) break;
            frames++;
            int frameRate = state.getBlock() instanceof FilterUpkeep.Rated rated ? rated.create_galore_bridge$rate() : 0;
            rate += frameRate;
            BlockEntity be = level.getBlockEntity(cursor);
            if (be instanceof FilterUpkeep.Budget budget) {
                // Last second's use is the whole story; this second's only counts once it is more than that.
                long when = budget.create_galore_bridge$second();
                if (when == second || when == second - 1) used += Math.min(frameRate, budget.create_galore_bridge$used());
            }
        }
        boolean outlet = Exhaust.hasOutlet(level, bottom);

        BlockPos start = bottom.below();
        for (int i = 0; i < FLUE_REACH && Exhaust.isFlue(level.getBlockState(start)); i++) start = start.below();
        if (!level.isLoaded(start) || !Exhaust.isVolume(level.getBlockState(start))) {
            return new Reading(frame, State.NO_ROOM, outlet, frames, rate, used, 0, 0, 0);
        }
        Exhaust.Pocket pocket = Exhaust.Pocket.scan(level, start, Exhaust.POCKET_LIMIT);
        if (pocket.open) {
            return new Reading(frame, outlet ? State.OPEN : State.OPEN_BLOCKED, outlet, frames, rate, used, 0, 0, 0);
        }
        int units = 0;
        int cells = 0;
        for (long packed : pocket.cells) {
            BlockState state = level.getBlockState(BlockPos.of(packed));
            if (!Exhaust.isVolume(state)) continue;
            cells++;
            if (state.getBlock() instanceof Pollutant<?> pollutant) units += pollutant.getCarriedPollutionAmount(state);
        }
        int room = cells * UNITS_PER_CELL;
        return new Reading(frame, judge(units, room, outlet, used >= rate && rate > 0), outlet, frames, rate, used, units, room, cells);
    }

    /** The sealed-room part of the reading. */
    static State judge(int units, int room, boolean outlet, boolean flatOut) {
        if (units <= 0) return State.CLEAN;
        if (!outlet) return State.OVER_BLOCKED;
        float fullness = room > 0 ? units / (float) room : 1f;
        if (fullness >= OVER_FULLNESS) return State.OVER_FULL;
        if (fullness >= PRESSURE_FULLNESS || flatOut) return State.PRESSURE;
        return State.WORKING;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_galore_bridge", path);
    }

    /** Client to server: read this filter frame. */
    public record Request(BlockPos frame) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("filter_gauge_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::frame, Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: one reading of a frame stack. {@code rate} and {@code used} are smog units a second for the
     * whole stack; {@code units}, {@code room} and {@code cells} describe the sealed room under it (zero otherwise).
     */
    public record Reading(BlockPos frame, State state, boolean outlet, int frames, int rate, int used, int units, int room, int cells)
            implements CustomPacketPayload {
        public static final Type<Reading> TYPE = new Type<>(id("filter_gauge_reading"));
        static final StreamCodec<RegistryFriendlyByteBuf, Reading> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeBlockPos(r.frame);
                    buf.writeVarInt(r.state.ordinal());
                    buf.writeBoolean(r.outlet);
                    buf.writeVarInt(r.frames);
                    buf.writeVarInt(r.rate);
                    buf.writeVarInt(r.used);
                    buf.writeVarInt(r.units);
                    buf.writeVarInt(r.room);
                    buf.writeVarInt(r.cells);
                },
                buf -> {
                    BlockPos frame = buf.readBlockPos();
                    State[] states = State.values();
                    int ordinal = buf.readVarInt();
                    State state = ordinal >= 0 && ordinal < states.length ? states[ordinal] : State.NO_ROOM;
                    return new Reading(frame, state, buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public Type<Reading> type() {
            return TYPE;
        }
    }
}

package dev.creategalore.bridge;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Glue check: shows, before a ship is assembled, which blocks the Physics Assembler will take and which blocks inside
 * the glue stay behind. The assembler walks from the block it faces to every block touching a taken one by a face or an
 * edge inside the ship's glue (every box it reaches plus the boxes overlapping or touching those, see
 * SimAssemblyWholeGlueMixin). A block touching the rest only at a corner or not at all, a box that misses the ship, and
 * unbreakable blocks stay behind. The server runs that same search without assembling anything and sends both sets to
 * the player holding glue, so a box drawn into the seabed or a loose block shows before the lever is pulled.
 */
public final class ShipGlueCheck {
    /** How far from the player an assembler may be checked (blocks). */
    static final double RANGE = 48;
    /** Ships bigger than this only send the blocks that stay behind (the outline of the rest is not worth the bytes). */
    public static final int MAX_SENT = 20000;
    private static final Map<UUID, Long> lastCheck = new HashMap<>();

    private ShipGlueCheck() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client or server without this Bridge version still connects, it just shows no check.
            var registrar = event.registrar("1").optional();
            registrar.playToServer(Request.TYPE, Request.CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) handle(player, payload.assembler());
            });
            registrar.playToClient(Result.TYPE, Result.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.ShipGlueView.onResult(payload));
        });
    }

    private static void handle(ServerPlayer player, BlockPos assembler) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = lastCheck.get(player.getUUID());
        // The client asks about once a second while glue is in hand; anything faster is ignored.
        if (last != null && now - last < 10 && now >= last) return;
        lastCheck.put(player.getUUID(), now);
        if (player.distanceToSqr(assembler.getCenter()) > RANGE * RANGE || !level.isLoaded(assembler)) return;
        PacketDistributor.sendToPlayer(player, check(level, assembler));
    }

    /** Runs the assembler's own search on the world as it stands. Nothing moves and no glue is used up. */
    public static Result check(ServerLevel level, BlockPos assembler) {
        BlockState state = level.getBlockState(assembler);
        if (!(state.getBlock() instanceof PhysicsAssemblerBlock)) return new Result(assembler, List.of(), List.of(), Component.empty(), false, BlockPos.ZERO);
        BlockPos start = assembler.relative(PhysicsAssemblerBlock.getStickyFacing(state));
        // The same call the assembler makes: start from the block it faces, take the assembler too, use all glue.
        SimAssemblyContraption search = new SimAssemblyContraption(null, false);
        Component problem = Component.empty();
        boolean hasProblemPos = false;
        BlockPos problemPos = BlockPos.ZERO;
        if (level.getBlockState(start).isAir()) {
            problem = Component.translatable("create_galore_bridge.glue_check.nothing_attached");
        } else {
            try {
                search.searchMovedStructure(level, start);
            } catch (AssemblyException e) {
                problem = e.component;
                if (e.hasPosition()) {
                    hasProblemPos = true;
                    problemPos = e.getPosition();
                }
            }
        }
        Set<BlockPos> joined = new HashSet<>(search.getBlocks());
        Set<BlockPos> loose = new java.util.LinkedHashSet<>();
        for (AABB box : glueBoxes(level, joined)) {
            BlockPos.betweenClosedStream(first(box), last(box)).forEach(pos -> {
                if (joined.contains(pos) || loose.size() >= MAX_SENT) return;
                BlockState s = level.getBlockState(pos);
                // Plain water and lava never go aboard (SimAssemblyNoFluidsMixin), and air is nothing to show.
                // An unbreakable block inside the ship's glue does show: it stays behind.
                if (s.isAir() || s.getBlock() instanceof LiquidBlock) return;
                loose.add(pos.immutable());
            });
        }
        List<BlockPos> sent = joined.size() <= MAX_SENT ? new ArrayList<>(joined) : List.of();
        return new Result(assembler, sent, new ArrayList<>(loose), problem, hasProblemPos, problemPos);
    }

    /**
     * The glue boxes worth showing: every Super Glue or Honey Glue box holding a block the search took, and every box
     * within two blocks of one of those (a box that misses the ship by a block is the usual way part of a ship gets left
     * behind, so its blocks must show up too).
     */
    static List<AABB> glueBoxes(ServerLevel level, Set<BlockPos> joined) {
        List<AABB> result = new ArrayList<>();
        if (joined.isEmpty()) return result;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : joined) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB area = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(32);
        List<AABB> candidates = new ArrayList<>();
        for (Entity glue : level.getEntitiesOfClass(Entity.class, area, e -> e instanceof SuperGlueEntity || e instanceof HoneyGlueEntity)) {
            candidates.add(glue.getBoundingBox());
        }
        for (AABB box : candidates) {
            if (holdsAny(box, joined)) result.add(box);
        }
        // Boxes near the ship's boxes, and boxes near those.
        boolean grew = !result.isEmpty();
        while (grew) {
            grew = false;
            for (AABB box : candidates) {
                if (result.contains(box)) continue;
                AABB touch = box.inflate(2);
                for (AABB own : result) {
                    if (touch.intersects(own)) {
                        result.add(box);
                        grew = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static boolean holdsAny(AABB box, Set<BlockPos> joined) {
        BlockPos min = first(box), max = last(box);
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > joined.size()) {
            for (BlockPos pos : joined) {
                if (box.contains(pos.getCenter())) return true;
            }
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (joined.contains(pos)) return true;
        }
        return false;
    }

    /** Glue holds a block when the block's centre is inside the box: the lowest such block corner of a box... */
    private static BlockPos first(AABB box) {
        return new BlockPos((int) Math.ceil(box.minX - 0.5), (int) Math.ceil(box.minY - 0.5), (int) Math.ceil(box.minZ - 0.5));
    }

    /** ...and the highest. */
    private static BlockPos last(AABB box) {
        return new BlockPos((int) Math.ceil(box.maxX - 0.5) - 1, (int) Math.ceil(box.maxY - 0.5) - 1, (int) Math.ceil(box.maxZ - 0.5) - 1);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, path);
    }

    /** Client to server: check the ship this Physics Assembler would make. */
    public record Request(BlockPos assembler) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("glue_check_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::assembler, Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: the blocks that go aboard (empty when the ship is too big to send) and the blocks inside the
     * ship's glue that stay behind, plus why the assembler would refuse, if it would (and where, if it says).
     */
    public record Result(BlockPos assembler, List<BlockPos> joined, List<BlockPos> loose, Component problem, boolean hasProblemPos,
                         BlockPos problemPos) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(id("glue_check_result"));
        static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeBlockPos(r.assembler);
                    writePositions(buf, r.joined);
                    writePositions(buf, r.loose);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.problem);
                    buf.writeBoolean(r.hasProblemPos);
                    buf.writeBlockPos(r.problemPos);
                },
                buf -> new Result(buf.readBlockPos(), readPositions(buf), readPositions(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBlockPos()));

        private static void writePositions(RegistryFriendlyByteBuf buf, List<BlockPos> list) {
            buf.writeVarInt(list.size());
            for (BlockPos pos : list) buf.writeLong(pos.asLong());
        }

        private static List<BlockPos> readPositions(RegistryFriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), MAX_SENT);
            List<BlockPos> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(BlockPos.of(buf.readLong()));
            return list;
        }

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }
}

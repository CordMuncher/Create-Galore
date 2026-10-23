package dev.creategalore.bridge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pinned schematics: a placed Create Schematic left standing in the world, so it shows without holding it and every
 * player on the server sees it. The server keeps the blocks with the world (the Schematic Table's upload is read once,
 * when pinning) and sends them to everyone; each client draws what is still to place as faint blocks and tints a wrong
 * block red ({@code client/PinnedSchematicView}). Pinned with {@code /schematic pin} or the Pin button on the
 * Schematic's placement screen (sneak-right-click it), taken down with {@code /schematic unpin} or the Unpin button.
 */
public final class PinnedSchematics {
    /** Bigger builds would not fit one packet; the Schematicannon is the tool for those. */
    public static final int MAX_BLOCKS = 120_000;
    static final int MAX_PINS = 64;

    private PinnedSchematics() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a client without this Bridge version still joins, it just shows no pinned schematics.
            var registrar = event.registrar("1").optional();
            registrar.playToClient(Added.TYPE, Added.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.PinnedSchematicView.onAdded(payload.pin()));
            registrar.playToClient(Removed.TYPE, Removed.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.PinnedSchematicView.onRemoved(payload.id()));
        });
        NeoForge.EVENT_BUS.addListener(PinnedSchematics::onCommands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
        });
    }

    /** Every pin to a player who just joined (pins of every dimension; the client shows the ones where it is). */
    private static void sendAll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Removed(-1));
        for (Pin pin : Pins.get(player.server).pins.values()) PacketDistributor.sendToPlayer(player, new Added(pin));
    }

    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("schematic")
                .then(Commands.literal("pin").executes(ctx -> pin(ctx.getSource())))
                .then(Commands.literal("unpin")
                        .executes(ctx -> unpinNearest(ctx.getSource()))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> unpin(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))));
    }

    /** The placed Schematic in the player's hand (main hand first), or empty. */
    private static ItemStack heldSchematic(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (AllItems.SCHEMATIC.isIn(stack) && stack.has(AllDataComponents.SCHEMATIC_FILE)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Where a Schematic was placed and how, so the same placement isn't pinned twice. */
    public static String placementKey(ItemStack stack, ResourceLocation dim) {
        return stack.get(AllDataComponents.SCHEMATIC_OWNER) + "/" + stack.get(AllDataComponents.SCHEMATIC_FILE) + "@"
                + stack.get(AllDataComponents.SCHEMATIC_ANCHOR) + " " + stack.get(AllDataComponents.SCHEMATIC_ROTATION) + " "
                + stack.get(AllDataComponents.SCHEMATIC_MIRROR) + " " + dim;
    }

    static int pin(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = heldSchematic(player);
        if (stack.isEmpty()) return fail(source, "Hold a Schematic to pin it (one made at a Schematic Table)");
        if (!stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false))
            return fail(source, "Place the Schematic first: right-click with it where the build goes");
        ServerLevel level = player.serverLevel();
        Pins pins = Pins.get(player.server);
        String key = placementKey(stack, level.dimension().location());
        for (Pin p : pins.pins.values()) if (p.key.equals(key)) return fail(source, "This Schematic is already pinned right here (#" + p.id + ")");
        if (pins.pins.size() >= MAX_PINS) return fail(source, "There are already " + MAX_PINS + " pinned schematics; unpin one first");
        StructureTemplate template = SchematicItem.loadSchematic(level, stack);
        if (template.getSize().equals(Vec3i.ZERO))
            return fail(source, "This server doesn't have that Schematic's file; make the Schematic again at a Schematic Table here");
        Map<BlockPos, BlockState> blocks = blocks(level, template, SchematicItem.getSettings(stack, false));
        if (blocks.isEmpty()) return fail(source, "That Schematic has no blocks in it");
        if (blocks.size() > MAX_BLOCKS)
            return fail(source, "That build has " + blocks.size() + " blocks; up to " + MAX_BLOCKS + " can be pinned (a Schematicannon prints any size)");
        String file = stack.get(AllDataComponents.SCHEMATIC_FILE);
        Pin pin = new Pin(pins.nextId++, level.dimension(), stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO),
                file.endsWith(".nbt") ? file.substring(0, file.length() - 4) : file, player.getGameProfile().getName(), key, blocks);
        pins.pins.put(pin.id, pin);
        pins.setDirty();
        PacketDistributor.sendToAllPlayers(new Added(pin));
        tellAll(player.server, player.getGameProfile().getName() + " pinned the schematic " + pin.name + " (#" + pin.id + ") at "
                + where(pin) + ": everyone sees it now");
        return 1;
    }

    /** The build placed at (0, 0, 0), turned and mirrored as the Schematic says, without air. */
    static Map<BlockPos, BlockState> blocks(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings) {
        SchematicLevel scratch = new SchematicLevel(level);
        template.placeInWorld(scratch, BlockPos.ZERO, BlockPos.ZERO, settings, scratch.getRandom(), Block.UPDATE_CLIENTS);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        scratch.getBlockMap().forEach((pos, state) -> {
            if (!state.isAir()) blocks.put(pos.immutable(), state);
        });
        return blocks;
    }

    private static int unpinNearest(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Pin best = null;
        double bestDist = 24 * 24;
        for (Pin p : Pins.get(player.server).pins.values()) {
            if (p.dim != player.level().dimension()) continue;
            double d = p.distanceSqr(player.blockPosition());
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null) return fail(source, "No pinned schematic near you; /schematic list shows them all with their number");
        return unpin(source, best.id);
    }

    static int unpin(CommandSourceStack source, int id) {
        Pins pins = Pins.get(source.getServer());
        Pin pin = pins.pins.remove(id);
        if (pin == null) {
            source.sendFailure(Component.literal("There is no pinned schematic #" + id + "; /schematic list shows them"));
            return 0;
        }
        pins.setDirty();
        PacketDistributor.sendToAllPlayers(new Removed(id));
        tellAll(source.getServer(), source.getTextName() + " unpinned the schematic " + pin.name + " (#" + id + ") at " + where(pin));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        Map<Integer, Pin> pins = Pins.get(source.getServer()).pins;
        if (pins.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No schematics are pinned. Hold a placed Schematic and use /schematic pin"), false);
            return 0;
        }
        StringBuilder out = new StringBuilder("Pinned schematics:");
        for (Pin p : pins.values())
            out.append("\n#").append(p.id).append(' ').append(p.name).append(" at ").append(where(p)).append(", by ").append(p.by)
                    .append(", ").append(p.blocks.size()).append(" blocks");
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return pins.size();
    }

    private static String where(Pin pin) {
        String dim = pin.dim == Level.OVERWORLD ? "" : " in " + pin.dim.location().getPath().replace('_', ' ');
        return pin.anchor.getX() + " " + pin.anchor.getY() + " " + pin.anchor.getZ() + dim;
    }

    private static int fail(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
        return 0;
    }

    private static void tellAll(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    /**
     * One pinned schematic: where it stands, who pinned it, and its blocks relative to the anchor (turned and mirrored
     * as placed). {@code key} is the Schematic placement it came from ({@link #placementKey}).
     */
    public record Pin(int id, ResourceKey<Level> dim, BlockPos anchor, String name, String by, String key,
                      Map<BlockPos, BlockState> blocks) {
        /** The build's box, relative to the anchor. */
        public BoundingBox box() {
            return BoundingBox.encapsulatingPositions(blocks.keySet()).orElse(new BoundingBox(BlockPos.ZERO));
        }

        /** Squared distance from a spot to the build's box (0 inside it). */
        public double distanceSqr(BlockPos pos) {
            return distanceSqr(box(), anchor, pos);
        }

        public static double distanceSqr(BoundingBox box, BlockPos anchor, BlockPos pos) {
            double dx = gap(pos.getX() - anchor.getX(), box.minX(), box.maxX()), dy = gap(pos.getY() - anchor.getY(), box.minY(), box.maxY()),
                    dz = gap(pos.getZ() - anchor.getZ(), box.minZ(), box.maxZ());
            return dx * dx + dy * dy + dz * dz;
        }

        private static double gap(int v, int min, int max) {
            return v < min ? min - v : v > max ? v - max : 0;
        }

        /** Blocks go as a palette of block state ids plus small zigzag numbers, about 5 bytes a block. */
        static final StreamCodec<RegistryFriendlyByteBuf, Pin> CODEC = StreamCodec.of((buf, pin) -> {
            buf.writeVarInt(pin.id);
            buf.writeResourceLocation(pin.dim.location());
            buf.writeBlockPos(pin.anchor);
            buf.writeUtf(pin.name);
            buf.writeUtf(pin.by);
            buf.writeUtf(pin.key);
            Map<BlockState, Integer> palette = new LinkedHashMap<>();
            for (BlockState s : pin.blocks.values()) palette.putIfAbsent(s, palette.size());
            buf.writeVarInt(palette.size());
            for (BlockState s : palette.keySet()) buf.writeVarInt(Block.getId(s));
            buf.writeVarInt(pin.blocks.size());
            pin.blocks.forEach((pos, state) -> {
                buf.writeVarInt(zig(pos.getX()));
                buf.writeVarInt(zig(pos.getY()));
                buf.writeVarInt(zig(pos.getZ()));
                buf.writeVarInt(palette.get(state));
            });
        }, buf -> {
            int id = buf.readVarInt();
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            BlockPos anchor = buf.readBlockPos();
            String name = buf.readUtf(), by = buf.readUtf(), key = buf.readUtf();
            int n = buf.readVarInt();
            BlockState[] palette = new BlockState[n];
            for (int i = 0; i < n; i++) palette[i] = Block.stateById(buf.readVarInt());
            int count = Math.min(buf.readVarInt(), MAX_BLOCKS);
            Map<BlockPos, BlockState> blocks = new HashMap<>(count * 2);
            for (int i = 0; i < count; i++) {
                BlockPos pos = new BlockPos(unzig(buf.readVarInt()), unzig(buf.readVarInt()), unzig(buf.readVarInt()));
                blocks.put(pos, palette[buf.readVarInt()]);
            }
            return new Pin(id, dim, anchor, name, by, key, blocks);
        });

        private static int zig(int v) {
            return (v << 1) ^ (v >> 31);
        }

        private static int unzig(int v) {
            return (v >>> 1) ^ -(v & 1);
        }
    }

    /** Server to client: a pin to show (or show again). */
    public record Added(Pin pin) implements CustomPacketPayload {
        public static final Type<Added> TYPE = new Type<>(id("pinned_schematic"));
        static final StreamCodec<RegistryFriendlyByteBuf, Added> CODEC = Pin.CODEC.map(Added::new, Added::pin);

        @Override
        public Type<Added> type() {
            return TYPE;
        }
    }

    /** Server to client: a pin taken down (-1: forget them all, a full list follows). */
    public record Removed(int id) implements CustomPacketPayload {
        public static final Type<Removed> TYPE = new Type<>(PinnedSchematics.id("unpinned_schematic"));
        static final StreamCodec<RegistryFriendlyByteBuf, Removed> CODEC = StreamCodec.of(
                (buf, r) -> buf.writeVarInt(r.id + 1), buf -> new Removed(buf.readVarInt() - 1));

        @Override
        public Type<Removed> type() {
            return TYPE;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, path);
    }

    /** Every pin, kept with the world. */
    private static final class Pins extends SavedData {
        private static final String NAME = "create_galore_pinned_schematics";
        final Map<Integer, Pin> pins = new LinkedHashMap<>();
        int nextId = 1;

        static Pins get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Pins::new, Pins::load, null), NAME);
        }

        private static Pins load(CompoundTag tag, HolderLookup.Provider registries) {
            Pins data = new Pins();
            data.nextId = Math.max(1, tag.getInt("next"));
            var blockLookup = registries.lookupOrThrow(Registries.BLOCK);
            for (Tag t : tag.getList("pins", Tag.TAG_COMPOUND)) {
                CompoundTag p = (CompoundTag) t;
                List<BlockState> palette = new ArrayList<>();
                for (Tag s : p.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(blockLookup, (CompoundTag) s));
                int[] packed = p.getIntArray("blocks");
                Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
                for (int i = 0; i + 3 < packed.length; i += 4)
                    blocks.put(new BlockPos(packed[i], packed[i + 1], packed[i + 2]), palette.get(packed[i + 3]));
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(p.getString("dim")));
                Pin pin = new Pin(p.getInt("id"), dim, NbtUtils.readBlockPos(p, "anchor").orElse(BlockPos.ZERO), p.getString("name"),
                        p.getString("by"), p.getString("key"), blocks);
                data.pins.put(pin.id, pin);
                data.nextId = Math.max(data.nextId, pin.id + 1);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Pin pin : pins.values()) {
                CompoundTag p = new CompoundTag();
                p.putInt("id", pin.id);
                p.putString("dim", pin.dim.location().toString());
                p.put("anchor", NbtUtils.writeBlockPos(pin.anchor));
                p.putString("name", pin.name);
                p.putString("by", pin.by);
                p.putString("key", pin.key);
                Map<BlockState, Integer> palette = new LinkedHashMap<>();
                ListTag paletteTag = new ListTag();
                int[] packed = new int[pin.blocks.size() * 4];
                int i = 0;
                for (Map.Entry<BlockPos, BlockState> e : pin.blocks.entrySet()) {
                    Integer index = palette.get(e.getValue());
                    if (index == null) {
                        index = palette.size();
                        palette.put(e.getValue(), index);
                        paletteTag.add(NbtUtils.writeBlockState(e.getValue()));
                    }
                    packed[i++] = e.getKey().getX();
                    packed[i++] = e.getKey().getY();
                    packed[i++] = e.getKey().getZ();
                    packed[i++] = index;
                }
                p.put("palette", paletteTag);
                p.putIntArray("blocks", packed);
                list.add(p);
            }
            tag.put("pins", list);
            tag.putInt("next", nextId);
            return tag;
        }
    }
}

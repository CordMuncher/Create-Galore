package dev.creategalore.bridge;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.CreatePaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * A Schematic's build travels with the paper. Create keeps the build only as a file: the server reads it from the
 * uploads of the player who made the paper, each client from its own schematics folder by file name. So a Schematic
 * handed to another player showed nothing for them (or their own, different file of the same name), and its maker
 * uploading another build under that name, or Create clearing old uploads, changed or emptied every paper made from it.
 * <p>
 * Now the server copies the build into the world the moment the paper is written (Schematic Table or Schematic and
 * Quill), into {@code create_galore/schematics/<hash>.nbt}, and marks the paper with that hash. It loads the paper's
 * build from that copy, and sends it to a client holding the paper when the client asks
 * ({@code client/SharedSchematicCache}). A paper from before this carries no hash: the server sends the upload it names
 * and marks it then.
 */
public final class SharedSchematics {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The paper's mark, in its custom data: the hash of the build's file. */
    static final String TAG = "create_galore_schematic";
    /** Payloads stay well under the 1 MiB a server can send at once. */
    private static final int CHUNK = 512 * 1024;
    public static final int MAX_BYTES = 64 * 1024 * 1024;

    private SharedSchematics() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional both ways: without the Bridge on one side, schematics work as Create alone does.
            var registrar = event.registrar("1").optional();
            registrar.playToServer(Request.TYPE, Request.CODEC, SharedSchematics::onRequest);
            registrar.playToClient(Part.TYPE, Part.CODEC,
                    (payload, context) -> dev.creategalore.bridge.client.SharedSchematicCache.onPart(payload));
        });
    }

    /**
     * What a client asks for to get a paper's build: its hash, or for a paper without one, its maker and file name.
     * Null when it isn't a written Schematic.
     */
    public static String key(ItemStack stack) {
        if (!AllItems.SCHEMATIC.isIn(stack)) return null;
        String hash = hash(stack);
        if (hash != null) return hash;
        String owner = stack.get(AllDataComponents.SCHEMATIC_OWNER), file = stack.get(AllDataComponents.SCHEMATIC_FILE);
        return owner == null || file == null || !file.endsWith(".nbt") ? null : owner + "/" + file;
    }

    static String hash(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG)) return null;
        String hash = data.copyTag().getString(TAG);
        return hash.matches("[0-9a-f]{40}") ? hash : null;
    }

    /** Copies the build a just-written paper names into the world and marks the paper (server side). */
    public static void stamp(Level level, ItemStack stack) {
        MinecraftServer server = level.getServer();
        if (level.isClientSide || server == null || hash(stack) != null) return;
        Path upload = upload(stack);
        if (upload == null || !Files.isRegularFile(upload)) return;
        try {
            byte[] bytes = Files.readAllBytes(upload);
            String hash = store(server, bytes);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG, hash));
        } catch (IOException e) {
            LOGGER.warn("Couldn't keep a copy of the schematic {}", upload, e);
        }
    }

    /** The file Create reads a paper's build from on the server, or null for a name that leaves the uploads folder. */
    private static Path upload(ItemStack stack) {
        String owner = stack.get(AllDataComponents.SCHEMATIC_OWNER), file = stack.get(AllDataComponents.SCHEMATIC_FILE);
        if (owner == null || file == null || !file.endsWith(".nbt")) return null;
        Path dir = CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize();
        Path path = dir.resolve(owner).resolve(file).normalize();
        return path.startsWith(dir) ? path : null;
    }

    private static Path storeDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("create_galore").resolve("schematics");
    }

    /** Keeps the bytes under their hash (once; the same build written twice is kept once) and gives the hash. */
    private static String store(MinecraftServer server, byte[] bytes) throws IOException {
        String hash = sha1(bytes);
        Path dir = storeDir(server), file = dir.resolve(hash + ".nbt");
        if (!Files.isRegularFile(file)) {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(hash + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        return hash;
    }

    private static byte[] stored(MinecraftServer server, String hash) {
        Path file = storeDir(server).resolve(hash + ".nbt");
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            LOGGER.warn("Couldn't read the kept schematic {}", file, e);
            return null;
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The paper's build, for Create's {@code SchematicItem.loadSchematic}: on the server from the world's copy, on a
     * client from what the server sent. Null to let Create read its file as before (a paper without a hash, a copy
     * that is gone, a server without the Bridge).
     */
    public static StructureTemplate load(Level level, ItemStack stack) {
        if (level.isClientSide) return dev.creategalore.bridge.client.SharedSchematicCache.load(level, stack);
        String hash = hash(stack);
        MinecraftServer server = level.getServer();
        if (hash == null || server == null) return null;
        byte[] bytes = stored(server, hash);
        return bytes == null ? null : parse(level, bytes);
    }

    /** A build from its file's bytes (gzipped structure NBT, as Create writes it); an empty one if they don't read. */
    public static StructureTemplate parse(Level level, byte[] bytes) {
        StructureTemplate template = new StructureTemplate();
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(0x20000000L));
            template.load(level.holderLookup(Registries.BLOCK), tag);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read schematic", e);
        }
        return template;
    }

    /**
     * A client wants a paper's build. It gets it only for a paper it has (inventory or the cursor), so a build can't be
     * fetched by guessing names; anything else gets an empty answer and the client falls back to its own folder.
     */
    private static void onRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        List<ItemStack> papers = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) papers.add(player.getInventory().getItem(i));
        papers.add(player.containerMenu.getCarried());
        byte[] bytes = null;
        String found = "";
        for (ItemStack paper : papers) {
            if (!request.key.equals(key(paper))) continue;
            // A paper from before: keep its build now, so it stays the same from here on.
            stamp(player.level(), paper);
            String hash = hash(paper);
            if (hash != null && bytes == null) {
                bytes = stored(player.server, hash);
                if (bytes != null) found = hash;
            }
            if (bytes == null) {
                Path upload = upload(paper);
                try {
                    if (upload != null && Files.isRegularFile(upload)) bytes = Files.readAllBytes(upload);
                } catch (IOException e) {
                    LOGGER.warn("Couldn't read the schematic {}", upload, e);
                }
            }
        }
        if (bytes == null || bytes.length > MAX_BYTES) bytes = new byte[0];
        send(player, request.key, bytes.length == 0 ? "" : found, bytes);
    }

    private static void send(ServerPlayer player, String key, String hash, byte[] bytes) {
        int offset = 0;
        do {
            int length = Math.min(CHUNK, bytes.length - offset);
            byte[] part = new byte[length];
            System.arraycopy(bytes, offset, part, 0, length);
            PacketDistributor.sendToPlayer(player, new Part(key, hash, bytes.length, offset, part));
            offset += length;
        } while (offset < bytes.length);
    }

    /** Client to server: send me this paper's build. */
    public record Request(String key) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("schematic_request"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = ByteBufCodecs.stringUtf8(512).map(Request::new, Request::key)
                .cast();

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: a piece of a build's file ({@code total} 0: the server has nothing for that key). {@code hash}
     * is the build's hash when the key was a paper's maker and file name, since the server marks that paper now.
     */
    public record Part(String key, String hash, int total, int offset, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Part> TYPE = new Type<>(id("schematic_part"));
        static final StreamCodec<FriendlyByteBuf, Part> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.key, 512);
            buf.writeUtf(p.hash, 64);
            buf.writeVarInt(p.total);
            buf.writeVarInt(p.offset);
            buf.writeByteArray(p.bytes);
        }, buf -> new Part(buf.readUtf(512), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK)));

        @Override
        public Type<Part> type() {
            return TYPE;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, path);
    }
}

package dev.creategalore.bridge;

import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.datafix.DataFixTypes;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Structure files (the .nbt pieces of every dungeon, village and ruin) saved by an older Minecraft are brought up to
 * date by DFU each time the game loads them, on the server thread, when worldgen or a chunk from disk first needs
 * them. 10,000 of the pack's 12,000 are older than 1.21.1 and the big ones take seconds each (a When Dungeons Arise
 * keep: 4 to 8 s), and ModernFix lets loaded ones be dropped under memory pressure (soft references) and blasts DFU's
 * caches, so they come back. That froze the server whenever someone walked near an old dungeon.
 * <p>
 * Here each file is converted once and the result kept on disk (cache/create_galore/structures-...), keyed by a hash of
 * its contents, so a mod update changes the key and a stale copy is never used. A background thread converts every
 * old structure the server has after it starts, so the server thread only reads finished files. Files that name
 * Steam 'n' Rails or Brewin' and Chewin' blocks are left alone: their own fixers also run on them and may change in
 * an update.
 * <p>
 * Also holds the check RailsStructureFixSkipMixin and BrewinStructureFixSkipMixin use.
 */
public final class StructureFixes {
    private static final Logger LOG = LogUtils.getLogger();
    /** Brewin' and Chewin' 4.5.0 is the version whose fixer was checked; a newer one may fix more, so it runs as is. */
    public static final boolean BREWIN_CHECKED = ModList.get().getModContainerById("brewinandchewin")
            .map(mod -> mod.getModInfo().getVersion().toString().equals("4.5.0")).orElse(false);
    /** Mods whose own data fixers run on structure files (see the mixins): files naming them aren't cached. */
    private static final String[] MOD_FIXED = {"railways:", "brewinandchewin:"};
    private static final int CURRENT = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    /** One folder per game data version (a different game version converts differently); "1" is this cache's format. */
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("cache").resolve("create_galore").resolve("structures-1-" + CURRENT);
    private static final FileToIdConverter STRUCTURES = new FileToIdConverter("structure", ".nbt");
    /** Writes cache files off the server thread. */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Create Galore structure cache writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** The file readNoting just read on this thread: its tag (weakly), key and which MOD_FIXED prefixes it holds. */
    private static final ThreadLocal<Parsed> PARSED = new ThreadLocal<>();
    private static volatile Thread warmer;
    private static volatile boolean stopping;

    private StructureFixes() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> startWarming(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> stopping = true);
    }

    private record Parsed(WeakReference<CompoundTag> tag, String key, Set<String> modFixed) {
        boolean is(CompoundTag other) {
            return tag.get() == other;
        }
    }

    /**
     * Reads a structure file for StructureTemplateManager.readStructure(InputStream) (as NbtIo.readCompressed does) and
     * notes its key and which MOD_FIXED prefixes its bytes hold, for upgrade() and mentions() on the tag it returns.
     */
    public static CompoundTag readNoting(InputStream in) throws IOException {
        byte[] raw = in.readAllBytes();
        byte[] plain = gunzip(raw);
        CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(plain)), NbtAccounter.unlimitedHeap());
        PARSED.set(new Parsed(new WeakReference<>(tag), key(raw), prefixesIn(plain)));
        return tag;
    }

    /**
     * Stands in for DataFixTypes.STRUCTURE.updateToCurrentVersion in StructureTemplateManager.readStructure: the cached
     * converted file when there is one, else the conversion (saved for next time). Only files read through
     * readNoting have a key; a tag from anywhere else is converted as usual.
     */
    public static CompoundTag upgrade(CompoundTag tag, int version, Supplier<CompoundTag> convert) {
        Parsed parsed = PARSED.get();
        try {
            if (version >= CURRENT || parsed == null || !parsed.is(tag)) return convert.get();
            // Only files without blocks of mods with their own fixers are ever saved, so a cached copy needs no check.
            CompoundTag cached = read(parsed.key());
            if (cached != null) return cached;
            if (!parsed.modFixed().isEmpty()) return convert.get();
            CompoundTag converted = convert.get();
            CompoundTag copy = converted.copy();
            WRITER.execute(() -> write(parsed.key(), copy));
            return converted;
        } finally {
            PARSED.remove();
        }
    }

    private static void startWarming(MinecraftServer server) {
        stopping = false;
        if (warmer != null && warmer.isAlive()) return;
        Thread t = new Thread(() -> warm(server), "Create Galore structure cache");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        warmer = t;
        t.start();
    }

    /** Converts every old structure file the server has that isn't cached yet, then removes cached files nothing uses. */
    private static void warm(MinecraftServer server) {
        long start = System.nanoTime();
        int converted = 0, cached = 0, failed = 0;
        Set<String> used = new HashSet<>();
        try {
            Files.createDirectories(DIR);
            Map<ResourceLocation, Resource> files = STRUCTURES.listMatchingResources(server.getResourceManager());
            for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
                if (stopping) return;
                try {
                    byte[] raw;
                    try (InputStream in = file.getValue().open()) {
                        raw = in.readAllBytes();
                    }
                    String key = key(raw);
                    if (Files.exists(DIR.resolve(key + ".nbt"))) {
                        used.add(key);
                        cached++;
                        continue;
                    }
                    byte[] plain = gunzip(raw);
                    if (!prefixesIn(plain).isEmpty()) continue;
                    CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(plain)), NbtAccounter.unlimitedHeap());
                    int version = NbtUtils.getDataVersion(tag, 500);
                    if (version >= CURRENT) continue;
                    used.add(key);
                    write(key, DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, version));
                    converted++;
                } catch (Exception e) {
                    failed++;
                    LOG.debug("Create Galore: couldn't convert structure {}", file.getKey(), e);
                }
            }
            int removed = 0;
            try (Stream<Path> old = Files.list(DIR)) {
                for (Path p : old.toList()) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".nbt") && !used.contains(name.substring(0, name.length() - 4)) && Files.deleteIfExists(p)) removed++;
                }
            }
            LOG.info("Create Galore: old structure files ready in {} s ({} converted now, {} already converted, {} unused removed, {} failed)",
                    (System.nanoTime() - start) / 1_000_000_000L, converted, cached, removed, failed);
        } catch (Exception | OutOfMemoryError e) {
            LOG.warn("Create Galore: stopped converting old structure files ({}); the rest convert when the world needs them", e.toString());
        }
    }

    private static CompoundTag read(String key) {
        Path file = DIR.resolve(key + ".nbt");
        if (!Files.isRegularFile(file)) return null;
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return NbtUtils.getDataVersion(tag, 0) == CURRENT ? tag : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void write(String key, CompoundTag converted) {
        try {
            Files.createDirectories(DIR);
            Path tmp = Files.createTempFile(DIR, key, ".tmp");
            NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(converted), tmp);
            Files.move(tmp, DIR.resolve(key + ".nbt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.debug("Create Galore: couldn't save converted structure {}", key, e);
        }
    }

    /** SHA-256 of the file's bytes, so any change to it (a mod update) gives a new key. */
    private static String key(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static byte[] gunzip(byte[] raw) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return in.readAllBytes();
        }
    }

    /** Which MOD_FIXED prefixes occur anywhere in a file's uncompressed bytes (NBT keeps ASCII text as is). */
    private static Set<String> prefixesIn(byte[] plain) {
        String text = new String(plain, StandardCharsets.ISO_8859_1);
        Set<String> found = new HashSet<>();
        for (String prefix : MOD_FIXED) if (text.contains(prefix)) found.add(prefix);
        return found;
    }

    /**
     * The last tag walked on this thread (weakly, they can be big) and which MOD_FIXED prefixes it holds: each file is
     * checked several times in a row.
     */
    private static final ThreadLocal<Object[]> LAST = ThreadLocal.withInitial(() -> new Object[2]);

    /**
     * True when any string value anywhere in the tag starts with the prefix (one of MOD_FIXED). A file just read by
     * readNoting was already checked from its bytes; any other tag is walked once for all prefixes, without recursion
     * (big files nest deep).
     */
    public static boolean mentions(CompoundTag root, String prefix) {
        Parsed parsed = PARSED.get();
        if (parsed != null && parsed.is(root)) return parsed.modFixed().contains(prefix);
        Object[] last = LAST.get();
        if (!(last[0] instanceof WeakReference<?> ref) || ref.get() != root) {
            last[0] = new WeakReference<>(root);
            last[1] = prefixesIn(root);
        }
        return ((Set<?>) last[1]).contains(prefix);
    }

    private static Set<String> prefixesIn(CompoundTag root) {
        Set<String> found = new HashSet<>();
        ArrayDeque<Tag> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty() && found.size() < MOD_FIXED.length) {
            Tag tag = todo.poll();
            if (tag instanceof StringTag s) {
                String value = s.getAsString();
                for (String prefix : MOD_FIXED) if (value.startsWith(prefix)) found.add(prefix);
            } else if (tag instanceof CompoundTag c) {
                for (String key : c.getAllKeys()) {
                    Tag child = c.get(key);
                    if (child.getId() == Tag.TAG_STRING || child.getId() == Tag.TAG_COMPOUND || child.getId() == Tag.TAG_LIST) todo.add(child);
                }
            } else if (tag instanceof CollectionTag<?> list && (list.getElementType() == Tag.TAG_STRING
                    || list.getElementType() == Tag.TAG_COMPOUND || list.getElementType() == Tag.TAG_LIST)) {
                todo.addAll(list);
            }
        }
        return found;
    }
}

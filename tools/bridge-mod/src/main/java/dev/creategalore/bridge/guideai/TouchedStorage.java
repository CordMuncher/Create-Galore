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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

/**
 * The chests and other storage each player has opened, so the book can count what is in them as theirs. A block is
 * remembered when the player right-clicks it and it holds items (anything with an item handler: chests, barrels,
 * Create vaults, drawers). Asking only looks at the ones within {@link #RANGE} blocks in the player's dimension, in
 * loaded chunks, so a chest opened far away is never counted. Kept per world, next to the book's pages.
 */
final class TouchedStorage {
    private static final Logger LOG = LogUtils.getLogger();
    static final int RANGE = 50;
    private static final int MAX_PER_PLAYER = 2000, MAX_CONTAINERS = 96;

    private final Path file;
    /** Player -> "dimension|x|y|z", oldest first. */
    private final Map<UUID, LinkedHashSet<String>> touched = new HashMap<>();
    private boolean dirty;

    TouchedStorage(Path folder) {
        this.file = folder.resolve("touched_storage.json");
        load();
    }

    /** Server thread. */
    void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = canonical(level, event.getPos());
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) return;
        LinkedHashSet<String> set = touched.computeIfAbsent(player.getUUID(), k -> new LinkedHashSet<>());
        String key = key(level, pos);
        set.remove(key); // most recently opened last, so the oldest go first when the list is full
        set.add(key);
        while (set.size() > MAX_PER_PLAYER) {
            Iterator<String> it = set.iterator();
            it.next();
            it.remove();
        }
        dirty = true;
    }

    /** Every item in the storage they have opened within {@link #RANGE} blocks, with its total. Server thread. */
    Map<net.minecraft.world.item.Item, Integer> counts(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos here = player.blockPosition();
        LinkedHashSet<String> set = touched.get(player.getUUID());
        Map<net.minecraft.world.item.Item, Integer> out = new LinkedHashMap<>();
        if (set == null) return out;
        String dim = level.dimension().location().toString();
        IdentityHashMap<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        int containers = 0;
        for (String k : set) {
            BlockPos p = parse(k, dim);
            if (p == null || p.distSqr(here) > (double) RANGE * RANGE || !level.isLoaded(p)) continue;
            if (containers >= MAX_CONTAINERS) break;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null || seen.put(handler, Boolean.TRUE) != null) continue;
            containers++;
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (!s.isEmpty()) out.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private record Found(String id, String name, int count, List<String> where) {
    }

    /**
     * What is in the player's storage nearby: every item with its total, or (with a filter) only the items whose name
     * or id contains it, with where each lies. Server thread.
     */
    String describe(ServerPlayer player, String filter, int maxItems) {
        ServerLevel level = player.serverLevel();
        BlockPos here = player.blockPosition();
        LinkedHashSet<String> set = touched.get(player.getUUID());
        String dim = level.dimension().location().toString();
        List<BlockPos> near = new ArrayList<>();
        if (set != null) {
            for (String k : set) {
                BlockPos p = parse(k, dim);
                if (p != null && p.distSqr(here) <= (double) RANGE * RANGE) near.add(p);
            }
        }
        near.sort(Comparator.comparingDouble(p -> p.distSqr(here)));
        String f = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
        Map<String, Found> items = new LinkedHashMap<>();
        IdentityHashMap<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        int containers = 0;
        List<String> gone = new ArrayList<>();
        for (BlockPos p : near) {
            if (containers >= MAX_CONTAINERS) break;
            if (!level.isLoaded(p)) continue;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) {
                gone.add(key(level, p));
                continue;
            }
            // Blocks of one multiblock (a Create vault) can hand out the same handler; count it once.
            if (seen.put(handler, Boolean.TRUE) != null) continue;
            containers++;
            String where = level.getBlockState(p).getBlock().getName().getString() + " " + offset(here, p);
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (s.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                String name = s.getHoverName().getString();
                if (!f.isEmpty() && !id.toString().contains(f) && !name.toLowerCase(Locale.ROOT).contains(f)) continue;
                String key = id + "|" + name;
                Found old = items.get(key);
                List<String> w = old == null ? new ArrayList<>() : old.where;
                if (!w.contains(where)) w.add(where);
                items.put(key, new Found(id.toString(), name, (old == null ? 0 : old.count) + s.getCount(), w));
            }
        }
        if (!gone.isEmpty() && set != null) {
            gone.forEach(set::remove);
            dirty = true;
        }
        if (containers == 0) return "No storage they have opened is within " + RANGE + " blocks.";
        List<Found> list = new ArrayList<>(items.values());
        list.sort(Comparator.comparingInt(Found::count).reversed());
        StringBuilder out = new StringBuilder();
        out.append(containers).append(containers == 1 ? " container" : " containers").append(" they have opened within ")
                .append(RANGE).append(" blocks");
        if (!f.isEmpty()) out.append(", items matching \"").append(filter.strip()).append('"');
        out.append(':');
        if (list.isEmpty()) return out.append(" nothing").append(f.isEmpty() ? " in them." : " matches.").toString();
        int shown = 0;
        for (Found item : list) {
            if (shown++ >= maxItems) {
                out.append("\n  ...and ").append(list.size() - maxItems).append(" more kinds (search_storage with a filter finds one)");
                break;
            }
            out.append("\n  ").append(item.count).append("x ").append(item.name).append(" (").append(item.id).append(')');
            if (!f.isEmpty()) out.append(": in ").append(String.join("; ", item.where.subList(0, Math.min(4, item.where.size()))));
        }
        return out.toString();
    }

    /** "12 blocks north-east, 3 up" from the player to a block. */
    static String offset(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + dz * dz));
        String ns = dz < -1 ? "north" : dz > 1 ? "south" : "", ew = dx > 1 ? "east" : dx < -1 ? "west" : "";
        String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
        StringBuilder s = new StringBuilder(dist <= 1 ? "right here" : dist + " blocks " + dir);
        if (Math.abs(dy) > 1) s.append(", ").append(Math.abs(dy)).append(dy > 0 ? " up" : " down");
        return s.toString();
    }

    /** One half of a double chest stands for both, so opening either half is the same chest. */
    private static BlockPos canonical(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (other.asLong() < pos.asLong()) return other;
        }
        return pos;
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static BlockPos parse(String key, String dim) {
        String[] p = key.split("\\|");
        if (p.length != 4 || !p[0].equals(dim)) return null;
        try {
            return new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) {
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (JsonElement k : e.getValue().getAsJsonArray()) set.add(k.getAsString());
                touched.put(UUID.fromString(e.getKey()), set);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Field Guide voice: couldn't read {} ({}); starting a fresh list of opened storage", file, e.toString());
        }
    }

    /** Server thread; writes only when something changed. */
    void save() {
        if (!dirty) return;
        JsonObject o = new JsonObject();
        touched.forEach((player, set) -> {
            JsonArray a = new JsonArray();
            set.forEach(a::add);
            o.add(player.toString(), a);
        });
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            LOG.warn("Field Guide voice: couldn't save {} ({})", file, e.toString());
        }
    }
}

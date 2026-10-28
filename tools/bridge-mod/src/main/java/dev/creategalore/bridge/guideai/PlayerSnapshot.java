package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * What the book can tell about the asking player, sent with every question: where they are and what it is like there,
 * how far along they are, what they hold, what they have made and built, and the builds around them. The book starts
 * from this instead of deciding whether to look, so no kind of question needs its own "call this tool first" rule; the
 * tools still give the full detail. Server thread. It goes in the question, never in the cached prefix.
 */
final class PlayerSnapshot {
    private static final int MAX_HELD = 40, MAX_MADE = 15, MAX_BUILDS = 5, MAX_ADVANCEMENT_MODS = 8;

    private PlayerSnapshot() {
    }

    static String of(ServerPlayer player, MinecraftServer server) {
        StringBuilder out = new StringBuilder("What you can tell about " + player.getGameProfile().getName()
                + " right now (for you only; your tools give the full detail):\n");
        section(out, "Where they are", () -> WorldTools.surroundings(player));
        section(out, "Looking at", () -> WorldTools.lookAt(player));
        section(out, "How far along", () -> progress(player, server));
        section(out, "Holding (inventory and the storage they opened nearby, most first)", () -> held(player));
        section(out, "Made and placed so far (from their statistics, most first)", () -> made(player));
        section(out, "Drive parts", () -> Drives.snapshot(player));
        section(out, "Builds around them", () -> builds(player));
        return out.toString().strip();
    }

    private interface Part {
        String get();
    }

    /** One part; a part that fails leaves a note instead of losing the whole snapshot. */
    private static void section(StringBuilder out, String title, Part part) {
        String text;
        try {
            text = part.get();
        } catch (RuntimeException | LinkageError e) {
            text = "(couldn't be read: " + e.getClass().getSimpleName() + ")";
        }
        if (text == null || text.isBlank()) return;
        out.append("## ").append(title).append('\n').append(text.strip()).append('\n');
    }

    /** Advancements done per mod (as get_advancements counts them) and the five latest. */
    private static String progress(ServerPlayer player, MinecraftServer server) {
        List<String> perMod = new ArrayList<>(), recent = new ArrayList<>();
        int part = 0;
        for (String line : WorldTools.advancements(player, server).split("\n")) {
            if (line.startsWith("Advancements done per mod")) part = 1;
            else if (line.startsWith("Most recent first")) part = 2;
            else if (!line.startsWith("- ")) part = 3;
            else if (part == 1 && perMod.size() < MAX_ADVANCEMENT_MODS) perMod.add(line.substring(2));
            else if (part == 2 && recent.size() < 5) recent.add(line.substring(2));
        }
        return "Advancements done per mod (done of visible): " + String.join("; ", perMod)
                + (recent.isEmpty() ? "" : "\nLatest: " + String.join("; ", recent));
    }

    private static String held(ServerPlayer player) {
        Map<Item, Integer> have = Reach.owned(player);
        if (have.isEmpty()) return "nothing";
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(have.entrySet());
        list.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_HELD, list.size()); i++)
            parts.add(list.get(i).getValue() + " " + list.get(i).getKey().getDescription().getString() + " ("
                    + BuiltInRegistries.ITEM.getKey(list.get(i).getKey()) + ")");
        String more = list.size() > MAX_HELD ? "; and " + (list.size() - MAX_HELD) + " other kinds (get_inventory lists all)" : "";
        return String.join(", ", parts) + more;
    }

    /** Modded items they have crafted and modded blocks they have placed: what they have actually built with. */
    private static String made(ServerPlayer player) {
        record Count(Item item, int n) {
        }
        List<Count> crafted = new ArrayList<>(), placed = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("minecraft")) continue;
            int c = player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
            if (c > 0) crafted.add(new Count(item, c));
            if (item instanceof BlockItem) {
                int u = player.getStats().getValue(Stats.ITEM_USED.get(item));
                if (u > 0) placed.add(new Count(item, u));
            }
        }
        Comparator<Count> most = Comparator.comparingInt(Count::n).reversed();
        crafted.sort(most);
        placed.sort(most);
        StringBuilder out = new StringBuilder();
        for (List<Count> list : List.of(crafted, placed)) {
            if (list.isEmpty()) continue;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Math.min(MAX_MADE, list.size()); i++)
                parts.add(list.get(i).n() + " " + list.get(i).item().getDescription().getString());
            out.append(list == crafted ? "Crafted: " : "Placed: ").append(String.join(", ", parts))
                    .append(list.size() > MAX_MADE ? ", and " + (list.size() - MAX_MADE) + " other kinds" : "").append('\n');
        }
        return out.isEmpty() ? "no modded items crafted or blocks placed yet" : out.toString();
    }

    private static String builds(ServerPlayer player) {
        PlacedBlocks placed = GuideAi.placed();
        if (placed == null) return "";
        String all = Builds.find(player, placed, "");
        List<String> keep = new ArrayList<>();
        int builds = 0;
        for (String line : all.split("\n")) {
            if (line.startsWith("- Build ") && builds++ >= MAX_BUILDS) continue;
            if (line.startsWith("...and ")) continue;
            keep.add(line);
        }
        if (builds > MAX_BUILDS) keep.add("...and " + (builds - MAX_BUILDS) + " more (find_placed_blocks lists them).");
        return String.join("\n", keep);
    }
}

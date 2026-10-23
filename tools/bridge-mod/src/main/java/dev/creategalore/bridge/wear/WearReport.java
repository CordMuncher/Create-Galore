package dev.creategalore.bridge.wear;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Op tool (/wear report): writes logs/wear-report.txt with every real slot the player has, which group each one feeds,
 * and every item in the game that fits any of them, with the group it would go to. After a mod update this shows a
 * new slot or a new item that no group takes.
 */
final class WearReport {
    private WearReport() {
    }

    static Path write(ServerPlayer player) throws IOException {
        List<String> lines = new ArrayList<>();
        Map<String, Worn.Loc> firstOfEach = new LinkedHashMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Worn.Loc loc : Worn.all(player)) {
            String id = (loc.curios() ? "curios " : "accessories ") + loc.slot();
            firstOfEach.putIfAbsent(id, loc);
            sizes.merge(id, 1, Integer::sum);
        }
        lines.add("# Real slots on " + player.getGameProfile().getName());
        for (Map.Entry<String, Integer> e : sizes.entrySet()) {
            WearGroups.Group g = WearGroups.ofSlot(firstOfEach.get(e.getKey()));
            lines.add(e.getKey() + " x" + e.getValue() + " -> " + (g == null ? "NO GROUP" : g.id()));
        }
        lines.add("");
        lines.add("# Items that fit a real slot: item | slots | group");
        Map<String, String> rows = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            List<String> fits = new ArrayList<>();
            for (Map.Entry<String, Worn.Loc> e : firstOfEach.entrySet()) {
                if (Worn.fits(player, e.getValue(), stack)) fits.add(e.getKey());
            }
            if (fits.isEmpty()) continue;
            List<String> groups = new ArrayList<>();
            for (WearGroups.Group g : WearGroups.of(player, stack)) groups.add(g.id());
            rows.put(BuiltInRegistries.ITEM.getKey(item).toString(), String.join(", ", fits) + " | " + (groups.isEmpty() ? "NO GROUP" : String.join(", ", groups)));
        }
        rows.forEach((k, v) -> lines.add(k + " | " + v));
        Path out = FMLPaths.GAMEDIR.get().resolve("logs/wear-report.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);
        return out;
    }
}

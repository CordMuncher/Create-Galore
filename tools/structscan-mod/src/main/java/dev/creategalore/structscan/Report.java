package dev.creategalore.structscan;

import dev.creategalore.structscan.Scanner.Layer;
import dev.creategalore.structscan.Scanner.Start;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Density numbers from a scan, per layer (surface, underground, underwater, sky), and the files they go into:
 * summary.md, structures.tsv, sets.tsv, mods.tsv, starts.tsv and scan.properties (what a later scan reads as its baseline).
 */
final class Report {
    /**
     * Emre's density targets (2026-10-17), as multiples of a vanilla layer's density:
     * surface 2× vanilla surface, underground and underwater 3× their vanilla layer, sky 1× vanilla surface.
     */
    private static final Map<Layer, Layer> TARGET_BASE = Map.of(Layer.SURFACE, Layer.SURFACE, Layer.UNDERGROUND, Layer.UNDERGROUND,
            Layer.UNDERWATER, Layer.UNDERWATER, Layer.SKY, Layer.SURFACE);
    private static final Map<Layer, Double> TARGET_X = Map.of(Layer.SURFACE, 2.0, Layer.UNDERGROUND, 3.0, Layer.UNDERWATER, 3.0, Layer.SKY, 1.0);

    /** Stats are taken this far inside the scanned square, so structures just outside it don't skew the edges. */
    private static int margin(Scanner.Result r) { return Math.min(1024, r.radius() / 4); }

    static double areaKm2(Scanner.Result r) {
        double side = 2.0 * r.radius();
        return side * side / 1e6;
    }

    private static String name(Layer l) { return l.name().charAt(0) + l.name().substring(1).toLowerCase(Locale.ROOT); }

    /** Spatial index: structures bucketed in 256-block cells. */
    private static final class Grid {
        static final int CELL = 256;
        final Map<Long, List<Start>> cells = new HashMap<>();
        Grid(List<Start> starts) {
            for (Start s : starts) cells.computeIfAbsent(key(Math.floorDiv(s.x(), CELL), Math.floorDiv(s.z(), CELL)), k -> new ArrayList<>()).add(s);
        }
        static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

        /** Distance to the nearest other structure in the grid, or -1 if none within maxDist. */
        double nearest(Start from, int maxDist) {
            int cx = Math.floorDiv(from.x(), CELL), cz = Math.floorDiv(from.z(), CELL);
            double best = Double.MAX_VALUE;
            for (int ring = 0; ring * CELL <= maxDist + CELL; ring++) {
                if ((ring - 1) * CELL > best) break;
                for (int x = cx - ring; x <= cx + ring; x++)
                    for (int z = cz - ring; z <= cz + ring; z++) {
                        if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != ring) continue;
                        List<Start> l = cells.get(key(x, z));
                        if (l == null) continue;
                        for (Start s : l) if (s != from) best = Math.min(best, Math.hypot(s.x() - from.x(), s.z() - from.z()));
                    }
            }
            return best <= maxDist ? best : -1;
        }

        int countWithin(int px, int pz, int radius) {
            int n = 0;
            for (int x = Math.floorDiv(px - radius, CELL); x <= Math.floorDiv(px + radius, CELL); x++)
                for (int z = Math.floorDiv(pz - radius, CELL); z <= Math.floorDiv(pz + radius, CELL); z++) {
                    List<Start> l = cells.get(key(x, z));
                    if (l == null) continue;
                    for (Start s : l) if (Math.hypot(s.x() - px, s.z() - pz) <= radius) n++;
                }
            return n;
        }
    }

    /** One layer's numbers. within192 is about what a 12-chunk view shows from a random spot. */
    record Stats(int count, double perKm2, double medianNn, double meanNn, double within192, double within512, double seeOne192, double seeNone512) {}

    static Stats stats(Scanner.Result r, List<Start> starts) {
        Grid g = new Grid(starts);
        int lim = r.radius() - margin(r);
        double[] nn = starts.stream().filter(s -> Math.abs(s.x() - r.centerX()) <= lim && Math.abs(s.z() - r.centerZ()) <= lim)
                .mapToDouble(s -> g.nearest(s, 2 * margin(r))).filter(v -> v >= 0).sorted().toArray();
        int inner = r.radius() - Math.max(512, margin(r));
        Random rnd = new Random(1);
        int n = inner > 0 ? 20000 : 0;
        double a = 0, b = 0, one = 0, none = 0;
        for (int i = 0; i < n; i++) {
            int x = r.centerX() + rnd.nextInt(2 * inner + 1) - inner, z = r.centerZ() + rnd.nextInt(2 * inner + 1) - inner;
            int k = g.countWithin(x, z, 192), far = g.countWithin(x, z, 512);
            a += k; b += far;
            if (k > 0) one++;
            if (far == 0) none++;
        }
        n = Math.max(n, 1);
        return new Stats(starts.size(), starts.size() / areaKm2(r), nn.length == 0 ? Double.NaN : nn[nn.length / 2],
                Arrays.stream(nn).average().orElse(Double.NaN), a / n, b / n, one / n, none / n);
    }

    static Map<Layer, List<Start>> byLayer(List<Start> starts) {
        Map<Layer, List<Start>> m = new EnumMap<>(Layer.class);
        for (Layer l : Layer.values()) m.put(l, new ArrayList<>());
        for (Start s : starts) m.get(s.layer()).add(s);
        return m;
    }

    /** A baseline scan's density per layer (per km²), read back from its folder. */
    static Map<Layer, Double> readBaseline(Path dir) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(dir.resolve("scan.properties"))) { p.load(in); }
        double km2 = Double.parseDouble(p.getProperty("area_km2"));
        Map<Layer, Double> out = new EnumMap<>(Layer.class);
        for (Layer l : Layer.values()) out.put(l, Integer.parseInt(p.getProperty("count_" + l.name().toLowerCase(Locale.ROOT), "0")) / km2);
        return out;
    }

    static List<String> headline(Scanner.Result r) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "%s: %d structures in %.0f km², %.1f s", r.dim(), r.starts().size(), areaKm2(r), r.millis() / 1000.0));
        byLayer(r.starts()).forEach((l, list) -> {
            Stats s = stats(r, list);
            out.add(String.format(Locale.ROOT, "  %s: %d (%.2f per km²), nearest neighbour median %.0f blocks, %.2f within 192 blocks of a spot",
                    name(l), s.count(), s.perKm2(), s.medianNn(), s.within192()));
        });
        return out;
    }

    static Path write(Path root, StructScan.Options opt, Scanner.Result r) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        Path dir = root.resolve(stamp + "-" + opt.label() + "-" + r.dim().replace(':', '_'));
        Files.createDirectories(dir);
        List<Start> all = r.starts();
        double km2 = areaKm2(r);
        Map<Layer, List<Start>> layers = byLayer(all);
        Map<Layer, Stats> stats = new EnumMap<>(Layer.class);
        layers.forEach((l, list) -> stats.put(l, stats(r, list)));
        Map<Layer, Double> base = null;
        if (opt.baseline() != null && Files.exists(opt.baseline().resolve("scan.properties"))) base = readBaseline(opt.baseline());

        // scan.properties: the totals, so this scan can be another one's baseline.
        Properties props = new Properties();
        props.setProperty("label", opt.label());
        props.setProperty("dimension", r.dim());
        props.setProperty("seed", String.valueOf(r.seed()));
        props.setProperty("radius", String.valueOf(r.radius()));
        props.setProperty("area_km2", String.valueOf(km2));
        for (Layer l : Layer.values()) props.setProperty("count_" + l.name().toLowerCase(Locale.ROOT), String.valueOf(layers.get(l).size()));
        try (var w = Files.newBufferedWriter(dir.resolve("scan.properties"))) { props.store(w, "Create Galore structure scan"); }

        // Every structure found, for anything the summary doesn't answer.
        StringBuilder st = new StringBuilder("set\tstructure\tx\tz\tmin_y\tmax_y\tfloor_y\twater_y\tlayer\tbiome\n");
        all.stream().sorted(Comparator.comparing(Start::structure).thenComparingInt(Start::x)).forEach(s ->
                st.append(s.set()).append('\t').append(s.structure()).append('\t').append(s.x()).append('\t').append(s.z()).append('\t')
                        .append(s.minY()).append('\t').append(s.maxY()).append('\t').append(s.floor()).append('\t').append(s.water()).append('\t')
                        .append(s.layer().name().toLowerCase(Locale.ROOT)).append('\t').append(s.biome()).append('\n'));
        Files.writeString(dir.resolve("starts.tsv"), st);

        // Per biome: how much of the square it covers (at the ground and down in the caves) and the structures in it.
        // Underground structures count against the cave area, the other layers against the ground area.
        Map<String, int[]> biomeCounts = new TreeMap<>();
        for (Start s : all) biomeCounts.computeIfAbsent(s.biome(), k -> new int[4])[s.layer().ordinal()]++;
        Set<String> biomeIds = new java.util.TreeSet<>(r.biomeArea().keySet());
        biomeIds.addAll(biomeCounts.keySet());
        List<String[]> biomeRows = new ArrayList<>();
        StringBuilder bt = new StringBuilder("biome\tground_km2\tcave_km2\tsurface\tunderground\tunderwater\tsky\tabove_ground_per_km2\tunderground_per_km2\n");
        for (String b : biomeIds) {
            double[] a = r.biomeArea().getOrDefault(b, new double[2]);
            int[] c = biomeCounts.getOrDefault(b, new int[4]);
            String[] row = {b, f2(a[0]), f2(a[1]), String.valueOf(c[0]), String.valueOf(c[1]), String.valueOf(c[2]), String.valueOf(c[3]),
                    a[0] > 0 ? f2((c[0] + c[2] + c[3]) / a[0]) : "-", a[1] > 0 ? f2(c[1] / a[1]) : "-"};
            biomeRows.add(row);
            bt.append(String.join("\t", row)).append('\n');
        }
        Files.writeString(dir.resolve("biomes.tsv"), bt);

        // Per structure.
        Map<String, List<Start>> byStructure = new TreeMap<>();
        for (Start s : all) byStructure.computeIfAbsent(s.structure(), k -> new ArrayList<>()).add(s);
        List<String[]> rows = new ArrayList<>();
        StringBuilder stt = new StringBuilder("structure\tset\tmain_layer\tcount\tper_km2\tsurface\tunderground\tunderwater\tsky\tmedian_nn_same_blocks\n");
        for (Map.Entry<String, List<Start>> e : byStructure.entrySet()) {
            List<Start> l = e.getValue();
            int[] c = new int[4];
            for (Start s : l) c[s.layer().ordinal()]++;
            Layer main = Layer.values()[argmax(c)];
            Stats same = stats(r, l);
            String[] row = {e.getKey(), l.get(0).set(), main.name().toLowerCase(Locale.ROOT), String.valueOf(l.size()), f2(l.size() / km2),
                    String.valueOf(c[0]), String.valueOf(c[1]), String.valueOf(c[2]), String.valueOf(c[3]),
                    Double.isNaN(same.medianNn()) ? "-" : String.format(Locale.ROOT, "%.0f", same.medianNn())};
            rows.add(row);
            stt.append(String.join("\t", row)).append('\n');
        }
        Files.writeString(dir.resolve("structures.tsv"), stt);

        // Per structure set: the placement numbers to tune, and what each set placed in each layer.
        StringBuilder sets = new StringBuilder("set\ttype\tspacing\tseparation\tfrequency\tnew_spacing\tnew_separation\tnew_frequency\trule\tcandidates\tplaced\tsurface\tunderground\tunderwater\tsky\tseconds\tstructures\n");
        for (Scanner.SetInfo i : r.sets()) {
            sets.append(i.id).append('\t').append(i.type).append('\t').append(i.spacing).append('\t').append(i.separation).append('\t')
                    .append(i.frequency).append('\t').append(i.newSpacing).append('\t').append(i.newSeparation).append('\t').append(i.newFrequency)
                    .append('\t').append(i.rule).append('\t').append(i.candidates).append('\t').append(i.placed.get());
            for (var a : i.byLayer) sets.append('\t').append(a.get());
            sets.append('\t').append(f1s(i.nanos.get())).append('\t').append(i.structures).append('\n');
        }
        Files.writeString(dir.resolve("sets.tsv"), sets);

        // Per mod: how many kinds of structure it can place in this dimension, and how many it did, per layer.
        Map<String, Set<String>> kinds = new TreeMap<>();
        for (Scanner.SetInfo i : r.sets()) for (String id : i.structureIds) kinds.computeIfAbsent(ns(id), k -> new HashSet<>()).add(id);
        Map<String, int[]> modCounts = new TreeMap<>();
        Map<String, Set<String>> modFoundKinds = new HashMap<>();
        for (Start s : all) {
            modCounts.computeIfAbsent(ns(s.structure()), k -> new int[4])[s.layer().ordinal()]++;
            modFoundKinds.computeIfAbsent(ns(s.structure()), k -> new HashSet<>()).add(s.structure());
            kinds.computeIfAbsent(ns(s.structure()), k -> new HashSet<>()).add(s.structure());
        }
        int totalKinds = kinds.values().stream().mapToInt(Set::size).sum();
        StringBuilder mods = new StringBuilder("mod\tkinds\tkinds_found\tsurface\tunderground\tunderwater\tsky\ttotal\tshare_of_structures\tshare_of_kinds\n");
        List<String[]> modRows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : kinds.entrySet()) {
            int[] c = modCounts.getOrDefault(e.getKey(), new int[4]);
            int tot = c[0] + c[1] + c[2] + c[3];
            String[] row = {e.getKey(), String.valueOf(e.getValue().size()), String.valueOf(modFoundKinds.getOrDefault(e.getKey(), Set.of()).size()),
                    String.valueOf(c[0]), String.valueOf(c[1]), String.valueOf(c[2]), String.valueOf(c[3]), String.valueOf(tot),
                    pct(all.isEmpty() ? 0 : tot / (double) all.size()), pct(e.getValue().size() / (double) Math.max(1, totalKinds))};
            modRows.add(row);
            mods.append(String.join("\t", row)).append('\n');
        }
        Files.writeString(dir.resolve("mods.tsv"), mods);

        StringBuilder md = new StringBuilder();
        md.append("# Structure scan: ").append(opt.label()).append("\n\n");
        md.append(String.format(Locale.ROOT, "Seed `%d`, %s, square ±%d blocks around %d, %d (%.0f km²). %d candidate chunks checked in %.1f s%s.\n\n",
                r.seed(), r.dim(), r.radius(), r.centerX(), r.centerZ(), km2, r.chunksChecked(), r.millis() / 1000.0,
                r.errors() > 0 ? ", " + r.errors() + " chunks failed (see the log)" : ""));
        if (r.whatif()) md.append("**What-if run**: some structure sets use rescaled spacing (see the rule column below). The world itself is unchanged.\n\n");
        md.append("Layers are judged at the middle of each structure: **sky** starts 16+ blocks above the ground or water, **underground** tops out more than 4 blocks under the ground or sea floor, ")
                .append("**underwater** stands in 3+ blocks of water and stays below its surface, **surface** is everything else. Distances are between bounding-box centres, measured from structures more than ")
                .append(margin(r)).append(" blocks inside the edge.\n\n");

        md.append("## By layer\n\n| Layer | Structures | Per km² | Nearest neighbour (median / mean blocks) | Within 192 of a spot | Spots seeing one within 192 | Within 512 | Spots with none within 512 |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Layer l : Layer.values()) {
            Stats s = stats.get(l);
            md.append(String.format(Locale.ROOT, "| %s | %d | %.2f | %.0f / %.0f | %.2f | %.0f%% | %.2f | %.0f%% |\n", name(l), s.count(), s.perKm2(),
                    s.medianNn(), s.meanNn(), s.within192(), s.seeOne192() * 100, s.within512(), s.seeNone512() * 100));
        }

        if (base != null) {
            md.append("\n## Against the vanilla baseline\n\nBaseline: `").append(opt.baseline().getFileName()).append("`. Targets are Emre's: surface 2× vanilla surface, underground 3× vanilla underground, ")
                    .append("underwater 3× vanilla underwater, sky 1× vanilla surface. \"Keep\" is the share of this scan's structures that would hit the target.\n\n")
                    .append("| Layer | This scan per km² | Vanilla per km² | This ÷ vanilla | Target per km² | Keep |\n|---|---:|---:|---:|---:|---:|\n");
            for (Layer l : Layer.values()) {
                double now = stats.get(l).perKm2(), v = base.get(l), target = base.get(TARGET_BASE.get(l)) * TARGET_X.get(l);
                md.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %s | %.2f (%s× vanilla %s) | %s |\n", name(l), now, v,
                        v > 0 ? String.format(Locale.ROOT, "%.1f×", now / v) : "-", target, f1(TARGET_X.get(l)), name(TARGET_BASE.get(l)).toLowerCase(Locale.ROOT),
                        now > 0 ? pct(Math.min(1, target / now)) : "-"));
            }
        }

        md.append("\n## By mod\n\nKinds = structure types the mod can place in this dimension (from its structure sets); share of kinds is what an even split by kinds would give it.\n\n")
                .append("| Mod | Kinds | Kinds found | Surface | Underground | Underwater | Sky | Share of structures | Share of kinds |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        modRows.stream().sorted((a, b) -> Integer.parseInt(b[7]) - Integer.parseInt(a[7])).forEach(row ->
                md.append("| ").append(String.join(" | ", row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[8], row[9])).append(" |\n"));

        md.append("\n## By biome\n\nStructures per km² of each biome that covers at least 1 km² of the square: surface, underwater and sky structures against the biome's area at the ground, ")
                .append("underground ones against its area at y ").append(Scanner.CAVE_Y).append(" (sampled every ").append(Scanner.BIOME_STEP).append(" blocks). ")
                .append("The ratio is against the whole square's density for that group, so 1.0× is average. Most crowded first.\n\n")
                .append("| Biome | Ground km² | Above ground per km² | Ratio | Cave km² | Underground per km² | Ratio |\n|---|---:|---:|---:|---:|---:|---:|\n");
        double aboveAll = (layers.get(Layer.SURFACE).size() + layers.get(Layer.UNDERWATER).size() + layers.get(Layer.SKY).size()) / km2;
        double underAll = layers.get(Layer.UNDERGROUND).size() / km2;
        biomeRows.stream().filter(b -> Double.parseDouble(b[1]) >= 1 || Double.parseDouble(b[2]) >= 1)
                .sorted(Comparator.comparingDouble(b -> -(b[7].equals("-") ? 0 : Double.parseDouble(b[7])))).forEach(b -> {
                    boolean g = Double.parseDouble(b[1]) >= 1, c = Double.parseDouble(b[2]) >= 1;
                    md.append("| ").append(b[0]).append(" | ").append(b[1]).append(" | ").append(g ? b[7] : "-").append(" | ")
                            .append(g && aboveAll > 0 ? f1(Double.parseDouble(b[7]) / aboveAll) + "×" : "-").append(" | ").append(b[2]).append(" | ")
                            .append(c ? b[8] : "-").append(" | ").append(c && underAll > 0 ? f1(Double.parseDouble(b[8]) / underAll) + "×" : "-").append(" |\n");
                });

        md.append("\n## By structure\n\n| Structure | Main layer | Count | Per km² | Median distance to the same kind |\n|---|---|---:|---:|---:|\n");
        rows.stream().sorted((a, b) -> Integer.parseInt(b[3]) - Integer.parseInt(a[3])).forEach(row ->
                md.append("| ").append(String.join(" | ", row[0], row[2], row[3], row[4], row[9])).append(" |\n"));

        md.append("\n## Structure sets\n\nSpacing and separation are in chunks. Candidates = chunks the placement grid picked; placed = structures that passed the biome and terrain checks there. Seconds = scan time spent on that set's chunks (all threads added up).\n\n");
        md.append("| Set | Type | Spacing | Separation | Frequency | Rule | Candidates | Placed | Seconds |\n|---|---|---:|---:|---:|---|---:|---:|---:|\n");
        r.sets().stream().sorted((a, b) -> b.placed.get() - a.placed.get()).forEach(i -> {
            String sp = i.spacing < 0 ? "-" : i.newSpacing != i.spacing ? i.spacing + " → " + i.newSpacing : String.valueOf(i.spacing);
            String se = i.separation < 0 ? "-" : i.newSeparation != i.separation ? i.separation + " → " + i.newSeparation : String.valueOf(i.separation);
            String fr = i.newFrequency != i.frequency ? f2(i.frequency) + " → " + f2(i.newFrequency) : f2(i.frequency);
            md.append("| ").append(i.id).append(" | ").append(i.type.replace("minecraft:", "")).append(" | ").append(sp).append(" | ").append(se)
                    .append(" | ").append(fr).append(" | ").append(i.rule.isEmpty() ? "" : "`" + i.rule + "`").append(" | ")
                    .append(i.candidates).append(" | ").append(i.placed.get()).append(" | ").append(f1s(i.nanos.get())).append(" |\n");
        });
        Files.writeString(dir.resolve("summary.md"), md);
        return dir;
    }

    private static int argmax(int[] c) {
        int best = 0;
        for (int i = 1; i < c.length; i++) if (c[i] > c[best]) best = i;
        return best;
    }

    private static String ns(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(0, i); }
    private static String f1(double v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v); }
    private static String f1s(long nanos) { return String.format(Locale.ROOT, "%.1f", nanos / 1e9); }
    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct(double v) { return String.format(Locale.ROOT, "%.0f%%", v * 100); }

    private Report() {}
}

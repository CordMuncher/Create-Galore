package dev.creategalore.bridge.guideai;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

/**
 * find_creatures: every creature on this server, so the book never has to guess whether something a player saw is
 * real. It reads the entity registry (every mod's creatures, by name and mod), what each one is like (health, attack,
 * size, whether it flies or swims, is hostile or a boss, the main colours of its skin), where it spawns by itself (the
 * biomes' real spawn lists, with every mod's spawn changes applied), what it drops when a player kills it (its loot
 * table rolled many times) and which creatures are around the asking player right now. Players describe creatures by
 * their looks ("a big green dragon"), so a search also matches those words.
 */
final class Creatures {
    static final int AROUND = 128;
    private static final int MAX_DETAILED = 6, MAX_LISTED = 40, DROP_ROLLS = 200, MAX_BIOMES = 14;

    /** What a creature is like; worked out once per type from a creature made for it (never added to the world). */
    private record Traits(boolean flies, boolean swims, boolean hostile, boolean boss, boolean large) {
        static final Traits UNKNOWN = new Traits(false, false, false, false, false);

        List<String> words() {
            List<String> w = new ArrayList<>();
            if (boss) w.add("boss");
            if (large) w.add("large");
            if (hostile) w.add("hostile");
            if (flies) w.add("flies");
            if (swims) w.add("swims");
            return w;
        }
    }

    private static final Map<EntityType<?>, Traits> TRAITS = new HashMap<>();
    private static final Map<EntityType<?>, String> COLOURS = new ConcurrentHashMap<>();

    private Creatures() {
    }

    /**
     * Works out every creature's traits at server start (on the server thread, where creatures can be made) and reads
     * their skin colours in the background, so the first search doesn't hold up the game.
     */
    static void warmUp(MinecraftServer server) {
        server.execute(() -> living().forEach(type -> traits(server.overworld(), type)));
        Thread t = new Thread(() -> {
            for (EntityType<?> type : living()) {
                try {
                    COLOURS.computeIfAbsent(type, Creatures::readColours);
                } catch (RuntimeException | LinkageError ignored) {
                    // A texture that can't be read just has no colour words.
                }
            }
        }, "Field Guide creatures");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    static String find(ServerPlayer player, MinecraftServer server, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || q.equals("all")) return all(player.level());
        if (q.equals("nearby") || q.equals("around")) return nearby(player);
        // Players describe creatures by their looks ("big green dragon"), and few creatures are called what they look
        // like, so the creatures that match the most words come first; every word matched is best.
        List<String> words = new ArrayList<>();
        for (String w : q.split("[\\s,]+")) words.add(SYNONYMS.getOrDefault(w, w));
        Map<EntityType<?>, Integer> scores = new HashMap<>();
        for (EntityType<?> type : living()) {
            String hay = (name(type) + " " + id(type) + " " + WorldTools.modName(id(type).getNamespace()) + " "
                    + String.join(" ", traits(player.level(), type).words()) + " " + colours(type)).toLowerCase(Locale.ROOT);
            int score = (int) words.stream().filter(hay::contains).count();
            if (score > 0) scores.put(type, score);
        }
        int best = scores.values().stream().max(Integer::compare).orElse(0);
        List<EntityType<?>> hits = new ArrayList<>();
        scores.forEach((type, score) -> {
            if (score == best) hits.add(type);
        });
        if (hits.isEmpty()) {
            return "No creature on this server matches '" + query + "'. Players name creatures by how they look, so try "
                    + "fewer or other words (a colour, 'flies', 'swims', 'hostile', 'boss'), 'nearby' for what is around "
                    + "them now, or 'all' for every creature.";
        }
        StringBuilder out = new StringBuilder();
        if (best < words.size()) {
            out.append("No creature matches every word; these match ").append(best).append(" of ").append(words.size())
                    .append(". Names rarely say how a creature looks, so for one a player describes try 'nearby', or its "
                    + "colour with flies, swims, large or hostile:\n");
        }
        hits.sort(Comparator.comparingInt((EntityType<?> t) -> exactness(t, q)).thenComparing(Creatures::name));
        for (int i = 0; i < hits.size() && i < MAX_DETAILED; i++) {
            out.append(describe(player, server, hits.get(i))).append("\n\n");
        }
        if (hits.size() > MAX_DETAILED) {
            List<String> rest = new ArrayList<>();
            for (int i = MAX_DETAILED; i < hits.size() && i < MAX_DETAILED + MAX_LISTED; i++) rest.add(line(player.level(), hits.get(i)));
            out.append("Also matching:\n- ").append(String.join("\n- ", rest));
            if (hits.size() > MAX_DETAILED + MAX_LISTED) out.append("\n...and ").append(hits.size() - MAX_DETAILED - MAX_LISTED).append(" more");
        }
        return out.toString().strip();
    }

    private static final Map<String, String> SYNONYMS = Map.of("big", "large", "huge", "large", "giant", "large",
            "flying", "flies", "fly", "flies", "swimming", "swims", "swim", "swims", "gray", "grey");

    private static int exactness(EntityType<?> type, String q) {
        String name = name(type).toLowerCase(Locale.ROOT);
        return name.equals(q) || id(type).toString().equals(q) || id(type).getPath().equals(q) ? 0 : name.contains(q) ? 1 : 2;
    }

    /** Every creature by mod, one line each, so a creature the player describes can be picked out by its looks. */
    private static String all(Level level) {
        Map<String, List<String>> byMod = new TreeMap<>();
        int vanilla = 0;
        for (EntityType<?> type : living()) {
            if (id(type).getNamespace().equals("minecraft")) {
                vanilla++;
                continue;
            }
            byMod.computeIfAbsent(WorldTools.modName(id(type).getNamespace()), k -> new ArrayList<>()).add(shortLine(level, type));
        }
        StringBuilder out = new StringBuilder("Every creature on this server, by mod (plus Minecraft's own " + vanilla
                + "), with what it is like and its skin's main colours; find_creatures with a name gives the details:\n");
        byMod.forEach((mod, list) -> {
            list.sort(String::compareTo);
            out.append("## ").append(mod).append(": ").append(String.join("; ", list)).append('\n');
        });
        return out.toString().strip();
    }

    /** The creatures loaded around the player now, whether or not they are on screen. */
    private static String nearby(ServerPlayer player) {
        record Group(EntityType<?> type, int[] counts, double[] nearest, double[] height) {
        }
        Map<EntityType<?>, Group> groups = new LinkedHashMap<>();
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(AROUND),
                e -> e != player && !(e instanceof Player) && e.isAlive() && !e.isInvisibleTo(player))) {
            double dist = e.distanceTo(player);
            if (dist > AROUND) continue;
            Group g = groups.computeIfAbsent(e.getType(), t -> new Group(t, new int[3], new double[] {Double.MAX_VALUE}, new double[1]));
            g.counts[0]++;
            if (player.hasLineOfSight(e)) g.counts[1]++;
            if (!e.onGround() && !e.isInWater() && !e.isPassenger()) g.counts[2]++;
            if (dist < g.nearest[0]) {
                g.nearest[0] = dist;
                g.height[0] = e.getY() - player.getY();
            }
        }
        if (groups.isEmpty()) return "No creatures within " + AROUND + " blocks of them right now.";
        List<Group> list = new ArrayList<>(groups.values());
        list.sort(Comparator.comparingDouble(g -> g.nearest[0]));
        StringBuilder out = new StringBuilder("Creatures within " + AROUND + " blocks of them right now, nearest first "
                + "(in sight means they could see it from where they stand):\n");
        for (Group g : list) {
            out.append("- ").append(g.counts[0]).append("x ").append(line(player.level(), g.type))
                    .append(String.format(Locale.ROOT, "; nearest %.0f blocks away, %s", g.nearest[0], height(g.height[0])));
            if (g.counts[1] > 0) out.append(", ").append(g.counts[1]).append(" in sight");
            if (g.counts[2] > 0) out.append(", ").append(g.counts[2]).append(" in the air");
            out.append('\n');
        }
        return out.toString().strip();
    }

    private static String height(double dy) {
        if (Math.abs(dy) < 3) return "level with them";
        return String.format(Locale.ROOT, "%.0f blocks %s them", Math.abs(dy), dy > 0 ? "above" : "below");
    }

    private static String describe(ServerPlayer player, MinecraftServer server, EntityType<?> type) {
        StringBuilder s = new StringBuilder("## ").append(line(player.level(), type)).append('\n');
        AttributeSupplier attributes = supplier(type);
        if (attributes != null) {
            s.append(String.format(Locale.ROOT, "Health %s", num(attributes.getBaseValue(Attributes.MAX_HEALTH))));
            if (attributes.hasAttribute(Attributes.ATTACK_DAMAGE) && attributes.getBaseValue(Attributes.ATTACK_DAMAGE) > 0) {
                s.append(", melee attack ").append(num(attributes.getBaseValue(Attributes.ATTACK_DAMAGE)));
            }
            if (attributes.hasAttribute(Attributes.ARMOR) && attributes.getBaseValue(Attributes.ARMOR) > 0) {
                s.append(", armor ").append(num(attributes.getBaseValue(Attributes.ARMOR)));
            }
            s.append(" (2 health is one heart)");
        }
        s.append(String.format(Locale.ROOT, "; %s blocks wide and %s tall", num(type.getWidth()), num(type.getHeight())));
        if (type.fireImmune()) s.append("; fire and lava don't hurt it");
        s.append(".\nSpawns by itself: ").append(spawns(server, type)).append('\n');
        s.append("A player's kill drops (rolled ").append(DROP_ROLLS).append(" times, average and range): ")
                .append(drops(player, type));
        return s.toString();
    }

    /** One creature in a line: name, id, mod, what it is like and its colours. */
    private static String line(Level level, EntityType<?> type) {
        StringBuilder s = new StringBuilder(name(type)).append(" (").append(id(type)).append(", ")
                .append(WorldTools.modName(id(type).getNamespace())).append(')');
        List<String> words = new ArrayList<>(traits(level, type).words());
        String colours = colours(type);
        if (!colours.isEmpty()) words.add(colours);
        if (!words.isEmpty()) s.append(": ").append(String.join(", ", words));
        return s.toString();
    }

    private static String shortLine(Level level, EntityType<?> type) {
        List<String> words = new ArrayList<>(traits(level, type).words());
        String colours = colours(type);
        if (!colours.isEmpty()) words.add(colours);
        return name(type) + (words.isEmpty() ? "" : " (" + String.join(", ", words) + ")");
    }

    private static String spawns(MinecraftServer server, EntityType<?> type) {
        Map<String, List<String>> byDimension = new LinkedHashMap<>();
        int weight = 0, min = Integer.MAX_VALUE, max = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<String> found = new ArrayList<>();
            for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                for (MobCategory category : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData data : biome.value().getMobSettings().getMobs(category).unwrap()) {
                        if (data.type != type) continue;
                        String name = biome.unwrapKey().map(ResourceKey::location).map(Creatures::biomeName).orElse("?");
                        if (!found.contains(name)) found.add(name);
                        weight = Math.max(weight, data.getWeight().asInt());
                        min = Math.min(min, data.minCount);
                        max = Math.max(max, data.maxCount);
                    }
                }
            }
            if (!found.isEmpty()) byDimension.put(dimensionName(level), found);
        }
        if (byDimension.isEmpty()) {
            return "not in any biome's spawn list; it comes from a structure, a spawner, a summoning, another creature or an event";
        }
        List<String> parts = new ArrayList<>();
        byDimension.forEach((dim, list) -> {
            list.sort(String::compareTo);
            String shown = String.join(", ", list.subList(0, Math.min(MAX_BIOMES, list.size())));
            parts.add(dim + " in " + list.size() + " biome" + (list.size() == 1 ? "" : "s") + " (" + shown
                    + (list.size() > MAX_BIOMES ? ", ..." : "") + ")");
        });
        return String.join("; ", parts) + String.format(Locale.ROOT, "; in groups of %d to %d, spawn weight %d. Its own "
                + "rules (time of day, light, height, ground) can narrow this down further", min, max, weight);
    }

    private static String biomeName(ResourceLocation id) {
        String key = net.minecraft.Util.makeDescriptionId("biome", id);
        String name = net.minecraft.network.chat.Component.translatable(key).getString();
        return name.equals(key) ? id.toString() : name;
    }

    private static String dimensionName(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        return switch (id.toString()) {
            case "minecraft:overworld" -> "the Overworld";
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> id.toString();
        };
    }

    private static String drops(ServerPlayer player, EntityType<?> type) {
        ServerLevel level = player.serverLevel();
        Entity entity;
        try {
            entity = type.create(level);
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
        if (entity == null) return "unknown";
        entity.moveTo(player.position());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
        Map<String, int[]> totals = new LinkedHashMap<>(); // sum, min, max
        for (int i = 0; i < DROP_ROLLS; i++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, entity)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.ENTITY);
            Map<String, Integer> once = new HashMap<>();
            for (ItemStack stack : table.getRandomItems(params)) if (!stack.isEmpty()) once.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
            for (String name : totals.keySet()) once.putIfAbsent(name, 0);
            boolean first = i == 0;
            for (Map.Entry<String, Integer> e : once.entrySet()) {
                int[] t = totals.computeIfAbsent(e.getKey(), k -> new int[] {0, first ? Integer.MAX_VALUE : 0, 0});
                t[0] += e.getValue();
                t[1] = Math.min(t[1], e.getValue());
                t[2] = Math.max(t[2], e.getValue());
            }
        }
        entity.discard();
        if (totals.isEmpty()) return "nothing (plus experience)";
        List<String> parts = new ArrayList<>();
        totals.forEach((name, t) -> parts.add(String.format(Locale.ROOT, "%.2f %s (%d to %d)", t[0] / (double) DROP_ROLLS, name, t[1], t[2])));
        return String.join(", ", parts) + "; Looting raises some of these";
    }

    private static Traits traits(Level level, EntityType<?> type) {
        synchronized (TRAITS) {
            Traits known = TRAITS.get(type);
            if (known != null) return known;
        }
        boolean boss = type.is(Tags.EntityTypes.BOSSES), large = type.getWidth() >= 2 || type.getHeight() >= 2.5f;
        Traits traits = new Traits(false, false, false, boss, large);
        try {
            Entity e = type.create(level);
            if (e != null) {
                boolean flies = e instanceof FlyingAnimal || e instanceof net.minecraft.world.entity.FlyingMob
                        || e instanceof Mob m && m.getNavigation() instanceof FlyingPathNavigation;
                boolean swims = e instanceof WaterAnimal || e instanceof Mob m && (m.getNavigation() instanceof WaterBoundPathNavigation
                        || m.getNavigation() instanceof AmphibiousPathNavigation);
                traits = new Traits(flies, swims, e instanceof Enemy, boss, large);
                e.discard();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A creature that can't be made away from the world keeps no traits but is still listed.
        }
        synchronized (TRAITS) {
            TRAITS.put(type, traits);
        }
        return traits;
    }

    /**
     * The main colours of a creature's skin texture, read from its mod's jar, such as "green and yellow". Reading the
     * jars takes seconds, so warmUp does it in the background and a creature not read yet has no colour words.
     */
    private static String colours(EntityType<?> type) {
        return COLOURS.getOrDefault(type, "");
    }

    private static String readColours(EntityType<?> type) {
        ResourceLocation id = id(type);
        List<String> textures = TEXTURES.computeIfAbsent(id.getNamespace(), Creatures::entityTextures);
        if (textures.isEmpty()) return "";
        String path = id.getPath(), best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String rel : textures) {
            String base = rel.substring(rel.lastIndexOf('/') + 1, rel.length() - 4);
            int score = base.equals(path) ? 0 : rel.startsWith(path + "/") && base.startsWith(path) ? 1
                    : rel.startsWith(path + "/") ? 2 : base.startsWith(path + "_") ? 3 : Integer.MAX_VALUE;
            if (score == Integer.MAX_VALUE) continue;
            if (base.contains("glow") || base.contains("eye") || base.contains("emissive") || base.contains("overlay")) score += 10;
            if (score < bestScore || score == bestScore && rel.length() < best.length()) {
                best = rel;
                bestScore = score;
            }
        }
        Path root = TEXTURE_ROOTS.get(id.getNamespace());
        if (best == null || root == null) return "";
        try (InputStream in = Files.newInputStream(root.resolve(best))) {
            BufferedImage image = ImageIO.read(in);
            return image == null ? "" : colourWords(image);
        } catch (Exception e) {
            return "";
        }
    }

    private static final Map<String, List<String>> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Path> TEXTURE_ROOTS = new ConcurrentHashMap<>();

    /** Every texture under a mod's textures/entity folder, as paths inside it; walked once per mod. */
    private static List<String> entityTextures(String namespace) {
        if (namespace.equals("minecraft")) return List.of(); // a server has no Minecraft textures; the book knows them
        var file = ModList.get().getModFileById(namespace);
        if (file == null) return List.of();
        Path root = file.getFile().findResource("assets", namespace, "textures", "entity");
        if (!Files.isDirectory(root)) return List.of();
        List<String> list = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.map(p -> root.relativize(p).toString().replace('\\', '/')).filter(r -> r.endsWith(".png")).forEach(list::add);
        } catch (Exception e) {
            return List.of();
        }
        TEXTURE_ROOTS.put(namespace, root);
        return list;
    }

    /** Colour names by where each hue range ends, in degrees. */
    private static final int[] HUE_ENDS = {15, 40, 65, 160, 200, 255, 290, 335, 360};
    private static final String[] HUES = {"red", "orange", "yellow", "green", "blue-green", "blue", "purple", "pink", "red"};

    /** Names the colours that cover most of a texture's visible pixels. */
    static String colourWords(BufferedImage image) {
        Map<String, Double> shares = new HashMap<>();
        double total = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 128) continue;
                float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
                String word;
                if (hsb[2] < 0.18f) word = "black";
                else if (hsb[1] < 0.18f) word = hsb[2] > 0.8f ? "white" : "grey";
                else {
                    int hue = (int) (hsb[0] * 360), k = 0;
                    while (k < HUE_ENDS.length - 1 && hue >= HUE_ENDS[k]) k++;
                    word = HUES[k];
                    if ((word.equals("orange") || word.equals("red") || word.equals("yellow")) && hsb[2] < 0.55f) word = "brown";
                }
                shares.merge(word, 1.0, Double::sum);
                total++;
            }
        }
        if (total == 0) return "";
        double all = total;
        List<Map.Entry<String, Double>> list = new ArrayList<>(shares.entrySet());
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        List<String> words = new ArrayList<>();
        for (Map.Entry<String, Double> e : list) {
            if (words.size() < 2 && (words.isEmpty() || e.getValue() / all >= 0.2)) words.add(e.getKey());
        }
        return String.join(" and ", words);
    }

    private static List<EntityType<?>> living() {
        List<EntityType<?>> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != EntityType.PLAYER && supplier(type) != null) list.add(type);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static AttributeSupplier supplier(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type) ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type) : null;
    }

    private static String name(EntityType<?> type) {
        return type.getDescription().getString();
    }

    private static ResourceLocation id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}

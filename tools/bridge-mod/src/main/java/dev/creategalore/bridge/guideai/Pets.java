package dev.creategalore.bridge.guideai;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Whether a player can keep a creature as a pet, found out by trying: a stand-in player hands a creature made for the
 * test (never added to the world) every food and taming item there is, as often as it takes, and the creature's own
 * code decides. So the book never tells a player a creature can't be tamed because its notes don't mention it, and it
 * gets the real item and odds from the mod itself. After taming, it reads whether the creature sits when told, follows
 * its owner (and whether that can be switched) and stays in the world.
 */
final class Pets {
    /** Tries per item: enough to see a 1 in 10 chance about 10 times. */
    private static final int TRIES = 100, MAX_ITEMS = 6;
    private static final Map<EntityType<?>, String> KNOWN = new HashMap<>();
    private static final GameProfile PROBE = new GameProfile(
            UUID.nameUUIDFromBytes("create_galore_bridge:field_guide_pet_probe".getBytes(StandardCharsets.UTF_8)), "[Field Guide]");

    private Pets() {
    }

    /** Whether the creature can be tamed (it has an owner once tamed); cheap, for the search words. */
    static boolean tameable(Entity e) {
        return e instanceof OwnableEntity;
    }

    /** How a player keeps this creature as a pet, worked out once per creature on the server thread. */
    static String describe(ServerLevel level, EntityType<?> type) {
        synchronized (KNOWN) {
            String known = KNOWN.get(type);
            if (known != null) return known;
        }
        String found;
        try {
            found = probe(level, type);
        } catch (RuntimeException | LinkageError e) {
            found = "unknown (trying it failed on this server)";
        }
        synchronized (KNOWN) {
            KNOWN.put(type, found);
        }
        return found;
    }

    private static String probe(ServerLevel level, EntityType<?> type) {
        Entity first = type.create(level);
        if (first == null) return "unknown";
        boolean ownable = first instanceof OwnableEntity, horse = first instanceof AbstractHorse;
        first.discard();
        if (!ownable && husbandry(type)) {
            return "as a farm animal, not a pet: Animal Husbandry counts it tamed once a player hands it its food (see the "
                    + "notes on farm animals); it never sits or follows";
        }
        if (!ownable) return "no; it never gets an owner, so it can't be tamed, made to sit or follow (a lead, a bucket or "
                + "breeding may still work)";
        if (horse) return "yes, like a horse: ride it again and again until hearts show; food it likes makes that quicker";

        FakePlayer player = probePlayer(level);
        Map<Item, Integer> tamedBy = new HashMap<>();
        for (Item item : candidates(level, type)) {
            int tamed = 0;
            boolean reacts = false;
            for (int i = 0; i < TRIES; i++) {
                Mob mob = fresh(level, type);
                if (mob == null) break;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
                var result = mob.interact(player, InteractionHand.MAIN_HAND);
                boolean owned = ((OwnableEntity) mob).getOwnerUUID() != null;
                mob.discard();
                if (owned) tamed++;
                if (result.consumesAction()) reacts = true;
                // An item the wild creature ignores (nothing taken, no owner) is not how it is tamed.
                if (i == 0 && !reacts && !owned) break;
            }
            if (tamed > 0) tamedBy.put(item, tamed);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().clearContent();
        if (tamedBy.isEmpty()) {
            return "it can have an owner, but no item tamed it when tried (" + TRIES + " times each with every food and "
                    + "taming item); it is tamed some other way (a mod's own rule, a structure, hatching or breeding), or "
                    + "only spawns tamed";
        }

        List<Map.Entry<Item, Integer>> list = new ArrayList<>(tamedBy.entrySet());
        list.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        List<String> items = new ArrayList<>();
        for (int i = 0; i < list.size() && i < MAX_ITEMS; i++) {
            Map.Entry<Item, Integer> e = list.get(i);
            items.add(new ItemStack(e.getKey()).getHoverName().getString() + " [" + BuiltInRegistries.ITEM.getKey(e.getKey())
                    + "] " + odds(e.getValue()));
        }
        StringBuilder s = new StringBuilder("yes; hand a wild one ").append(String.join(", ", items));
        if (list.size() > MAX_ITEMS) s.append(" and ").append(list.size() - MAX_ITEMS).append(" more items");
        s.append(" (each one handed over is a try, the odds as tried here ").append(TRIES).append(" times)");
        s.append(afterTaming(level, type, list.get(0).getKey(), player));
        return s.toString();
    }

    /** What the tamed creature does: an empty-handed right-click from its owner, following and staying around. */
    private static String afterTaming(ServerLevel level, EntityType<?> type, Item tamer, FakePlayer player) {
        Mob mob = null;
        for (int i = 0; i < TRIES && mob == null; i++) {
            Mob m = fresh(level, type);
            if (m == null) break;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tamer, 64));
            m.interact(player, InteractionHand.MAIN_HAND);
            if (((OwnableEntity) m).getOwnerUUID() != null) mob = m;
            else m.discard();
        }
        if (mob == null) return "";
        List<String> parts = new ArrayList<>();
        // Whether the owner's empty-handed right-click does it can't be tried here (the stand-in owner isn't in the
        // world, and a creature only obeys an owner it can find), so this reads the goal that makes it sit when told.
        boolean sits = false, follows = false;
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            sits |= goal.getGoal() instanceof SitWhenOrderedToGoal;
            follows |= goal.getGoal() instanceof FollowOwnerGoal;
        }
        if (sits) parts.add("its owner can tell it to sit and stand (for most pets a right-click with an empty hand)");
        if (follows) {
            Boolean on = followSwitch(mob);
            if (on == null) parts.add("it follows its owner");
            else if (on) parts.add("it follows its owner from the moment it is tamed; its follow can be switched off and on"
                    + (whistle(type) ? " by right-clicking it with a Whistle [naturalist:whistle] (wandering or following)" : ""));
            else parts.add("it doesn't follow until its follow is switched on"
                    + (whistle(type) ? " by right-clicking it with a Whistle [naturalist:whistle]" : ""));
        }
        if (!mob.removeWhenFarAway(1024)) parts.add("it never despawns");
        mob.discard();
        player.getInventory().clearContent();
        return parts.isEmpty() ? "" : ". Once tamed: " + String.join("; ", parts);
    }

    /** A mod's own on/off for following (Naturalist's pets have one), or null when it has none. */
    private static Boolean followSwitch(Mob mob) {
        try {
            Method m = mob.getClass().getMethod("isFollowingOwner");
            if (m.getReturnType() == boolean.class) return (Boolean) m.invoke(mob);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No switch: it follows whenever it isn't sitting.
        }
        return null;
    }

    /** The farm animals Animal Husbandry looks after; it tames them its own way, without an owner. */
    private static boolean husbandry(EntityType<?> type) {
        if (!net.neoforged.fml.ModList.get().isLoaded("animalhusbandry")) return false;
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("animalhusbandry") || type == EntityType.COW
                || type == EntityType.PIG || type == EntityType.SHEEP || type == EntityType.GOAT || type == EntityType.CHICKEN;
    }

    private static boolean whistle(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("naturalist")
                && BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("naturalist", "whistle"));
    }

    private static final Map<ServerLevel, FakePlayer> PLAYERS = new java.util.WeakHashMap<>();

    private static FakePlayer probePlayer(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, ProbePlayer::new);
    }

    /**
     * The stand-in player. Taming a creature gives its tamer an advancement, and an advancement is announced in chat,
     * so this one keeps a record of its own that never awards anything: nobody sees "[Field Guide] has made the
     * advancement".
     */
    private static final class ProbePlayer extends FakePlayer {
        private final PlayerAdvancements quiet;

        ProbePlayer(ServerLevel level) {
            super(level, PROBE);
            var server = level.getServer();
            Path none = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve("create_galore_pet_probe_never_saved.json");
            quiet = new PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(), server.getAdvancements(), none, this) {
                @Override
                public boolean award(AdvancementHolder advancement, String criterion) {
                    return false;
                }

                @Override
                public void save() {
                }
            };
        }

        @Override
        public PlayerAdvancements getAdvancements() {
            return quiet;
        }
    }

    private static Mob fresh(ServerLevel level, EntityType<?> type) {
        Entity e = type.create(level);
        if (e instanceof Mob mob) {
            mob.moveTo(0, level.getMaxBuildHeight() + 16, 0);
            return mob;
        }
        if (e != null) e.discard();
        return null;
    }

    /**
     * The items worth trying: anything the creature eats, anything with food on it, the items of every tag named for
     * taming, and bones and seeds (wolves and parrots take those). Spawn eggs, buckets, leads and name tags do their own
     * thing on a creature and are left out.
     */
    private static Set<Item> candidates(ServerLevel level, EntityType<?> type) {
        Set<Item> items = new LinkedHashSet<>();
        Entity e = type.create(level);
        Animal animal = e instanceof Animal a ? a : null;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item instanceof SpawnEggItem || item instanceof BucketItem || item instanceof MobBucketItem
                    || item instanceof LeadItem || item instanceof NameTagItem) continue;
            ItemStack stack = new ItemStack(item);
            boolean food = stack.has(DataComponents.FOOD) || item == Items.BONE || stack.is(Tags.Items.SEEDS);
            if (!food && animal != null) {
                try {
                    food = animal.isFood(stack);
                } catch (RuntimeException ignored) {
                    // A creature whose food check fails on a bare stack just isn't offered that item.
                }
            }
            if (food) items.add(item);
        }
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            String path = pair.getFirst().location().getPath().toLowerCase(Locale.ROOT);
            if (path.contains("tame") || path.contains("taming")) pair.getSecond().forEach(h -> items.add(h.value()));
        });
        if (e != null) e.discard();
        return items;
    }

    private static String odds(int tamed) {
        double p = tamed / (double) TRIES;
        if (p >= 0.95) return "(tames on the first try)";
        long oneIn = Math.round(1 / p);
        return oneIn <= 1 ? "(about 1 try in 1 to 2)" : "(about 1 try in " + oneIn + ")";
    }
}

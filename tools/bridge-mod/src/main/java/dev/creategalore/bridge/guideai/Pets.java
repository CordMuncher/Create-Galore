package dev.creategalore.bridge.guideai;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Whether a player can keep a creature as a pet and what a pet of it does, found out by trying, the same way for every
 * creature of every mod. A stand-in player hands a creature made for the test (never added to the world) every food and
 * taming item there is, as often as it takes, and the creature's own code decides. Then, for check_pet, the stand-in is
 * the owner of a tamed one and really right-clicks it: with an empty hand, with Shift, and with every food, taming item
 * and item of the creature's own mod, each on a fresh copy of the pet, and reads what changed (sitting, breeding, health,
 * riding, an inventory, equipment, the mod's own on/off switches). It also checks for real whether the pet would start
 * after its owner, fights for its owner, and whether its babies are the owner's. So the book never needs notes on a
 * single pet: what it says comes from the mod itself, on this server.
 */
final class Pets {
    /** Tries per item: enough to see a 1 in 10 chance about 10 times. */
    private static final long SLICE_NANOS = 15_000_000L;
    private static final int TRIES = 100, EXTRA_TRIES = 900, MAX_ITEMS = 6, MAX_PER_EFFECT = 4, FOLLOW_SAMPLES = 20;
    private static final Map<EntityType<?>, String> KNOWN = new HashMap<>(), CHECKED = new HashMap<>();
    private static final GameProfile PROBE = new GameProfile(
            UUID.nameUUIDFromBytes("create_galore_bridge:field_guide_pet_probe".getBytes(StandardCharsets.UTF_8)), "[Field Guide]");
    /** While a test slice runs (server thread only), nothing it does may put an entity into the world: drops, babies, XP. */
    private static boolean testing;
    private static int blocked;

    static {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (testing && !event.getLevel().isClientSide()) {
                event.setCanceled(true);
                blocked++;
            }
        });
    }

    private Pets() {
    }

    /** Whether the creature can be tamed (it has an owner once tamed); cheap, for the search words. */
    static boolean tameable(Entity e) {
        return e instanceof OwnableEntity;
    }

    /** Whether and how a player tames this creature, worked out once per creature on the server thread. */
    static String describe(ServerLevel level, EntityType<?> type) {
        synchronized (KNOWN) {
            String known = KNOWN.get(type);
            if (known != null) return known;
        }
        String found;
        try {
            Taming t = taming(new Runner(level, false), type);
            if (t.tamed != null) t.tamed.discard();
            found = t.text;
        } catch (RuntimeException | LinkageError e) {
            found = "unknown (trying it failed on this server)";
        }
        synchronized (KNOWN) {
            KNOWN.put(type, found);
        }
        return found;
    }

    /**
     * check_pet: the whole trial for the creature that best matches the query. Called off the server thread (the book's
     * worker), it runs the world work in short slices, one a tick, so the server never stalls while a big trial (a cat
     * has some 200 fish to try) runs; on the server thread it runs at once.
     */
    static String check(MinecraftServer server, UUID asker, String query) {
        try {
            ServerPlayer player = onServer(server, () -> server.getPlayerList().getPlayer(asker));
            return player == null ? "The player has left the server." : checkIn(player.serverLevel(), query);
        } catch (RuntimeException e) {
            return "Trying it failed on this server (" + e.getMessage() + ").";
        }
    }

    /** The same for a player in hand (the casebook's stand-in): on the server thread it runs at once. */
    static String check(ServerPlayer player, String query) {
        try {
            return checkIn(player.serverLevel(), query);
        } catch (RuntimeException e) {
            return "Trying it failed on this server (" + e.getMessage() + ").";
        }
    }

    private static String checkIn(ServerLevel level, String query) {
        List<EntityType<?>> hits = Creatures.matches(level, query);
        if (hits.isEmpty()) return "No creature on this server matches '" + query + "'; find_creatures finds one by its looks.";
        EntityType<?> type = hits.get(0);
        String result;
        synchronized (CHECKED) {
            result = CHECKED.get(type);
        }
        if (result == null) {
            try {
                result = trial(new Runner(level, false), type);
            } catch (RuntimeException | LinkageError e) {
                result = "Trying it failed on this server (" + e.getClass().getSimpleName() + "), so nothing is known for sure.";
            }
            synchronized (CHECKED) {
                CHECKED.put(type, result);
            }
        }
        StringBuilder out = new StringBuilder("## ").append(Creatures.nameOf(type)).append(", tried on this server\n").append(result);
        if (hits.size() > 1) {
            List<String> rest = new ArrayList<>();
            for (int i = 1; i < hits.size() && i < 6; i++) rest.add(Creatures.nameOf(hits.get(i)));
            out.append("\nAlso matching (ask check_pet by id): ").append(String.join("; ", rest));
        }
        return out.toString();
    }

    private static <T> T onServer(MinecraftServer server, java.util.function.Supplier<T> work) {
        if (server.isSameThread()) return work.get();
        try {
            return server.submit(work).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("the server was too busy", e);
        }
    }

    /**
     * Runs a trial's world work on the server thread: at once when already on it, otherwise in slices of at most
     * {@link #SLICE_NANOS} a tick. Each slice blocks entities from joining the world and, once the pet is tamed, puts the
     * stand-in owner in the world's player list for the pet to find (getOwner looks there), taking it out again before
     * the slice ends, so nothing else in the world ever sees it.
     */
    private static final class Runner {
        final ServerLevel level;
        final ProbePlayer owner;
        boolean ownerPresent;

        Runner(ServerLevel level, boolean ownerPresent) {
            this.level = level;
            this.owner = probePlayer(level);
            this.ownerPresent = ownerPresent;
        }

        <T> T get(java.util.function.Supplier<T> work) {
            return onServer(level.getServer(), () -> guarded(work));
        }

        void run(Runnable work) {
            get(() -> {
                work.run();
                return null;
            });
        }

        /** Each item in turn, as many as fit in a slice each tick. */
        <T> void each(List<T> list, java.util.function.Consumer<T> work) {
            boolean direct = level.getServer().isSameThread();
            int[] next = {0};
            while (next[0] < list.size()) {
                run(() -> {
                    long end = System.nanoTime() + SLICE_NANOS;
                    while (next[0] < list.size() && (direct || System.nanoTime() < end)) work.accept(list.get(next[0]++));
                });
            }
        }

        private <T> T guarded(java.util.function.Supplier<T> work) {
            boolean added = ownerPresent && !level.players().contains(owner);
            if (added) level.players().add(owner);
            testing = true;
            try {
                return work.get();
            } finally {
                testing = false;
                if (added) level.players().remove(owner);
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    /** How taming went: the answer text, the item that tames best (or null) and one tamed creature (or null). */
    private record Taming(String text, Item item, Mob tamed) {
    }

    private static Taming taming(Runner on, EntityType<?> type) {
        ServerLevel level = on.level;
        ProbePlayer player = on.owner;
        int kind = on.get(() -> {
            Entity first = type.create(level);
            if (first == null) return -1;
            int k = first instanceof AbstractHorse ? 2 : first instanceof OwnableEntity ? 1 : 0;
            first.discard();
            return k;
        });
        if (kind < 0) return new Taming("unknown", null, null);
        if (kind == 0 && husbandry(type)) {
            return new Taming("as a farm animal, not a pet: Animal Husbandry counts it tamed once a player hands it its food (see "
                    + "the notes on farm animals); it never sits or follows", null, null);
        }
        if (kind == 0) return new Taming("no; it never gets an owner, so it can't be tamed, made to sit or follow (a lead, a "
                + "bucket or breeding may still work)", null, null);
        if (kind == 2) {
            Mob m = on.get(() -> {
                Mob h = fresh(level, type);
                if (h instanceof AbstractHorse horse) horse.tameWithName(player);
                return h;
            });
            return new Taming("yes, like a horse: ride it again and again until hearts show; food it likes makes that quicker",
                    null, m);
        }

        Map<Item, Integer> tamedBy = new HashMap<>();
        Mob[] tamed = {null};
        List<Item> candidates = new ArrayList<>(on.get(() -> candidates(level, type)));
        on.each(candidates, item -> {
            int count = 0;
            boolean reacts = false;
            for (int i = 0; i < TRIES; i++) {
                Mob mob = fresh(level, type);
                if (mob == null) break;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
                var result = mob.interact(player, InteractionHand.MAIN_HAND);
                boolean owned = ((OwnableEntity) mob).getOwnerUUID() != null;
                if (owned && tamed[0] == null) tamed[0] = mob;
                else mob.discard();
                if (owned) count++;
                if (result.consumesAction()) reacts = true;
                // An item the wild creature ignores (nothing taken, no owner) is not how it is tamed.
                if (i == 0 && !reacts && !owned) break;
            }
            if (count > 0) tamedBy.put(item, count);
        });
        if (tamedBy.isEmpty()) {
            return new Taming("it can have an owner, but no item tamed it when tried (" + TRIES + " times each with every "
                    + "food and taming item); it is tamed some other way (a mod's own rule, a structure, hatching or "
                    + "breeding), or only spawns tamed", null, null);
        }
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(tamedBy.entrySet());
        list.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        // The items named get more tries, so their odds come close to the mod's own (100 tries of a 1 in 10 chance
        // can come out anywhere from 1 in 7 to 1 in 20). Counts become tries out of TRIES + EXTRA_TRIES.
        for (int n = 0; n < list.size(); n++) {
            Map.Entry<Item, Integer> e = list.get(n);
            if (n >= MAX_ITEMS || e.getValue() >= TRIES * 0.95) {
                e.setValue(e.getValue() * (TRIES + EXTRA_TRIES) / TRIES);
                continue;
            }
            int[] count = {e.getValue()};
            on.each(java.util.Collections.nCopies(EXTRA_TRIES, e.getKey()), item -> {
                Mob mob = fresh(level, type);
                if (mob == null) return;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 64));
                mob.interact(player, InteractionHand.MAIN_HAND);
                if (((OwnableEntity) mob).getOwnerUUID() != null) count[0]++;
                mob.discard();
            });
            e.setValue(count[0]);
        }
        on.run(() -> player.getInventory().clearContent());
        list.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        List<String> items = new ArrayList<>();
        for (int i = 0; i < list.size() && i < MAX_ITEMS; i++) {
            Map.Entry<Item, Integer> e = list.get(i);
            items.add(label(e.getKey()) + " " + odds(e.getValue() / (double) (TRIES + EXTRA_TRIES)));
        }
        StringBuilder s = new StringBuilder("yes; hand a wild one ").append(String.join(", ", items));
        if (list.size() > MAX_ITEMS) s.append(" and ").append(list.size() - MAX_ITEMS).append(" more items");
        s.append(" (each one handed over is a try, the odds as tried here ").append(TRIES + EXTRA_TRIES).append(" times). check_pet tries "
                + "out what a tamed one does");
        return new Taming(s.toString(), list.get(0).getKey(), tamed[0]);
    }

    /** The whole trial: taming, then the owner's right-clicks, following, fighting for its owner, babies, despawning. */
    private static String trial(Runner on, EntityType<?> type) {
        ServerLevel level = on.level;
        ProbePlayer owner = on.owner;
        Taming taming = taming(on, type);
        StringBuilder out = new StringBuilder("Taming: ").append(taming.text.replace(". check_pet tries out what a tamed one does", ""));
        Mob found = taming.tamed;
        if (found == null && taming.text.startsWith("it can have an owner")) {
            // Tamed some other way: still show what a pet of it does, made the owner's directly.
            found = on.get(() -> {
                Mob m = fresh(level, type);
                if (m instanceof TamableAnimal t) {
                    t.tame(owner);
                    return m;
                }
                if (m != null) m.discard();
                return null;
            });
            if (found != null) out.append("\nIf a player gets one tamed, this is what it does:");
        }
        if (found == null) return out.toString();
        Mob tamed = found;
        boolean sitsAtFirst = tamed instanceof TamableAnimal t0 && t0.isOrderedToSit();
        CompoundTag pet = on.get(() -> {
            if (tamed instanceof TamableAnimal t0) {
                // Tests start from a standing pet; a creature that sits as soon as it is tamed is told so.
                t0.setOrderedToSit(false);
                t0.setInSittingPose(false);
            }
            CompoundTag tag = tamed.saveWithoutId(new CompoundTag());
            tamed.discard();
            return tag;
        });
        if (sitsAtFirst) out.append("\nA newly tamed one sits down at first; an empty-hand right-click gets it up.");

        on.ownerPresent = true;
        owner.tickCount = 1000;
        SWITCHED.clear();

        // Right-clicks by the owner, each on a fresh copy of the pet.
        Effect empty = on.get(() -> click(level, type, pet, owner, ItemStack.EMPTY, false, false));
        Effect again = on.get(() -> click(level, type, pet, owner, ItemStack.EMPTY, false, true));
        owner.askedShift = false;
        Effect shift = on.get(() -> click(level, type, pet, owner, ItemStack.EMPTY, true, false));
        // Its own Shift action: its code asked about Shift and did something else than a plain click (the same rule
        // PatPat follows in this pack, see client/PatPriority).
        boolean ownShift = owner.askedShift && !shift.text.isEmpty() && !shift.text.equals(empty.text);
        out.append("\nIts owner's right-click (tried on a tamed one):");
        out.append("\n- with an empty hand: ").append(empty.text.isEmpty() ? "nothing happens" : empty.text);
        if (!again.text.isEmpty() && !again.text.equals(empty.text)) out.append("; a second time: ").append(again.text);
        if (ownShift) {
            out.append("\n- Shift + empty hand: ").append(shift.text).append(" (its own Shift action, so PatPat doesn't pat it)");
        } else {
            out.append("\n- Shift + empty hand: pats it (PatPat); the plain right-click doesn't happen then");
        }

        // Items with the same effect are listed together; how much each food heals is given as a range.
        Map<String, List<String>> byEffect = new LinkedHashMap<>();
        Map<String, float[]> heals = new HashMap<>();
        List<Item> items = new ArrayList<>(on.get(() -> trialItems(level, type)));
        on.each(items, item -> {
            Effect full = click(level, type, pet, owner, new ItemStack(item, 64), false, false);
            Effect hurt = click(level, type, pet, owner, new ItemStack(item, 64), false, false, true);
            List<String> parts = new ArrayList<>();
            if (!full.text.isEmpty() && !full.text.equals(empty.text)) parts.add(full.text);
            if (hurt.healed > 0) parts.add("heals it when hurt");
            if (parts.isEmpty()) return;
            String key = String.join("; ", parts);
            byEffect.computeIfAbsent(key, k -> new ArrayList<>()).add(label(item));
            if (hurt.healed > 0) {
                float[] r = heals.computeIfAbsent(key, k -> new float[] {Float.MAX_VALUE, 0});
                r[0] = Math.min(r[0], hurt.healed);
                r[1] = Math.max(r[1], hurt.healed);
            }
        });
        byEffect.forEach((effect, list) -> {
            out.append("\n- ").append(String.join(", ", list.subList(0, Math.min(MAX_PER_EFFECT, list.size()))));
            if (list.size() > MAX_PER_EFFECT) out.append(" and ").append(list.size() - MAX_PER_EFFECT).append(" more");
            out.append(": ").append(effect);
            float[] r = heals.get(effect);
            if (r != null) out.append(" (").append(r[0] == r[1] ? num(r[0]) : num(r[0]) + " to " + num(r[1])).append(" health)");
        });
        if (!empty.text.isEmpty()) out.append("\n- other items tried do the same as an empty hand, or nothing");

        // Following: whether any of its goals starts when the owner is far and not when near, standing and sitting.
        out.append(on.get(() -> {
            Mob m = copy(level, type, pet);
            if (m == null) return "";
            StringBuilder f = new StringBuilder();
            String follow = follows(m, owner);
            if (follow == null) {
                f.append("\nFollowing: it doesn't start after its owner when they walk off (tried up to 24 blocks away)");
            } else {
                f.append("\nFollowing: it starts after its owner once they are ").append(follow).append(" away");
                if (m instanceof TamableAnimal t) {
                    t.setOrderedToSit(true);
                    f.append(follows(m, owner) == null ? ", not while told to sit" : ", even when told to sit");
                }
            }
            m.discard();
            return f.toString();
        }));
        for (Map.Entry<String, String> e : SWITCHED.entrySet()) out.append("\n").append(e.getValue());
        SWITCHED.clear();

        out.append("\nFighting: ").append(on.get(() -> fights(level, type, pet, owner)));
        String babies = on.get(() -> babies(level, type, pet, owner));
        if (babies != null) out.append("\nBabies: ").append(babies);
        out.append("\nDespawning: ").append(on.get(() -> {
            Mob d = copy(level, type, pet);
            if (d == null) return "unknown";
            boolean despawns = d.removeWhenFarAway(1024);
            d.discard();
            return despawns ? "a tamed one can still despawn when no one is near" : "a tamed one never despawns";
        }));
        on.run(() -> owner.getInventory().clearContent());
        out.append("\n(Things that need the real world, such as tricks, riding controls or a mod's own key, may not show here.)");
        return out.toString();
    }

    /** Lines about items that switch the pet's following, filled in by click() and printed after following. */
    private static final Map<String, String> SWITCHED = new LinkedHashMap<>();

    /** What one right-click changed. healed is the health gained. */
    private record Effect(String text, float healed) {
    }

    private static Effect click(ServerLevel level, EntityType<?> type, CompoundTag pet, ProbePlayer owner, ItemStack stack,
                                boolean shift, boolean twice) {
        return click(level, type, pet, owner, stack, shift, twice, false);
    }

    private static Effect click(ServerLevel level, EntityType<?> type, CompoundTag pet, ProbePlayer owner, ItemStack stack,
                                boolean shift, boolean twice, boolean hurt) {
        Mob mob = copy(level, type, pet);
        if (mob == null) return new Effect("", 0);
        try {
            if (hurt) mob.setHealth(mob.getMaxHealth() / 2);
            owner.moveTo(mob.getX() + 1.5, mob.getY(), mob.getZ(), 270, 0);
            owner.setShiftKeyDown(shift);
            if (twice) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                mob.interact(owner, InteractionHand.MAIN_HAND);
                if (owner.isPassenger()) owner.stopRiding();
                owner.containerMenu = owner.inventoryMenu;
            }
            owner.rode = false;
            owner.opened = false;
            State before = State.of(mob, owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            blocked = 0;
            InteractionResult result = mob.interact(owner, InteractionHand.MAIN_HAND);
            State after = State.of(mob, owner);
            String text = before.diff(after, result, blocked);
            if (!stack.isEmpty() && !after.switches.equals(before.switches)) {
                // An item that flips one of the mod's own switches: does the pet follow differently afterwards?
                Mob plain = copy(level, type, pet);
                String was = follows(plain, owner), now = follows(mob, owner);
                if (plain != null) plain.discard();
                if (!Objects.equals(was, now)) {
                    SWITCHED.put(label(stack.getItem()), "- " + label(stack.getItem()) + (now == null
                            ? " stops it following (right-click again to switch it back)" : " makes it follow again"));
                }
            }
            return new Effect(text, after.health - before.health);
        } catch (RuntimeException e) {
            return new Effect("", 0);
        } finally {
            owner.setShiftKeyDown(false);
            if (owner.isPassenger()) owner.stopRiding();
            owner.containerMenu = owner.inventoryMenu;
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mob.discard();
        }
    }

    /** What can be seen of a pet and its owner, to compare before and after a right-click. */
    private record State(boolean owned, boolean sitting, boolean inLove, float health, boolean removed, boolean riding,
                         boolean menu, List<ItemStack> gear, Map<String, Boolean> switches, List<Object> data) {
        static State of(Mob mob, ProbePlayer owner) {
            List<ItemStack> gear = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) gear.add(mob.getItemBySlot(slot).copy());
            List<Object> data = new ArrayList<>();
            List<SynchedEntityData.DataValue<?>> values = mob.getEntityData().getNonDefaultValues();
            if (values != null) for (var v : values) data.add(v.id() + "=" + v.value());
            return new State(((OwnableEntity) mob).getOwnerUUID() != null,
                    mob instanceof TamableAnimal t && t.isOrderedToSit(), mob instanceof Animal a && a.isInLove(),
                    mob.getHealth(), mob.isRemoved(), owner.rode || owner.getVehicle() == mob,
                    owner.opened || owner.containerMenu != owner.inventoryMenu,
                    gear, Pets.switches(mob), data);
        }

        String diff(State after, InteractionResult result, int spawned) {
            List<String> parts = new ArrayList<>();
            if (owned && !after.owned) parts.add("it is no longer tamed");
            if (!sitting && after.sitting) parts.add("it sits down and stays");
            if (sitting && !after.sitting) parts.add("it stands up again");
            if (!inLove && after.inLove) parts.add("it wants to breed (hearts; two such tamed ones make a baby)");
            if (after.health <= 0) parts.add("it dies");
            else if (after.health < health - 0.01f) parts.add("it gets hurt");
            if (!removed && after.removed && after.health > 0) parts.add("it is picked up or caught");
            if (!riding && after.riding) parts.add("the player rides it");
            if (!menu && after.menu) parts.add("its inventory opens");
            for (int i = 0; i < gear.size(); i++) {
                if (!ItemStack.matches(gear.get(i), after.gear.get(i))) {
                    parts.add("it wears it (" + EquipmentSlot.values()[i].getName() + " slot)");
                    break;
                }
            }
            switches.forEach((name, was) -> {
                Boolean now = after.switches.get(name);
                if (now != null && !now.equals(was)) parts.add("switches '" + name + "' " + (now ? "on" : "off"));
            });
            if (spawned > 0 && after.health > 0) parts.add("something drops or appears");
            if (parts.isEmpty() && !data.equals(after.data)) parts.add("it changes its look or a state of its own");
            if (parts.isEmpty() && result.consumesAction() && !after.removed) parts.add("it takes it, with no change seen here");
            return String.join("; ", parts);
        }
    }

    /** The creature's own mod's on/off states (public isX()/hasX() methods outside Minecraft), by readable name. */
    private static Map<String, Boolean> switches(Mob mob) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Class<?> c = mob.getClass(); c != null && !c.getName().startsWith("net.minecraft."); c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (m.getParameterCount() != 0 || m.getReturnType() != boolean.class || !Modifier.isPublic(m.getModifiers())
                        || Modifier.isStatic(m.getModifiers()) || !n.matches("(is|has)[A-Z].*")) continue;
                try {
                    out.putIfAbsent(words(n.replaceFirst("^(is|has)", "")), (Boolean) m.invoke(mob));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // A getter that needs the world just isn't compared.
                }
            }
        }
        return out;
    }

    private static String words(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    /**
     * Whether some goal of the pet starts once its owner is far and not while they are close: its follow, whatever the
     * mod calls it. Returns the distance it starts at ("12 blocks"), or null when nothing does.
     */
    private static String follows(Mob mob, ProbePlayer owner) {
        if (mob == null) return null;
        for (int far : new int[] {4, 6, 8, 10, 12, 16, 20, 24}) {
            for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
                if (rate(mob, goal, owner, far) >= 0.9 && rate(mob, goal, owner, 2) <= 0.1) return "about " + far + " blocks";
            }
        }
        return null;
    }

    private static double rate(Mob mob, WrappedGoal goal, ProbePlayer owner, int distance) {
        owner.moveTo(mob.getX() + distance, mob.getY(), mob.getZ());
        int yes = 0;
        for (int i = 0; i < FOLLOW_SAMPLES; i++) {
            try {
                if (goal.getGoal().canUse()) yes++;
            } catch (RuntimeException e) {
                return 0;
            }
        }
        return yes / (double) FOLLOW_SAMPLES;
    }

    /** Whether the pet takes on what hurts its owner and what its owner hits, tried with a zombie next to it. */
    private static String fights(ServerLevel level, EntityType<?> type, CompoundTag pet, ProbePlayer owner) {
        List<String> parts = new ArrayList<>();
        for (int which = 0; which < 2; which++) {
            Mob mob = copy(level, type, pet);
            Entity z = EntityType.ZOMBIE.create(level);
            if (mob == null || !(z instanceof LivingEntity enemy)) continue;
            enemy.moveTo(mob.getX() + 3, mob.getY(), mob.getZ());
            owner.moveTo(mob.getX() - 2, mob.getY(), mob.getZ());
            owner.tickCount += 20;
            if (which == 0) owner.setLastHurtByMob(enemy);
            else owner.setLastHurtMob(enemy);
            boolean starts = false;
            for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
                try {
                    starts |= goal.getGoal().canUse();
                } catch (RuntimeException ignored) {
                    // A goal that needs more of the world just doesn't count.
                }
            }
            if (starts) parts.add(which == 0 ? "it goes for whatever hurts its owner" : "it goes for whatever its owner hits");
            owner.setLastHurtByMob(null);
            owner.setLastHurtMob(null);
            mob.discard();
            enemy.discard();
        }
        return parts.isEmpty() ? "it doesn't take on its owner's enemies (tried with a zombie)" : String.join("; ", parts)
                + " (tried with a zombie)";
    }

    /** Whether a baby of a tamed one belongs to the same owner. */
    private static String babies(ServerLevel level, EntityType<?> type, CompoundTag pet, ProbePlayer owner) {
        Mob a = copy(level, type, pet), b = copy(level, type, pet);
        try {
            if (!(a instanceof AgeableMob parent) || !(b instanceof AgeableMob mate)) return null;
            AgeableMob baby = parent.getBreedOffspring(level, mate);
            if (baby == null) return null;
            boolean theirs = baby instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID());
            baby.discard();
            return theirs ? "a baby of a tamed one belongs to the same owner, already tamed" : "a baby is born wild (tame it again)";
        } catch (RuntimeException e) {
            return null;
        } finally {
            if (a != null) a.discard();
            if (b != null) b.discard();
        }
    }

    /** A fresh copy of the tamed pet, never added to the world. */
    private static Mob copy(ServerLevel level, EntityType<?> type, CompoundTag pet) {
        Mob mob = fresh(level, type);
        if (mob == null) return null;
        try {
            mob.load(pet.copy());
        } catch (RuntimeException e) {
            mob.discard();
            return null;
        }
        mob.setUUID(UUID.randomUUID());
        mob.moveTo(0, level.getMaxBuildHeight() + 16, 0);
        // Standing, not falling: some pets only listen to their owner on the ground (a parrot in the air is flying).
        mob.setOnGround(true);
        return mob;
    }

    /** The farm animals Animal Husbandry looks after; it tames them its own way, without an owner. */
    private static boolean husbandry(EntityType<?> type) {
        if (!net.neoforged.fml.ModList.get().isLoaded("animalhusbandry")) return false;
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("animalhusbandry") || type == EntityType.COW
                || type == EntityType.PIG || type == EntityType.SHEEP || type == EntityType.GOAT || type == EntityType.CHICKEN;
    }

    private static final Map<ServerLevel, ProbePlayer> PLAYERS = new java.util.WeakHashMap<>();

    private static ProbePlayer probePlayer(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, ProbePlayer::new);
    }

    /**
     * The stand-in player. Taming a creature gives its tamer an advancement, and an advancement is announced in chat,
     * so this one keeps a record of its own that never awards anything: nobody sees "[Field Guide] has made the
     * advancement".
     */
    private static final class ProbePlayer extends FakePlayer {
        private final PlayerAdvancements quiet;
        /** Set when the creature's code asks whether Shift is held, tries to seat the player or opens a screen. */
        boolean askedShift, rode, opened;

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

        @Override
        public boolean isShiftKeyDown() {
            askedShift = true;
            return super.isShiftKeyDown();
        }

        @Override
        public boolean isSecondaryUseActive() {
            askedShift = true;
            return super.isSecondaryUseActive();
        }

        @Override
        public boolean isCrouching() {
            askedShift = true;
            return super.isCrouching();
        }

        @Override
        public boolean startRiding(Entity vehicle, boolean force) {
            rode = true;
            return false;
        }

        @Override
        public void openHorseInventory(AbstractHorse horse, net.minecraft.world.Container inventory) {
            opened = true;
        }

        @Override
        public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider menu) {
            opened = true;
            return java.util.OptionalInt.empty();
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
     * The items worth trying for taming: anything the creature eats, anything with food on it, the items of every tag
     * named for taming, and bones and seeds (wolves and parrots take those).
     */
    private static Set<Item> candidates(ServerLevel level, EntityType<?> type) {
        Set<Item> items = new LinkedHashSet<>();
        Entity e = type.create(level);
        Animal animal = e instanceof Animal a ? a : null;
        for (Item item : BuiltInRegistries.ITEM) {
            if (skipped(item)) continue;
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

    /**
     * The items worth trying on a tamed one: the taming candidates, every item of the creature's own mod (its whistles,
     * collars and armor), and the vanilla items players use on pets (saddle, chest, shears, dye, stick).
     */
    private static Set<Item> trialItems(ServerLevel level, EntityType<?> type) {
        Set<Item> items = candidates(level, type);
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!skipped(item) && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ns)) items.add(item);
        }
        items.addAll(List.of(Items.SADDLE, Items.CHEST, Items.SHEARS, Items.BLUE_DYE, Items.STICK));
        return items;
    }

    /** Spawn eggs, buckets, leads and name tags do their own thing on any creature. */
    private static boolean skipped(Item item) {
        return item == Items.AIR || item instanceof SpawnEggItem || item instanceof BucketItem || item instanceof MobBucketItem
                || item instanceof LeadItem || item instanceof NameTagItem;
    }

    private static String label(Item item) {
        return new ItemStack(item).getHoverName().getString() + " [" + BuiltInRegistries.ITEM.getKey(item) + "]";
    }

    private static String odds(double p) {
        if (p >= 0.95) return "(tames on the first try)";
        long oneIn = Math.round(1 / p);
        return oneIn <= 1 ? "(about 1 try in 1 to 2)" : "(about 1 try in " + oneIn + ")";
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}

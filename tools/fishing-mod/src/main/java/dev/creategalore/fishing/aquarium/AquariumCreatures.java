package dev.creategalore.fishing.aquarium;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Sea creatures moved with Hybrid Aquatic's Creature Net ({@code hapi:creature_net}), which keeps the mob it caught
 * (all of it: kind, colours, size) in the net.
 *
 * <ul>
 * <li>A netted fish that is a fishing fish (a fish item with the mob's id, as vanilla and Tide fish have) goes into a
 * tank as that fish item, with its length, like a fish from a bucket, unless it has looks of its own.</li>
 * <li>Any other netted mob of {@link #LIVES_IN_TANK} (starfish, crabs, Hybrid Aquatic's fish, axolotls...) goes in as
 * the full net itself, so it keeps everything about the mob and comes back out the same; the client draws the mob
 * (crawling on the sand if it is one of {@link #CRAWLERS}).</li>
 * <li>An empty net takes a creature back out, or a fish that has a mob of its own.</li>
 * </ul>
 */
public final class AquariumCreatures {
    private AquariumCreatures() {
    }

    public static final ResourceLocation NET = ResourceLocation.fromNamespaceAndPath("hapi", "creature_net");
    private static final String STORED = "storedEntity";
    /** What the net catches (Hybrid Aquatic's tag; the fishing mod adds its own fish to it). */
    public static final TagKey<EntityType<?>> NET_CATCHABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("hapi", "net_catchable"));
    /** Mobs that can live in a tank. */
    public static final TagKey<EntityType<?>> LIVES_IN_TANK = TagKey.create(Registries.ENTITY_TYPE, CreateGaloreFishing.id("lives_in_aquarium"));
    /** Of those, the ones that keep to the floor (starfish, crabs, urchins...). */
    public static final TagKey<EntityType<?>> CRAWLERS = TagKey.create(Registries.ENTITY_TYPE, CreateGaloreFishing.id("aquarium_crawlers"));
    /** Of the crawlers, the ones that walk quickly (crabs, shrimp, lobsters); the rest creep. */
    public static final TagKey<EntityType<?>> QUICK_CRAWLERS = TagKey.create(Registries.ENTITY_TYPE, CreateGaloreFishing.id("aquarium_quick_crawlers"));

    public static boolean isNet(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(NET);
    }

    /** The mob a net holds (its saved data), if it is a net holding one. */
    public static Optional<CompoundTag> stored(ItemStack stack) {
        if (!isNet(stack)) return Optional.empty();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(STORED)) return Optional.empty();
        CompoundTag mob = data.copyTag().getCompound(STORED);
        return mob.isEmpty() ? Optional.empty() : Optional.of(mob);
    }

    public static Optional<EntityType<?>> type(ItemStack stack) {
        return stored(stack).flatMap(EntityType::by);
    }

    /** True for a net holding a mob: in a tank's list, that's a creature rather than a fish item. */
    public static boolean isCreature(ItemStack stack) {
        return stored(stack).isPresent();
    }

    /** The net emptied. */
    public static ItemStack emptied(ItemStack net) {
        ItemStack out = net.copyWithCount(1);
        CustomData data = out.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            tag.remove(STORED);
            if (tag.isEmpty()) out.remove(DataComponents.CUSTOM_DATA);
            else out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return out;
    }

    /** {@code net} holding {@code mob}. */
    static ItemStack holding(ItemStack net, CompoundTag mob) {
        ItemStack out = net.copyWithCount(1);
        CompoundTag tag = out.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(STORED, mob);
        out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return out;
    }

    /**
     * What goes into a tank from a full net: the fish item for a fishing fish, the net itself for another tank
     * creature, empty if the mob can't live in a tank.
     */
    static ItemStack tankEntry(ItemStack net) {
        Optional<CompoundTag> mob = stored(net);
        Optional<EntityType<?>> type = mob.flatMap(EntityType::by);
        if (type.isEmpty()) return ItemStack.EMPTY;
        Item fish = BuiltInRegistries.ITEM.get(BuiltInRegistries.ENTITY_TYPE.getKey(type.get()));
        if (fish != Items.AIR && FishData.getExact(fish).isPresent() && !hasLooks(mob.get())) {
            ItemStack out = new ItemStack(fish);
            double cm = mob.get().getDouble("FishLength");
            if (cm > 0) out.set(TideDataComponents.FISH_LENGTH, cm);
            return out;
        }
        return type.get().is(LIVES_IN_TANK) ? net.copyWithCount(1) : ItemStack.EMPTY;
    }

    /**
     * True if the mob has looks of its own (a tropical fish's pattern, a clownfish's kind): a fish item would draw the
     * plain kind, so such a fish lives in the tank as itself.
     */
    private static boolean hasLooks(CompoundTag mob) {
        return mob.contains("Variant") || mob.contains("Type");
    }

    /** The mob that a tank entry goes back into a net as: the creature's own, or a fish's mob with its length. */
    static Optional<CompoundTag> mobOf(ItemStack entry) {
        Optional<CompoundTag> stored = stored(entry);
        if (stored.isPresent()) return stored;
        if (FishData.getExact(entry).isEmpty()) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getItem());
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(id).isEmpty()) return Optional.empty();
        CompoundTag mob = new CompoundTag();
        mob.putString("id", id.toString());
        mob.putDouble("FishLength", AquariumBlockEntity.cm(entry));
        mob.putBoolean("PersistenceRequired", true);
        mob.putBoolean("FromFishingNet", true);
        return Optional.of(mob);
    }

    /** A creature's length in cm: its mob's width (crabs and starfish are about as long as they are wide). */
    static double cm(ItemStack creature) {
        return type(creature).map(t -> t.getDimensions().width() * 100.0).orElse(30.0);
    }
}

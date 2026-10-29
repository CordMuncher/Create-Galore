package dev.creategalore.fishing.aquarium;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.bait.LiveBait;
import dev.creategalore.fishing.bait.LiveBaitBucketItem;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;

/**
 * What right-clicking a tank does, by what's in the hand:
 * <ul>
 * <li>a fish just caught and held up: it goes in alive (kept, with its XP);</li>
 * <li>a water bucket fills a bucket's worth, an empty bucket scoops out a fish that has a bucket (else water);</li>
 * <li>any mod's fish bucket lets its fish in, with its water;</li>
 * <li>the Live-Bait Bucket scoops out spare baitfish (the tank keeps two of each kind to breed), or with sneak lets
 * its own into the tank;</li>
 * <li>a Creature Net lets the creature it holds in (a starfish, a crab, any fish), an empty one takes one out
 * ({@link AquariumCreatures});</li>
 * <li>fish meal feeds the tank; an empty hand shows how the tank is doing.</li>
 * </ul>
 * The client answers "handled" for the same items, so it doesn't also use them on the world (pour the water).
 */
final class AquariumUse {
    private AquariumUse() {
    }

    private static final String P = "message.create_galore_fishing.aquarium.";

    static ItemInteractionResult useItem(AquariumBlockEntity be, ItemStack stack, Player player, InteractionHand hand) {
        boolean ours = holdsFish(player) || stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)
                || FishData.fromBucket(stack).isPresent() || stack.getItem() instanceof LiveBaitBucketItem
                || stack.is(FishingRegistry.FISH_MEAL.get()) || AquariumCreatures.isNet(stack);
        if (!ours) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(player instanceof ServerPlayer server)) return ItemInteractionResult.SUCCESS;
        AquariumBlockEntity tank = be.tank();
        if (tank == null) {
            tell(player, Component.translatable(P + "not_formed"));
            return ItemInteractionResult.SUCCESS;
        }
        if (holdsFish(player)) {
            heldFishIn(tank, server);
        } else if (stack.is(Items.WATER_BUCKET)) {
            if (tank.fill(1000, true) < 1000) tell(player, Component.translatable(P + "water_full"));
            else {
                tank.fill(1000, false);
                swap(player, hand, stack, new ItemStack(Items.BUCKET), SoundEvents.BUCKET_EMPTY);
            }
        } else if (stack.is(Items.BUCKET)) {
            scoopOut(tank, player, hand, stack);
        } else if (stack.getItem() instanceof LiveBaitBucketItem) {
            if (player.isShiftKeyDown()) releaseBait(tank, player, stack);
            else scoopBait(tank, player, stack);
        } else if (AquariumCreatures.isNet(stack)) {
            if (AquariumCreatures.isCreature(stack)) netIn(tank, player, hand, stack);
            else netOut(tank, player, hand, stack);
        } else if (stack.is(FishingRegistry.FISH_MEAL.get())) {
            int fed = tank.feed(stack.getCount());
            if (fed > 0) {
                if (!player.getAbilities().instabuild) stack.shrink(fed);
                tank.getLevel().playSound(null, tank.getBlockPos(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.4f, 1.6f);
            }
            tell(player, Component.translatable(P + "fed", fed, tank.food()));
        } else {
            fishBucketIn(tank, player, hand, stack);
        }
        return ItemInteractionResult.CONSUME;
    }

    static InteractionResult useEmpty(AquariumBlockEntity be, Player player) {
        if (!(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        AquariumBlockEntity tank = be.tank();
        if (tank == null) {
            tell(player, Component.translatable(P + "not_formed"));
        } else if (holdsFish(player)) {
            heldFishIn(tank, server);
        } else {
            tell(player, Component.translatable(P + "status", tank.sizeX(), tank.sizeY(), tank.sizeZ(),
                    tank.water() / 1000, tank.capacity() / 1000, tank.fish().size(), tank.food()));
        }
        return InteractionResult.CONSUME;
    }

    private static boolean holdsFish(Player player) {
        return player.getData(CatchHolding.HELD).holding();
    }

    private static void heldFishIn(AquariumBlockEntity tank, ServerPlayer player) {
        ItemStack fish = CatchHolding.aliveFish(player);
        if (fish.isEmpty()) return;
        if (!tank.fits(fish)) {
            tell(player, whyNot(tank, fish));
            return;
        }
        tank.addFish(CatchHolding.keepAlive(player));
        splash(tank, SoundEvents.BUCKET_EMPTY_FISH);
        tell(player, Component.translatable(P + "in", fish.getHoverName()));
    }

    private static void fishBucketIn(AquariumBlockEntity tank, Player player, InteractionHand hand, ItemStack bucket) {
        Optional<ItemStack> fish = FishData.fromBucket(bucket).map(d -> fishFrom(bucket, d));
        if (fish.isEmpty()) return;
        // The bucket's water goes in with the fish; it comes back out if the fish can't.
        int poured = tank.fill(1000, false);
        if (!tank.addFish(fish.get())) {
            tank.drain(poured, false);
            tell(player, whyNot(tank, fish.get()));
            return;
        }
        swap(player, hand, bucket, new ItemStack(Items.BUCKET), SoundEvents.BUCKET_EMPTY_FISH);
        tell(player, Component.translatable(P + "in", fish.get().getHoverName()));
    }

    private static void netIn(AquariumBlockEntity tank, Player player, InteractionHand hand, ItemStack net) {
        ItemStack entry = AquariumCreatures.tankEntry(net);
        Component name = AquariumCreatures.type(net).map(t -> t.getDescription()).orElse(net.getHoverName());
        if (entry.isEmpty()) {
            tell(player, Component.translatable(P + "not_aquatic", name));
            return;
        }
        if (!tank.addFish(entry)) {
            tell(player, whyNot(tank, entry));
            return;
        }
        swap(player, hand, net, AquariumCreatures.emptied(net), SoundEvents.BUCKET_EMPTY_FISH);
        tell(player, Component.translatable(P + "in", name));
    }

    /** An empty net takes out the first creature, or else the first fish that has a mob of its own. */
    private static void netOut(AquariumBlockEntity tank, Player player, InteractionHand hand, ItemStack net) {
        ItemStack out = tank.takeFish(AquariumCreatures::isCreature);
        if (out.isEmpty()) out = tank.takeFish(f -> AquariumCreatures.mobOf(f).isPresent());
        if (out.isEmpty()) {
            tell(player, Component.translatable(P + "nothing_to_net"));
            return;
        }
        CompoundTag mob = AquariumCreatures.mobOf(out).orElseThrow();
        swap(player, hand, net, AquariumCreatures.holding(net, mob), SoundEvents.BUCKET_FILL_FISH);
    }

    /** The fish item for a fish bucket, with the length the bucketed mob had (Tide's fish keep one). */
    static ItemStack fishFrom(ItemStack bucket, FishData data) {
        ItemStack fish = new ItemStack(data.fish().value());
        CustomData mob = bucket.get(DataComponents.BUCKET_ENTITY_DATA);
        if (mob != null && mob.copyTag().getDouble("FishLength") > 0) {
            fish.set(TideDataComponents.FISH_LENGTH, mob.copyTag().getDouble("FishLength"));
        }
        return fish;
    }

    /** An empty bucket takes out the first fish that has a bucket of its own; with no such fish, water. */
    private static void scoopOut(AquariumBlockEntity tank, Player player, InteractionHand hand, ItemStack bucket) {
        ItemStack fish = tank.takeFish(f -> FishData.getExact(f).flatMap(FishData::bucket).isPresent());
        if (!fish.isEmpty()) {
            Item bucketItem = FishData.getExact(fish).flatMap(FishData::bucket).orElseThrow().value();
            ItemStack full = new ItemStack(bucketItem);
            CompoundTag mob = new CompoundTag();
            mob.putDouble("FishLength", AquariumBlockEntity.cm(fish));
            full.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(mob));
            tank.drain(1000, false);
            swap(player, hand, bucket, full, SoundEvents.BUCKET_FILL_FISH);
        } else if (tank.drain(1000, true) == 1000) {
            tank.drain(1000, false);
            swap(player, hand, bucket, new ItemStack(Items.WATER_BUCKET), SoundEvents.BUCKET_FILL);
        } else {
            tell(player, Component.translatable(P + "empty"));
        }
    }

    private static void scoopBait(AquariumBlockEntity tank, Player player, ItemStack bucket) {
        BundleContents.Mutable contents = new BundleContents.Mutable(
                bucket.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        int moved = 0;
        while (true) {
            ItemStack spare = tank.takeFish(tank::spare);
            if (spare.isEmpty()) break;
            ItemStack loose = LiveBait.dead(spare);
            if (contents.tryInsert(loose) == 0) {
                tank.addFish(spare);
                break;
            }
            moved++;
        }
        bucket.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        if (moved > 0) splash(tank, SoundEvents.BUCKET_FILL_FISH);
        tell(player, moved > 0 ? Component.translatable(P + "scooped", moved) : Component.translatable(P + "no_spare"));
    }

    private static void releaseBait(AquariumBlockEntity tank, Player player, ItemStack bucket) {
        BundleContents contents = bucket.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        List<ItemStack> kept = new ArrayList<>();
        int moved = 0;
        for (ItemStack stack : contents.itemCopyStream().toList()) {
            while (!stack.isEmpty() && stack.is(AquariumBlockEntity.BAITFISH) && tank.addFish(stack.copyWithCount(1))) {
                stack.shrink(1);
                moved++;
            }
            if (!stack.isEmpty()) kept.add(stack);
        }
        bucket.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(kept));
        if (moved > 0) splash(tank, SoundEvents.BUCKET_EMPTY_FISH);
        tell(player, Component.translatable(P + "released", moved));
    }

    private static Component whyNot(AquariumBlockEntity tank, ItemStack fish) {
        if (tank.water() * 2 < tank.capacity()) return Component.translatable(P + "needs_water");
        if (AquariumBlockEntity.cm(fish) / 100 > Math.max(tank.sizeX(), tank.sizeZ())) return Component.translatable(P + "too_long");
        return Component.translatable(P + "full");
    }

    private static void swap(Player player, InteractionHand hand, ItemStack held, ItemStack result, SoundEvent sound) {
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8f, 1f);
    }

    private static void splash(AquariumBlockEntity tank, SoundEvent sound) {
        tank.getLevel().playSound(null, tank.getBlockPos(), sound, SoundSource.BLOCKS, 0.8f, 1f);
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}

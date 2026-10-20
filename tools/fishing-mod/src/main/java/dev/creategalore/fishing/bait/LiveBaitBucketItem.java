package dev.creategalore.fishing.bait;

import dev.creategalore.fishing.aquarium.AquariumBlockEntity;import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * A bucket of water that keeps baitfish and leeches alive: a bundle (same clicks, tooltip and fill bar, 64 small fish
 * at most) that only takes {@link LiveBait#TAG}. Right-clicking a rod in the inventory with it loads bait from it, and
 * that bait counts as live (see {@code RodItem}). Using it in the world does nothing, so the fish don't end up on the
 * ground.
 */
public class LiveBaitBucketItem extends BundleItem {
    public LiveBaitBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack bucket, Slot slot, ClickAction action, Player player) {
        ItemStack other = slot.getItem();
        if (!other.isEmpty() && !other.is(LiveBait.TAG)) return false;
        return super.overrideStackedOnOther(bucket, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bucket, ItemStack other, Slot slot, ClickAction action, Player player,
                                            SlotAccess access) {
        if (!other.isEmpty() && !other.is(LiveBait.TAG)) return false;
        return super.overrideOtherStackedOnMe(bucket, other, slot, action, player, access);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** Sneaking on an Aquarium Tank still reaches the tank, which then lets the bucket's fish in. */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof AquariumBlockEntity;
    }

    /**
     * Takes up to {@code max} of one bait out of the bucket, marked live: the same item as {@code like} if the bucket
     * has it, else whatever is on top. Empty if the bucket is empty.
     */
    public static ItemStack takeBait(ItemStack bucket, int max, ItemStack like) {
        BundleContents contents = bucket.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.isEmpty() || max <= 0) return ItemStack.EMPTY;
        List<ItemStack> items = new ArrayList<>();
        contents.itemCopyStream().forEach(items::add);
        int pick = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!like.isEmpty() && ItemStack.isSameItem(items.get(i), like)) {
                pick = i;
                break;
            }
        }
        ItemStack taken = items.get(pick).split(max);
        if (items.get(pick).isEmpty()) items.remove(pick);
        bucket.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
        return LiveBait.alive(taken);
    }

    /** Puts {@code bait} back in the bucket (unmarked); returns what didn't fit. */
    public static ItemStack putBack(ItemStack bucket, ItemStack bait) {
        BundleContents.Mutable contents = new BundleContents.Mutable(
                bucket.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        ItemStack rest = LiveBait.dead(bait);
        contents.tryInsert(rest);
        bucket.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        return rest;
    }
}

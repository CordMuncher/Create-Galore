package dev.creategalore.fishing.machine;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Right-clicks on a Reel Drive: a rod in or out (and a hanging fish taken) as on any rod machine; bait in hand goes
 * into a geared drive's bait slot; sneaking with an empty hand takes the slot's contents back out.
 */
final class DriveUse {
    private DriveUse() {
    }

    static boolean use(DriveMount be, Player player, InteractionHand hand) {
        ReelDrive drive = be.drive();
        ItemStack held = player.getItemInHand(hand);
        boolean client = be.blockEntity().getLevel().isClientSide;
        if (drive.kind().geared() && player.isShiftKeyDown() && held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            return takeAll(drive.bait(), player, client);
        }
        if (be.mounted().use(player, hand)) return true;
        if (drive.kind().geared() && ReelDrive.isBait(held)) return insert(drive.bait(), 0, held, player, client);
        return false;
    }

    /** Puts as much of {@code held} as fits into {@code slot}; true if any went in. */
    static boolean insert(IItemHandlerModifiable handler, int slot, ItemStack held, Player player, boolean client) {
        ItemStack rest = handler.insertItem(slot, held.copy(), true);
        if (rest.getCount() == held.getCount()) return false;
        if (!client) {
            handler.insertItem(slot, held.copy(), false);
            if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.7f, 1f);
        }
        return true;
    }

    /** Gives the player everything in {@code handler}; true if there was anything. */
    static boolean takeAll(IItemHandlerModifiable handler, Player player, boolean client) {
        boolean any = false;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            any = true;
            if (client) continue;
            handler.setStackInSlot(i, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (any && !client) player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.7f, 1f);
        return any;
    }
}

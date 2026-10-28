package dev.creategalore.fishing.machine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Right-clicks on a Reel Drive: a rod in or out (and a hanging fish taken) as on any rod machine; bait in hand goes
 * into a geared drive's bait slot; sneaking with an empty hand opens the drive's window ({@link DriveMenu}).
 */
final class DriveUse {
    private DriveUse() {
    }

    static boolean use(DriveMount be, Player player, InteractionHand hand) {
        ReelDrive drive = be.drive();
        ItemStack held = player.getItemInHand(hand);
        boolean client = be.blockEntity().getLevel().isClientSide;
        if (player.isShiftKeyDown() && held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (player instanceof ServerPlayer server) {
                server.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DriveMenu(id, inventory, be),
                        be.blockEntity().getBlockState().getBlock().getName()), buf -> buf.writeByte(drive.kind().ordinal()));
            }
            return true;
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
}

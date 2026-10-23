package dev.creategalore.bridge.wear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Moves what players already wear into the wear screen. Everything that fits a group's limits simply shows up in it
 * (nothing moves: the wear slots point at the real slots the items are in). What doesn't fit (more of a group than
 * its cap, more of one item than allowed, items in the mods' look-only cosmetic slots or in a real slot no group
 * uses) goes into the player's inventory with a chat note. If the inventory is full the rest is kept in a stash: a
 * chat line with a button gives it back (once, it can't hand anything out twice), and it is shown again at every
 * join until the stash is empty.
 *
 * Runs two seconds after each join (the mods have their slots set up by then) and whenever the screen opens, so it
 * also catches a later change of the limits.
 */
final class WearMigration {
    private WearMigration() {
    }

    private static final int DELAY = 40;
    private static final Map<UUID, Integer> pending = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) pending.put(p.getUUID(), p.tickCount + DELAY);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> pending.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            Integer at = pending.get(p.getUUID());
            if (at == null || p.tickCount < at) return;
            pending.remove(p.getUUID());
            settle(p, false);
            remind(p);
        });
    }

    /**
     * Hands back whatever the wear screen can't show. Returns how many items were taken off. With {@code remind}, a
     * stash that grew is announced right away (the join check announces the stash itself, once).
     */
    static int settle(ServerPlayer player, boolean remind) {
        List<ItemStack> off = new ArrayList<>(Worn.takeCosmetics(player));
        WearLayout layout = new WearLayout(player, WearLayout.shownGroups(player));
        layout.refresh();
        for (Worn.Loc loc : layout.overflow) {
            ItemStack stack = Worn.get(player, loc);
            if (stack.isEmpty()) continue;
            off.add(stack.copy());
            Worn.set(player, loc, ItemStack.EMPTY, false);
        }
        if (off.isEmpty()) return 0;
        List<ItemStack> stash = WearData.stash(player);
        int given = 0, kept = 0;
        for (ItemStack stack : off) {
            int count = stack.getCount();
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                stash.add(stack);
                kept++;
            }
            if (stack.getCount() < count) given++;
        }
        WearData.setStash(player, stash);
        player.inventoryMenu.broadcastChanges();
        if (given > 0) player.sendSystemMessage(Component.translatable("create_galore_bridge.wear.moved", given).withStyle(ChatFormatting.YELLOW));
        if (kept > 0 && remind) remind(player);
        return off.size();
    }

    /** The chat line with the button, if anything is waiting in the stash. */
    static void remind(ServerPlayer player) {
        int waiting = count(WearData.stash(player));
        if (waiting == 0) return;
        MutableComponent button = Component.translatable("create_galore_bridge.wear.claim_button", waiting)
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wear claim"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("create_galore_bridge.wear.claim_hover"))));
        player.sendSystemMessage(Component.translatable("create_galore_bridge.wear.stashed", waiting, button).withStyle(ChatFormatting.YELLOW));
    }

    private static int count(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) n += s.getCount();
        return n;
    }

    /** /wear claim: gives the stash back as far as the inventory has room; what doesn't fit stays for the next click. */
    static int claim(ServerPlayer player) {
        List<ItemStack> stash = WearData.stash(player);
        if (stash.isEmpty()) {
            player.sendSystemMessage(Component.translatable("create_galore_bridge.wear.claim_none"));
            return 0;
        }
        int before = count(stash);
        List<ItemStack> left = new ArrayList<>();
        // The stash is emptied before anything is handed out, so a second click (or a lag spike) can't give twice.
        WearData.setStash(player, List.of());
        for (ItemStack stack : stash) {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) left.add(stack);
        }
        WearData.setStash(player, left);
        player.inventoryMenu.broadcastChanges();
        int stillWaiting = count(left);
        int got = before - stillWaiting;
        if (stillWaiting == 0) {
            player.sendSystemMessage(Component.translatable("create_galore_bridge.wear.claimed", got).withStyle(ChatFormatting.GREEN));
        } else if (got == 0) {
            remind(player);
        } else {
            player.sendSystemMessage(Component.translatable("create_galore_bridge.wear.claimed_some", got, stillWaiting).withStyle(ChatFormatting.YELLOW));
            remind(player);
        }
        return got;
    }
}

package dev.creategalore.bridge;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Takes outdated copies of the Field Guide out of players' inventories and ender chests, and hands over the current
 * book in their place when the player has none. Runs on join and every two seconds, so a copy picked up from a chest
 * goes too. The Modonomicon book reads its pages from the datapack, so it never goes out of date itself; add any
 * future replaced book to OUTDATED_PATCHOULI_BOOKS (or a check like it).
 */
public final class GuideCleanup {
    /** Patchouli book IDs of retired Field Guides. The Patchouli guide was replaced by Modonomicon in pack 0.1.6. */
    private static final Set<String> OUTDATED_PATCHOULI_BOOKS = Set.of("patchouli:create_galore");
    private static final ResourceLocation PATCHOULI_BOOK_ITEM = ResourceLocation.parse("patchouli:guide_book");
    private static final ResourceLocation PATCHOULI_BOOK_COMPONENT = ResourceLocation.parse("patchouli:book");
    private static final ResourceLocation MODONOMICON_ITEM = ResourceLocation.parse("modonomicon:modonomicon");
    private static final ResourceLocation MODONOMICON_BOOK_COMPONENT = ResourceLocation.parse("modonomicon:book_id");
    private static final ResourceLocation FIELD_GUIDE = ResourceLocation.parse("create_galore:field_guide");

    private GuideCleanup() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GuideCleanup::onLogin);
        NeoForge.EVENT_BUS.addListener(GuideCleanup::onTick);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sweep(player, true);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 0) {
            sweep(player, false);
        }
    }

    private static void sweep(ServerPlayer player, boolean enderChest) {
        int removed = removeOutdated(player.getInventory(), player, true);
        if (enderChest) {
            removed += removeOutdated(player.getEnderChestInventory(), player, false);
        }
        if (removed > 0) {
            player.sendSystemMessage(Component.translatable("create_galore_bridge.guide_cleanup.removed")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Clears outdated guides; in the player's own inventory the first one becomes the current book if they lack it. */
    private static int removeOutdated(Container container, ServerPlayer player, boolean giveCurrent) {
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!isOutdated(container.getItem(slot))) {
                continue;
            }
            removed++;
            if (giveCurrent && !hasCurrentGuide(player.getInventory())) {
                container.setItem(slot, currentGuide());
            } else {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    static boolean isOutdated(ItemStack stack) {
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(PATCHOULI_BOOK_ITEM)) {
            return false;
        }
        DataComponentType<?> book = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PATCHOULI_BOOK_COMPONENT);
        Object id = book == null ? null : stack.get(book);
        return id != null && OUTDATED_PATCHOULI_BOOKS.contains(id.toString());
    }

    private static boolean hasCurrentGuide(Inventory inventory) {
        DataComponentType<?> book = BuiltInRegistries.DATA_COMPONENT_TYPE.get(MODONOMICON_BOOK_COMPONENT);
        if (book == null) {
            return true; // no Modonomicon: nothing to hand over
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            Object id = inventory.getItem(slot).get(book);
            if (FIELD_GUIDE.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack currentGuide() {
        Item item = BuiltInRegistries.ITEM.get(MODONOMICON_ITEM);
        DataComponentType<ResourceLocation> book =
                (DataComponentType<ResourceLocation>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(MODONOMICON_BOOK_COMPONENT);
        ItemStack stack = new ItemStack(item);
        stack.set(book, FIELD_GUIDE);
        return stack;
    }
}

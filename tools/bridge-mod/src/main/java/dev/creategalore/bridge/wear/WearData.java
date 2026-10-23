package dev.creategalore.bridge.wear;

import dev.creategalore.bridge.CreateGaloreBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * What the wear screen remembers per player (kept through death, saved with the player): which real slot each wear
 * slot points at, so things stay where they were put; which wear slots are set to hidden; and the stash of accessories
 * that didn't fit anywhere when the screen came in and are waiting to be claimed.
 */
final class WearData {
    private WearData() {
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreateGaloreBridge.ID);
    static final Supplier<AttachmentType<CompoundTag>> DATA = ATTACHMENTS.register("wear",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    private static CompoundTag tag(Player player) {
        return player.getData(DATA);
    }

    private static void changed(Player player) {
        player.setData(DATA, tag(player));
    }

    /** The real slot (as {@link Worn.Loc#key}) the wear slot "group/index" pointed at, or "" if none. */
    static String pointer(Player player, String slot) {
        return tag(player).getCompound("slots").getString(slot);
    }

    static CompoundTag pointers(Player player) {
        return tag(player).getCompound("slots");
    }

    static void setPointers(Player player, CompoundTag pointers) {
        tag(player).put("slots", pointers);
        changed(player);
    }

    static boolean hidden(Player player, String slot) {
        return tag(player).getCompound("hidden").getBoolean(slot);
    }

    static boolean hasShownSetting(Player player, String slot) {
        return tag(player).getCompound("hidden").contains(slot);
    }

    static void setHidden(Player player, String slot, boolean hidden) {
        CompoundTag t = tag(player).getCompound("hidden");
        t.putBoolean(slot, hidden);
        tag(player).put("hidden", t);
        changed(player);
    }

    static List<ItemStack> stash(Player player) {
        HolderLookup.Provider registries = player.registryAccess();
        List<ItemStack> out = new ArrayList<>();
        for (Tag t : tag(player).getList("stash", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(out::add);
        }
        return out;
    }

    static void setStash(Player player, List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) if (!s.isEmpty()) list.add(s.save(player.registryAccess()));
        if (list.isEmpty()) tag(player).remove("stash");
        else tag(player).put("stash", list);
        changed(player);
    }
}

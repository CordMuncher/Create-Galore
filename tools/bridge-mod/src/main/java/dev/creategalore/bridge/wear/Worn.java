package dev.creategalore.bridge.wear;

import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.pond.AccessoriesLivingEntityExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender;

/**
 * The places a player really wears accessories: the slots of Curios and of Accessories (The Aether's slots are
 * Accessories slots too). The wear screen shows none of these directly; each of its slots points at one of them while
 * something is in it, and the item sits there exactly as if the mod's own screen had put it there, so every mod finds
 * its items where it looks for them.
 *
 * Every check here asks the mod that owns the slot (its tags, its item's own rules, other mods' events) with the
 * pack's own limits switched off, because the limits ask these questions themselves.
 */
public final class Worn {
    private Worn() {
    }

    public static final boolean HAS_CURIOS = ModList.get().isLoaded("curios");
    public static final boolean HAS_ACCESSORIES = ModList.get().isLoaded("accessories");

    /** One real slot: a Curios slot (by its identifier) or an Accessories slot (by its slot name), and its index. */
    public record Loc(boolean curios, String slot, int index) {
        public String key() {
            return (curios ? "c:" : "a:") + slot + "/" + index;
        }

        public static Loc parse(String key) {
            int cut = key.lastIndexOf('/');
            if (cut < 3 || !(key.startsWith("c:") || key.startsWith("a:"))) return null;
            try {
                return new Loc(key.startsWith("c:"), key.substring(2, cut), Integer.parseInt(key.substring(cut + 1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Set while the pack itself asks a mod whether an item fits, so its own limit checks stay out of the answer. */
    static final ThreadLocal<Boolean> ASKING = ThreadLocal.withInitial(() -> false);

    /** Every slot the player has right now, Curios first, each mod's slots in that mod's own order. */
    public static List<Loc> all(Player player) {
        List<Loc> out = new ArrayList<>();
        if (HAS_CURIOS) curiosSlots(player, out);
        if (HAS_ACCESSORIES) accessoriesSlots(player, out);
        return out;
    }

    private static void curiosSlots(Player player, List<Loc> out) {
        CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
            Map<String, ISlotType> types = CuriosApi.getPlayerSlots(player);
            List<Map.Entry<String, ICurioStacksHandler>> entries = new ArrayList<>(inv.getCurios().entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<String, ICurioStacksHandler> e) ->
                    types.containsKey(e.getKey()) ? types.get(e.getKey()).getOrder() : 1000).thenComparing(Map.Entry::getKey));
            for (Map.Entry<String, ICurioStacksHandler> e : entries) {
                NonNullList<Boolean> active = e.getValue().getActiveStates();
                for (int i = 0; i < e.getValue().getStacks().getSlots(); i++) {
                    if (active.size() > i && !active.get(i)) continue;
                    out.add(new Loc(true, e.getKey(), i));
                }
            }
        });
    }

    private static void accessoriesSlots(Player player, List<Loc> out) {
        AccessoriesCapability cap = AccessoriesCapability.get(player);
        if (cap == null) return;
        List<AccessoriesContainer> containers = new ArrayList<>(cap.getContainers().values());
        containers.sort(Comparator.comparingInt((AccessoriesContainer c) -> c.slotType() != null ? c.slotType().order() : 1000)
                .thenComparing(AccessoriesContainer::getSlotName));
        for (AccessoriesContainer c : containers) {
            for (int i = 0; i < c.getSize(); i++) out.add(new Loc(false, c.getSlotName(), i));
        }
    }

    private static IDynamicStackHandler curios(Player player, Loc loc, boolean cosmetic) {
        ICurioStacksHandler h = CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler(loc.slot)).orElse(null);
        if (h == null) return null;
        IDynamicStackHandler stacks = cosmetic ? h.getCosmeticStacks() : h.getStacks();
        return loc.index < stacks.getSlots() ? stacks : null;
    }

    private static AccessoriesContainer accessories(Player player, Loc loc) {
        AccessoriesCapability cap = AccessoriesCapability.get(player);
        if (cap == null) return null;
        AccessoriesContainer c = cap.getContainers().get(loc.slot);
        return c != null && loc.index < c.getSize() ? c : null;
    }

    /** Whether the slot still exists (a mod update or a removed mod can take slots away). */
    public static boolean exists(Player player, Loc loc) {
        return loc.curios ? HAS_CURIOS && curios(player, loc, false) != null : HAS_ACCESSORIES && accessories(player, loc) != null;
    }

    public static ItemStack get(Player player, Loc loc) {
        if (loc.curios) {
            IDynamicStackHandler s = HAS_CURIOS ? curios(player, loc, false) : null;
            return s == null ? ItemStack.EMPTY : s.getStackInSlot(loc.index);
        }
        AccessoriesContainer c = HAS_ACCESSORIES ? accessories(player, loc) : null;
        return c == null ? ItemStack.EMPTY : c.getAccessories().getItem(loc.index);
    }

    /** Puts the item into the slot as the mod's own screen would (the mod notices the change on its next tick). */
    public static void set(Player player, Loc loc, ItemStack stack, boolean byPlayer) {
        if (loc.curios) {
            IDynamicStackHandler s = curios(player, loc, false);
            if (s != null) s.setStackInSlot(loc.index, stack);
            return;
        }
        AccessoriesContainer c = accessories(player, loc);
        if (c == null) return;
        ItemStack old = c.getAccessories().getItem(loc.index);
        if (byPlayer && player instanceof AccessoriesLivingEntityExtension ext) ext.onEquipItem(c.createReference(loc.index), old, stack);
        c.getAccessories().setItem(loc.index, stack);
        c.markChanged();
    }

    /** Whether the owning mod lets this item into this slot (its tags and the item's own rules, not the pack's limits). */
    public static boolean fits(Player player, Loc loc, ItemStack stack) {
        if (stack.isEmpty()) return true;
        boolean was = ASKING.get();
        ASKING.set(true);
        try {
            if (loc.curios) {
                IDynamicStackHandler s = curios(player, loc, false);
                return s != null && s.isItemValid(loc.index, stack);
            }
            return accessories(player, loc) != null && AccessoriesAPI.canInsertIntoSlot(stack, SlotReference.of(player, loc.slot, loc.index));
        } catch (RuntimeException e) {
            return false;
        } finally {
            ASKING.set(was);
        }
    }

    /** Whether the item may be taken off (some items, like cursed ones, refuse). */
    public static boolean canTakeOff(Player player, Loc loc) {
        ItemStack stack = get(player, loc);
        if (stack.isEmpty()) return true;
        if (loc.curios) {
            IDynamicStackHandler s = curios(player, loc, false);
            return s != null && s.extractItem(loc.index, stack.getCount(), true).getCount() == stack.getCount();
        }
        return AccessoriesAPI.canUnequip(stack, SlotReference.of(player, loc.slot, loc.index));
    }

    public static int maxStack(Player player, Loc loc, ItemStack stack) {
        if (loc.curios) {
            IDynamicStackHandler s = curios(player, loc, false);
            return s == null ? 1 : Math.min(stack.getMaxStackSize(), s.getSlotLimit(loc.index));
        }
        return Math.max(1, AccessoriesAPI.getOrDefaultAccessory(stack).maxStackSize(stack));
    }

    /** Whether the item is drawn on the player (each mod's show/hide switch for the slot). */
    public static boolean shown(Player player, Loc loc) {
        if (loc.curios) {
            ICurioStacksHandler h = CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler(loc.slot)).orElse(null);
            return h == null || h.getRenders().size() <= loc.index || h.getRenders().get(loc.index);
        }
        AccessoriesContainer c = accessories(player, loc);
        return c == null || c.shouldRender(loc.index);
    }

    public static void setShown(Player player, Loc loc, boolean shown) {
        if (loc.curios) {
            ICurioStacksHandler h = CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler(loc.slot)).orElse(null);
            if (h == null || h.getRenders().size() <= loc.index) return;
            h.getRenders().set(loc.index, shown);
            if (!player.level().isClientSide) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SPacketSyncRender(player.getId(), loc.slot, loc.index, shown));
            return;
        }
        AccessoriesContainer c = accessories(player, loc);
        if (c == null) return;
        List<Boolean> options = c.renderOptions();
        while (options.size() <= loc.index) options.add(true);
        options.set(loc.index, shown);
        c.markChanged(false);
    }

    /**
     * Takes every item out of the cosmetic (look-only) slots of both mods. The wear screen has no cosmetic slots, so
     * these would otherwise be stuck where nobody can reach them.
     */
    public static List<ItemStack> takeCosmetics(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (HAS_CURIOS) CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
            for (ICurioStacksHandler h : inv.getCurios().values()) {
                IDynamicStackHandler s = h.getCosmeticStacks();
                for (int i = 0; i < s.getSlots(); i++) {
                    if (s.getStackInSlot(i).isEmpty()) continue;
                    out.add(s.getStackInSlot(i).copy());
                    s.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });
        if (HAS_ACCESSORIES) {
            AccessoriesCapability cap = AccessoriesCapability.get(player);
            if (cap != null) for (AccessoriesContainer c : cap.getContainers().values()) {
                for (int i = 0; i < c.getCosmeticAccessories().getContainerSize(); i++) {
                    ItemStack s = c.getCosmeticAccessories().getItem(i);
                    if (s.isEmpty()) continue;
                    out.add(s.copy());
                    c.getCosmeticAccessories().setItem(i, ItemStack.EMPTY);
                    c.markChanged();
                }
            }
        }
        return out;
    }
}

package dev.creategalore.bridge.wear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The wear slots of one player: the groups they have real slots for, in screen order, each with as many wear slots as
 * the group's cap, and the real slot each wear slot points at while something is in it.
 *
 * Built from what the player really wears, so it is always right: items keep the wear slot they were put in (saved
 * with the player), items that turned up some other way (a mod equipping on right-click) take the first free wear
 * slot of their group, and whatever is over a group's limits, or in a real slot no group uses, is reported as
 * overflow for {@link WearMigration} to hand back.
 */
public final class WearLayout {
    final Player player;
    public final List<WearGroups.Group> groups;
    final int[] start;
    public final int size;
    final Worn.Loc[] bound;

    WearLayout(Player player, List<WearGroups.Group> groups) {
        this.player = player;
        this.groups = groups;
        this.start = new int[groups.size()];
        int n = 0;
        for (int i = 0; i < groups.size(); i++) {
            start[i] = n;
            n += groups.get(i).cap();
        }
        this.size = n;
        this.bound = new Worn.Loc[n];
    }

    /** The groups the player has real slots for. */
    static List<WearGroups.Group> shownGroups(Player player) {
        List<Worn.Loc> locs = Worn.all(player);
        List<WearGroups.Group> out = new ArrayList<>();
        for (WearGroups.Group g : WearGroups.ALL) {
            for (Worn.Loc loc : locs) {
                if (g.uses(loc)) {
                    out.add(g);
                    break;
                }
            }
        }
        return out;
    }

    public static int size(List<WearGroups.Group> groups) {
        int n = 0;
        for (WearGroups.Group g : groups) n += g.cap();
        return n;
    }

    public int groupIndex(int slot) {
        for (int i = groups.size() - 1; i >= 0; i--) if (slot >= start[i]) return i;
        return 0;
    }

    public WearGroups.Group group(int slot) {
        return groups.get(groupIndex(slot));
    }

    public int first(WearGroups.Group g) {
        return start[groups.indexOf(g)];
    }

    String slotKey(int slot) {
        int gi = groupIndex(slot);
        return groups.get(gi).id() + "/" + (slot - start[gi]);
    }

    /** Real slots that hold something no wear slot shows: over a group's limits, or in a real slot no group uses. */
    final List<Worn.Loc> overflow = new ArrayList<>();

    /** Matches the wear slots to what the player wears right now (see the class comment). */
    void refresh() {
        overflow.clear();
        Map<WearGroups.Group, List<Worn.Loc>> worn = new LinkedHashMap<>();
        for (Worn.Loc loc : Worn.all(player)) {
            ItemStack stack = Worn.get(player, loc);
            if (stack.isEmpty()) continue;
            WearGroups.Group g = WearGroups.at(stack, loc);
            if (g == null || !groups.contains(g)) overflow.add(loc);
            else worn.computeIfAbsent(g, k -> new ArrayList<>()).add(loc);
        }
        CompoundTag pointers = new CompoundTag();
        for (int gi = 0; gi < groups.size(); gi++) {
            WearGroups.Group g = groups.get(gi);
            List<Worn.Loc> left = worn.getOrDefault(g, new ArrayList<>());
            Worn.Loc[] now = new Worn.Loc[g.cap()];
            List<ItemStack> kept = new ArrayList<>();
            // Items stay in the wear slot they had (this screen's own record first, then the saved one).
            for (int k = 0; k < g.cap(); k++) {
                int slot = start[gi] + k;
                String key = bound[slot] != null ? bound[slot].key() : WearData.pointer(player, slotKey(slot));
                for (Worn.Loc loc : left) {
                    if (loc.key().equals(key) && allows(g, kept, Worn.get(player, loc))) {
                        now[k] = loc;
                        kept.add(Worn.get(player, loc));
                        left.remove(loc);
                        break;
                    }
                }
            }
            for (Worn.Loc loc : left) {
                int free = -1;
                for (int k = 0; k < g.cap() && free < 0; k++) if (now[k] == null) free = k;
                if (free >= 0 && allows(g, kept, Worn.get(player, loc))) {
                    now[free] = loc;
                    kept.add(Worn.get(player, loc));
                } else {
                    overflow.add(loc);
                }
            }
            for (int k = 0; k < g.cap(); k++) {
                int slot = start[gi] + k;
                if (now[k] != null && !now[k].equals(bound[slot])) adoptShown(slot, now[k]);
                bound[slot] = now[k];
                if (now[k] != null) pointers.putString(slotKey(slot), now[k].key());
            }
        }
        if (!player.level().isClientSide && !pointers.equals(WearData.pointers(player))) WearData.setPointers(player, pointers);
    }

    /** A real slot newly shown in a wear slot: the wear slot's show/hide setting wins; with none yet, it takes the mod's. */
    private void adoptShown(int slot, Worn.Loc loc) {
        if (player.level().isClientSide) return;
        String key = slotKey(slot);
        if (WearData.hasShownSetting(player, key)) Worn.setShown(player, loc, !WearData.hidden(player, key));
        else WearData.setHidden(player, key, !Worn.shown(player, loc));
    }

    /** Whether one more item fits a group that already holds these: the cap (pairs of gloves count twice) and same-item limit. */
    static boolean allows(WearGroups.Group g, List<ItemStack> others, ItemStack stack) {
        int units = WearGroups.weight(stack, g);
        int same = 1;
        for (ItemStack o : others) {
            if (o.isEmpty()) continue;
            units += WearGroups.weight(o, g);
            if (ItemStack.isSameItem(o, stack)) same++;
        }
        return units <= g.cap() && same <= g.same();
    }

    /**
     * Which wear slots of a group a pair of gloves blocks: each pair covers the first empty slot after it, so the screen
     * shows both hands in use. Worked out from the group's items alone, so the screen and the server always agree.
     */
    static boolean[] covered(WearGroups.Group g, ItemStack[] items) {
        boolean[] out = new boolean[items.length];
        for (int k = 0; k < items.length; k++) {
            if (items[k].isEmpty() || WearGroups.weight(items[k], g) < 2) continue;
            for (int j = 0; j < items.length; j++) {
                if (j != k && items[j].isEmpty() && !out[j]) {
                    out[j] = true;
                    break;
                }
            }
        }
        return out;
    }

    /**
     * A real slot of the group where this item can go now: one the item's mod accepts it in, empty or the one the wear
     * slot already points at. Prefers the real slot the wear slot points at, then the mods' own order.
     */
    Worn.Loc place(WearGroups.Group g, int slot, ItemStack stack) {
        Worn.Loc mine = bound[slot];
        if (mine != null && WearGroups.at(stack, mine) == g && Worn.fits(player, mine, stack)) return mine;
        for (Worn.Loc loc : Worn.all(player)) {
            if (!g.uses(loc) || WearGroups.at(stack, loc) != g) continue;
            if (!Worn.get(player, loc).isEmpty() && !loc.equals(mine)) continue;
            if (Worn.fits(player, loc, stack)) return loc;
        }
        return null;
    }
}

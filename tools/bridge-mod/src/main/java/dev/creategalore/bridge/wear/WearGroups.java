package dev.creategalore.bridge.wear;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The wear screen's slot groups: one per thing a person can wear, each as many as a person could really wear at once,
 * and the real slots of every mod that feed it. A ring from any mod goes in Rings, a hat from any mod in Hat.
 *
 * cap: how many can be worn at once. same: how many of one and the same item; no single item counts more times than
 * the old screens let it (two ring slots per mod, one of everything else). takes: the real slots whose items count in
 * this group unless the item says otherwise. items: items that belong here wherever their mod put them (goggles are on
 * the face even though the mods give them a head slot), and also: the real slots those items may use.
 */
public final class WearGroups {
    private WearGroups() {
    }

    public record Group(String id, int cap, int same, Set<String> takes, Set<String> items, Set<String> also, String icon) {
        /** Whether items of this group may sit in this real slot. */
        public boolean uses(Worn.Loc loc) {
            return takes.contains(key(loc)) || also.contains(key(loc));
        }

        public String nameKey() {
            return "create_galore_bridge.wear.group." + id;
        }

        public ResourceLocation iconSprite() {
            return ResourceLocation.parse(icon);
        }
    }

    /** "curios:ring" for Curios' ring slot, "accessories:aether:ring_slot" for The Aether's ring slot in Accessories. */
    static String key(Worn.Loc loc) {
        return (loc.curios() ? "curios:" : "accessories:") + loc.slot();
    }

    /** Items that cover both hands: a pair of gloves uses both hand places. */
    private static final Set<String> PAIRS = Set.of(
            "aether:leather_gloves", "aether:chainmail_gloves", "aether:iron_gloves", "aether:golden_gloves", "aether:diamond_gloves",
            "aether:netherite_gloves", "aether:zanite_gloves", "aether:gravitite_gloves", "aether:neptune_gloves", "aether:obsidian_gloves",
            "aether:phoenix_gloves", "aether:valkyrie_gloves", "cataclysm:sticky_gloves", "cataclysm:blazing_grips");

    private static Group g(String id, int cap, int same, String takes, String items, String also, String icon) {
        return new Group(id, cap, same, set(takes), set(items), set(also), icon);
    }

    private static Set<String> set(String list) {
        return list.isEmpty() ? Set.of() : Set.of(list.split(" "));
    }

    private static final String HEAD = "curios:head accessories:hat accessories:face";

    /** In screen order, head to feet, then pockets. */
    public static final List<Group> ALL = List.of(
            g("hat", 1, 1, "curios:head accessories:hat", "aeronauticswinds:captains_hat", HEAD, "curios:slot/empty_head_slot"),
            g("eyes", 1, 1, "accessories:eyes", "create:goggles artifacts:night_vision_goggles", HEAD,
                    "create_galore_bridge:wear_slot/eyes"),
            g("mask", 1, 1, "accessories:face",
                    "adpother:iron_respirator adpother:gold_respirator adpother:diamond_respirator createbigcannons:gas_mask smog:respirator artifacts:snorkel",
                    HEAD, "create_galore_bridge:wear_slot/mask"),
            g("necklace", 3, 1, "curios:necklace accessories:necklace accessories:aether:pendant_slot", "", "", "curios:slot/empty_necklace_slot"),
            g("cape", 1, 1, "accessories:cape accessories:aether:cape_slot", "", "", "aether:slot/cape"),
            g("back", 1, 1, "curios:back accessories:back", "", "", "curios:slot/empty_back_slot"),
            g("body", 1, 1, "curios:body", "", "", "curios:slot/empty_body_slot"),
            g("hands", 2, 2, "curios:hands accessories:hand accessories:aether:gloves_slot", "", "", "curios:slot/empty_hands_slot"),
            g("shield", 1, 1, "accessories:aether:shield_slot", "", "", "aether:slot/shield"),
            g("rings", 10, 2, "curios:ring curios:rings accessories:ring accessories:aether:ring_slot", "artifacts:onion_ring",
                    "curios:hands accessories:hand", "curios:slot/empty_ring_slot"),
            g("belt", 1, 1, "curios:belt curios:waist accessories:belt", "", "", "curios:slot/empty_belt_slot"),
            g("quiver", 1, 1, "", "supplementaries:quiver", "curios:belt", "create_galore_bridge:wear_slot/quiver"),
            g("spellbook", 1, 1, "curios:spellbook", "", "", "curios:slot/spellbook_slot"),
            g("feet", 1, 1, "curios:feet accessories:shoes", "", "", "artifacts:slot/feet"),
            g("charm", 3, 1, "curios:charm curios:talisman accessories:charm accessories:aether:accessory_slot",
                    "supplementaries:key artifacts:whoopee_cushion", "curios:belt curios:charm", "curios:slot/empty_charm_slot"),
            g("gadget", 3, 1, "curios:informational", "", "", "create_galore_bridge:wear_slot/gadget"));

    public static Group byId(String id) {
        for (Group g : ALL) if (g.id.equals(id)) return g;
        return null;
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The group an item belongs to whatever slot it is in, or null if it goes by its slot. */
    static Group named(ItemStack stack) {
        String id = id(stack);
        for (Group g : ALL) if (g.items.contains(id)) return g;
        return null;
    }

    /** The group a real slot gives its items by default, or null if no group takes that slot. */
    public static Group ofSlot(Worn.Loc loc) {
        String key = key(loc);
        for (Group g : ALL) if (g.takes.contains(key)) return g;
        return null;
    }

    /** The group an item counts in while it sits in this real slot, or null if it may not be there. */
    public static Group at(ItemStack stack, Worn.Loc loc) {
        Group named = named(stack);
        if (named != null) return named.uses(loc) ? named : null;
        return ofSlot(loc);
    }

    /** How many places of its group the item uses: a pair of gloves takes both hands. */
    public static int weight(ItemStack stack, Group group) {
        return group.id.equals("hands") && PAIRS.contains(id(stack)) ? 2 : 1;
    }

    /** Every group the item could go in for this player (usually one). */
    public static List<Group> of(Player player, ItemStack stack) {
        List<Group> out = new ArrayList<>();
        if (stack.isEmpty()) return out;
        for (Worn.Loc loc : Worn.all(player)) {
            Group g = at(stack, loc);
            if (g != null && !out.contains(g) && Worn.fits(player, loc, stack)) out.add(g);
        }
        return out;
    }
}

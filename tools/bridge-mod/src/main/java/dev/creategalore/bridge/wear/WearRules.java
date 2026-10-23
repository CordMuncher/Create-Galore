package dev.creategalore.bridge.wear;

import io.wispforest.accessories.api.events.CanEquipCallback;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;

/**
 * The wear screen's limits hold everywhere a mod can put an accessory on, not only in the screen: right-click to
 * equip, a mod equipping its own item, a dispenser. Both mods ask every listener before an item goes into one of
 * their slots; this answers no when the item's group is full, when it already holds as many of that item as allowed,
 * or when the real slot is one no group uses. An item already in that slot is never refused, and nothing is refused
 * while the mods re-check worn items at join (a refusal would throw them off the player, onto the ground when the
 * inventory is full; {@link WearMigration} takes what doesn't fit off instead).
 */
final class WearRules {
    private WearRules() {
    }

    static void register() {
        if (Worn.HAS_CURIOS) NeoForge.EVENT_BUS.addListener(WearRules::onCurio);
        if (Worn.HAS_ACCESSORIES) CanEquipCallback.EVENT.register((stack, ref) -> {
            if (!(ref.entity() instanceof Player player)) return net.fabricmc.fabric.api.util.TriState.DEFAULT;
            return allowed(player, new Worn.Loc(false, ref.slotName(), ref.slot()), stack)
                    ? net.fabricmc.fabric.api.util.TriState.DEFAULT : net.fabricmc.fabric.api.util.TriState.FALSE;
        });
    }

    private static void onCurio(CurioCanEquipEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getEquipResult() == TriState.FALSE || Wear.rechecking()) return;
        Worn.Loc loc = new Worn.Loc(true, event.getSlotContext().identifier(), event.getSlotContext().index());
        if (event.getSlotContext().cosmetic()) {
            // Cosmetic (look-only) slots have no place on the wear screen.
            event.setEquipResult(TriState.FALSE);
            return;
        }
        if (!allowed(player, loc, event.getStack())) event.setEquipResult(TriState.FALSE);
    }

    static boolean allowed(Player player, Worn.Loc loc, ItemStack stack) {
        if (Worn.ASKING.get() || Wear.rechecking() || stack.isEmpty()) return true;
        ItemStack there = Worn.get(player, loc);
        if (there == stack || ItemStack.matches(there, stack)) return true;
        WearGroups.Group g = WearGroups.at(stack, loc);
        if (g == null) return false;
        List<ItemStack> others = new ArrayList<>();
        for (Worn.Loc other : Worn.all(player)) {
            if (other.equals(loc)) continue;
            ItemStack s = Worn.get(player, other);
            if (!s.isEmpty() && WearGroups.at(s, other) == g) others.add(s);
        }
        return WearLayout.allows(g, others, stack);
    }
}

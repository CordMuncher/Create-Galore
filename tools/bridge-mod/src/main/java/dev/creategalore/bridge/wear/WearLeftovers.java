package dev.creategalore.bridge.wear;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Takes Artifacts' effects off a player who no longer wears the item that gave them. Artifacts puts a wearable's
 * attribute changes on the player as saved modifiers (the Charm of Shrinking's is artifacts:charm_of_shrinking/generic.scale)
 * and takes them off only when Curios or Accessories tell it the item left its slot. If that news never arrives, the
 * modifier stays for good, saved with the player: a player who took the charm off stayed half size on a server.
 *
 * Once a second, and at login, every Artifacts modifier on a player is checked against what the player wears
 * (every Curios and Accessories slot, armor and hands); one whose item is worn nowhere is removed, and the log says so,
 * so it can be traced how the item left without the news.
 */
final class WearLeftovers {
    private WearLeftovers() {
    }

    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final String ARTIFACTS = "artifacts";
    private static final String WEARABLE = "artifacts.item.WearableArtifactItem";

    private static Class<?> wearable;

    static void register() {
        if (!ModList.get().isLoaded(ARTIFACTS)) return;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) check(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && (p.tickCount + p.getId()) % 20 == 0) check(p);
        });
    }

    static void check(ServerPlayer player) {
        Set<Item> worn = null;
        for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            List<AttributeModifier> leftovers = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                Item item = itemOf(modifier.id());
                if (item == null) continue;
                if (worn == null) worn = worn(player);
                if (!worn.contains(item)) leftovers.add(modifier);
            }
            for (AttributeModifier modifier : leftovers) {
                instance.removeModifier(modifier.id());
                LOG.info("Wear: took the leftover {} off {}: no {} is worn", modifier.id(), player.getGameProfile().getName(),
                        BuiltInRegistries.ITEM.getKey(itemOf(modifier.id())));
            }
        }
    }

    /** The Artifacts wearable a modifier belongs to (its id is the item's name, a slash, then the attribute's), or null. */
    private static Item itemOf(ResourceLocation id) {
        if (!id.getNamespace().equals(ARTIFACTS)) return null;
        int cut = id.getPath().indexOf('/');
        if (cut <= 0) return null;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ARTIFACTS, id.getPath().substring(0, cut)));
        return isWearable(item) ? item : null;
    }

    private static boolean isWearable(Item item) {
        if (wearable == null) {
            try {
                wearable = Class.forName(WEARABLE, false, item.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                wearable = Void.class;
            }
        }
        return wearable.isInstance(item);
    }

    /** Every item the player has on: all slots of both accessory mods (cosmetic and switched-off ones too), armor and hands. */
    private static Set<Item> worn(ServerPlayer player) {
        Set<Item> out = new HashSet<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) add(out, player.getItemBySlot(slot));
        if (Worn.HAS_CURIOS) CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
            for (ICurioStacksHandler h : inv.getCurios().values()) {
                for (IDynamicStackHandler s : List.of(h.getStacks(), h.getCosmeticStacks())) {
                    for (int i = 0; i < s.getSlots(); i++) add(out, s.getStackInSlot(i));
                }
            }
        });
        if (Worn.HAS_ACCESSORIES) {
            AccessoriesCapability cap = AccessoriesCapability.get(player);
            if (cap != null) for (AccessoriesContainer c : cap.getContainers().values()) {
                for (int i = 0; i < c.getAccessories().getContainerSize(); i++) add(out, c.getAccessories().getItem(i));
                for (int i = 0; i < c.getCosmeticAccessories().getContainerSize(); i++) add(out, c.getCosmeticAccessories().getItem(i));
            }
        }
        return out;
    }

    private static void add(Set<Item> out, ItemStack stack) {
        if (!stack.isEmpty()) out.add(stack.getItem());
    }
}

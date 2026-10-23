package dev.creategalore.bridge.wear;

import com.mojang.datafixers.util.Pair;
import dev.creategalore.bridge.CreateGaloreBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The wear screen's menu: a wear slot for each place of each group, the four armor slots, the off-hand slot and the
 * player's inventory. On the server each wear slot reads and writes the real slot it points at (see {@link WearLayout});
 * on the client the slots only hold what the server sends, and the same limit checks run on what the menu shows, so a
 * click looks right at once and the server has the last word.
 *
 * Button clicks (the vanilla packet) switch a wear slot between shown and hidden on the player.
 */
public final class WearMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreateGaloreBridge.ID);
    public static final Supplier<MenuType<WearMenu>> TYPE = MENUS.register("wear",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new WearMenu(id, inv, readGroups(buf))));

    static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    public final Player player;
    public final WearLayout layout;
    /** Wear slots first, then armor (4), off hand (1), inventory (27) and hotbar (9). */
    public final int wearEnd, armorStart, offhand, invStart, invEnd;
    private final boolean server;
    /** One bit per wear slot: hidden on the player. 16 bits per data slot (the vanilla packet sends shorts). */
    private final DataSlot[] hiddenBits;

    WearMenu(int id, Inventory inv, List<WearGroups.Group> groups) {
        super(TYPE.get(), id);
        this.player = inv.player;
        this.server = !player.level().isClientSide;
        this.layout = new WearLayout(player, groups);
        if (server) layout.refresh();
        Container shown = server ? null : new SimpleContainer(layout.size);
        for (int i = 0; i < layout.size; i++) {
            int[] xy = WearLook.slotPos(layout, i);
            addSlot(new WearSlot(this, i, shown, xy[0], xy[1]));
        }
        wearEnd = slots.size();
        armorStart = slots.size();
        for (int k = 0; k < 4; k++) addSlot(new ArmorSlot(inv, ARMOR[k], ARMOR_ICONS[k], WearLook.ARMOR_X, WearLook.ARMOR_Y + k * 18));
        offhand = slots.size();
        addSlot(new Slot(inv, 40, WearLook.OFFHAND_X, WearLook.OFFHAND_Y) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        invStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, WearLook.INV_X + col * 18, WearLook.INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, WearLook.INV_X + col * 18, WearLook.HOTBAR_Y));
        invEnd = slots.size();
        hiddenBits = new DataSlot[(layout.size + 15) / 16];
        for (int b = 0; b < hiddenBits.length; b++) {
            int word = b;
            hiddenBits[b] = addDataSlot(server ? new DataSlot() {
                private int value;

                @Override
                public int get() {
                    int bits = 0;
                    for (int i = word * 16; i < Math.min(layout.size, word * 16 + 16); i++) {
                        if (WearData.hidden(player, layout.slotKey(i))) bits |= 1 << (i - word * 16);
                    }
                    return bits;
                }

                @Override
                public void set(int value) {
                    this.value = value;
                }
            } : DataSlot.standalone());
        }
    }

    /** Opens the wear screen for a player (the server side; the client asks with {@link Wear.Open}). */
    public static void open(ServerPlayer player) {
        List<WearGroups.Group> groups = WearLayout.shownGroups(player);
        WearMigration.settle(player, true);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WearMenu(id, inv, groups),
                Component.translatable("create_galore_bridge.wear.title")), buf -> writeGroups(buf, groups));
    }

    private static void writeGroups(FriendlyByteBuf buf, List<WearGroups.Group> groups) {
        buf.writeVarInt(groups.size());
        for (WearGroups.Group g : groups) buf.writeUtf(g.id());
    }

    private static List<WearGroups.Group> readGroups(FriendlyByteBuf buf) {
        List<WearGroups.Group> out = new ArrayList<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            WearGroups.Group g = WearGroups.byId(buf.readUtf());
            if (g != null) out.add(g);
        }
        return out;
    }

    public boolean hidden(int slot) {
        DataSlot d = hiddenBits[slot / 16];
        return (d.get() >> (slot % 16) & 1) != 0;
    }

    /** What the menu shows in a group's wear slots right now. */
    ItemStack[] groupItems(WearGroups.Group g) {
        int first = layout.first(g);
        ItemStack[] items = new ItemStack[g.cap()];
        for (int k = 0; k < g.cap(); k++) items[k] = slots.get(first + k).getItem();
        return items;
    }

    /** Whether a wear slot is blocked by a pair of gloves in the same group. */
    public boolean covered(int slot) {
        WearGroups.Group g = layout.group(slot);
        return WearLayout.covered(g, groupItems(g))[slot - layout.first(g)];
    }

    /** The item that blocks a covered slot (for its tooltip). */
    public ItemStack coveredBy(int slot) {
        WearGroups.Group g = layout.group(slot);
        for (ItemStack s : groupItems(g)) if (!s.isEmpty() && WearGroups.weight(s, g) > 1) return s;
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= layout.size || !server) return false;
        String key = layout.slotKey(id);
        boolean hide = !WearData.hidden(player, key);
        WearData.setHidden(player, key, hide);
        Worn.Loc loc = layout.bound[id];
        if (loc != null) Worn.setShown(player, loc, !hide);
        return true;
    }

    @Override
    public void broadcastChanges() {
        // Something may have been put on or taken off another way (a mod equipping on use, an item breaking).
        if (server) layout.refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack before = stack.copy();
        if (index < wearEnd || index >= armorStart && index < invStart) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, invStart, invEnd, false)) return ItemStack.EMPTY;
        } else if (toWear(stack)) {
            // One item at a time into the wear slots: stop here, so the rest of a stack stays where it was.
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack, before);
            return ItemStack.EMPTY;
        } else {
            if (!toArmor(stack)) {
                int hotbar = invEnd - 9;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, invEnd, false) : !moveItemStackTo(stack, invStart, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack, before);
        slot.onTake(player, before);
        return before;
    }

    /** Shift-click from the inventory: the first free wear slot of the item's group. */
    private boolean toWear(ItemStack stack) {
        for (int i = 0; i < wearEnd; i++) {
            Slot s = slots.get(i);
            if (!s.hasItem() && s.mayPlace(stack)) {
                s.setByPlayer(stack.split(1));
                return true;
            }
        }
        return false;
    }

    private boolean toArmor(ItemStack stack) {
        for (int i = armorStart; i < armorStart + 4; i++) {
            Slot s = slots.get(i);
            if (!s.hasItem() && s.mayPlace(stack)) {
                s.setByPlayer(stack.split(1));
                return true;
            }
        }
        return false;
    }

    /** One wear slot. */
    public static final class WearSlot extends Slot {
        private final WearMenu menu;
        public final int wear;

        WearSlot(WearMenu menu, int wear, Container shown, int x, int y) {
            super(shown != null ? shown : new SimpleContainer(1), shown != null ? wear : 0, x, y);
            this.menu = menu;
            this.wear = wear;
        }

        public WearGroups.Group group() {
            return menu.layout.group(wear);
        }

        @Override
        public ItemStack getItem() {
            if (!menu.server) return super.getItem();
            Worn.Loc loc = menu.layout.bound[wear];
            return loc == null ? ItemStack.EMPTY : Worn.get(menu.player, loc);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            if (!menu.server) {
                super.set(stack);
                return;
            }
            WearLayout layout = menu.layout;
            Worn.Loc mine = layout.bound[wear];
            if (stack.isEmpty()) {
                if (mine != null) Worn.set(menu.player, mine, ItemStack.EMPTY, true);
                layout.bound[wear] = null;
                layout.refresh();
                return;
            }
            Worn.Loc loc = layout.place(group(), wear, stack);
            if (loc == null) {
                // mayPlace said yes a moment ago; if no real slot takes it now, the item goes back to the player.
                menu.player.getInventory().placeItemBackInInventory(stack);
                return;
            }
            if (mine != null && !mine.equals(loc)) Worn.set(menu.player, mine, ItemStack.EMPTY, true);
            Worn.set(menu.player, loc, stack, true);
            layout.bound[wear] = loc;
            String key = layout.slotKey(wear);
            Worn.setShown(menu.player, loc, !WearData.hidden(menu.player, key));
            layout.refresh();
        }

        @Override
        public void setChanged() {
            if (!menu.server) super.setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack now = getItem().copy();
            if (now.isEmpty()) return ItemStack.EMPTY;
            ItemStack taken = now.split(amount);
            set(now);
            return taken;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!menu.server) return true;
            Worn.Loc loc = menu.layout.bound[wear];
            return loc == null || Worn.canTakeOff(menu.player, loc);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty() || menu.covered(wear)) return false;
            WearGroups.Group g = group();
            ItemStack[] items = menu.groupItems(g);
            int k = wear - menu.layout.first(g);
            List<ItemStack> others = new ArrayList<>();
            for (int j = 0; j < items.length; j++) if (j != k) others.add(items[j]);
            if (!WearLayout.allows(g, others, stack)) return false;
            if (menu.server) return menu.layout.place(g, wear, stack) != null;
            // The client doesn't know which real slot this wear slot points at: any free one of the group will do.
            for (Worn.Loc loc : Worn.all(menu.player)) {
                if (!g.uses(loc) || WearGroups.at(stack, loc) != g) continue;
                ItemStack there = Worn.get(menu.player, loc);
                if ((there.isEmpty() || ItemStack.matches(there, items[k])) && Worn.fits(menu.player, loc, stack)) return true;
            }
            return false;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return menu.covered(wear) ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, group().iconSprite());
        }
    }

    /** Armor as in the vanilla inventory. */
    private static final class ArmorSlot extends Slot {
        private final EquipmentSlot slot;
        private final ResourceLocation icon;
        private final Player owner;

        ArmorSlot(Inventory inv, EquipmentSlot slot, ResourceLocation icon, int x, int y) {
            super(inv, 36 + slot.getIndex(), x, y);
            this.slot = slot;
            this.icon = icon;
            this.owner = inv.player;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            owner.onEquipItem(slot, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(slot, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return (stack.isEmpty() || player.isCreative() || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                    && super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }
}
